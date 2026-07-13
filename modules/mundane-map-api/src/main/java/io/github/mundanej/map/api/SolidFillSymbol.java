package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** An immutable even-odd solid polygon fill with an optional line-symbol outline. */
@SuppressWarnings("deprecation")
public final class SolidFillSymbol implements FillSymbol {
    /** The explicit built-in solid-fill renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.solid-fill");

    private final Rgba fill;
    private final Optional<Symbol> outline;
    private final double opacity;

    private SolidFillSymbol(Rgba fill, Optional<Symbol> outline, double opacity) {
        this.fill = Objects.requireNonNull(fill, "fill");
        this.outline = requireOutline(outline);
        this.opacity = requireOpacity(opacity);
    }

    /** Creates a solid fill with an optional line-symbol outline. */
    public static SolidFillSymbol of(Rgba fill, Optional<Symbol> outline, double opacity) {
        return new SolidFillSymbol(fill, outline, opacity);
    }

    /** Creates a solid fill without an outline. */
    public static SolidFillSymbol of(Rgba fill, double opacity) {
        return of(fill, Optional.empty(), opacity);
    }

    /** Returns the interior color. */
    public Rgba fill() {
        return fill;
    }

    /** Returns the optional line-symbol outline. */
    public Optional<Symbol> outline() {
        return outline;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    static Optional<Symbol> requireOutline(Optional<Symbol> candidate) {
        Objects.requireNonNull(candidate, "outline");
        candidate.ifPresent(
                symbol -> {
                    if (symbol instanceof FeatureStyle
                            || symbol.role() != SymbolRole.LINE
                            || (!(symbol instanceof LineSymbol)
                                    && !(symbol instanceof CompositeSymbol))) {
                        throw new IllegalArgumentException("outline must have line role");
                    }
                });
        return candidate;
    }

    static double requireOpacity(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        return value == 0.0 ? 0.0 : value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SolidFillSymbol symbol
                && fill.equals(symbol.fill)
                && outline.equals(symbol.outline)
                && Double.compare(opacity, symbol.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fill, outline, opacity);
    }

    @Override
    public String toString() {
        return "SolidFillSymbol{fill="
                + fill
                + ", outline="
                + outline
                + ", opacity="
                + opacity
                + '}';
    }
}
