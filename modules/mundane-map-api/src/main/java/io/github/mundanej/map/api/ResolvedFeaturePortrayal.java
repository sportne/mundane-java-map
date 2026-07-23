package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable symbols selected for all three geometry roles by one evaluation.
 *
 * @param marker optional marker symbol
 * @param line optional line symbol
 * @param fill optional fill symbol
 */
public record ResolvedFeaturePortrayal(
        Optional<Symbol> marker, Optional<Symbol> line, Optional<Symbol> fill) {
    /** Empty result. */
    public static final ResolvedFeaturePortrayal EMPTY =
            new ResolvedFeaturePortrayal(Optional.empty(), Optional.empty(), Optional.empty());

    /** Validates role compatibility. */
    public ResolvedFeaturePortrayal {
        marker = role(marker, SymbolRole.MARKER, "marker");
        line = role(line, SymbolRole.LINE, "line");
        fill = role(fill, SymbolRole.FILL, "fill");
    }

    /**
     * Returns the optional result for one role.
     *
     * @param role geometry role
     * @return optional selected symbol
     */
    public Optional<Symbol> forRole(SymbolRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case MARKER -> marker;
            case LINE -> line;
            case FILL -> fill;
            case LEGACY_GEOMETRY -> Optional.empty();
        };
    }

    private static Optional<Symbol> role(Optional<Symbol> value, SymbolRole expected, String name) {
        Objects.requireNonNull(value, name);
        Optional<Symbol> copy = value.map(Objects::requireNonNull);
        if (copy.isPresent() && copy.orElseThrow().role() != expected) {
            throw new IllegalArgumentException(name + " symbol has the wrong role");
        }
        return copy;
    }
}
