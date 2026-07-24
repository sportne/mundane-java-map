package io.github.mundanej.map.io.kml;

import io.github.mundanej.map.api.FeatureSourceLimits;
import java.util.Objects;

/**
 * Immutable KML opening policy.
 *
 * @param formatLimits format-specific opening ceilings
 * @param sourceLimits query ceilings retained by the returned source
 */
public record KmlOpenOptions(KmlLimits formatLimits, FeatureSourceLimits sourceLimits) {
    /** Validates the opening policy. */
    public KmlOpenOptions {
        Objects.requireNonNull(formatLimits, "formatLimits");
        Objects.requireNonNull(sourceLimits, "sourceLimits");
    }

    /**
     * Returns the default opening policy.
     *
     * @return immutable defaults
     */
    public static KmlOpenOptions defaults() {
        return new KmlOpenOptions(KmlLimits.defaults(), FeatureSourceLimits.LEVEL_1);
    }

    /**
     * Returns a copy with format limits replaced.
     *
     * @param value replacement limits
     * @return immutable copy
     */
    public KmlOpenOptions withFormatLimits(KmlLimits value) {
        return new KmlOpenOptions(value, sourceLimits);
    }

    /**
     * Returns a copy with source/query limits replaced.
     *
     * @param value replacement limits
     * @return immutable copy
     */
    public KmlOpenOptions withSourceLimits(FeatureSourceLimits value) {
        return new KmlOpenOptions(formatLimits, value);
    }
}
