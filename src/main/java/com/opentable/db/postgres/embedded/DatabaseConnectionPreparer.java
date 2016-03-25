package com.opentable.db.postgres.embedded;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

public interface DatabaseConnectionPreparer extends DatabasePreparer {

    @Override
    default void prepare(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            prepare(c);
        }
    }

    void prepare(Connection conn) throws SQLException;
}
