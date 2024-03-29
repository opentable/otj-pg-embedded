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
import java.util.Properties;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

// TODO: Detect missing migration files.
// cf. https://github.com/flyway/flyway/issues/1496
// There is also a related @Ignored test in otj-sql.
// MJB: This is finally fixed in Flyway 8.41  onwards
// failOnMissingLocations = true, not willing to force that update yet.
/**
 * Support for integrating Flyway and performing a DB migration as part of the setup process.
 */
public final class FlywayPreparer implements DatabasePreparer {

    private final List<String> locations;
    private final Properties flywayConfiguration;

    public static FlywayPreparer forClasspathLocation(String... locations) {
        return new FlywayPreparer(Arrays.asList(locations), new Properties());
    }

    public static FlywayPreparer forClasspathLocation(Properties flywayConfiguration, String... locations) {
        return new FlywayPreparer(Arrays.asList(locations), flywayConfiguration);
    }

    private FlywayPreparer(List<String> locations, Properties flywayConfiguration) {
        this.locations = locations;
        this.flywayConfiguration = flywayConfiguration;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        Flyway.configure()
                .configuration(flywayConfiguration)
                .locations(locations.toArray(new String[0]))
                .dataSource(ds)
                .load()
                .migrate();
    }

    public List<String> getLocations() {
        return locations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)  {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlywayPreparer that = (FlywayPreparer) o;
        return Objects.equals(locations, that.locations) && Objects.equals(flywayConfiguration, that.flywayConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locations, flywayConfiguration);
    }
}
