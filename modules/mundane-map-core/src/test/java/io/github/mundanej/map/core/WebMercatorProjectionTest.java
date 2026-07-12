package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.Coordinate;
import org.junit.jupiter.api.Test;

class WebMercatorProjectionTest {
    @Test
    void roundTripsOrdinaryLongitudeAndLatitude() {
        WebMercatorProjection projection = new WebMercatorProjection();
        Coordinate source = new Coordinate(-71.0589, 42.3601);

        Coordinate restored = projection.unproject(projection.project(source));

        assertEquals(source.x(), restored.x(), 1.0e-9);
        assertEquals(source.y(), restored.y(), 1.0e-9);
    }

    @Test
    void clampsLatitudeToTheProjectionDomain() {
        WebMercatorProjection projection = new WebMercatorProjection();

        Coordinate restored = projection.unproject(projection.project(new Coordinate(0.0, 90.0)));

        assertEquals(WebMercatorProjection.MAX_LATITUDE, restored.y(), 1.0e-9);
    }
}
