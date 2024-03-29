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

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.configuration.ConfigUtils;

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
    private final Map<String, String> flywayConfiguration;

    public static FlywayPreparer forClasspathLocation(String... locations) {
        return new FlywayPreparer(Arrays.asList(locations), new HashMap<>());
    }

    public static FlywayPreparer forClasspathLocation(Map<String, String> flywayConfiguration, String... locations) {
        return new FlywayPreparer(Arrays.asList(locations), flywayConfiguration);
    }

    private FlywayPreparer(List<String> locations, Map<String, String> flywayConfiguration) {
        this.locations = locations;
        this.flywayConfiguration = flywayConfiguration;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        // Precedence:
        // 1. Method set FlywayPreparer Map.
        // 2. Env vars
        // 3. Class path
        Map<String, String> fromClassPath;
        try (InputStream inputStream = FlywayPreparer.class.getResourceAsStream("/flyway.properties")) {
             fromClassPath = ConfigUtils.loadConfigurationFromInputStream(inputStream);
        } catch (IOException e) {
            throw new SQLException(e);
        }
        flywayConfiguration.putAll(this.flywayConfiguration);
        Flyway.configure()
                .configuration(fromClassPath)
                .envVars()
                .configuration(this.flywayConfiguration)
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
