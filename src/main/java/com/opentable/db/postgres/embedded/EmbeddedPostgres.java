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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressWarnings("PMD.AvoidDuplicateLiterals") // "postgres"
@SuppressFBWarnings({"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"})
// java 11 triggers: https://github.com/spotbugs/spotbugs/issues/756
public class EmbeddedPostgres implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);

    private static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);

    private final PostgreSQLContainer<?> postgreDBContainer;


    private final UUID instanceId = UUID.randomUUID();


    EmbeddedPostgres(File dataDirectory, boolean cleanDataDirectory,
                     Map<String, String> postgresConfig, Map<String, String> localeConfig, Map<String, String> connectConfig,
                     ProcessBuilder.Redirect errorRedirector, ProcessBuilder.Redirect outputRedirector) throws IOException {
        this(dataDirectory, cleanDataDirectory, postgresConfig, localeConfig, connectConfig,
                errorRedirector, outputRedirector, DEFAULT_PG_STARTUP_WAIT, Optional.empty());
    }

    EmbeddedPostgres(File dataDirectory, boolean cleanDataDirectory,
                     Map<String, String> postgresConfig, Map<String, String> localeConfig, Map<String, String> connectConfig,
                     ProcessBuilder.Redirect errorRedirector,
                     ProcessBuilder.Redirect outputRedirector, Duration pgStartupWait,
                     Optional<File> overrideWorkingDirectory) throws IOException {
        this.postgreDBContainer = new PostgreSQLContainer<>("postgres:10.6")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword(null)
                .withStartupTimeout(pgStartupWait)
                .withLogConsumer(new Slf4jLogConsumer(LOG));
        postgreDBContainer.start();
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
        private Optional<File> overrideWorkingDirectory = Optional.empty(); // use tmpdir
        private File builderDataDirectory;
        private final Map<String, String> config = new HashMap<>();
        private final Map<String, String> localeConfig = new HashMap<>();
        private boolean builderCleanDataDirectory = true;
        private final Map<String, String> connectConfig = new HashMap<>();
        private Duration pgStartupWait = DEFAULT_PG_STARTUP_WAIT;

        private ProcessBuilder.Redirect errRedirector = ProcessBuilder.Redirect.PIPE;
        private ProcessBuilder.Redirect outRedirector = ProcessBuilder.Redirect.PIPE;

        Builder() {
            config.put("timezone", "UTC");
            config.put("synchronous_commit", "off");
            config.put("max_connections", "300");
        }

        public Builder setPGStartupWait(Duration pgStartupWait) {
            Objects.requireNonNull(pgStartupWait);
            if (pgStartupWait.isNegative()) {
                throw new IllegalArgumentException("Negative durations are not permitted.");
            }

            this.pgStartupWait = pgStartupWait;
            return this;
        }

        public Builder setCleanDataDirectory(boolean cleanDataDirectory) {
            builderCleanDataDirectory = cleanDataDirectory;
            return this;
        }

        public Builder setDataDirectory(Path path) {
            return setDataDirectory(path.toFile());
        }

        public Builder setDataDirectory(File directory) {
            builderDataDirectory = directory;
            return this;
        }

        public Builder setDataDirectory(String path) {
            return setDataDirectory(new File(path));
        }

        public Builder setServerConfig(String key, String value) {
            config.put(key, value);
            return this;
        }

        public Builder setLocaleConfig(String key, String value) {
            localeConfig.put(key, value);
            return this;
        }

        public Builder setConnectConfig(String key, String value) {
            connectConfig.put(key, value);
            return this;
        }

        public Builder setOverrideWorkingDirectory(File workingDirectory) {
            overrideWorkingDirectory = Optional.ofNullable(workingDirectory);
            return this;
        }

        public Builder setErrorRedirector(ProcessBuilder.Redirect errRedirector) {
            this.errRedirector = errRedirector;
            return this;
        }

        public Builder setOutputRedirector(ProcessBuilder.Redirect outRedirector) {
            this.outRedirector = outRedirector;
            return this;
        }


        public EmbeddedPostgres start() throws IOException {
            return new EmbeddedPostgres(builderDataDirectory, builderCleanDataDirectory, config,
                    localeConfig, connectConfig, errRedirector, outRedirector,
                    pgStartupWait, overrideWorkingDirectory);
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
            return builderCleanDataDirectory == builder.builderCleanDataDirectory &&
                    Objects.equals(builderDataDirectory, builder.builderDataDirectory) &&
                    Objects.equals(config, builder.config) &&
                    Objects.equals(localeConfig, builder.localeConfig) &&
                    Objects.equals(connectConfig, builder.connectConfig) &&
                    Objects.equals(pgStartupWait, builder.pgStartupWait) &&
                    Objects.equals(errRedirector, builder.errRedirector) &&
                    Objects.equals(outRedirector, builder.outRedirector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(builderDataDirectory, config, localeConfig, builderCleanDataDirectory, connectConfig, pgStartupWait, errRedirector, outRedirector);
        }
    }

    @Override
    public String toString() {
        return "EmbeddedPG-" + instanceId;
    }
}
