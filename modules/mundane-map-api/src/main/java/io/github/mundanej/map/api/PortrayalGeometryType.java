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
     * @throws GeometryException when a heterogeneous collection has no singular category
     */
    public static PortrayalGeometryType fromGeometry(Geometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        return switch (geometry.kind()) {
            case POINT, MULTI_POINT -> POINT;
            case LINE_STRING, MULTI_LINE_STRING -> LINE_STRING;
            case POLYGON, MULTI_POLYGON -> POLYGON;
            case GEOMETRY_COLLECTION ->
                    throw new GeometryException(
                            GeometryException.KIND_UNSUPPORTED,
                            "A heterogeneous collection has no singular portrayal category",
                            java.util.Map.of(
                                    "consumer",
                                    "PortrayalGeometryType",
                                    "kind",
                                    geometry.kind().name()));
        };
    }
}
