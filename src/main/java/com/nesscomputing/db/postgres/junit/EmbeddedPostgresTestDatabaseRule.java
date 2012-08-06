package com.nesscomputing.db.postgres.junit;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Locale;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.rules.ExternalResource;
import org.skife.jdbi.v2.DBI;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.nesscomputing.config.Config;
import com.nesscomputing.db.postgres.PostgresUtils;
import com.nesscomputing.migratory.Migratory;
import com.nesscomputing.migratory.MigratoryConfig;
import com.nesscomputing.migratory.MigratoryContext;
import com.nesscomputing.migratory.locator.AbstractSqlResourceLocator;
import com.nesscomputing.migratory.migration.MigrationPlan;
import com.nesscomputing.testing.lessio.AllowAll;
import com.nesscomputing.testing.postgres.EmbeddedPostgreSQL;

@AllowAll
public class EmbeddedPostgresTestDatabaseRule extends ExternalResource
{
    @GuardedBy("EmbeddedPostgresTestDatabaseRule.class")
    private static final Map<Entry<URI, Set<String>>, EmbeddedPostgreSQL> CLUSTERS = Maps.newHashMap();

    private final URI baseUrl;
    private final String[] personalities;

    private volatile EmbeddedPostgreSQL cluster;
    private volatile DBI pgDb;

    EmbeddedPostgresTestDatabaseRule(URI baseUrl, String[] personalities)
    {
        this.baseUrl = baseUrl;
        this.personalities = personalities;
    }

    /**
     * Each schema set has its own database cluster.  The template1 database has the schema preloaded so that
     * each test case need only create a new database and not re-invoke Migratory.
     */
    private synchronized static EmbeddedPostgreSQL getCluster(URI baseUrl, String[] personalities) throws IOException
    {
        Entry<URI, Set<String>> key = Maps.immutableEntry(baseUrl, (Set<String>)ImmutableSet.copyOf(personalities));

        EmbeddedPostgreSQL result = CLUSTERS.get(key);
        if (result != null) {
            return result;
        }

        result = EmbeddedPostgreSQL.start();

        String url = result.getTemplateDatabaseUri();
        DBI dbi = new DBI(url);
        Migratory migratory = new Migratory(new MigratoryConfig() {}, dbi, dbi);
        migratory.addLocator(new DatabasePreparerLocator(migratory, baseUrl));
        migratory.dbMigrate(new MigrationPlan(personalities));

        CLUSTERS.put(key, result);
        return result;
    }

    @Override
    protected void before() throws Throwable
    {
        super.before();
        cluster = getCluster(baseUrl, personalities);
        pgDb = new DBI(cluster.getPostgresDatabaseUri());
    }

    /**
     * Expose direct access to this rule's cluster.  Advanced usage only.
     * @return the cluster used by this rule.
     */
    protected EmbeddedPostgreSQL getCluster()
    {
        EmbeddedPostgreSQL cluster = this.cluster;
        Preconditions.checkState(cluster != null, "rule not active");
        return cluster;
    }

    /**
     * Override a {@link Config} to set <code>ness.db.[db-name].uri</code> to a unique
     * database in the cluster.
     */
    public Config getTweakedConfig(Config config, String dbModuleName)
    {
        String newDbName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);
        PostgresUtils.create(pgDb, newDbName, "postgres");
        return Config.getOverriddenConfig(config,
                new MapConfiguration(ImmutableMap.of("ness.db." + dbModuleName + ".uri", cluster.getDatabaseUri("postgres", newDbName))));
    }

    /**
     * Shorthand for <code>getTweakedConfig(Config.getEmptyConfig(), dbModuleName)</code>.
     */
    public Config getTweakedConfig(String dbModuleName)
    {
        return getTweakedConfig(Config.getEmptyConfig(), dbModuleName);
    }

    @Override
    protected void after()
    {
        pgDb = null;
        cluster = null;
        super.after();
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
}
