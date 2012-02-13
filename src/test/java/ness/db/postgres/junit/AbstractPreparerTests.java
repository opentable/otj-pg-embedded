package ness.db.postgres.junit;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;

public abstract class AbstractPreparerTests
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
    public void testMigratory() throws Exception
    {
        final int dbTest = getDbi().withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery("SELECT COUNT(1) FROM migratory_metadata")
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

        Assert.assertEquals(2, dbTest);
    }
}
