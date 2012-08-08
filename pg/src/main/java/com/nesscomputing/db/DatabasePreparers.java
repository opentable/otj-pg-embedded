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

import java.net.URI;

import javax.annotation.Nonnull;

import com.nesscomputing.db.postgres.PostgresPreparer;

/**
 * Gives access to the Database preparers.
 */
public final class DatabasePreparers
{
    private DatabasePreparers()
    {
    }

    /**
     * Returns a database preparer that manages a Postgres database.
     * @param baseUri BaseUri to load migratory sql files.
     */
    public static DatabasePreparer forPostgres(@Nonnull final URI baseUri)
    {
        return PostgresPreparer.getDatabasePreparer(baseUri);
    }

    /**
     * Returns a database preparer that manages a schema inside a Postgres database.
     * @param dbName The database to prepare.
     * @param baseUri BaseUri to load migratory sql files.
     */
    public static DatabasePreparer forPostgresSchema(@Nonnull final String dbName, @Nonnull final URI baseUri)
    {
        return PostgresPreparer.getSchemaPreparer(dbName, baseUri);
    }
}
