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
import java.util.Random;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;


import com.nesscomputing.db.DatabaseController;
import com.nesscomputing.db.DatabaseControllers;
import com.nesscomputing.db.postgres.PostgresController;
import com.nesscomputing.db.postgres.PostgresUtils;
import com.nesscomputing.migratory.ImmutableMigratoryDBIConfig;
import com.nesscomputing.migratory.MigratoryDBIConfig;
import com.nesscomputing.testing.lessio.AllowDNSResolution;
import com.nesscomputing.testing.lessio.AllowNetworkAccess;


@AllowDNSResolution
@AllowNetworkAccess(endpoints="127.0.0.1:*")
public class TestPostgresController
{
    private MigratoryDBIConfig rootConfig = PostgresUtils.PG_LOCALHOST_ROOT_CONFIG;
    private ImmutableMigratoryDBIConfig userConfig = null;
    private String dbName = null;
    private String userName = null;

    @Before
    public void setUp() throws Exception
    {
        userName = "test" + RandomStringUtils.randomNumeric(8);
        dbName = "test" + RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);

        userConfig = new ImmutableMigratoryDBIConfig(String.format(PostgresUtils.PG_LOCALHOST_TEMPLATE, dbName), userName, "");
    }

    @Test
    public void testCreate()
    {
        final PostgresController manager = new PostgresController(rootConfig, userConfig, true);

        Assert.assertFalse(checkExists(dbName));
        Assert.assertFalse(manager.exists());

        try {
            manager.create();
            Assert.assertTrue(manager.exists());
            Assert.assertTrue(checkExists(dbName));
        }
        finally {
            manager.drop();
            Assert.assertFalse(checkExists(dbName));
            Assert.assertFalse(manager.exists());
        }
    }

    private boolean checkExists(final String dbName)
    {
        final DBI rootDbi = new DBI(rootConfig.getDBUrl(), rootConfig.getDBUser(), rootConfig.getDBPassword());
        return rootDbi.withHandle(new HandleCallback<Boolean>() {
                @Override
                public Boolean withHandle(final Handle handle) {
                    return handle.createQuery("SELECT count(*) FROM pg_database WHERE datname=:database")
                        .bind("database", dbName)
                        .map(IntegerMapper.FIRST)
                        .first() != 0;
                }
            });
    }

    @Test
    public void testSimple()
    {
        final PostgresController manager = new PostgresController(rootConfig, userConfig, true);
        Assert.assertFalse(manager.exists());

        try {
            manager.create();
            Assert.assertTrue(manager.exists());

            final DBI dbi = new DBI(userConfig.getDBUrl(), userConfig.getDBUser(), userConfig.getDBPassword());

            final int testValue = new Random().nextInt();

            final int dbTest = dbi.withHandle(new HandleCallback<Integer>() {
                    @Override
                    public Integer withHandle(final Handle handle) {
                        return handle.createQuery(String.format("SELECT %d", testValue))
                        .map(IntegerMapper.FIRST)
                        .first();
                    }
                });

            Assert.assertEquals(testValue, dbTest);
        }
        finally {
            manager.drop();
            Assert.assertFalse(manager.exists());
        }
    }

    @Test
    public void testDbi() throws Exception
    {
        final DatabaseController manager = DatabaseControllers.forPostgres(rootConfig, userConfig);

        Assert.assertFalse(manager.exists());

        try {
            manager.create();
            final DBI dbi = manager.getDbi();

            final int testValue = new Random().nextInt();

            final int dbTest = dbi.withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery(String.format("SELECT %d", testValue))
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

            Assert.assertEquals(testValue, dbTest);
        }
        finally {
            manager.drop();
        }

        Assert.assertFalse(manager.exists());
    }

    @Test
    public void testTable() throws Exception
    {
        final DatabaseController manager = DatabaseControllers.forPostgres(rootConfig, userConfig);

        Assert.assertFalse(manager.exists());

        try {
            manager.create();
            final DBI dbi = manager.getDbi();

            dbi.withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement("CREATE TABLE foo ( bar CHARACTER VARYING )").execute();
                    return null;
                }
            });

            final int count = dbi.withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery("SELECT count(*) FROM foo")
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

            Assert.assertEquals(0, count);
        }
        finally {
            manager.drop();
        }

        Assert.assertFalse(manager.exists());
    }
}
