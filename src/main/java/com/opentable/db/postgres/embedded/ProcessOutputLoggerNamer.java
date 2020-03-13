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

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy for naming the process output logger.
 */
@FunctionalInterface
public interface ProcessOutputLoggerNamer {
    String name(Optional<String> prefix, String kind, UUID instanceId, Optional<String> command);

    ProcessOutputLoggerNamer DEFAULT = (prefix, kind, instanceId, command) -> {
        StringBuilder buf =
                new StringBuilder(kind.length() + command.orElse("").length() + prefix.orElse("").length() + 45);
        prefix.ifPresent(p -> buf.append(p).append('.'));
        buf.append(kind);
        buf.append('-');
        buf.append(instanceId);
        command.ifPresent(c -> buf.append(':').append(c));
        return buf.toString();
    };
}
