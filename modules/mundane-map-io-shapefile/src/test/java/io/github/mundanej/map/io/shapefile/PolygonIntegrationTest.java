package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolygonIntegrationTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @TempDir Path directory;
    private int failureFixture;

    @Test
    void mapsOneShellAndSourceDisorderedHolesToASingularPolygon() throws Exception {
        double[] holeOne = hole(2, 2, 4, 4);
        double[] shell = shell(0, 0, 10, 10);
        double[] holeTwo = hole(6, 6, 8, 8);
        byte[] content = polygon(holeOne, shell, holeTwo);
        Path path = dataset("singular", 0, 0, 10, 10, content);

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            FeatureRecord record = cursor.current();
            assertEquals("record:1", record.id());
            PolygonGeometry polygon = assertInstanceOf(PolygonGeometry.class, record.geometry());
            assertCoordinates(shell, polygon.exterior().toArray());
            assertEquals(2, polygon.holes().size());
            assertCoordinates(holeOne, polygon.holes().get(0).toArray());
            assertCoordinates(holeTwo, polygon.holes().get(1).toArray());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void mapsDisjointShellsAndTheirHolesToStablePackedPolygonGroups() throws Exception {
        double[] holeB = hole(22, 2, 24, 4);
        double[] shellA = shell(0, 0, 10, 10);
        double[] shellB = shell(20, 0, 30, 10);
        double[] holeA = hole(6, 6, 8, 8);
        byte[] content = polygon(holeB, shellA, shellB, holeA);
        Path path = dataset("multipart", 0, 0, 30, 10, ShpFixtures.nullShape(), content);

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals("record:2", cursor.current().id());
            MultiPolygonGeometry polygons =
                    assertInstanceOf(MultiPolygonGeometry.class, cursor.current().geometry());
            assertEquals(2, polygons.polygonCount());
            assertEquals(List.of(0, 5, 10, 15, 20), boxed(polygons.ringOffsets()));
            assertEquals(List.of(0, 2, 4), boxed(polygons.polygonRingOffsets()));
            assertCoordinates(join(shellA, holeA, shellB, holeB), polygons.coordinates().toArray());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void treatsNestedClockwiseIslandsAsIndependentComponents() throws Exception {
        double[] outer = shell(0, 0, 20, 20);
        double[] hole = hole(2, 2, 18, 18);
        double[] island = shell(6, 6, 14, 14);
        Path path = dataset("island", 0, 0, 20, 20, polygon(outer, hole, island));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            MultiPolygonGeometry polygons =
                    assertInstanceOf(MultiPolygonGeometry.class, cursor.current().geometry());
            assertEquals(List.of(0, 2, 3), boxed(polygons.polygonRingOffsets()));
            assertCoordinates(join(outer, hole, island), polygons.coordinates().toArray());
        }
    }

    @Test
    void assignsASourceDisorderedHoleToTheSmallestUnequalContainingShell() throws Exception {
        double[] containedHole = hole(8, 8, 12, 12);
        double[] outerShell = shell(0, 0, 20, 20);
        double[] innerShell = shell(5, 5, 15, 15);
        Path path =
                dataset(
                        "smallest-shell",
                        0,
                        0,
                        20,
                        20,
                        polygon(containedHole, outerShell, innerShell));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            MultiPolygonGeometry polygons =
                    assertInstanceOf(MultiPolygonGeometry.class, cursor.current().geometry());
            assertEquals(List.of(0, 1, 3), boxed(polygons.polygonRingOffsets()));
            assertCoordinates(
                    join(outerShell, innerShell, containedHole), polygons.coordinates().toArray());
        }
    }

    @Test
    void acceptsCanonicalSignedZeroClosureAndConsecutiveDuplicates() throws Exception {
        double[] ring = {-0.0, -0.0, 0, 4, 0, 4, 4, 4, 4, 0, +0.0, +0.0};
        Path path = dataset("canonical", 0, 0, 4, 4, polygon(ring));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            PolygonGeometry polygon =
                    assertInstanceOf(PolygonGeometry.class, cursor.current().geometry());
            assertEquals(6, polygon.exterior().size());
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(polygon.exterior().x(0)));
            assertEquals(polygon.exterior().coordinate(1), polygon.exterior().coordinate(2));
        }
    }

    @Test
    void retainsExplicitStructuralNonGoals() throws Exception {
        double[] selfCrossing = {0, 0, 3, 0, 0, 4, 4, 4, 0, 0};
        Path self = dataset("self-crossing", 0, 0, 4, 4, polygon(selfCrossing));
        assertEquals(PolygonGeometry.class, firstGeometry(self).getClass());

        double[] shellOne = shell(0, 0, 10, 10);
        double[] shellTwo = shell(5, 0, 15, 10);
        Path overlappingShells =
                dataset("overlapping-shells", 0, 0, 15, 10, polygon(shellOne, shellTwo));
        assertEquals(MultiPolygonGeometry.class, firstGeometry(overlappingShells).getClass());

        double[] outer = shell(0, 0, 20, 20);
        double[] holeOne = hole(2, 2, 12, 12);
        double[] holeTwo = hole(8, 8, 18, 18);
        Path overlappingHoles =
                dataset("overlapping-holes", 0, 0, 20, 20, polygon(outer, holeOne, holeTwo));
        PolygonGeometry value =
                assertInstanceOf(PolygonGeometry.class, firstGeometry(overlappingHoles));
        assertEquals(2, value.holes().size());
    }

    @Test
    void computesExactAreaForSubnormalAndExtremeFiniteCoordinates() throws Exception {
        double tiny = Double.MIN_VALUE;
        double extent = tiny * 4;
        Path small =
                dataset(
                        "subnormal",
                        0,
                        0,
                        extent,
                        extent,
                        polygon(shell(0, 0, extent, extent), hole(tiny, tiny, tiny * 3, tiny * 3)));
        PolygonGeometry smallPolygon =
                assertInstanceOf(PolygonGeometry.class, firstGeometry(small));
        assertEquals(1, smallPolygon.holes().size());

        double maximum = Double.MAX_VALUE;
        Path large =
                dataset(
                        "extreme",
                        0,
                        0,
                        maximum,
                        maximum,
                        polygon(shell(0, 0, maximum, maximum), hole(1, 1, 2, 2)));
        PolygonGeometry largePolygon =
                assertInstanceOf(PolygonGeometry.class, firstGeometry(large));
        assertEquals(1, largePolygon.holes().size());
    }

    @Test
    void rejectsAggregateSpanOpenAndZeroAreaRingsWithExactLocations() throws Exception {
        assertFailure(
                contentWithCounts(2, 7),
                defaults(),
                "SHAPEFILE_RING_INVALID",
                148,
                OptionalInt.empty(),
                Map.of("reason", "tooShort"));

        assertFailure(
                ShpFixtures.polygon(new int[] {0, 3}, points(8)),
                defaults(),
                "SHAPEFILE_RING_INVALID",
                152,
                OptionalInt.of(0),
                Map.of("reason", "tooShort"));

        double[] open = {0, 0, 0, 4, 4, 4, 4, 0, 1, 0};
        assertFailure(
                polygon(open),
                defaults(),
                "SHAPEFILE_RING_INVALID",
                220,
                OptionalInt.of(0),
                Map.of("reason", "open"));

        double[] zeroArea = {0, 0, 1, 1, 2, 2, 0, 0};
        assertFailure(
                polygon(zeroArea),
                defaults(),
                "SHAPEFILE_RING_INVALID",
                152,
                OptionalInt.of(0),
                Map.of("reason", "zeroArea"));
    }

    @Test
    void retainsCommonMultipartDiagnosticPrecedenceForPolygonRecords() throws Exception {
        assertFailure(
                ShpFixtures.polygon(new int[] {1}, shell(0, 0, 3, 3)),
                defaults(),
                "SHAPEFILE_PART_TABLE_INVALID",
                152,
                OptionalInt.of(0),
                Map.of("reason", "firstNotZero"));
        assertFailure(
                ShpFixtures.polygon(new int[] {0, 0}, join(shell(0, 0, 3, 3), shell(4, 0, 7, 3))),
                defaults(),
                "SHAPEFILE_PART_TABLE_INVALID",
                156,
                OptionalInt.of(1),
                Map.of("reason", "notIncreasing"));

        byte[] nonFinite = polygon(shell(0, 0, 3, 3));
        ByteBuffer.wrap(nonFinite).order(ByteOrder.LITTLE_ENDIAN).putDouble(48, Double.NaN);
        assertFailure(
                nonFinite,
                defaults(),
                "SHAPEFILE_COORDINATE_NON_FINITE",
                156,
                OptionalInt.empty(),
                Map.of("axis", "x"));

        byte[] outsideRecord = polygon(shell(0, 0, 3, 3));
        ByteBuffer.wrap(outsideRecord).order(ByteOrder.LITTLE_ENDIAN).putDouble(20, 2);
        SourceException boundsFailure =
                failure(outsideRecord, defaults(), new Envelope(0, 0, 3, 3), FeatureQuery.all());
        assertDiagnostic(
                boundsFailure.terminal(),
                "SHAPEFILE_BOUNDS_MISMATCH",
                188,
                OptionalInt.empty(),
                Map.of("bounds", "record"));
    }

    @Test
    void validatesEveryRingBeforeClassifyingAnEarlierOrphanHole() throws Exception {
        double[] orphan = hole(20, 20, 22, 22);
        double[] openShell = {0, 0, 0, 10, 10, 10, 10, 0, 1, 0};
        SourceException failure =
                failure(
                        polygon(orphan, openShell),
                        defaults(),
                        new Envelope(0, 0, 22, 22),
                        FeatureQuery.all());

        assertDiagnostic(
                failure.terminal(),
                "SHAPEFILE_RING_INVALID",
                304,
                OptionalInt.of(1),
                Map.of("reason", "open"));
    }

    @Test
    void rejectsOrphanContactAndEqualInnermostHoleAssociations() throws Exception {
        assertTopologyFailure(polygon(hole(20, 20, 22, 22), shell(0, 0, 10, 10)), 0, 152, "orphan");

        double[] touchingHole = {0, 2, 3, 2, 3, 4, 0, 4, 0, 2};
        assertTopologyFailure(polygon(shell(0, 0, 10, 10), touchingHole), 1, 156, "contact");

        double[] concaveShell = {0, 0, 0, 10, 4, 10, 4, 4, 6, 4, 6, 10, 10, 10, 10, 0, 0, 0};
        double[] firstPointOutsideCrossing = {5, 6, 3, 5, 7, 5, 5, 6};
        assertTopologyFailure(polygon(concaveShell, firstPointOutsideCrossing), 1, 156, "contact");

        assertTopologyFailure(
                polygon(shell(0, 0, 10, 10), shell(0, 0, 10, 10), hole(2, 2, 4, 4)),
                2,
                160,
                "equalInnermost");
    }

    @Test
    void enforcesTopologyComparisonMinusEqualAndPlusOneBoundaries() throws Exception {
        byte[] content = polygon(shell(0, 0, 10, 10), hole(2, 2, 4, 4));
        Path accepted = dataset("topology-equal", 0, 0, 10, 10, content);
        try (FeatureSource source = open(accepted, defaults().withMaximumTopologyComparisons(21));
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
        }

        SourceException failure =
                failure(
                        content,
                        defaults().withMaximumTopologyComparisons(20),
                        new Envelope(0, 0, 10, 10),
                        FeatureQuery.all());
        assertDiagnostic(
                failure.terminal(),
                "SOURCE_LIMIT_EXCEEDED",
                156,
                OptionalInt.of(1),
                Map.of("limit", "topologyComparisons", "maximum", "20", "requested", "21"));
    }

    @Test
    void prospectivelyChargesTheCompleteCountDerivedPolygonReservation() throws Exception {
        byte[] content = polygon(shell(0, 0, 10, 10));
        FeatureQuery disjoint = disjointQuery();
        Path accepted = dataset("allocation-equal", 0, 0, 10, 10, content);
        try (FeatureSource source =
                        open(accepted, defaults().withMaximumParserAllocationBytes(1476));
                FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
            assertFalse(cursor.advance());
        }

        SourceException failure =
                failure(
                        content,
                        defaults().withMaximumParserAllocationBytes(1475),
                        new Envelope(0, 0, 10, 10),
                        disjoint);
        assertDiagnostic(
                failure.terminal(),
                "SOURCE_LIMIT_EXCEEDED",
                144,
                OptionalInt.empty(),
                Map.of("limit", "parserAllocationBytes", "maximum", "1475", "requested", "1476"));
    }

    @Test
    void parserAllocationReservationIsCumulativeAcrossFilteredPolygonRecords() throws Exception {
        byte[] content = polygon(shell(0, 0, 10, 10));
        Path path = dataset("cumulative-allocation", 0, 0, 10, 10, content, content);

        try (FeatureSource source = open(path, defaults().withMaximumParserAllocationBytes(2864));
                FeatureCursor cursor =
                        source.openCursor(disjointQuery(), CancellationToken.none())) {
            assertFalse(cursor.advance());
        }

        try (FeatureSource source = open(path, defaults().withMaximumParserAllocationBytes(2863));
                FeatureCursor cursor =
                        source.openCursor(disjointQuery(), CancellationToken.none())) {
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("parserAllocationBytes", failure.terminal().context().get("limit"));
            assertEquals("2863", failure.terminal().context().get("maximum"));
            assertEquals("2864", failure.terminal().context().get("requested"));
            assertEquals(
                    280, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
            assertEquals(
                    2, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
        }
    }

    @Test
    void checksExactAreaCapacityBeforeAggregateSizeAndDerivedLength() throws Exception {
        int parts = Integer.MAX_VALUE / 133 + 1;
        int points = Math.multiplyExact(parts, 4);
        ShapefileLimits limits = defaults().withMaximumParts(parts).withMaximumPoints(points);

        assertFailure(
                contentWithCounts(parts, points),
                limits,
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                144,
                OptionalInt.empty(),
                Map.of("reason", "arrayCapacity"));
    }

    @Test
    void fullyValidatesMalformedOffQueryRecordsAndLeavesTheSourceReusable() throws Exception {
        byte[] malformed = polygon(new double[] {0, 0, 1, 1, 2, 2, 0, 0});
        Path path = dataset("filtered-malformed", 0, 0, 2, 2, malformed);

        try (FeatureSource source = open(path)) {
            try (FeatureCursor cursor =
                    source.openCursor(disjointQuery(), CancellationToken.none())) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("zeroArea", failure.terminal().context().get("reason"));
            }
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                SourceException repeated =
                        assertThrows(SourceException.class, replacement::advance);
                assertEquals("zeroArea", repeated.terminal().context().get("reason"));
            }
        }
    }

    @Test
    void cancellationAtSingularOutputGroupingReleasesTheCursorSlot() throws Exception {
        Path path =
                dataset(
                        "output-cancellation",
                        0,
                        0,
                        10,
                        10,
                        polygon(hole(2, 2, 4, 4), shell(0, 0, 10, 10)));
        CancellationToken cancellation =
                () ->
                        StackWalker.getInstance()
                                .walk(
                                        frames ->
                                                frames.anyMatch(
                                                        frame ->
                                                                frame.getClassName()
                                                                                .equals(
                                                                                        PolygonDecoder
                                                                                                .class
                                                                                                .getName())
                                                                        && frame.getMethodName()
                                                                                .equals(
                                                                                        "buildPolygon")));

        try (FeatureSource source = open(path)) {
            try (FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation)) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            }
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    @Test
    void indexedAndSequentialPathsReturnEquivalentPolygonValuesAndFailures() throws Exception {
        byte[][] contents = {
            ShpFixtures.nullShape(),
            polygon(shell(0, 0, 10, 10), hole(2, 2, 4, 4)),
            polygon(shell(20, 0, 30, 10), shell(40, 0, 50, 10))
        };
        Path sequential = dataset("sequential", 0, 0, 50, 10, contents);
        Path indexed = dataset("indexed", 0, 0, 50, 10, contents);
        Files.write(directory.resolve("indexed.shx"), ShxFixtures.file(5, 0, 0, 50, 10, contents));

        assertEquals(readSummaries(sequential), readSummaries(indexed));

        byte[] malformed = polygon(new double[] {0, 0, 1, 1, 2, 2, 0, 0});
        Path badSequential = dataset("bad-sequential", 0, 0, 2, 2, malformed);
        Path badIndexed = dataset("bad-indexed", 0, 0, 2, 2, malformed);
        Files.write(
                directory.resolve("bad-indexed.shx"), ShxFixtures.file(5, 0, 0, 2, 2, malformed));
        assertEquals(firstFailure(badSequential).terminal(), firstFailure(badIndexed).terminal());
    }

    @Test
    void saturatesTheTopologyIncrementSeamBeforeOverflow() {
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of("shp"),
                        OptionalLong.of(1),
                        OptionalInt.of(2),
                        OptionalInt.empty(),
                        Optional.empty(),
                        OptionalLong.of(160));

        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                PolygonDecoder.checkTopologyIncrement(
                                        IDENTITY, location, Long.MAX_VALUE, Long.MAX_VALUE));

        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("topologyComparisons", failure.terminal().context().get("limit"));
        assertEquals(Long.toString(Long.MAX_VALUE), failure.terminal().context().get("maximum"));
        assertEquals(Long.toString(Long.MAX_VALUE), failure.terminal().context().get("requested"));
        assertEquals(location, failure.terminal().location().orElseThrow());
    }

    private void assertTopologyFailure(byte[] content, int part, long offset, String reason)
            throws Exception {
        SourceException failure = failure(content, defaults(), bounds(content), FeatureQuery.all());
        assertDiagnostic(
                failure.terminal(),
                "SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS",
                offset,
                OptionalInt.of(part),
                Map.of("reason", reason));
    }

    private void assertFailure(
            byte[] content,
            ShapefileLimits limits,
            String code,
            long offset,
            OptionalInt part,
            Map<String, String> context)
            throws Exception {
        SourceException failure = failure(content, limits, bounds(content), FeatureQuery.all());
        assertDiagnostic(failure.terminal(), code, offset, part, context);
    }

    private SourceException failure(
            byte[] content, ShapefileLimits limits, Envelope fileBounds, FeatureQuery query)
            throws Exception {
        Path path =
                dataset(
                        "failure-" + failureFixture++,
                        fileBounds.minX(),
                        fileBounds.minY(),
                        fileBounds.maxX(),
                        fileBounds.maxY(),
                        content);
        try (FeatureSource source = open(path, limits);
                FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            return assertThrows(SourceException.class, cursor::advance);
        }
    }

    private static void assertDiagnostic(
            SourceDiagnostic diagnostic,
            String code,
            long offset,
            OptionalInt part,
            Map<String, String> expectedContext) {
        assertEquals(code, diagnostic.code());
        assertEquals(offset, diagnostic.location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(1, diagnostic.location().orElseThrow().recordNumber().orElseThrow());
        assertEquals(part, diagnostic.location().orElseThrow().partIndex());
        expectedContext.forEach(
                (key, value) -> assertEquals(value, diagnostic.context().get(key), key));
    }

    private io.github.mundanej.map.api.Geometry firstGeometry(Path path) throws Exception {
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            return cursor.current().geometry();
        }
    }

    private List<String> readSummaries(Path path) throws Exception {
        List<String> summaries = new ArrayList<>();
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                summaries.add(cursor.current().id() + ':' + cursor.current().geometry());
            }
        }
        return summaries;
    }

    private SourceException firstFailure(Path path) throws Exception {
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            return assertThrows(SourceException.class, cursor::advance);
        }
    }

    private Path dataset(
            String stem, double minX, double minY, double maxX, double maxY, byte[]... contents)
            throws Exception {
        Path path = directory.resolve(stem + ".shp");
        Files.write(path, ShpFixtures.file(5, minX, minY, maxX, maxY, contents));
        return path;
    }

    private FeatureSource open(Path path) {
        return open(path, defaults());
    }

    private FeatureSource open(Path path, ShapefileLimits limits) {
        return Shapefiles.open(
                IDENTITY, path, ShapefileOpenOptions.defaults().withShapefileLimits(limits));
    }

    private static ShapefileLimits defaults() {
        return ShapefileLimits.defaults();
    }

    private static FeatureQuery disjointQuery() {
        return new FeatureQuery(
                Optional.of(new Envelope(100, 100, 101, 101)),
                AttributeSelection.NONE,
                Optional.empty());
    }

    private static byte[] polygon(double[]... rings) {
        int[] starts = new int[rings.length];
        int pointCount = 0;
        for (int index = 0; index < rings.length; index++) {
            starts[index] = pointCount;
            pointCount += rings[index].length / 2;
        }
        return ShpFixtures.polygon(starts, join(rings));
    }

    private static double[] shell(double minX, double minY, double maxX, double maxY) {
        return new double[] {minX, minY, minX, maxY, maxX, maxY, maxX, minY, minX, minY};
    }

    private static double[] hole(double minX, double minY, double maxX, double maxY) {
        return new double[] {minX, minY, maxX, minY, maxX, maxY, minX, maxY, minX, minY};
    }

    private static double[] join(double[]... arrays) {
        int length = Arrays.stream(arrays).mapToInt(array -> array.length).sum();
        double[] joined = new double[length];
        int offset = 0;
        for (double[] array : arrays) {
            System.arraycopy(array, 0, joined, offset, array.length);
            offset += array.length;
        }
        return joined;
    }

    private static Envelope bounds(byte[] content) {
        ByteBuffer buffer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        return new Envelope(
                buffer.getDouble(4),
                buffer.getDouble(12),
                buffer.getDouble(20),
                buffer.getDouble(28));
    }

    private static byte[] contentWithCounts(int parts, int points) {
        ByteBuffer buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(5).putDouble(0).putDouble(0).putDouble(3).putDouble(3);
        buffer.putInt(parts).putInt(points);
        return buffer.array();
    }

    private static double[] points(int count) {
        double[] values = new double[count * 2];
        for (int index = 0; index < count; index++) {
            values[index * 2] = index;
            values[index * 2 + 1] = index;
        }
        return values;
    }

    private static List<Integer> boxed(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static void assertCoordinates(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(Shapefiles.canonical(expected[index]), actual[index], "ordinate " + index);
        }
    }
}
