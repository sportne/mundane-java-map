package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.DistanceStrategy;
import java.util.Map;
import java.util.Objects;

/** Explicit JDK-only Level 1 distance strategies. */
public final class DistanceStrategies {
    /** Mean spherical Earth radius used by the EPSG:4326 great-circle strategy. */
    public static final double GREAT_CIRCLE_RADIUS_METRES = 6_371_008.8;

    private DistanceStrategies() {}

    /**
     * Returns Euclidean projected-coordinate distance for exact metre axes.
     *
     * <p>For EPSG:3857 this is projected map distance and does not correct Web Mercator scale
     * distortion.
     *
     * @param projectedCrs projected easting/northing CRS whose axes use metres
     * @return immutable Euclidean distance strategy for the exact CRS
     * @throws CrsException when the definition does not have the required projected-metre profile
     */
    public static DistanceStrategy planarMetres(CrsDefinition projectedCrs) {
        CrsDefinition crs = Objects.requireNonNull(projectedCrs, "projectedCrs");
        if (crs.kind() != CrsKind.PROJECTED
                || crs.xAxis().meaning() != CrsAxisMeaning.EASTING
                || crs.xAxis().unit() != CrsUnit.METRE
                || crs.yAxis().meaning() != CrsAxisMeaning.NORTHING
                || crs.yAxis().unit() != CrsUnit.METRE) {
            throw profileMismatch(crs);
        }
        return new PlanarMetres(crs);
    }

    /**
     * Returns the spherical great-circle strategy for the exact canonical EPSG:4326 definition.
     *
     * <p>The strategy uses a radius of exactly {@value #GREAT_CIRCLE_RADIUS_METRES} metres,
     * normalizes longitude differences across the antimeridian, and reports out-of-domain inputs
     * with {@code CRS_COORDINATE_OUT_OF_DOMAIN}.
     *
     * @param geographicCrs exact canonical EPSG:4326 definition
     * @return immutable spherical great-circle strategy
     * @throws CrsException when the definition is not the canonical Level 1 EPSG:4326 value
     */
    public static DistanceStrategy epsg4326GreatCircle(CrsDefinition geographicCrs) {
        CrsDefinition crs = Objects.requireNonNull(geographicCrs, "geographicCrs");
        if (!CrsDefinitions.EPSG_4326.equals(crs)) {
            throw mismatch(
                    "Canonical EPSG:4326 definition required", CrsDefinitions.EPSG_4326, crs);
        }
        return new GreatCircle(crs);
    }

    /**
     * Verifies exact strategy/view CRS identity.
     *
     * @param strategy strategy whose coordinate CRS is required
     * @param actualCrs actual CRS of coordinates supplied to that strategy
     * @throws CrsException when the definitions are not equal
     */
    public static void requireCoordinateCrs(DistanceStrategy strategy, CrsDefinition actualCrs) {
        Objects.requireNonNull(strategy, "strategy");
        CrsDefinition actual = Objects.requireNonNull(actualCrs, "actualCrs");
        if (!strategy.coordinateCrs().equals(actual)) {
            throw mismatch(
                    "Distance strategy does not match the view map CRS",
                    strategy.coordinateCrs(),
                    actual);
        }
    }

    private static void requireDomain(CrsDefinition crs, Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        if (!crs.coordinateDomain().contains(coordinate)) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_COORDINATE_OUT_OF_DOMAIN",
                            "Coordinate lies outside the distance strategy CRS domain",
                            Map.of("crs", crs.canonicalIdentifier())));
        }
    }

    private static DistanceResult result(CrsDefinition crs, double metres) {
        if (!Double.isFinite(metres)) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_TRANSFORM_NON_FINITE",
                            "Distance calculation produced a non-finite result",
                            Map.of("crs", crs.canonicalIdentifier())));
        }
        return new DistanceResult(metres);
    }

    private static CrsException mismatch(
            String message, CrsDefinition expected, CrsDefinition actual) {
        return new CrsException(
                new CrsProblem(
                        "CRS_DEFINITION_MISMATCH",
                        message,
                        Map.of(
                                "expectedCrs",
                                expected.canonicalIdentifier(),
                                "actualCrs",
                                actual.canonicalIdentifier())));
    }

    private static CrsException profileMismatch(CrsDefinition actual) {
        String actualAxes =
                actual.kind()
                        + ":"
                        + actual.xAxis().meaning()
                        + "/"
                        + actual.xAxis().unit()
                        + ","
                        + actual.yAxis().meaning()
                        + "/"
                        + actual.yAxis().unit();
        return new CrsException(
                new CrsProblem(
                        "CRS_DEFINITION_MISMATCH",
                        "Projected easting/northing metre CRS required",
                        Map.of(
                                "requiredProfile",
                                "PROJECTED_EASTING_NORTHING_METRE",
                                "actualCrs",
                                actual.canonicalIdentifier(),
                                "actualAxes",
                                actualAxes)));
    }

    private record PlanarMetres(CrsDefinition coordinateCrs) implements DistanceStrategy {
        @Override
        public DistanceResult distance(Coordinate start, Coordinate end) {
            requireDomain(coordinateCrs, start);
            requireDomain(coordinateCrs, end);
            return result(
                    coordinateCrs, StrictMath.hypot(end.x() - start.x(), end.y() - start.y()));
        }
    }

    private record GreatCircle(CrsDefinition coordinateCrs) implements DistanceStrategy {
        @Override
        public DistanceResult distance(Coordinate start, Coordinate end) {
            requireDomain(coordinateCrs, start);
            requireDomain(coordinateCrs, end);
            double startLatitude = StrictMath.toRadians(start.y());
            double endLatitude = StrictMath.toRadians(end.y());
            double deltaLatitude = endLatitude - startLatitude;
            double rawLongitude = StrictMath.toRadians(end.x() - start.x());
            double deltaLongitude =
                    StrictMath.atan2(StrictMath.sin(rawLongitude), StrictMath.cos(rawLongitude));
            double sinLatitude = StrictMath.sin(deltaLatitude / 2.0);
            double sinLongitude = StrictMath.sin(deltaLongitude / 2.0);
            double haversine =
                    sinLatitude * sinLatitude
                            + StrictMath.cos(startLatitude)
                                    * StrictMath.cos(endLatitude)
                                    * sinLongitude
                                    * sinLongitude;
            double bounded = StrictMath.max(0.0, StrictMath.min(1.0, haversine));
            double angle =
                    2.0
                            * StrictMath.atan2(
                                    StrictMath.sqrt(bounded), StrictMath.sqrt(1.0 - bounded));
            return result(coordinateCrs, GREAT_CIRCLE_RADIUS_METRES * angle);
        }
    }
}
