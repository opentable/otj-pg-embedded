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
package com.nesscomputing.db.postgres;

import static com.nesscomputing.db.postgres.PostgresUtils.PG_LOCALHOST_ROOT_CONFIG;
import static com.nesscomputing.db.postgres.PostgresUtils.PG_LOCALHOST_TEMPLATE;

import java.net.URI;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

import org.skife.jdbi.v2.DBI;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.Module;
import com.nesscomputing.db.DatabaseController;
import com.nesscomputing.db.DatabaseControllers;
import com.nesscomputing.db.DatabasePreparer;
import com.nesscomputing.migratory.ImmutableMigratoryDBIConfig;
import com.nesscomputing.migratory.Migratory;
import com.nesscomputing.migratory.MigratoryConfig;
import com.nesscomputing.migratory.MigratoryContext;
import com.nesscomputing.migratory.locator.AbstractSqlResourceLocator;
import com.nesscomputing.migratory.migration.MigrationPlan;

/**
 * The database preparer creates a database and a schema for testing.
 */
public class PostgresPreparer implements DatabasePreparer
{
    private final Migratory migratory;
    private final DatabaseController databaseController;

    public static final PostgresPreparer getDatabasePreparer(@Nonnull final URI baseUri)
    {
        final ImmutableMigratoryDBIConfig userConfig = DatabaseControllers.createRandomUserConfig(PG_LOCALHOST_TEMPLATE, null);
        final DatabaseController databaseController = DatabaseControllers.forPostgres(PG_LOCALHOST_ROOT_CONFIG, userConfig);

        return new PostgresPreparer(baseUri, userConfig, databaseController);
    }

    public static final PostgresPreparer getSchemaPreparer(@Nonnull final String dbName, @Nonnull final URI baseUri)
    {
        final ImmutableMigratoryDBIConfig userConfig = DatabaseControllers.createRandomUserConfig(PG_LOCALHOST_TEMPLATE, dbName);
        final DatabaseController databaseController = DatabaseControllers.forPostgresSchema(PG_LOCALHOST_ROOT_CONFIG, userConfig);
        return new PostgresPreparer(baseUri, userConfig, databaseController);
    }


    private PostgresPreparer(@Nonnull final URI baseUri, final ImmutableMigratoryDBIConfig userConfig, final DatabaseController databaseController)
    {
        Preconditions.checkArgument(baseUri != null, "baseUri can not be null");

        this.databaseController = databaseController;

        migratory = new Migratory(new MigratoryConfig() {}, userConfig, PG_LOCALHOST_ROOT_CONFIG);
        migratory.addLocator(new DatabasePreparerLocator(migratory, baseUri));
    }

    @Override
    public void setupDatabase(final String ... personalities)
    {
        databaseController.create();
        migratory.dbInit();
        migratory.dbMigrate(new MigrationPlan(personalities));
    }

    @Override
    public void teardownDatabase()
    {
        databaseController.drop();
    }

    @Override
    public boolean exists()
    {
        return databaseController.exists();
    }

    @Override
    public DBI getDbi()
    {
        return databaseController.getDbi();
    }

    @Override
    public Module getGuiceModule(@Nonnull final String visibleDbName)
    {
        return databaseController.getGuiceModule(visibleDbName);
    }

    @Override
    public Module getJdbcModule(@Nonnull final String visibleDbName, @Nonnull final Module jdbcModule)
    {
        return databaseController.getJdbcModule(visibleDbName, jdbcModule);
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
