package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.FeatureSourceLimits;
import java.util.Objects;

/**
 * Options for a selected GeoPackage feature table.
 *
 * @param limits immutable container and geometry ceilings
 * @param featureSourceLimits immutable query-output ceilings
 */
public record GeoPackageFeatureOptions(
        GeoPackageLimits limits, FeatureSourceLimits featureSourceLimits) {
    /** Validates options. */
    public GeoPackageFeatureOptions {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(featureSourceLimits, "featureSourceLimits");
    }

    /**
     * Returns conservative defaults.
     *
     * @return default feature options
     */
    public static GeoPackageFeatureOptions defaults() {
        return new GeoPackageFeatureOptions(GeoPackageLimits.DEFAULTS, FeatureSourceLimits.LEVEL_1);
    }
}
