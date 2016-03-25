/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.db.postgres.embedded;

import static com.google.common.base.MoreObjects.firstNonNull;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.postgresql.jdbc2.optional.SimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedPostgreSQL implements Closeable
{
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgreSQL.class);
    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%s/%s?user=%s";

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_S = "5";
    private static final String PG_SUPERUSER = "postgres";
    private static final int PG_STARTUP_WAIT_MS = 10 * 1000;
    private static final String LOCK_FILE_NAME = "epg-lock";

    private static final String TMP_DIR_LOC = System.getProperty("java.io.tmpdir");
    private static final File TMP_DIR = new File(TMP_DIR_LOC, "embedded-pg");

    private final File pgDir;

    private final File dataDirectory, lockFile;
    private final UUID instanceId = UUID.randomUUID();
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final Map<String, String> postgresConfig;

    private volatile Process postmaster;
    private volatile FileOutputStream lockStream;
    private volatile FileLock lock;
    private final boolean cleanDataDirectory;

    EmbeddedPostgreSQL(File parentDirectory, File dataDirectory, boolean cleanDataDirectory, Map<String, String> postgresConfig, int port, PgBinaryResolver pgBinaryResolver) throws IOException
    {
        this.cleanDataDirectory = cleanDataDirectory;
        this.postgresConfig = ImmutableMap.copyOf(postgresConfig);
        this.port = port;
        this.pgDir = prepareBinaries(pgBinaryResolver);

        if (parentDirectory != null) {
            mkdirs(parentDirectory);
            cleanOldDataDirectories(parentDirectory);
            this.dataDirectory = firstNonNull(dataDirectory, new File(parentDirectory, instanceId.toString()));
        } else {
            this.dataDirectory = dataDirectory;
        }
        Preconditions.checkArgument(this.dataDirectory != null, "null data directory");
        LOG.trace("{} postgres data directory is {}", instanceId, this.dataDirectory);
        Preconditions.checkState(this.dataDirectory.exists() || this.dataDirectory.mkdir(), "Failed to mkdir %s", this.dataDirectory);

        lockFile = new File(this.dataDirectory, LOCK_FILE_NAME);

        if (cleanDataDirectory || !new File(dataDirectory, "postgresql.conf").exists()) {
            initdb();
        }

        lock();
        startPostmaster();
    }

    public DataSource getTemplateDatabase()
    {
        return getDatabase("postgres", "template1");
    }

    public DataSource getPostgresDatabase()
    {
        return getDatabase("postgres", "postgres");
    }

    public DataSource getDatabase(String userName, String dbName)
    {
        final SimpleDataSource ds = new SimpleDataSource();
        ds.setServerName("localhost");
        ds.setPortNumber(port);
        ds.setDatabaseName(dbName);
        ds.setUser(userName);
        return ds;
    }

    public String getJdbcUrl(String userName, String dbName)
    {
        return String.format(JDBC_FORMAT, port, dbName, userName);
    }

    public int getPort()
    {
        return port;
    }

    private static int detectPort() throws IOException
    {
        final ServerSocket socket = new ServerSocket(0);
        try {
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    private void lock() throws IOException
    {
        lockStream = new FileOutputStream(lockFile);
        Preconditions.checkState((lock = lockStream.getChannel().tryLock()) != null, "could not lock %s", lockFile);
    }

    private void initdb()
    {
        final StopWatch watch = new StopWatch();
        watch.start();
        system(pgBin("initdb"), "-A", "trust", "-U", PG_SUPERUSER, "-D", dataDirectory.getPath(), "-E", "UTF-8");
        LOG.info("{} initdb completed in {}", instanceId, watch);
    }

    private void startPostmaster() throws IOException
    {
        final StopWatch watch = new StopWatch();
        watch.start();
        Preconditions.checkState(started.getAndSet(true) == false, "Postmaster already started");

        final List<String> args = Lists.newArrayList(
                pgBin("postgres"),
                "-D", dataDirectory.getPath(),
                "-p", Integer.toString(port),
                "-i",
                "-F");

        for (final Entry<String, String> config : postgresConfig.entrySet())
        {
            args.add("-c");
            args.add(config.getKey() + "=" + config.getValue());
        }

        final ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        postmaster = builder.start();
        LOG.info("{} postmaster started as {} on port {}.  Waiting up to {}ms for server startup to finish.", instanceId, postmaster.toString(), port, PG_STARTUP_WAIT_MS);

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        waitForServerStartup(watch);
    }

    private void waitForServerStartup(StopWatch watch) throws UnknownHostException, IOException
    {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        final long maxWaitNs = TimeUnit.NANOSECONDS.convert(PG_STARTUP_WAIT_MS, TimeUnit.MILLISECONDS);
        while (System.nanoTime() - start < maxWaitNs) {
            try {
                checkReady();
                LOG.info("{} postmaster startup finished in {}", instanceId, watch);
                return;
            } catch (final SQLException e) {
                lastCause = e;
                LOG.trace("While waiting for server startup", e);
            }

            try {
                throw new IOException(String.format("%s postmaster exited with value %d, check standard out for more detail!", instanceId, postmaster.exitValue()));
            } catch (final IllegalThreadStateException e) {
                // Process is not yet dead, loop and try again
                LOG.trace("While waiting for server startup", e);
            }

            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IOException("Gave up waiting for server to start after " + PG_STARTUP_WAIT_MS + "ms", lastCause);
    }

    private void checkReady() throws SQLException
    {
        try (final Connection c = getPostgresDatabase().getConnection()) {
            try (final Statement s = c.createStatement()) {
                try (final ResultSet rs = s.executeQuery("SELECT 1")) { // NOPMD
                    Preconditions.checkState(rs.next() == true, "expecting single row");
                    Preconditions.checkState(1 == rs.getInt(1), "expecting 1");
                    Preconditions.checkState(rs.next() == false, "expecting single row");
                }
            }
        }
    }

    private Thread newCloserThread()
    {
        final Thread closeThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    Closeables.close(EmbeddedPostgreSQL.this, true);
                }
                catch (IOException ex) {
                    LOG.error("Unexpected IOException from Closeables.close", ex);
                }
            }
        });
        closeThread.setName("postgres-" + instanceId + "-closer");
        return closeThread;
    }

    @Override
    public void close() throws IOException
    {
        if (closed.getAndSet(true)) {
            return;
        }
        final StopWatch watch = new StopWatch();
        watch.start();
        try {
            pgCtl(dataDirectory, "stop");
            LOG.info("{} shut down postmaster in {}", instanceId, watch);
        } catch (final Exception e) {
            LOG.error("Could not stop postmaster " + instanceId, e);
        }
        if (lock != null) {
            lock.release();
        }
        Closeables.close(lockStream, true);

        if (cleanDataDirectory && System.getProperty("ot.epg.no-cleanup") == null) {
            try {
                FileUtils.deleteDirectory(dataDirectory);
            } catch (IOException e) {
                LOG.error("Could not clean up directory {}", dataDirectory.getAbsolutePath());
            }
        } else {
            LOG.info("Did not clean up directory {}", dataDirectory.getAbsolutePath());
        }
    }

    private void pgCtl(File dir, String action)
    {
        system(pgBin("pg_ctl"), "-D", dir.getPath(), action, "-m", PG_STOP_MODE, "-t", PG_STOP_WAIT_S, "-w");
    }

    private void cleanOldDataDirectories(File parentDirectory)
    {
        final File[] children = parentDirectory.listFiles();
        if (children == null) {
            return;
        }
        for (final File dir : children)
        {
            if (!dir.isDirectory()) {
                continue;
            }

            final File lockFile = new File(dir, LOCK_FILE_NAME);
            final boolean isTooNew = System.currentTimeMillis() - lockFile.lastModified() < 10 * 60 * 1000;
            if (!lockFile.exists() || isTooNew) {
                continue;
            }
            try {
                final FileOutputStream fos = new FileOutputStream(lockFile);
                try {
                    try (FileLock lock = fos.getChannel().tryLock()) {
                        if (lock != null) {
                            LOG.info("Found stale data directory {}", dir);
                            if (new File(dir, "postmaster.pid").exists()) {
                                try {
                                    pgCtl(dir, "stop");
                                    LOG.info("Shut down orphaned postmaster!");
                                } catch (Exception e) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.warn("Failed to stop postmaster " + dir, e);
                                    } else {
                                        LOG.warn("Failed to stop postmaster " + dir + ": " + e.getMessage());
                                    }
                                }
                            }
                            FileUtils.deleteDirectory(dir);
                        }
                    }
                } finally {
                    fos.close();
                }
            } catch (final OverlappingFileLockException e) {
                // The directory belongs to another instance in this VM.
                LOG.trace("While cleaning old data directories", e);
            } catch (final Exception e) {
                LOG.warn("While cleaning old data directories", e);
            }
        }
    }

    private String pgBin(String binaryName)
    {
        return new File(pgDir, "bin/" + binaryName).getPath();
    }

    public static EmbeddedPostgreSQL start() throws IOException
    {
        return builder().start();
    }

    public static EmbeddedPostgreSQL.Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private final File parentDirectory = new File(System.getProperty("ness.embedded-pg.dir", TMP_DIR.getPath()));
        private File builderDataDirectory;
        private final Map<String, String> config = Maps.newHashMap();
        private boolean builderCleanDataDirectory = true;
        private int builderPort = 0;
        private PgBinaryResolver pgBinaryResolver = new BundledPostgresBinaryResolver();

        Builder() {
            config.put("timezone", "UTC");
            config.put("synchronous_commit", "off");
            config.put("max_connections", "300");
        }

        public Builder setCleanDataDirectory(boolean cleanDataDirectory)
        {
            builderCleanDataDirectory = cleanDataDirectory;
            return this;
        }

        public Builder setDataDirectory(File directory)
        {
            builderDataDirectory = directory;
            return this;
        }

        public Builder setServerConfig(String key, String value)
        {
            config.put(key, value);
            return this;
        }

        public Builder setPort(int port)
        {
            builderPort = port;
            return this;
        }

        public Builder setPgBinaryResolver(PgBinaryResolver pgBinaryResolver) {
            this.pgBinaryResolver = pgBinaryResolver;
            return this;
        }

        public EmbeddedPostgreSQL start() throws IOException
        {
            if (builderPort == 0)
            {
                builderPort = detectPort();
            }
            return new EmbeddedPostgreSQL(parentDirectory, builderDataDirectory, builderCleanDataDirectory, config, builderPort, pgBinaryResolver);
        }
    }

    private static List<String> system(String... command)
    {
        try {
            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            final Process process = builder.start();
            Preconditions.checkState(0 == process.waitFor(), "Process %s failed\n%s", Arrays.asList(command), IOUtils.toString(process.getErrorStream()));
            try (InputStream stream = process.getInputStream()) {
                return IOUtils.readLines(stream);
            }
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void mkdirs(File dir)
    {
        Preconditions.checkState(dir.mkdirs() || (dir.isDirectory() && dir.exists()), // NOPMD
                "could not create %s", dir);
    }

    private static final AtomicReference<File> BINARY_DIR = new AtomicReference<>();
    private static final Lock PREPARE_BINARIES_LOCK = new ReentrantLock();

    private File prepareBinaries(PgBinaryResolver pgBinaryResolver) {
        PREPARE_BINARIES_LOCK.lock();
        try {
            if(BINARY_DIR.get() != null) {
                return BINARY_DIR.get();
            }

            String system = system("uname", "-s").get(0);
            String machineHardware = system("uname", "-m").get(0);

            LOG.info("Detected a {} {} system", system, machineHardware);
            File pgDir;
            File pgTbz;
            try {
                pgTbz = File.createTempFile("pgpg", "pgpg");
            } catch (final IOException e) {
                throw new ExceptionInInitializerError(e);
            }
            try {
                final DigestInputStream pgArchiveData = new DigestInputStream(
                        pgBinaryResolver.getPgBinary(system, machineHardware),
                        MessageDigest.getInstance("MD5"));

                final FileOutputStream os = new FileOutputStream(pgTbz);
                IOUtils.copy(pgArchiveData, os);
                pgArchiveData.close();
                os.close();

                String pgDigest = Hex.encodeHexString(pgArchiveData.getMessageDigest().digest());

                pgDir = new File(TMP_DIR, String.format("PG-%s", pgDigest));

                mkdirs(pgDir);
                final File unpackLockFile = new File(pgDir, LOCK_FILE_NAME);
                final File pgDirExists = new File(pgDir, ".exists");

                if (!pgDirExists.exists()) {
                    try (final FileOutputStream lockStream = new FileOutputStream(unpackLockFile);
                                    final FileLock unpackLock = lockStream.getChannel().tryLock()) {
                        if (unpackLock != null) {
                            try {
                                Preconditions.checkState(!pgDirExists.exists(), "unpack lock acquired but .exists file is present.");
                                LOG.info("Extracting Postgres...");
                                system("tar", "-x", "-f", pgTbz.getPath(), "-C", pgDir.getPath());
                                Files.touch(pgDirExists);
                            } finally {
                                Preconditions.checkState(unpackLockFile.delete(), "could not remove lock file %s", unpackLockFile.getAbsolutePath());
                            }
                        } else {
                            // the other guy is unpacking for us.
                            int maxAttempts = 60;
                            while (!pgDirExists.exists() && --maxAttempts > 0) {
                                Thread.sleep(1000L);
                            }
                            Preconditions.checkState(pgDirExists.exists(), "Waited 60 seconds for postgres to be unpacked but it never finished!");
                        }
                    }
                }
            } catch (final IOException | NoSuchAlgorithmException e) {
                throw new ExceptionInInitializerError(e);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ExceptionInInitializerError(ie);
            } finally {
                Preconditions.checkState(pgTbz.delete(), "could not delete %s", pgTbz);
            }
            BINARY_DIR.set(pgDir);
            LOG.info("Postgres binaries at {}", pgDir);
            return pgDir;
        } finally {
            PREPARE_BINARIES_LOCK.unlock();
        }
    }

    @Override
    public String toString()
    {
        return "EmbeddedPG-" + instanceId;
    }
}
