package io.github.mundanej.map.api;

import java.util.Objects;

/** Closed geometry category available to portrayal predicates. */
public enum PortrayalGeometryType {
    /** Singular or multi-point geometry. */
    POINT,
    /** Singular or multi-line-string geometry. */
    LINE_STRING,
    /** Singular or multi-polygon geometry. */
    POLYGON;

    /**
     * Normalizes one supported geometry to its singular portrayal category.
     *
     * @param geometry immutable supported geometry
     * @return normalized category
     * @throws IllegalArgumentException if an argument violates the documented constraints
     */
    public static PortrayalGeometryType fromGeometry(Geometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            return POINT;
        }
        if (geometry instanceof LineStringGeometry || geometry instanceof MultiLineStringGeometry) {
            return LINE_STRING;
        }
        if (geometry instanceof PolygonGeometry || geometry instanceof MultiPolygonGeometry) {
            return POLYGON;
        }
        throw new IllegalArgumentException("unsupported geometry");
    }
}
