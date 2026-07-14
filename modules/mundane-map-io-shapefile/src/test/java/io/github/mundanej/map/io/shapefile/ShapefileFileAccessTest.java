package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShapefileFileAccessTest {
    private static final Path PATH = Path.of("fixture.shp");
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @Test
    void mapsOpenSizeAndReadFailuresToExactOperations() {
        NoSuchFileException openFailure = new NoSuchFileException("not exposed");
        FakeAccess open = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        open.openFailure = openFailure;
        SourceException openResult = assertThrows(SourceException.class, () -> open(open));
        assertFailure(openResult, "open", "notFound");
        assertSame(openFailure, openResult.getCause());

        FakeAccess size = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        size.channel.sizeFailure = new IOException("not exposed");
        SourceException sizeResult = assertThrows(SourceException.class, () -> open(size));
        assertFailure(sizeResult, "size", "other");
        assertEquals(1, size.channel.closeCount);

        FakeAccess read = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        read.channel.readFailure = new IOException("not exposed");
        SourceException readResult = assertThrows(SourceException.class, () -> open(read));
        assertFailure(readResult, "read", "other");
        assertEquals(0, readResult.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(1, read.channel.closeCount);
    }

    @Test
    void retainsThePrimaryFailureAndSuppressesOpeningCleanupFailure() {
        FakeAccess access = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        access.channel.sizeFailure = new IOException("primary");
        IOException cleanup = new IOException("cleanup");
        access.channel.closeFailure = cleanup;

        SourceException result = assertThrows(SourceException.class, () -> open(access));

        assertFailure(result, "size", "other");
        assertEquals(1, result.getSuppressed().length);
        assertSame(cleanup, result.getSuppressed()[0]);
        assertEquals(1, access.channel.closeCount);
    }

    @Test
    void reportsHeaderShortReadAndClosesTheAcquiredChannel() {
        FakeAccess access = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        access.channel.readLimit = 63;

        SourceException result = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_HEADER_INVALID", result.terminal().code());
        assertEquals("truncated", result.terminal().context().get("field"));
        assertEquals("63", result.terminal().context().get("actualBytes"));
        assertEquals(63, result.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(1, access.channel.closeCount);
    }

    @Test
    void closesAValidShpWhenAStagedSidecarIsDiscovered() {
        FakeAccess access = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        access.dbfPresent = true;

        SourceException result = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_PROFILE_NOT_IMPLEMENTED", result.terminal().code());
        assertEquals("dbf", result.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals(1, access.channel.closeCount);
    }

    @Test
    void reportsAggregateMultipointCoordinateShortReadAtFirstMissingByte() {
        FakeAccess access =
                new FakeAccess(ShpFixtures.file(8, 0, 0, 2, 2, ShpFixtures.multipoint(0, 0, 2, 2)));
        try (FeatureSource source = openSource(access);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            // The frame declares 72 content bytes. Make the channel end after the prefix, the first
            // coordinate, and the first ordinate of the second coordinate (64 content bytes).
            access.channel.readLimit = 172;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SHAPEFILE_RECORD_LENGTH_INVALID", result.terminal().code());
            assertEquals("truncatedPayload", result.terminal().context().get("reason"));
            assertEquals("72", result.terminal().context().get("expectedBytes"));
            assertEquals("64", result.terminal().context().get("actualBytes"));
            assertEquals(
                    1, result.terminal().location().orElseThrow().recordNumber().orElseThrow());
            assertEquals(
                    172, result.terminal().location().orElseThrow().byteOffset().orElseThrow());
        }
    }

    @Test
    void reportsPolylinePartTableAndCoordinateShortReadsAtFirstMissingByte() {
        byte[] content = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);

        FakeAccess table = new FakeAccess(ShpFixtures.file(3, 0, 0, 2, 2, content));
        try (FeatureSource source = openSource(table);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            table.channel.readLimit = 154;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertTruncatedPolyline(result, 46, 154);
        }

        FakeAccess coordinates = new FakeAccess(ShpFixtures.file(3, 0, 0, 2, 2, content));
        try (FeatureSource source = openSource(coordinates);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            coordinates.channel.readLimit = 178;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertTruncatedPolyline(result, 70, 178);
        }
    }

    @Test
    void reportsPolygonPartTableAndCoordinateShortReadsAtFirstMissingByte() {
        byte[] content = ShpFixtures.polygon(new int[] {0}, 0, 0, 0, 2, 2, 2, 2, 0, 0, 0);

        FakeAccess table = new FakeAccess(ShpFixtures.file(5, 0, 0, 2, 2, content));
        try (FeatureSource source = openSource(table);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            table.channel.readLimit = 154;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertTruncatedMultipart(result, 128, 46, 154);
        }

        FakeAccess coordinates = new FakeAccess(ShpFixtures.file(5, 0, 0, 2, 2, content));
        try (FeatureSource source = openSource(coordinates);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            coordinates.channel.readLimit = 178;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertTruncatedMultipart(result, 128, 70, 178);
        }
    }

    @Test
    void cursorReadFailureIsStructuredAndReleasesTheCursorSlot() {
        FakeAccess access =
                new FakeAccess(ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        try (FeatureSource source = openSource(access)) {
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            IOException failure = new IOException("not exposed");
            access.channel.readFailure = failure;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertFailure(result, "read", "other");
            assertSame(failure, result.getCause());
            assertEquals(
                    100, result.terminal().location().orElseThrow().byteOffset().orElseThrow());

            access.channel.readFailure = null;
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    @Test
    void sourceCloseFailureIsStableAndStillTransitionsClosed() {
        FakeAccess access = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0));
        FeatureSource source = openSource(access);
        IOException failure = new IOException("not exposed");
        access.channel.closeFailure = failure;

        SourceException result = assertThrows(SourceException.class, source::close);

        assertEquals("SOURCE_CLOSE_FAILED", result.terminal().code());
        assertSame(failure, result.getCause());
        assertTrue(source.isClosed());
        assertEquals(1, access.channel.closeCount);
        source.close();
        assertEquals(1, access.channel.closeCount);
    }

    @Test
    void exhaustionRechecksSizeAndReleasesTheCursorSlotOnMutation() {
        FakeAccess access =
                new FakeAccess(ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        try (FeatureSource source = openSource(access)) {
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            assertTrue(cursor.advance());
            access.channel.reportedSize++;

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SHAPEFILE_FILE_LENGTH_MISMATCH", result.terminal().code());
            assertEquals("128", result.terminal().context().get("declaredBytes"));
            assertEquals("129", result.terminal().context().get("actualBytes"));
            assertFalse(source.isClosed());
            assertThrows(
                    SourceException.class,
                    () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        }
    }

    @Test
    void cancellationImmediatelyBeforePackedAllocationReleasesTheCursorSlot() {
        assertMultipointCancellationAtCheckpoint(12);
    }

    @Test
    void cancellationImmediatelyBeforeCoordinateSequenceCopyReleasesTheCursorSlot() {
        assertMultipointCancellationAtCheckpoint(16);
    }

    @Test
    void polylineCancellationDuringCoordinateReadReleasesTheCursorSlot() {
        FakeAccess access =
                new FakeAccess(
                        ShpFixtures.file(
                                3, 0, 0, 2, 2, ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2)));
        try (FeatureSource source = openSource(access)) {
            CancellationToken cancellation = () -> access.channel.lastReadPosition >= 156;
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SOURCE_CANCELLED", result.terminal().code());
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    @Test
    void polylineCancellationImmediatelyBeforeVariableAllocationReleasesTheCursorSlot() {
        FakeAccess access =
                new FakeAccess(
                        ShpFixtures.file(
                                3, 0, 0, 2, 2, ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2)));
        try (FeatureSource source = openSource(access)) {
            int[] checkpointsAfterPrefix = {0};
            CancellationToken cancellation =
                    () -> access.channel.lastReadLength == 44 && ++checkpointsAfterPrefix[0] == 2;
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SOURCE_CANCELLED", result.terminal().code());
            assertEquals(2, checkpointsAfterPrefix[0]);
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    @Test
    void polylineCustomCancellationThrowablePropagatesAfterCursorCleanup() {
        FakeAccess access =
                new FakeAccess(
                        ShpFixtures.file(
                                3, 0, 0, 2, 2, ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2)));
        try (FeatureSource source = openSource(access)) {
            MarkerFailure marker = new MarkerFailure();
            CancellationToken cancellation =
                    () -> {
                        if (access.channel.lastReadPosition >= 156) {
                            throw marker;
                        }
                        return false;
                    };
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            assertSame(marker, assertThrows(MarkerFailure.class, cursor::advance));

            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    @Test
    void polygonCancellationInsideExactAreaWorkReleasesTheCursorSlot() {
        double[] ring = new double[50];
        ring[3] = 10;
        for (int point = 2; point < 22; point++) {
            ring[point * 2] = 1;
            ring[point * 2 + 1] = 10;
        }
        ring[44] = 10;
        ring[45] = 10;
        ring[46] = 10;
        byte[] content = ShpFixtures.polygon(new int[] {0}, ring);
        FakeAccess access = new FakeAccess(ShpFixtures.file(5, 0, 0, 10, 10, content));
        try (FeatureSource source = openSource(access)) {
            int[] postPayloadChecks = {0};
            CancellationToken cancellation =
                    () -> access.channel.lastReadPosition >= 540 && ++postPayloadChecks[0] == 12;
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SOURCE_CANCELLED", result.terminal().code());
            assertEquals(12, postPayloadChecks[0]);
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    @Test
    void polygonCancellationDuringExactTopologyPredicateReleasesTheCursorSlot() {
        byte[] content =
                ShpFixtures.polygon(
                        new int[] {0, 5},
                        0,
                        0,
                        0,
                        10,
                        10,
                        10,
                        10,
                        0,
                        0,
                        0,
                        2,
                        2,
                        4,
                        2,
                        4,
                        4,
                        2,
                        4,
                        2,
                        2);
        FakeAccess access = new FakeAccess(ShpFixtures.file(5, 0, 0, 10, 10, content));
        try (FeatureSource source = openSource(access)) {
            int[] postPayloadChecks = {0};
            CancellationToken cancellation =
                    () -> access.channel.lastReadPosition >= 304 && ++postPayloadChecks[0] == 18;
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SOURCE_CANCELLED", result.terminal().code());
            assertEquals(18, postPayloadChecks[0]);
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    private static void assertMultipointCancellationAtCheckpoint(int cancellationCheck) {
        FakeAccess access =
                new FakeAccess(ShpFixtures.file(8, 0, 0, 1, 1, ShpFixtures.multipoint(0, 0)));
        try (FeatureSource source = openSource(access)) {
            CountingCancellationToken cancellation =
                    new CountingCancellationToken(cancellationCheck);
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            SourceException result = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SOURCE_CANCELLED", result.terminal().code());
            assertEquals(cancellationCheck, cancellation.checks);
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    private static void open(FakeAccess access) {
        openSource(access);
    }

    private static FeatureSource openSource(FakeAccess access) {
        return Shapefiles.open(
                IDENTITY, PATH, ShapefileOpenOptions.defaults(), CancellationToken.none(), access);
    }

    private static void assertFailure(SourceException failure, String operation, String causeKind) {
        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals(operation, failure.terminal().context().get("operation"));
        assertEquals(causeKind, failure.terminal().context().get("causeKind"));
    }

    private static void assertTruncatedPolyline(
            SourceException failure, long actualBytes, long offset) {
        assertTruncatedMultipart(failure, 80, actualBytes, offset);
    }

    private static void assertTruncatedMultipart(
            SourceException failure, long expectedBytes, long actualBytes, long offset) {
        assertEquals("SHAPEFILE_RECORD_LENGTH_INVALID", failure.terminal().code());
        assertEquals("truncatedPayload", failure.terminal().context().get("reason"));
        assertEquals(
                Long.toString(expectedBytes), failure.terminal().context().get("expectedBytes"));
        assertEquals(Long.toString(actualBytes), failure.terminal().context().get("actualBytes"));
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
    }

    private static final class MarkerFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class FakeAccess implements ShapefileFileAccess {
        private final FakeChannel channel;
        private IOException openFailure;
        private boolean dbfPresent;

        private FakeAccess(byte[] bytes) {
            channel = new FakeChannel(bytes);
        }

        @Override
        public boolean exists(Path path) {
            Path fileName = path.getFileName();
            String name = fileName == null ? path.toString() : fileName.toString();
            return name.equals("fixture.shp") || (dbfPresent && name.equals("fixture.dbf"));
        }

        @Override
        public boolean isSameFile(Path first, Path second) {
            return first.equals(second);
        }

        @Override
        public Channel open(Path path) throws IOException {
            if (openFailure != null) {
                throw openFailure;
            }
            return channel;
        }
    }

    private static final class CountingCancellationToken implements CancellationToken {
        private final int cancellationCheck;
        private int checks;

        private CountingCancellationToken(int cancellationCheck) {
            this.cancellationCheck = cancellationCheck;
        }

        @Override
        public boolean isCancellationRequested() {
            checks++;
            return checks == cancellationCheck;
        }
    }

    private static final class FakeChannel implements ShapefileFileAccess.Channel {
        private final byte[] bytes;
        private IOException sizeFailure;
        private IOException readFailure;
        private IOException closeFailure;
        private int readLimit = Integer.MAX_VALUE;
        private long lastReadPosition = -1;
        private int lastReadLength;
        private long reportedSize;
        private int closeCount;

        private FakeChannel(byte[] bytes) {
            this.bytes = bytes.clone();
            reportedSize = bytes.length;
        }

        @Override
        public long size() throws IOException {
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return reportedSize;
        }

        @Override
        public int read(ByteBuffer target, long position) throws IOException {
            lastReadPosition = position;
            lastReadLength = target.remaining();
            if (readFailure != null) {
                throw readFailure;
            }
            int available = Math.min(bytes.length, readLimit) - Math.toIntExact(position);
            if (available <= 0) {
                return -1;
            }
            int length = Math.min(target.remaining(), available);
            target.put(bytes, Math.toIntExact(position), length);
            return length;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
