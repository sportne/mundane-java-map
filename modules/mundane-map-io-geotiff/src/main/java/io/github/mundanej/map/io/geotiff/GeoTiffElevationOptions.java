package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationUnit;
import java.util.Objects;

/**
 * Immutable options captured by one signed-integer GeoTIFF elevation open.
 *
 * @param elevationUnit caller-declared unit of every decoded elevation sample
 * @param formatLimits format parser, decoder, and working-memory ceilings
 * @param sourceLimits final eager elevation-source ceilings
 */
public record GeoTiffElevationOptions(
        ElevationUnit elevationUnit,
        GeoTiffLimits formatLimits,
        ElevationSourceLimits sourceLimits) {
    /** Validates the explicit vertical unit and immutable resource ceilings. */
    public GeoTiffElevationOptions {
        Objects.requireNonNull(elevationUnit, "elevationUnit");
        Objects.requireNonNull(formatLimits, "formatLimits");
        Objects.requireNonNull(sourceLimits, "sourceLimits");
    }

    /**
     * Creates options with conservative default format and elevation-source limits.
     *
     * @param elevationUnit explicit unit of every stored sample
     * @return immutable options
     */
    public static GeoTiffElevationOptions of(ElevationUnit elevationUnit) {
        return new GeoTiffElevationOptions(
                elevationUnit, GeoTiffLimits.defaults(), ElevationSourceLimits.DEFAULTS);
    }

    /**
     * Returns a copy with the requested format ceilings.
     *
     * @param limits format parser, decoder, and working-memory ceilings
     * @return updated immutable options
     */
    public GeoTiffElevationOptions withFormatLimits(GeoTiffLimits limits) {
        return new GeoTiffElevationOptions(
                elevationUnit, Objects.requireNonNull(limits, "limits"), sourceLimits);
    }

    /**
     * Returns a copy with the requested elevation-source ceilings.
     *
     * @param limits final eager elevation-source ceilings
     * @return updated immutable options
     */
    public GeoTiffElevationOptions withSourceLimits(ElevationSourceLimits limits) {
        return new GeoTiffElevationOptions(
                elevationUnit, formatLimits, Objects.requireNonNull(limits, "limits"));
    }
}
