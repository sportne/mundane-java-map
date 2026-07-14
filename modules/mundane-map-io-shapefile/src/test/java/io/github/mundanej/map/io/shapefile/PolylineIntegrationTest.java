package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolylineIntegrationTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @TempDir Path directory;
    private int failureFixture;

    @Test
    void mapsNullSingleAndMultipartRecordsWithoutBridgingParts() throws Exception {
        byte[] single = ShpFixtures.polyline(new int[] {0}, -0.0, 1, 2, 3);
        byte[] multipart =
                ShpFixtures.polyline(new int[] {0, 3}, 0, 0, 1, 1, 2, 0, 8, 8, 9, 9, 10, 8);
        Path path = dataset("mapped", 0, 0, 10, 9, ShpFixtures.nullShape(), single, multipart);

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals("record:2", cursor.current().id());
            LineStringGeometry line =
                    assertInstanceOf(LineStringGeometry.class, cursor.current().geometry());
            assertEquals(2, line.coordinates().size());
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(line.coordinates().x(0)));

            assertTrue(cursor.advance());
            assertEquals("record:3", cursor.current().id());
            MultiLineStringGeometry lines =
                    assertInstanceOf(MultiLineStringGeometry.class, cursor.current().geometry());
            assertEquals(2, lines.partCount());
            assertEquals(List.of(0, 3, 6), boxed(lines.partOffsets()));
            assertEquals(2, lines.coordinates().x(2));
            assertEquals(8, lines.coordinates().x(3));
            assertFalse(cursor.advance());
        }
    }

    @Test
    void retainsConsecutiveDuplicatesAndStructurallyValidSelfCrossingLines() throws Exception {
        byte[] content = ShpFixtures.polyline(new int[] {0}, 0, 0, 0, 0, 2, 2, 0, 2, 2, 0, 2, 0);
        Path path = dataset("duplicates", 0, 0, 2, 2, content);

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            LineStringGeometry line =
                    assertInstanceOf(LineStringGeometry.class, cursor.current().geometry());
            assertEquals(6, line.coordinates().size());
            assertEquals(0, line.coordinates().x(0));
            assertEquals(0, line.coordinates().x(1));
            assertEquals(2, line.coordinates().x(4));
            assertEquals(0, line.coordinates().y(4));
        }
    }

    @Test
    void acceptsAConservativeRecordBoxAndPublishesTheExactCoordinateEnvelope() throws Exception {
        byte[] content = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);
        little(content).putDouble(4, -1).putDouble(12, -2).putDouble(20, 3).putDouble(28, 4);
        Path path = dataset("conservative-box", -10, -10, 10, 10, content);

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals(new Envelope(0, 0, 2, 2), cursor.current().geometry().envelope());
        }
    }

    @Test
    void rejectsEachInvalidCountAndCapacityAtItsStableField() throws Exception {
        assertFailure(
                contentWithCounts(0, 2),
                defaults(),
                "SHAPEFILE_PART_TABLE_INVALID",
                144,
                Map.of("reason", "partCount"));
        assertFailure(
                contentWithCounts(1, 0),
                defaults(),
                "SHAPEFILE_PART_TABLE_INVALID",
                148,
                Map.of("reason", "pointCount"));

        ShapefileLimits highCounts =
                defaults().withMaximumParts(Integer.MAX_VALUE).withMaximumPoints(Integer.MAX_VALUE);
        assertFailure(
                contentWithCounts(Integer.MAX_VALUE, Integer.MAX_VALUE),
                highCounts,
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                144,
                Map.of("reason", "arrayCapacity"));
        assertFailure(
                contentWithCounts(1, Integer.MAX_VALUE),
                highCounts,
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                148,
                Map.of("reason", "arrayCapacity"));
        assertFailure(
                contentWithCounts(2, 3),
                highCounts,
                "SHAPEFILE_PART_TABLE_INVALID",
                148,
                Map.of("reason", "insufficientPoints"));
    }

    @Test
    void enforcesPartsPointsAndDerivedSizeBeforeReadingVariablePayload() throws Exception {
        byte[] valid = ShpFixtures.polyline(new int[] {0, 2}, 0, 0, 1, 1, 2, 2, 3, 3);
        Path path = dataset("limit-equality", 0, 0, 3, 3, valid);
        try (FeatureSource source =
                        open(path, defaults().withMaximumParts(2).withMaximumPoints(4));
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
        }

        assertFailure(
                valid,
                defaults().withMaximumParts(1),
                "SOURCE_LIMIT_EXCEEDED",
                144,
                Map.of("limit", "parts", "maximum", "1", "requested", "2"));
        assertFailure(
                valid,
                defaults().withMaximumPoints(3),
                "SOURCE_LIMIT_EXCEEDED",
                148,
                Map.of("limit", "points", "maximum", "3", "requested", "4"));

        byte[] unexpected = contentWithCounts(1, 2);
        assertFailure(
                unexpected,
                defaults(),
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                104,
                Map.of("reason", "unexpectedSize", "actualBytes", "44", "expectedBytes", "80"));
        assertFailure(
                ShpFixtures.typed(3, 42),
                defaults(),
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                104,
                Map.of("reason", "truncatedPrefix", "actualBytes", "42", "expectedBytes", "44"));
    }

    @Test
    void rejectsEveryInvalidPartTableReasonAtTheResponsibleEntry() throws Exception {
        assertTableFailure(new int[] {1}, points(2), 0, 152, "firstNotZero");
        assertTableFailure(new int[] {0, 0}, points(4), 1, 156, "notIncreasing");
        assertTableFailure(new int[] {0, -1}, points(4), 1, 156, "notIncreasing");
        assertTableFailure(new int[] {0, 4}, points(4), 1, 156, "outOfRange");
        assertTableFailure(new int[] {0, 1}, points(4), 0, 152, "tooShort");
        assertTableFailure(new int[] {0, 3}, points(4), 1, 156, "tooShort");
    }

    @Test
    void validatesRecordAndCoordinateBoundsInDeterministicOrder() throws Exception {
        byte[] nonFiniteRecord = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);
        little(nonFiniteRecord).putDouble(4, Double.NaN);
        assertFailure(
                nonFiniteRecord,
                defaults(),
                "SHAPEFILE_COORDINATE_NON_FINITE",
                112,
                Map.of("axis", "x"));

        byte[] unorderedRecord = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);
        little(unorderedRecord).putDouble(4, 3);
        assertFailure(
                unorderedRecord,
                defaults(),
                "SHAPEFILE_BOUNDS_MISMATCH",
                112,
                Map.of("bounds", "record"));

        byte[] nonFiniteCoordinate = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);
        little(nonFiniteCoordinate).putDouble(48, Double.NaN);
        assertFailure(
                nonFiniteCoordinate,
                defaults(),
                "SHAPEFILE_COORDINATE_NON_FINITE",
                156,
                Map.of("axis", "x"));

        byte[] outsideRecord = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);
        little(outsideRecord).putDouble(20, 1);
        assertFailure(
                outsideRecord,
                defaults(),
                "SHAPEFILE_BOUNDS_MISMATCH",
                172,
                Map.of("bounds", "record"));

        byte[] outsideFile = ShpFixtures.polyline(new int[] {0}, 0, 0, 2, 2);
        assertFailure(
                outsideFile,
                defaults(),
                new Envelope(0, 0, 1, 2),
                "SHAPEFILE_BOUNDS_MISMATCH",
                172,
                Map.of("bounds", "file"));
    }

    @Test
    void rejectsWhollyDegeneratePartAfterRetainingItsCompletePayload() throws Exception {
        byte[] content = ShpFixtures.polyline(new int[] {0}, -0.0, 1, 0.0, 1, -0.0, 1);
        SourceException failure =
                failure(content, defaults(), new Envelope(0, 1, 0, 1), FeatureQuery.all());

        assertDiagnostic(
                failure.terminal(),
                "SHAPEFILE_PART_TABLE_INVALID",
                152,
                Map.of("reason", "degenerate"));
        assertEquals(0, failure.terminal().location().orElseThrow().partIndex().orElseThrow());
    }

    @Test
    void fullyValidatesMalformedRecordsBeforeApplyingAQueryFilterAndLeavesSourceReusable()
            throws Exception {
        byte[] malformed = ShpFixtures.polyline(new int[] {0}, 0, 0, 0, 0);
        Path path = dataset("filtered-malformed", 0, 0, 0, 0, malformed);
        FeatureQuery disjoint =
                new FeatureQuery(
                        Optional.of(new Envelope(100, 100, 101, 101)),
                        AttributeSelection.NONE,
                        Optional.empty());

        try (FeatureSource source = open(path)) {
            try (FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("degenerate", failure.terminal().context().get("reason"));
            }
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                SourceException repeated =
                        assertThrows(SourceException.class, replacement::advance);
                assertEquals("degenerate", repeated.terminal().context().get("reason"));
            }
        }
    }

    @Test
    void prospectivelyChargesAllPackedCopiesBeforeVariableAllocation() throws Exception {
        byte[] content = ShpFixtures.polyline(new int[] {0}, 0, 0, 1, 1);
        FeatureQuery disjoint =
                new FeatureQuery(
                        Optional.of(new Envelope(10, 10, 11, 11)),
                        AttributeSelection.NONE,
                        Optional.empty());
        Path accepted = dataset("allocation-equal", 0, 0, 1, 1, content);
        try (FeatureSource source =
                        open(accepted, defaults().withMaximumParserAllocationBytes(160));
                FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
            assertFalse(cursor.advance());
        }

        SourceException failure =
                failure(
                        content,
                        defaults().withMaximumParserAllocationBytes(159),
                        new Envelope(0, 0, 1, 1),
                        disjoint);
        assertDiagnostic(
                failure.terminal(),
                "SOURCE_LIMIT_EXCEEDED",
                144,
                Map.of("limit", "parserAllocationBytes", "maximum", "159", "requested", "160"));

        byte[] multipart = ShpFixtures.polyline(new int[] {0, 2}, 0, 0, 1, 1, 2, 2, 3, 3);
        Path multipartAccepted = dataset("multipart-allocation-equal", 0, 0, 3, 3, multipart);
        try (FeatureSource source =
                        open(multipartAccepted, defaults().withMaximumParserAllocationBytes(240));
                FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
            assertFalse(cursor.advance());
        }
        SourceException multipartFailure =
                failure(
                        multipart,
                        defaults().withMaximumParserAllocationBytes(239),
                        new Envelope(0, 0, 3, 3),
                        disjoint);
        assertDiagnostic(
                multipartFailure.terminal(),
                "SOURCE_LIMIT_EXCEEDED",
                144,
                Map.of("limit", "parserAllocationBytes", "maximum", "239", "requested", "240"));
    }

    @Test
    void parserAllocationAccountingIsCumulativeAcrossFilteredRecords() throws Exception {
        byte[] content = ShpFixtures.polyline(new int[] {0}, 0, 0, 1, 1);
        FeatureQuery disjoint =
                new FeatureQuery(
                        Optional.of(new Envelope(10, 10, 11, 11)),
                        AttributeSelection.NONE,
                        Optional.empty());
        Path path = dataset("cumulative-allocation", 0, 0, 1, 1, content, content);

        try (FeatureSource source = open(path, defaults().withMaximumParserAllocationBytes(232));
                FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
            assertFalse(cursor.advance());
        }

        try (FeatureSource source = open(path, defaults().withMaximumParserAllocationBytes(231));
                FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("parserAllocationBytes", failure.terminal().context().get("limit"));
            assertEquals("231", failure.terminal().context().get("maximum"));
            assertEquals("232", failure.terminal().context().get("requested"));
            assertEquals(
                    232, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
            assertEquals(
                    2, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
        }
    }

    @Test
    void indexedAndSequentialPathsReturnEquivalentPolylineValuesAndDiagnostics() throws Exception {
        byte[][] contents = {
            ShpFixtures.nullShape(),
            ShpFixtures.polyline(new int[] {0}, 0, 0, 1, 1),
            ShpFixtures.polyline(new int[] {0, 2}, 2, 2, 3, 3, 8, 8, 9, 9)
        };
        Path sequential = dataset("sequential", 0, 0, 9, 9, contents);
        Path indexed = dataset("indexed", 0, 0, 9, 9, contents);
        Files.write(directory.resolve("indexed.shx"), ShxFixtures.file(3, 0, 0, 9, 9, contents));

        assertEquals(readSummaries(sequential), readSummaries(indexed));

        byte[] malformed = ShpFixtures.polyline(new int[] {0, 0}, points(4));
        Path badSequential = dataset("bad-sequential", 0, 0, 3, 3, malformed);
        Path badIndexed = dataset("bad-indexed", 0, 0, 3, 3, malformed);
        Files.write(
                directory.resolve("bad-indexed.shx"), ShxFixtures.file(3, 0, 0, 3, 3, malformed));
        assertEquals(firstFailure(badSequential).terminal(), firstFailure(badIndexed).terminal());
    }

    @Test
    void polygonHeaderIsCurrentAndPolygonRecordInPolylineFileRemainsAMismatch() throws Exception {
        Path polygon = dataset("polygon", 0, 0, 1, 1);
        byte[] bytes = Files.readAllBytes(polygon);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(32, 5);
        Files.write(polygon, bytes);
        try (FeatureSource source = open(polygon);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertFalse(cursor.advance());
        }

        Path mismatch = dataset("polygon-record", 0, 0, 1, 1, ShpFixtures.typed(5, 44));
        assertEquals("SHAPEFILE_RECORD_TYPE_MISMATCH", firstFailure(mismatch).terminal().code());
    }

    private void assertTableFailure(
            int[] starts, double[] coordinates, int part, long offset, String reason)
            throws Exception {
        SourceException failure =
                failure(
                        ShpFixtures.polyline(starts, coordinates),
                        defaults(),
                        new Envelope(0, 0, 3, 3),
                        FeatureQuery.all());
        assertDiagnostic(
                failure.terminal(),
                "SHAPEFILE_PART_TABLE_INVALID",
                offset,
                Map.of("reason", reason));
        assertEquals(part, failure.terminal().location().orElseThrow().partIndex().orElseThrow());
    }

    private void assertFailure(
            byte[] content,
            ShapefileLimits limits,
            String code,
            long offset,
            Map<String, String> context)
            throws Exception {
        assertFailure(content, limits, new Envelope(0, 0, 3, 3), code, offset, context);
    }

    private void assertFailure(
            byte[] content,
            ShapefileLimits limits,
            Envelope fileBounds,
            String code,
            long offset,
            Map<String, String> context)
            throws Exception {
        SourceException failure = failure(content, limits, fileBounds, FeatureQuery.all());
        assertDiagnostic(failure.terminal(), code, offset, context);
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
            Map<String, String> expectedContext) {
        assertEquals(code, diagnostic.code());
        assertEquals(offset, diagnostic.location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(1, diagnostic.location().orElseThrow().recordNumber().orElseThrow());
        expectedContext.forEach(
                (key, value) -> assertEquals(value, diagnostic.context().get(key), key));
    }

    private List<String> readSummaries(Path path) throws Exception {
        List<String> values = new ArrayList<>();
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                FeatureRecord record = cursor.current();
                if (record.geometry() instanceof LineStringGeometry line) {
                    values.add(record.id() + ":line:" + line.coordinates());
                } else {
                    MultiLineStringGeometry lines = (MultiLineStringGeometry) record.geometry();
                    values.add(
                            record.id()
                                    + ":multi:"
                                    + lines.coordinates()
                                    + ':'
                                    + boxed(lines.partOffsets()));
                }
            }
        }
        return values;
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
        Files.write(path, ShpFixtures.file(3, minX, minY, maxX, maxY, contents));
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

    private static byte[] contentWithCounts(int parts, int points) {
        ByteBuffer buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(3).putDouble(0).putDouble(0).putDouble(3).putDouble(3);
        buffer.putInt(parts).putInt(points);
        return buffer.array();
    }

    private static ByteBuffer little(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
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
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
