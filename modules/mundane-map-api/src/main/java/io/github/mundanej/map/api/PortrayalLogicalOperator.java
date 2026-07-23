package io.github.mundanej.map.api;

/** Closed boolean composition operations for portrayal predicates. */
public enum PortrayalLogicalOperator {
    /** Every child must match. */
    AND,
    /** At least one child must match. */
    OR,
    /** The one child must not match. */
    NOT
}
