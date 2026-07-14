package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** A pointer event expressed in both screen and source-map coordinate systems. */
public record MapPointerEvent(
        Type type, double screenX, double screenY, Optional<Coordinate> mapCoordinate) {
    /** Supported event types in the initial interaction slice. */
    public enum Type {
        /** Pointer movement without a button action. */
        MOVED,
        /** Pointer click. */
        CLICKED
    }

    /** Creates a map pointer event. */
    public MapPointerEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mapCoordinate, "mapCoordinate");
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("Screen coordinates must be finite");
        }
    }
}
