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

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;

/*
    This is a legacy version of this class that implements the old lifecycle behavior.
    Late in our testing for 1.0, we ran into a test case where the new EmbeddedPostgresExtension
    failed to work properly.

    This is DEPRECATED until we figure out what's going on ;) and can be removed
    in a minor version. Until then this is a workaround should anyone else run into this.

 */
@Deprecated
public class LegacySingleInstancePostgresExtension implements AfterTestExecutionCallback, BeforeTestExecutionCallback {

    private volatile EmbeddedPostgres epg;
    private volatile Connection postgresConnection;
    private final List<Consumer<EmbeddedPostgres.Builder>> builderCustomizers = new CopyOnWriteArrayList<>();

    LegacySingleInstancePostgresExtension() { }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        epg = pg();
        postgresConnection = epg.getPostgresDatabase().getConnection();
    }

    private EmbeddedPostgres pg() throws IOException {
        final EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
        builderCustomizers.forEach(c -> c.accept(builder));
        return builder.start();
    }

    public LegacySingleInstancePostgresExtension customize(Consumer<EmbeddedPostgres.Builder> customizer) {
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
    public void afterTestExecution(ExtensionContext context) {
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
