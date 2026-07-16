package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Basic geometry-dependent styling retained through the first Level 1 {@code 0.x} release for
 * pre-1.0 source migration.
 *
 * @param stroke geometry outline color
 * @param fill geometry interior color
 * @param strokeWidth non-negative outline width in screen pixels
 * @param pointDiameter positive point diameter in screen pixels
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

    /**
     * Creates a point style with a dark outline.
     *
     * @param fill point fill color
     * @param diameter positive diameter in screen pixels
     * @return legacy point style
     */
    public static FeatureStyle point(Rgba fill, double diameter) {
        return new FeatureStyle(Rgba.rgb(32, 32, 32), fill, 1.0, diameter);
    }

    /**
     * Creates a line style.
     *
     * @param stroke line color
     * @param width non-negative width in screen pixels
     * @return legacy line style
     */
    public static FeatureStyle line(Rgba stroke, double width) {
        return new FeatureStyle(stroke, Rgba.TRANSPARENT, width, 6.0);
    }

    /**
     * Creates a polygon style.
     *
     * @param stroke outline color
     * @param fill interior color
     * @param width non-negative outline width in screen pixels
     * @return legacy polygon style
     */
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
