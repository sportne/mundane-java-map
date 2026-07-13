package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Basic geometry-dependent styling retained temporarily for pre-1.0 source migration.
 *
 * @deprecated use a role-specific {@link Symbol}; this compatibility value is removed before 1.0
 */
@Deprecated
public record FeatureStyle(Rgba stroke, Rgba fill, double strokeWidth, double pointDiameter)
        implements Symbol {
    /** The explicit compatibility renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.legacy-feature-style");

    /** Creates a feature style. */
    public FeatureStyle {
        Objects.requireNonNull(stroke, "stroke");
        Objects.requireNonNull(fill, "fill");
        if (!Double.isFinite(strokeWidth) || strokeWidth < 0.0) {
            throw new IllegalArgumentException("Stroke width must be finite and non-negative");
        }
        if (!Double.isFinite(pointDiameter) || pointDiameter <= 0.0) {
            throw new IllegalArgumentException("Point diameter must be finite and positive");
        }
    }

    /** Creates a point style with a dark outline. */
    public static FeatureStyle point(Rgba fill, double diameter) {
        return new FeatureStyle(Rgba.rgb(32, 32, 32), fill, 1.0, diameter);
    }

    /** Creates a line style. */
    public static FeatureStyle line(Rgba stroke, double width) {
        return new FeatureStyle(stroke, Rgba.TRANSPARENT, width, 6.0);
    }

    /** Creates a polygon style. */
    public static FeatureStyle polygon(Rgba stroke, Rgba fill, double width) {
        return new FeatureStyle(stroke, fill, width, 6.0);
    }

    @Override
    public SymbolRole role() {
        return SymbolRole.LEGACY_GEOMETRY;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    @Override
    public double opacity() {
        return 1.0;
    }
}
