package io.github.mundanej.map.api;

/** Lifecycle phase of one immutable measurement snapshot. */
public enum MeasurementPhase {
    /** No committed vertices. */
    EMPTY,
    /** One or more vertices are being collected. */
    MEASURING,
    /** A path of at least two vertices is complete. */
    COMPLETE
}
