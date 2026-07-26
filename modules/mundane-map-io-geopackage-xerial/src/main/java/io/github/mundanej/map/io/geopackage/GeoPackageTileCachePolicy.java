package io.github.mundanej.map.io.geopackage;

import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Immutable source-local decoded-tile cache policy.
 *
 * <p>The cache is disabled by default. Enabled policies count successful immutable 256-by-256 RGBA
 * tiles and commit access-order changes only after a complete successful raster read.
 */
public final class GeoPackageTileCachePolicy {
    private static final long TILE_BYTES = 256L * 256L * Integer.BYTES;
    private static final GeoPackageTileCachePolicy DISABLED = new GeoPackageTileCachePolicy(0, 0);

    private final int maximumEntries;
    private final long maximumPixelBytes;

    private GeoPackageTileCachePolicy(int maximumEntries, long maximumPixelBytes) {
        this.maximumEntries = maximumEntries;
        this.maximumPixelBytes = maximumPixelBytes;
    }

    /**
     * Returns the shared disabled policy.
     *
     * @return a policy retaining no decoded tiles
     */
    public static GeoPackageTileCachePolicy disabled() {
        return DISABLED;
    }

    /**
     * Creates a positive bounded policy.
     *
     * @param maximumEntries maximum retained tile count
     * @param maximumPixelBytes maximum exact RGBA bytes
     * @return enabled cache policy
     * @throws IllegalArgumentException if an argument violates the documented constraints
     */
    public static GeoPackageTileCachePolicy bounded(int maximumEntries, long maximumPixelBytes) {
        if (maximumEntries <= 0 || maximumPixelBytes < TILE_BYTES) {
            throw new IllegalArgumentException(
                    "GeoPackage tile-cache limits must retain at least one decoded tile");
        }
        return new GeoPackageTileCachePolicy(maximumEntries, maximumPixelBytes);
    }

    /**
     * Returns whether retention is enabled.
     *
     * @return whether decoded tiles may be retained
     */
    public boolean enabled() {
        return maximumEntries != 0;
    }

    /**
     * Returns the entry ceiling when enabled.
     *
     * @return optional positive entry ceiling
     */
    public OptionalInt maximumEntries() {
        return enabled() ? OptionalInt.of(maximumEntries) : OptionalInt.empty();
    }

    /**
     * Returns the byte ceiling when enabled.
     *
     * @return optional positive byte ceiling
     */
    public OptionalLong maximumPixelBytes() {
        return enabled() ? OptionalLong.of(maximumPixelBytes) : OptionalLong.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GeoPackageTileCachePolicy policy
                && maximumEntries == policy.maximumEntries
                && maximumPixelBytes == policy.maximumPixelBytes;
    }

    @Override
    public int hashCode() {
        return 31 * maximumEntries + Long.hashCode(maximumPixelBytes);
    }

    @Override
    public String toString() {
        return enabled()
                ? "GeoPackageTileCachePolicy[maximumEntries="
                        + maximumEntries
                        + ", maximumPixelBytes="
                        + maximumPixelBytes
                        + ']'
                : "GeoPackageTileCachePolicy[disabled]";
    }
}
