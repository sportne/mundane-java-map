package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DbfFileAccessTest {
    private static final Path PATH = Path.of("fixture.shp");
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");
    private static final DbfFixtures.Field[] FIELDS = {DbfFixtures.field("NAME", 'C', 4, 0)};

    @Test
    void reportsDbfHeaderShortReadAtTheFirstMissingByteAndCleansUpInReverseOrder() {
        FakeAccess access = access(false);
        access.dbf.readLimit = 20;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_DBF_HEADER_INVALID", failure.terminal().code());
        assertEquals("fileLayout", failure.terminal().context().get("field"));
        assertEquals("truncated", failure.terminal().context().get("reason"));
        assertEquals(20, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(List.of("dbf", "shp"), access.closeOrder);
    }

    @Test
    void reportsSelectedFieldShortReadAndLeavesTheSourceReusable() {
        FakeAccess access = access(false);
        try (FeatureSource source = openSource(access)) {
            access.dbf.readLimit = 67;
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("SHAPEFILE_DBF_HEADER_INVALID", failure.terminal().code());
                assertEquals("fileLayout", failure.terminal().context().get("field"));
                assertEquals("truncated", failure.terminal().context().get("reason"));
                assertEquals(
                        67, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
            }
            access.dbf.readLimit = Integer.MAX_VALUE;
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
                assertEquals("name", replacement.current().attributes().get("NAME"));
            }
        }
    }

    @Test
    void detectsDbfSizeMutationOnCursorOpenAndExhaustion() {
        FakeAccess before = access(false);
        try (FeatureSource source = openSource(before)) {
            before.dbf.reportedSize++;
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
            assertEquals("SHAPEFILE_DBF_HEADER_INVALID", failure.terminal().code());
            assertEquals("fileLayout", failure.terminal().context().get("field"));
        }

        FakeAccess exhaustion = access(false);
        try (FeatureSource source = openSource(exhaustion);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            exhaustion.dbf.reportedSize++;
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SHAPEFILE_DBF_HEADER_INVALID", failure.terminal().code());
            assertEquals("fileLayout", failure.terminal().context().get("field"));
        }
    }

    @Test
    void sourceCloseUsesDbfThenShpAndSuppressesTheLaterFailure() {
        FakeAccess access = access(false);
        FeatureSource source = openSource(access);
        IOException dbfFailure = new IOException("dbf close");
        IOException shpFailure = new IOException("shp close");
        access.dbf.closeFailure = dbfFailure;
        access.shp.closeFailure = shpFailure;

        SourceException failure = assertThrows(SourceException.class, source::close);

        assertEquals("SOURCE_CLOSE_FAILED", failure.terminal().code());
        assertSame(dbfFailure, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        SourceException suppressed =
                assertInstanceOf(SourceException.class, failure.getSuppressed()[0]);
        assertSame(shpFailure, suppressed.getCause());
        assertEquals(List.of("dbf", "shp"), access.closeOrder);
        assertTrue(source.isClosed());
    }

    @Test
    void successfulCpgReadFollowedByCloseFailureIsTerminalAndCleansOwnedChannels() {
        FakeAccess access = access(true);
        IOException closeFailure = new IOException("cpg close");
        access.cpg.closeFailure = closeFailure;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals("cpg", failure.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals("close", failure.terminal().context().get("operation"));
        assertSame(closeFailure, failure.getCause());
        assertEquals(List.of("cpg", "dbf", "shp"), access.closeOrder);
    }

    @Test
    void invalidCpgWarningPrecedesATemporaryCloseFailure() {
        FakeAccess access = access(true);
        access.cpg.bytes[0] = '?';
        IOException closeFailure = new IOException("cpg close");
        access.cpg.closeFailure = closeFailure;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals(
                List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_CPG_INVALID", "SHAPEFILE_IO_FAILED"),
                failure.report().entries().stream().map(diagnostic -> diagnostic.code()).toList());
        assertSame(closeFailure, failure.getCause());
        assertEquals(List.of("cpg", "dbf", "shp"), access.closeOrder);
    }

    @Test
    void cpgReadFailureRemainsPrimaryWhenTemporaryCloseAlsoFails() {
        FakeAccess access = access(true);
        IOException readFailure = new IOException("cpg read");
        IOException closeFailure = new IOException("cpg close");
        access.cpg.readFailure = readFailure;
        access.cpg.closeFailure = closeFailure;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals("read", failure.terminal().context().get("operation"));
        assertSame(readFailure, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(closeFailure, failure.getSuppressed()[0]);
        assertEquals(List.of("cpg", "dbf", "shp"), access.closeOrder);
    }

    @Test
    void cancellationDuringSelectedDbfValueReadReleasesTheCursorSlot() {
        FakeAccess access = access(false);
        try (FeatureSource source = openSource(access)) {
            CancellationToken cancellation =
                    () -> access.dbf.lastReadPosition >= 66 && access.dbf.readCount > 4;
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation);

            SourceException failure = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
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

    private static FakeAccess access(boolean withCpg) {
        byte[] shp = ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1));
        byte[] dbf = DbfFixtures.dbf(0x03, 0x57, FIELDS, DbfFixtures.row(' ', FIELDS, "name"));
        return new FakeAccess(
                shp,
                dbf,
                withCpg ? "1252".getBytes(java.nio.charset.StandardCharsets.US_ASCII) : null);
    }

    private static final class FakeAccess implements ShapefileFileAccess {
        private final FakeChannel shp;
        private final FakeChannel dbf;
        private final FakeChannel cpg;
        private final List<String> closeOrder = new ArrayList<>();

        private FakeAccess(byte[] shpBytes, byte[] dbfBytes, byte[] cpgBytes) {
            shp = new FakeChannel("shp", shpBytes, closeOrder);
            dbf = new FakeChannel("dbf", dbfBytes, closeOrder);
            cpg = cpgBytes == null ? null : new FakeChannel("cpg", cpgBytes, closeOrder);
        }

        @Override
        public boolean exists(Path path) {
            String name = Objects.requireNonNull(path.getFileName()).toString();
            return name.equals("fixture.shp")
                    || name.equals("fixture.dbf")
                    || (cpg != null && name.equals("fixture.cpg"));
        }

        @Override
        public boolean isSameFile(Path first, Path second) {
            return first.equals(second);
        }

        @Override
        public Channel open(Path path) {
            String name = Objects.requireNonNull(path.getFileName()).toString();
            return name.endsWith(".shp") ? shp : name.endsWith(".dbf") ? dbf : cpg;
        }
    }

    private static final class FakeChannel implements ShapefileFileAccess.Channel {
        private final String component;
        private final byte[] bytes;
        private final List<String> closeOrder;
        private IOException readFailure;
        private IOException closeFailure;
        private int readLimit = Integer.MAX_VALUE;
        private long reportedSize;
        private long lastReadPosition = -1;
        private int readCount;

        private FakeChannel(String component, byte[] bytes, List<String> closeOrder) {
            this.component = component;
            this.bytes = bytes.clone();
            this.closeOrder = closeOrder;
            reportedSize = bytes.length;
        }

        @Override
        public long size() {
            return reportedSize;
        }

        @Override
        public int read(ByteBuffer target, long position) throws IOException {
            lastReadPosition = position;
            readCount++;
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
            closeOrder.add(component);
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
