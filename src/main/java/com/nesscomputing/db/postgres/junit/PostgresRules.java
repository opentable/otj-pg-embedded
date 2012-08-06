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
package com.nesscomputing.db.postgres.junit;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.annotation.Nonnull;

import org.junit.rules.TestRule;


import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import com.nesscomputing.config.Config;
import com.nesscomputing.db.DatabaseControllers;
import com.nesscomputing.db.DatabasePreparers;
import com.nesscomputing.db.postgres.PostgresUtils;

public final class PostgresRules
{

    /**
     * Returns a {@link TestRule} to create and load a postgres database with a set of personalities.
     *
     * @param baseUri Base to load personalities from.
     * @param personalities List of personalities to load.
     * @return A {@link TestRule} to use in junit unit tests.
     */
    public static LocalPostgresPreparerTestRule databasePreparerRule(final URI baseUri,
                                                                     final String ... personalities)
    {
        return new LocalPostgresPreparerTestRule(DatabasePreparers.forPostgres(baseUri), personalities);
    }

    /**
     * Returns a {@link TestRule} to create and load a postgres schema with a set of personalities.
     *
     * @param dbName The database to use. If the database does not exist, it will be created and not dropped.
     * @param baseUri Base to load personalities from.
     * @param personalities List of personalities to load.
     * @return A {@link TestRule} to use in junit unit tests.
     */
    public static LocalPostgresPreparerTestRule schemaPreparerRule(@Nonnull final String dbName,
                                                                   @Nonnull final URI baseUri,
                                                                   final String ... personalities)
    {
        return new LocalPostgresPreparerTestRule(DatabasePreparers.forPostgresSchema(dbName, baseUri), personalities);
    }

    /**
     * Returns a {@link TestRule} to create and load a postgres database with a set of personalities.
     *
     * This method takes an {@link java.net.URL} as the base to allow one line usage with {@link Resources#getResource(Class, String)}.
     *
     * @param baseUri Base to load personalities from.
     * @param personalities List of personalities to load.
     * @return A {@link TestRule} to use in junit unit tests.
     */
    public static LocalPostgresPreparerTestRule databasePreparerRule(final URL baseUrl,
                                                                     final String ... personalities)
    {
        try {
            final URI baseUri = baseUrl.toURI();
            return new LocalPostgresPreparerTestRule(DatabasePreparers.forPostgres(baseUri), personalities);
        }
        catch (URISyntaxException use) {
            throw Throwables.propagate(use);
        }
    }

    /**
     * Returns a {@link TestRule} to create and load a postgres schema with a set of personalities.
     *
     * This method takes an {@link java.net.URL} as the base to allow one line usage with {@link Resources#getResource(Class, String)}.
     *
     * @param dbName The database to use. If the database does not exist, it will be created and not dropped.
     * @param baseUri Base to load personalities from.
     * @param personalities List of personalities to load.
     * @return A {@link TestRule} to use in junit unit tests.
     */
    public static LocalPostgresPreparerTestRule schemaPreparerRule(@Nonnull final String dbName,
                                                                   @Nonnull final URL baseUrl,
                                                                   final String ... personalities)
    {
        try {
            final URI baseUri = baseUrl.toURI();
            return new LocalPostgresPreparerTestRule(DatabasePreparers.forPostgresSchema(dbName, baseUri), personalities);
        }
        catch (URISyntaxException use) {
            throw Throwables.propagate(use);
        }
    }

    /**
     * Returns a {@link TestRule} to create a postgres database.
     *
     * @return A {@link TestRule} to use in junit unit tests.
     */
    public static LocalPostgresControllerTestRule databaseControllerRule()
    {
        return new LocalPostgresControllerTestRule(DatabaseControllers.forPostgres(PostgresUtils.PG_LOCALHOST_ROOT_CONFIG,
                                                                                   DatabaseControllers.createRandomUserConfig(PostgresUtils.PG_LOCALHOST_TEMPLATE, null)));
    }

    /**
     * Returns a {@link TestRule} to create a postgres schema.
     *
     * @param dbName The database to use.
     * @return A {@link TestRule} to use in junit unit tests.
     */
    public static LocalPostgresControllerTestRule schemaControllerRule(@Nonnull final String dbName)
    {
        return new LocalPostgresControllerTestRule(DatabaseControllers.forPostgresSchema(PostgresUtils.PG_LOCALHOST_ROOT_CONFIG,
                                                                                         DatabaseControllers.createRandomUserConfig(PostgresUtils.PG_LOCALHOST_TEMPLATE, dbName)));
    }

    /**
     * Returns a {@link TestRule} to create a Postgres cluster, shared amongst all test cases in this JVM.
     * The rule contributes {@link Config} switches to configure each test case to get its own database.
     */
    public static EmbeddedPostgresTestDatabaseRule embeddedDatabaseRule(@Nonnull final URL baseUrl, final String... personalities)
    {
        try {
            return embeddedDatabaseRule(baseUrl.toURI(), personalities);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a {@link TestRule} to create a Postgres cluster, shared amongst all test cases in this JVM.
     * The rule contributes {@link Config} switches to configure each test case to get its own database.
     */
    public static EmbeddedPostgresTestDatabaseRule embeddedDatabaseRule(@Nonnull final URI baseUri, final String... personalities)
    {
        return new EmbeddedPostgresTestDatabaseRule(baseUri, personalities);
    }
}
