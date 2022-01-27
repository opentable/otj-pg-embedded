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
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

/**
 * Core class of the library, providing a builder (with reasonable defaults) to wrap
 * testcontainers and launch postgres container.
 */
public class EmbeddedPostgres implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);

    static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(30);
    static final String POSTGRES = "postgres";

    // There are 3 defaults.
    // 1) If this is defined, then it's assumed this contains the full image and tag...
    static final String ENV_DOCKER_IMAGE="PG_FULL_IMAGE";
    // 2)Otherwise if this is defined, we'll use this as the prefix, and combine with the DOCKER_DEFAULT_TAG below
    // This is already used in TestContainers as a env var, so it's useful to reuse for consistency.
    static final String ENV_DOCKER_PREFIX = "TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX";
    // 3) Otherwise we'll just pull from docker hub with the DOCKER_DEFAULT_TAG
    static final DockerImageName DOCKER_DEFAULT_IMAGE_NAME = DockerImageName.parse(POSTGRES);
    static final String DOCKER_DEFAULT_TAG = "13-alpine";
    // Note you can override any of these defaults explicitly in the builder.

    private final PostgreSQLContainer<?> postgreDBContainer;

    private final UUID instanceId = UUID.randomUUID();


    EmbeddedPostgres(Map<String, String> postgresConfig,
                     Map<String, String> localeConfig,
                     DockerImageName image,
                     String databaseName
    ) throws IOException {
        this(postgresConfig, localeConfig, image,  DEFAULT_PG_STARTUP_WAIT, databaseName);
    }

    EmbeddedPostgres(Map<String, String> postgresConfig,
                     Map<String, String> localeConfig,
                     DockerImageName image,
                     Duration pgStartupWait,
                     String databaseName
    ) throws IOException {
        LOG.trace("Starting containers with image {}, pgConfig {}, localeConfig {}, pgStartupWait {}, dbName {}", image,
                postgresConfig, localeConfig, pgStartupWait, databaseName);
        image = image.asCompatibleSubstituteFor(POSTGRES);
        this.postgreDBContainer = new PostgreSQLContainer<>(image)
                .withDatabaseName(databaseName)
                .withUsername(POSTGRES)
                .withPassword(POSTGRES)
                .withStartupTimeout(pgStartupWait)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                // https://github.com/docker-library/docs/blob/master/postgres/README.md#postgres_initdb_args
                .withEnv("POSTGRES_INITDB_ARGS", String.join(" ", createInitOptions(localeConfig)))
                .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
        final List<String> cmd = new ArrayList<>(Collections.singletonList(POSTGRES));
        cmd.addAll(createConfigOptions(postgresConfig));
        postgreDBContainer.setCommand(cmd.toArray(new String[0]));
        postgreDBContainer.start();
    }

    private List<String> createConfigOptions(final Map<String, String> postgresConfig) {
        final List<String> configOptions = new ArrayList<>();
        for (final Map.Entry<String, String> config : postgresConfig.entrySet()) {
            configOptions.add("-c");
            configOptions.add(config.getKey() + "=" + config.getValue());
        }
        return configOptions;
    }

    private List<String> createInitOptions(final Map<String, String> localeConfig) {
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

    /**
     * Returns JDBC connection string for specified database
     * @param dbName Database name
     * @return URL
     */
    public String getJdbcUrl(String dbName) {
        try {
            return JdbcUrlUtils.replaceDatabase(postgreDBContainer.getJdbcUrl(), dbName);
        } catch (URISyntaxException e) {
            return null;
        }
     }

     public String getHost() {
        return postgreDBContainer.getContainerIpAddress();
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

        private DockerImageName image = getDefaultImage();
        private String databaseName = POSTGRES;

        // See comments at top for the logic.
        DockerImageName getDefaultImage() {
            if (getEnvOrProperty(ENV_DOCKER_IMAGE) != null) {
                return DockerImageName.parse(getEnvOrProperty(ENV_DOCKER_IMAGE));
            }
            if (getEnvOrProperty(ENV_DOCKER_PREFIX) != null) {
                return DockerImageName.parse(insertSlashIfNeeded(getEnvOrProperty(ENV_DOCKER_PREFIX),POSTGRES)).withTag(DOCKER_DEFAULT_TAG);
            }
            return DOCKER_DEFAULT_IMAGE_NAME.withTag(DOCKER_DEFAULT_TAG);
        }

        String getEnvOrProperty(String key) {
            return Optional.ofNullable(System.getenv(key)).orElse(System.getProperty(key));
        }

        String insertSlashIfNeeded(String prefix, String repo) {
            if ((prefix.endsWith("/")) || (repo.startsWith("/"))) {
                return prefix + repo;
            }
            return prefix + "/" + repo;
        }

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

        public Builder setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }
        public Builder setLocaleConfig(String key, String value) {
            localeConfig.put(key, value);
            return this;
        }

        public Builder setImage(DockerImageName image) {
            this.image = image;
            return this;
        }

        public Builder setTag(String tag) {
            this.image = this.image.withTag(tag);
            return this;
        }

        DockerImageName getImage() {
            return image;
        }

        public EmbeddedPostgres start() throws IOException {
            return new EmbeddedPostgres(config, localeConfig,  image, pgStartupWait, databaseName);
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
                    Objects.equals(image, builder.image);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config, localeConfig, pgStartupWait, image);
        }
    }

    @Override
    public String toString() {
        return "EmbeddedPG-" + instanceId;
    }
}
