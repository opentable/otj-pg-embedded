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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

// TODO: Detect missing migration files.
// cf. https://github.com/flyway/flyway/issues/1496
// There is also a related @Ignored test in otj-sql.

public final class FlywayPreparer implements DatabasePreparer {

    private final Flyway flyway;
    private final List<String> locations;

    public static FlywayPreparer forClasspathLocation(String... locations) {
        Flyway f = new Flyway();
        f.setLocations(locations);
        return new FlywayPreparer(f, Arrays.asList(locations));
    }

    public static FlywayPreparer forFlyway(Flyway flyway){
        return new FlywayPreparer(flyway, Arrays.asList(flyway.getLocations()));
    }

    private FlywayPreparer(Flyway flyway, List<String> locations) {
        this.flyway = flyway;
        this.locations = locations;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        flyway.setDataSource(ds);
        flyway.migrate();
    }

    @Override
    public boolean equals(Object obj) {
        if (! (obj instanceof FlywayPreparer)) {
            return false;
        }
        return Objects.equals(locations, ((FlywayPreparer) obj).locations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(locations);
    }
}
