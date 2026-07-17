package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable finite elevation in its source's declared vertical unit.
 *
 * @param value finite elevation value
 * @param unit exact declared source unit
 */
public record ElevationValue(double value, ElevationUnit unit) {
    /** Validates the value and canonicalizes signed zero. */
    public ElevationValue {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        if (value == 0.0) {
            value = 0.0;
        }
        Objects.requireNonNull(unit, "unit");
    }
}
