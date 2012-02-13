package ness.db.postgres.junit;


import org.junit.Assert;
import org.junit.Rule;
import org.skife.jdbi.v2.DBI;

public class TestLocalControllerDatabasePerMethod extends AbstractControllerTests
{
    @Rule
    public final LocalPostgresControllerTestRule database = PostgresRules.databaseControllerRule();

    @Override
    public DBI getDbi()
    {
        Assert.assertNotNull(database);
        final DBI dbi = database.getDbi();
        Assert.assertNotNull(dbi);
        return dbi;
    }
}
