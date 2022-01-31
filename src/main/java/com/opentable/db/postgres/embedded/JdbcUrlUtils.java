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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class JdbcUrlUtils {

    static final String JDBC_URL_PREFIX = "jdbc:";

    private JdbcUrlUtils() {
    }

    /**
     * Extracts port from JDBC url
     *
     * @param url JDBC url
     * @return
     * The port component of this URI, or -1 if the port is undefined
     */
    static int getPort(final String url) {
        return URI.create(url.substring(JDBC_URL_PREFIX.length())).getPort();
    }

    /**
     * Adds Username/Password to the JDBC url (in postgres format)
     *
     * @param url      JDBC url
     * @param userName User name
     * @param password Password
     * @return Modified Url
     * @throws URISyntaxException If Url violates RFC&nbsp;2396
     */
    static String addUsernamePassword(final String url, final String userName, final String password) throws URISyntaxException, UnsupportedEncodingException {
        final URI uri = URI.create(url.substring(JDBC_URL_PREFIX.length()));
        final Map<String, String> params = new HashMap<>(
                Optional.ofNullable(uri.getQuery())
                        .map(Stream::of).orElse(Stream.empty())// Hack around the fact Optional.Stream requires Java 9+.
                        .flatMap(par -> Arrays.stream(par.split("&")))
                        .map(part -> part.split("="))
                        .filter(part -> part.length > 1)
                        .collect(Collectors.toMap(part -> part[0], part -> part[1])));
        params.put("user", URLEncoder.encode(userName, "UTF-8")); // Use the old form for Java 8 compatibility.
        params.put("password", URLEncoder.encode(password, "UTF-8"));
        return JDBC_URL_PREFIX + new URI(uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                params.entrySet().stream().map(i -> i.getKey() + "=" + i.getValue()).collect(Collectors.joining("&")),
                uri.getFragment());
    }

    /**
     * Replaces database name in the JDBC url
     *
     * @param url    JDBC url
     * @param dbName Database name
     * @return Modified Url
     * @throws URISyntaxException If Url violates RFC&nbsp;2396
     */
    static String replaceDatabase(final String url, final String dbName) throws URISyntaxException {
        final URI uri = URI.create(url.substring(JDBC_URL_PREFIX.length()));
        return JDBC_URL_PREFIX + new URI(uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                "/" + dbName,
                uri.getQuery(),
                uri.getFragment());
    }
}
