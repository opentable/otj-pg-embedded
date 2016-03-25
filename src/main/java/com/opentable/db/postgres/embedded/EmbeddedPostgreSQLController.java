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

import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.sql.DataSource;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.apache.commons.lang3.RandomStringUtils;

public class EmbeddedPostgreSQLController
{
    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s";

    /**
     * Each database cluster's <code>template1</code> database has a unique set of schema
     * loaded so that the databases may be cloned.
     */
    @GuardedBy("EmbeddedPostgreSQLController.class")
    private static final Map<Entry<URI, Set<String>>, Cluster> CLUSTERS = Maps.newHashMap();

    private final DatabaseMigrator migrator;
    private final Cluster cluster;

    public EmbeddedPostgreSQLController(URI baseUrl, String[] personalities)
    {
        try {
            cluster = getCluster(baseUrl, personalities);
        } catch (final IOException | SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Each schema set has its own database cluster.  The template1 database has the schema preloaded so that
     * each test case need only create a new database and not re-invoke Migratory.
     */
    private synchronized Cluster getCluster(URI baseUrl, String[] personalities) throws IOException, SQLException
    {
        final Entry<URI, Set<String>> key = Maps.immutableEntry(baseUrl, (Set<String>)ImmutableSet.copyOf(personalities));

        Cluster result = CLUSTERS.get(key);
        if (result != null) {
            return result;
        }

        result = new Cluster(EmbeddedPostgreSQL.start());

        try (Connection c = result.getPg().getTemplateDatabase().getConnection()) {
            migrator.migrate(c);
        }

        result.start();

        CLUSTERS.put(key, result);
        return result;
    }

    /**
     * @return a JDBC uri for a self-contained environment.  No two invocations will return the same database.
     */
    public String getJdbcUri()
    {
        final DbInfo db = cluster.getNextDb();
        return getJdbcUri(db);
    }

    private String getJdbcUri(DbInfo db)
    {
        return String.format(JDBC_FORMAT, db.port, db.dbName);
    }

    /**
     * Return configuration tweaks in a format appropriate for otj-jdbc DatabaseModule.
     */
    public ImmutableMap<String, String> getConfigurationTweak(String dbModuleName)
    {
        final DbInfo db = cluster.getNextDb();
        return ImmutableMap.of("ot.db." + dbModuleName + ".uri", getJdbcUri(db),
                               "ot.db." + dbModuleName + ".ds.user", db.user);
    }

    /**
     * Spawns a background thread that prepares databases ahead of time for speed, and then uses a
     * synchronous queue to hand the prepared databases off to test cases.
     */
    private static class Cluster implements Runnable
    {
        private final EmbeddedPostgreSQL pg;
        private final SynchronousQueue<DbInfo> nextDatabase = new SynchronousQueue<DbInfo>();

        Cluster(EmbeddedPostgreSQL pg)
        {
            this.pg = pg;

        }

        void start()
        {
            final ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setDaemon(true).setNameFormat("cluster-" + pg + "-preparer").build());
            service.submit(this);
            service.shutdown();
        }

        DbInfo getNextDb() throws SQLException
        {
            try {
                final DbInfo next = nextDatabase.take();
                if (next.ex != null) {
                    throw next.ex;
                }
                return next;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        EmbeddedPostgreSQL getPg()
        {
            return pg;
        }

        @Override
        public void run()
        {
            while (true) {
                final String newDbName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);
                SQLException failure = null;
                try {
                    create(pg.getPostgresDatabase(), newDbName, "postgres");
                } catch (SQLException e) {
                    failure = e;
                }
                try {
                    if (failure == null) {
                        nextDatabase.put(new DbInfo(newDbName, pg.getPort(), "postgres"));
                    } else {
                        nextDatabase.put(new DbInfo(failure));
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void create(final DataSource connectDb, @Nonnull final String dbName, @Nonnull final String userName) throws SQLException
    {
        Preconditions.checkArgument(dbName != null, "the database name must not be null!");
        Preconditions.checkArgument(userName != null, "the user name must not be null!");

        try (Connection c = connectDb.getConnection();
             PreparedStatement stmt = c.prepareStatement(String.format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", dbName, userName))) {
            stmt.executeQuery();
        }
    }

    private static class DbInfo
    {
        private final String dbName;
        private final int port;
        private final String user;
        private final SQLException ex;

        DbInfo(String dbName, int port, String user) {
            this.dbName = dbName;
            this.port = port;
            this.user = user;
            this.ex = null;
        }

        DbInfo(SQLException e) {
            this.dbName = null;
            this.port = -1;
            this.user = null;
            this.ex = e;
        }
    }
}
