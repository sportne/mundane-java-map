package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.RasterSourceLimits;
import java.util.Objects;

/**
 * Immutable options for one explicit-zoom MBTiles raster source.
 *
 * @param limits container and tile ceilings
 * @param rasterSourceLimits raster request/output ceilings
 * @param cachePolicy optional source-local decoded-tile retention
 */
public record MbTilesOpenOptions(
        MbTilesLimits limits,
        RasterSourceLimits rasterSourceLimits,
        MbTilesTileCachePolicy cachePolicy) {
    /** Validates the option graph. */
    public MbTilesOpenOptions {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(rasterSourceLimits, "rasterSourceLimits");
        Objects.requireNonNull(cachePolicy, "cachePolicy");
        long requestAllowance =
                Math.max(
                        rasterSourceLimits.requestLimits().decodedIntermediateBytes(),
                        rasterSourceLimits.requestLimits().ownedPayloadBytes());
        long requiredOwnedBytes =
                Math.addExact(
                        Math.addExact(2L * limits.maximumBlobBytes(), 256L * 256L * Integer.BYTES),
                        requestAllowance);
        if (limits.maximumOwnedBytes() < requiredOwnedBytes) {
            throw new IllegalArgumentException(
                    "MBTiles owned-byte limit cannot cover configured raster requests");
        }
        if (cachePolicy.enabled()
                && (cachePolicy.maximumEntries().orElseThrow() > limits.maximumCacheEntries()
                        || cachePolicy.maximumPixelBytes().orElseThrow()
                                > limits.maximumCacheBytes())) {
            throw new IllegalArgumentException("MBTiles cache may not exceed container limits");
        }
    }

    /**
     * Returns conservative defaults with decoded caching disabled.
     *
     * @return default raster options
     */
    public static MbTilesOpenOptions defaults() {
        return new MbTilesOpenOptions(
                MbTilesLimits.DEFAULTS,
                RasterSourceLimits.LEVEL_1,
                MbTilesTileCachePolicy.disabled());
    }
}
