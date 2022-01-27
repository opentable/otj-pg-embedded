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
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.apache.commons.lang3.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opentable.db.postgres.embedded.EmbeddedPostgres.Builder;

public class PreparedDbProvider {
    private static final Logger LOG = LoggerFactory.getLogger(PreparedDbProvider.class);

    /**
     * Each database cluster's <code>template1</code> database has a unique set of schema
     * loaded so that the databases may be cloned.
     */
    // @GuardedBy("PreparedDbProvider.class")
    private static final Map<ClusterKey, PrepPipeline> CLUSTERS = new HashMap<>();

    private final PrepPipeline dbPreparer;

    public static PreparedDbProvider forPreparer(DatabasePreparer preparer) {
        return forPreparer(preparer, Collections.emptyList());
    }

    public static PreparedDbProvider forPreparer(DatabasePreparer preparer, Iterable<Consumer<EmbeddedPostgres.Builder>> customizers) {
        return new PreparedDbProvider(preparer, customizers);
    }

    private PreparedDbProvider(DatabasePreparer preparer, Iterable<Consumer<Builder>> customizers) {
        try {
            dbPreparer = createOrFindPreparer(preparer, customizers);
        } catch (final IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Each schema set has its own database cluster.  The template1 database has the schema preloaded so that
     * each test case need only create a new database and not re-invoke your preparer.
     */
    private static synchronized PrepPipeline createOrFindPreparer(DatabasePreparer preparer, Iterable<Consumer<Builder>> customizers) throws IOException, SQLException {
        final ClusterKey key = new ClusterKey(preparer, customizers);
        PrepPipeline result = CLUSTERS.get(key);
        if (result != null) {
            return result;
        }

        final Builder builder = EmbeddedPostgres.builder();
        customizers.forEach(c -> c.accept(builder));
        final EmbeddedPostgres pg = builder.start(); //NOPMD
        preparer.prepare(pg.getTemplateDatabase());

        result = new PrepPipeline(pg).start();
        CLUSTERS.put(key, result);
        return result;
    }

    /**
     * Create a new database, and return it as a JDBC connection string.
     * NB: No two invocations will return the same database.
     *
     * @return JDBC connection string.
     * @throws SQLException SQLException if any
     */
    public String createDatabase() throws SQLException {
        final DbInfo info = createNewDB();
        if (!info.isSuccess()) {
            return null;
        }
        try {
            return JdbcUrlUtils.addUsernamePassword(info.getUrl(), info.getUser(), info.getPassword());
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            throw new SQLException(e);
        }
    }

    /**
     * Create a new database, and return the backing info.
     * This allows you to access the host and port.
     * More common usage is to call createDatabase() and
     * get the JDBC connection string.
     * NB: No two invocations will return the same database.
     */
    private DbInfo createNewDB() throws SQLException {
        return dbPreparer.getNextDb();
    }

    public ConnectionInfo createNewDatabase() throws SQLException {
        final DbInfo dbInfo = createNewDB();
        return !dbInfo.isSuccess() ? null : new ConnectionInfo(dbInfo.getUrl(), dbInfo.getUser(), dbInfo.getPassword(), dbInfo.getHost(), dbInfo.getPort());
    }

    /**
     * Create a new Datasource given DBInfo.
     * More common usage is to call createDatasource().
     *
     * @param connectionInfo connection information
     * @return Datasource
     */
    public DataSource createDataSourceFromConnectionInfo(final ConnectionInfo connectionInfo) {
        final PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(connectionInfo.getUrl());
        ds.setUser(connectionInfo.getUser());
        ds.setPassword(connectionInfo.getPassword());
        return ds;
    }

    /**
     * Create a new database, and return it as a DataSource.
     * No two invocations will return the same database.
     *
     * @return Datasource the datasource
     * @throws SQLException SQLException if any
     */
    public DataSource createDataSource() throws SQLException {
        return createDataSourceFromConnectionInfo(createNewDatabase());
    }



    /**
     * Return configuration tweaks in a format appropriate for otj-jdbc DatabaseModule.
     *
     * @param dbModuleName Name of the module
     * @return Configuration properties
     * @throws SQLException SQLException if any
     */
    public Map<String, String> getConfigurationTweak(String dbModuleName) throws SQLException {
        final DbInfo db = dbPreparer.getNextDb();
        final Map<String, String> result = new HashMap<>();
        result.put("ot.db." + dbModuleName + ".uri", db.getUrl());
        result.put("ot.db." + dbModuleName + ".ds.user", db.user);
        result.put("ot.db." + dbModuleName + ".ds.password", db.password);
        return result;
    }

    /**
     * Spawns a background thread that prepares databases ahead of time for speed, and then uses a
     * synchronous queue to hand the prepared databases off to test cases.
     */
    private static class PrepPipeline implements Runnable {
        private final EmbeddedPostgres pg;
        private final SynchronousQueue<DbInfo> nextDatabase = new SynchronousQueue<>();

        PrepPipeline(EmbeddedPostgres pg) {
            this.pg = pg;
        }

        PrepPipeline start() {
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

        DbInfo getNextDb() throws SQLException {
            try {
                final DbInfo next = nextDatabase.take();
                if (next.ex != null) {
                    throw new SQLException(next.ex);
                }
                return next;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void run() {
            while (true) {
                final String newDbName = "pge_" + RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);
                SQLException failure = null;
                try {
                    create(pg.getPostgresDatabase(), newDbName, pg.getUserName());
                } catch (SQLException e) {
                    failure = e;
                }
                try {
                    if (failure == null) {
                        nextDatabase.put(DbInfo.ok(pg.getJdbcUrl(newDbName), pg.getUserName(), pg.getPassword(), pg.getHost(), pg.getPort()));
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

    private static void create(final DataSource connectDb, final String dbName, final String userName) throws SQLException {
        if (dbName == null) {
            throw new IllegalStateException("the database name must not be null!");
        }
        if (userName == null) {
            throw new IllegalStateException("the user name must not be null!");
        }

        try (Connection c = connectDb.getConnection();
            PreparedStatement stmt = c.prepareStatement(String.format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", dbName, userName))) {
            LOG.debug("Statement: {}", stmt);
            stmt.execute();
        }
    }

    private static class ClusterKey {

        private final DatabasePreparer preparer;
        private final Builder builder;

        ClusterKey(DatabasePreparer preparer, Iterable<Consumer<Builder>> customizers) {
            this.preparer = preparer;
            this.builder = EmbeddedPostgres.builder();
            customizers.forEach(c -> c.accept(this.builder));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClusterKey that = (ClusterKey) o;
            return Objects.equals(preparer, that.preparer) &&
                    Objects.equals(builder, that.builder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preparer, builder);
        }
    }

    public static class DbInfo {
        public static DbInfo ok(final String url, final String user, final String password, final String host, final int port) {
            return new DbInfo(url, user, password, null, host, port);
        }

        public static DbInfo error(SQLException e) {
            return new DbInfo(null, null, null, e, null, -1);
        }

        private final String url;
        private final String user;
        private final String password;
        private final SQLException ex;
        private final String host;
        private final int port;

        private DbInfo(final String url, final String user, final String password, final SQLException e, final String host, final int port) {
            this.url = url;
            this.user = user;
            this.password = password;
            this.ex = e;
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
        public String getUrl() {
            return url;
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

        public String getPassword() {
            return password;
        }
    }
}
