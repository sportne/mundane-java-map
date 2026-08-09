package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ViewerUploadStagingTest {
    @TempDir Path temporaryDirectory;

    @Test
    void completeShapefileSidecarsUseFreshServerIdentityAndSurviveCommit() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        ViewerUploadStaging staging = new ViewerUploadStaging(root);
        ViewerUploadStaging.UploadSelection selection;
        try (ViewerUploadStaging.Batch batch =
                staging.begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
            add(batch, "Road Network.SHP", new byte[] {1, 2});
            add(batch, "Road Network.shx", new byte[] {3});
            add(batch, "Road Network.DBF", new byte[] {4, 5, 6});
            selection = batch.commit();
        }

        assertEquals(ViewerUploadStaging.UploadKind.SHAPEFILE, selection.kind());
        assertEquals("dataset.shp", selection.entry().getFileName().toString());
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(selection.entry()));
        assertTrue(selection.entry().startsWith(root.toRealPath()));

        staging.close();
        assertFalse(Files.exists(root));
    }

    @Test
    void rejectsPathsDuplicateOrIncompleteSidecarsAndUnsupportedTypes() throws Exception {
        for (String name : List.of("../escape.shp", "folder/file.shp", "folder\\file.shp", ".")) {
            ViewerUploadStaging staging = staging(name.replaceAll("[^A-Za-z]", "x"));
            try (ViewerUploadStaging.Batch batch =
                    staging.begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
                assertCode("UPLOAD_NAME_INVALID", () -> add(batch, name, new byte[] {1}));
            } finally {
                staging.close();
            }
        }

        ViewerUploadStaging duplicate = staging("duplicate");
        try (ViewerUploadStaging.Batch batch =
                duplicate.begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
            add(batch, "roads.shp", new byte[] {1});
            assertCode("UPLOAD_DUPLICATE_SIDECAR", () -> add(batch, "ROADS.SHP", new byte[] {2}));
        } finally {
            duplicate.close();
        }

        ViewerUploadStaging incomplete = staging("incomplete");
        try (ViewerUploadStaging.Batch batch =
                incomplete.begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
            add(batch, "roads.shp", new byte[] {1});
            add(batch, "roads.dbf", new byte[] {2});
            assertCode("UPLOAD_SIDECAR_INCOMPLETE", batch::commit);
        } finally {
            incomplete.close();
        }

        ViewerUploadStaging unsupported = staging("unsupported");
        try (ViewerUploadStaging.Batch batch =
                unsupported.begin(ViewerUploadStaging.UploadKind.RASTER)) {
            add(batch, "map.png", new byte[] {1});
            assertCode("UPLOAD_TYPE_UNSUPPORTED", batch::commit);
        } finally {
            unsupported.close();
        }
    }

    @Test
    void exactLengthsLimitsAndCancellationAreFailureAtomic() throws Exception {
        ViewerUploadStaging staging = staging("atomic");
        try (ViewerUploadStaging.Batch mismatch =
                staging.begin(ViewerUploadStaging.UploadKind.WORKSPACE)) {
            ViewerUploadStaging.ViewerUploadException failure =
                    assertThrows(
                            ViewerUploadStaging.ViewerUploadException.class,
                            () ->
                                    mismatch.add(
                                            "map.xml",
                                            2,
                                            new ByteArrayInputStream(new byte[] {1})));
            assertEquals("UPLOAD_LENGTH_MISMATCH", failure.code());
            assertEquals(0, regularFiles(staging.root()));
        }

        try (ViewerUploadStaging.Batch oversized =
                staging.begin(ViewerUploadStaging.UploadKind.RASTER)) {
            assertCode(
                    "UPLOAD_FILE_LIMIT_EXCEEDED",
                    () ->
                            oversized.add(
                                    "large.tif",
                                    ViewerUploadStaging.MAXIMUM_FILE_BYTES + 1,
                                    new ByteArrayInputStream(new byte[0])));
        }

        ViewerUploadStaging.Batch cancelled = staging.begin(ViewerUploadStaging.UploadKind.RASTER);
        staging.cancel();
        assertCode("UPLOAD_CANCELLED", () -> add(cancelled, "map.tif", new byte[] {1}));
        cancelled.close();
        staging.close();
    }

    @Test
    void cancellationAfterInputRegistrationPreventsTheFirstRead() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("registration-race"));
        AtomicReference<ViewerUploadStaging> owner = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        ViewerUploadStaging staging =
                new ViewerUploadStaging(
                        root,
                        () -> {
                            owner.get().cancel();
                        });
        owner.set(staging);

        try (ViewerUploadStaging.Batch batch =
                staging.begin(ViewerUploadStaging.UploadKind.RASTER)) {
            assertCode(
                    "UPLOAD_CANCELLED",
                    () ->
                            batch.add(
                                    "map.tif",
                                    1,
                                    new ByteArrayInputStream(new byte[] {1}) {
                                        @Override
                                        public synchronized int read(
                                                byte[] buffer, int offset, int length) {
                                            reads.incrementAndGet();
                                            return super.read(buffer, offset, length);
                                        }
                                    }));
            assertEquals(0, reads.get());
        } finally {
            staging.close();
        }
        assertFalse(Files.exists(root));
    }

    @Test
    void receivedBatchAndSessionByteLimitsAreProspective() throws Exception {
        ViewerUploadStaging received = staging("received-limit");
        try (ViewerUploadStaging.Batch batch =
                received.begin(ViewerUploadStaging.UploadKind.RASTER)) {
            assertCode(
                    "UPLOAD_BYTE_LIMIT_EXCEEDED",
                    () ->
                            batch.add(
                                    "map.tif",
                                    ViewerUploadStaging.MAXIMUM_FILE_BYTES,
                                    new RepeatedInputStream(
                                            ViewerUploadStaging.MAXIMUM_FILE_BYTES + 1)));
        } finally {
            received.close();
        }

        ViewerUploadStaging batchLimit = staging("batch-limit");
        try (ViewerUploadStaging.Batch batch =
                batchLimit.begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
            batch.add(
                    "map.shp",
                    ViewerUploadStaging.MAXIMUM_FILE_BYTES,
                    new RepeatedInputStream(ViewerUploadStaging.MAXIMUM_FILE_BYTES));
            batch.add(
                    "map.shx",
                    ViewerUploadStaging.MAXIMUM_FILE_BYTES,
                    new RepeatedInputStream(ViewerUploadStaging.MAXIMUM_FILE_BYTES));
            assertCode(
                    "UPLOAD_BYTE_LIMIT_EXCEEDED",
                    () -> batch.add("map.dbf", 1, new RepeatedInputStream(1)));
        } finally {
            batchLimit.close();
        }

        ViewerUploadStaging session = staging("session-byte-limit");
        for (int index = 0; index < 4; index++) {
            try (ViewerUploadStaging.Batch batch =
                    session.begin(ViewerUploadStaging.UploadKind.WORKSPACE)) {
                batch.add(
                        "workspace.xml",
                        ViewerUploadStaging.MAXIMUM_FILE_BYTES,
                        new RepeatedInputStream(ViewerUploadStaging.MAXIMUM_FILE_BYTES));
                batch.commit();
            }
        }
        try {
            assertCode(
                    "UPLOAD_SESSION_LIMIT_EXCEEDED",
                    () -> session.begin(ViewerUploadStaging.UploadKind.WORKSPACE));
        } finally {
            session.close();
        }
    }

    @Test
    void stalledReadsAreBoundedAndCloseBreaksAnActiveStreamBeforeDeletingRoot() throws Exception {
        ViewerUploadStaging stalled = staging("stalled");
        try (ViewerUploadStaging.Batch batch =
                        stalled.begin(ViewerUploadStaging.UploadKind.RASTER);
                ZeroInputStream input = new ZeroInputStream()) {
            assertCode("UPLOAD_READ_STALLED", () -> batch.add("map.tif", 1, input));
        } finally {
            stalled.close();
        }

        ViewerUploadStaging active = staging("active");
        ViewerUploadStaging.Batch batch = active.begin(ViewerUploadStaging.UploadKind.RASTER);
        try (BlockingInputStream input = new BlockingInputStream()) {
            AtomicReference<Throwable> terminal = new AtomicReference<>();
            Thread writer =
                    new Thread(
                            () -> {
                                try {
                                    batch.add("map.tif", 1, input);
                                } catch (RuntimeException | Error failure) {
                                    terminal.set(failure);
                                }
                            });
            writer.start();
            input.entered.await();
            Path root = active.root();
            active.close();
            writer.join();

            ViewerUploadStaging.ViewerUploadException failure =
                    (ViewerUploadStaging.ViewerUploadException) terminal.get();
            assertEquals("UPLOAD_CANCELLED", failure.code());
            assertFalse(Files.exists(root));
        }
    }

    @Test
    void sessionCountLimitIsProspectiveAndCloseDeletesEveryCommittedBatch() throws Exception {
        ViewerUploadStaging staging = staging("session");
        for (int index = 0; index < ViewerUploadStaging.MAXIMUM_SESSION_FILES; index++) {
            try (ViewerUploadStaging.Batch batch =
                    staging.begin(ViewerUploadStaging.UploadKind.WORKSPACE)) {
                add(batch, "workspace.xml", new byte[] {(byte) index});
                batch.commit();
            }
        }
        assertCode(
                "UPLOAD_SESSION_LIMIT_EXCEEDED",
                () -> staging.begin(ViewerUploadStaging.UploadKind.WORKSPACE));
        Path root = staging.root();
        staging.close();
        assertFalse(Files.exists(root));
    }

    @Test
    void activeBatchReservationsBoundConcurrentAndCommittedSessionStorage() throws Exception {
        ViewerUploadStaging concurrent = staging("concurrent-reservations");
        ViewerUploadStaging.Batch first =
                concurrent.begin(ViewerUploadStaging.UploadKind.WORKSPACE);
        ViewerUploadStaging.Batch second =
                concurrent.begin(ViewerUploadStaging.UploadKind.WORKSPACE);
        assertCode(
                "UPLOAD_SESSION_LIMIT_EXCEEDED",
                () -> concurrent.begin(ViewerUploadStaging.UploadKind.WORKSPACE));
        first.close();
        ViewerUploadStaging.Batch replacement =
                concurrent.begin(ViewerUploadStaging.UploadKind.WORKSPACE);
        replacement.close();
        second.close();
        concurrent.close();

        ViewerUploadStaging committed = staging("committed-reservation");
        try (ViewerUploadStaging.Batch batch =
                committed.begin(ViewerUploadStaging.UploadKind.WORKSPACE)) {
            add(batch, "workspace.xml", new byte[] {1});
            batch.commit();
        }
        ViewerUploadStaging.Batch active =
                committed.begin(ViewerUploadStaging.UploadKind.WORKSPACE);
        try {
            ViewerUploadStaging.Batch secondActive =
                    committed.begin(ViewerUploadStaging.UploadKind.WORKSPACE);
            assertCode(
                    "UPLOAD_SESSION_LIMIT_EXCEEDED",
                    () -> committed.begin(ViewerUploadStaging.UploadKind.WORKSPACE));
            secondActive.close();
        } finally {
            active.close();
            committed.close();
        }
    }

    @Test
    void committedUploadedShapefileOpensThroughTheSessionAndExpiresOnClose() throws Exception {
        Path fixtures = Path.of(System.getProperty("mundane.viewer.fixtures"));
        Path source = fixtures.resolve("shapefile");
        ViewerSession session = new ViewerSession();
        ViewerUploadStaging.UploadSelection selection;
        try (ViewerUploadStaging.Batch batch =
                session.uploads().begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
            for (String extension : List.of("shp", "shx", "dbf", "prj", "cpg")) {
                Path file = source.resolve("generated-polygon-hole-windows1252-3857." + extension);
                if (Files.exists(file)) {
                    try (var input = Files.newInputStream(file)) {
                        batch.add(file.getFileName().toString(), Files.size(file), input);
                    }
                }
            }
            selection = batch.commit();
        }

        assertTrue(session.openUploaded(selection).toCompletableFuture().join().opened());
        Path root = session.uploads().root();
        session.close();
        assertFalse(Files.exists(root));
    }

    private ViewerUploadStaging staging(String name) throws IOException {
        return new ViewerUploadStaging(Files.createDirectory(temporaryDirectory.resolve(name)));
    }

    private static void add(ViewerUploadStaging.Batch batch, String name, byte[] bytes) {
        batch.add(name, bytes.length, new ByteArrayInputStream(bytes));
    }

    private static void assertCode(String code, Runnable operation) {
        ViewerUploadStaging.ViewerUploadException failure =
                assertThrows(ViewerUploadStaging.ViewerUploadException.class, operation::run);
        assertEquals(code, failure.code());
    }

    private static long regularFiles(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).count();
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static final class ZeroInputStream extends InputStream {
        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return 0;
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            entered.countDown();
            try {
                closed.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", failure);
            }
            throw new IOException("closed");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return read();
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    private static final class RepeatedInputStream extends InputStream {
        private long remaining;

        private RepeatedInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = (int) Math.min(remaining, length);
            java.util.Arrays.fill(buffer, offset, offset + count, (byte) 0);
            remaining -= count;
            return count;
        }
    }
}
