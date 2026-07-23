package io.github.mundanej.map.symbology.milstd2525;

import io.github.mundanej.map.api.Symbol;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable degraded-resolution result.
 *
 * @param symbol ordinary toolkit-neutral symbol
 * @param problem empty for a fully supported identifier, otherwise the retained degradation
 */
public record MilitarySymbolResolution(Symbol symbol, Optional<MilitarySymbolProblem> problem) {
    /** Validates the immutable result. */
    public MilitarySymbolResolution {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(problem, "problem");
    }
}
