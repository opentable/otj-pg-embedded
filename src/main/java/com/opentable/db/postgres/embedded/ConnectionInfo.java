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

public class ConnectionInfo {
    private final String dbName;
    private final int port;
    private final String user;

    public ConnectionInfo(final String dbName, final int port, final String user) {
        this.dbName = dbName;
        this.port = port;
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    public String getDbName() {
        return dbName;
    }

    public int getPort() {
        return port;
    }
}
