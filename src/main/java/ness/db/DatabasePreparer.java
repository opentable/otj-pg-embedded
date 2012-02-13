package ness.db;

import javax.annotation.Nonnull;

import org.skife.jdbi.v2.DBI;

import com.google.inject.Module;

/**
 * Prepares databases with personalities.
 */
public interface DatabasePreparer
{
    /**
     * Creates the database and loads the listed personalities into it.
     *
     * @param personalities
     */
    void setupDatabase(String ... personalities);

    /**
     * Remove the database.
     */
    void teardownDatabase();

    /**
     * Returns true if the database exists and the personalities have been loaded successfully.
     *
     * @return
     */
    boolean exists();

    /**
     * Returns a DBI connected to the database. The database must have been created before.
     * @return
     */
    DBI getDbi();

    /**
     * Return a Guice module to bind the database represented by this controller. The module will
     * be bound using the visibleName. If the database does not exist, it will be created.
     *
     * This is not the actual JDBC module! The JDBC module must still be bound. This module will modify
     * a JDBC module bound with the same name to point at the test database.
     *
     * @param visibleDbName The name used to bind all elements in the guice module. This is not the name of the physical database instance.
     * @return A guice module that can be installed.
     */
    Module getGuiceModule(@Nonnull final String visibleDbName);
}
