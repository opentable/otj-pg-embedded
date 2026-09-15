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

import com.opentable.db.postgres.junit5.EmbeddedPostgresExtension;
import com.opentable.db.postgres.junit5.PreparedDbExtension;
import liquibase.Contexts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LiquibasePreparerContextTest {
  @RegisterExtension
  static final PreparedDbExtension db = EmbeddedPostgresExtension.preparedDatabase(
      LiquibasePreparer.forClasspathLocation("liqui/master-test.xml", new Contexts("test"))
  );

  @Test
  public void testEmptyTables() throws Exception {
    try (Connection c = db.getTestDatabase().getConnection();
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) AS cnt FROM foo")) {

      rs.next();
      long count = rs.getLong("cnt");
      assertEquals(0L, count);
    }
  }
}
