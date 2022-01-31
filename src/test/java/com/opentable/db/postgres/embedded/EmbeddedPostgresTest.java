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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.utility.DockerImageName;

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

    @Test
    public void testImageOptions() {
        // Ugly hack, since OT already has this defined as an ENV VAR, which can't really be cleared
        Assume.assumeTrue(System.getenv(EmbeddedPostgres.ENV_DOCKER_PREFIX) ==  null);
        System.clearProperty(EmbeddedPostgres.ENV_DOCKER_PREFIX);
        System.clearProperty(EmbeddedPostgres.ENV_DOCKER_IMAGE);

        DockerImageName defaultImage = EmbeddedPostgres.builder().getDefaultImage();
        assertEquals(EmbeddedPostgres.DOCKER_DEFAULT_IMAGE_NAME.withTag(EmbeddedPostgres.DOCKER_DEFAULT_TAG).toString(), defaultImage.toString());

        System.setProperty(EmbeddedPostgres.ENV_DOCKER_PREFIX, "dockerhub.otenv.com/");
        defaultImage = EmbeddedPostgres.builder().getDefaultImage();
        assertEquals("dockerhub.otenv.com/" + EmbeddedPostgres.DOCKER_DEFAULT_IMAGE_NAME.getUnversionedPart() + ":" + EmbeddedPostgres.DOCKER_DEFAULT_TAG, defaultImage.toString());

        System.clearProperty(EmbeddedPostgres.ENV_DOCKER_PREFIX);
        System.setProperty(EmbeddedPostgres.ENV_DOCKER_IMAGE, "dockerhub.otenv.com/ot-pg:14-latest");

        EmbeddedPostgres.Builder b = EmbeddedPostgres.builder();
        defaultImage = b.getDefaultImage();
        assertEquals("dockerhub.otenv.com/ot-pg:14-latest", defaultImage.toString());
        assertEquals("dockerhub.otenv.com/ot-pg:14-latest", b.getImage().toString());
        b.setImage(DockerImageName.parse("foo").withTag("15-latest"));
        assertEquals("foo:15-latest", b.getImage().toString());

        System.clearProperty(EmbeddedPostgres.ENV_DOCKER_IMAGE);
    }

    @Test
    public void testDatabaseName() throws IOException, SQLException {
        EmbeddedPostgres db = EmbeddedPostgres.builder().start();
        try {
            testSpecificDatabaseName(db, EmbeddedPostgres.POSTGRES);
        } finally {
            db.close();
        }
        db = EmbeddedPostgres.builder().setDatabaseName("mike").start();
        try {
            testSpecificDatabaseName(db, "mike");
        } finally {
            db.close();
        }

    }

    @Test
    public void testTemplateDatabase() throws IOException, SQLException {
        EmbeddedPostgres db = EmbeddedPostgres.builder().start();
        try {
            testSpecificDatabaseName(db.getTemplateDatabase(), db, "template1");
        } finally {
            db.close();
        }
    }

    private void testSpecificDatabaseName(EmbeddedPostgres db, String expectedName) throws SQLException, IOException {
        testSpecificDatabaseName(db.getPostgresDatabase(), db,expectedName);
    }
    private void testSpecificDatabaseName(DataSource dataSource, EmbeddedPostgres db, String expectedName) throws IOException, SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (Statement statement = c.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT current_database()")) {
                resultSet.next();
                assertEquals(expectedName, resultSet.getString(1));
            }
        }
    }
}
