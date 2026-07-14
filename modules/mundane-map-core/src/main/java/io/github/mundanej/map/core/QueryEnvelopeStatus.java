package io.github.mundanej.map.core;

/** Result state of a domain-aware query-envelope transformation. */
public enum QueryEnvelopeStatus {
    /** The complete input envelope was transformed. */
    COMPLETE,
    /** The input was clipped to the operation domain before transformation. */
    CLIPPED,
    /** The input did not intersect the operation domain. */
    OUTSIDE
}
