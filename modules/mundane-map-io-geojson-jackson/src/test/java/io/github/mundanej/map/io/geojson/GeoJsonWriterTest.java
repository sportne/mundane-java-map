package io.github.mundanej.map.io.geojson;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoJsonWriterTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("writer", "Writer");
    private static final CrsMetadata EPSG_4326 =
            CrsMetadata.recognized(
                    CrsDefinitions.EPSG_4326, Optional.of("EPSG:4326"), Optional.empty());

    @TempDir Path temporaryDirectory;

    @Test
    void writesExactDeterministicBytesAndLeavesTheSourceOpen() throws Exception {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("text", "café");
        attributes.put("flag", true);
        attributes.put("count", 7L);
        attributes.put("decimal", new BigDecimal("1.2500"));
        attributes.put("nothing", AttributeNull.INSTANCE);
        FeatureSource source = source(new FeatureRecord("alpha", "", point(1, 2), attributes));
        Path target = temporaryDirectory.resolve("map.geojson");

        GeoJsonFiles.write(target, source, GeoJsonWriteLimits.defaults(), CancellationToken.none());

        String expected =
                String.join(
                        "",
                        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",",
                        "\"id\":\"alpha\",\"geometry\":{\"type\":\"Point\",",
                        "\"coordinates\":[1.0,2.0]},\"properties\":{\"text\":\"café\",",
                        "\"flag\":true,\"count\":7,\"decimal\":1.25,\"nothing\":null}}]}\n");
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
        Path repeated = temporaryDirectory.resolve("map-repeated.geojson");
        GeoJsonFiles.write(
                repeated, source, GeoJsonWriteLimits.defaults(), CancellationToken.none());
        assertArrayEquals(Files.readAllBytes(target), Files.readAllBytes(repeated));

        FeatureSource reopened =
                GeoJsonFiles.open(
                        target,
                        new SourceIdentity("written", "Written"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        FeatureCursor reopenedCursor =
                reopened.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(reopenedCursor.advance());
        assertEquals(
                Map.of(
                        "text",
                        "café",
                        "flag",
                        true,
                        "count",
                        7L,
                        "decimal",
                        new BigDecimal("1.25"),
                        "nothing",
                        AttributeNull.INSTANCE),
                reopenedCursor.current().attributes());
        reopenedCursor.close();
        reopened.close();
        assertFalse(source.isClosed());
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(cursor.advance());
        cursor.close();
        source.close();
    }

    @Test
    void writesAndReopensAllGeometryFamiliesInOrder() {
        List<FeatureRecord> records = allGeometries();
        FeatureSource source = source(records.toArray(FeatureRecord[]::new));
        Path target = temporaryDirectory.resolve("all.geojson");
        GeoJsonFiles.write(target, source, GeoJsonWriteLimits.defaults(), CancellationToken.none());

        FeatureSource reopened =
                GeoJsonFiles.open(
                        target,
                        new SourceIdentity("reopened", "Reopened"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        List<Geometry> geometries = new ArrayList<>();
        FeatureCursor cursor = reopened.openCursor(FeatureQuery.all(), CancellationToken.none());
        while (cursor.advance()) {
            geometries.add(cursor.current().geometry());
        }
        cursor.close();
        reopened.close();
        assertEquals(records.stream().map(FeatureRecord::geometry).toList(), geometries);
        source.close();
    }

    @Test
    void rejectsCrsAndUnrepresentableValuesBeforeReplacingTarget() throws Exception {
        Path target = temporaryDirectory.resolve("preserved.geojson");
        byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        Files.write(target, old);
        FeatureSource missingCrs =
                InMemoryFeatureSource.open(IDENTITY, List.of(record("a", point(0, 0))));

        GeoJsonWriteException crs =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        missingCrs,
                                        GeoJsonWriteLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOJSON_WRITE_CRS_INVALID", crs.problem().code());
        assertEquals(Map.of("reason", "missing"), crs.problem().context());
        assertArrayEquals(old, Files.readAllBytes(target));
        missingCrs.close();

        FeatureSource named = source(new FeatureRecord("a", "not dropped", point(0, 0), Map.of()));
        GeoJsonWriteException name =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        named,
                                        GeoJsonWriteLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOJSON_WRITE_VALUE_UNREPRESENTABLE", name.problem().code());
        assertEquals(Map.of("field", "name", "reason", "nonEmpty"), name.problem().context());
        assertEquals(List.of("field", "reason"), List.copyOf(name.problem().context().keySet()));
        assertArrayEquals(old, Files.readAllBytes(target));
        named.close();

        FeatureSource date =
                source(
                        new FeatureRecord(
                                "a", "", point(0, 0), Map.of("date", LocalDate.of(2026, 1, 2))));
        GeoJsonWriteException attribute =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        date,
                                        GeoJsonWriteLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals(Map.of("field", "attribute", "reason", "type"), attribute.problem().context());
        assertArrayEquals(old, Files.readAllBytes(target));
        date.close();
    }

    @Test
    void enforcesOutputAndComponentLimitsAndPreservesTheOldTarget() throws Exception {
        Path target = temporaryDirectory.resolve("limited.geojson");
        byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        Files.write(target, old);
        FeatureSource source = source(allGeometries().getLast());
        GeoJsonWriteLimits limited = limits(80, 1);

        GeoJsonWriteException failure =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target, source, limited, CancellationToken.none()));
        assertEquals("GEOJSON_WRITE_LIMIT_EXCEEDED", failure.problem().code());
        assertTrue(
                List.of("outputBytes", "parts").contains(failure.problem().context().get("limit")));
        assertArrayEquals(old, Files.readAllBytes(target));
        source.close();
    }

    @Test
    void alreadyCancelledWriteDoesNotTouchTheTargetOrBorrowACursor() throws Exception {
        Path target = temporaryDirectory.resolve("cancelled.geojson");
        byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        Files.write(target, old);
        FeatureSource source = source(record("a", point(0, 0)));
        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();

        GeoJsonWriteException failure =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        source,
                                        GeoJsonWriteLimits.defaults(),
                                        cancellation.token()));
        assertEquals("GEOJSON_WRITE_CANCELLED", failure.problem().code());
        assertEquals(Map.of("phase", "validate"), failure.problem().context());
        assertArrayEquals(old, Files.readAllBytes(target));
        assertFalse(source.isClosed());
        source.close();
    }

    @Test
    void cancellationDuringEncodingClosesTheCursorAndPreservesTheTarget() throws Exception {
        Path target = temporaryDirectory.resolve("mid-cancel.geojson");
        byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        Files.write(target, old);
        FeatureSource source = source(record("a", point(0, 0)));
        AtomicInteger polls = new AtomicInteger();
        CancellationToken cancellation = () -> polls.incrementAndGet() >= 8;

        GeoJsonWriteException failure =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        source,
                                        GeoJsonWriteLimits.defaults(),
                                        cancellation));

        assertEquals("GEOJSON_WRITE_CANCELLED", failure.problem().code());
        assertArrayEquals(old, Files.readAllBytes(target));
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(cursor.advance());
        cursor.close();
        source.close();
    }

    @Test
    void cancellationBecomingVisibleInsideEachSourceCallStaysCancellation() throws Exception {
        for (String callback :
                List.of(
                        "metadata",
                        "metadataThrow",
                        "open",
                        "advance",
                        "current",
                        "currentThrow")) {
            Path target = temporaryDirectory.resolve("callback-" + callback + ".geojson");
            byte[] old = "old".getBytes(StandardCharsets.UTF_8);
            Files.write(target, old);
            CancellingSource source =
                    new CancellingSource(source(record("a", point(0, 0))), callback);

            GeoJsonWriteException failure =
                    assertThrows(
                            GeoJsonWriteException.class,
                            () ->
                                    GeoJsonFiles.write(
                                            target,
                                            source,
                                            GeoJsonWriteLimits.defaults(),
                                            source.cancellation.token()));

            assertEquals("GEOJSON_WRITE_CANCELLED", failure.problem().code());
            assertEquals(Map.of("phase", "source"), failure.problem().context());
            assertArrayEquals(old, Files.readAllBytes(target));
            if (!callback.startsWith("metadata")) {
                assertTrue(source.cursorClosed);
            }
            source.close();
        }
    }

    @Test
    void rejectsUnsafeTargetKinds() throws Exception {
        FeatureSource source = source(record("a", point(0, 0)));
        Path directory = temporaryDirectory.resolve("directory");
        Files.createDirectory(directory);
        GeoJsonWriteException wrongKind =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        directory,
                                        source,
                                        GeoJsonWriteLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals(Map.of("reason", "wrongKind"), wrongKind.problem().context());
        source.close();
    }

    @Test
    void enforcesNestingAtTheExactGeneratedBoundary() throws Exception {
        FeatureRecord multiPolygon = allGeometries().getLast();
        Path target = temporaryDirectory.resolve("nesting.geojson");
        FeatureSource oneUnder = source(multiPolygon);
        GeoJsonWriteException failure =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        oneUnder,
                                        limits(5_000, 10, 7),
                                        CancellationToken.none()));
        assertEquals("GEOJSON_WRITE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals(
                List.of("limit", "requested", "maximum"),
                List.copyOf(failure.problem().context().keySet()));
        assertEquals(
                Map.of("limit", "nesting", "requested", "8", "maximum", "7"),
                failure.problem().context());
        oneUnder.close();

        FeatureSource exact = source(multiPolygon);
        GeoJsonFiles.write(target, exact, limits(5_000, 10, 8), CancellationToken.none());
        assertTrue(Files.size(target) > 0);
        exact.close();
    }

    @Test
    void forceAndMoveFailuresPreserveTheOldTargetAndCleanTemporaryFiles() throws Exception {
        for (String phase : List.of("force", "move")) {
            Path target = temporaryDirectory.resolve(phase + ".geojson");
            byte[] old = "old".getBytes(StandardCharsets.UTF_8);
            Files.write(target, old);
            FeatureSource source = source(record("a", point(0, 0)));
            TestFiles files = new TestFiles(phase, false);

            GeoJsonWriteException failure =
                    assertThrows(
                            GeoJsonWriteException.class,
                            () ->
                                    GeoJsonWriter.write(
                                            target,
                                            source,
                                            GeoJsonWriteLimits.defaults(),
                                            CancellationToken.none(),
                                            files));

            assertEquals("GEOJSON_WRITE_FAILED", failure.problem().code());
            assertEquals(phase, failure.problem().context().get("phase"));
            assertArrayEquals(old, Files.readAllBytes(target));
            assertFalse(Files.exists(files.temporary));
            source.close();
        }
    }

    @Test
    void cleanupFailureIsSuppressedBehindThePrimaryWriteFailure() throws Exception {
        Path target = temporaryDirectory.resolve("cleanup.geojson");
        byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        Files.write(target, old);
        FeatureSource source = source(record("a", point(0, 0)));
        TestFiles files = new TestFiles("force", true);

        GeoJsonWriteException failure =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonWriter.write(
                                        target,
                                        source,
                                        GeoJsonWriteLimits.defaults(),
                                        CancellationToken.none(),
                                        files));

        assertEquals("force", failure.problem().context().get("phase"));
        assertEquals(1, failure.getSuppressed().length);
        GeoJsonWriteException cleanup =
                assertInstanceOf(GeoJsonWriteException.class, failure.getSuppressed()[0]);
        assertEquals("cleanup", cleanup.problem().context().get("phase"));
        assertArrayEquals(old, Files.readAllBytes(target));
        Files.deleteIfExists(files.temporary);
        source.close();
    }

    @Test
    void sourceAdvanceFailureClosesTheBorrowedCursorAndPreservesTheTarget() throws Exception {
        Path target = temporaryDirectory.resolve("source-failure.geojson");
        byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        Files.write(target, old);
        FailingAdvanceSource source = new FailingAdvanceSource(source(record("a", point(0, 0))));

        GeoJsonWriteException failure =
                assertThrows(
                        GeoJsonWriteException.class,
                        () ->
                                GeoJsonFiles.write(
                                        target,
                                        source,
                                        GeoJsonWriteLimits.defaults(),
                                        CancellationToken.none()));

        assertEquals("GEOJSON_WRITE_SOURCE_FAILED", failure.problem().code());
        assertEquals(Map.of("phase", "advance"), failure.problem().context());
        assertTrue(source.cursorClosed);
        assertFalse(source.isClosed());
        assertArrayEquals(old, Files.readAllBytes(target));
        source.close();
    }

    private static FeatureSource source(FeatureRecord... records) {
        return InMemoryFeatureSource.open(
                IDENTITY,
                List.of(records),
                Optional.empty(),
                Optional.of(EPSG_4326),
                FeatureSourceLimits.LEVEL_1);
    }

    private static FeatureRecord record(String id, io.github.mundanej.map.api.Geometry geometry) {
        return new FeatureRecord(id, "", geometry, Map.of());
    }

    private static PointGeometry point(double x, double y) {
        return new PointGeometry(new io.github.mundanej.map.api.Coordinate(x, y));
    }

    private static List<FeatureRecord> allGeometries() {
        CoordinateSequence line = CoordinateSequence.of(0, 0, 1, 1);
        CoordinateSequence ring = CoordinateSequence.of(0, 0, 1, 0, 1, 1, 0, 0);
        return List.of(
                record("point", point(0, 0)),
                record("multipoint", new MultiPointGeometry(line)),
                record("line", new LineStringGeometry(line)),
                record(
                        "multiline",
                        MultiLineStringGeometry.ofParts(
                                List.of(line, CoordinateSequence.of(2, 2, 3, 3)))),
                record("polygon", new PolygonGeometry(ring)),
                record(
                        "multipolygon",
                        MultiPolygonGeometry.ofPolygons(
                                List.of(
                                        new PolygonGeometry(ring),
                                        new PolygonGeometry(
                                                CoordinateSequence.of(2, 2, 3, 2, 3, 3, 2, 2))))));
    }

    private static GeoJsonWriteLimits limits(long bytes, int parts) {
        return limits(bytes, parts, 8);
    }

    private static GeoJsonWriteLimits limits(long bytes, int parts, int nesting) {
        return new GeoJsonWriteLimits(
                bytes, 20_000, nesting, 10, 20, 20, parts, 10, 10, 100, 200, 32);
    }

    private static final class TestFiles implements GeoJsonWriter.FileOperations {
        private final String failPhase;
        private final boolean failCleanup;
        private String phase = "temporary";
        private Path temporary;

        private TestFiles(String failPhase, boolean failCleanup) {
            this.failPhase = failPhase;
            this.failCleanup = failCleanup;
        }

        @Override
        public Path createTemporary(Path parent) throws IOException {
            phase = "temporary";
            temporary = Files.createTempFile(parent, ".geojson-test-", ".tmp");
            return temporary;
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public void write(Path path, GeoJsonWriter.Encoded encoded, CancellationToken cancellation)
                throws IOException {
            phase = "write";
            try (FileChannel channel =
                    FileChannel.open(
                            path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded.bytes(), 0, encoded.length());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        }

        @Override
        public void force(Path path) throws IOException {
            phase = "force";
            if (failPhase.equals(phase)) {
                throw new IOException("injected force failure");
            }
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }

        @Override
        public void move(Path temporary, Path target) throws IOException {
            phase = "move";
            if (failPhase.equals(phase)) {
                throw new IOException("injected move failure");
            }
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void delete(Path path) throws IOException {
            phase = "cleanup";
            if (failCleanup) {
                throw new IOException("injected cleanup failure");
            }
            Files.deleteIfExists(path);
        }

        @Override
        public String failurePhase() {
            return phase;
        }
    }

    private static final class FailingAdvanceSource implements FeatureSource {
        private final FeatureSource delegate;
        private boolean cursorClosed;

        private FailingAdvanceSource(FeatureSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            FeatureCursor cursor = delegate.openCursor(query, cancellation);
            return new FeatureCursor() {
                private int advances;

                @Override
                public boolean advance() {
                    if (++advances == 2) {
                        throw new IllegalStateException("injected source failure");
                    }
                    return cursor.advance();
                }

                @Override
                public FeatureRecord current() {
                    return cursor.current();
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return cursor.diagnostics();
                }

                @Override
                public boolean isClosed() {
                    return cursor.isClosed();
                }

                @Override
                public void close() {
                    cursorClosed = true;
                    cursor.close();
                }
            };
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class CancellingSource implements FeatureSource {
        private final FeatureSource delegate;
        private final String callback;
        private final CancellationSource cancellation = new CancellationSource();
        private boolean cursorClosed;

        private CancellingSource(FeatureSource delegate, String callback) {
            this.delegate = delegate;
            this.callback = callback;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            FeatureSourceMetadata metadata = delegate.metadata();
            cancel("metadata");
            if (callback.equals("metadataThrow")) {
                cancellation.cancel();
                throw sourceCancellation();
            }
            return metadata;
        }

        @Override
        public FeatureSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken token) {
            FeatureCursor cursor = delegate.openCursor(query, token);
            cancel("open");
            return new FeatureCursor() {
                @Override
                public boolean advance() {
                    boolean advanced = cursor.advance();
                    cancel("advance");
                    return advanced;
                }

                @Override
                public FeatureRecord current() {
                    FeatureRecord record = cursor.current();
                    cancel("current");
                    if (callback.equals("currentThrow")) {
                        cancellation.cancel();
                        throw sourceCancellation();
                    }
                    return record;
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return cursor.diagnostics();
                }

                @Override
                public boolean isClosed() {
                    return cursor.isClosed();
                }

                @Override
                public void close() {
                    cursorClosed = true;
                    cursor.close();
                }
            };
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }

        private void cancel(String current) {
            if (callback.equals(current)) {
                cancellation.cancel();
            }
        }

        private static io.github.mundanej.map.api.SourceException sourceCancellation() {
            return GeoJsonReader.failure(
                    "writer", "SOURCE_CANCELLED", "Injected source cancellation", Map.of());
        }
    }
}
