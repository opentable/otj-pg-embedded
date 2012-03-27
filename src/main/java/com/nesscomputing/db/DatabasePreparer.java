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

import javax.annotation.Nonnull;

import org.skife.jdbi.v2.DBI;

import com.google.inject.Module;

/**
 * Prepares databases with personalities.
 */
public interface DatabasePreparer
{
    /**
     * Creates the database and loads the listed personalities into it.
     *
     * @param personalities
     */
    void setupDatabase(String ... personalities);

    /**
     * Remove the database.
     */
    void teardownDatabase();

    /**
     * Returns true if the database exists and the personalities have been loaded successfully.
     *
     * @return
     */
    boolean exists();

    /**
     * Returns a DBI connected to the database. The database must have been created before.
     * @return
     */
    DBI getDbi();

    /**
     * Return a Guice module to bind the database represented by this controller. The module will
     * be bound using the visibleName. If the database does not exist, it will be created.
     *
     * The DBI bound from this module is a very simple DBI that does not support e.g. argument factories. It is intended for
     * simple tests. For more complex tests, use the {@link DatabaseController#getJdbcModule(Module)} method with a JdbcModule.
     *
     * @param visibleDbName The name used to bind all elements in the guice module. This is not the name of the physical database instance.
     * @return A guice module that can be installed.
     *
     */
    Module getGuiceModule(@Nonnull String visibleDbName);

    /**
     * Overrides a JDBC module (from ness-jdbc) to point the test database.
     *
     * @param visibleDbName The name used to bind all elements in the guice module. This is not the name of the physical database instance.
     */
    Module getJdbcModule(@Nonnull final String visibleDbName, @Nonnull Module jdbcModule);
}
