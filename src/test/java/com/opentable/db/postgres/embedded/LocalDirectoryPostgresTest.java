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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Assume;
import org.junit.Test;

public class LocalDirectoryPostgresTest {

    private static final File USR_LOCAL = new File("/usr/local");
    private static final File USR_LOCAL_BIN_POSTGRES = new File("/usr/local/bin/postgres");

    @Test
    public void testEmbeddedPg() throws Exception {
        Assume.assumeTrue("PostgreSQL binary must exist", USR_LOCAL_BIN_POSTGRES.exists());
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().setPostgresBinaryDirectory(USR_LOCAL).start();
                Connection c = pg.getPostgresDatabase().getConnection()) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse(rs.next());
        }
    }
}
