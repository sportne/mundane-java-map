package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalGeometryType;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapLibreBindingTest {
    @Test
    void filtersPreserveTypedMissingNullAndGeometrySemantics() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{"s":{"type":"geojson"}},"layers":[
                          {"id":"filtered","type":"circle","source":"s",
                           "filter":["all",
                             ["has","kind"],
                             ["==",["get","kind"],"road"],
                             ["==",["get","rank"],5],
                             ["==",["geometry-type"],"Point"]]}
                        ]}
                        """);
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        style.layers().getFirst().portrayal().orElseThrow());
        PortrayalEvaluationContext point =
                PortrayalEvaluationContext.UNSCALED.withGeometryType(PortrayalGeometryType.POINT);

        assertTrue(
                resolver.resolveAll(Map.of("kind", "road", "rank", 5L), point)
                        .marker()
                        .isPresent());
        assertTrue(
                resolver.resolveAll(Map.of("kind", "road", "rank", "5"), point).marker().isEmpty());
        assertTrue(
                resolver.resolveAll(
                                Map.of("kind", "road", "rank", 5L),
                                PortrayalEvaluationContext.UNSCALED.withGeometryType(
                                        PortrayalGeometryType.LINE_STRING))
                        .marker()
                        .isEmpty());

        FeaturePortrayalResolver nulls =
                FeaturePortrayalResolver.compile(
                        read("""
                                        {"version":8,"sources":{},"layers":[
                                          {"id":"null","type":"circle","source":"s",
                                           "filter":["==",["get","optional"],null]}
                                        ]}
                                        """)
                                .layers()
                                .getFirst()
                                .portrayal()
                                .orElseThrow());
        assertTrue(nulls.resolveAll(Map.of(), point).marker().isPresent());
        assertTrue(
                nulls.resolveAll(Map.of("optional", AttributeNull.INSTANCE), point)
                        .marker()
                        .isPresent());
        assertTrue(nulls.resolveAll(Map.of("optional", "value"), point).marker().isEmpty());

        FeaturePortrayalResolver notNumber =
                FeaturePortrayalResolver.compile(
                        read("""
                                        {"version":8,"sources":{},"layers":[
                                          {"id":"not-number","type":"circle","source":"s",
                                           "filter":["!=",["get","optional"],5]}
                                        ]}
                                        """)
                                .layers()
                                .getFirst()
                                .portrayal()
                                .orElseThrow());
        assertTrue(notNumber.resolveAll(Map.of(), point).marker().isEmpty());
        assertTrue(
                notNumber
                        .resolveAll(Map.of("optional", AttributeNull.INSTANCE), point)
                        .marker()
                        .isEmpty());
        assertTrue(notNumber.resolveAll(Map.of("optional", 5L), point).marker().isEmpty());
        assertTrue(notNumber.resolveAll(Map.of("optional", 6L), point).marker().isPresent());
        assertTrue(notNumber.resolveAll(Map.of("optional", "5"), point).marker().isPresent());
    }

    @Test
    void bindingPreflightsExactlyAndProjectsRequiredAttributes() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{"s":{"type":"geojson"}},"layers":[
                          {"id":"first","type":"circle","source":"s","minzoom":2,"maxzoom":4,
                           "filter":["==",["get","kind"],"road"]},
                          {"id":"second","type":"circle","source":"s",
                           "layout":{"visibility":"none"}}
                        ]}
                        """);
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("memory", "memory"),
                        List.of(
                                new FeatureRecord(
                                        "1",
                                        "",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of("kind", "road", "unused", 9L))));
        MapLibreSourceRegistry registry =
                MapLibreSourceRegistry.builder().register("s", source).build();
        MapLibreStyleBinding binding = MapLibreStyleBinder.bind(style, registry);

        assertEquals(
                List.of("first"), binding.layers().stream().map(MapLibreBoundLayer::id).toList());
        assertEquals(List.of("kind"), binding.layers().getFirst().queryAttributes().orderedNames());
        assertEquals(List.of(), binding.activeLayers(1.999));
        assertEquals(
                List.of("first"),
                binding.activeLayers(2).stream().map(MapLibreBoundLayer::id).toList());
        assertEquals(List.of(), binding.activeLayers(4));
        assertSame(source, binding.layers().getFirst().source());
        try (FeatureCursor cursor =
                source.openCursor(
                        new FeatureQuery(
                                Optional.empty(),
                                binding.layers().getFirst().queryAttributes(),
                                Optional.empty()),
                        CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals(Map.of("kind", "road"), cursor.current().attributes());
            assertFalse(cursor.advance());
        }
        binding.close();
        assertTrue(binding.isClosed());
        assertFalse(source.isClosed());
        assertThrows(IllegalStateException.class, binding::layers);
        source.close();
    }

    @Test
    void unresolvedAndDuplicateRegistrationsFailBeforePublication() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{"s":{"type":"geojson"}},"layers":[
                          {"id":"first","type":"circle","source":"s"}
                        ]}
                        """);
        MapLibreBindException missing =
                assertThrows(
                        MapLibreBindException.class,
                        () ->
                                MapLibreStyleBinder.bind(
                                        style, MapLibreSourceRegistry.builder().build()));
        assertEquals("MAPLIBRE_SOURCE_UNRESOLVED", missing.problem().code());
        assertEquals("bind", missing.problem().phase());
        assertEquals("/layers/0/source", missing.problem().location());

        MapLibreStyle hiddenMissing =
                read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"hidden","type":"circle","source":"absent",
                           "layout":{"visibility":"none"}}
                        ]}
                        """);
        MapLibreStyleBinding hiddenBinding =
                MapLibreStyleBinder.bind(hiddenMissing, MapLibreSourceRegistry.builder().build());
        assertTrue(hiddenBinding.layers().isEmpty());
        hiddenBinding.close();

        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(new SourceIdentity("memory", "memory"), List.of());
        MapLibreSourceRegistry.Builder builder =
                MapLibreSourceRegistry.builder().register("s", source);
        assertThrows(IllegalArgumentException.class, () -> builder.register("s", source));
        source.close();
    }

    @Test
    void webMercatorZoomIsExactAndCrsBounded() {
        assertEquals(
                0.0,
                MapLibreZoom.fromWebMercatorResolution(
                        CrsDefinitions.EPSG_3857, 2.0 * StrictMath.PI * 6_378_137.0 / 512.0),
                1.0e-12);
        assertEquals(
                3.0,
                MapLibreZoom.fromWebMercatorResolution(
                        CrsDefinitions.EPSG_3857,
                        2.0 * StrictMath.PI * 6_378_137.0 / (512.0 * 8.0)),
                1.0e-12);
        assertThrows(
                MapLibreBindException.class,
                () -> MapLibreZoom.fromWebMercatorResolution(CrsDefinitions.EPSG_4326, 1));
        CrsDefinition spoofed =
                new CrsDefinition(
                        "EPSG:3857",
                        CrsDefinitions.EPSG_3857.kind(),
                        CrsDefinitions.EPSG_3857.xAxis(),
                        CrsDefinitions.EPSG_3857.yAxis(),
                        new Envelope(-1, -1, 1, 1));
        assertThrows(
                MapLibreBindException.class,
                () -> MapLibreZoom.fromWebMercatorResolution(spoofed, 1));
    }

    @Test
    void rejectsLegacyAndUnboundedFilterFormsStructurally() {
        assertCode(
                "MAPLIBRE_EXPRESSION_TYPE",
                """
                {"version":8,"sources":{},"layers":[
                  {"id":"x","type":"circle","source":"s","filter":["==","kind","road"]}
                ]}
                """);
        assertCode(
                "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                """
                {"version":8,"sources":{},"layers":[
                  {"id":"x","type":"circle","source":"s","filter":["in",["get","kind"],"road"]}
                ]}
                """);

        String supportedWideChildren = ",true".repeat(1_024);
        assertEquals(
                1,
                read("{\"version\":8,\"sources\":{},\"layers\":["
                                + "{\"id\":\"x\",\"type\":\"circle\",\"source\":\"s\","
                                + "\"filter\":[\"all\""
                                + supportedWideChildren
                                + "]}]}")
                        .layers()
                        .size());

        String wideChildren = ",true".repeat(1_025);
        assertCode(
                "MAPLIBRE_LIMIT_EXCEEDED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + "{\"id\":\"x\",\"type\":\"circle\",\"source\":\"s\","
                        + "\"filter\":[\"all\""
                        + wideChildren
                        + "]}]}");
    }

    @Test
    void expressionLimitsAreAggregateAndStringOrderingUsesCodePoints() {
        String aggregateJson =
                """
                {"version":8,"sources":{},"layers":[
                  {"id":"a","type":"circle","source":"s","filter":["==",["get","x"],1]},
                  {"id":"b","type":"circle","source":"s","filter":["==",["get","x"],1]}
                ]}
                """;
        byte[] bytes = aggregateJson.getBytes(StandardCharsets.UTF_8);
        MapLibreReadLimits limits =
                new MapLibreReadLimits(
                        bytes.length,
                        16,
                        1_000,
                        1_024,
                        4_096,
                        100,
                        1,
                        2,
                        1,
                        4,
                        4,
                        2,
                        2,
                        0,
                        2,
                        65_536);
        MapLibreReadException aggregate =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        bytes,
                                        new MapLibreReadOptions(limits, CancellationToken.none())));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", aggregate.problem().code());
        assertEquals("expressionNodes", aggregate.problem().context().get("limit"));

        FeaturePortrayalResolver ordering =
                FeaturePortrayalResolver.compile(
                        read("""
                                        {"version":8,"sources":{},"layers":[
                                          {"id":"order","type":"circle","source":"s",
                                           "filter":["<",["get","text"],""]}
                                        ]}
                                        """)
                                .layers()
                                .getFirst()
                                .portrayal()
                                .orElseThrow());
        PortrayalEvaluationContext point =
                PortrayalEvaluationContext.UNSCALED.withGeometryType(PortrayalGeometryType.POINT);
        assertTrue(ordering.resolveAll(Map.of("text", "𐀀"), point).marker().isEmpty());

        FeaturePortrayalResolver foldedOrdering =
                FeaturePortrayalResolver.compile(
                        read("""
                                        {
                                          "version":8,
                                          "sources":{"tracks":{"type":"geojson"}},
                                          "layers":[{
                                            "id":"unicode-order",
                                            "type":"circle",
                                            "source":"tracks",
                                            "filter":["<",["literal","𐀀"],["literal",""]],
                                            "paint":{"circle-radius":4,"circle-color":"#123456"}
                                          }]
                                        }
                                        """)
                                .layers()
                                .getFirst()
                                .portrayal()
                                .orElseThrow());
        assertTrue(foldedOrdering.resolveAll(Map.of(), point).marker().isEmpty());
    }

    private static MapLibreStyle read(String json) {
        return MapLibreStyles.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCode(String code, String json) {
        MapLibreReadException failure = assertThrows(MapLibreReadException.class, () -> read(json));
        assertEquals(code, failure.problem().code());
    }
}
