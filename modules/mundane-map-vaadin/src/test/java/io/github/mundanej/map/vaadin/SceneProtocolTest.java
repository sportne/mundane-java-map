package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SceneProtocolTest {
    private static final Rgba RED = Rgba.rgb(190, 20, 30);
    private static final SymbolStroke BLUE_STROKE =
            new SymbolStroke(Rgba.rgb(20, 40, 190), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL));

    @Test
    void encodesPointLinePolygonAndHoleInStablePaintOrder() {
        Feature point =
                new Feature(
                        "point",
                        "Point",
                        new PointGeometry(new Coordinate(2, 3)),
                        Map.of("ignored", true),
                        marker());
        Feature line =
                new Feature(
                        "line",
                        "Line",
                        new LineStringGeometry(CoordinateSequence.of(-4, -2, 8, 6)),
                        Map.of(),
                        SolidLineSymbol.of(BLUE_STROKE, 0.75));
        SolidLineSymbol outline = SolidLineSymbol.of(BLUE_STROKE, 0.5);
        Feature polygon =
                new Feature(
                        "polygon",
                        "Polygon",
                        new PolygonGeometry(
                                CoordinateSequence.of(0, 0, 10, 0, 10, 10, 0, 10, 0, 0),
                                List.of(CoordinateSequence.of(2, 2, 4, 2, 4, 4, 2, 4, 2, 2))),
                        Map.of(),
                        SolidFillSymbol.of(RED, Optional.of(outline), 0.6));
        InMemoryLayer source = new InMemoryLayer("layer", "Layer", List.of(point, line, polygon));

        SceneProtocol.Result result =
                protocol()
                        .encode(
                                List.of(source),
                                Rgba.rgb(250, 250, 250),
                                new MapViewport(640, 480, 1, 2, 0.5),
                                3,
                                7);

        assertEquals(1, result.layers().size());
        assertNotSame(source, result.layers().getFirst());
        assertEquals(
                List.of("point", "line", "polygon"),
                result.layers().getFirst().features().stream().map(Feature::id).toList());
        assertEquals(new Envelope(-4, -2, 10, 10), result.envelope().orElseThrow());
        assertEquals(747, result.logicalBytes());
        assertEquals(SceneProtocol.VERSION, result.scene().get("protocolVersion"));
        assertEquals(3L, result.scene().get("componentGeneration"));
        assertEquals(7L, result.scene().get("sceneGeneration"));
        assertEquals(0L, result.scene().get("viewportGeneration"));
        List<?> encodedLayers = assertInstanceOf(List.class, result.scene().get("layers"));
        Map<?, ?> encodedLayer = assertInstanceOf(Map.class, encodedLayers.getFirst());
        List<?> encodedFeatures = assertInstanceOf(List.class, encodedLayer.get("features"));
        assertEquals(
                List.of("point", "line", "polygon"),
                encodedFeatures.stream().map(value -> ((Map<?, ?>) value).get("id")).toList());
        Map<?, ?> polygonValue = (Map<?, ?>) encodedFeatures.get(2);
        Map<?, ?> polygonPrimitive =
                (Map<?, ?>) ((List<?>) polygonValue.get("primitives")).getFirst();
        assertEquals(2, ((List<?>) polygonPrimitive.get("rings")).size());
        assertInstanceOf(Map.class, polygonPrimitive.get("outline"));
        assertThrows(UnsupportedOperationException.class, () -> result.scene().put("bad", true));
        assertThrows(UnsupportedOperationException.class, () -> result.layers().add(source));
    }

    @Test
    void acceptsEmptySceneAndFillWithoutOutline() {
        SceneProtocol.Result empty =
                protocol().encode(List.of(), Rgba.TRANSPARENT, MapViewport.initial(1, 1), 0, 0);
        assertTrue(empty.layers().isEmpty());
        assertTrue(empty.envelope().isEmpty());

        Feature polygon =
                new Feature(
                        "p",
                        "",
                        new PolygonGeometry(CoordinateSequence.of(0, 0, 1, 0, 1, 1, 0, 0)),
                        Map.of(),
                        SolidFillSymbol.of(RED, 1));
        SceneProtocol.Result result =
                protocol()
                        .encode(
                                List.of(new InMemoryLayer("l", "L", List.of(polygon))),
                                Rgba.TRANSPARENT,
                                MapViewport.initial(10, 10),
                                1,
                                1);
        assertFalse(result.envelope().isEmpty());
    }

    @Test
    void rejectsDuplicateIdentitiesAndNullValues() {
        InMemoryLayer first = new InMemoryLayer("same", "One", List.of());
        InMemoryLayer second = new InMemoryLayer("same", "Two", List.of());
        MundaneMapException duplicateLayer =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                protocol()
                                        .encode(
                                                List.of(first, second),
                                                Rgba.TRANSPARENT,
                                                MapViewport.initial(10, 10),
                                                0,
                                                1));
        assertEquals(MundaneMapException.DUPLICATE_ID, duplicateLayer.code());
        assertEquals(Map.of("identityNamespace", "layer"), duplicateLayer.context());

        Feature feature = point("same");
        Layer duplicateFeatures = layer("layer", "Layer", List.of(feature, feature));
        MundaneMapException duplicateFeature =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                protocol()
                                        .encode(
                                                List.of(duplicateFeatures),
                                                Rgba.TRANSPARENT,
                                                MapViewport.initial(10, 10),
                                                0,
                                                1));
        assertEquals(MundaneMapException.DUPLICATE_ID, duplicateFeature.code());
        assertEquals(Map.of("identityNamespace", "feature"), duplicateFeature.context());
        assertThrows(
                NullPointerException.class,
                () -> protocol().encode(null, Rgba.TRANSPARENT, MapViewport.initial(10, 10), 0, 1));
        assertThrows(
                NullPointerException.class,
                () ->
                        protocol()
                                .encode(
                                        List.of((Layer) null),
                                        Rgba.TRANSPARENT,
                                        MapViewport.initial(10, 10),
                                        0,
                                        1));
    }

    @Test
    void rejectsEveryOutOfSliceGeometryAndSymbolShapeAtomically() {
        assertUnsupported(
                new Feature(
                        "multi",
                        "Multi",
                        new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                        Map.of(),
                        marker()));
        assertUnsupported(
                new Feature(
                        "raster",
                        "Raster",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        RasterIconSymbol.nativeScreenSize(
                                1, 1, new int[] {0xffffffff}, RasterInterpolation.NEAREST, 1)));
        MarkerPlacement moved =
                new MarkerPlacement(
                        SymbolSize.square(10, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.NORTH,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        assertUnsupported(
                new Feature(
                        "moved",
                        "Moved",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        VectorMarkerSymbol.of(
                                marker().path(),
                                marker().viewBox(),
                                RED,
                                Optional.empty(),
                                moved,
                                1)));
        SolidLineSymbol endpointLine =
                SolidLineSymbol.of(BLUE_STROKE, Optional.of(marker()), Optional.empty(), 1);
        assertUnsupported(
                new Feature(
                        "endpoint",
                        "Endpoint",
                        new LineStringGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                        Map.of(),
                        endpointLine));
        SymbolStroke mapStroke = new SymbolStroke(RED, new SymbolLength(1, SymbolUnit.MAP_UNIT));
        assertUnsupported(
                new Feature(
                        "map-line",
                        "Map line",
                        new LineStringGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                        Map.of(),
                        SolidLineSymbol.of(mapStroke, 1)));
        assertUnsupported(
                new Feature(
                        "outline",
                        "Outline",
                        new PolygonGeometry(CoordinateSequence.of(0, 0, 1, 0, 1, 1, 0, 0)),
                        Map.of(),
                        SolidFillSymbol.of(
                                RED,
                                Optional.of(
                                        SolidLineSymbol.of(
                                                BLUE_STROKE,
                                                Optional.of(marker()),
                                                Optional.empty(),
                                                1)),
                                1)));
    }

    @Test
    void enforcesAllConfigurableProtocolBoundaries() {
        InMemoryLayer one = new InMemoryLayer("one", "One", List.of(point("point")));
        assertLimit(new SceneProtocol.Limits(1, 1, 1, 1, 3, 1000, 10), List.of(one));
        assertLimit(new SceneProtocol.Limits(1, 1, 1, 1, 3, 1000, 2), List.of(one));
        assertLimit(new SceneProtocol.Limits(1, 1, 1, 1, 2, 1000, 10), List.of(one));
        assertLimit(new SceneProtocol.Limits(1, 1, 1, 0 + 1, 3, 1, 10), List.of(one));
        assertLimit(
                new SceneProtocol.Limits(1, 1, 1, 1, 3, 1000, 10),
                List.of(one, new InMemoryLayer("two", "Two", List.of())));
        assertLimit(
                new SceneProtocol.Limits(1, 1, 1, 1, 3, 1000, 10),
                List.of(new InMemoryLayer("one", "One", List.of(point("p1"), point("p2")))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SceneProtocol.Limits(0, 1, 1, 1, 1, 1, 1));
        MundaneMapException oversizedViewport =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                protocol()
                                        .encode(
                                                List.of(),
                                                Rgba.TRANSPARENT,
                                                MapViewport.initial(16_385, 1),
                                                1,
                                                1));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, oversizedViewport.code());
    }

    private static SceneProtocol protocol() {
        return new SceneProtocol(SceneProtocol.DEFAULT_LIMITS);
    }

    private static Feature point(String id) {
        return new Feature(id, id, new PointGeometry(new Coordinate(0, 0)), Map.of(), marker());
    }

    private static VectorMarkerSymbol marker() {
        VectorPath path =
                VectorPath.builder()
                        .moveTo(0, 0)
                        .lineTo(10, 0)
                        .lineTo(10, 10)
                        .lineTo(0, 10)
                        .close()
                        .build();
        return VectorMarkerSymbol.filledScreen(path, new Envelope(0, 0, 10, 10), RED, 12, 0.8);
    }

    private static Layer layer(String id, String name, List<Feature> features) {
        return new Layer() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public List<Feature> features() {
                return features;
            }

            @Override
            public Optional<Envelope> envelope() {
                return Optional.empty();
            }
        };
    }

    private static void assertUnsupported(Feature feature) {
        MundaneMapException exception =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                protocol()
                                        .encode(
                                                List.of(
                                                        new InMemoryLayer(
                                                                "layer",
                                                                "Layer",
                                                                List.of(feature))),
                                                Rgba.TRANSPARENT,
                                                MapViewport.initial(20, 20),
                                                1,
                                                1));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, exception.code());
        assertEquals("feature", exception.context().get("scope"));
        assertFalse(exception.context().containsValue(feature.id()));
    }

    private static void assertLimit(SceneProtocol.Limits limits, List<? extends Layer> layers) {
        MundaneMapException exception =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                new SceneProtocol(limits)
                                        .encode(
                                                layers,
                                                Rgba.TRANSPARENT,
                                                MapViewport.initial(20, 20),
                                                1,
                                                1));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, exception.code());
    }
}
