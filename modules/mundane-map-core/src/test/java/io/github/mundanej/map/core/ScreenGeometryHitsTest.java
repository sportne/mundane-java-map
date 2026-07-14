package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CoordinateSequence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenGeometryHitsTest {
    @Test
    void pointAndPolylineUseInclusiveToleranceAndHandleDegenerateSegments() {
        assertTrue(ScreenGeometryHits.pointWithin(0.0, 0.0, 3.0, 4.0, 5.0));
        assertFalse(ScreenGeometryHits.pointWithin(0.0, 0.0, 3.0, 4.0, 4.99));

        CoordinateSequence repeated = CoordinateSequence.of(0.0, 0.0, 0.0, 0.0, 10.0, 0.0);
        assertTrue(ScreenGeometryHits.polylineWithin(repeated, false, 5.0, 2.0, 2.0));
        assertTrue(ScreenGeometryHits.polylineWithin(repeated, false, 0.0, 1.0, 1.0));
        assertFalse(ScreenGeometryHits.polylineWithin(repeated, false, 5.0, 2.01, 2.0));
    }

    @Test
    void closedPolylineIncludesTheClosingEdge() {
        CoordinateSequence triangle = CoordinateSequence.of(0.0, 0.0, 10.0, 0.0, 10.0, 10.0);

        assertFalse(ScreenGeometryHits.polylineWithin(triangle, false, 5.0, 5.0, 0.1));
        assertTrue(ScreenGeometryHits.polylineWithin(triangle, true, 5.0, 5.0, 0.0));
    }

    @Test
    void polygonFillIncludesBoundariesAndExcludesHoleInteriors() {
        CoordinateSequence exterior = square(0.0, 0.0, 20.0);
        CoordinateSequence hole = square(0.0, 0.0, 4.0);

        assertTrue(ScreenGeometryHits.filledPolygonWithin(exterior, List.of(hole), 8.0, 0.0, 0.0));
        assertFalse(ScreenGeometryHits.filledPolygonWithin(exterior, List.of(hole), 0.0, 0.0, 0.0));
        assertTrue(ScreenGeometryHits.filledPolygonWithin(exterior, List.of(hole), 2.0, 0.0, 0.0));
        assertTrue(ScreenGeometryHits.filledPolygonWithin(exterior, List.of(hole), 2.5, 0.0, 0.5));
        assertFalse(ScreenGeometryHits.filledPolygonWithin(exterior, List.of(hole), 0.0, 0.0, 1.9));
    }

    @Test
    void convexQuadIncludesInteriorAndToleranceExpandedEdges() {
        double[] quad = {0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0};

        assertTrue(ScreenGeometryHits.convexQuadWithin(quad, 5.0, 5.0, 0.0));
        assertTrue(ScreenGeometryHits.convexQuadWithin(quad, 10.5, 5.0, 0.5));
        assertFalse(ScreenGeometryHits.convexQuadWithin(quad, 10.51, 5.0, 0.5));
    }

    @Test
    void extremeFiniteCoordinatesRemainDeterministicAndInvalidInputsFailFast() {
        double large = Double.MAX_VALUE / 2.0;
        CoordinateSequence line = CoordinateSequence.of(-large, 0.0, large, 0.0);
        assertTrue(ScreenGeometryHits.polylineWithin(line, false, 0.0, 0.0, 0.0));
        assertFalse(ScreenGeometryHits.polylineWithin(line, false, 0.0, 1.0, 0.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> ScreenGeometryHits.pointWithin(0.0, 0.0, 0.0, 0.0, -1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScreenGeometryHits.pointWithin(Double.NaN, 0.0, 0.0, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScreenGeometryHits.convexQuadWithin(new double[6], 0.0, 0.0, 0.0));
    }

    private static CoordinateSequence square(double centerX, double centerY, double size) {
        double half = size / 2.0;
        return CoordinateSequence.of(
                centerX - half,
                centerY - half,
                centerX + half,
                centerY - half,
                centerX + half,
                centerY + half,
                centerX - half,
                centerY + half,
                centerX - half,
                centerY - half);
    }
}
