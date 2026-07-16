package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * A finite positive length expressed in one symbol unit.
 *
 * @param value finite positive magnitude
 * @param unit screen-pixel or map-unit interpretation
 */
public record SymbolLength(double value, SymbolUnit unit) {
    /** Creates and validates a symbol length. */
    public SymbolLength {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("value must be finite and positive");
        }
        Objects.requireNonNull(unit, "unit");
    }
}
