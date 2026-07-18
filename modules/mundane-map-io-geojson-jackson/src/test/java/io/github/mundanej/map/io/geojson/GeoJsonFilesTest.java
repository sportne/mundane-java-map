package io.github.mundanej.map.io.geojson;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoJsonFilesTest {
    private static final SourceIdentity IDENTITY =
            new SourceIdentity("geojson-test", "GeoJSON test");

    @TempDir Path temporaryDirectory;

    @Test
    void opensBarePointFromDefensiveByteSnapshot() {
        byte[] input = json("{\"coordinates\":[-73.5,40.25],\"type\":\"Point\"}");
        FeatureSource source = open(input);
        input[0] = '[';

        assertEquals(OptionalLong.of(1), source.metadata().featureCount());
        assertEquals(
                Optional.of(new Envelope(-73.5, 40.25, -73.5, 40.25)), source.metadata().extent());
        assertEquals(
                "EPSG:4326",
                source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
        assertTrue(source.openingDiagnostics().entries().isEmpty());

        FeatureRecord record = only(source, FeatureQuery.all());
        assertEquals("geometry:0", record.id());
        assertEquals(
                new Coordinate(-73.5, 40.25), ((PointGeometry) record.geometry()).coordinate());
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertEquals(OptionalLong.of(1), source.metadata().featureCount());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
    }

    @Test
    void opensFeatureCollectionWithIdsPropertiesProjectionAndBounds() {
        FeatureSource source =
                open(
                        json(
                                """
                                {"type":"FeatureCollection","foreign":{"nested":[true,null]},"features":[
                                  {"type":"Feature","id":"alpha","geometry":{"type":"Point","coordinates":[1,2]},
                                   "properties":{"text":"value","flag":true,"whole":7,"decimal":1.25,"missing":null}},
                                  {"properties":null,"geometry":{"coordinates":[[8,9],[10,11]],"type":"MultiPoint"},
                                   "id":2.0,"type":"Feature"}
                                ]}
                                """));

        assertEquals(OptionalLong.of(2), source.metadata().featureCount());
        FeatureQuery firstOnly =
                new FeatureQuery(
                        Optional.of(new Envelope(0, 0, 3, 3)),
                        AttributeSelection.only(List.of("decimal", "text")),
                        Optional.empty());
        FeatureRecord first = only(source, firstOnly);
        assertEquals("string:alpha", first.id());
        assertEquals(
                Map.of("decimal", new BigDecimal("1.25"), "text", "value"), first.attributes());

        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(cursor.advance());
        FeatureRecord complete = cursor.current();
        assertEquals(true, complete.attributes().get("flag"));
        assertEquals(7L, complete.attributes().get("whole"));
        assertEquals(AttributeNull.INSTANCE, complete.attributes().get("missing"));
        assertTrue(cursor.advance());
        FeatureRecord second = cursor.current();
        assertEquals("number:2", second.id());
        MultiPointGeometry points = assertInstanceOf(MultiPointGeometry.class, second.geometry());
        assertArrayEquals(new double[] {8, 9, 10, 11}, points.coordinates().toArray());
        assertFalse(cursor.advance());
        assertThrows(IllegalStateException.class, cursor::current);
        cursor.close();
        source.close();
    }

    @Test
    void ignoresObjectSpecificNamesWhenTheyAreForeignAtTheRoot() {
        FeatureSource collection =
                open(
                        json(
                                """
                                {"geometry":{"not":"a feature member here"},
                                 "properties":["foreign"],"coordinates":"foreign",
                                 "type":"FeatureCollection","features":[
                                   {"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},
                                    "properties":null}]}
                                """));
        assertEquals(new Coordinate(1, 2), point(only(collection, FeatureQuery.all())));
        collection.close();

        FeatureSource feature =
                open(
                        json(
                                """
                                {"features":{"foreign":true},"coordinates":"foreign",
                                 "type":"Feature","geometry":{"type":"Point","coordinates":[3,4]},
                                 "properties":null}
                                """));
        assertEquals(new Coordinate(3, 4), point(only(feature, FeatureQuery.all())));
        feature.close();

        FeatureSource geometry =
                open(
                        json(
                                """
                                {"features":["foreign"],"properties":{"foreign":true},
                                 "type":"Point","coordinates":[5,6]}
                                """));
        assertEquals(new Coordinate(5, 6), point(only(geometry, FeatureQuery.all())));
        geometry.close();
    }

    @Test
    void duplicateIdsReportPhysicalIndexesWithoutRawValues() {
        String supplied =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":"secret","geometry":{"type":"Point","coordinates":[0,0]},"properties":null},
                  {"type":"Feature","id":"secret","geometry":{"type":"Point","coordinates":[1,1]},"properties":null}]}
                """;
        SourceException suppliedFailure =
                assertThrows(SourceException.class, () -> open(json(supplied)));
        assertEquals("SOURCE_DUPLICATE_FEATURE_ID", suppliedFailure.terminal().code());
        assertEquals(
                Map.of("firstIndex", "0", "duplicateIndex", "1"),
                suppliedFailure.terminal().context());
        assertFalse(suppliedFailure.terminal().toString().contains("secret"));

        String numeric = supplied.replaceFirst("\"secret\"", "1").replaceFirst("\"secret\"", "1.0");
        SourceException numericFailure =
                assertThrows(SourceException.class, () -> open(json(numeric)));
        assertEquals("SOURCE_DUPLICATE_FEATURE_ID", numericFailure.terminal().code());
        assertEquals(
                Map.of("firstIndex", "0", "duplicateIndex", "1"),
                numericFailure.terminal().context());
    }

    @Test
    void skipsNullGeometryAndRetainsBomWarnings() {
        byte[] document =
                json(
                        """
                        {"type":"FeatureCollection","features":[
                          {"type":"Feature","geometry":null,"properties":{"checked":{"nested":true}}},
                          {"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":null}
                        ]}
                        """);
        byte[] withBom = new byte[document.length + 3];
        withBom[0] = (byte) 0xef;
        withBom[1] = (byte) 0xbb;
        withBom[2] = (byte) 0xbf;
        System.arraycopy(document, 0, withBom, 3, document.length);
        SourceException nested = assertThrows(SourceException.class, () -> open(withBom));
        assertEquals("GEOJSON_PROFILE_UNSUPPORTED", nested.terminal().code());

        String valid =
                "{\"type\":\"FeatureCollection\",\"features\":["
                        + "{\"type\":\"Feature\",\"geometry\":null,\"properties\":null},"
                        + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\","
                        + "\"coordinates\":[0,0]},\"properties\":null}]}";
        byte[] validBytes = json(valid);
        byte[] validBom = new byte[validBytes.length + 3];
        validBom[0] = (byte) 0xef;
        validBom[1] = (byte) 0xbb;
        validBom[2] = (byte) 0xbf;
        System.arraycopy(validBytes, 0, validBom, 3, validBytes.length);
        FeatureSource source = open(validBom);
        assertEquals(OptionalLong.of(1), source.metadata().featureCount());
        assertEquals(
                List.of("GEOJSON_UTF8_BOM_IGNORED", "GEOJSON_NULL_GEOMETRY_SKIPPED"),
                source.openingDiagnostics().entries().stream().map(entry -> entry.code()).toList());
        assertEquals("record:1", only(source, FeatureQuery.all()).id());
        source.close();
    }

    @Test
    void opensRegularFileAndRejectsUnsafeOrChangedKinds() throws Exception {
        Path path = temporaryDirectory.resolve("points.geojson");
        Files.write(path, json("{\"type\":\"Point\",\"coordinates\":[3,4]}"));
        FeatureSource source =
                GeoJsonFiles.open(
                        path, IDENTITY, GeoJsonOpenOptions.defaults(), CancellationToken.none());
        assertEquals(
                new Coordinate(3, 4),
                ((PointGeometry) only(source, FeatureQuery.all()).geometry()).coordinate());
        source.close();

        SourceException missing =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        temporaryDirectory.resolve("missing.geojson"),
                                        IDENTITY,
                                        GeoJsonOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOJSON_IO_FAILED", missing.terminal().code());
        SourceException directory =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        temporaryDirectory,
                                        IDENTITY,
                                        GeoJsonOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOJSON_IO_FAILED", directory.terminal().code());
    }

    @Test
    void enforcesStrictSyntaxProfileAndStableDiagnostics() {
        assertFailure(
                "{\"type\":\"Point\",\"type\":\"Point\",\"coordinates\":[0,0]}",
                "GEOJSON_JSON_INVALID");
        assertFailure("{\"type\":\"Point\",\"coordinates\":[181,0]}", "GEOJSON_VALUE_INVALID");
        assertFailure("{\"type\":\"Point\",\"coordinates\":[]}", "GEOJSON_PROFILE_UNSUPPORTED");
        assertFailure("{\"type\":\"Feature\",\"geometry\":null}", "GEOJSON_VALUE_INVALID");
        assertFailure(
                "{\"type\":\"GeometryCollection\",\"geometries\":[]}",
                "GEOJSON_PROFILE_UNSUPPORTED");
        assertFailure(
                "{\"type\":\"FeatureCollection\",\"features\":[]} trailing",
                "GEOJSON_JSON_INVALID");
        assertFailure(
                "{\"type\":\"Point\",//comment\n\"coordinates\":[0,0]}", "GEOJSON_JSON_INVALID");
        SourceException utf8 =
                assertThrows(
                        SourceException.class,
                        () -> open(new byte[] {'{', '"', (byte) 0xc3, '"', ':', '1', '}'}));
        assertEquals("GEOJSON_ENCODING_INVALID", utf8.terminal().code());
        assertEquals("geojson", utf8.terminal().location().orElseThrow().component().orElseThrow());
        assertFalse(utf8.terminal().message().contains("c3"));
    }

    @Test
    void cancellationAndCursorOwnershipFollowSourceContracts() {
        CancellationSource cancelled = new CancellationSource();
        cancelled.cancel();
        SourceException opening =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        json("{\"type\":\"Point\",\"coordinates\":[0,0]}"),
                                        IDENTITY,
                                        GeoJsonOpenOptions.defaults(),
                                        cancelled.token()));
        assertEquals("SOURCE_CANCELLED", opening.terminal().code());

        FeatureSource source = open(json("{\"type\":\"Point\",\"coordinates\":[0,0]}"));
        FeatureCursor first = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        first.close();
        first.close();
        assertTrue(first.isClosed());
        FeatureCursor second = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        source.close();
        assertTrue(second.isClosed());
        assertThrows(IllegalStateException.class, second::advance);
    }

    @Test
    void validatesLimitsAndOpeningBoundaries() {
        GeoJsonLimits defaults = GeoJsonLimits.defaults();
        assertEquals(16_777_216, defaults.maximumInputBytes());
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(1, 1, 1, 1, 1, 2, 3, 1, 1, 1, 1, 1, 1, 1, 100, 1));
        assertThrows(
                NullPointerException.class,
                () -> new GeoJsonOpenOptions(null, FeatureSourceLimits.LEVEL_1));

        GeoJsonLimits tiny =
                new GeoJsonLimits(8, 8, 100, 100, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 200, 1);
        SourceException tooLarge =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        json("{\"type\":\"Point\",\"coordinates\":[0,0]}"),
                                        IDENTITY,
                                        new GeoJsonOpenOptions(tiny, FeatureSourceLimits.LEVEL_1),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", tooLarge.terminal().code());
    }

    @Test
    void mapsParserCeilingsToStableProjectLimits() {
        assertLimit(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}",
                limits(8, 5, 30, 30, 30, 10),
                "tokens");
        assertLimit(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}",
                limits(1, 100, 30, 30, 30, 10),
                "nestingDepth");
        assertLimit(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}",
                limits(8, 100, 3, 30, 30, 10),
                "memberNameCharacters");
        assertLimit(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}",
                limits(8, 100, 30, 4, 30, 10),
                "scalarCharacters");
        assertLimit(
                "{\"type\":\"Point\",\"coordinates\":[10,0]}",
                limits(8, 100, 30, 30, 30, 1),
                "numberCharacters");
        assertLimit(
                "{\"type\":\"Point\",\"coordinates\":[0,0]}",
                limits(8, 100, 30, 8, 8, 10),
                "aggregateCharacters");
    }

    @Test
    void enforcesConservativePrimitiveAccumulatorPeakAtExactBoundary() {
        String document = "{\"type\":\"MultiPoint\",\"coordinates\":[[0,0],[1,1]]}";
        assertLimit(document, multiPointLimits(290), "ownedBytes");

        FeatureSource exact =
                GeoJsonFiles.open(
                        json(document),
                        IDENTITY,
                        new GeoJsonOpenOptions(multiPointLimits(291), FeatureSourceLimits.LEVEL_1),
                        CancellationToken.none());
        assertEquals(
                2,
                ((MultiPointGeometry) only(exact, FeatureQuery.all()).geometry())
                        .coordinates()
                        .size());
        exact.close();
    }

    private static FeatureSource open(byte[] bytes) {
        return GeoJsonFiles.open(
                bytes, IDENTITY, GeoJsonOpenOptions.defaults(), CancellationToken.none());
    }

    private static FeatureRecord only(FeatureSource source, FeatureQuery query) {
        FeatureCursor cursor = source.openCursor(query, CancellationToken.none());
        try {
            assertTrue(cursor.advance());
            FeatureRecord record = cursor.current();
            assertFalse(cursor.advance());
            return record;
        } finally {
            cursor.close();
        }
    }

    private static Coordinate point(FeatureRecord record) {
        return ((PointGeometry) record.geometry()).coordinate();
    }

    private static void assertFailure(String document, String code) {
        SourceException failure = assertThrows(SourceException.class, () -> open(json(document)));
        assertEquals(code, failure.terminal().code());
        assertEquals(
                "geojson", failure.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals("geojson-test", failure.terminal().sourceId());
    }

    private static void assertLimit(String document, GeoJsonLimits limits, String expectedLimit) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        json(document),
                                        IDENTITY,
                                        new GeoJsonOpenOptions(limits, FeatureSourceLimits.LEVEL_1),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(expectedLimit, failure.terminal().context().get("limit"));
    }

    private static GeoJsonLimits limits(
            int depth, long tokens, int names, int scalar, int aggregate, int number) {
        return new GeoJsonLimits(
                1_000, depth, tokens, 100, 10, 10, 10, 10, 10, 10, names, scalar, aggregate, number,
                5_000, 10);
    }

    private static GeoJsonLimits multiPointLimits(long ownedBytes) {
        return new GeoJsonLimits(100, 8, 100, 10, 1, 2, 2, 1, 1, 1, 11, 10, 25, 10, ownedBytes, 1);
    }

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
