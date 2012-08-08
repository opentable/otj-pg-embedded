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
package com.nesscomputing.db.postgres.junit;

import javax.annotation.Nonnull;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skife.jdbi.v2.DBI;

import com.google.inject.Module;
import com.nesscomputing.db.DatabaseController;
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

    public Module getJdbcModule(@Nonnull final String visibleDbName, final Module jdbcModule)
    {
        return databaseController.getJdbcModule(visibleDbName, jdbcModule);
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
