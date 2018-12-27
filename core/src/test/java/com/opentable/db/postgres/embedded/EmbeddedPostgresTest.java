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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EmbeddedPostgresTest
{
    @Rule
    public TemporaryFolder tf = new TemporaryFolder();

    @Test
    public void testEmbeddedPg() throws Exception
    {
        try (EmbeddedPostgres pg = EmbeddedPostgres.start();
             Connection c = pg.getPostgresDatabase().getConnection()) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse(rs.next());
        }
    }

    @Test
    public void testEmbeddedPgCreationWithNestedDataDirectory() throws Exception
    {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(tf.newFolder("data-dir-parent") + "/data-dir")
                .start()) {
            // nothing to do
        }
    }

    @Test
    public void testValidLocaleSettingsPassthrough() throws IOException {
        try {
            EmbeddedPostgres.Builder builder = null;
            if (SystemUtils.IS_OS_WINDOWS) {
                builder = EmbeddedPostgres.builder()
                        .setLocaleConfig("locale", "en-us")
                        .setLocaleConfig("lc-messages", "en-us");
            } else if (SystemUtils.IS_OS_MAC) {
                builder = EmbeddedPostgres.builder()
                        .setLocaleConfig("locale", "en_US")
                        .setLocaleConfig("lc-messages", "en_US");
            } else if (SystemUtils.IS_OS_LINUX){
                builder = EmbeddedPostgres.builder()
                        .setLocaleConfig("locale", "en_US.utf8")
                        .setLocaleConfig("lc-messages", "en_US.utf8");
            } else {
                fail("System not detected!");
            }
            builder.start();
        } catch (IllegalStateException e){
            e.printStackTrace();
            fail("Failed to set locale settings: " + e.getLocalizedMessage());
        }
    }
}
