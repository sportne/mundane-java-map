package io.github.mundanej.map.api;

/** Broad kind of a recognized coordinate reference system. */
public enum CrsKind {
    /** Angular coordinates on a geographic reference surface. */
    GEOGRAPHIC,
    /** Planar coordinates in a projected coordinate system. */
    PROJECTED,
    /** Metadata whose coordinate reference system is not recognized. */
    UNKNOWN
}
