package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
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

class PrjFileAccessTest {
    private static final Path PATH = Path.of("fixture.shp");
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @Test
    void mapsPrjOpenSizeReadAndTemporaryCloseFailuresToExactOperations() {
        FakeAccess open = validAccess(false);
        IOException openCause = new IOException("open");
        open.prj.openFailure = openCause;
        assertIoFailure(open, "open", openCause);
        assertEquals(List.of("shp"), open.closeOrder);

        FakeAccess size = validAccess(false);
        IOException sizeCause = new IOException("size");
        size.prj.channel.sizeFailure = sizeCause;
        assertIoFailure(size, "size", sizeCause);
        assertEquals(List.of("prj", "shp"), size.closeOrder);

        FakeAccess read = validAccess(false);
        IOException readCause = new IOException("read");
        read.prj.channel.readFailure = readCause;
        assertIoFailure(read, "read", readCause);
        assertEquals(List.of("prj", "shp"), read.closeOrder);

        FakeAccess close = validAccess(false);
        IOException closeCause = new IOException("close");
        close.prj.channel.closeFailure = closeCause;
        SourceException closeFailure = assertIoFailure(close, "close", closeCause);
        assertTrue(closeFailure.terminal().location().orElseThrow().byteOffset().isEmpty());
        assertEquals(List.of("prj", "shp"), close.closeOrder);
    }

    @Test
    void distinguishesNonThrowingShortReadAndCapturedSizeMutation() {
        FakeAccess truncated = validAccess(false);
        truncated.prj.channel.readLimit = 17;
        SourceException shortRead = assertThrows(SourceException.class, () -> open(truncated));
        assertPrjInvalid(shortRead, "truncated", 17);
        assertEquals(List.of("prj", "shp"), truncated.closeOrder);

        FakeAccess mutated = validAccess(false);
        mutated.prj.channel.sizeAfterFirstCall = mutated.prj.channel.bytes.length + 1L;
        SourceException sizeChanged = assertThrows(SourceException.class, () -> open(mutated));
        assertPrjInvalid(sizeChanged, "sizeChanged", 0);
        assertEquals(
                Integer.toString(mutated.prj.channel.bytes.length),
                sizeChanged.terminal().context().get("capturedBytes"));
        assertEquals(
                Integer.toString(mutated.prj.channel.bytes.length + 1),
                sizeChanged.terminal().context().get("actualBytes"));
        assertEquals(List.of("prj", "shp"), mutated.closeOrder);
    }

    @Test
    void retainsPrimaryFailureAndClosesPrjDbfThenShpInReverseOwnershipOrder() {
        FakeAccess access = validAccess(true);
        IOException primary = new IOException("read");
        IOException dbfCleanup = new IOException("dbf close");
        IOException shpCleanup = new IOException("shp close");
        access.prj.channel.readFailure = primary;
        access.dbf.channel.closeFailure = dbfCleanup;
        access.shp.channel.closeFailure = shpCleanup;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals("prj", failure.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals("read", failure.terminal().context().get("operation"));
        assertSame(primary, failure.getCause());
        assertEquals(2, failure.getSuppressed().length);
        assertSame(dbfCleanup, failure.getSuppressed()[0]);
        assertSame(shpCleanup, failure.getSuppressed()[1]);
        assertEquals(List.of("prj", "dbf", "shp"), access.closeOrder);
    }

    @Test
    void temporaryPrjCloseFailureIsSuppressedWhenReadAlreadyFailed() {
        FakeAccess access = validAccess(false);
        IOException primary = new IOException("read");
        IOException cleanup = new IOException("close");
        access.prj.channel.readFailure = primary;
        access.prj.channel.closeFailure = cleanup;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertSame(primary, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanup, failure.getSuppressed()[0]);
        assertEquals(List.of("prj", "shp"), access.closeOrder);
    }

    @Test
    void cancellationAfterPrjReadCleansEveryAcquiredResourceAndPreservesWarnings() {
        FakeAccess access = validAccess(true);
        CancellationToken cancellation = () -> access.prj.channel.readCount > 0;

        SourceException failure =
                assertThrows(SourceException.class, () -> open(access, cancellation));

        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals("SHAPEFILE_SHX_MISSING", failure.report().entries().get(0).code());
        assertEquals("SHAPEFILE_ENCODING_FALLBACK", failure.report().entries().get(1).code());
        assertEquals(failure.terminal(), failure.report().entries().get(2));
        assertEquals(List.of("prj", "dbf", "shp"), access.closeOrder);
    }

    @Test
    void cancellationImmediatelyAfterPrjOpenOrSizeClosesTheTemporaryChannel() {
        FakeAccess afterOpen = validAccess(true);
        assertCancelled(afterOpen, () -> afterOpen.prj.openCount > 0);

        FakeAccess afterSize = validAccess(true);
        assertCancelled(afterSize, () -> afterSize.prj.channel.sizeCount > 0);
    }

    @Test
    void largePrjReadsAreChunkedForCancellation() {
        byte[] prj = PrjFixtures.utf8("A[\"" + "x".repeat(9000) + "\"]");
        FakeAccess access = new FakeAccess(ShpFixtures.file(0, 0, 0, 0, 0), null, prj);
        CancellationToken cancellation = () -> access.prj.channel.readCount > 1;

        SourceException failure =
                assertThrows(SourceException.class, () -> open(access, cancellation));

        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals(2, access.prj.channel.readCount);
        assertEquals(List.of("prj", "shp"), access.closeOrder);
    }

    @Test
    void customCancellationFailurePropagatesAfterReverseCleanup() {
        FakeAccess access = validAccess(true);
        MarkerFailure marker = new MarkerFailure();
        CancellationToken cancellation =
                () -> {
                    if (access.prj.channel.readCount > 0) {
                        throw marker;
                    }
                    return false;
                };

        assertSame(marker, assertThrows(MarkerFailure.class, () -> open(access, cancellation)));
        assertEquals(List.of("prj", "dbf", "shp"), access.closeOrder);
    }

    @Test
    void successfulOpeningDiscardsPrjTemporariesAndMetadataSurvivesSourceClose() {
        FakeAccess access = validAccess(true);

        FeatureSource source = open(access);
        CrsMetadata metadata = source.metadata().crs().orElseThrow();

        assertEquals("EPSG:4326", metadata.canonicalIdentifier().orElseThrow());
        assertEquals(PrjFixtures.EPSG_4326, metadata.retainedDefinition().orElseThrow());
        assertEquals(List.of("prj"), access.closeOrder);
        assertEquals(1, access.prj.channel.closeCount);
        source.close();
        assertTrue(source.isClosed());
        assertEquals(List.of("prj", "dbf", "shp"), access.closeOrder);
        assertEquals(
                "EPSG:4326",
                source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
        assertEquals(
                PrjFixtures.EPSG_4326,
                source.metadata().crs().orElseThrow().retainedDefinition().orElseThrow());
        source.close();
        assertEquals(List.of("prj", "dbf", "shp"), access.closeOrder);
    }

    private static SourceException assertIoFailure(
            FakeAccess access, String operation, IOException cause) {
        SourceException failure = assertThrows(SourceException.class, () -> open(access));
        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals("prj", failure.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals(operation, failure.terminal().context().get("operation"));
        assertSame(cause, failure.getCause());
        return failure;
    }

    private static void assertCancelled(FakeAccess access, CancellationToken cancellation) {
        SourceException failure =
                assertThrows(SourceException.class, () -> open(access, cancellation));
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals(List.of("prj", "dbf", "shp"), access.closeOrder);
    }

    private static void assertPrjInvalid(SourceException failure, String reason, long offset) {
        assertEquals("SHAPEFILE_PRJ_INVALID", failure.terminal().code());
        assertEquals(reason, failure.terminal().context().get("reason"));
        assertEquals("prj", failure.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
    }

    private static FeatureSource open(FakeAccess access) {
        return open(access, CancellationToken.none());
    }

    private static FeatureSource open(FakeAccess access, CancellationToken cancellation) {
        return Shapefiles.open(
                IDENTITY, PATH, ShapefileOpenOptions.defaults(), cancellation, access);
    }

    private static FakeAccess validAccess(boolean withDbf) {
        return new FakeAccess(
                ShpFixtures.file(0, 0, 0, 0, 0),
                withDbf ? DbfFixtures.dbf(0x03, 0, new DbfFixtures.Field[0]) : null,
                PrjFixtures.utf8(PrjFixtures.EPSG_4326));
    }

    private static final class FakeAccess implements ShapefileFileAccess {
        private final List<String> closeOrder = new ArrayList<>();
        private final Component shp;
        private final Component dbf;
        private final Component prj;

        private FakeAccess(byte[] shpBytes, byte[] dbfBytes, byte[] prjBytes) {
            shp = new Component("shp", shpBytes, closeOrder);
            dbf = dbfBytes == null ? null : new Component("dbf", dbfBytes, closeOrder);
            prj = new Component("prj", prjBytes, closeOrder);
        }

        @Override
        public boolean exists(Path path) {
            String name = Objects.requireNonNull(path.getFileName()).toString();
            return name.equals("fixture.shp")
                    || (dbf != null && name.equals("fixture.dbf"))
                    || name.equals("fixture.prj");
        }

        @Override
        public boolean isSameFile(Path first, Path second) {
            return first.equals(second);
        }

        @Override
        public Channel open(Path path) throws IOException {
            String name = Objects.requireNonNull(path.getFileName()).toString();
            Component component = name.endsWith(".prj") ? prj : name.endsWith(".dbf") ? dbf : shp;
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

        private Component(String name, byte[] bytes, List<String> closeOrder) {
            channel = new FakeChannel(name, bytes, closeOrder);
        }
    }

    private static final class FakeChannel implements ShapefileFileAccess.Channel {
        private final String name;
        private final byte[] bytes;
        private final List<String> closeOrder;
        private IOException sizeFailure;
        private IOException readFailure;
        private IOException closeFailure;
        private long sizeAfterFirstCall = -1;
        private int readLimit = Integer.MAX_VALUE;
        private int sizeCount;
        private int readCount;
        private int closeCount;

        private FakeChannel(String name, byte[] bytes, List<String> closeOrder) {
            this.name = name;
            this.bytes = bytes.clone();
            this.closeOrder = closeOrder;
        }

        @Override
        public long size() throws IOException {
            sizeCount++;
            if (sizeFailure != null) {
                throw sizeFailure;
            }
            return sizeCount > 1 && sizeAfterFirstCall >= 0 ? sizeAfterFirstCall : bytes.length;
        }

        @Override
        public int read(ByteBuffer target, long position) throws IOException {
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
            closeCount++;
            closeOrder.add(name);
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static final class MarkerFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
