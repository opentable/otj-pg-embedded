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
package com.opentable.db.postgres.junit5;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

/*
    Implementing AfterTestExecutionCallback and BeforeTestExecutionCallback does not work if you want to use the EmbeddedPostgres in a @BeforeEach
    or @BeforeAll method because it isn't instantiated then.

    The order in which the methods are called with  BeforeTestExecutionCallback is:
        @BeforeAll method of the test class
        @BeforeEach method of the test class
        beforeTestExecution(ExtensionContext) method of
        SingleInstancePostgresExtension
        Actual test method of the test class

    And using BeforeAllCallback instead it will be:
        beforeAll(ExtensionContext) method of  SingleInstancePostgresExtension
        @BeforeAll method of the test class
        @BeforeEach method of the test class
        Actual test method of the test class

  See:     https://github.com/opentable/otj-pg-embedded/pull/138.
  Credits: https://github.com/qutax

 */
public class SingleInstancePostgresExtension implements AfterAllCallback, BeforeAllCallback {

    private volatile EmbeddedPostgres epg;
    private volatile Connection postgresConnection;
    private final List<Consumer<EmbeddedPostgres.Builder>> builderCustomizers = new CopyOnWriteArrayList<>();

    SingleInstancePostgresExtension() { }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        epg = pg();
        postgresConnection = epg.getPostgresDatabase().getConnection();
    }

    private EmbeddedPostgres pg() throws IOException {
        final EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
        builderCustomizers.forEach(c -> c.accept(builder));
        return builder.start();
    }

    public SingleInstancePostgresExtension customize(Consumer<EmbeddedPostgres.Builder> customizer) {
        if (epg != null) {
            throw new AssertionError("already started");
        }
        builderCustomizers.add(customizer);
        return this;
    }

    public EmbeddedPostgres getEmbeddedPostgres()
    {
        EmbeddedPostgres epg = this.epg;
        if (epg == null) {
            throw new AssertionError("JUnit test not started yet!");
        }
        return epg;
    }

    @Override
    public void afterAll(ExtensionContext context) {
        try {
            postgresConnection.close();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
        try {
            epg.close();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
