package ness.db.postgres.junit;


import org.junit.Assert;
import org.junit.Rule;
import org.skife.jdbi.v2.DBI;

public class TestLocalControllerSchemaPerClass extends AbstractControllerTests
{
    @Rule
    public static final LocalPostgresControllerTestRule DATABASE = PostgresRules.schemaControllerRule("trumpet_test");

    @Override
    public DBI getDbi()
    {
        Assert.assertNotNull(DATABASE);
        final DBI dbi = DATABASE.getDbi();
        Assert.assertNotNull(dbi);
        return dbi;
    }
}
