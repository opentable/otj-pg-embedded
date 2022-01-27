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

/**
 * Basic data holding class to hold the connection information - the url, user, and password
 */
public class ConnectionInfo {
    private final String url;
    private final String user;
    private final String password;
    private final String host;
    private final int port;

    public ConnectionInfo(final String url, final String user, final String password, final String host, final int port) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.host = host;
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public String getUrl() {
        return url;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Prefer getUrl as a general rule over composition using getHost and getPort
     */
    public String getHost() {
        return host;
    }

    /**
     * Prefer getUrl as a general rule over composition using getHost and getPort
     */
    public int getPort() {
        return port;
    }

}
