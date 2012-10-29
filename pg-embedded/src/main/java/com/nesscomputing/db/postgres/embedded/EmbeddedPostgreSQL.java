/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
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
package com.nesscomputing.db.postgres.embedded;

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

import javax.sql.DataSource;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.postgresql.jdbc2.optional.SimpleDataSource;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.nesscomputing.logging.Log;

public class EmbeddedPostgreSQL implements Closeable
{
    private static final Log LOG = Log.findLog();

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_S = "5";
    private static final String PG_SUPERUSER = "postgres";
    private static final int PG_STARTUP_WAIT_MS = 10 * 1000;

    private static final String PG_DIGEST;

    private static final String TMP_DIR_LOC = System.getProperty("java.io.tmpdir");
    private static final File TMP_DIR = new File(TMP_DIR_LOC, "embedded-pg");

    private static final String LOCK_FILE_NAME = "epg-lock";
    private static final String UNAME_S, UNAME_M;
    private static final File PG_DIR;

    private final File dataDirectory, lockFile;
    private final UUID instanceId = UUID.randomUUID();
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final Map<String, String> postgresConfig;

    private volatile Process postmaster;
    private volatile FileOutputStream lockStream;
    private volatile FileLock lock;

    EmbeddedPostgreSQL(File parentDirectory, Map<String, String> postgresConfig) throws IOException
    {
        this.postgresConfig = ImmutableMap.copyOf(postgresConfig);

        Preconditions.checkNotNull(parentDirectory, "null parent directory");
        mkdirs(parentDirectory);

        cleanOldDataDirectories(parentDirectory);

        port = detectPort();

        dataDirectory = new File(parentDirectory, instanceId.toString());
        LOG.trace("%s postgres data directory is %s", instanceId, dataDirectory);
        Preconditions.checkState(dataDirectory.mkdir(), "Failed to mkdir %s", dataDirectory);

        lockFile = new File(dataDirectory, LOCK_FILE_NAME);

        initdb();
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

    public int getPort()
    {
        return port;
    }

    private int detectPort() throws IOException
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
        LOG.info("%s initdb completed in %s", instanceId, watch);
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

        ProcessBuilder builder = new ProcessBuilder(args);
        enableRedirects(builder);
        postmaster = builder.start();
        LOG.info("%s postmaster started as %s on port %s.  Waiting up to %sms for server startup to finish.", instanceId, postmaster.toString(), port, PG_STARTUP_WAIT_MS);

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
                LOG.info("%s postmaster startup finished in %s", instanceId, watch);
                return;
            } catch (final SQLException e) {
                lastCause = e;
                LOG.trace(e);
            }

            try {
                throw new IOException(String.format("%s postmaster exited with value %d, check standard out for more detail!", instanceId, postmaster.exitValue()));
            } catch (final IllegalThreadStateException e) {
                // Process is not yet dead, loop and try again
                LOG.trace(e);
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
        final Connection c = getPostgresDatabase().getConnection();
        try {
            final Statement s = c.createStatement();
            try {
                final ResultSet rs = s.executeQuery("SELECT 1"); // NOPMD
                Preconditions.checkState(rs.next() == true, "expecting single row");
                Preconditions.checkState(1 == rs.getInt(1), "expecting 1");
                Preconditions.checkState(rs.next() == false, "expecting single row");
            } finally {
                s.close();
            }
        } finally {
            c.close();
        }
    }

    private Thread newCloserThread()
    {
        final Thread closeThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Closeables.closeQuietly(EmbeddedPostgreSQL.this);
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
            LOG.info("%s shut down postmaster in %s", instanceId, watch);
        } catch (final Exception e) {
            LOG.error(e, "Could not stop postmaster %s", instanceId);
        }
        if (lock != null) {
            lock.release();
        }
        Closeables.closeQuietly(lockStream);

        if (System.getProperty("ness.epg.no-cleanup") == null) {
            FileUtils.deleteDirectory(dataDirectory);
        } else {
            LOG.info("Did not clean up directory %s", dataDirectory.getAbsolutePath());
        }
    }

    private void pgCtl(File dir, String action)
    {
        system(pgBin("pg_ctl"), "-D", dir.getPath(), action, "-m", PG_STOP_MODE, "-t", PG_STOP_WAIT_S, "-w");
    }

    private void cleanOldDataDirectories(File parentDirectory)
    {
        for (final File dir : parentDirectory.listFiles())
        {
            if (!dir.isDirectory()) {
                continue;
            }

            final File lockFile = new File(dir, LOCK_FILE_NAME);
            if (!lockFile.exists()) {
                continue;
            }
            try {
                final FileOutputStream fos = new FileOutputStream(lockFile);
                try {
                    final FileLock lock = fos.getChannel().tryLock();
                    if (lock != null) {
                        LOG.info("Found stale data directory %s", dir);
                        if (new File(dir, "postmaster.pid").exists()) {
                            pgCtl(dir, "stop");
                            LOG.info("Shut down orphaned postmaster!");
                        }
                        FileUtils.deleteDirectory(dir);
                    }
                } finally {
                    fos.close();
                }
            } catch (final IOException e) {
                LOG.error(e);
            } catch (final OverlappingFileLockException e) {
                // The directory belongs to another instance in this VM.
                LOG.trace(e);
            }
        }
    }

    private static String pgBin(String binaryName)
    {
        return new File(PG_DIR, "bin/" + binaryName).getPath();
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
        private final Map<String, String> config = Maps.newHashMap();

        Builder() {
            config.put("timezone", "UTC");
            config.put("max_connections", "300");
        }

        public void setServerConfig(String key, String value)
        {
            config.put(key, value);
        }

        public EmbeddedPostgreSQL start() throws IOException
        {
            return new EmbeddedPostgreSQL(parentDirectory, config);
        }
    }

    private static List<String> system(String... command)
    {
        try {
            final Process process = new ProcessBuilder(command).start();
            Preconditions.checkState(0 == process.waitFor(), "Process %s failed\n%s", Arrays.asList(command), IOUtils.toString(process.getErrorStream()));
            final InputStream stream = process.getInputStream();
            return IOUtils.readLines(stream);
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static void mkdirs(File dir)
    {
        Preconditions.checkState(dir.mkdirs() || (dir.isDirectory() && dir.exists()),
                "could not create %s", dir);
    }

    static {
        UNAME_S = system("uname", "-s").get(0);
        UNAME_M = system("uname", "-m").get(0);

        LOG.info("Detected a %s %s system", UNAME_S, UNAME_M);

        File pgTbz;
        try {
            pgTbz = File.createTempFile("pgpg", "pgpg");
        } catch (final IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        try {
            final DigestInputStream pgArchiveData = new DigestInputStream(
                    EmbeddedPostgreSQL.class.getResourceAsStream(String.format("/postgresql-%s-%s.tbz", UNAME_S, UNAME_M)),
                    MessageDigest.getInstance("MD5"));

            final FileOutputStream os = new FileOutputStream(pgTbz);
            IOUtils.copy(pgArchiveData, os);
            pgArchiveData.close();
            os.close();

            PG_DIGEST = Hex.encodeHexString(pgArchiveData.getMessageDigest().digest());

            PG_DIR = new File(TMP_DIR, String.format("PG-%s", PG_DIGEST));

            if (!PG_DIR.exists())
            {
                LOG.info("Extracting Postgres...");
                mkdirs(PG_DIR);
                system("tar", "-x", "-f", pgTbz.getPath(), "-C", PG_DIR.getPath());
            }
        } catch (final Exception e) {
            throw new ExceptionInInitializerError(e);
        } finally {
            Preconditions.checkState(pgTbz.delete(), "could not delete %s", pgTbz);
        }

        LOG.info("Postgres binaries at %s", PG_DIR);
    }

    @Override
    public String toString()
    {
        return "EmbeddedPG-" + instanceId;
    }
}
