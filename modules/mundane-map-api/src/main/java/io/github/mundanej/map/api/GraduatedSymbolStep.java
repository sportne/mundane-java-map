package io.github.mundanej.map.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One normalized lower-inclusive numeric threshold and its immutable symbol.
 *
 * @param lowerInclusive normalized finite decimal lower bound
 * @param symbol immutable marker, line, or fill symbol
 */
public record GraduatedSymbolStep(BigDecimal lowerInclusive, Symbol symbol) {
    /** Validates and normalizes the lower bound and symbol. */
    public GraduatedSymbolStep {
        lowerInclusive = normalize(Objects.requireNonNull(lowerInclusive, "lowerInclusive"));
        Objects.requireNonNull(symbol, "symbol");
        FixedSymbolSelector.requireVectorRole(symbol.role());
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
