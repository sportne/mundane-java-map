package io.github.mundanej.map.awt;

/** Renderer-reported logical source-paint eligibility for interaction overlays. */
public enum AwtLogicalPaintPresence {
    /** The renderer produced no modeled source paint. */
    EMPTY,
    /** The renderer produced positive modeled source paint. */
    PRESENT,
    /** A source-compatible custom renderer did not declare presence. */
    UNKNOWN
}
