package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.RasterInterpolation;
import java.util.Objects;

/**
 * Immutable view-owned raster sampling and opacity presentation options.
 *
 * @param interpolation sampling mode used by the raster request
 * @param opacity finite layer opacity in {@code [0,1]}
 */
public record RasterRenderOptions(RasterInterpolation interpolation, double opacity) {
    /** Shared nearest-neighbor, fully opaque defaults. */
    private static final RasterRenderOptions DEFAULTS =
            new RasterRenderOptions(RasterInterpolation.NEAREST, 1.0);

    /**
     * Validates interpolation and finite opacity in {@code [0,1]}.
     *
     * @throws NullPointerException if {@code interpolation} is {@code null}
     * @throws IllegalArgumentException if {@code opacity} is non-finite or outside {@code [0,1]}
     */
    public RasterRenderOptions {
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
     * @return shared immutable defaults
     */
    public static RasterRenderOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a copy with the requested interpolation mode.
     *
     * @param value requested sampling mode
     * @return immutable updated options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RasterRenderOptions withInterpolation(RasterInterpolation value) {
        return new RasterRenderOptions(value, opacity);
    }

    /**
     * Returns a copy with the requested opacity.
     *
     * @param value finite opacity in {@code [0,1]}
     * @return immutable updated options
     * @throws IllegalArgumentException if {@code value} is non-finite or outside {@code [0,1]}
     */
    public RasterRenderOptions withOpacity(double value) {
        return new RasterRenderOptions(interpolation, value);
    }
}
