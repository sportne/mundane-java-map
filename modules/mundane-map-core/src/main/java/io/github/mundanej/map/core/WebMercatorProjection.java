package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Projection;
import java.util.Map;
import java.util.Objects;

/**
 * Strict spherical Web Mercator projection from EPSG:4326 longitude/latitude degrees to EPSG:3857
 * easting/northing metres.
 *
 * <p>Longitude is limited to the closed range [-180, 180]. Latitude is limited to the closed Web
 * Mercator range [-85.0511287798066, 85.0511287798066], so the geographic poles are rejected. The
 * projection never wraps longitude or clamps latitude. Inverse input is limited to the conventional
 * closed projected square whose ordinate magnitude is {@link #WORLD_LIMIT}.
 *
 * <p>Every accepted result is finite and inside its declared target domain. Out-of-domain input or
 * an invalid numeric result throws a {@link CrsException} with stable bounded context.
 */
public final class WebMercatorProjection implements Projection {
    /** Maximum latitude representable by Web Mercator. */
    public static final double MAX_LATITUDE = 85.0511287798066;

    /** Conventional projected-world ordinate limit. */
    public static final double WORLD_LIMIT = Math.PI * 6_378_137.0;

    private static final double EARTH_RADIUS_METERS = 6_378_137.0;
    private static final Envelope SOURCE_DOMAIN =
            new Envelope(-180.0, -MAX_LATITUDE, 180.0, MAX_LATITUDE);

    /** Creates a strict stateless Web Mercator projection. */
    public WebMercatorProjection() {}

    @Override
    public CrsDefinition sourceCrs() {
        return CrsDefinitions.EPSG_4326;
    }

    @Override
    public CrsDefinition targetCrs() {
        return CrsDefinitions.EPSG_3857;
    }

    @Override
    public Envelope sourceDomain() {
        return SOURCE_DOMAIN;
    }

    @Override
    public Envelope targetDomain() {
        return CrsDefinitions.EPSG_3857.coordinateDomain();
    }

    @Override
    public Coordinate project(Coordinate source) {
        Objects.requireNonNull(source, "source");
        requireCoordinate(SOURCE_DOMAIN, source, "forward", "longitude", "latitude");
        double x;
        if (Double.compare(source.x(), -180.0) == 0) {
            x = -WORLD_LIMIT;
        } else if (Double.compare(source.x(), 180.0) == 0) {
            x = WORLD_LIMIT;
        } else {
            x = Math.toRadians(source.x()) * EARTH_RADIUS_METERS;
        }
        double y;
        if (Double.compare(source.y(), -MAX_LATITUDE) == 0) {
            y = -WORLD_LIMIT;
        } else if (Double.compare(source.y(), MAX_LATITUDE) == 0) {
            y = WORLD_LIMIT;
        } else {
            y =
                    EARTH_RADIUS_METERS
                            * Math.log(Math.tan(Math.PI / 4.0 + Math.toRadians(source.y()) / 2.0));
        }
        return checkedResult(x, y, targetDomain(), "forward", "easting", "northing");
    }

    @Override
    public Coordinate unproject(Coordinate projected) {
        Objects.requireNonNull(projected, "projected");
        requireCoordinate(targetDomain(), projected, "inverse", "easting", "northing");
        double longitude;
        if (Double.compare(projected.x(), -WORLD_LIMIT) == 0) {
            longitude = -180.0;
        } else if (Double.compare(projected.x(), WORLD_LIMIT) == 0) {
            longitude = 180.0;
        } else {
            longitude = Math.toDegrees(projected.x() / EARTH_RADIUS_METERS);
        }
        double latitude;
        if (Double.compare(projected.y(), -WORLD_LIMIT) == 0) {
            latitude = -MAX_LATITUDE;
        } else if (Double.compare(projected.y(), WORLD_LIMIT) == 0) {
            latitude = MAX_LATITUDE;
        } else {
            latitude = Math.toDegrees(Math.atan(Math.sinh(projected.y() / EARTH_RADIUS_METERS)));
        }
        return checkedResult(
                longitude, latitude, SOURCE_DOMAIN, "inverse", "longitude", "latitude");
    }

    @Override
    public Envelope projectEnvelope(Envelope source) {
        requireEnvelope(
                SOURCE_DOMAIN,
                Objects.requireNonNull(source, "source"),
                "forward",
                "longitude",
                "latitude");
        Coordinate minimum = project(new Coordinate(source.minX(), source.minY()));
        Coordinate maximum = project(new Coordinate(source.maxX(), source.maxY()));
        return new Envelope(minimum.x(), minimum.y(), maximum.x(), maximum.y());
    }

    @Override
    public Envelope unprojectEnvelope(Envelope target) {
        requireEnvelope(
                targetDomain(),
                Objects.requireNonNull(target, "target"),
                "inverse",
                "easting",
                "northing");
        Coordinate minimum = unproject(new Coordinate(target.minX(), target.minY()));
        Coordinate maximum = unproject(new Coordinate(target.maxX(), target.maxY()));
        return new Envelope(minimum.x(), minimum.y(), maximum.x(), maximum.y());
    }

    private static Coordinate checkedResult(
            double x, double y, Envelope domain, String operation, String xAxis, String yAxis) {
        double canonicalX = x == 0.0 ? 0.0 : x;
        double canonicalY = y == 0.0 ? 0.0 : y;
        requireResultOrdinate(canonicalX, domain.minX(), domain.maxX(), operation, xAxis);
        requireResultOrdinate(canonicalY, domain.minY(), domain.maxY(), operation, yAxis);
        return new Coordinate(canonicalX, canonicalY);
    }

    private static void requireCoordinate(
            Envelope domain, Coordinate coordinate, String operation, String xAxis, String yAxis) {
        requireOrdinate(
                coordinate.x(),
                domain.minX(),
                domain.maxX(),
                operation,
                xAxis,
                "CRS_COORDINATE_OUT_OF_DOMAIN",
                "Coordinate is outside the CRS operation domain");
        requireOrdinate(
                coordinate.y(),
                domain.minY(),
                domain.maxY(),
                operation,
                yAxis,
                "CRS_COORDINATE_OUT_OF_DOMAIN",
                "Coordinate is outside the CRS operation domain");
    }

    private static void requireEnvelope(
            Envelope domain, Envelope envelope, String operation, String xAxis, String yAxis) {
        requireOrdinate(
                envelope.minX(),
                domain.minX(),
                domain.maxX(),
                operation,
                xAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN",
                "Envelope is outside the CRS operation domain");
        requireOrdinate(
                envelope.maxX(),
                domain.minX(),
                domain.maxX(),
                operation,
                xAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN",
                "Envelope is outside the CRS operation domain");
        requireOrdinate(
                envelope.minY(),
                domain.minY(),
                domain.maxY(),
                operation,
                yAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN",
                "Envelope is outside the CRS operation domain");
        requireOrdinate(
                envelope.maxY(),
                domain.minY(),
                domain.maxY(),
                operation,
                yAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN",
                "Envelope is outside the CRS operation domain");
    }

    private static void requireOrdinate(
            double value,
            double minimum,
            double maximum,
            String operation,
            String axis,
            String code,
            String message) {
        if (value < minimum || value > maximum) {
            throw new CrsException(
                    new CrsProblem(
                            code,
                            message,
                            Map.of(
                                    "axis",
                                    axis,
                                    "maximum",
                                    Double.toString(maximum),
                                    "minimum",
                                    Double.toString(minimum),
                                    "operation",
                                    operation,
                                    "value",
                                    Double.toString(value))));
        }
    }

    private static void requireResultOrdinate(
            double value, double minimum, double maximum, String operation, String axis) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_TRANSFORM_NON_FINITE",
                            "CRS transformation produced an invalid result",
                            Map.of(
                                    "axis",
                                    axis,
                                    "maximum",
                                    Double.toString(maximum),
                                    "minimum",
                                    Double.toString(minimum),
                                    "operation",
                                    operation,
                                    "value",
                                    Double.toString(value))));
        }
    }
}
