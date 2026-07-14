package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShxFileAccessTest {
    private static final Path PATH = Path.of("fixture.shp");
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @Test
    void shxOpenReadAndCloseIoAreRecoveredWithStableOrderedWarnings() {
        FakeAccess open = validAccess();
        NoSuchFileException openFailure = new NoSuchFileException("must not leak");
        open.shx.openFailure = openFailure;
        try (FeatureSource source = open(open, CancellationToken.none())) {
            SourceDiagnostic warning = assertIgnoredIo(source, "notFound", -1);
            assertFalse(warning.message().contains("must not leak"));
            assertEquals(0, open.shx.channel.closeCount);
        }

        FakeAccess read = validAccess();
        IOException readFailure = new IOException("must not leak");
        read.shx.channel.readFailure = readFailure;
        read.shx.channel.failReadAtOrAfter = 0;
        try (FeatureSource source = open(read, CancellationToken.none())) {
            SourceDiagnostic warning = assertIgnoredIo(source, "other", 0);
            assertFalse(warning.message().contains("must not leak"));
            assertEquals(1, read.shx.channel.closeCount);
        }

        FakeAccess close = validAccess();
        close.shx.channel.closeFailure = new IOException("must not leak");
        try (FeatureSource source = open(close, CancellationToken.none())) {
            assertIgnoredIo(source, "other", -1);
            assertEquals(2, close.shx.channel.closeCount);
        }
    }

    @Test
    void structuralShortReadsAndFinalSizeMutationUseLengthReason() {
        FakeAccess header = validAccess();
        header.shx.channel.readLimit = 63;
        try (FeatureSource source = open(header, CancellationToken.none())) {
            assertIgnored(source, "length", 63);
        }

        FakeAccess entry = validAccess();
        entry.shx.channel.readLimit = 105;
        try (FeatureSource source = open(entry, CancellationToken.none())) {
            assertIgnored(source, "length", 105);
        }

        FakeAccess size = validAccess();
        size.shx.channel.sizeAfterFirstCall = size.shx.channel.reportedSize + 8;
        try (FeatureSource source = open(size, CancellationToken.none())) {
            assertIgnored(source, "length", 0);
        }
    }

    @Test
    void nonThrowingShpFrameEofRejectsTheIndexAndKeepsSequentialFallback() {
        FakeAccess access = validAccess();
        access.shp.channel.readLimit = 105;

        try (FeatureSource source = open(access, CancellationToken.none())) {
            assertIgnored(source, "shpMismatch", 104);
            assertEquals(1, access.shx.channel.closeCount);
            assertEquals(0, access.shp.channel.closeCount);
        }

        assertEquals(1, access.shp.channel.closeCount);
    }

    @Test
    void shpIoDuringCrossCheckIsTerminalAndNotRecoveredAsIgnoredIndex() {
        FakeAccess access = validAccess();
        IOException failure = new IOException("must not leak");
        access.shp.channel.failReadAtOrAfter = 100;
        access.shp.channel.readFailure = failure;

        SourceException result =
                assertThrows(SourceException.class, () -> open(access, CancellationToken.none()));

        assertEquals("SHAPEFILE_IO_FAILED", result.terminal().code());
        assertEquals("shp", result.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals(100, result.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals("read", result.terminal().context().get("operation"));
        assertSame(failure, result.getCause());
        assertTrue(
                result.report().entries().stream()
                        .noneMatch(value -> value.code().equals("SHAPEFILE_SHX_IGNORED")));
        assertEquals(1, access.shx.channel.closeCount);
        assertEquals(1, access.shp.channel.closeCount);
    }

    @Test
    void cancellationAfterShxReadIsTerminalAndCleansBothChannels() {
        FakeAccess access = validAccess();
        CancellationToken token = () -> access.shx.channel.readCount > 0;

        SourceException result = assertThrows(SourceException.class, () -> open(access, token));

        assertEquals("SOURCE_CANCELLED", result.terminal().code());
        assertTrue(
                result.report().entries().stream()
                        .noneMatch(value -> value.code().equals("SHAPEFILE_SHX_IGNORED")));
        assertEquals(1, access.shx.channel.closeCount);
        assertEquals(1, access.shp.channel.closeCount);

        FakeAccess beforePublication = validAccess();
        CancellationToken afterClose = () -> beforePublication.shx.channel.closeCount > 0;
        SourceException publicationResult =
                assertThrows(SourceException.class, () -> open(beforePublication, afterClose));
        assertEquals("SOURCE_CANCELLED", publicationResult.terminal().code());
        assertEquals(1, beforePublication.shx.channel.closeCount);
        assertEquals(1, beforePublication.shp.channel.closeCount);
    }

    @Test
    void cancellationAtShxOpenSizeAllocationAndEntryValidationCleansBothChannels() {
        FakeAccess afterOpen = validAccess();
        assertCancelled(afterOpen, () -> afterOpen.shx.openCount > 0);

        FakeAccess afterSize = validAccess();
        assertCancelled(afterSize, () -> afterSize.shx.channel.sizeCount > 0);

        FakeAccess beforeAllocation = validAccess();
        assertCancelled(beforeAllocation, new BeforePackedAllocationToken(beforeAllocation));

        FakeAccess duringEntryValidation = validAccess();
        assertCancelled(
                duringEntryValidation, () -> duringEntryValidation.shx.channel.readCount > 1);
    }

    @Test
    void closeFailureIsSecondaryToStructuralRejectionAndTerminalCancellation() {
        FakeAccess rejected = validAccess();
        ByteBuffer.wrap(rejected.shx.channel.bytes).putInt(0, 9995);
        rejected.shx.channel.closeFailure = new IOException("cleanup");

        try (FeatureSource source = open(rejected, CancellationToken.none())) {
            assertIgnored(source, "header", 0);
        }
        assertEquals(1, rejected.shp.channel.closeCount);
        assertEquals(1, rejected.shx.channel.closeCount);

        FakeAccess cancelled = validAccess();
        IOException cleanup = new IOException("cleanup");
        cancelled.shx.channel.closeFailure = cleanup;
        CancellationToken token = () -> cancelled.shx.channel.readCount > 0;

        SourceException failure = assertThrows(SourceException.class, () -> open(cancelled, token));

        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanup, failure.getSuppressed()[0]);
        assertEquals(1, cancelled.shp.channel.closeCount);
        assertEquals(1, cancelled.shx.channel.closeCount);
    }

    @Test
    void customTokenFailurePropagatesAfterReverseCleanup() {
        FakeAccess access = validAccess();
        RuntimeException failure = new RuntimeException("token");
        CancellationToken token =
                () -> {
                    if (access.shx.channel.readCount > 0) {
                        throw failure;
                    }
                    return false;
                };

        RuntimeException result = assertThrows(RuntimeException.class, () -> open(access, token));

        assertSame(failure, result);
        assertEquals(1, access.shx.channel.closeCount);
        assertEquals(1, access.shp.channel.closeCount);
    }

    @Test
    void validIndexClosesItsHandleBeforePublishingAndLeavesOnlyShpOwned() {
        FakeAccess access = validAccess();

        FeatureSource source = open(access, CancellationToken.none());

        assertMissingDbfOnly(source);
        assertEquals(1, access.shx.channel.closeCount);
        assertEquals(0, access.shp.channel.closeCount);
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertFalse(cursor.advance());
        }
        source.close();
        assertEquals(1, access.shx.channel.closeCount);
        assertEquals(1, access.shp.channel.closeCount);
    }

    private static FeatureSource open(FakeAccess access, CancellationToken cancellation) {
        return Shapefiles.open(
                IDENTITY, PATH, ShapefileOpenOptions.defaults(), cancellation, access);
    }

    private static void assertCancelled(FakeAccess access, CancellationToken cancellation) {
        SourceException result =
                assertThrows(SourceException.class, () -> open(access, cancellation));
        assertEquals("SOURCE_CANCELLED", result.terminal().code());
        assertTrue(
                result.report().entries().stream()
                        .noneMatch(value -> value.code().equals("SHAPEFILE_SHX_IGNORED")));
        assertEquals(1, access.shx.channel.closeCount);
        assertEquals(1, access.shp.channel.closeCount);
    }

    private static SourceDiagnostic assertIgnoredIo(
            FeatureSource source, String causeKind, long offset) {
        SourceDiagnostic warning = assertIgnored(source, "io", offset);
        assertEquals(causeKind, warning.context().get("causeKind"));
        assertEquals(2, warning.context().size());
        return warning;
    }

    private static SourceDiagnostic assertIgnored(
            FeatureSource source, String reason, long offset) {
        assertEquals(2, source.openingDiagnostics().entries().size());
        SourceDiagnostic warning = source.openingDiagnostics().entries().get(0);
        assertEquals("SHAPEFILE_SHX_IGNORED", warning.code());
        assertEquals(DiagnosticSeverity.WARNING, warning.severity());
        assertEquals("shx", warning.location().orElseThrow().component().orElseThrow());
        assertEquals(reason, warning.context().get("reason"));
        assertTrue(warning.location().orElseThrow().recordNumber().isEmpty());
        if (offset < 0) {
            assertTrue(warning.location().orElseThrow().byteOffset().isEmpty());
        } else {
            assertEquals(offset, warning.location().orElseThrow().byteOffset().orElseThrow());
        }
        assertEquals("SHAPEFILE_DBF_MISSING", source.openingDiagnostics().entries().get(1).code());
        return warning;
    }

    private static void assertMissingDbfOnly(FeatureSource source) {
        assertEquals(1, source.openingDiagnostics().entries().size());
        SourceDiagnostic warning = source.openingDiagnostics().entries().get(0);
        assertEquals("SHAPEFILE_DBF_MISSING", warning.code());
        assertEquals(DiagnosticSeverity.WARNING, warning.severity());
        assertEquals("dbf", warning.location().orElseThrow().component().orElseThrow());
    }

    private static FakeAccess validAccess() {
        byte[] content = ShpFixtures.point(1, 1);
        return new FakeAccess(
                ShpFixtures.file(1, 0, 0, 1, 1, content), ShxFixtures.file(1, 0, 0, 1, 1, content));
    }

    private static final class FakeAccess implements ShapefileFileAccess {
        private final Component shp;
        private final Component shx;

        private FakeAccess(byte[] shpBytes, byte[] shxBytes) {
            shp = new Component(shpBytes);
            shx = new Component(shxBytes);
        }

        @Override
        public boolean exists(Path path) {
            Path fileName = path.getFileName();
            String name = fileName == null ? "" : fileName.toString();
            return name.equals("fixture.shp") || name.equals("fixture.shx");
        }

        @Override
        public boolean isSameFile(Path first, Path second) {
            return first.equals(second);
        }

        @Override
        public Channel open(Path path) throws IOException {
            Path fileName = path.getFileName();
            String name = fileName == null ? "" : fileName.toString();
            Component component = name.endsWith(".shx") ? shx : shp;
            component.openCount++;
            if (component.openFailure != null) {
                throw component.openFailure;
            }
            return component.channel;
        }
    }

    private static final class Component {
        private final FakeChannel channel;
        private IOException openFailure;
        private int openCount;

        private Component(byte[] bytes) {
            channel = new FakeChannel(bytes);
        }
    }

    private static final class FakeChannel implements ShapefileFileAccess.Channel {
        private final byte[] bytes;
        private IOException readFailure;
        private IOException closeFailure;
        private long reportedSize;
        private long sizeAfterFirstCall = -1;
        private long failReadAtOrAfter = Long.MAX_VALUE;
        private int readLimit = Integer.MAX_VALUE;
        private int sizeCount;
        private int readCount;
        private int closeCount;

        private FakeChannel(byte[] bytes) {
            this.bytes = bytes.clone();
            reportedSize = bytes.length;
        }

        @Override
        public long size() {
            sizeCount++;
            return sizeCount > 1 && sizeAfterFirstCall >= 0 ? sizeAfterFirstCall : reportedSize;
        }

        @Override
        public int read(ByteBuffer target, long position) throws IOException {
            readCount++;
            if (readFailure != null && position >= failReadAtOrAfter) {
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

    private static final class BeforePackedAllocationToken implements CancellationToken {
        private final FakeAccess access;
        private int checkpointsAfterHeaderRead;

        private BeforePackedAllocationToken(FakeAccess access) {
            this.access = access;
        }

        @Override
        public boolean isCancellationRequested() {
            if (access.shx.channel.readCount == 1) {
                checkpointsAfterHeaderRead++;
            }
            return checkpointsAfterHeaderRead == 2;
        }
    }
}
