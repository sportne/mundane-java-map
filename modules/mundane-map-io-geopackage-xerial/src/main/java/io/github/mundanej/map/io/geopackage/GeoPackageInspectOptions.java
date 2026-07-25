package io.github.mundanej.map.io.geopackage;

import java.util.Objects;

/**
 * Options for detached GeoPackage catalog inspection.
 *
 * @param limits immutable parser and SQLite work ceilings
 */
public record GeoPackageInspectOptions(GeoPackageLimits limits) {
    /** Validates options. */
    public GeoPackageInspectOptions {
        Objects.requireNonNull(limits, "limits");
    }

    /**
     * Returns conservative defaults.
     *
     * @return default inspection options
     */
    public static GeoPackageInspectOptions defaults() {
        return new GeoPackageInspectOptions(GeoPackageLimits.DEFAULTS);
    }
}
