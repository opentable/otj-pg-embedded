package ness.db.postgres.junit;


import org.junit.Assert;
import org.junit.Rule;
import org.skife.jdbi.v2.DBI;

import com.google.common.io.Resources;

public class TestLocalPreparerSchemaPerClass extends AbstractPreparerTests
{
    @Rule
    public static final LocalPostgresPreparerTestRule DATABASE = PostgresRules.schemaPreparerRule("trumpet_test", Resources.getResource(PostgresRules.class, "/sql/"), "simple");

    @Override
    public DBI getDbi()
    {
        Assert.assertNotNull(DATABASE);
        final DBI dbi = DATABASE.getDbi();
        Assert.assertNotNull(dbi);
        return dbi;
    }
}
