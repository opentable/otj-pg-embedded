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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.Checksum;

public interface OwnedTransferable extends Transferable {

    default int getUid() {
        return -1;
    }

    default int getGid() {
        return -1;
    }

    static OwnedTransferable of(byte[] bytes, int fileMode) {
        return of(bytes, fileMode, -1, -1);
    }

    static OwnedTransferable of(byte[] bytes, int fileMode, int uid, int gid) {
        return new OwnedTransferable() {
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public void updateChecksum(Checksum c) { c.update(bytes, 0, bytes.length); }
            @Override public int getFileMode() { return fileMode; }
            @Override public int getUid() { return uid; }
            @Override public int getGid() { return gid; }
        };
    }

    @Override
    default void transferTo(TarArchiveOutputStream tarArchiveOutputStream, String destination) {
        TarArchiveEntry tarEntry = new TarArchiveEntry(destination);
        tarEntry.setSize(getSize());
        tarEntry.setMode(getFileMode());
        if (getUid() >= 0) {
            tarEntry.setUserId(getUid());
        }
        if (getGid() >= 0) {
            tarEntry.setGroupId(getGid());
        }
        try {
            tarArchiveOutputStream.putArchiveEntry(tarEntry);
            IOUtils.write(getBytes(), tarArchiveOutputStream);
            tarArchiveOutputStream.closeArchiveEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't transfer " + getDescription(), e);
        }
    }
}
