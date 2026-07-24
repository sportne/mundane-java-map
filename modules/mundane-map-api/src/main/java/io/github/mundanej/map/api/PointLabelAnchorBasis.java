package io.github.mundanej.map.api;

/** Screen anchor used to construct point-label candidates. */
public enum PointLabelAnchorBasis {
    /** Place candidates around the final marker bounds. */
    MARKER_BOUNDS,
    /** Place candidates relative to the projected feature point. */
    FEATURE_POINT
}
