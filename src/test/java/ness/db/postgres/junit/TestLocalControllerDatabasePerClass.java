package ness.db.postgres.junit;


import org.junit.Assert;
import org.junit.Rule;
import org.skife.jdbi.v2.DBI;

public class TestLocalControllerDatabasePerClass extends AbstractControllerTests
{
    @Rule
    public static final LocalPostgresControllerTestRule DATABASE = PostgresRules.databaseControllerRule();

    @Override
    public DBI getDbi()
    {
        Assert.assertNotNull(DATABASE);
        final DBI dbi = DATABASE.getDbi();
        Assert.assertNotNull(dbi);
        return dbi;
    }
}
