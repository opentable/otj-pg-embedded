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

import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * A DatabasePreparer applies an arbitrary set of changes
 * (e.g. database migrations, user creation) to a database
 * before it is presented to the user.
 *
 * The preparation steps are expected to be deterministic.
 * For efficiency reasons, databases created by DatabasePreparer
 * instances may be pooled, using {@link Object#hashCode()} and
 * {@link Object#equals(Object)} to determine equivalence.
 */
public interface DatabasePreparer {
    void prepare(DataSource ds) throws SQLException;
}
