package io.github.mundanej.map.api;

/** Canonical Level 1 attribute types. */
public enum AttributeType {
    /** Unicode text. */
    TEXT,
    /** Boolean logical value. */
    LOGICAL,
    /** Integral numeric value. */
    INTEGER,
    /** Binary floating-point numeric value. */
    FLOATING,
    /** Decimal numeric value with explicit scale. */
    DECIMAL,
    /** Calendar date without a time or time zone. */
    DATE,
    /** Opaque immutable bytes. */
    BINARY
}
