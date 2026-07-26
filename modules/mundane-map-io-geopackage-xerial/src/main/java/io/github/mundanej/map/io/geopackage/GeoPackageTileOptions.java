package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.RasterSourceLimits;
import java.util.Objects;

/**
 * Immutable options for one explicit-zoom GeoPackage tile source.
 *
 * @param limits container, tile, and cache ceilings
 * @param rasterSourceLimits raster request/output ceilings
 * @param cachePolicy optional source-local decoded-tile retention
 */
public record GeoPackageTileOptions(
        GeoPackageLimits limits,
        RasterSourceLimits rasterSourceLimits,
        GeoPackageTileCachePolicy cachePolicy) {
    /** Validates the immutable option graph. */
    public GeoPackageTileOptions {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(rasterSourceLimits, "rasterSourceLimits");
        Objects.requireNonNull(cachePolicy, "cachePolicy");
        if (cachePolicy.enabled()
                && (cachePolicy.maximumEntries().orElseThrow() > limits.maximumCacheEntries()
                        || cachePolicy.maximumPixelBytes().orElseThrow()
                                > limits.maximumCacheBytes())) {
            throw new IllegalArgumentException(
                    "GeoPackage tile cache may not exceed container limits");
        }
    }

    /**
     * Returns conservative defaults with decoded caching disabled.
     *
     * @return default tile options
     */
    public static GeoPackageTileOptions defaults() {
        return new GeoPackageTileOptions(
                GeoPackageLimits.DEFAULTS,
                RasterSourceLimits.LEVEL_1,
                GeoPackageTileCachePolicy.disabled());
    }
}
