package io.github.mundanej.map.api;

/** Coordinate-reference-system kinds in the supported WKT2 metadata profile. */
public enum WktCrsKind {
    /** Geodetic coordinates on an ellipsoid. */
    GEOGRAPHIC,
    /** Planar coordinates produced by a named conversion. */
    PROJECTED,
    /** One-dimensional height or depth coordinates. */
    VERTICAL,
    /** Ordered horizontal and vertical component metadata. */
    COMPOUND
}
