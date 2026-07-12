package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Projection;
import java.util.Objects;

/** Spherical Web Mercator projection for longitude/latitude source coordinates in degrees. */
public final class WebMercatorProjection implements Projection {
    /** Conventional Web Mercator identifier. */
    public static final String ID = "EPSG:3857";

    /** Maximum latitude representable by Web Mercator. */
    public static final double MAX_LATITUDE = 85.0511287798066;

    private static final double EARTH_RADIUS_METERS = 6_378_137.0;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Coordinate project(Coordinate source) {
        Objects.requireNonNull(source, "source");
        double latitude = Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, source.y()));
        double x = Math.toRadians(source.x()) * EARTH_RADIUS_METERS;
        double y =
                EARTH_RADIUS_METERS
                        * Math.log(Math.tan(Math.PI / 4.0 + Math.toRadians(latitude) / 2.0));
        return new Coordinate(x, y);
    }

    @Override
    public Coordinate unproject(Coordinate projected) {
        Objects.requireNonNull(projected, "projected");
        double longitude = Math.toDegrees(projected.x() / EARTH_RADIUS_METERS);
        double latitude =
                Math.toDegrees(2.0 * Math.atan(Math.exp(projected.y() / EARTH_RADIUS_METERS))
                        - Math.PI / 2.0);
        return new Coordinate(longitude, latitude);
    }
}

