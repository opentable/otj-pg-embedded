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

import java.util.Objects;

import org.testcontainers.containers.BindMode;

public final class BindMount {
    public static BindMount of(String localFile, String remoteFile, BindMode bindMode) {
        return new BindMount(localFile, remoteFile, bindMode);
    }

    private final String localFile;
    private final String remoteFile;
    private final BindMode bindMode;

    private BindMount(String localFile, String remoteFile, BindMode bindMode) {
        this.localFile = localFile;
        this.remoteFile = remoteFile;
        this.bindMode = bindMode == null ? BindMode.READ_ONLY : bindMode;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public String getLocalFile() {
        return localFile;
    }

    public String getRemoteFile() {
        return remoteFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)  {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BindMount bindMount = (BindMount) o;
        return Objects.equals(localFile, bindMount.localFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localFile);
    }

    @Override
    public String toString() {
        return "BindMount{" +
                "localFile='" + localFile + '\'' +
                ", remoteFile='" + remoteFile + '\'' +
                ", bindMode=" + bindMode +
                '}';
    }
}
