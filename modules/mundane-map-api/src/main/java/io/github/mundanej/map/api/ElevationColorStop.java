package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * One finite elevation threshold and its immutable display color.
 *
 * @param elevation finite threshold in the owning ramp's unit
 * @param color immutable unpremultiplied display color
 */
public record ElevationColorStop(double elevation, Rgba color) {
    /** Validates the finite threshold and non-null color. */
    public ElevationColorStop {
        if (!Double.isFinite(elevation)) {
            throw new IllegalArgumentException("elevation must be finite");
        }
        if (elevation == 0.0) {
            elevation = 0.0;
        }
        Objects.requireNonNull(color, "color");
    }
}
