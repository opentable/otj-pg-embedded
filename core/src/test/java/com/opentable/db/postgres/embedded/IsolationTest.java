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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Rule;
import org.junit.Test;

import com.opentable.db.postgres.junit.EmbeddedPostgresRules;
import com.opentable.db.postgres.junit.SingleInstancePostgresRule;

public class IsolationTest
{
    @Rule
    public SingleInstancePostgresRule pg1 = EmbeddedPostgresRules.singleInstance();

    @Rule
    public SingleInstancePostgresRule pg2 = EmbeddedPostgresRules.singleInstance();

    @Test
    public void testIsolation() throws Exception
    {
        try (Connection c = getConnection(pg1)) {
            makeTable(c);
            try (Connection c2 = getConnection(pg2)) {
                makeTable(c2);
            }
        }
    }

    private void makeTable(Connection c) throws SQLException
    {
        Statement s = c.createStatement();
        s.execute("CREATE TABLE public.foo (a INTEGER)");
    }

    private Connection getConnection(SingleInstancePostgresRule epg) throws SQLException
    {
        return epg.getEmbeddedPostgres().getPostgresDatabase().getConnection();
    }
}
