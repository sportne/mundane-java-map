package io.github.mundanej.map.api;

/** Named policies for consumers restricted to x/y coordinates. */
public enum OrdinateLossPolicy {
    /** Reject any conversion that would discard z or m. */
    REJECT,
    /** Deliberately retain x/y and discard every unsupported ordinate. */
    DROP_TO_XY
}
