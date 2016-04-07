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

import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

public class FlywayPreparer implements DatabasePreparer {

    private final Flyway flyway;

    public static FlywayPreparer forClasspathLocation(String... locations) {
        Flyway f = new Flyway();
        f.setLocations(locations);
        return new FlywayPreparer(f);
    }

    private FlywayPreparer(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        flyway.setDataSource(ds);
        flyway.migrate();
    }
}
