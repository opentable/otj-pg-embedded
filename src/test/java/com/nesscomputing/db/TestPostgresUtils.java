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

import org.junit.Assert;
import org.junit.Test;

import com.nesscomputing.db.postgres.PostgresUtils;
import com.nesscomputing.migratory.ImmutableMigratoryDBIConfig;

public class TestPostgresUtils
{
    @Test
    public void testSimpleUri()
    {
        final ImmutableMigratoryDBIConfig cfg = new ImmutableMigratoryDBIConfig("jdbc:postgresql://localhost/test", "hello", "world");

        Assert.assertEquals("jdbc:postgresql://localhost/test", cfg.getDBUrl());
        Assert.assertEquals("localhost", PostgresUtils.getHostFromUri(cfg.getDBUrl()));
        Assert.assertEquals("test", PostgresUtils.getDatabaseFromUri(cfg.getDBUrl()));
    }

    @Test
    public void testComplicatedUri()
    {
        final URI uri = URI.create("jdbc:postgresql://localhost/test?user=foo&password=bar");

        Assert.assertEquals("localhost", PostgresUtils.getHostFromUri(uri));
        Assert.assertEquals("test", PostgresUtils.getDatabaseFromUri(uri));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonPostgres()
    {
        final URI uri = URI.create("jdbc:h2:mem");
        PostgresUtils.getHostFromUri(uri);
    }

    @Test(expected = IllegalStateException.class)
    public void testBadPostgres()
    {
        final URI uri = URI.create("jdbc:postgresql://foo/bar/baz?bla=foo");
        PostgresUtils.getHostFromUri(uri);
    }
}

