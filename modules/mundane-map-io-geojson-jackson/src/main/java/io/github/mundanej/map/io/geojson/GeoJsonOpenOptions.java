package io.github.mundanej.map.io.geojson;

import io.github.mundanej.map.api.FeatureSourceLimits;
import java.util.Objects;

/**
 * Immutable GeoJSON opening policy.
 *
 * @param formatLimits format-specific opening ceilings
 * @param sourceLimits query ceilings retained by the returned source
 */
public record GeoJsonOpenOptions(GeoJsonLimits formatLimits, FeatureSourceLimits sourceLimits) {
    /** Validates the opening policy. */
    public GeoJsonOpenOptions {
        Objects.requireNonNull(formatLimits, "formatLimits");
        Objects.requireNonNull(sourceLimits, "sourceLimits");
    }

    /**
     * Returns the default opening policy.
     *
     * @return immutable defaults
     */
    public static GeoJsonOpenOptions defaults() {
        return new GeoJsonOpenOptions(GeoJsonLimits.defaults(), FeatureSourceLimits.LEVEL_1);
    }
}
