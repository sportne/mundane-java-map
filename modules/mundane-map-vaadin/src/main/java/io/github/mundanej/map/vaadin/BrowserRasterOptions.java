package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.RasterInterpolation;
import java.util.Objects;

/**
 * Immutable browser raster sampling and opacity options.
 *
 * @param interpolation server-side resampling policy
 * @param opacity finite layer opacity in {@code [0,1]}
 */
public record BrowserRasterOptions(RasterInterpolation interpolation, double opacity) {
    private static final BrowserRasterOptions DEFAULTS =
            new BrowserRasterOptions(RasterInterpolation.NEAREST, 1.0);

    /** Validates the closed interpolation and opacity profile. */
    public BrowserRasterOptions {
        Objects.requireNonNull(interpolation, "interpolation");
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        if (opacity == 0.0) {
            opacity = 0.0;
        }
    }

    /**
     * Returns nearest-neighbor, fully opaque options.
     *
     * @return shared defaults
     */
    public static BrowserRasterOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a copy using another interpolation policy.
     *
     * @param value requested policy
     * @return updated options
     */
    public BrowserRasterOptions withInterpolation(RasterInterpolation value) {
        return new BrowserRasterOptions(value, opacity);
    }

    /**
     * Returns a copy using another opacity.
     *
     * @param value finite opacity in {@code [0,1]}
     * @return updated options
     */
    public BrowserRasterOptions withOpacity(double value) {
        return new BrowserRasterOptions(interpolation, value);
    }
}
