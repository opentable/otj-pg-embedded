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

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Properties;

import javax.annotation.Nonnull;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.nesscomputing.db.DatabaseController;
import com.nesscomputing.logging.Log;
import com.nesscomputing.migratory.ImmutableMigratoryDBIConfig;
import com.nesscomputing.migratory.MigratoryDBIConfig;

/**
 * The database controller manages creation and removal of databases and schemas.
 */
public final class PostgresController implements DatabaseController
{
    private static final Log LOG = Log.findLog();

    // Magic constants when trying to drop the database. Controls the number of attempts and the time to
    // wait between attempts.
    private static final int WAIT_USER_COUNT = 10;
    private static final long WAIT_USER_TIME = 25L;

    public static final String DETECT_LANG = "SELECT COUNT(1) FROM pg_language WHERE lanname='plpgsql'";
    public static final String CREATE_LANG = "CREATE LANGUAGE plpgsql";

    public static final String GRANT_SCHEMA = "GRANT ALL ON SCHEMA %s TO %s";
    public static final String REVOKE_SCHEMA = "REVOKE ALL ON SCHEMA %s FROM %s";

    private final DBI rootDbi;
    private final DBI userDbi;
    private final DBI userAsRootDbi;
    private final ImmutableMigratoryDBIConfig userConfig;
    private final String dbName;
    private final String schemaName;
    private final boolean manageDatabase;

    public PostgresController(final MigratoryDBIConfig rootConfig, final ImmutableMigratoryDBIConfig userConfig, final boolean manageDatabase)
    {
        this.rootDbi = new DBI(rootConfig.getDBUrl(), rootConfig.getDBUser(), rootConfig.getDBPassword());
        this.userDbi = new DBI(userConfig.getDBUrl(), userConfig.getDBUser(), userConfig.getDBPassword());

        // This is the database with the root user for language creation.
        this.userAsRootDbi = new DBI(userConfig.getDBUrl(), rootConfig.getDBUser(), rootConfig.getDBPassword());

        this.userConfig = userConfig;
        this.dbName = PostgresUtils.getDatabaseFromUri(userConfig.getDBUrl());
        this.schemaName = userConfig.getDBUser();

        this.manageDatabase = manageDatabase;
    }

    @Override
    public void create()
    {
        createUser();
        createDatabase();
        createLanguage();

        if (manageDatabase) {
            dropSchema("public");
        }
        else {
            revokeSchema("public");
        }

        createSchema(schemaName);
    }

    @Override
    public void drop()
    {
        if (manageDatabase) {
            dropDatabase();
        }
        else {
            dropSchema(schemaName);
        }

        dropUser();
    }


    @Override
    public Module getJdbcModule(@Nonnull final String visibleDbName, @Nonnull Module jdbcModule)
    {
        return Modules.override(jdbcModule).with(getInternalModule(visibleDbName));
    }


    @Override
    public Module getGuiceModule(@Nonnull final String visibleDbName)
    {
        return Modules.combine(getInternalModule(visibleDbName), new Module() {
            @Override
            public void configure(final Binder binder) {
                final Annotation annotation = Names.named(visibleDbName);
                binder.bind(IDBI.class).annotatedWith(annotation).toInstance(userDbi);
            }
        });
    }

    private Module getInternalModule(final String dbName)
    {
        final URI uri = URI.create(userConfig.getDBUrl());
        final Properties props = new Properties();
        final Annotation annotation = Names.named(dbName);

        props.put("ds.user", userConfig.getDBUser());
        props.put("ds.password", userConfig.getDBPassword());

        return new AbstractModule() {
            @Override
            public void configure() {
                bind(Properties.class).annotatedWith(annotation).toInstance(props);
                bind(URI.class).annotatedWith(annotation).toInstance(uri);
            }
        };
    }

    @Override
    public boolean exists()
    {
        return PostgresUtils.exists(rootDbi, dbName) && PostgresUtils.schemaExists(userAsRootDbi, schemaName);
    }

    @Override
    public DBI getDbi()
    {
        return userDbi;
    }

    private void createUser()
    {
        if (!PostgresUtils.userExists(rootDbi, userConfig.getDBUser())) {
            PostgresUtils.userCreate(rootDbi, userConfig.getDBUser(), userConfig.getDBPassword());
        }
    }

    private void dropUser()
    {
        if (PostgresUtils.userExists(rootDbi, userConfig.getDBUser())) {
            PostgresUtils.userDrop(rootDbi, userConfig.getDBUser());
        }
    }

    private void createDatabase()
    {
        if (!PostgresUtils.exists(rootDbi, dbName)) {
            // Create the database
            PostgresUtils.create(rootDbi, dbName, userConfig.getDBUser());
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="DM_GC", justification="c3p0 bug")
    private void dropDatabase()
    {
        if (exists()) {
            int currentUsers = 0;
            int count = WAIT_USER_COUNT;

            do {
                currentUsers = PostgresUtils.activeUserCount(rootDbi, dbName, userConfig.getDBUser());
                if (currentUsers == 0) {
                    break;
                }

                LOG.warn("****** %d users still accessing %s, trying System.gc() to finalize possibly leaked objects from the c3p0 pool!", currentUsers, dbName);
                System.gc();

                try {
                    Thread.sleep(WAIT_USER_TIME);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } while(count-- > 0 && currentUsers > 0);

            Preconditions.checkState(currentUsers == 0, "There are still %d users using the database %s, can not drop!", currentUsers, dbName);

            PostgresUtils.drop(rootDbi, dbName);
        }
    }

    private void createSchema(final String schemaName)
    {
        if (!PostgresUtils.schemaExists(userAsRootDbi, schemaName)) {
            PostgresUtils.schemaCreate(userAsRootDbi, schemaName);
        }
    }

    private void dropSchema(final String schemaName)
    {
        if (PostgresUtils.schemaExists(userAsRootDbi, schemaName)) {
            PostgresUtils.schemaDrop(userAsRootDbi, schemaName);
        }
    }

    private void revokeSchema(final String schemaName)
    {
        if (PostgresUtils.schemaExists(userAsRootDbi, schemaName) && PostgresUtils.userExists(rootDbi, userConfig.getDBUser())) {
            userAsRootDbi.withHandle(new HandleCallback<Void>() {
                    @Override
                    public Void withHandle(final Handle handle) {
                        // Kill all rights on the "public" schema, so that the user only knows its own schema.
                        handle.createStatement(String.format(REVOKE_SCHEMA, "public", userConfig.getDBUser())).execute();
                        return null;
                    }
                });
        }
    }


    private void createLanguage()
    {
        final boolean languageExists = userAsRootDbi.withHandle(new HandleCallback<Boolean>() {
                @Override
                public Boolean withHandle(final Handle handle) {
                    return handle.createQuery(DETECT_LANG)
                    .map(IntegerMapper.FIRST)
                    .first() != 0;
                }
            });


        if (!languageExists) {
            userAsRootDbi.withHandle(new HandleCallback<Void>() {
                    @Override
                    public Void withHandle(final Handle handle) {
                        handle.createStatement(CREATE_LANG).execute();
                        return null;
                    }
                });
        }
    }
}

