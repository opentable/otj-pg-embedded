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

import java.net.URI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;

import com.google.common.base.Preconditions;
import com.nesscomputing.migratory.ImmutableMigratoryDBIConfig;
import com.nesscomputing.migratory.MigratoryDBIConfig;

/**
 * Postgres specific helpers.
 */
public final class PostgresUtils
{
    /**
     * A template to connect to a database on the local machine. Should only be used for testing.
     */
    public static final String PG_LOCALHOST_TEMPLATE = "jdbc:postgresql://localhost/%s";

    /**
     * A {@link MigratoryDBIConfig} object for root access to a local database.
     */
    public static final MigratoryDBIConfig PG_LOCALHOST_ROOT_CONFIG = new ImmutableMigratoryDBIConfig(String.format(PG_LOCALHOST_TEMPLATE, "template1"), "postgres", "");

    private PostgresUtils()
    {
    }

    /**
     * Parse the database host out of a valid postgres JDBC URI.
     */
    public static String getHostFromUri(@Nonnull final URI uri)
    {
        return parsePostgresUri(uri)[0];
    }

    /**
     * Parse the database host out of a valid postgres JDBC URI.
     */
    public static String getHostFromUri(@Nonnull final String uri)
    {
        return parsePostgresUri(URI.create(uri))[0];
    }

    /**
     * Parse the database name out of a valid postgres JDBC URI.
     */
    public static String getDatabaseFromUri(@Nonnull final URI uri)
    {
        return parsePostgresUri(uri)[1];
    }

    /**
     * Parse the database name out of a valid postgres JDBC URI.
     */
    public static String getDatabaseFromUri(@Nonnull final String uri)
    {
        return parsePostgresUri(URI.create(uri))[1];
    }

    private static String [] parsePostgresUri(@Nonnull final URI uri)
    {
        Preconditions.checkArgument(uri != null, "uri must not be null!");
        Preconditions.checkArgument("jdbc".equals(uri.getScheme()), "uri scheme must be jdbc!");
        final String schemeSpecific = uri.getSchemeSpecificPart();
        Preconditions.checkArgument(schemeSpecific != null && schemeSpecific.startsWith("postgresql://"), "not a postgres jdbc uri!");
        String pgUri = schemeSpecific.substring(13); // "postgresql://".length()
        if (pgUri.indexOf("?") != -1) {
            pgUri = pgUri.substring(0, pgUri.indexOf("?")); // cut off parameters
        }

        final String [] uriParts = StringUtils.split(pgUri, "/");
        Preconditions.checkState(uriParts.length == 2, "URI contains more than hostname and database!");
        return uriParts;
    }

    public static void create(final DBI dbi, @Nonnull final String dbName, @Nonnull final String userName)
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

    public static void drop(final DBI dbi, @Nonnull final String dbName)
    {
        Preconditions.checkArgument(dbName != null, "the database name must not be null!");

        dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement(String.format("DROP DATABASE %s", dbName)).execute();
                    return null;
                }
            });
    }

    /**
     * Returns true if the given database exists and is accessible using the supplied DBI.
     */
    public static boolean exists(final DBI dbi, @Nonnull final String dbName)
    {
        Preconditions.checkArgument(dbName != null, "the database name must not be null!");

        return dbi.withHandle(new HandleCallback<Boolean>() {
                @Override
                public Boolean withHandle(final Handle handle) {
                    return handle.createQuery("SELECT count(*) FROM pg_database WHERE datname=:database")
                        .bind("database", dbName)
                        .map(IntegerMapper.FIRST)
                        .first() != 0;
                }
            });
    }

    public static void userCreate(final DBI dbi, @Nonnull final String userName, @Nonnull final String password)
    {
        Preconditions.checkArgument(userName != null, "the user name must not be null!");
        Preconditions.checkArgument(password != null, "the password must not be null!");

        dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement(String.format("CREATE ROLE %s PASSWORD '%s' LOGIN", userName, password)).execute();
                    return null;
                }
            });
    }

    public static void userDrop(final DBI dbi, @Nonnull final String userName)
    {
        Preconditions.checkArgument(userName != null, "the user name must not be null!");

        dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement(String.format("DROP ROLE %s", userName)).execute();
                    return null;
                }
            });
    }

    /**
     * Returns true if the user database exists and is accessible using the supplied DBI.
     */
    public static boolean userExists(final DBI dbi, @Nonnull final String userName)
    {
        Preconditions.checkArgument(userName != null, "the user name must not be null!");

        return dbi.withHandle(new HandleCallback<Boolean>() {
                @Override
                public Boolean withHandle(final Handle handle) {
                    return handle.createQuery("SELECT count(*) FROM pg_roles WHERE rolname=:user")
                        .bind("user", userName)
                        .map(IntegerMapper.FIRST)
                        .first() != 0;
                }
            });
    }

    public static void schemaCreate(final DBI dbi, @Nonnull final String schemaName)
    {
        Preconditions.checkArgument(schemaName != null, "the schema name must not be null!");

        dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement(String.format("CREATE SCHEMA AUTHORIZATION %s", schemaName)).execute();
                    return null;
                }
            });
    }

    public static void schemaDrop(final DBI dbi, @Nonnull final String schemaName)
    {
        Preconditions.checkArgument(schemaName != null, "the schema name must not be null!");

        dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement(String.format("DROP SCHEMA %s CASCADE", schemaName)).execute();
                    return null;
                }
            });
    }


    /**
     * Returns true if the given schema name exists in the database represented by the given DBI object.
     */
    public static boolean schemaExists(final DBI dbi, @Nonnull final String schemaName)
    {
        Preconditions.checkArgument(schemaName != null, "the schema name must not be null!");

        return dbi.withHandle(new HandleCallback<Boolean>() {
                @Override
                public Boolean withHandle(final Handle handle) {
                    return handle.createQuery("SELECT COUNT(1) FROM pg_namespace where nspname=:schema")
                        .bind("schema", schemaName)
                        .map(IntegerMapper.FIRST)
                        .first() != 0;
                }
            });
    }


    /**
     * Returns an user count for a given user connected to a database. If the user is null, all users are counted.
     */
    public static int activeUserCount(final DBI dbi, @Nonnull final String dbName, @Nullable final String userName)
    {
        Preconditions.checkArgument(dbName != null, "the database name must not be null!");

        return dbi.withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    final String query = (userName != null) ? "SELECT count(*) FROM pg_stat_activity WHERE datname=:database AND usename=:user"
                                                            : "SELECT count(*) FROM pg_stat_activity WHERE datname=:database";

                    return handle.createQuery(query)
                        .bind("user", userName)
                        .bind("database", dbName)
                        .map(IntegerMapper.FIRST)
                        .first();
                }
            });
    }

}
