package io.github.mundanej.map.api;

/** Closed outcome of an attempted edit transaction. */
public enum FeatureEditStatus {
    /** State changed and one new revision was published. */
    APPLIED,
    /** The requested replacements equal current records and no event was published. */
    UNCHANGED,
    /** The transaction was rejected without changing session state. */
    REJECTED
}
