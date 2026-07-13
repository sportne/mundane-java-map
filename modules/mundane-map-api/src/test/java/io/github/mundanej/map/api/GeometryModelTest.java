package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    void pointerEventRetainsItsTypeAndCoordinates() {
        Coordinate mapCoordinate = new Coordinate(-71.0, 42.0);

        MapPointerEvent event =
                new MapPointerEvent(MapPointerEvent.Type.CLICKED, 120.0, 80.0, mapCoordinate);

        assertEquals(MapPointerEvent.Type.CLICKED, event.type());
        assertEquals(120.0, event.screenX());
        assertEquals(80.0, event.screenY());
        assertEquals(mapCoordinate, event.mapCoordinate());
    }
}
