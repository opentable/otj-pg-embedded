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

import com.opentable.db.postgres.embedded.ConnectionInfo;
import com.opentable.db.postgres.embedded.DatabasePreparer;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import com.opentable.db.postgres.embedded.PreparedDbProvider;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class PreparedDbExtension implements BeforeAllCallback, AfterAllCallback {

    private final DatabasePreparer preparer;
    private volatile DataSource dataSource;
    private volatile PreparedDbProvider provider;
    private volatile ConnectionInfo connectionInfo;

    private final List<Consumer<EmbeddedPostgres.Builder>> builderCustomizers = new CopyOnWriteArrayList<>();

    PreparedDbExtension(DatabasePreparer preparer) {
        if (preparer == null) {
            throw new IllegalStateException("null preparer");
        }
        this.preparer = preparer;
    }

    public PreparedDbExtension customize(Consumer<EmbeddedPostgres.Builder> customizer) {
        if (dataSource != null) {
            throw new AssertionError("already started");
        }
        builderCustomizers.add(customizer);
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        provider = PreparedDbProvider.forPreparer(preparer, builderCustomizers);
        connectionInfo = provider.createNewDatabase();
        dataSource = provider.createDataSourceFromConnectionInfo(connectionInfo);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        dataSource = null;
        connectionInfo = null;
        provider = null;
    }

    public DataSource getTestDatabase() {
        if (dataSource == null) {
            throw new AssertionError("not initialized");
        }
        return dataSource;
    }

    public ConnectionInfo getConnectionInfo() {
        if (connectionInfo == null) {
            throw new AssertionError("not initialized");
        }
        return connectionInfo;
    }

    public PreparedDbProvider getDbProvider() {
        if (provider == null) {
            throw new AssertionError("not initialized");
        }
        return provider;
    }

}
