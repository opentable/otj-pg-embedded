package com.opentable.db.postgres.embedded;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Rule;
import org.junit.Test;

import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.PreparedDbRule;

public class FlywayPreparerTest {
    @Rule
    public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(FlywayPreparer.forClasspathLocation("db/testing"));

    @Test
    public void testTablesMade() throws Exception {
        try (Connection c = db.getTestDatabase().getConnection();
                Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT * FROM foo");
            rs.next();
            assertEquals("bar", rs.getString(1));
        }
    }
}
