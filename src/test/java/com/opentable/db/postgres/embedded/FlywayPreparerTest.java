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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;

import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;

public class FlywayPreparerTest {
    private static final Map<String, String> flywayConfiguration = new HashMap<>();
    static {
        flywayConfiguration.put("flyway.postgresql.transactional.lock", "false");
    }
    @Rule
    public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(
            FlywayPreparer.forClasspathLocation(flywayConfiguration, "db/testing")
    );

    @Test
    public void testTablesMade() throws Exception {
        try (Connection c = db.getTestDatabase().getConnection();
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT * FROM foo")) {
                rs.next();
                assertEquals("bar", rs.getString(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT table_name\n" +
                    "  FROM information_schema.tables\n" +
                    " WHERE table_schema='public'\n" +
                    "   AND table_type='BASE TABLE'")) {
                Set<String> tableNames = new HashSet<>();
                while(rs.next()) {
                    tableNames.add(rs.getString(1));
                }
                assertTrue(tableNames.contains("foo"));
                assertTrue(tableNames.contains("flyway_schema_history"));
            }


        }
    }
}
