package io.github.mundanej.map.core;

/** Relationship between a requested world region and TileMatrixSet bounds. */
public enum TileCoverageStatus {
    /** No part of the request intersects the set. */
    OUTSIDE,
    /** The request is completely represented. */
    COMPLETE,
    /** Only the intersection with set and matrix bounds is represented. */
    CLIPPED
}
