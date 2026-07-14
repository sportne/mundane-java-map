package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import org.junit.jupiter.api.Test;

class MapViewportTest {
    @Test
    void visibleEnvelopeIsFiniteAndOverflowingViewportsFailAtConstruction() {
        MapViewport viewport = new MapViewport(200, 100, 10.0, 20.0, 2.0);

        assertEquals(new Envelope(-190.0, -80.0, 210.0, 120.0), viewport.visibleWorldEnvelope());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapViewport(2, 2, Double.MAX_VALUE, 0.0, Double.MAX_VALUE));
        assertThrows(
                IllegalArgumentException.class,
                () -> viewport.screenToWorld(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals(viewport, viewport.panByPixels(Double.MAX_VALUE, 0.0)));
        assertThrows(
                IllegalArgumentException.class, () -> new MapViewport(2, 2, 0.0, 0.0, 1.0e308));
        double roundedCenter = -1000.0 * Math.ulp(Double.MAX_VALUE / 2.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapViewport(2, 2, roundedCenter, roundedCenter, Double.MAX_VALUE / 2.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MapViewport.fit(
                                100, 100, new Envelope(0.0, 0.0, 1.0, 1.0), Double.MAX_VALUE));
        assertThrows(
                IllegalArgumentException.class,
                () -> viewport.screenToWorld(Double.MAX_VALUE, 0.0));
        MapViewport largeCenter = new MapViewport(1, 1, 1.0e308, 0.0, 1.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> largeCenter.worldToScreen(new Coordinate(-Double.MAX_VALUE, 0.0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(viewport.zoomAt(100.0, 50.0, Double.MIN_VALUE)));
        MapViewport resizeSource = new MapViewport(1, 1, 0.0, 0.0, 1.0e300);
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(resizeSource.resized(Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }

    private static final double TOLERANCE = 1.0e-9;

    @Test
    void worldAndScreenTransformsRoundTrip() {
        MapViewport viewport = new MapViewport(800, 600, 100.0, 200.0, 2.5);
        Coordinate world = new Coordinate(250.0, -75.0);

        Coordinate screen = viewport.worldToScreen(world);
        Coordinate restored = viewport.screenToWorld(screen.x(), screen.y());

        assertEquals(world.x(), restored.x(), TOLERANCE);
        assertEquals(world.y(), restored.y(), TOLERANCE);
    }

    @Test
    void zoomKeepsTheWorldCoordinateUnderTheCursor() {
        MapViewport viewport = new MapViewport(800, 600, 100.0, 200.0, 25.0);
        Coordinate before = viewport.screenToWorld(175.0, 422.0);

        MapViewport zoomed = viewport.zoomAt(175.0, 422.0, 2.0);
        Coordinate after = zoomed.screenToWorld(175.0, 422.0);

        assertEquals(before.x(), after.x(), TOLERANCE);
        assertEquals(before.y(), after.y(), TOLERANCE);
        assertEquals(12.5, zoomed.worldUnitsPerPixel(), TOLERANCE);
    }

    @Test
    void fitContainsEnvelopeCornersInsidePadding() {
        MapViewport viewport =
                MapViewport.fit(500, 300, new Envelope(0.0, 0.0, 1000.0, 200.0), 25.0);

        Coordinate minimum = viewport.worldToScreen(new Coordinate(0.0, 0.0));
        Coordinate maximum = viewport.worldToScreen(new Coordinate(1000.0, 200.0));

        assertEquals(25.0, minimum.x(), TOLERANCE);
        assertEquals(475.0, maximum.x(), TOLERANCE);
    }

    @Test
    void panAndResizePreserveTheDocumentedState() {
        MapViewport viewport = new MapViewport(800, 600, 100.0, 200.0, 2.5);

        MapViewport panned = viewport.panByPixels(20.0, -12.0);
        MapViewport resized = panned.resized(320, 240);

        assertEquals(50.0, panned.centerX(), TOLERANCE);
        assertEquals(170.0, panned.centerY(), TOLERANCE);
        assertEquals(panned.centerX(), resized.centerX(), TOLERANCE);
        assertEquals(panned.centerY(), resized.centerY(), TOLERANCE);
        assertEquals(panned.worldUnitsPerPixel(), resized.worldUnitsPerPixel(), TOLERANCE);
        assertEquals(320, resized.width());
        assertEquals(240, resized.height());
    }

    @Test
    void fitHandlesPointAndDegenerateScreenSpace() {
        Envelope point = new Envelope(12.0, -4.0, 12.0, -4.0);

        MapViewport fitted = MapViewport.fit(1, 1, point, 500.0);

        assertEquals(12.0, fitted.centerX(), TOLERANCE);
        assertEquals(-4.0, fitted.centerY(), TOLERANCE);
        assertEquals(1.0e-9, fitted.worldUnitsPerPixel(), 1.0e-18);
        assertThrows(
                IllegalArgumentException.class, () -> MapViewport.fit(100, 100, point, Double.NaN));
    }
}
