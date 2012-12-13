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
package com.nesscomputing.db.postgres.embedded;

import java.io.IOException;

import org.junit.rules.ExternalResource;

import com.google.common.base.Preconditions;
import com.nesscomputing.testing.lessio.AllowAll;

@AllowAll
public class EmbeddedPostgreSQLRule extends ExternalResource
{
    private volatile EmbeddedPostgreSQL epg;

    @Override
    protected void before() throws Throwable
    {
        super.before();
        epg = EmbeddedPostgreSQL.start();
    }

    public EmbeddedPostgreSQL getEmbeddedPostgreSQL()
    {
        EmbeddedPostgreSQL epg = this.epg;
        Preconditions.checkState(epg != null, "JUnit test not started yet!");
        return epg;
    }

    @Override
    protected void after()
    {
        try {
            epg.close();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        super.after();
    }
}
