package io.github.mundanej.map.api;

/** Converts source map coordinates to and from projected world coordinates. */
public interface Projection {
    /** Returns a stable human-readable projection identifier. */
    String id();

    /** Projects a source coordinate into world coordinates. */
    Coordinate project(Coordinate source);

    /** Converts projected world coordinates back to source coordinates. */
    Coordinate unproject(Coordinate projected);
}
