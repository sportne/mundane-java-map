package io.github.mundanej.map.io.mbtiles;

import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Immutable source-local decoded-tile cache policy.
 *
 * <p>The cache is disabled by default. Enabled policies count successful immutable 256-by-256 RGBA
 * tiles and commit access-order changes only after a complete successful raster read.
 */
public final class MbTilesTileCachePolicy {
    private static final long TILE_BYTES = 256L * 256L * Integer.BYTES;
    private static final MbTilesTileCachePolicy DISABLED = new MbTilesTileCachePolicy(0, 0);

    private final int maximumEntries;
    private final long maximumPixelBytes;

    private MbTilesTileCachePolicy(int maximumEntries, long maximumPixelBytes) {
        this.maximumEntries = maximumEntries;
        this.maximumPixelBytes = maximumPixelBytes;
    }

    /**
     * Returns the shared disabled policy.
     *
     * @return a policy retaining no decoded tiles
     */
    public static MbTilesTileCachePolicy disabled() {
        return DISABLED;
    }

    /**
     * Creates a positive bounded policy.
     *
     * @param maximumEntries maximum retained tile count
     * @param maximumPixelBytes maximum exact RGBA bytes
     * @return enabled cache policy
     */
    public static MbTilesTileCachePolicy bounded(int maximumEntries, long maximumPixelBytes) {
        if (maximumEntries <= 0 || maximumPixelBytes < TILE_BYTES) {
            throw new IllegalArgumentException(
                    "MBTiles tile-cache limits must retain at least one decoded tile");
        }
        return new MbTilesTileCachePolicy(maximumEntries, maximumPixelBytes);
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
        return other instanceof MbTilesTileCachePolicy policy
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
                ? "MbTilesTileCachePolicy[maximumEntries="
                        + maximumEntries
                        + ", maximumPixelBytes="
                        + maximumPixelBytes
                        + ']'
                : "MbTilesTileCachePolicy[disabled]";
    }
}
