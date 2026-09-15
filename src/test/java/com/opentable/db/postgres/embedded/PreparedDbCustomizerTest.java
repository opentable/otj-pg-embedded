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

import static com.opentable.db.postgres.embedded.EmbeddedPostgres.DEFAULT_PG_STARTUP_WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.opentable.db.postgres.junit5.EmbeddedPostgresExtension;
import com.opentable.db.postgres.junit5.PreparedDbExtension;

public class PreparedDbCustomizerTest {

    private static final DatabasePreparer EMPTY_PREPARER = ds -> {};

    @RegisterExtension
    static final PreparedDbExtension dbA1 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER);
    @RegisterExtension
    static final PreparedDbExtension dbA2 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER).customize(builder -> {});
    @RegisterExtension
    static final PreparedDbExtension dbA3 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER).customize(builder -> builder.setPGStartupWait(DEFAULT_PG_STARTUP_WAIT));
    @RegisterExtension
    static final PreparedDbExtension dbB1 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER).customize(builder -> builder.setPGStartupWait(Duration.ofSeconds(DEFAULT_PG_STARTUP_WAIT.getSeconds() + 1)));
    @RegisterExtension
    static final PreparedDbExtension dbB2 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER).customize(builder -> builder.setPGStartupWait(Duration.ofSeconds(DEFAULT_PG_STARTUP_WAIT.getSeconds() + 1)));

    @Test
    public void testCustomizers() {
        int dbA1Port = JdbcUrlUtils.getPort(dbA1.getConnectionInfo().getUrl());
        int dbA2Port = JdbcUrlUtils.getPort(dbA2.getConnectionInfo().getUrl());
        int dbA3Port = JdbcUrlUtils.getPort(dbA3.getConnectionInfo().getUrl());

        assertEquals(dbA1Port, dbA2Port);
        assertEquals(dbA1Port, dbA3Port);

        int dbB1Port = JdbcUrlUtils.getPort(dbB1.getConnectionInfo().getUrl());
        int dbB2Port = JdbcUrlUtils.getPort(dbB2.getConnectionInfo().getUrl());

        assertEquals(dbB1Port, dbB2Port);

        assertNotEquals(dbA1Port, dbB2Port);
    }
}
