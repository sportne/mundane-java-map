package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable polygon symbol with exactly one solid or graphic fill and an optional advanced outline.
 *
 * @param color optional solid color
 * @param graphicFill optional repeated marker fill
 * @param outline optional advanced stroke outline
 * @param opacity finite opacity from zero through one
 */
public record AdvancedFillSymbol(
        Optional<Rgba> color,
        Optional<GraphicPaint> graphicFill,
        Optional<AdvancedStroke> outline,
        double opacity)
        implements FillSymbol {
    /** Explicit advanced-fill renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.advanced-fill");

    /** Creates and validates an advanced fill symbol. */
    public AdvancedFillSymbol {
        color = Objects.requireNonNull(color, "color").map(Objects::requireNonNull);
        graphicFill =
                Objects.requireNonNull(graphicFill, "graphicFill").map(Objects::requireNonNull);
        outline = Objects.requireNonNull(outline, "outline").map(Objects::requireNonNull);
        if (color.isPresent() == graphicFill.isPresent()) {
            throw new IllegalArgumentException(
                    "exactly one of color or graphicFill must be present");
        }
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be between zero and one");
        }
        opacity = opacity == 0.0 ? 0.0 : opacity;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }
}
