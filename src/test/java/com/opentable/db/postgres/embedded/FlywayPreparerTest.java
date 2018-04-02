/*
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
package com.opentable.db.postgres.embedded;

import static org.junit.Assert.assertEquals;

import com.opentable.db.postgres.embedded.utils.FlywayConfig;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import java.text.MessageFormat;
import org.flywaydb.core.Flyway;
import org.junit.Rule;
import org.junit.Test;

import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;

public class FlywayPreparerTest {

    private Flyway configuredFlyway = FlywayConfig.get();

    @Rule
    public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(FlywayPreparer.forClasspathLocation("db/testing"));

    @Rule
    public PreparedDbRule customDb = EmbeddedPostgresRules.preparedDatabase(FlywayPreparer.forFlyway(configuredFlyway));

    private String getCustomQuery() {
        String schemaName = configuredFlyway.getPlaceholders().get("schemaName");
        String tableName = configuredFlyway.getPlaceholders().get("tableName");
        return MessageFormat.format("SELECT * FROM {0}.{1}", schemaName, tableName);
    }

    @Test
    public void testTablesMade() throws Exception {
        try (Connection c = db.getTestDatabase().getConnection();
                Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT * FROM foo");
            rs.next();
            assertEquals("bar", rs.getString(1));
        }
    }

    @Test
    public void testCustomTablesMade() throws Exception {
        try (Connection c = customDb.getTestDatabase().getConnection();
            Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(getCustomQuery());
            rs.next();
            assertEquals(configuredFlyway.getPlaceholders().get("tableValue"), rs.getString(1));
        }
    }


}
