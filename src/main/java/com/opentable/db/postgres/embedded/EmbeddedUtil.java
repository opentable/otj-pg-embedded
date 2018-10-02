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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class EmbeddedUtil {

    static final String LOCK_FILE_NAME = "epg-lock";

    private EmbeddedUtil() {}
    static File getWorkingDirectory() {
        final File tempWorkingDirectory = new File(System.getProperty("java.io.tmpdir"), "embedded-pg");
        return new File(System.getProperty("ot.epg.working-dir", tempWorkingDirectory.getPath()));
    }


    static void mkdirs(File dir) {
        if (!dir.mkdirs() && !(dir.isDirectory() && dir.exists())) {
            throw new IllegalStateException("could not create " + dir);
        }
    }

    /**
     * Get current operating system string. The string is used in the appropriate
     * postgres binary name.
     *
     * @return Current operating system string.
     */
    static String getOS() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "Windows";
        }
        if (SystemUtils.IS_OS_MAC_OSX) {
            return "Darwin";
        }
        if (SystemUtils.IS_OS_LINUX) {
            return "Linux";
        }
        throw new UnsupportedOperationException("Unknown OS " + SystemUtils.OS_NAME);
    }

    /**
     * Get the machine architecture string. The string is used in the appropriate
     * postgres binary name.
     *
     * @return Current machine architecture string.
     */
    static String getArchitecture() {
        return "amd64".equals(SystemUtils.OS_ARCH) ? "x86_64" : SystemUtils.OS_ARCH;
    }

    /**
     * Unpack archive compressed by tar with xz compression. By default system tar is used (faster). If not found, then the
     * java implementation takes place.
     *
     * @param tbzPath   The archive path.
     * @param targetDir The directory to extract the content to.
     */
    static void extractTxz(String tbzPath, String targetDir) throws IOException {
        try (
                InputStream in = Files.newInputStream(Paths.get(tbzPath));
                XZInputStream xzIn = new XZInputStream(in);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)
        ) {
            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                final String individualFile = entry.getName();
                final File fsObject = new File(targetDir + "/" + individualFile);

                if (entry.isSymbolicLink() || entry.isLink()) {
                    Path target = FileSystems.getDefault().getPath(entry.getLinkName());
                    Files.createSymbolicLink(fsObject.toPath(), target);
                } else if (entry.isFile()) {
                    byte[] content = new byte[(int) entry.getSize()];
                    int read = tarIn.read(content, 0, content.length);
                    if (read == -1) {
                        throw new IllegalStateException("could not read " + individualFile);
                    }
                    mkdirs(fsObject.getParentFile());
                    try (OutputStream outputFile = new FileOutputStream(fsObject)) {
                        IOUtils.write(content, outputFile);
                    }
                } else if (entry.isDirectory()) {
                    mkdirs(fsObject);
                } else {
                    throw new UnsupportedOperationException(
                            String.format("Unsupported entry found: %s", individualFile)
                    );
                }

                if (individualFile.startsWith("bin/") || individualFile.startsWith("./bin/")) {
                    fsObject.setExecutable(true);
                }
            }
        }
    }

}
