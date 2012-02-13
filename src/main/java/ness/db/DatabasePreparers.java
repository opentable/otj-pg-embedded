package ness.db;

import java.net.URI;

import javax.annotation.Nonnull;

import ness.db.postgres.PostgresPreparer;

/**
 * Gives access to the Database preparers.
 */
public final class DatabasePreparers
{
    private DatabasePreparers()
    {
    }

    /**
     * Returns a database preparer that manages a Postgres database.
     * @param baseUri BaseUri to load migratory sql files.
     */
    public static DatabasePreparer forPostgres(@Nonnull final URI baseUri)
    {
        return PostgresPreparer.getDatabasePreparer(baseUri);
    }

    /**
     * Returns a database preparer that manages a schema inside a Postgres database.
     * @param dbName The database to prepare.
     * @param baseUri BaseUri to load migratory sql files.
     */
    public static DatabasePreparer forPostgresSchema(@Nonnull final String dbName, @Nonnull final URI baseUri)
    {
        return PostgresPreparer.getSchemaPreparer(dbName, baseUri);
    }
}
