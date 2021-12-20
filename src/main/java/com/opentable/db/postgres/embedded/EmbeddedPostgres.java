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


import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("PMD.AvoidDuplicateLiterals") // "postgres"
// java 11 triggers: https://github.com/spotbugs/spotbugs/issues/756
public class EmbeddedPostgres implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);

    private static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);
    public static final DockerImageName POSTGRES = DockerImageName.parse("postgres");

    private final PostgreSQLContainer<?> postgreDBContainer;


    private final UUID instanceId = UUID.randomUUID();


    EmbeddedPostgres(Map<String, String> postgresConfig,
                     Map<String, String> localeConfig,
                     String tag
    ) throws IOException {
        this(postgresConfig, localeConfig, tag, DEFAULT_PG_STARTUP_WAIT);
    }

    EmbeddedPostgres(Map<String, String> postgresConfig,
                     Map<String, String> localeConfig,
                     String tag,
                     Duration pgStartupWait
    ) throws IOException {
        this.postgreDBContainer = new PostgreSQLContainer<>(POSTGRES.withTag(tag))
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword(null)
                .withStartupTimeout(pgStartupWait)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withEnv("POSTGRES_INITDB_ARGS", String.join(" ", createLocaleOptions(localeConfig)));
        final List<String> cmd = new ArrayList<>(Collections.singletonList("postgres"));
        cmd.addAll(createInitOptions(postgresConfig));
        postgreDBContainer.setCommand(cmd.toArray(new String[0]));
        postgreDBContainer.start();
    }

    private List<String> createInitOptions(final Map<String, String> postgresConfig) {
        final List<String> initOptions = new ArrayList<>();
        for (final Map.Entry<String, String> config : postgresConfig.entrySet()) {
            initOptions.add("-c");
            initOptions.add(config.getKey() + "=" + config.getValue());
        }
        return initOptions;
    }

    private List<String> createLocaleOptions(final Map<String, String> localeConfig) {
        final List<String> localeOptions = new ArrayList<>();
        for (final Map.Entry<String, String> config : localeConfig.entrySet()) {
            localeOptions.add("--" + config.getKey());
            localeOptions.add(config.getValue());
        }
        return localeOptions;
    }

    public DataSource getTemplateDatabase() {
        return getDatabase(postgreDBContainer.getUsername(), "template1");
    }

    public DataSource getTemplateDatabase(Map<String, String> properties) {
        return getDatabase(postgreDBContainer.getUsername(), "template1", properties);
    }

    public DataSource getPostgresDatabase() {
        return getDatabase(postgreDBContainer.getUsername(), postgreDBContainer.getDatabaseName());
    }

    public DataSource getPostgresDatabase(Map<String, String> properties) {
        return getDatabase(postgreDBContainer.getUsername(), postgreDBContainer.getDatabaseName(), properties);
    }

    public DataSource getDatabase(String userName, String dbName) {
        return getDatabase(userName, dbName, Collections.emptyMap());
    }

    public DataSource getDatabase(String userName, String dbName, Map<String, String> properties) {
        final PGSimpleDataSource ds = new PGSimpleDataSource();

        ds.setURL(postgreDBContainer.getJdbcUrl());
        ds.setDatabaseName(dbName);
        ds.setUser(userName);
        ds.setPassword(postgreDBContainer.getPassword());

        properties.forEach((propertyKey, propertyValue) -> {
            try {
                ds.setProperty(propertyKey, propertyValue);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        return ds;
    }

    public String getJdbcUrl(String userName, String dbName) {
        return postgreDBContainer.getJdbcUrl();
    }

    public int getPort() {
        return postgreDBContainer.getMappedPort(POSTGRESQL_PORT);
    }

    @Override
    public void close() throws IOException {
        postgreDBContainer.close();
    }

    public static EmbeddedPostgres start() throws IOException {
        return builder().start();
    }

    public static EmbeddedPostgres.Builder builder() {
        return new Builder();
    }

    public String getUserName() {
        return postgreDBContainer.getUsername();
    }

    public String getPassword() {
        return postgreDBContainer.getPassword();
    }

    public static class Builder {
        private final Map<String, String> config = new HashMap<>();
        private final Map<String, String> localeConfig = new HashMap<>();
        private Duration pgStartupWait = DEFAULT_PG_STARTUP_WAIT;

        private String tag = "10.6";

        Builder() {
            config.put("timezone", "UTC");
            config.put("synchronous_commit", "off");
            config.put("max_connections", "300");
            config.put("fsync", "off");
        }

        public Builder setPGStartupWait(Duration pgStartupWait) {
            Objects.requireNonNull(pgStartupWait);
            if (pgStartupWait.isNegative()) {
                throw new IllegalArgumentException("Negative durations are not permitted.");
            }

            this.pgStartupWait = pgStartupWait;
            return this;
        }



        public Builder setServerConfig(String key, String value) {
            config.put(key, value);
            return this;
        }

        public Builder setLocaleConfig(String key, String value) {
            localeConfig.put(key, value);
            return this;
        }

        public Builder setTag(String tag) {
            this.tag = tag;
            return this;
        }

        public EmbeddedPostgres start() throws IOException {
            return new EmbeddedPostgres(config, localeConfig,  tag, pgStartupWait);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Builder builder = (Builder) o;
            return  Objects.equals(config, builder.config) &&
                    Objects.equals(localeConfig, builder.localeConfig) &&
                    Objects.equals(pgStartupWait, builder.pgStartupWait) &&
                    Objects.equals(tag, builder.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config, localeConfig, pgStartupWait, tag);
        }
    }

    @Override
    public String toString() {
        return "EmbeddedPG-" + instanceId;
    }
}
