/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
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
package com.nesscomputing.db.postgres.junit;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.annotation.Nonnull;

import org.junit.rules.TestRule;

import com.nesscomputing.config.Config;

public class EmbeddedPostgresRules
{
    /**
     * Returns a {@link TestRule} to create a Postgres cluster, shared amongst all test cases in this JVM.
     * The rule contributes {@link Config} switches to configure each test case to get its own database.
     */
    public static EmbeddedPostgresTestDatabaseRule embeddedDatabaseRule(@Nonnull final URL baseUrl, final String... personalities)
    {
        try {
            return embeddedDatabaseRule(baseUrl.toURI(), personalities);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a {@link TestRule} to create a Postgres cluster, shared amongst all test cases in this JVM.
     * The rule contributes {@link Config} switches to configure each test case to get its own database.
     */
    public static EmbeddedPostgresTestDatabaseRule embeddedDatabaseRule(@Nonnull final URI baseUri, final String... personalities)
    {
        return new EmbeddedPostgresTestDatabaseRule(baseUri, personalities);
    }
}
