package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable line symbol carrying the complete advanced stroke contract.
 *
 * @param stroke immutable advanced stroke
 * @param opacity finite opacity from zero through one
 */
public record AdvancedLineSymbol(AdvancedStroke stroke, double opacity) implements LineSymbol {
    /** Explicit advanced-line renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.advanced-line");

    /** Creates and validates an advanced line symbol. */
    public AdvancedLineSymbol {
        Objects.requireNonNull(stroke, "stroke");
        opacity = requireOpacity(opacity);
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    private static double requireOpacity(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("opacity must be between zero and one");
        }
        return value == 0.0 ? 0.0 : value;
    }
}
