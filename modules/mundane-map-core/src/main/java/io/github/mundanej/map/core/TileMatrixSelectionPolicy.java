package io.github.mundanej.map.core;

/** Deterministic relationship used when selecting a matrix for a scale denominator. */
public enum TileMatrixSelectionPolicy {
    /** Select the logarithmically nearest scale, choosing the finer scale on an exact tie. */
    NEAREST,
    /** Select the closest scale denominator greater than or equal to the request. */
    COARSER_OR_EQUAL,
    /** Select the closest scale denominator less than or equal to the request. */
    FINER_OR_EQUAL
}
