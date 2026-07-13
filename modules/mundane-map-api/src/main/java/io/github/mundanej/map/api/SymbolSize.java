package io.github.mundanej.map.api;

import java.util.Objects;

/** A finite positive symbol width and height expressed in one unit. */
public record SymbolSize(double width, double height, SymbolUnit unit) {
    /** Creates and validates a symbol size. */
    public SymbolSize {
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("width must be finite and positive");
        }
        if (!Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("height must be finite and positive");
        }
        Objects.requireNonNull(unit, "unit");
    }

    /** Creates a square size in the supplied unit. */
    public static SymbolSize square(double value, SymbolUnit unit) {
        return new SymbolSize(value, value, unit);
    }
}
