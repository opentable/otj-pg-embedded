package ness.db.postgres.junit;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;

public abstract class AbstractControllerTests
{
    public abstract DBI getDbi();

    @Test
    public void testSimple() throws Exception
    {
        final int testValue = new Random().nextInt();

        final int dbTest = getDbi().withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery(String.format("SELECT %d", testValue))
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

        Assert.assertEquals(testValue, dbTest);
    }

    @Test
    public void testTable() throws Exception
    {
        getDbi().withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(final Handle handle) {
                    handle.createStatement("CREATE TABLE foo ( bar CHARACTER VARYING )").execute();
                    return null;
                }
            });

        final int count = getDbi().withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery("SELECT count(*) FROM foo")
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

        Assert.assertEquals(0, count);
    }
}
