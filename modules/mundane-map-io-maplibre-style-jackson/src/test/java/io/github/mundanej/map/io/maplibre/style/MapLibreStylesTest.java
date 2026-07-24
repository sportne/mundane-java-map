package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapLibreStylesTest {
    @Test
    void readsOrderedLiteralLayersAndRetainedMetadata() {
        MapLibreStyle style = read(completeStyle());

        assertEquals("literal", style.name().orElseThrow());
        assertEquals("test", style.metadata().get("owner"));
        assertEquals(AttributeNull.INSTANCE, style.metadata().get("optional"));
        assertEquals(-75.0, style.camera().longitude().orElseThrow());
        assertEquals(10.5, style.camera().zoom().orElseThrow());
        assertEquals(1, style.sources().size());
        assertEquals("memory", style.sources().get(0).id());
        assertEquals("memory.geojson", style.sources().get(0).dataLocator().orElseThrow());
        assertEquals(3, style.layers().size());
        assertEquals(
                java.util.List.of("points", "roads", "land"),
                style.layers().stream().map(MapLibreLayer::id).toList());

        CompositeSymbol marker =
                assertInstanceOf(
                        CompositeSymbol.class,
                        fixed(style.layers().get(0).portrayal().orElseThrow()).marker());
        assertEquals(2, marker.children().size());
        VectorMarkerSymbol ring =
                assertInstanceOf(VectorMarkerSymbol.class, marker.children().get(0));
        VectorMarkerSymbol disk =
                assertInstanceOf(VectorMarkerSymbol.class, marker.children().get(1));
        assertEquals(new Rgba(17, 34, 51, 255), ring.fill());
        assertEquals(new Rgba(51, 102, 153, 204), disk.fill());
        assertEquals(16.0, ring.placement().size().width());
        assertEquals(4.0, ring.placement().offsetX());
        assertEquals(-2.0, ring.placement().offsetY());

        SolidLineSymbol line =
                assertInstanceOf(
                        SolidLineSymbol.class,
                        fixed(style.layers().get(1).portrayal().orElseThrow()).line());
        assertEquals(3.0, line.stroke().width().value());
        assertEquals(0.75, line.opacity());

        SolidFillSymbol fill =
                assertInstanceOf(
                        SolidFillSymbol.class,
                        fixed(style.layers().get(2).portrayal().orElseThrow()).fill());
        assertEquals(new Rgba(68, 136, 68, 128), fill.fill());
        SolidLineSymbol outline =
                assertInstanceOf(SolidLineSymbol.class, fill.outline().orElseThrow());
        assertEquals(Rgba.rgb(68, 136, 68), outline.stroke().color());
    }

    @Test
    void invisibleAndDegenerateLayersRetainOrderWithoutPortrayal() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"hidden","type":"circle","source":"s",
                           "layout":{"visibility":"none"}},
                          {"id":"hidden-line","type":"line","source":"s",
                           "layout":{"visibility":"none"}},
                          {"id":"zero-line","type":"line","source":"s",
                           "layout":{},"paint":{"line-width":0}},
                          {"id":"zero-circle","type":"circle","source":"s",
                           "paint":{"circle-radius":0,"circle-stroke-width":0}}
                        ]}
                        """);

        assertFalse(style.layers().get(0).visible());
        assertFalse(style.layers().get(1).visible());
        assertTrue(style.layers().stream().allMatch(layer -> layer.portrayal().isEmpty()));
    }

    @Test
    void explicitFillOutlineAndTransparentCircleArePreserved() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"fill","type":"fill","source":"s",
                           "paint":{"fill-color":"#01020304",
                                    "fill-outline-color":"#a0b0c080","fill-opacity":0.5}},
                          {"id":"stroke","type":"circle","source":"s",
                           "paint":{"circle-radius":0,"circle-color":"#00000000",
                                    "circle-stroke-width":2,"circle-stroke-color":"#ff0000"}}
                        ]}
                        """);
        SolidFillSymbol fill =
                assertInstanceOf(
                        SolidFillSymbol.class,
                        fixed(style.layers().get(0).portrayal().orElseThrow()).fill());
        SolidLineSymbol outline =
                assertInstanceOf(SolidLineSymbol.class, fill.outline().orElseThrow());
        assertEquals(new Rgba(160, 176, 192, 128), outline.stroke().color());
        assertEquals(0.5, fill.opacity());
        VectorMarkerSymbol stroke =
                assertInstanceOf(
                        VectorMarkerSymbol.class,
                        fixed(style.layers().get(1).portrayal().orElseThrow()).marker());
        assertEquals(4.0, stroke.placement().size().width());
    }

    @Test
    void rejectsGrammarExpressionsAndUnsupportedRenderingSemantics() {
        assertCode("MAPLIBRE_JSON_INVALID", "[]");
        assertCode(
                "MAPLIBRE_JSON_INVALID", "{\"version\":8,\"sources\":{},\"layers\":[]} trailing");
        assertCode(
                "MAPLIBRE_JSON_INVALID",
                "{\"version\":8,\"version\":8,\"sources\":{},\"layers\":[]}");
        assertCode(
                "MAPLIBRE_VERSION_UNSUPPORTED",
                "{\"version\":7,\"sources\":{},\"layers\":[" + layer("\"type\":\"circle\"") + "]}");
        assertCode(
                "MAPLIBRE_ROOT_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"sprite\":\"x\",\"layers\":["
                        + layer("\"type\":\"circle\"")
                        + "]}");
        assertCode(
                "MAPLIBRE_SOURCE_UNSUPPORTED",
                "{\"version\":8,\"sources\":{\"x\":{\"type\":\"vector\"}},\"layers\":["
                        + layer("\"type\":\"circle\"")
                        + "]}");
        assertCode(
                "MAPLIBRE_LAYER_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":[" + layer("\"type\":\"raster\"") + "]}");
        assertCode(
                "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer(
                                "\"type\":\"circle\",\"paint\":"
                                        + "{\"circle-radius\":[\"get\",\"size\"]}")
                        + "]}");
        assertCode(
                "MAPLIBRE_PROPERTY_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer("\"type\":\"line\",\"paint\":{\"line-dasharray\":[1,2]}")
                        + "]}");
        assertCode(
                "MAPLIBRE_PROPERTY_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer("\"type\":\"line\",\"layout\":{\"line-cap\":\"butt\"}")
                        + "]}");
        assertCode(
                "MAPLIBRE_PROPERTY_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer(
                                "\"type\":\"circle\",\"layout\":{\"visibility\":\"none\"},"
                                        + "\"paint\":{\"arbitrary\":1}")
                        + "]}");
        assertCode(
                "MAPLIBRE_VALUE_INVALID",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer("\"type\":\"fill\",\"paint\":{\"fill-color\":\"red\"}")
                        + "]}");
    }

    @Test
    void enforcesSourceLayerMetadataCameraAndInputLimits() {
        assertCode(
                "MAPLIBRE_VALUE_INVALID",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer("\"type\":\"circle\",\"minzoom\":8,\"maxzoom\":8")
                        + "]}");
        assertCode(
                "MAPLIBRE_VALUE_INVALID",
                "{\"version\":8,\"sources\":{},\"metadata\":{\"nested\":{}},\"layers\":["
                        + layer("\"type\":\"circle\"")
                        + "]}");
        assertCode(
                "MAPLIBRE_VALUE_INVALID",
                "{\"version\":8,\"sources\":{},\"center\":[181,0],\"layers\":["
                        + layer("\"type\":\"circle\"")
                        + "]}");
        MapLibreReadLimits tight =
                new MapLibreReadLimits(32, 8, 64, 16, 32, 16, 1, 1, 1, 8, 4, 2, 2, 1, 4, 32);
        MapLibreReadException input =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        completeStyle().getBytes(StandardCharsets.UTF_8),
                                        new MapLibreReadOptions(
                                                tight,
                                                io.github.mundanej.map.api.CancellationToken
                                                        .none())));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", input.problem().code());

        MapLibreReadException cancelled =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        "{}".getBytes(StandardCharsets.UTF_8),
                                        new MapLibreReadOptions(
                                                MapLibreReadLimits.defaults(), () -> true)));
        assertEquals("MAPLIBRE_CANCELLED", cancelled.problem().code());
    }

    @Test
    void acceptsUtf8BomAndRejectsOtherEncodingsAndCallerMutation() {
        byte[] json = completeStyle().getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[json.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(json, 0, bom, 3, json.length);
        assertEquals(3, MapLibreStyles.read(bom).layers().size());

        byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28};
        assertCode("MAPLIBRE_JSON_INVALID", invalidUtf8);
        assertCode("MAPLIBRE_JSON_INVALID", new byte[] {(byte) 0xff, (byte) 0xfe, 0, 0});
        assertCode("MAPLIBRE_JSON_INVALID", completeStyle().getBytes(StandardCharsets.UTF_16BE));
        assertCode("MAPLIBRE_JSON_INVALID", completeStyle().getBytes(StandardCharsets.UTF_16LE));

        byte[] mutable = completeStyle().getBytes(StandardCharsets.UTF_8);
        MapLibreStyle style = MapLibreStyles.read(mutable);
        java.util.Arrays.fill(mutable, (byte) 0);
        assertEquals("points", style.layers().get(0).id());
        assertArrayEquals(
                new String[] {"points", "roads", "land"},
                style.layers().stream().map(MapLibreLayer::id).toArray(String[]::new));
    }

    @Test
    void publicValuesValidateTheirInvariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapLibreReadLimits(1, 1, 1, 2, 1, 1, 0, 1, 0, 1, 1, 1, 1, 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MapLibreCamera(
                                java.util.OptionalDouble.of(0),
                                java.util.OptionalDouble.empty(),
                                java.util.OptionalDouble.empty(),
                                java.util.OptionalDouble.empty(),
                                java.util.OptionalDouble.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapLibreProblem("bad", "read", "/", Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MapLibreSourceDescriptor(
                                " bad ", java.util.Optional.empty(), java.util.Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MapLibreStyle(
                                java.util.Optional.empty(),
                                Map.of("bad", new java.util.ArrayList<>()),
                                MapLibreCamera.EMPTY,
                                java.util.List.of(),
                                java.util.List.of(
                                        new MapLibreLayer(
                                                "x",
                                                "s",
                                                MapLibreLayerType.CIRCLE,
                                                true,
                                                0,
                                                24,
                                                Map.of(),
                                                java.util.Optional.empty()))));
        String longText = "x".repeat(65_537);
        new MapLibreSourceDescriptor(
                longText, java.util.Optional.of(longText), java.util.Optional.of(longText));
        new MapLibreLayer(
                longText,
                longText,
                MapLibreLayerType.CIRCLE,
                false,
                0,
                24,
                Map.of("key", longText),
                java.util.Optional.empty());
        new MapLibreStyle(
                java.util.Optional.of(longText),
                Map.of("", "accepted"),
                MapLibreCamera.EMPTY,
                java.util.List.of(),
                java.util.List.of(
                        new MapLibreLayer(
                                longText,
                                longText,
                                MapLibreLayerType.CIRCLE,
                                false,
                                0,
                                24,
                                Map.of(),
                                java.util.Optional.empty())));
    }

    @Test
    void hostileIdentifiersMetadataAndOwnedAllocationFailStructurally() {
        String longMember = "x".repeat(300);
        assertCode(
                "MAPLIBRE_ROOT_UNSUPPORTED",
                "{\"version\":8,\"sources\":{},\"layers\":["
                        + layer("\"type\":\"circle\"")
                        + "],\""
                        + longMember
                        + "\":1}");
        assertCode(
                "MAPLIBRE_VALUE_INVALID",
                "{\"version\":8,\"sources\":{\" \":{\"type\":\"geojson\"}},\"layers\":["
                        + layer("\"type\":\"circle\"")
                        + "]}");

        MapLibreReadLimits aggregate =
                new MapLibreReadLimits(
                        4_096, 16, 1_000, 1_024, 2_048, 100, 1, 2, 1, 8, 4, 2, 2, 1, 4, 65_536);
        String metadata =
                "{\"version\":8,\"sources\":{},\"metadata\":{\"a\":1},\"layers\":["
                        + layer("\"type\":\"circle\",\"metadata\":{\"b\":2}")
                        + "]}";
        MapLibreReadException aggregateFailure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        metadata.getBytes(StandardCharsets.UTF_8),
                                        new MapLibreReadOptions(
                                                aggregate,
                                                io.github.mundanej.map.api.CancellationToken
                                                        .none())));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", aggregateFailure.problem().code());

        byte[] allocationJson =
                ("{\"version\":8,\"sources\":{},\"layers\":["
                                + layer(
                                        "\"type\":\"circle\",\"paint\":{\"circle-radius\":"
                                                + "[1,2,3,4,5,6,7,8,9,10]}")
                                + "]}")
                        .getBytes(StandardCharsets.UTF_8);
        MapLibreReadLimits owned =
                new MapLibreReadLimits(
                        allocationJson.length,
                        16,
                        1_000,
                        1_024,
                        2_048,
                        100,
                        1,
                        2,
                        1,
                        8,
                        4,
                        2,
                        2,
                        1,
                        4,
                        allocationJson.length + 200L);
        MapLibreReadException ownedFailure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        allocationJson,
                                        new MapLibreReadOptions(
                                                owned,
                                                io.github.mundanej.map.api.CancellationToken
                                                        .none())));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", ownedFailure.problem().code());
    }

    private static Resolved fixed(FeaturePortrayal portrayal) {
        return new Resolved(
                portrayal
                        .marker()
                        .map(selector -> ((FixedSymbolSelector) selector).symbol())
                        .orElse(null),
                portrayal
                        .line()
                        .map(selector -> ((FixedSymbolSelector) selector).symbol())
                        .orElse(null),
                portrayal
                        .fill()
                        .map(selector -> ((FixedSymbolSelector) selector).symbol())
                        .orElse(null));
    }

    private static MapLibreStyle read(String json) {
        return MapLibreStyles.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCode(String expected, String json) {
        assertCode(expected, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCode(String expected, byte[] bytes) {
        MapLibreReadException failure =
                assertThrows(MapLibreReadException.class, () -> MapLibreStyles.read(bytes));
        assertEquals(expected, failure.problem().code());
        assertEquals("read", failure.problem().phase());
        assertFalse(failure.getMessage().contains("{"));
    }

    private static String layer(String members) {
        return "{\"id\":\"a\",\"source\":\"s\"," + members + '}';
    }

    private static String completeStyle() {
        return """
                {
                  "version": 8,
                  "name": "literal",
                  "metadata": {"owner":"test","optional":null},
                  "center": [-75, 40],
                  "zoom": 10.5,
                  "bearing": 15,
                  "pitch": 30,
                  "sources": {
                    "memory": {
                      "type": "geojson",
                      "data": "memory.geojson",
                      "attribution": "Project fixture"
                    }
                  },
                  "layers": [
                    {
                      "id": "points", "type": "circle", "source": "memory",
                      "paint": {
                        "circle-radius": 6,
                        "circle-color": "#336699cc",
                        "circle-opacity": 0.8,
                        "circle-stroke-width": 2,
                        "circle-stroke-color": "#112233",
                        "circle-stroke-opacity": 0.9,
                        "circle-translate": [4, -2],
                        "circle-translate-anchor": "viewport"
                      }
                    },
                    {
                      "id": "roads", "type": "line", "source": "memory",
                      "layout": {"line-cap":"round","line-join":"round"},
                      "paint": {"line-color":"#123456","line-width":3,"line-opacity":0.75}
                    },
                    {
                      "id": "land", "type": "fill", "source": "memory",
                      "paint": {"fill-color":"#44884480","fill-opacity":0.6}
                    }
                  ]
                }
                """;
    }

    private record Resolved(Object marker, Object line, Object fill) {}
}
