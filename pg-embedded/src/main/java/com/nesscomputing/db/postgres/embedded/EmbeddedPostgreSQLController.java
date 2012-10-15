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

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.nesscomputing.config.Config;
import com.nesscomputing.migratory.Migratory;
import com.nesscomputing.migratory.MigratoryConfig;
import com.nesscomputing.migratory.MigratoryContext;
import com.nesscomputing.migratory.locator.AbstractSqlResourceLocator;
import com.nesscomputing.migratory.migration.MigrationPlan;
import com.nesscomputing.testing.tweaked.TweakedModule;

public class EmbeddedPostgreSQLController
{
    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s";

    /**
     * Each database cluster's <code>template1</code> database has a unique set of schema
     * loaded so that the databases may be cloned.
     */
    @GuardedBy("EmbeddedPostgreSQLController.class")
    private static final Map<Entry<URI, Set<String>>, Cluster> CLUSTERS = Maps.newHashMap();

    private final Cluster cluster;

    public EmbeddedPostgreSQLController(URI baseUrl, String[] personalities)
    {
        try {
            cluster = getCluster(baseUrl, personalities);
        } catch (final IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Each schema set has its own database cluster.  The template1 database has the schema preloaded so that
     * each test case need only create a new database and not re-invoke Migratory.
     */
    private synchronized static Cluster getCluster(URI baseUrl, String[] personalities) throws IOException
    {
        final Entry<URI, Set<String>> key = Maps.immutableEntry(baseUrl, (Set<String>)ImmutableSet.copyOf(personalities));

        Cluster result = CLUSTERS.get(key);
        if (result != null) {
            return result;
        }

        result = new Cluster(EmbeddedPostgreSQL.start());

        final DBI dbi = new DBI(result.getPg().getTemplateDatabase());
        final Migratory migratory = new Migratory(new MigratoryConfig() {}, dbi, dbi);
        migratory.addLocator(new DatabasePreparerLocator(migratory, baseUrl));

        final MigrationPlan plan = new MigrationPlan();
        int priority = 100;

        for (final String personality : personalities) {
            plan.addMigration(personality, Integer.MAX_VALUE, priority--);
        }

        migratory.dbMigrate(plan);

        result.start();

        CLUSTERS.put(key, result);
        return result;
    }

    /**
     * Override a {@link Config} to set <code>ness.db.[db-name].uri</code> to a unique
     * database in the cluster.
     */
    public Config getTweakedConfig(Config config, String dbModuleName)
    {
        return Config.getOverriddenConfig(config,
                new MapConfiguration(getConfigurationTweak(dbModuleName)));
    }


    /**
     * Shorthand for <code>getTweakedConfig(Config.getEmptyConfig(), dbModuleName)</code>.
     */
    public Config getTweakedConfig(String dbModuleName)
    {
        return getTweakedConfig(Config.getEmptyConfig(), dbModuleName);
    }

    /**
     * @return a {@link TweakedModule} which gives services database URLs
     */
    public TweakedModule getTweakedModule(final String dbModuleName)
    {
        return new TweakedModule() {
            @Override
            public Map<String, String> getServiceConfigTweaks()
            {
                return getConfigurationTweak(dbModuleName);
            }
        };
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

    private ImmutableMap<String, String> getConfigurationTweak(String dbModuleName)
    {
        final DbInfo db = cluster.getNextDb();
        return ImmutableMap.of("ness.db." + dbModuleName + ".uri", getJdbcUri(db),
                               "ness.db." + dbModuleName + ".ds.user", db.user);
    }

    private static class DatabasePreparerLocator extends AbstractSqlResourceLocator
    {
        private final URI baseUri;

        protected DatabasePreparerLocator(final MigratoryContext migratoryContext, final URI baseUri)
        {
            super(migratoryContext);
            this.baseUri = baseUri;
        }

        @Override
        protected Entry<URI, String> getBaseInformation(final String personalityName, final String databaseType)
        {
            return Maps.immutableEntry(URI.create(baseUri.toString() + "/" + personalityName), ".*\\.sql");
        }
    }

    /**
     * Spawns a background thread that prepares databases ahead of time for speed, and then uses a
     * synchronous queue to hand the prepared databases off to test cases.
     */
    private static class Cluster implements Runnable
    {
        private final EmbeddedPostgreSQL pg;
        private final DBI pgDb;
        private final SynchronousQueue<DbInfo> nextDatabase = new SynchronousQueue<DbInfo>();

        Cluster(EmbeddedPostgreSQL pg)
        {
            this.pg = pg;
            pgDb = new DBI(pg.getPostgresDatabase());

        }

        void start()
        {
            final ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setDaemon(true).setNameFormat("cluster-" + pg + "-preparer").build());
            service.submit(this);
            service.shutdown();
        }

        DbInfo getNextDb()
        {
            try {
                return nextDatabase.take();
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
                create(pgDb, newDbName, "postgres");
                try {
                    nextDatabase.put(new DbInfo(newDbName, pg.getPort(), "postgres"));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void create(final DBI dbi, @Nonnull final String dbName, @Nonnull final String userName)
    {
        Preconditions.checkArgument(dbName != null, "the database name must not be null!");
        Preconditions.checkArgument(userName != null, "the user name must not be null!");

        dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement(String.format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", dbName, userName)).execute();
                    return null;
                }
            });
    }

    private static class DbInfo
    {
        private final String dbName;
        private final int port;
        private final String user;

        DbInfo(String dbName, int port, String user) {
            this.dbName = dbName;
            this.port = port;
            this.user = user;
        }
    }
}
