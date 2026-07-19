package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.RasterSourceLimits;
import java.util.Objects;

/**
 * Immutable options captured by one GeoTIFF raster open.
 *
 * @param formatLimits format parser and decoder ceilings
 * @param requestLimits raster request ceilings
 */
public record GeoTiffRasterOptions(GeoTiffLimits formatLimits, RasterSourceLimits requestLimits) {
    private static final GeoTiffRasterOptions DEFAULTS =
            new GeoTiffRasterOptions(GeoTiffLimits.defaults(), RasterSourceLimits.LEVEL_1);

    /** Validates immutable options. */
    public GeoTiffRasterOptions {
        Objects.requireNonNull(formatLimits, "formatLimits");
        Objects.requireNonNull(requestLimits, "requestLimits");
    }

    /**
     * Returns the shared conservative defaults.
     *
     * @return immutable defaults
     */
    public static GeoTiffRasterOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a copy with the requested format limits.
     *
     * @param limits format ceilings
     * @return updated options
     */
    public GeoTiffRasterOptions withFormatLimits(GeoTiffLimits limits) {
        return new GeoTiffRasterOptions(Objects.requireNonNull(limits, "limits"), requestLimits);
    }

    /**
     * Returns a copy with the requested raster request limits.
     *
     * @param limits request ceilings
     * @return updated options
     */
    public GeoTiffRasterOptions withRequestLimits(RasterSourceLimits limits) {
        return new GeoTiffRasterOptions(formatLimits, Objects.requireNonNull(limits, "limits"));
    }
}
