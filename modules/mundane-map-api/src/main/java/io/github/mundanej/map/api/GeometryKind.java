package io.github.mundanej.map.api;

/** Standards-neutral geometry value families. */
public enum GeometryKind {
    /** A point. */
    POINT,
    /** A line string. */
    LINE_STRING,
    /** A polygon. */
    POLYGON,
    /** A homogeneous point collection. */
    MULTI_POINT,
    /** A homogeneous line-string collection. */
    MULTI_LINE_STRING,
    /** A homogeneous polygon collection. */
    MULTI_POLYGON,
    /** A heterogeneous, possibly nested collection. */
    GEOMETRY_COLLECTION
}
