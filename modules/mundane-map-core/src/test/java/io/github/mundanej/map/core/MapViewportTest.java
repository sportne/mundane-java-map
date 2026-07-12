package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import org.junit.jupiter.api.Test;

class MapViewportTest {
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
        MapViewport viewport = MapViewport.fit(500, 300, new Envelope(0.0, 0.0, 1000.0, 200.0), 25.0);

        Coordinate minimum = viewport.worldToScreen(new Coordinate(0.0, 0.0));
        Coordinate maximum = viewport.worldToScreen(new Coordinate(1000.0, 200.0));

        assertEquals(25.0, minimum.x(), TOLERANCE);
        assertEquals(475.0, maximum.x(), TOLERANCE);
    }
}

