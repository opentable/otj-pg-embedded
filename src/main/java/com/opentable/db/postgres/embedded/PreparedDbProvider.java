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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import javax.sql.DataSource;

import org.apache.commons.lang3.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;

public class PreparedDbProvider
{
    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s";

    /**
     * Each database cluster's <code>template1</code> database has a unique set of schema
     * loaded so that the databases may be cloned.
     */
    // @GuardedBy("PreparedDbProvider.class")
    private static final Map<DatabasePreparer, PrepPipeline> CLUSTERS = new HashMap<>();

    private final PrepPipeline dbPreparer;
    private final EmbeddedPostgres.Builder embeddedPostgresBuilder;

    public static PreparedDbProvider forPreparer(DatabasePreparer preparer) {
        return new PreparedDbProvider(preparer, EmbeddedPostgres.builder());
    }

    public static PreparedDbProvider forPreparerWithBuilder(DatabasePreparer preparer, EmbeddedPostgres.Builder builder) {
        if (builder.getBuilderPort() != 0) {
            throw new IllegalArgumentException("Do not set port on a builder to be used by PreparedDbProvider.");
        }
        if (builder.getBuilderDataDirectory() != null) {
            throw new IllegalArgumentException("Do not set the data directory on a builder to be used by PreparedDbProvider.");
        }
        return new PreparedDbProvider(preparer, builder);
    }

    private PreparedDbProvider(DatabasePreparer preparer, EmbeddedPostgres.Builder builder)
    {
        embeddedPostgresBuilder = builder;
        try {
            dbPreparer = createOrFindPreparer(preparer);
        } catch (final IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Each schema set has its own database cluster.  The template1 database has the schema preloaded so that
     * each test case need only create a new database and not re-invoke your preparer.
     */
    private synchronized PrepPipeline createOrFindPreparer(DatabasePreparer preparer) throws IOException, SQLException
    {
        PrepPipeline result = CLUSTERS.get(preparer);
        if (result != null) {
            return result;
        }

        final EmbeddedPostgres pg = embeddedPostgresBuilder.start();
        preparer.prepare(pg.getTemplateDatabase());

        result = new PrepPipeline(pg).start();
        CLUSTERS.put(preparer, result);
        return result;
    }

    /**
     * Create a new database, and return it as a JDBC connection string.
     * NB: No two invocations will return the same database.
     */
    public String createDatabase() throws SQLException
    {
        return getJdbcUri(createNewDB());
    }

    /**
     * Create a new database, and return the backing info.
     * This allows you to access the host and port.
     * More common usage is to call createDatabase() and
     * get the JDBC connection string.
     * NB: No two invocations will return the same database.
     */
    private DbInfo createNewDB() throws SQLException
    {
       return dbPreparer.getNextDb();
    }

    public ConnectionInfo createNewDatabase() throws SQLException
    {
        final DbInfo dbInfo = createNewDB();
        return dbInfo == null || !dbInfo.isSuccess() ? null : new ConnectionInfo(dbInfo.getDbName(), dbInfo.getPort(), dbInfo.getUser());
    }

    /**
     * Create a new Datasource given DBInfo.
     * More common usage is to call createDatasource().
     */
    public DataSource createDataSourceFromConnectionInfo(final ConnectionInfo connectionInfo) throws SQLException
    {
        final PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setPortNumber(connectionInfo.getPort());
        ds.setDatabaseName(connectionInfo.getDbName());
        ds.setUser(connectionInfo.getUser());
        return ds;
    }

    /**
     * Create a new database, and return it as a DataSource.
     * No two invocations will return the same database.
     */
    public DataSource createDataSource() throws SQLException
    {
        return createDataSourceFromConnectionInfo(createNewDatabase());
    }

    String getJdbcUri(DbInfo db)
    {
        return String.format(JDBC_FORMAT, db.port, db.dbName);
    }

    /**
     * Return configuration tweaks in a format appropriate for otj-jdbc DatabaseModule.
     */
    public Map<String, String> getConfigurationTweak(String dbModuleName) throws SQLException
    {
        final DbInfo db = dbPreparer.getNextDb();
        final Map<String, String> result = new HashMap<>();
        result.put("ot.db." + dbModuleName + ".uri", getJdbcUri(db));
        result.put("ot.db." + dbModuleName + ".ds.user", db.user);
        return result;
    }

    /**
     * Spawns a background thread that prepares databases ahead of time for speed, and then uses a
     * synchronous queue to hand the prepared databases off to test cases.
     */
    private static class PrepPipeline implements Runnable
    {
        private final EmbeddedPostgres pg;
        private final SynchronousQueue<DbInfo> nextDatabase = new SynchronousQueue<DbInfo>();

        PrepPipeline(EmbeddedPostgres pg)
        {
            this.pg = pg;
        }

        PrepPipeline start()
        {
            final ExecutorService service = Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("cluster-" + pg + "-preparer");
                return t;
            });
            service.submit(this);
            service.shutdown();
            return this;
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
                        nextDatabase.put(DbInfo.ok(newDbName, pg.getPort(), "postgres"));
                    } else {
                        nextDatabase.put(DbInfo.error(failure));
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void create(final DataSource connectDb, final String dbName, final String userName) throws SQLException
    {
        if (dbName == null) {
            throw new IllegalStateException("the database name must not be null!");
        }
        if (userName == null) {
            throw new IllegalStateException("the user name must not be null!");
        }

        try (Connection c = connectDb.getConnection();
             PreparedStatement stmt = c.prepareStatement(String.format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", dbName, userName))) {
            stmt.execute();
        }
    }

    public static class DbInfo
    {
        public static DbInfo ok(final String dbName, final int port, final String user) {
            return new DbInfo(dbName, port, user, null);
        }

        public static DbInfo error(SQLException e) {
            return new DbInfo(null, -1, null, e);
        }

        private final String dbName;
        private final int port;
        private final String user;
        private final SQLException ex;

        private DbInfo(final String dbName, final int port, final String user, final SQLException e) {
            this.dbName = dbName;
            this.port = port;
            this.user = user;
            this.ex = null;
        }

        public int getPort() {
            return port;
        }

        public String getDbName() {
            return dbName;
        }

        public String getUser() {
            return user;
        }

        public SQLException getException() {
            return ex;
        }

        public boolean isSuccess() {
            return ex == null;
        }
    }
}
