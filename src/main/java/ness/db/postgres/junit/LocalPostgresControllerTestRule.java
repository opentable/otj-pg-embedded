package ness.db.postgres.junit;

import javax.annotation.Nonnull;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skife.jdbi.v2.DBI;

import ness.db.DatabaseController;

import com.google.inject.Module;
import com.nesscomputing.testing.lessio.AllowDNSResolution;
import com.nesscomputing.testing.lessio.AllowNetworkAccess;

public class LocalPostgresControllerTestRule implements TestRule
{
    private final DatabaseController databaseController;

    LocalPostgresControllerTestRule(final DatabaseController databaseController)
    {
        this.databaseController = databaseController;
    }

    @Override
    public Statement apply(final Statement base, final Description description)
    {
        return new ControllerStatement(base);
    }

    public DBI getDbi()
    {
        return databaseController.getDbi();
    }

    public Module getGuiceModule(@Nonnull final String visibleDbName)
    {
        return databaseController.getGuiceModule(visibleDbName);
    }

    @AllowDNSResolution
    @AllowNetworkAccess(endpoints="127.0.0.1:*")
    public class ControllerStatement extends Statement
    {
        private final Statement delegate;

        ControllerStatement(final Statement delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                databaseController.create();
                delegate.evaluate();
            }
            finally {
                databaseController.drop();
            }
        }
    }
}
