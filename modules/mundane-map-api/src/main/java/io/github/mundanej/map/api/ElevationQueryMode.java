package io.github.mundanej.map.api;

/** Explicit interpolation policy for a source-coordinate elevation query. */
public enum ElevationQueryMode {
    /** Return the nearest sample, resolving exact ties to the lower numeric index. */
    NEAREST,

    /** Interpolate the positive-weight surrounding samples without renormalizing no-data. */
    BILINEAR
}
