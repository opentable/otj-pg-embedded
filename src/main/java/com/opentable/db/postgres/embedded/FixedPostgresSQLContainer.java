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

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * This class exists only to expose the addFixedExposedPort option.
 * Otherwise we'd use PostgreSQLContainer directly.
 * @param <SELF> boring generics for fluent api
 */
public class FixedPostgresSQLContainer<SELF extends PostgreSQLContainer<SELF>> extends PostgreSQLContainer<SELF> {
    public FixedPostgresSQLContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    @Override
    protected void addFixedExposedPort(int hostPort, int containerPort) {
        super.addFixedExposedPort(hostPort, containerPort);
    }
}