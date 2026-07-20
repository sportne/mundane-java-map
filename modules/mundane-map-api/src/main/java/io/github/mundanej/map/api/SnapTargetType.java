package io.github.mundanej.map.api;

/** Closed type of an explicit geometry snap target. */
public enum SnapTargetType {
    /** An exact stored geometry vertex. */
    VERTEX,

    /** The closest point on a non-zero straight segment. */
    SEGMENT
}
