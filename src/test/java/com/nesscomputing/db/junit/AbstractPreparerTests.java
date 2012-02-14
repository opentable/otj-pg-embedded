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
package com.nesscomputing.db.junit;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;

public abstract class AbstractPreparerTests
{
    public abstract DBI getDbi();

    @Test
    public void testSimple() throws Exception
    {
        final int testValue = new Random().nextInt();

        final int dbTest = getDbi().withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery(String.format("SELECT %d", testValue))
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

        Assert.assertEquals(testValue, dbTest);
    }

    @Test
    public void testMigratory() throws Exception
    {
        final int dbTest = getDbi().withHandle(new HandleCallback<Integer>() {
                @Override
                public Integer withHandle(final Handle handle) {
                    return handle.createQuery("SELECT COUNT(1) FROM migratory_metadata")
                    .map(IntegerMapper.FIRST)
                    .first();
                }
            });

        Assert.assertEquals(2, dbTest);
    }
}
