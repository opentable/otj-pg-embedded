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

import static com.opentable.db.postgres.embedded.EmbeddedUtil.LOCK_FILE_NAME;
import static com.opentable.db.postgres.embedded.EmbeddedUtil.extractTxz;
import static com.opentable.db.postgres.embedded.EmbeddedUtil.getArchitecture;
import static com.opentable.db.postgres.embedded.EmbeddedUtil.getOS;
import static com.opentable.db.postgres.embedded.EmbeddedUtil.getWorkingDirectory;
import static com.opentable.db.postgres.embedded.EmbeddedUtil.mkdirs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UncompressBundleDirectoryResolver implements PgDirectoryResolver {

    private static volatile UncompressBundleDirectoryResolver DEFAULT_INSTANCE;

    public static synchronized UncompressBundleDirectoryResolver getDefault() {
        if (DEFAULT_INSTANCE == null) {
            DEFAULT_INSTANCE = new UncompressBundleDirectoryResolver(new BundledPostgresBinaryResolver());
        }
        return DEFAULT_INSTANCE;
    }

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);
    private final Lock prepareBinariesLock = new ReentrantLock();

    private final PgBinaryResolver pgBinaryResolver;

    public UncompressBundleDirectoryResolver(PgBinaryResolver pgBinaryResolver) {
        this.pgBinaryResolver = pgBinaryResolver;
    }

    private final Map<PgBinaryResolver, File> prepareBinaries = new HashMap<>();

    @Override
    public File getDirectory(Optional<File> overrideWorkingDirectory) {
        prepareBinariesLock.lock();
        try {
            if (prepareBinaries.containsKey(pgBinaryResolver) && prepareBinaries.get(pgBinaryResolver).exists()) {
                return prepareBinaries.get(pgBinaryResolver);
            }

            final String system = getOS();
            final String machineHardware = getArchitecture();

            LOG.info("Detected a {} {} system", system, machineHardware);
            File pgDir;
            final InputStream pgBinary; // NOPMD
            try {
                pgBinary = pgBinaryResolver.getPgBinary(system, machineHardware);
            } catch (final IOException e) {
                throw new ExceptionInInitializerError(e);
            }

            if (pgBinary == null) {
                throw new IllegalStateException("No Postgres binary found for " + system + " / " + machineHardware);
            }

            try (DigestInputStream pgArchiveData = new DigestInputStream(pgBinary, MessageDigest.getInstance("MD5"));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                IOUtils.copy(pgArchiveData, baos);
                pgArchiveData.close();

                String pgDigest = Hex.encodeHexString(pgArchiveData.getMessageDigest().digest());
                File workingDirectory = overrideWorkingDirectory.isPresent() ? overrideWorkingDirectory.get()
                        : getWorkingDirectory();
                pgDir = new File(workingDirectory, String.format("PG-%s", pgDigest));

                mkdirs(pgDir);
                final File unpackLockFile = new File(pgDir, LOCK_FILE_NAME);
                final File pgDirExists = new File(pgDir, ".exists");

                if (!pgDirExists.exists()) {
                    try (FileOutputStream lockStream = new FileOutputStream(unpackLockFile);
                            FileLock unpackLock = lockStream.getChannel().tryLock()) {
                        if (unpackLock != null) {
                            try {
                                if (pgDirExists.exists()) {
                                    throw new IllegalStateException(
                                            "unpack lock acquired but .exists file is present " + pgDirExists);
                                }
                                LOG.info("Extracting Postgres...");
                                try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                                    extractTxz(bais, pgDir.getPath());
                                }
                                if (!pgDirExists.createNewFile()) {
                                    throw new IllegalStateException("couldn't make .exists file " + pgDirExists);
                                }
                            } catch (Exception e) {
                                LOG.error("while unpacking Postgres", e);
                            }
                        } else {
                            // the other guy is unpacking for us.
                            int maxAttempts = 60;
                            while (!pgDirExists.exists() && --maxAttempts > 0) { // NOPMD
                                Thread.sleep(1000L);
                            }
                            if (!pgDirExists.exists()) {
                                throw new IllegalStateException(
                                        "Waited 60 seconds for postgres to be unpacked but it never finished!");
                            }
                        }
                    } finally {
                        if (unpackLockFile.exists() && !unpackLockFile.delete()) {
                            LOG.error("could not remove lock file {}", unpackLockFile.getAbsolutePath());
                        }
                    }
                }
            } catch (final IOException | NoSuchAlgorithmException e) {
                throw new ExceptionInInitializerError(e);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ExceptionInInitializerError(ie);
            }
            prepareBinaries.put(pgBinaryResolver, pgDir);
            LOG.info("Postgres binaries at {}", pgDir);
            return pgDir;
        } finally {
            prepareBinariesLock.unlock();
        }
    }
}
