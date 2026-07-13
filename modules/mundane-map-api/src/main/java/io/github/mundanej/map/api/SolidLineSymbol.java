package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** An immutable solid line with optional independently oriented endpoint markers. */
@SuppressWarnings("deprecation")
public final class SolidLineSymbol implements LineSymbol {
    /** The explicit built-in solid-line renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.solid-line");

    private final SymbolStroke stroke;
    private final Optional<Symbol> startMarker;
    private final Optional<Symbol> endMarker;
    private final double opacity;

    private SolidLineSymbol(
            SymbolStroke stroke,
            Optional<Symbol> startMarker,
            Optional<Symbol> endMarker,
            double opacity) {
        this.stroke = Objects.requireNonNull(stroke, "stroke");
        this.startMarker = requireMarker(startMarker, "startMarker");
        this.endMarker = requireMarker(endMarker, "endMarker");
        this.opacity = requireOpacity(opacity);
    }

    /** Creates a solid line with optional start and end markers. */
    public static SolidLineSymbol of(
            SymbolStroke stroke,
            Optional<Symbol> startMarker,
            Optional<Symbol> endMarker,
            double opacity) {
        return new SolidLineSymbol(stroke, startMarker, endMarker, opacity);
    }

    /** Creates a solid line without endpoint markers. */
    public static SolidLineSymbol of(SymbolStroke stroke, double opacity) {
        return of(stroke, Optional.empty(), Optional.empty(), opacity);
    }

    /** Returns the line stroke. */
    public SymbolStroke stroke() {
        return stroke;
    }

    /** Returns the optional start marker. */
    public Optional<Symbol> startMarker() {
        return startMarker;
    }

    /** Returns the optional end marker. */
    public Optional<Symbol> endMarker() {
        return endMarker;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    private static Optional<Symbol> requireMarker(Optional<Symbol> candidate, String name) {
        Objects.requireNonNull(candidate, name);
        candidate.ifPresent(
                symbol -> {
                    if (symbol instanceof FeatureStyle
                            || symbol.role() != SymbolRole.MARKER
                            || (!(symbol instanceof MarkerSymbol)
                                    && !(symbol instanceof CompositeSymbol))) {
                        throw new IllegalArgumentException(name + " must have marker role");
                    }
                });
        return candidate;
    }

    private static double requireOpacity(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        return value == 0.0 ? 0.0 : value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SolidLineSymbol symbol
                && stroke.equals(symbol.stroke)
                && startMarker.equals(symbol.startMarker)
                && endMarker.equals(symbol.endMarker)
                && Double.compare(opacity, symbol.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stroke, startMarker, endMarker, opacity);
    }

    @Override
    public String toString() {
        return "SolidLineSymbol{stroke="
                + stroke
                + ", startMarker="
                + startMarker
                + ", endMarker="
                + endMarker
                + ", opacity="
                + opacity
                + '}';
    }
}
