package io.github.mundanej.map.io.gpx;

import io.github.mundanej.map.api.FeatureSourceLimits;
import java.util.Objects;

/**
 * Immutable GPX opening policy.
 *
 * @param formatLimits format-specific opening ceilings
 * @param sourceLimits query ceilings retained by the returned source
 */
public record GpxOpenOptions(GpxLimits formatLimits, FeatureSourceLimits sourceLimits) {
    /** Validates the opening policy. */
    public GpxOpenOptions {
        Objects.requireNonNull(formatLimits, "formatLimits");
        Objects.requireNonNull(sourceLimits, "sourceLimits");
    }

    /**
     * Returns the default opening policy.
     *
     * @return immutable defaults
     */
    public static GpxOpenOptions defaults() {
        return new GpxOpenOptions(GpxLimits.defaults(), FeatureSourceLimits.LEVEL_1);
    }

    /**
     * Returns a copy with format limits replaced.
     *
     * @param value replacement limits
     * @return immutable copy
     */
    public GpxOpenOptions withFormatLimits(GpxLimits value) {
        return new GpxOpenOptions(value, sourceLimits);
    }

    /**
     * Returns a copy with source/query limits replaced.
     *
     * @param value replacement limits
     * @return immutable copy
     */
    public GpxOpenOptions withSourceLimits(FeatureSourceLimits value) {
        return new GpxOpenOptions(formatLimits, value);
    }
}
