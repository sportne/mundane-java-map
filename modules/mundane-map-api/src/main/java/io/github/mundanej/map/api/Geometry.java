package io.github.mundanej.map.api;

/** A two-dimensional geometry with a finite envelope. */
public interface Geometry {
    /** Returns the geometry envelope in source-coordinate units. */
    Envelope envelope();
}

