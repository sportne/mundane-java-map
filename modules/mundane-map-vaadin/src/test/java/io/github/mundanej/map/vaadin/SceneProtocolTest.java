package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
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
        assertEquals(1076, result.logicalBytes());
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
        assertEquals(
                List.of("polygon", "line", "line"),
                ((List<?>) polygonValue.get("primitives"))
                        .stream().map(value -> ((Map<?, ?>) value).get("kind")).toList());
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
    void expandsMultipartGeometryUnderOneStableLogicalFeatureIdentity() {
        PolygonGeometry firstPolygon =
                new PolygonGeometry(CoordinateSequence.of(0, 0, 2, 0, 2, 2, 0, 0));
        PolygonGeometry secondPolygon =
                new PolygonGeometry(
                        CoordinateSequence.of(3, 3, 6, 3, 6, 6, 3, 3),
                        List.of(CoordinateSequence.of(4, 4, 5, 4, 5, 5, 4, 4)));
        List<Feature> features =
                List.of(
                        new Feature(
                                "points",
                                "Points",
                                new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                                Map.of(),
                                marker()),
                        new Feature(
                                "lines",
                                "Lines",
                                MultiLineStringGeometry.ofParts(
                                        List.of(
                                                CoordinateSequence.of(0, 0, 1, 1),
                                                CoordinateSequence.of(2, 2, 3, 3, 4, 4))),
                                Map.of(),
                                SolidLineSymbol.of(BLUE_STROKE, 1)),
                        new Feature(
                                "polygons",
                                "Polygons",
                                MultiPolygonGeometry.ofPolygons(
                                        List.of(firstPolygon, secondPolygon)),
                                Map.of(),
                                SolidFillSymbol.of(RED, 1)));

        SceneProtocol.Result result =
                protocol()
                        .encode(
                                List.of(new InMemoryLayer("multipart", "Multipart", features)),
                                Rgba.TRANSPARENT,
                                MapViewport.initial(100, 100),
                                1,
                                1);

        Map<?, ?> layer = (Map<?, ?>) ((List<?>) result.scene().get("layers")).getFirst();
        List<?> encodedFeatures = (List<?>) layer.get("features");
        assertEquals(
                List.of("points", "lines", "polygons"),
                encodedFeatures.stream().map(value -> ((Map<?, ?>) value).get("id")).toList());
        assertEquals(
                List.of(2, 2, 2),
                encodedFeatures.stream()
                        .map(value -> ((List<?>) ((Map<?, ?>) value).get("primitives")).size())
                        .toList());
        Map<?, ?> polygonPrimitive =
                (Map<?, ?>)
                        ((List<?>) ((Map<?, ?>) encodedFeatures.get(2)).get("primitives")).get(1);
        assertEquals(2, ((List<?>) polygonPrimitive.get("rings")).size());
    }

    @Test
    void omitsCoincidentLinePartsAndTheirEndpointMarkers() {
        SolidLineSymbol endpoints =
                SolidLineSymbol.of(BLUE_STROKE, Optional.of(marker()), Optional.of(marker()), 1);
        List<Feature> features =
                List.of(
                        new Feature(
                                "coincident",
                                "Coincident",
                                new LineStringGeometry(CoordinateSequence.of(1, 1, 1, 1, 1, 1)),
                                Map.of(),
                                SolidLineSymbol.of(BLUE_STROKE, 1)),
                        new Feature(
                                "multipart",
                                "Multipart",
                                MultiLineStringGeometry.ofParts(
                                        List.of(
                                                CoordinateSequence.of(2, 2, 2, 2),
                                                CoordinateSequence.of(0, 0, 3, 0))),
                                Map.of(),
                                endpoints));

        SceneProtocol.Result result =
                protocol()
                        .encode(
                                List.of(new InMemoryLayer("lines", "Lines", features)),
                                Rgba.TRANSPARENT,
                                MapViewport.initial(20, 20),
                                1,
                                1);
        List<?> encoded =
                (List<?>)
                        ((Map<?, ?>) ((List<?>) result.scene().get("layers")).getFirst())
                                .get("features");
        assertTrue(kinds((Map<?, ?>) encoded.get(0)).isEmpty());
        assertEquals(List.of("line", "point", "point"), kinds((Map<?, ?>) encoded.get(1)));
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
    void completesBuiltInMarkerLineFillCompositeEndpointAndHatchMatrix() {
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
                        new SymbolSize(10, 14, SymbolUnit.MAP_UNIT),
                        SymbolAnchor.NORTH,
                        2,
                        -3,
                        37,
                        SymbolRotationMode.MAP_RELATIVE);
        VectorMarkerSymbol placed =
                VectorMarkerSymbol.of(
                        marker().path(),
                        marker().viewBox(),
                        RED,
                        Optional.of(BLUE_STROKE),
                        moved,
                        0.8);
        SolidLineSymbol endpointLine =
                SolidLineSymbol.of(
                        new SymbolStroke(RED, new SymbolLength(1, SymbolUnit.MAP_UNIT)),
                        Optional.of(CompositeSymbol.of(List.of(marker(), placed), 0.5)),
                        Optional.of(placed),
                        0.75);
        SymbolStroke mapStroke = new SymbolStroke(RED, new SymbolLength(1, SymbolUnit.MAP_UNIT));
        SolidLineSymbol outline = SolidLineSymbol.of(mapStroke, 0.6);
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.CROSS_DIAGONAL,
                        BLUE_STROKE,
                        new SymbolLength(4, SymbolUnit.MAP_UNIT),
                        SymbolRotationMode.MAP_RELATIVE,
                        Optional.of(CompositeSymbol.of(List.of(outline, outline), 0.5)),
                        0.7,
                        1234);
        HatchFillSymbol forward =
                HatchFillSymbol.of(
                        HatchPattern.FORWARD_DIAGONAL,
                        BLUE_STROKE,
                        new SymbolLength(5, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.SCREEN_RELATIVE,
                        1);
        HatchFillSymbol backward =
                HatchFillSymbol.of(
                        HatchPattern.BACKWARD_DIAGONAL,
                        BLUE_STROKE,
                        new SymbolLength(6, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.MAP_RELATIVE,
                        0.9);
        List<Feature> features =
                List.of(
                        new Feature(
                                "markers",
                                "Markers",
                                new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                                Map.of(),
                                CompositeSymbol.of(List.of(marker(), placed), 0.5)),
                        new Feature(
                                "endpoint",
                                "Endpoint",
                                new LineStringGeometry(CoordinateSequence.of(0, 0, 2, 0, 2, 3)),
                                Map.of(),
                                CompositeSymbol.of(
                                        List.of(SolidLineSymbol.of(BLUE_STROKE, 1), endpointLine),
                                        0.5)),
                        new Feature(
                                "fill",
                                "Fill",
                                new PolygonGeometry(
                                        CoordinateSequence.of(0, 0, 4, 0, 4, 4, 0, 0),
                                        List.of(CoordinateSequence.of(1, 1, 2, 1, 2, 2, 1, 1))),
                                Map.of(),
                                CompositeSymbol.of(
                                        List.of(
                                                SolidFillSymbol.of(RED, Optional.of(outline), 0.8),
                                                forward,
                                                backward,
                                                hatch),
                                        0.5)));

        SceneProtocol.Result result =
                protocol()
                        .encode(
                                List.of(new InMemoryLayer("symbols", "Symbols", features)),
                                Rgba.TRANSPARENT,
                                MapViewport.initial(200, 100),
                                1,
                                2);
        List<?> encoded =
                (List<?>)
                        ((Map<?, ?>) ((List<?>) result.scene().get("layers")).getFirst())
                                .get("features");
        assertEquals(
                List.of("point", "point", "point", "point"), kinds((Map<?, ?>) encoded.get(0)));
        assertEquals(
                List.of("line", "line", "point", "point", "point"),
                kinds((Map<?, ?>) encoded.get(1)));
        assertEquals(
                List.of(
                        "polygon", "line", "line", "hatch", "hatch", "hatch", "line", "line",
                        "line", "line"),
                kinds((Map<?, ?>) encoded.get(2)));
        Map<?, ?> placedPrimitive =
                (Map<?, ?>) ((List<?>) ((Map<?, ?>) encoded.get(0)).get("primitives")).get(2);
        assertEquals(List.of(10.0, 14.0), placedPrimitive.get("size"));
        assertEquals("MAP_UNIT", placedPrimitive.get("unit"));
        assertEquals("NORTH", placedPrimitive.get("anchor"));
        assertEquals("MAP_RELATIVE", placedPrimitive.get("rotationMode"));
        Map<?, ?> endpoint =
                (Map<?, ?>) ((List<?>) ((Map<?, ?>) encoded.get(1)).get("primitives")).get(2);
        assertEquals(Map.of("present", true, "value", 180.0), endpoint.get("endpointBearing"));
        Map<?, ?> end =
                (Map<?, ?>) ((List<?>) ((Map<?, ?>) encoded.get(1)).get("primitives")).get(4);
        assertEquals(Map.of("present", true, "value", 270.0), end.get("endpointBearing"));
        Map<?, ?> hatchPrimitive =
                (Map<?, ?>) ((List<?>) ((Map<?, ?>) encoded.get(2)).get("primitives")).get(5);
        assertEquals(
                List.of("FORWARD_DIAGONAL", "BACKWARD_DIAGONAL", "CROSS_DIAGONAL"),
                ((List<?>) ((Map<?, ?>) encoded.get(2)).get("primitives"))
                        .subList(3, 6).stream()
                                .map(value -> ((Map<?, ?>) value).get("pattern"))
                                .toList());
        assertEquals("CROSS_DIAGONAL", hatchPrimitive.get("pattern"));
        assertEquals(1234, hatchPrimitive.get("maxSegments"));
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

    @Test
    @SuppressWarnings("deprecation")
    void rejectsLegacyAndExcessivelyNestedSymbolsWithStableDiagnostics() {
        assertUnsupported(
                new Feature(
                        "legacy",
                        "Legacy",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        FeatureStyle.point(RED, 4)));
        MarkerSymbol custom =
                new MarkerSymbol() {
                    @Override
                    public SymbolRendererKey rendererKey() {
                        return new SymbolRendererKey("example.custom-marker");
                    }

                    @Override
                    public double opacity() {
                        return 1;
                    }
                };
        assertUnsupported(
                new Feature(
                        "custom",
                        "Custom",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        custom));

        Symbol nested = marker();
        for (int depth = 0; depth <= 64; depth++) {
            nested = CompositeSymbol.of(List.of(nested), 1);
        }
        Symbol overDepth = nested;
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
                                                                List.of(
                                                                        new Feature(
                                                                                "deep",
                                                                                "Deep",
                                                                                new PointGeometry(
                                                                                        new Coordinate(
                                                                                                0,
                                                                                                0)),
                                                                                Map.of(),
                                                                                overDepth)))),
                                                Rgba.TRANSPARENT,
                                                MapViewport.initial(20, 20),
                                                1,
                                                1));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, exception.code());
        assertEquals("symbolDepth", exception.context().get("limit"));
        assertEquals("64", exception.context().get("maximum"));
    }

    @Test
    void enforcesFocusedMultipartPrimitiveCoordinatePathAndByteLimits() {
        Feature points =
                new Feature(
                        "points",
                        "Points",
                        new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                        Map.of(),
                        marker());
        Feature lines =
                new Feature(
                        "lines",
                        "Lines",
                        MultiLineStringGeometry.ofParts(
                                List.of(
                                        CoordinateSequence.of(0, 0, 1, 1),
                                        CoordinateSequence.of(2, 2, 3, 3))),
                        Map.of(),
                        SolidLineSymbol.of(BLUE_STROKE, 1));
        PolygonGeometry first = new PolygonGeometry(CoordinateSequence.of(0, 0, 2, 0, 2, 2, 0, 0));
        PolygonGeometry second =
                new PolygonGeometry(
                        CoordinateSequence.of(3, 3, 6, 3, 6, 6, 3, 3),
                        List.of(CoordinateSequence.of(4, 4, 5, 4, 5, 5, 4, 4)));
        Feature polygons =
                new Feature(
                        "polygons",
                        "Polygons",
                        MultiPolygonGeometry.ofPolygons(List.of(first, second)),
                        Map.of(),
                        SolidFillSymbol.of(RED, 1));
        InMemoryLayer pointLayer = new InMemoryLayer("points", "Points", List.of(points));
        InMemoryLayer lineLayer = new InMemoryLayer("lines", "Lines", List.of(lines));
        InMemoryLayer polygonLayer = new InMemoryLayer("polygons", "Polygons", List.of(polygons));

        assertLimit(
                "primitives",
                new SceneProtocol.Limits(1, 1, 1, 100, 100, 100_000, 100),
                List.of(lineLayer));
        assertLimit(
                "coordinatePairs",
                new SceneProtocol.Limits(1, 1, 10, 11, 100, 100_000, 100),
                List.of(polygonLayer));
        assertLimit(
                "pathCommands",
                new SceneProtocol.Limits(
                        1, 1, 10, 100, marker().path().commandCount() * 2 - 1, 100_000, 100),
                List.of(pointLayer));
        SceneProtocol.Limits generous = new SceneProtocol.Limits(1, 1, 10, 100, 100, 100_000, 100);
        long multipartBytes =
                new SceneProtocol(generous)
                        .encode(
                                List.of(polygonLayer),
                                Rgba.TRANSPARENT,
                                MapViewport.initial(20, 20),
                                1,
                                1)
                        .logicalBytes();
        assertLimit(
                "logicalBytes",
                new SceneProtocol.Limits(1, 1, 10, 100, 100, multipartBytes - 1, 100),
                List.of(polygonLayer));
    }

    private static SceneProtocol protocol() {
        return new SceneProtocol(SceneProtocol.DEFAULT_LIMITS);
    }

    private static List<?> kinds(Map<?, ?> feature) {
        return ((List<?>) feature.get("primitives"))
                .stream().map(value -> ((Map<?, ?>) value).get("kind")).toList();
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
        assertLimit(null, limits, layers);
    }

    private static void assertLimit(
            String expectedLimit, SceneProtocol.Limits limits, List<? extends Layer> layers) {
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
        if (expectedLimit != null) {
            assertEquals(expectedLimit, exception.context().get("limit"));
        }
    }
}
