package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * A selector that always returns one immutable symbol.
 *
 * @param symbol fixed marker, line, or fill symbol
 */
public record FixedSymbolSelector(Symbol symbol) implements SymbolSelector {
    /** Validates the selected symbol. */
    public FixedSymbolSelector {
        Objects.requireNonNull(symbol, "symbol");
        requireVectorRole(symbol.role());
    }

    @Override
    public SymbolRole role() {
        return symbol.role();
    }

    static void requireVectorRole(SymbolRole role) {
        Objects.requireNonNull(role, "symbol.role");
        if (role == SymbolRole.LEGACY_GEOMETRY) {
            throw new IllegalArgumentException("symbol must have marker, line, or fill role");
        }
    }
}
