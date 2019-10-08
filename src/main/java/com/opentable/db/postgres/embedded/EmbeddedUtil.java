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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Phaser;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

final class EmbeddedUtil {
    static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);
    static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%s/%s?user=%s";
    static final String PG_STOP_MODE = "fast";
    static final String PG_STOP_WAIT_S = "5";
    static final String PG_SUPERUSER = "postgres";
    static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);
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
     * @param stream    A stream with the postgres binaries.
     * @param targetDir The directory to extract the content to.
     */
    static void extractTxz(InputStream stream, String targetDir) throws IOException {
        try (
                XZInputStream xzIn = new XZInputStream(stream);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)
        ) {
            final Phaser phaser = new Phaser(1);
            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) { //NOPMD
                final String individualFile = entry.getName();
                final File fsObject = new File(targetDir, individualFile);

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

                    final AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(fsObject.toPath(), CREATE, WRITE); //NOPMD
                    final ByteBuffer buffer = ByteBuffer.wrap(content); //NOPMD

                    phaser.register();
                    fileChannel.write(buffer, 0, fileChannel, new CompletionHandler<Integer, Channel>() {
                        @Override
                        public void completed(Integer written, Channel channel) {
                            closeChannel(channel);
                        }

                        @Override
                        public void failed(Throwable error, Channel channel) {
                            LOG.error("Could not write file {}", fsObject.getAbsolutePath(), error);
                            closeChannel(channel);
                        }

                        private void closeChannel(Channel channel) {
                            try {
                                channel.close();
                            } catch (IOException e) {
                                LOG.error("Unexpected error while closing the channel", e);
                            } finally {
                                phaser.arriveAndDeregister();
                            }
                        }
                    });
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

            phaser.arriveAndAwaitAdvance();
        }
    }
}
