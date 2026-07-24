package io.github.mundanej.map.api;

/** Closed input kind for linear symbol interpolation. */
public enum InterpolationInput {
    /** A canonical finite numeric feature attribute. */
    ATTRIBUTE,

    /** The explicit finite zoom level in the portrayal evaluation context. */
    ZOOM
}
