package io.github.mundanej.map.api;

import java.util.Objects;

/** Immutable meaning and unit for one coordinate-reference axis. */
public record CrsAxis(CrsAxisMeaning meaning, CrsUnit unit) {
    /** Creates an axis. */
    public CrsAxis {
        Objects.requireNonNull(meaning, "meaning");
        Objects.requireNonNull(unit, "unit");
    }
}
