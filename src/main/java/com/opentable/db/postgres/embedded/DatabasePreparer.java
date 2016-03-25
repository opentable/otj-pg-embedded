package com.opentable.db.postgres.embedded;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabasePreparer {
    void prepare(Connection conn) throws SQLException;
}
