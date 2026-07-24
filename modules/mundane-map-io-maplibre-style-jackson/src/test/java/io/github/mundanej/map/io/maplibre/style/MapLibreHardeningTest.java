package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapLibreHardeningTest {
    private static final byte[] VALID =
            """
            {"version":8,"sources":{"s":{"type":"geojson"}},"layers":[
              {"id":"p","type":"circle","source":"s",
               "paint":{"circle-radius":5,"circle-color":"#336699"}}
            ]}
            """
                    .getBytes(StandardCharsets.UTF_8);

    @Test
    void deterministicByteMutationNeverLeaksRawRuntimeFailures() {
        for (int index = 0; index < VALID.length; index += 3) {
            for (byte replacement : new byte[] {0, '"', '[', '9', (byte) 0xff}) {
                byte[] mutated = VALID.clone();
                mutated[index] = replacement;
                assertBoundedRead(mutated, "replacement at " + index);
            }
        }
        for (int length = 0; length < VALID.length; length += 5) {
            assertBoundedRead(java.util.Arrays.copyOf(VALID, length), "truncation at " + length);
        }
    }

    @Test
    void hostileDepthStringsNumbersAndDuplicateKeysFailStably() {
        assertCode(
                "MAPLIBRE_JSON_INVALID",
                "{\"version\":8,\"sources\":{\"s\":{\"type\":\"geojson\",\"type\":\"geojson\"}},"
                        + "\"layers\":[]}");
        assertCode(
                "MAPLIBRE_LIMIT_EXCEEDED",
                "{\"version\":8,\"sources\":{},\"metadata\":{\"x\":\""
                        + "a".repeat(70_000)
                        + "\"},\"layers\":[]}");
        assertCode(
                "MAPLIBRE_VALUE_INVALID",
                "{\"version\":8,\"sources\":{},\"zoom\":1e308,\"layers\":[]}");
        assertCode(
                "MAPLIBRE_LIMIT_EXCEEDED",
                "{\"version\":8,\"sources\":{},\"metadata\":"
                        + "[".repeat(300)
                        + "0"
                        + "]".repeat(300)
                        + ",\"layers\":[]}");
        assertCode(
                "MAPLIBRE_SOURCE_UNSUPPORTED",
                "{\"version\":8,\"sources\":{\"s\":{\"type\":\"raster\"}},\"layers\":[]}");
        assertCode(
                "MAPLIBRE_LAYER_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + "{\"id\":\"x\",\"type\":\"heatmap\",\"source\":\"s\"}]}");
        assertCode(
                "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + "{\"id\":\"x\",\"type\":\"circle\",\"source\":\"s\","
                        + "\"paint\":{\"circle-radius\":[\"+\",1,2]}}]}");
    }

    @Test
    void aggregateCancellationInterruptsParsingAtAStableBoundary() {
        AtomicInteger polls = new AtomicInteger();
        MapLibreReadException cancelled =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        manyLayers(300).getBytes(StandardCharsets.UTF_8),
                                        new MapLibreReadOptions(
                                                MapLibreReadLimits.defaults(),
                                                () -> polls.incrementAndGet() >= 4)));
        assertEquals("MAPLIBRE_CANCELLED", cancelled.problem().code());
        assertTrue(polls.get() >= 4);
    }

    @Test
    void failedBindingPublishesNothingAndLeavesBorrowedSourcesOpen() {
        MapLibreStyle style =
                MapLibreStyles.read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"first","type":"circle","source":"available"},
                          {"id":"second","type":"circle","source":"missing"}
                        ]}
                        """
                                .getBytes(StandardCharsets.UTF_8));
        InMemoryFeatureSource source = source();
        MapLibreBindException failure =
                assertThrows(
                        MapLibreBindException.class,
                        () ->
                                MapLibreStyleBinder.bind(
                                        style,
                                        MapLibreSourceRegistry.builder()
                                                .register("available", source)
                                                .build()));
        assertEquals("MAPLIBRE_SOURCE_UNRESOLVED", failure.problem().code());
        assertEquals("/layers/1/source", failure.problem().location());
        assertFalse(source.isClosed());
        source.close();
    }

    @Test
    void diagnosticPrecedenceFollowsDocumentOrder() {
        MapLibreReadException failure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        """
                                        {"version":8,"sprite":"forbidden","sources":{
                                          "bad":{"type":"raster"}},"layers":[
                                          {"id":"bad","type":"heatmap","source":"bad"}]}
                                        """
                                                .getBytes(StandardCharsets.UTF_8)));
        assertEquals("MAPLIBRE_ROOT_UNSUPPORTED", failure.problem().code());
        assertEquals("/sprite", failure.problem().location());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("Jackson"));
        assertTrue(
                failure.problem().context().values().stream()
                        .noneMatch(value -> value.contains("forbidden")));
    }

    private static void assertBoundedRead(byte[] bytes, String description) {
        try {
            MapLibreStyles.read(bytes);
        } catch (MapLibreReadException expected) {
            assertTrue(expected.problem().code().startsWith("MAPLIBRE_"), description);
            assertNull(expected.getCause(), description);
            assertFalse(expected.toString().contains("Jackson"), description);
        } catch (RuntimeException leaked) {
            throw new AssertionError(
                    description + " leaked " + leaked.getClass().getName(), leaked);
        }
    }

    private static void assertCode(String expected, String json) {
        MapLibreReadException failure =
                assertThrows(
                        MapLibreReadException.class,
                        () -> MapLibreStyles.read(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, failure.problem().code());
    }

    private static String manyLayers(int count) {
        StringBuilder json = new StringBuilder("{\"version\":8,\"sources\":{},\"layers\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"p")
                    .append(index)
                    .append("\",\"type\":\"circle\",\"source\":\"s\"}");
        }
        return json.append("]}").toString();
    }

    private static InMemoryFeatureSource source() {
        return InMemoryFeatureSource.open(
                new SourceIdentity("available", "Available"),
                List.of(
                        new FeatureRecord(
                                "p", "Point", new PointGeometry(new Coordinate(0, 0)), Map.of())),
                Optional.empty(),
                Optional.of(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty())),
                FeatureSourceLimits.LEVEL_1);
    }
}
