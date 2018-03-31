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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import org.junit.Test;

public class CustomPreparedDbProviderTest {
    @Test
    public void testTablesMade() throws Exception {
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder().setPGStartupWait(Duration.ofSeconds(20));
        PreparedDbProvider dbProvider = PreparedDbProvider.forPreparerWithBuilder(FlywayPreparer.forClasspathLocation("db/testing"), builder);
        try (Connection c = dbProvider.createDataSource().getConnection();
                Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT * FROM foo");
            rs.next();
            assertEquals("bar", rs.getString(1));
        }
    }
}
