package io.github.mundanej.map.io.geojson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoJsonHardeningTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("hostile", "Hostile");
    private static final String VALID =
            String.join(
                    "",
                    "{\"type\":\"Feature\",\"id\":\"safe\",",
                    "\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]},",
                    "\"properties\":{\"label\":\"SECRET_CANARY\"}}");

    @TempDir Path temporaryDirectory;

    @Test
    void exposesTheClosedDiagnosticMatrixWithoutRawData() {
        List<Case> cases =
                List.of(
                        new Case(
                                new byte[] {(byte) 0xff, (byte) 0xfe, '{', 0},
                                "GEOJSON_ENCODING_INVALID",
                                Map.of("reason", "unsupportedBom")),
                        new Case(
                                new byte[] {'{', '"', (byte) 0xc3, '"', ':', '1', '}'},
                                "GEOJSON_ENCODING_INVALID",
                                Map.of("reason", "malformedUtf8")),
                        text(
                                "{\"type\":\"Point\",\"type\":\"Point\",\"coordinates\":[0,0]}",
                                "GEOJSON_JSON_INVALID",
                                Map.of("reason", "duplicateMember")),
                        text(
                                "{\"type\":\"Point\",\"coordinates\":[0,0]}{}",
                                "GEOJSON_JSON_INVALID",
                                Map.of("reason", "trailingContent")),
                        text(
                                "{\"type\":\"Unknown\",\"SECRET_CANARY\":true}",
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "root")),
                        text(
                                String.join(
                                        "",
                                        "{\"type\":\"Feature\",\"geometry\":",
                                        "{\"type\":\"FeatureCollection\",\"features\":[]},",
                                        "\"properties\":null}"),
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "nestedCollection")),
                        text(
                                "{\"type\":\"GeometryCollection\",\"geometries\":[]}",
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "geometryCollection")),
                        text(
                                "{\"type\":\"Point\",\"coordinates\":[]}",
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "emptyGeometry")),
                        text(
                                "{\"type\":\"Point\",\"coordinates\":[0,0,1]}",
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "positionArity")),
                        text(
                                String.join(
                                        "",
                                        "{\"type\":\"Feature\",\"geometry\":",
                                        "{\"type\":\"Point\",\"coordinates\":[0,0]},",
                                        "\"properties\":{\"SECRET_CANARY\":[]}}"),
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "nestedProperty")),
                        text(
                                "{\"type\":\"Point\",\"coordinates\":[0,0],\"crs\":{}}",
                                "GEOJSON_PROFILE_UNSUPPORTED",
                                Map.of("construct", "legacyCrs")),
                        text(
                                "{\"coordinates\":[0,0]}",
                                "GEOJSON_VALUE_INVALID",
                                ordered("field", "type", "reason", "missing")),
                        text(
                                "{\"type\":\"FeatureCollection\"}",
                                "GEOJSON_VALUE_INVALID",
                                ordered("field", "features", "reason", "missing")),
                        text(
                                "{\"type\":\"Point\",\"coordinates\":[181,0]}",
                                "GEOJSON_VALUE_INVALID",
                                ordered("field", "coordinates", "reason", "range")));

        for (Case testCase : cases) {
            SourceException failure =
                    assertThrows(SourceException.class, () -> open(testCase.document()));
            assertEquals(testCase.code(), failure.terminal().code());
            assertEquals(testCase.context(), failure.terminal().context());
            assertEquals(
                    new ArrayList<>(testCase.context().keySet()),
                    new ArrayList<>(failure.terminal().context().keySet()));
            String stable =
                    failure.terminal().code()
                            + failure.terminal().message()
                            + failure.terminal().context();
            assertFalse(stable.contains("SECRET_CANARY"));
            assertEquals("hostile", failure.terminal().sourceId());
            assertEquals(
                    "geojson",
                    failure.terminal().location().orElseThrow().component().orElseThrow());
        }
    }

    @Test
    void retainsOnlyTheConfiguredWarningPrefixAndCountsOmissions() {
        byte[] document =
                utf8(
                        "{\"type\":\"FeatureCollection\",\"features\":["
                                + nullFeature()
                                + ","
                                + nullFeature()
                                + "]}");
        byte[] bom = new byte[document.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(document, 0, bom, 3, document.length);
        GeoJsonLimits limits =
                new GeoJsonLimits(
                        2_000, 16, 500, 30, 3, 10, 10, 10, 1, 3, 30, 100, 500, 30, 5_000, 1);

        FeatureSource source =
                GeoJsonFiles.open(
                        bom,
                        IDENTITY,
                        new GeoJsonOpenOptions(limits, FeatureSourceLimits.LEVEL_1),
                        CancellationToken.none());

        assertEquals(1, source.openingDiagnostics().entries().size());
        assertEquals(
                "GEOJSON_UTF8_BOM_IGNORED",
                source.openingDiagnostics().entries().getFirst().code());
        assertEquals(2, source.openingDiagnostics().omittedWarningCount());
        source.close();
    }

    @Test
    void snapshotsCallerBytesAndFilesBeforePublication() throws Exception {
        byte[] caller = utf8(VALID);
        FeatureSource bytesSource = open(caller);
        Arrays.fill(caller, (byte) 'x');
        assertEquals("string:safe", onlyId(bytesSource));
        bytesSource.close();

        Path path = temporaryDirectory.resolve("snapshot.geojson");
        Files.write(path, utf8(VALID));
        FeatureSource fileSource =
                GeoJsonFiles.open(
                        path, IDENTITY, GeoJsonOpenOptions.defaults(), CancellationToken.none());
        Files.writeString(path, "changed", StandardCharsets.UTF_8);
        assertEquals("string:safe", onlyId(fileSource));
        fileSource.close();
    }

    @Test
    void deterministicBoundedMutationsHaveOnlyStableOutcomes() {
        List<String> first = mutationOutcomes(0x4d554e44414e454aL);
        List<String> second = mutationOutcomes(0x4d554e44414e454aL);
        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(value -> !value.equals("success")));
    }

    @Test
    void validatesReachabilityAndAcceptsEveryCrossFieldEquality() {
        GeoJsonLimits equality =
                new GeoJsonLimits(100, 8, 44, 4, 1, 1, 1, 1, 1, 1, 10, 10, 10, 10, 136, 1);
        assertEquals(44, equality.maximumTokens());
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 43, 4, 1, 1, 1, 1, 1, 1, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 44, 3, 1, 1, 1, 1, 1, 1, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 80, 6, 1, 1, 1, 1, 1, 2, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 44, 4, 1, 1, 2, 1, 1, 1, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 44, 4, 1, 1, 1, 1, 2, 1, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 44, 4, 1, 1, 1, 1, 1, 1, 10, 11, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 44, 4, 1, 1, 1, 1, 1, 2, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 50, 7, 2, 1, 1, 1, 1, 1, 10, 10, 10, 10, 136, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeoJsonLimits(100, 8, 44, 4, 1, 1, 1, 1, 1, 1, 10, 10, 10, 10, 135, 1));
    }

    @Test
    void rejectsEveryCeilingAboveItsSupportedHardMaximum() {
        GeoJsonLimits exactHardMaxima =
                new GeoJsonLimits(
                        268_435_456,
                        128,
                        134_217_728L,
                        16_000_000,
                        1_000_000,
                        16_000_000,
                        16_000_000,
                        2_000_000,
                        4_096,
                        8_000_000,
                        256,
                        1_048_576,
                        134_217_728,
                        256,
                        1_073_741_824L,
                        4_096);
        assertEquals(268_435_456, exactHardMaxima.maximumInputBytes());
        assertEquals(4_096, exactHardMaxima.retainedWarnings());

        assertInvalidLimits(
                268_435_457,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                129,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                134_217_729L,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                16_000_001,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                1_000_001,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                16_000_001,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                16_000_001,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                2_000_001,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                4_097,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                8_000_001,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                257,
                65_536,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                1_048_577,
                16_777_216,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                134_217_729,
                128,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                257,
                268_435_456,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                1_073_741_825L,
                256);
        assertInvalidLimits(
                16_777_216,
                64,
                16_000_000,
                2_000_000,
                100_000,
                2_000_000,
                1_000_000,
                250_000,
                256,
                1_000_000,
                256,
                65_536,
                16_777_216,
                128,
                268_435_456,
                4_097);
    }

    @Test
    void acceptsEveryLexicalAndInputEqualityAndRejectsOneUnder() {
        String point = "{\"type\":\"Point\",\"coordinates\":[100,0]}";
        byte[] encoded = utf8(point);
        assertOpens(point, lexicalLimits(encoded.length, 2, 93, 11, 5, 20, 3));
        assertReadLimit(
                point, lexicalLimits(encoded.length - 1, 2, 93, 11, 5, 20, 3), "inputBytes");
        assertReadLimit(point, lexicalLimits(encoded.length, 1, 93, 11, 5, 20, 3), "nestingDepth");
        assertReadLimit(
                point, lexicalLimits(encoded.length, 2, 93, 10, 5, 20, 3), "memberNameCharacters");
        assertReadLimit(
                point, lexicalLimits(encoded.length, 2, 93, 11, 4, 20, 3), "scalarCharacters");
        assertReadLimit(
                point, lexicalLimits(encoded.length, 2, 93, 11, 5, 19, 3), "aggregateCharacters");
        assertReadLimit(
                point, lexicalLimits(encoded.length, 2, 93, 11, 5, 20, 2), "numberCharacters");

        String exactTokens = tokenDocument(81);
        assertOpens(exactTokens, lexicalLimits(2_000, 3, 93, 11, 5, 27, 3));
        assertReadLimit(exactTokens, lexicalLimits(2_000, 3, 92, 11, 5, 27, 3), "tokens");
    }

    @Test
    void observesCancellationAfterParsingHasStarted() {
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() >= 3;
        String document = tokenDocument(5_000);
        GeoJsonLimits limits =
                new GeoJsonLimits(
                        100_000, 8, 10_000, 10, 1, 1, 1, 1, 1, 1, 20, 20, 100, 10, 200_000, 1);

        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        utf8(document),
                                        IDENTITY,
                                        new GeoJsonOpenOptions(limits, FeatureSourceLimits.LEVEL_1),
                                        cancellation));

        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals(Map.of("operation", "geojson-open"), failure.terminal().context());
        assertEquals(3, checks.get());
    }

    @Test
    void isolatesStructuralReaderCountersAtEqualityAndOneOver() {
        String feature = feature("[0,0]", "null");
        assertOpens(feature, readLimits(5, 1, 1, 1, 1, 1, 1));
        assertReadLimit(
                feature.replace("\"properties\":null", "\"properties\":null,\"bbox\":[0,0,1,1]"),
                readLimits(5, 1, 1, 1, 1, 1, 1),
                "objectMembers");

        String twoFeatures =
                "{\"type\":\"FeatureCollection\",\"features\":["
                        + feature
                        + ","
                        + feature.replace("[0,0]", "[1,1]")
                        + "]}";
        assertOpens(feature, readLimits(20, 1, 2, 2, 1, 1, 1));
        assertReadLimit(twoFeatures, readLimits(20, 1, 2, 2, 1, 1, 1), "physicalFeatures");

        assertOpens(
                "{\"type\":\"MultiPoint\",\"coordinates\":[[0,0]]}",
                readLimits(5, 1, 1, 1, 1, 1, 1));
        assertReadLimit(
                "{\"type\":\"MultiPoint\",\"coordinates\":[[0,0],[1,1]]}",
                readLimits(5, 1, 1, 1, 1, 1, 1),
                "totalPositions");
        assertReadLimit(
                "{\"type\":\"MultiPoint\",\"coordinates\":[[0,0],[1,1]]}",
                readLimits(5, 1, 2, 1, 1, 1, 1),
                "positionsPerGeometry");

        assertReadLimit(
                feature("[0,0]", "{\"a\":1,\"b\":2}"),
                readLimits(20, 2, 1, 1, 1, 1, 2),
                "propertiesPerFeature");
        assertOpens(feature("[0,0]", "{\"a\":1,\"b\":2}"), readLimits(20, 1, 1, 1, 1, 2, 2));
        String twoProperties =
                "{\"type\":\"FeatureCollection\",\"features\":["
                        + feature("[0,0]", "{\"a\":1}")
                        + ","
                        + feature("[1,1]", "{\"b\":2}")
                        + "]}";
        assertReadLimit(twoProperties, readLimits(30, 2, 2, 2, 1, 1, 1), "totalProperties");
        assertOpens(twoProperties, readLimits(30, 2, 2, 2, 1, 1, 2));
    }

    private static List<String> mutationOutcomes(long seed) {
        byte[] valid = utf8(VALID);
        Random random = new Random(seed);
        List<String> outcomes = new ArrayList<>();
        for (int caseIndex = 0; caseIndex < 256; caseIndex++) {
            byte[] mutated = valid.clone();
            int changes = 1 + random.nextInt(4);
            for (int change = 0; change < changes; change++) {
                mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
            }
            try {
                FeatureSource source = open(mutated);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none());
                while (cursor.advance()) {
                    cursor.current();
                }
                cursor.close();
                source.close();
                outcomes.add("success");
            } catch (SourceException failure) {
                assertFalse(failure.terminal().message().contains("SECRET_CANARY"));
                assertFalse(failure.terminal().context().toString().contains("SECRET_CANARY"));
                outcomes.add(failure.terminal().code() + failure.terminal().context());
            }
        }
        return outcomes;
    }

    private static void assertOpens(String document, GeoJsonLimits limits) {
        FeatureSource source =
                GeoJsonFiles.open(
                        utf8(document),
                        IDENTITY,
                        new GeoJsonOpenOptions(limits, FeatureSourceLimits.LEVEL_1),
                        CancellationToken.none());
        source.close();
    }

    private static void assertReadLimit(
            String document, GeoJsonLimits limits, String expectedLimit) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonFiles.open(
                                        utf8(document),
                                        IDENTITY,
                                        new GeoJsonOpenOptions(limits, FeatureSourceLimits.LEVEL_1),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(expectedLimit, failure.terminal().context().get("limit"));
        assertEquals(
                List.of("limit", "maximum", "requested", "scope"),
                List.copyOf(failure.terminal().context().keySet()));
    }

    private static GeoJsonLimits readLimits(
            int members,
            int physicalFeatures,
            int positions,
            int positionsPerGeometry,
            int parts,
            int propertiesPerFeature,
            int properties) {
        long tokens = 4L * positions + 2L * members + 64L;
        return new GeoJsonLimits(
                2_000,
                16,
                tokens,
                members,
                physicalFeatures,
                positions,
                positionsPerGeometry,
                parts,
                propertiesPerFeature,
                properties,
                30,
                100,
                500,
                30,
                5_000,
                10);
    }

    private static GeoJsonLimits lexicalLimits(
            int input, int depth, long tokens, int names, int scalar, int aggregate, int number) {
        return new GeoJsonLimits(
                input,
                depth,
                tokens,
                10,
                1,
                10,
                10,
                10,
                1,
                1,
                names,
                scalar,
                aggregate,
                number,
                input + 16L * 10 + 2L * aggregate,
                1);
    }

    private static void assertInvalidLimits(
            int input,
            int depth,
            long tokens,
            int members,
            int features,
            int positions,
            int geometryPositions,
            int parts,
            int featureProperties,
            int properties,
            int names,
            int scalar,
            int aggregate,
            int number,
            long owned,
            int warnings) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GeoJsonLimits(
                                input,
                                depth,
                                tokens,
                                members,
                                features,
                                positions,
                                geometryPositions,
                                parts,
                                featureProperties,
                                properties,
                                names,
                                scalar,
                                aggregate,
                                number,
                                owned,
                                warnings));
    }

    private static String tokenDocument(int values) {
        StringBuilder document =
                new StringBuilder("{\"type\":\"Point\",\"coordinates\":[0,0],\"foreign\":[");
        for (int index = 0; index < values; index++) {
            if (index > 0) {
                document.append(',');
            }
            document.append('0');
        }
        return document.append("]}").toString();
    }

    private static String feature(String coordinates, String properties) {
        return String.join(
                "",
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":",
                coordinates,
                "},\"properties\":",
                properties,
                "}");
    }

    private static String onlyId(FeatureSource source) {
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(cursor.advance());
        String id = cursor.current().id();
        assertFalse(cursor.advance());
        cursor.close();
        return id;
    }

    private static FeatureSource open(byte[] document) {
        return GeoJsonFiles.open(
                document, IDENTITY, GeoJsonOpenOptions.defaults(), CancellationToken.none());
    }

    private static Case text(String document, String code, Map<String, String> context) {
        return new Case(utf8(document), code, context);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String nullFeature() {
        return "{\"type\":\"Feature\",\"geometry\":null,\"properties\":null}";
    }

    private static Map<String, String> ordered(String... values) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }

    private static final class Case {
        private final byte[] document;
        private final String code;
        private final Map<String, String> context;

        private Case(byte[] document, String code, Map<String, String> context) {
            this.document = document.clone();
            this.code = code;
            this.context =
                    java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(context));
        }

        private String code() {
            return code;
        }

        private Map<String, String> context() {
            return context;
        }

        private byte[] document() {
            return document.clone();
        }
    }
}
