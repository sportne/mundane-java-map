package io.github.mundanej.map.api;

import java.util.Objects;

/** A round-cap, round-join symbol stroke. */
public record SymbolStroke(Rgba color, SymbolLength width) {
    /** Creates a symbol stroke. */
    public SymbolStroke {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(width, "width");
    }
}
