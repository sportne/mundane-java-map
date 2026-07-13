package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** An immutable bounded diagonal hatch fill with an optional line-symbol outline. */
public final class HatchFillSymbol implements FillSymbol {
    /** Default maximum candidate segments generated for one feature. */
    public static final int DEFAULT_MAX_SEGMENTS = 8_192;

    /** The explicit built-in hatch-fill renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.hatch-fill");

    private final HatchPattern pattern;
    private final SymbolStroke stroke;
    private final SymbolLength spacing;
    private final SymbolRotationMode rotationMode;
    private final Optional<Symbol> outline;
    private final double opacity;
    private final int maxSegments;

    private HatchFillSymbol(
            HatchPattern pattern,
            SymbolStroke stroke,
            SymbolLength spacing,
            SymbolRotationMode rotationMode,
            Optional<Symbol> outline,
            double opacity,
            int maxSegments) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.stroke = Objects.requireNonNull(stroke, "stroke");
        this.spacing = Objects.requireNonNull(spacing, "spacing");
        this.rotationMode = Objects.requireNonNull(rotationMode, "rotationMode");
        this.outline = SolidFillSymbol.requireOutline(outline);
        this.opacity = SolidFillSymbol.requireOpacity(opacity);
        if (maxSegments <= 0) {
            throw new IllegalArgumentException("maxSegments must be positive");
        }
        this.maxSegments = maxSegments;
    }

    /** Creates a bounded hatch fill with all policies explicit. */
    public static HatchFillSymbol of(
            HatchPattern pattern,
            SymbolStroke stroke,
            SymbolLength spacing,
            SymbolRotationMode rotationMode,
            Optional<Symbol> outline,
            double opacity,
            int maxSegments) {
        return new HatchFillSymbol(
                pattern, stroke, spacing, rotationMode, outline, opacity, maxSegments);
    }

    /** Creates a hatch fill without an outline and with the default work limit. */
    public static HatchFillSymbol of(
            HatchPattern pattern,
            SymbolStroke stroke,
            SymbolLength spacing,
            SymbolRotationMode rotationMode,
            double opacity) {
        return of(
                pattern,
                stroke,
                spacing,
                rotationMode,
                Optional.empty(),
                opacity,
                DEFAULT_MAX_SEGMENTS);
    }

    /** Returns the hatch pattern. */
    public HatchPattern pattern() {
        return pattern;
    }

    /** Returns the hatch stroke. */
    public SymbolStroke stroke() {
        return stroke;
    }

    /** Returns the perpendicular spacing between hatch lines. */
    public SymbolLength spacing() {
        return spacing;
    }

    /** Returns whether hatch orientation and phase are screen- or map-relative. */
    public SymbolRotationMode rotationMode() {
        return rotationMode;
    }

    /** Returns the optional line-symbol outline. */
    public Optional<Symbol> outline() {
        return outline;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    /** Returns the maximum candidate segments permitted for one feature. */
    public int maxSegments() {
        return maxSegments;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HatchFillSymbol symbol
                && pattern == symbol.pattern
                && stroke.equals(symbol.stroke)
                && spacing.equals(symbol.spacing)
                && rotationMode == symbol.rotationMode
                && outline.equals(symbol.outline)
                && Double.compare(opacity, symbol.opacity) == 0
                && maxSegments == symbol.maxSegments;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, stroke, spacing, rotationMode, outline, opacity, maxSegments);
    }

    @Override
    public String toString() {
        return "HatchFillSymbol{pattern="
                + pattern
                + ", stroke="
                + stroke
                + ", spacing="
                + spacing
                + ", rotationMode="
                + rotationMode
                + ", outline="
                + outline
                + ", opacity="
                + opacity
                + ", maxSegments="
                + maxSegments
                + '}';
    }
}
