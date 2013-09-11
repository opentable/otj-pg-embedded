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
package com.nesscomputing.db.postgres.integration;

import java.util.Map;

import com.nesscomputing.db.postgres.embedded.EmbeddedPostgreSQLController;
import com.nesscomputing.db.postgres.junit.EmbeddedPostgresTestDatabaseRule;
import com.nesscomputing.testing.tweaked.TweakedModule;

/**
 * Integration point with ness-integration-testing component.
 */
public class PostgresTweakedModule
{

    /**
     * @return a {@link TweakedModule} which gives services database URLs
     */
    public static TweakedModule getTweakedModule(final EmbeddedPostgresTestDatabaseRule rule, final String dbModuleName)
    {
        return getTweakedModule(rule.getController(), dbModuleName);
    }

    /**
     * @return a {@link TweakedModule} which gives services database URLs
     */
    public static TweakedModule getTweakedModule(final EmbeddedPostgreSQLController controller, final String dbModuleName)
    {
        return new TweakedModule() {
            @Override
            public Map<String, String> getServiceConfigTweaks()
            {
                return controller.getConfigurationTweak(dbModuleName);
            }
        };
    }
}
