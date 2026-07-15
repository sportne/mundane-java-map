package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable strict source-window raster request.
 *
 * @param sourceWindow strict source window
 * @param outputWidth positive output width
 * @param outputHeight positive output height
 * @param interpolation sampling mode
 * @param tighterLimits optional limits that only tighten the source limits
 */
public record RasterRequest(
        RasterWindow sourceWindow,
        int outputWidth,
        int outputHeight,
        RasterInterpolation interpolation,
        Optional<RasterRequestLimits> tighterLimits) {
    /**
     * Creates a nearest-neighbor request for source compatibility.
     *
     * @param sourceWindow strict source window
     * @param outputWidth positive output width
     * @param outputHeight positive output height
     * @param tighterLimits optional per-request limits that only tighten the source limits
     */
    public RasterRequest(
            RasterWindow sourceWindow,
            int outputWidth,
            int outputHeight,
            Optional<RasterRequestLimits> tighterLimits) {
        this(sourceWindow, outputWidth, outputHeight, RasterInterpolation.NEAREST, tighterLimits);
    }

    /** Validates structural request values. */
    public RasterRequest {
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        Objects.requireNonNull(interpolation, "interpolation");
        Objects.requireNonNull(tighterLimits, "tighterLimits");
        if (outputWidth <= 0 || outputHeight <= 0) {
            throw new IllegalArgumentException("Raster output dimensions must be positive");
        }
        if (Math.multiplyExact((long) outputWidth, outputHeight) <= 0) {
            throw new IllegalArgumentException("Raster output pixel count must be positive");
        }
    }
}
