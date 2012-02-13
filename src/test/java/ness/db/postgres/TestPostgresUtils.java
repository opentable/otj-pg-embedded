package ness.db.postgres;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

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

