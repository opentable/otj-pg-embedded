package com.opentable.db.postgres.embedded;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A DatabasePreparer applies an arbitrary set of changes
 * (e.g. database migrations, user creation) to a database
 * before it is presented to the user.
 *
 * The preparation steps are expected to be deterministic.
 * For efficiency reasons, databases created by DatabasePreparer
 * instances may be pooled, using {@link #hashCode()} and
 * {@link #equals(Object)} to determine equivalence.
 */
public interface DatabasePreparer {
    void prepare(Connection conn) throws SQLException;
}
