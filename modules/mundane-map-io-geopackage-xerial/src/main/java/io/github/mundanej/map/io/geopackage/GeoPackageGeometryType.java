package io.github.mundanej.map.io.geopackage;

/** Canonical two-dimensional geometry declarations in the approved GeoPackage profile. */
public enum GeoPackageGeometryType {
    /** Row-level geometry type is validated individually. */
    GEOMETRY,
    /** Singular point. */
    POINT,
    /** Ordered collection of points. */
    MULTI_POINT,
    /** Singular line string. */
    LINE_STRING,
    /** Ordered collection of line strings. */
    MULTI_LINE_STRING,
    /** Singular polygon. */
    POLYGON,
    /** Ordered collection of polygons. */
    MULTI_POLYGON
}
