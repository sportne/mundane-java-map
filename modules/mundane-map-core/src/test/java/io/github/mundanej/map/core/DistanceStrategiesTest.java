package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.DistanceStrategy;
import io.github.mundanej.map.api.Envelope;
import org.junit.jupiter.api.Test;

class DistanceStrategiesTest {
    private static final double EARTH = DistanceStrategies.GREAT_CIRCLE_RADIUS_METRES;

    @Test
    void planarMetresUsesCheckedEuclideanCoordinates() {
        DistanceStrategy strategy = DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857);

        assertEquals(5, strategy.distance(new Coordinate(0, 0), new Coordinate(3, 4)).metres());
        CrsException outside =
                assertThrows(
                        CrsException.class,
                        () ->
                                strategy.distance(
                                        new Coordinate(0, 0), new Coordinate(Double.MAX_VALUE, 0)));
        assertEquals("CRS_COORDINATE_OUT_OF_DOMAIN", outside.problem().code());
    }

    @Test
    void greatCircleHandlesQuarterEquatorAntimeridianPolesAndAntipodes() {
        DistanceStrategy strategy =
                DistanceStrategies.epsg4326GreatCircle(CrsDefinitions.EPSG_4326);

        assertNear(EARTH * StrictMath.PI / 2, distance(strategy, 0, 0, 90, 0));
        assertNear(EARTH * StrictMath.toRadians(2), distance(strategy, 179, 0, -179, 0));
        assertNear(EARTH * StrictMath.PI / 2, distance(strategy, 0, 90, 120, 0));
        assertNear(EARTH * StrictMath.PI, distance(strategy, 0, 0, 180, 0));
        double nearAntipodal = distance(strategy, 0, 0, 179.999999, 0.000001);
        assertTrueFiniteNearAntipode(nearAntipodal);
        assertEquals(0, distance(strategy, 12, -30, 12, -30));
    }

    @Test
    void geographicFactoryAndStrategyViewBindingRequireExactDefinitions() {
        CrsDefinition fabricated =
                new CrsDefinition(
                        "EPSG:4326-copy",
                        CrsKind.GEOGRAPHIC,
                        new CrsAxis(CrsAxisMeaning.LONGITUDE, CrsUnit.DEGREE),
                        new CrsAxis(CrsAxisMeaning.LATITUDE, CrsUnit.DEGREE),
                        new Envelope(-180, -90, 180, 90));
        CrsException factory =
                assertThrows(
                        CrsException.class,
                        () -> DistanceStrategies.epsg4326GreatCircle(fabricated));
        assertEquals("CRS_DEFINITION_MISMATCH", factory.problem().code());

        CrsException view =
                assertThrows(
                        CrsException.class,
                        () ->
                                DistanceStrategies.requireCoordinateCrs(
                                        DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857),
                                        CrsDefinitions.EPSG_4326));
        assertEquals("EPSG:3857", view.problem().context().get("expectedCrs"));
        assertEquals("EPSG:4326", view.problem().context().get("actualCrs"));
    }

    @Test
    void planarProfileFailureHasMeaningfulStructuredContext() {
        CrsException failure =
                assertThrows(
                        CrsException.class,
                        () -> DistanceStrategies.planarMetres(CrsDefinitions.EPSG_4326));

        assertEquals("CRS_DEFINITION_MISMATCH", failure.problem().code());
        assertEquals(
                "PROJECTED_EASTING_NORTHING_METRE",
                failure.problem().context().get("requiredProfile"));
        assertEquals("EPSG:4326", failure.problem().context().get("actualCrs"));
        assertEquals(
                "GEOGRAPHIC:LONGITUDE/DEGREE,LATITUDE/DEGREE",
                failure.problem().context().get("actualAxes"));
    }

    private static double distance(
            DistanceStrategy strategy, double startX, double startY, double endX, double endY) {
        return strategy.distance(new Coordinate(startX, startY), new Coordinate(endX, endY))
                .metres();
    }

    private static void assertNear(double expected, double actual) {
        assertEquals(expected, actual, StrictMath.max(1e-6, StrictMath.abs(expected) * 1e-12));
    }

    private static void assertTrueFiniteNearAntipode(double actual) {
        if (!Double.isFinite(actual)
                || actual <= EARTH * StrictMath.toRadians(179.99)
                || actual > EARTH * StrictMath.PI) {
            throw new AssertionError("unexpected near-antipodal distance: " + actual);
        }
    }
}
