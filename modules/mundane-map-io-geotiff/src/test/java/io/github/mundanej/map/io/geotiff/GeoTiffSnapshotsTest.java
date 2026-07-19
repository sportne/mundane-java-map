package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeoTiffSnapshotsTest {
    private static final SourceIdentity ID = new SourceIdentity("snapshot", "Snapshot");
    private static final Path PATH = Path.of("SECRET_PATH_CANARY.tif");
    private static final String CANARY = "SECRET_IO_CANARY";

    @Test
    void allocatesOneOwnedExactSnapshotAndMapsOpenReadAndCloseFailures() {
        byte[] fixture = GeoTiffFixtures.areaGray();
        TrackingInput successInput = new TrackingInput(fixture, Integer.MAX_VALUE, false, false);
        TestAccess success = new TestAccess(fixture.length, () -> successInput);
        byte[] snapshot = read(success, CancellationToken.none());
        assertArrayEquals(fixture, snapshot);
        assertNotSame(fixture, snapshot);
        assertEquals(1, successInput.closeCalls);

        TestAccess open =
                new TestAccess(
                        fixture.length,
                        () -> {
                            throw new IOException(CANARY);
                        });
        assertIoFailure(
                assertThrows(SourceException.class, () -> read(open, CancellationToken.none())),
                "open");

        TrackingInput readInput = new TrackingInput(fixture, Integer.MAX_VALUE, true, false);
        assertIoFailure(
                assertThrows(
                        SourceException.class,
                        () ->
                                read(
                                        new TestAccess(fixture.length, () -> readInput),
                                        CancellationToken.none())),
                "read");
        assertEquals(1, readInput.closeCalls);

        TrackingInput closeInput = new TrackingInput(fixture, Integer.MAX_VALUE, false, true);
        assertIoFailure(
                assertThrows(
                        SourceException.class,
                        () ->
                                read(
                                        new TestAccess(fixture.length, () -> closeInput),
                                        CancellationToken.none())),
                "close");
        assertEquals(1, closeInput.closeCalls);
    }

    @Test
    void keepsReadFailurePrimaryAndSuppressesSanitizedCloseFailure() {
        byte[] fixture = GeoTiffFixtures.areaGray();
        TrackingInput input = new TrackingInput(fixture, Integer.MAX_VALUE, true, true);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                read(
                                        new TestAccess(fixture.length, () -> input),
                                        CancellationToken.none()));
        assertIoFailure(failure, "read");
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0] instanceof SourceException);
        assertEquals(
                "close",
                ((SourceException) failure.getSuppressed()[0])
                        .terminal()
                        .context()
                        .get("operation"));
        assertEquals(1, input.closeCalls);
    }

    @Test
    void oneByteProbeReportsTheExactInputLimitWithoutASecondRetainedBuffer() {
        byte[] stated = new byte[] {1, 2};
        TrackingInput input =
                new TrackingInput(new byte[] {1, 2, 3}, Integer.MAX_VALUE, false, false);
        GeoTiffLimits limits =
                GeoTiffLimits.defaults()
                        .withMaximumGeoAsciiBytes(2)
                        .withMaximumNoDataBytes(2)
                        .withMaximumTagPayloadBytes(2)
                        .withMaximumEncodedSegmentBytes(2)
                        .withMaximumInputBytes(stated.length);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffSnapshots.read(
                                        ID,
                                        PATH,
                                        limits,
                                        CancellationToken.none(),
                                        new TestAccess(stated.length, () -> input)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("inputBytes", failure.terminal().context().get("limit"));
        assertEquals("3", failure.terminal().context().get("requested"));
        assertEquals("2", failure.terminal().context().get("maximum"));
        assertEquals(1, input.probeReads);
        assertEquals(1, input.closeCalls);
        assertCleanChain(failure);
    }

    @Test
    void cancellationCoversEverySnapshotBoundaryIncludingProbeAndPublication() {
        byte[] fixture = GeoTiffFixtures.areaGray();
        for (int checkpoint = 1; checkpoint <= 8; checkpoint++) {
            int currentCheckpoint = checkpoint;
            TrackingInput input = new TrackingInput(fixture, Integer.MAX_VALUE, false, false);
            TestAccess access = new TestAccess(fixture.length, () -> input);
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> read(access, new CancelAt(currentCheckpoint)),
                            () -> "checkpoint " + currentCheckpoint);
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            assertCleanChain(failure);
            assertEquals(checkpoint >= 3 ? 1 : 0, access.openCalls);
            assertEquals(checkpoint >= 3 ? 1 : 0, input.closeCalls);
            if (checkpoint >= 7) {
                assertEquals(1, input.probeReads);
            }
        }

        TrackingInput partial = new TrackingInput(fixture, 1, false, false);
        SourceException afterSecondRead =
                assertThrows(
                        SourceException.class,
                        () -> read(new TestAccess(fixture.length, () -> partial), new CancelAt(7)));
        assertEquals("SOURCE_CANCELLED", afterSecondRead.terminal().code());
        assertEquals(2, partial.bulkReads);
        assertEquals(1, partial.closeCalls);
    }

    private static byte[] read(GeoTiffFileAccess access, CancellationToken cancellation) {
        return GeoTiffSnapshots.read(ID, PATH, GeoTiffLimits.defaults(), cancellation, access);
    }

    private static void assertIoFailure(SourceException failure, String operation) {
        assertEquals("GEOTIFF_IO_FAILED", failure.terminal().code());
        assertEquals(operation, failure.terminal().context().get("operation"));
        assertEquals("other", failure.terminal().context().get("reason"));
        assertCleanChain(failure);
    }

    private static void assertCleanChain(Throwable failure) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            assertTrue(current instanceof SourceException, current.getClass().getName());
            SourceException sourceFailure = (SourceException) current;
            String text =
                    String.valueOf(current.getMessage())
                            + sourceFailure.report()
                            + sourceFailure.terminal();
            assertFalse(text.contains(CANARY));
            assertFalse(text.contains(PATH.toString()));
            assertFalse(text.contains("java."));
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            Collections.addAll(pending, current.getSuppressed());
        }
    }

    @FunctionalInterface
    private interface Opener {
        InputStream open() throws IOException;
    }

    private static final class TestAccess implements GeoTiffFileAccess {
        private final long size;
        private final Opener opener;
        private int openCalls;

        private TestAccess(long size, Opener opener) {
            this.size = size;
            this.opener = opener;
        }

        @Override
        public long size(Path path) {
            return size;
        }

        @Override
        public InputStream open(Path path) throws IOException {
            openCalls++;
            return opener.open();
        }
    }

    private static final class TrackingInput extends InputStream {
        private final byte[] bytes;
        private final int maximumChunk;
        private final boolean failRead;
        private final boolean failClose;
        private int position;
        private int bulkReads;
        private int probeReads;
        private int closeCalls;

        private TrackingInput(byte[] bytes, int maximumChunk, boolean failRead, boolean failClose) {
            this.bytes = bytes.clone();
            this.maximumChunk = maximumChunk;
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            bulkReads++;
            if (failRead) {
                throw new IOException(CANARY);
            }
            if (position == bytes.length) {
                return -1;
            }
            int count = Math.min(Math.min(maximumChunk, length), bytes.length - position);
            System.arraycopy(bytes, position, target, offset, count);
            position += count;
            return count;
        }

        @Override
        public int read() throws IOException {
            probeReads++;
            if (failRead) {
                throw new IOException(CANARY);
            }
            return position == bytes.length ? -1 : Byte.toUnsignedInt(bytes[position++]);
        }

        @Override
        public void close() throws IOException {
            closeCalls++;
            if (failClose) {
                throw new IOException(CANARY);
            }
        }
    }

    private static final class CancelAt implements CancellationToken {
        private final int target;
        private int checkpoints;

        private CancelAt(int target) {
            this.target = target;
        }

        @Override
        public boolean isCancellationRequested() {
            return ++checkpoints >= target;
        }
    }
}
