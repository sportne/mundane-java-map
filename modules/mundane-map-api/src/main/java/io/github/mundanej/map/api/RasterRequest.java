package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable strict source-window raster request. */
public record RasterRequest(
        RasterWindow sourceWindow,
        int outputWidth,
        int outputHeight,
        Optional<RasterRequestLimits> tighterLimits) {
    /** Validates structural request values. */
    public RasterRequest {
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        Objects.requireNonNull(tighterLimits, "tighterLimits");
        if (outputWidth <= 0 || outputHeight <= 0) {
            throw new IllegalArgumentException("Raster output dimensions must be positive");
        }
        if (Math.multiplyExact((long) outputWidth, outputHeight) <= 0) {
            throw new IllegalArgumentException("Raster output pixel count must be positive");
        }
    }
}
