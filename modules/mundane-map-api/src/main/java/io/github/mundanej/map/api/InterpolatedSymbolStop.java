package io.github.mundanej.map.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One finite input and already constructed endpoint symbol.
 *
 * @param input normalized finite interpolation input
 * @param symbol immutable endpoint symbol
 */
public record InterpolatedSymbolStop(BigDecimal input, Symbol symbol) {
    /** Validates immutable endpoint state. */
    public InterpolatedSymbolStop {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(symbol, "symbol");
        input = input.signum() == 0 ? BigDecimal.ZERO : input.stripTrailingZeros();
    }
}
