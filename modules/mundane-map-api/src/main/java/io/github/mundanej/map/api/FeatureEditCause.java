package io.github.mundanej.map.api;

/** Closed cause of a published edit event. */
public enum FeatureEditCause {
    /** An ordinary transaction was applied. */
    COMMIT,

    /** A retained transaction was reversed. */
    UNDO,

    /** A previously undone transaction was reapplied. */
    REDO
}
