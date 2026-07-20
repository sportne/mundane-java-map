package io.github.mundanej.map.api;

/** Closed outcome of a bounded snap query. */
public enum SnapQueryStatus {
    /** One target won within the inclusive tolerance. */
    SNAPPED,

    /** The bounded query completed without a candidate in tolerance. */
    UNSNAPPED,

    /** The query stopped with a stable edit problem. */
    REJECTED
}
