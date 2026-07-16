package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * A round-cap, round-join symbol stroke.
 *
 * @param color immutable stroke color and opacity
 * @param width finite positive stroke width and unit
 */
public record SymbolStroke(Rgba color, SymbolLength width) {
    /** Creates a symbol stroke. */
    public SymbolStroke {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(width, "width");
    }
}
