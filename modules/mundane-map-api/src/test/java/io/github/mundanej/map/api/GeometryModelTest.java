package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class GeometryModelTest {
    @Test
    void coordinateSequenceDefensivelyCopiesInputAndOutput() {
        double[] input = {1.0, 2.0, 3.0, 4.0};
        CoordinateSequence sequence = CoordinateSequence.of(input);
        input[0] = 99.0;

        assertEquals(1.0, sequence.x(0));
        double[] output = sequence.toArray();
        output[0] = 88.0;
        assertEquals(1.0, sequence.x(0));
        assertNotSame(input, output);
        assertEquals(new Envelope(1.0, 2.0, 3.0, 4.0), sequence.envelope());
    }

    @Test
    void coordinatesAndSequencesRejectNonFiniteOrIncompleteInput() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(Double.NaN, 0.0));
        assertThrows(NullPointerException.class, () -> new PointGeometry(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> CoordinateSequence.of(0.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> CoordinateSequence.of());
        assertThrows(IllegalArgumentException.class, () -> CoordinateSequence.of(1.0, 2.0, 3.0));
    }

    @Test
    void lineAndPolygonEnforceTheirCardinalityAndTopology() {
        CoordinateSequence onePoint = CoordinateSequence.of(0.0, 0.0);
        CoordinateSequence shortClosed = CoordinateSequence.of(0.0, 0.0, 1.0, 0.0, 0.0, 0.0);

        assertThrows(IllegalArgumentException.class, () -> new LineStringGeometry(onePoint));
        assertThrows(IllegalArgumentException.class, () -> new PolygonGeometry(shortClosed));

        CoordinateSequence exterior = CoordinateSequence.of(0.0, 0.0, 5.0, 0.0, 5.0, 5.0, 0.0, 0.0);
        CoordinateSequence openHole = CoordinateSequence.of(1.0, 1.0, 2.0, 1.0, 2.0, 2.0, 1.0, 2.0);
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new PolygonGeometry(exterior, List.of(openHole)));
        assertTrue(failure.getMessage().contains("hole"));
    }

    @Test
    void polygonRequiresAClosedRing() {
        CoordinateSequence open = CoordinateSequence.of(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0);

        assertThrows(IllegalArgumentException.class, () -> new PolygonGeometry(open));
    }

    @Test
    void featureCopiesAttributes() {
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("kind", "city");
        Feature feature =
                new Feature(
                        "one",
                        "One",
                        new PointGeometry(new Coordinate(1.0, 2.0)),
                        attributes,
                        FeatureStyle.point(Rgba.rgb(10, 20, 30), 8.0));
        attributes.put("kind", "changed");

        assertEquals("city", feature.attributes().get("kind"));
        assertThrows(UnsupportedOperationException.class, () -> feature.attributes().put("x", "y"));
    }

    @Test
    void legacyFeatureStylesRetainClosedFactoriesAndValidation() {
        Rgba stroke = Rgba.rgb(1, 2, 3);
        Rgba fill = Rgba.rgb(4, 5, 6);
        FeatureStyle point = FeatureStyle.point(fill, 8);
        FeatureStyle line = FeatureStyle.line(stroke, 2);
        FeatureStyle polygon = FeatureStyle.polygon(stroke, fill, 3);

        assertEquals(fill, point.fill());
        assertEquals(8, point.pointDiameter());
        assertEquals(Rgba.TRANSPARENT, line.fill());
        assertEquals(2, line.strokeWidth());
        assertEquals(fill, polygon.fill());
        assertEquals(SymbolRole.LEGACY_GEOMETRY, polygon.role());
        assertEquals(FeatureStyle.RENDERER_KEY, polygon.rendererKey());
        assertEquals(1, polygon.opacity());
        assertThrows(NullPointerException.class, () -> FeatureStyle.point(null, 2));
        assertThrows(IllegalArgumentException.class, () -> FeatureStyle.line(stroke, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureStyle(stroke, fill, Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> new FeatureStyle(stroke, fill, 1, 0));
    }

    @Test
    void polygonCopiesItsHoleList() {
        CoordinateSequence exterior =
                CoordinateSequence.of(0.0, 0.0, 5.0, 0.0, 5.0, 5.0, 0.0, 5.0, 0.0, 0.0);
        CoordinateSequence hole = CoordinateSequence.of(1.0, 1.0, 2.0, 1.0, 2.0, 2.0, 1.0, 1.0);
        List<CoordinateSequence> holes = new java.util.ArrayList<>();
        holes.add(hole);
        PolygonGeometry polygon = new PolygonGeometry(exterior, holes);
        holes.clear();

        assertEquals(1, polygon.holes().size());
    }

    @Test
    void featuresValidateEveryGeometryRoleAndSymbolBoundary() {
        MarkerSymbol marker = new TestMarker(1);
        LineSymbol lineSymbol = new TestLine(1);
        FillSymbol fill = new TestFill(1);
        PointGeometry point = new PointGeometry(new Coordinate(0, 0));
        LineStringGeometry line = new LineStringGeometry(CoordinateSequence.of(0, 0, 1, 1));
        PolygonGeometry polygon =
                new PolygonGeometry(CoordinateSequence.of(0, 0, 1, 0, 0, 1, 0, 0));

        assertEquals(point, feature("point", point, marker).geometry());
        assertEquals(line, feature("line", line, lineSymbol).geometry());
        assertEquals(polygon, feature("polygon", polygon, fill).geometry());
        assertTrue(
                feature(
                                        "multipoint",
                                        new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                                        marker)
                                .geometry()
                        instanceof MultiPointGeometry);
        assertTrue(
                feature(
                                        "multiline",
                                        MultiLineStringGeometry.ofParts(
                                                List.of(line.coordinates())),
                                        lineSymbol)
                                .geometry()
                        instanceof MultiLineStringGeometry);
        assertTrue(
                feature("multipolygon", MultiPolygonGeometry.ofPolygons(List.of(polygon)), fill)
                                .geometry()
                        instanceof MultiPolygonGeometry);

        assertThrows(IllegalArgumentException.class, () -> feature(" ", point, marker));
        assertThrows(
                NullPointerException.class, () -> new Feature("id", null, point, Map.of(), marker));
        assertThrows(SymbolException.class, () -> feature("wrong-role", point, lineSymbol));
        assertThrows(
                SymbolException.class,
                () ->
                        feature(
                                "unsupported-symbol",
                                point,
                                new Symbol() {
                                    @Override
                                    public SymbolRole role() {
                                        return SymbolRole.MARKER;
                                    }

                                    @Override
                                    public SymbolRendererKey rendererKey() {
                                        return new SymbolRendererKey("test.unsupported");
                                    }

                                    @Override
                                    public double opacity() {
                                        return 1;
                                    }
                                }));
        assertThrows(
                NullPointerException.class,
                () -> feature("null-key", point, new TestMarker(1, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> feature("bad-opacity", point, new TestMarker(Double.NaN)));
    }

    @Test
    void pointerEventRetainsItsTypeAndCoordinates() {
        Coordinate mapCoordinate = new Coordinate(-71.0, 42.0);

        MapPointerEvent event =
                new MapPointerEvent(
                        MapPointerEvent.Type.CLICKED, 120.0, 80.0, Optional.of(mapCoordinate));

        assertEquals(MapPointerEvent.Type.CLICKED, event.type());
        assertEquals(120.0, event.screenX());
        assertEquals(80.0, event.screenY());
        assertEquals(Optional.of(mapCoordinate), event.mapCoordinate());
    }

    private static Feature feature(String id, Geometry geometry, Symbol symbol) {
        return new Feature(id, id, geometry, Map.of(), symbol);
    }

    private record TestMarker(double opacity, SymbolRendererKey rendererKey)
            implements MarkerSymbol {
        private TestMarker(double opacity) {
            this(opacity, new SymbolRendererKey("test.marker"));
        }
    }

    private record TestLine(double opacity) implements LineSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("test.line");
        }
    }

    private record TestFill(double opacity) implements FillSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("test.fill");
        }
    }
}
