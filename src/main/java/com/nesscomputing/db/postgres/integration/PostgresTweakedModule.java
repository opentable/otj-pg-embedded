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
