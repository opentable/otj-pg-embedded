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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.Rule;
import org.junit.Test;

import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;

public class PreparedDbTest {

    private final DatabasePreparer prepA = new SimplePreparer("a");
    private final DatabasePreparer prepB = new SimplePreparer("b");

    @Rule
    public PreparedDbRule dbA1 = EmbeddedPostgresRules.preparedDatabase(prepA);
    @Rule
    public PreparedDbRule dbA2 = EmbeddedPostgresRules.preparedDatabase(prepA);
    @Rule
    public PreparedDbRule dbB1 = EmbeddedPostgresRules.preparedDatabase(prepB);

    @Test
    public void testDbs() throws Exception {
        try (Connection c = dbA1.getTestDatabase().getConnection();
                Statement stmt = c.createStatement()) {
            commonAssertion(stmt);
        }
        try (Connection c = dbA2.getTestDatabase().getConnection();
                PreparedStatement stmt = c.prepareStatement("SELECT count(1) FROM a")) {
            ResultSet rs = stmt.executeQuery();
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
        try (Connection c = dbB1.getTestDatabase().getConnection();
                PreparedStatement stmt = c.prepareStatement("SELECT * FROM b")) {
            stmt.execute();
        }
    }

    private void commonAssertion(final Statement stmt) throws SQLException {
        stmt.execute("INSERT INTO a VALUES(1)");
        ResultSet rs = stmt.executeQuery("SELECT COUNT(1) FROM a");
        rs.next();
        assertEquals(1, rs.getInt(1));
    }

    @Test
    public void testEquivalentAccess() throws SQLException {
        ConnectionInfo dbInfo = dbA1.getConnectionInfo();
        DataSource dataSource = dbA1.getTestDatabase();
        try (Connection c = dataSource.getConnection(); Statement stmt = c.createStatement()) {
            commonAssertion(stmt);
            assertEquals(dbInfo.getUser(), c.getMetaData().getUserName());
        }
    }

    @Test
    public void testDbUri() throws Exception {
        try (Connection c = DriverManager.getConnection(dbA1.getDbProvider().createDatabase());
             Statement stmt = c.createStatement()) {
            commonAssertion(stmt);
        }
    }

    static class SimplePreparer implements DatabaseConnectionPreparer {
        private final String name;

        public SimplePreparer(String name) {
            this.name = name;
        }

        @Override
        public void prepare(Connection conn) throws SQLException {
            try (PreparedStatement stmt = conn.prepareStatement(String.format(
                    "CREATE TABLE %s (foo int)", name))) {
                stmt.execute();
            }
        }
    }
}
