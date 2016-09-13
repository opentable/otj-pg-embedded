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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

public class EmbeddedPostgres implements Closeable
{
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);
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

    EmbeddedPostgres(File parentDirectory, File dataDirectory, boolean cleanDataDirectory, Map<String, String> postgresConfig, int port, PgBinaryResolver pgBinaryResolver) throws IOException
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
        Verify.verify(this.dataDirectory.exists() || this.dataDirectory.mkdir(), "Failed to mkdir %s", this.dataDirectory);

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
        final PGSimpleDataSource ds = new PGSimpleDataSource();
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
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void lock() throws IOException
    {
        lockStream = new FileOutputStream(lockFile);
        Verify.verify((lock = lockStream.getChannel().tryLock()) != null, "could not lock %s", lockFile);
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
                pgBin("pg_ctl"),
                "-D", dataDirectory.getPath(),
                "-o", Joiner.on(" ").join(createInitOptions()),
                "start"
        );

        final ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        postmaster = builder.start();
        LOG.info("{} postmaster started as {} on port {}.  Waiting up to {}ms for server startup to finish.", instanceId, postmaster.toString(), port, PG_STARTUP_WAIT_MS);

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        waitForServerStartup(watch);
    }

    private List<String> createInitOptions()
    {
        final List<String> initOptions = Lists.newArrayList(
                "-p", Integer.toString(port),
                "-F"
        );

        for (final Entry<String, String> config : postgresConfig.entrySet())
        {
            initOptions.add("-c");
            initOptions.add(config.getKey() + "=" + config.getValue());
        }

        return initOptions;
    }

    private void waitForServerStartup(StopWatch watch) throws UnknownHostException, IOException
    {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        final long maxWaitNs = TimeUnit.NANOSECONDS.convert(PG_STARTUP_WAIT_MS, TimeUnit.MILLISECONDS);
        while (System.nanoTime() - start < maxWaitNs) {
            try {
                verifyReady();
                LOG.info("{} postmaster startup finished in {}", instanceId, watch);
                return;
            } catch (final SQLException e) {
                lastCause = e;
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

    private void verifyReady() throws SQLException
    {
        try (final Connection c = getPostgresDatabase().getConnection()) {
            try (final Statement s = c.createStatement()) {
                try (final ResultSet rs = s.executeQuery("SELECT 1")) { // NOPMD
                    Verify.verify(rs.next() == true, "expecting single row");
                    Verify.verify(1 == rs.getInt(1), "expecting 1");
                    Verify.verify(rs.next() == false, "expecting single row");
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
                    Closeables.close(EmbeddedPostgres.this, true);
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
            try (final FileOutputStream fos = new FileOutputStream(lockFile);
                 final FileLock lock = fos.getChannel().tryLock()) {
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
        final String extension = SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
        return new File(pgDir, "bin/" + binaryName + extension).getPath();
    }

    public static EmbeddedPostgres start() throws IOException
    {
        return builder().start();
    }

    public static EmbeddedPostgres.Builder builder()
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

        public EmbeddedPostgres start() throws IOException
        {
            if (builderPort == 0)
            {
                builderPort = detectPort();
            }
            return new EmbeddedPostgres(parentDirectory, builderDataDirectory, builderCleanDataDirectory, config, builderPort, pgBinaryResolver);
        }
    }

    private static List<String> system(String... command)
    {
        try {
            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            final Process process = builder.start();
            Verify.verify(0 == process.waitFor(), "Process %s failed\n%s", Arrays.asList(command), IOUtils.toString(process.getErrorStream()));
            try (InputStream stream = process.getInputStream()) {
                return IOUtils.readLines(stream);
            }
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static void mkdirs(File dir)
    {
        Verify.verify(dir.mkdirs() || (dir.isDirectory() && dir.exists()), // NOPMD
                "could not create %s", dir);
    }

    private static final AtomicReference<File> BINARY_DIR = new AtomicReference<>();
    private static final Lock PREPARE_BINARIES_LOCK = new ReentrantLock();

    /**
     * Get current operating system string. The string is used in the appropriate postgres binary name.
     *
     * @return Current operating system string.
     */
    private static String getOS()
    {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "Windows";
        }
        if (SystemUtils.IS_OS_MAC_OSX) {
            return "Darwin";
        }
        if (SystemUtils.IS_OS_LINUX) {
            return "Linux";
        }
        throw new UnsupportedOperationException("Unknown OS " + SystemUtils.OS_NAME);
    }

    /**
     * Get the machine architecture string. The string is used in the appropriate postgres binary name.
     *
     * @return Current machine architecture string.
     */
    private static String getArchitecture()
    {
        return "amd64".equals(SystemUtils.OS_ARCH) ? "x86_64" : SystemUtils.OS_ARCH;
    }

    /**
     * Unpack archive compressed by tar with bzip2 compression. By default system tar is used (faster). If not found, then the
     * java implementation takes place.
     *
     * @param tbzPath The archive path.
     * @param targetDir The directory to extract the content to.
     */
    private static void extractTxz(String tbzPath, String targetDir) throws IOException
    {
        try (
                InputStream in = Files.newInputStream(Paths.get(tbzPath));
                XZInputStream xzIn = new XZInputStream(in);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)
        ) {
            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                final String individualFile = entry.getName();
                final File fsObject = new File(targetDir + "/" + individualFile);

                if (entry.isSymbolicLink() || entry.isLink()) {
                    Path target = FileSystems.getDefault().getPath(entry.getLinkName());
                    Files.createSymbolicLink(fsObject.toPath(), target);
                } else if (entry.isFile()) {
                    byte[] content = new byte[(int) entry.getSize()];
                    int read = tarIn.read(content, 0, content.length);
                    Verify.verify(read != -1, "could not read %s", individualFile);
                    mkdirs(fsObject.getParentFile());
                    try (OutputStream outputFile = new FileOutputStream(fsObject)) {
                        IOUtils.write(content, outputFile);
                    }
                } else if (entry.isDirectory()) {
                    mkdirs(fsObject);
                } else {
                    throw new UnsupportedOperationException(
                            String.format("Unsupported entry found: %s", individualFile)
                    );
                }

                if (individualFile.startsWith("bin/") || individualFile.startsWith("./bin/")) {
                    fsObject.setExecutable(true);
                }
            }
        }
    }

    private static File prepareBinaries(PgBinaryResolver pgBinaryResolver)
    {
        PREPARE_BINARIES_LOCK.lock();
        try {
            if(BINARY_DIR.get() != null) {
                return BINARY_DIR.get();
            }

            final String system = getOS();
            final String machineHardware = getArchitecture();

            LOG.info("Detected a {} {} system", system, machineHardware);
            File pgDir;
            File pgTbz;
            final InputStream pgBinary;
            try {
                pgTbz = File.createTempFile("pgpg", "pgpg");
                pgBinary = pgBinaryResolver.getPgBinary(system, machineHardware);
            } catch (final IOException e) {
                throw new ExceptionInInitializerError(e);
            }

            if (pgBinary == null) {
                throw new IllegalStateException("No Postgres binary found for " + system + " / " + machineHardware);
            }

            try (final DigestInputStream pgArchiveData = new DigestInputStream(
                        pgBinary, MessageDigest.getInstance("MD5"));
                final FileOutputStream os = new FileOutputStream(pgTbz))
            {
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
                                extractTxz(pgTbz.getPath(), pgDir.getPath());
                                Verify.verify(pgDirExists.createNewFile(), "couldn't make .exists file");
                            } catch (Exception e) {
                                LOG.error("while unpacking Postgres", e);
                            }
                        } else {
                            // the other guy is unpacking for us.
                            int maxAttempts = 60;
                            while (!pgDirExists.exists() && --maxAttempts > 0) {
                                Thread.sleep(1000L);
                            }
                            Verify.verify(pgDirExists.exists(), "Waited 60 seconds for postgres to be unpacked but it never finished!");
                        }
                    } finally {
                        if (unpackLockFile.exists()) {
                            Verify.verify(unpackLockFile.delete(), "could not remove lock file %s", unpackLockFile.getAbsolutePath());
                        }
                    }
                }
            } catch (final IOException | NoSuchAlgorithmException e) {
                throw new ExceptionInInitializerError(e);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ExceptionInInitializerError(ie);
            } finally {
                Verify.verify(pgTbz.delete(), "could not delete %s", pgTbz);
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
