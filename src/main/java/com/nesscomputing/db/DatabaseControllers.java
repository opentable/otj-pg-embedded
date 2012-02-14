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
package com.nesscomputing.db;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.RandomStringUtils;


import com.google.common.base.Preconditions;
import com.nesscomputing.db.postgres.PostgresController;
import com.nesscomputing.migratory.ImmutableMigratoryDBIConfig;
import com.nesscomputing.migratory.MigratoryDBIConfig;

/**
 * Gives access to database controllers.
 */
public final class DatabaseControllers
{
    private DatabaseControllers()
    {
    }

    /**
     * Returns a {@link MigratoryDBIConfig} object representing a random user and optionally database.
     *
     * This method is intended to be used in test code.
     *
     * @param dbTemplate A {@link String.format} format string. The string must contain exactly one "%s" place holder for the database name. This template is used to create the JDBC connect string.
     * @param dbName The database name. Can be null, then a random name with length 16 starting with "test" is used.
     * @return A {@link MigratoryDBIConfig} object.
     *
     */
    public static ImmutableMigratoryDBIConfig createRandomUserConfig(@Nonnull final String dbTemplate, @Nullable final String dbName)
    {
        Preconditions.checkArgument(dbTemplate != null, "template must not be null!");

        final String finalDbName = (dbName != null) ? dbName : "test" + RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);
        final String userName = "test" + RandomStringUtils.randomNumeric(8);
        return new ImmutableMigratoryDBIConfig(String.format(dbTemplate, finalDbName), userName, "");
    }

    /**
     * Create a Database controller that will manage a database instance as described by the userConfig object. The database instance will be created and dropped.
     *
     * @param rootConfig Describes a database connection to manipulate database objects (database, schema, user etc.).
     * @param userConfig Description of the database connection to manage.
     * @return A database controller.
     */
    public static DatabaseController forPostgres(final MigratoryDBIConfig rootConfig, final ImmutableMigratoryDBIConfig userConfig)
    {
        return new PostgresController(rootConfig, userConfig, true);
    }

    /**
     * Create a Database controller that will manage a single schema in a database instance. The database instance will be created as necessary but is
     * not dropped. Only the schema itself will be managed by the controller.
     *
     * @param rootConfig Describes a database connection to manipulate database objects (database, schema, user etc.).
     * @param userConfig Description of the database connection to manage. The user name is used as schema name.
     * @return A database controller.
     */
    public static DatabaseController forPostgresSchema(final MigratoryDBIConfig rootConfig, final ImmutableMigratoryDBIConfig userConfig)
    {
        return new PostgresController(rootConfig, userConfig, false);
    }
}
