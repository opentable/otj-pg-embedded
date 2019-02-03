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

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import static liquibase.database.DatabaseFactory.getInstance;

public final class LiquibasePreparer implements DatabasePreparer {

    private final String location;
    private final Contexts contexts;

    public static LiquibasePreparer forClasspathLocation(String location) {
        return new LiquibasePreparer(location, new Contexts());
    }
    public static LiquibasePreparer forClasspathLocation(String location, Contexts contexts) {
        return new LiquibasePreparer(location, contexts);
    }

    private LiquibasePreparer(String location, Contexts contexts) {
        this.location = location;
        this.contexts = contexts;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        Connection connection = null;
        try {
            connection = ds.getConnection();
            Database database = getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(location, new ClassLoaderResourceAccessor(), database);
            liquibase.update(contexts);
        } catch (LiquibaseException e) {
            throw new SQLException(e);
        } finally {
            if (connection != null) {
                connection.rollback();
                connection.close();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LiquibasePreparer && Objects.equals(location, ((LiquibasePreparer) obj).location);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(location);
    }
}
