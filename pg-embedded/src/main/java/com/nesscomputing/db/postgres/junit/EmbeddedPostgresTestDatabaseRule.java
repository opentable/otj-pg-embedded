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

import java.net.URI;

import org.junit.rules.ExternalResource;

import com.nesscomputing.config.Config;
import com.nesscomputing.db.postgres.embedded.EmbeddedPostgreSQLController;
import com.nesscomputing.testing.lessio.AllowAll;
import com.nesscomputing.testing.tweaked.TweakedModule;

@AllowAll
public class EmbeddedPostgresTestDatabaseRule extends ExternalResource
{
    private final EmbeddedPostgreSQLController control;

    EmbeddedPostgresTestDatabaseRule(URI baseUrl, String[] personalities)
    {
        control = new EmbeddedPostgreSQLController(baseUrl, personalities);
    }

    /**
     * Override a {@link Config} to set <code>ness.db.[db-name].uri</code> to a unique
     * database in the cluster.
     */
    public Config getTweakedConfig(Config config, String dbModuleName)
    {
        return control.getTweakedConfig(config, dbModuleName);
    }


    /**
     * Shorthand for <code>getTweakedConfig(Config.getEmptyConfig(), dbModuleName)</code>.
     */
    public Config getTweakedConfig(String dbModuleName)
    {
        return control.getTweakedConfig(dbModuleName);
    }

    /**
     * @return a {@link TweakedModule} which gives services database URLs
     */
    public TweakedModule getTweakedModule(final String dbModuleName)
    {
        return control.getTweakedModule(dbModuleName);
    }
}
