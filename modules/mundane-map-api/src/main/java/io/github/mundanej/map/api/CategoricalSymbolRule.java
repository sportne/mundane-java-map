package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * One exact thematic category and its immutable symbol.
 *
 * @param value exact normalized category
 * @param symbol immutable marker, line, or fill symbol
 */
public record CategoricalSymbolRule(ThematicValue value, Symbol symbol) {
    /** Validates the category and symbol. */
    public CategoricalSymbolRule {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(symbol, "symbol");
        FixedSymbolSelector.requireVectorRole(symbol.role());
    }
}
