package ness.db.postgres.junit;

import javax.annotation.Nonnull;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skife.jdbi.v2.DBI;

import ness.db.DatabasePreparer;

import com.google.inject.Module;
import com.nesscomputing.testing.lessio.AllowDNSResolution;
import com.nesscomputing.testing.lessio.AllowNetworkAccess;

public class LocalPostgresPreparerTestRule implements TestRule
{
    private final DatabasePreparer databasePreparer;
    private final String [] personalities;

    LocalPostgresPreparerTestRule(final DatabasePreparer databasePreparer, final String ... personalities)
    {
        this.databasePreparer = databasePreparer;
        this.personalities = personalities;
    }

    @Override
    public Statement apply(final Statement base, final Description description)
    {
        return new PreparerStatement(base);
    }

    public DBI getDbi()
    {
        return databasePreparer.getDbi();
    }

    public Module getGuiceModule(@Nonnull final String visibleDbName)
    {
        return databasePreparer.getGuiceModule(visibleDbName);
    }

    @AllowDNSResolution
    @AllowNetworkAccess(endpoints="127.0.0.1:*")
    public class PreparerStatement extends Statement
    {
        private final Statement delegate;

        PreparerStatement(final Statement delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                databasePreparer.setupDatabase(personalities);
                delegate.evaluate();
            }
            finally {
                databasePreparer.teardownDatabase();
            }
        }
    }

}
