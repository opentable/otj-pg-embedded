package ness.db.postgres;

import static ness.db.postgres.PostgresUtils.PG_LOCALHOST_ROOT_CONFIG;
import static ness.db.postgres.PostgresUtils.PG_LOCALHOST_TEMPLATE;

import java.util.List;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.StringMapper;

/**
 * Testcode to clean out a local database. Drops all databases starting with "test", removes all roles starting with "test" and drops all schemas starting with "test" from trumpet_test.
 *
 * DO NOT RUN UNLESS YOU KNOW WHAT YOU DO.
 */
public final class PostgresCleaner
{
    PostgresCleaner()
    {
    }

    public static void main(final String ... args) throws Exception
    {
        final PostgresCleaner cleaner = new PostgresCleaner();
        cleaner.clean();
    }

    void clean() throws Exception
    {
        final DBI dbi = new DBI(PG_LOCALHOST_ROOT_CONFIG.getDBUrl(), PG_LOCALHOST_ROOT_CONFIG.getDBUser(), PG_LOCALHOST_ROOT_CONFIG.getDBPassword());

        final List<String> testDatabases = dbi.withHandle(new HandleCallback<List<String>>() {
                @Override
                public List<String> withHandle(final Handle handle) {
                    return handle.createQuery("SELECT datname FROM pg_database WHERE datname like 'test%'")
                    .map(StringMapper.FIRST)
                    .list();
                }
            });

        for (String testDatabase : testDatabases) {
            PostgresUtils.drop(dbi, testDatabase);
        }

        if(PostgresUtils.exists(dbi, "trumpet_test")) {

            final DBI ttDbi = new DBI(String.format(PG_LOCALHOST_TEMPLATE, "trumpet_test"), PG_LOCALHOST_ROOT_CONFIG.getDBUser(), PG_LOCALHOST_ROOT_CONFIG.getDBPassword());

            final List<String> testSchemas = ttDbi.withHandle(new HandleCallback<List<String>>() {
                @Override
                public List<String> withHandle(final Handle handle) {
                    return handle.createQuery("SELECT nspname FROM pg_namespace WHERE nspname like 'test%'")
                    .map(StringMapper.FIRST)
                    .list();
                }
            });


            for (String testSchema : testSchemas) {
                PostgresUtils.schemaDrop(ttDbi, testSchema);
            }
        }

        final List<String> testRoles = dbi.withHandle(new HandleCallback<List<String>>() {
                @Override
                public List<String> withHandle(final Handle handle) {
                    return handle.createQuery("SELECT rolname FROM pg_roles WHERE rolname like 'test%'")
                    .map(StringMapper.FIRST)
                    .list();
                }
            });

        for (String testRole : testRoles) {
            PostgresUtils.userDrop(dbi, testRole);
        }
    }
}







