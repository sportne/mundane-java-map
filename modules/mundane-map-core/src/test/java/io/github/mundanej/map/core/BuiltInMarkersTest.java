package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.api.VectorPathCommand;
import org.junit.jupiter.api.Test;

class BuiltInMarkersTest {
    private static final double TOLERANCE = 1.0e-14;

    @Test
    void everyBuiltInIsClosedContainedNormalizedAndReusable() {
        Envelope viewBox = new Envelope(-0.5, -0.5, 0.5, 0.5);
        assertEquals(viewBox, BuiltInMarkers.viewBox());

        for (BuiltInMarker marker : BuiltInMarker.values()) {
            VectorPath path = BuiltInMarkers.path(marker);

            assertSame(path, BuiltInMarkers.path(marker));
            assertEquals(VectorPathCommand.MOVE_TO, path.commandAt(0), marker::name);
            assertEquals(
                    VectorPathCommand.CLOSE, path.commandAt(path.commandCount() - 1), marker::name);
            assertTrue(viewBox.contains(path.coordinateEnvelope().center()), marker::name);
            assertTrue(path.coordinateEnvelope().minX() >= -0.5 - TOLERANCE, marker::name);
            assertTrue(path.coordinateEnvelope().minY() >= -0.5 - TOLERANCE, marker::name);
            assertTrue(path.coordinateEnvelope().maxX() <= 0.5 + TOLERANCE, marker::name);
            assertTrue(path.coordinateEnvelope().maxY() <= 0.5 + TOLERANCE, marker::name);
            assertEquals(
                    path.coordinateEnvelope().minY(), path.ordinateAt(1), TOLERANCE, marker::name);
        }
    }

    @Test
    void markerStructuresAndClockwisePolygonOrientationAreStable() {
        assertEquals(6, BuiltInMarkers.path(BuiltInMarker.CIRCLE).commandCount());
        assertEquals(5, BuiltInMarkers.path(BuiltInMarker.SQUARE).commandCount());
        assertEquals(4, BuiltInMarkers.path(BuiltInMarker.TRIANGLE).commandCount());
        assertEquals(5, BuiltInMarkers.path(BuiltInMarker.DIAMOND).commandCount());
        assertEquals(13, BuiltInMarkers.path(BuiltInMarker.CROSS).commandCount());
        assertEquals(13, BuiltInMarkers.path(BuiltInMarker.X).commandCount());
        assertEquals(11, BuiltInMarkers.path(BuiltInMarker.STAR).commandCount());
        assertEquals(8, BuiltInMarkers.path(BuiltInMarker.ARROW).commandCount());

        for (BuiltInMarker marker : BuiltInMarker.values()) {
            if (marker != BuiltInMarker.CIRCLE) {
                assertTrue(signedEndpointArea(BuiltInMarkers.path(marker)) > 0.0, marker::name);
            }
        }
    }

    @Test
    void circleUsesFourCubicQuartersAndReturnsToNorth() {
        VectorPath circle = BuiltInMarkers.path(BuiltInMarker.CIRCLE);

        assertEquals(VectorPathCommand.MOVE_TO, circle.commandAt(0));
        for (int index = 1; index <= 4; index++) {
            assertEquals(VectorPathCommand.CUBIC_TO, circle.commandAt(index));
        }
        assertEquals(0.0, circle.ordinateAt(circle.ordinateCount() - 2), TOLERANCE);
        assertEquals(-0.5, circle.ordinateAt(circle.ordinateCount() - 1), TOLERANCE);
    }

    @Test
    void filledFactoryUsesTheSharedPathAndViewBox() {
        VectorMarkerSymbol marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.STAR, Rgba.rgb(10, 20, 30), 18.0, 0.75);

        assertSame(BuiltInMarkers.path(BuiltInMarker.STAR), marker.path());
        assertEquals(BuiltInMarkers.viewBox(), marker.viewBox());
        assertEquals(Rgba.rgb(10, 20, 30), marker.fill());
        assertEquals(18.0, marker.screenSizePixels());
        assertEquals(0.75, marker.opacity());
        assertThrows(NullPointerException.class, () -> BuiltInMarkers.path(null));
    }

    private static double signedEndpointArea(VectorPath path) {
        double[] x = new double[path.commandCount() - 1];
        double[] y = new double[path.commandCount() - 1];
        int ordinate = 0;
        int vertex = 0;
        for (int command = 0; command < path.commandCount(); command++) {
            VectorPathCommand kind = path.commandAt(command);
            if (kind == VectorPathCommand.MOVE_TO || kind == VectorPathCommand.LINE_TO) {
                x[vertex] = path.ordinateAt(ordinate);
                y[vertex] = path.ordinateAt(ordinate + 1);
                vertex++;
            }
            ordinate += kind.arity();
        }
        double areaTwice = 0.0;
        for (int index = 0; index < vertex; index++) {
            int next = (index + 1) % vertex;
            areaTwice += x[index] * y[next] - x[next] * y[index];
        }
        return areaTwice / 2.0;
    }
}
