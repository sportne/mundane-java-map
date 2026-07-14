package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.Envelope;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebMercatorProjectionTest {
    @Test
    void roundTripsOrdinaryLongitudeAndLatitude() {
        WebMercatorProjection projection = new WebMercatorProjection();
        Coordinate source = new Coordinate(-71.0589, 42.3601);

        Coordinate restored = projection.unproject(projection.project(source));

        assertEquals(source.x(), restored.x(), 1.0e-9);
        assertEquals(source.y(), restored.y(), 1.0e-9);

        Coordinate projected = new Coordinate(-7_910_000.25, 5_220_000.75);
        Coordinate projectedRestored = projection.project(projection.unproject(projected));
        assertEquals(projected.x(), projectedRestored.x(), 1.0e-6);
        assertEquals(projected.y(), projectedRestored.y(), 1.0e-6);
    }

    @Test
    void rejectsLatitudeOutsideTheProjectionDomain() {
        WebMercatorProjection projection = new WebMercatorProjection();

        CrsException failure =
                assertThrows(
                        CrsException.class, () -> projection.project(new Coordinate(0.0, 90.0)));

        assertEquals("CRS_COORDINATE_OUT_OF_DOMAIN", failure.problem().code());
        assertEquals(
                Map.of(
                        "axis",
                        "latitude",
                        "maximum",
                        Double.toString(WebMercatorProjection.MAX_LATITUDE),
                        "minimum",
                        Double.toString(-WebMercatorProjection.MAX_LATITUDE),
                        "operation",
                        "forward",
                        "value",
                        "90.0"),
                failure.problem().context());
    }

    @Test
    void exactEdgesRoundTripAndAdjacentValuesFail() {
        WebMercatorProjection projection = new WebMercatorProjection();
        Coordinate minimum =
                projection.project(new Coordinate(-180.0, -WebMercatorProjection.MAX_LATITUDE));
        Coordinate maximum =
                projection.project(new Coordinate(180.0, WebMercatorProjection.MAX_LATITUDE));

        assertEquals(-WebMercatorProjection.WORLD_LIMIT, minimum.x());
        assertEquals(-WebMercatorProjection.WORLD_LIMIT, minimum.y());
        assertEquals(WebMercatorProjection.WORLD_LIMIT, maximum.x());
        assertEquals(WebMercatorProjection.WORLD_LIMIT, maximum.y());
        assertEquals(
                new Coordinate(-180.0, -WebMercatorProjection.MAX_LATITUDE),
                projection.unproject(minimum));
        assertThrows(
                CrsException.class,
                () -> projection.project(new Coordinate(Math.nextUp(180.0), 0.0)));
        assertThrows(
                CrsException.class,
                () -> projection.project(new Coordinate(Math.nextDown(-180.0), 0.0)));
        assertThrows(
                CrsException.class,
                () ->
                        projection.project(
                                new Coordinate(
                                        0.0, Math.nextUp(WebMercatorProjection.MAX_LATITUDE))));
        assertThrows(
                CrsException.class,
                () ->
                        projection.project(
                                new Coordinate(
                                        0.0, Math.nextDown(-WebMercatorProjection.MAX_LATITUDE))));
        assertThrows(CrsException.class, () -> projection.project(new Coordinate(0.0, -90.0)));
        assertThrows(
                CrsException.class,
                () ->
                        projection.unproject(
                                new Coordinate(
                                        0.0, Math.nextUp(WebMercatorProjection.WORLD_LIMIT))));
        assertThrows(
                CrsException.class,
                () ->
                        projection.unproject(
                                new Coordinate(
                                        Math.nextDown(-WebMercatorProjection.WORLD_LIMIT), 0.0)));
        assertThrows(
                CrsException.class,
                () ->
                        projection.unproject(
                                new Coordinate(
                                        Math.nextUp(WebMercatorProjection.WORLD_LIMIT), 0.0)));
        assertThrows(
                CrsException.class,
                () ->
                        projection.unproject(
                                new Coordinate(
                                        0.0, Math.nextDown(-WebMercatorProjection.WORLD_LIMIT))));
        assertThrows(
                CrsException.class,
                () -> projection.unproject(new Coordinate(Double.MAX_VALUE, 0.0)));
    }

    @Test
    void strictEnvelopeProjectionPreservesDegenerateAxes() {
        WebMercatorProjection projection = new WebMercatorProjection();
        Coordinate projected = projection.project(new Coordinate(12.0, 34.0));
        assertEquals(
                new Envelope(projected.x(), projected.y(), projected.x(), projected.y()),
                projection.projectEnvelope(new Envelope(12.0, 34.0, 12.0, 34.0)));
        assertTrue(
                Double.doubleToRawLongBits(projection.project(new Coordinate(0.0, 0.0)).x()) >= 0);
        CrsException envelopeFailure =
                assertThrows(
                        CrsException.class,
                        () -> projection.projectEnvelope(new Envelope(-1.0, 80.0, 1.0, 90.0)));
        assertEquals("latitude", envelopeFailure.problem().context().get("axis"));
        assertEquals("90.0", envelopeFailure.problem().context().get("value"));
        assertEquals(
                projection.targetDomain(), projection.projectEnvelope(projection.sourceDomain()));
        Envelope longitudeLine = new Envelope(12.0, -10.0, 12.0, 10.0);
        assertEquals(
                projection.project(new Coordinate(12.0, -10.0)).x(),
                projection.projectEnvelope(longitudeLine).minX());
    }
}
