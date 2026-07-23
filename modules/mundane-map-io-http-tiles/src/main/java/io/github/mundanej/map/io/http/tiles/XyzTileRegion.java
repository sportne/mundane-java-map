package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.WebMercatorProjection;

/**
 * Inclusive bounded XYZ tile rectangle at one zoom level.
 *
 * @param zoom canonical XYZ zoom
 * @param minimumX inclusive minimum tile column
 * @param minimumY inclusive minimum tile row
 * @param maximumX inclusive maximum tile column
 * @param maximumY inclusive maximum tile row
 */
public record XyzTileRegion(int zoom, int minimumX, int minimumY, int maximumX, int maximumY) {
    /** Validates canonical XYZ coordinates through zoom 22. */
    public XyzTileRegion {
        if (zoom < 0 || zoom > 22) {
            throw new IllegalArgumentException("XYZ zoom must be in [0,22]");
        }
        int axis = 1 << zoom;
        if (minimumX < 0
                || minimumY < 0
                || maximumX < minimumX
                || maximumY < minimumY
                || maximumX >= axis
                || maximumY >= axis) {
            throw new IllegalArgumentException("XYZ tile coordinates are outside the zoom grid");
        }
    }

    /**
     * Creates one exact tile region.
     *
     * @param zoom canonical XYZ zoom
     * @param x tile column
     * @param y tile row
     * @return single-tile region
     */
    public static XyzTileRegion single(int zoom, int x, int y) {
        return new XyzTileRegion(zoom, x, y, x, y);
    }

    /**
     * Returns the checked number of tiles.
     *
     * @return positive tile count
     */
    public long tileCount() {
        return Math.multiplyExact((long) maximumX - minimumX + 1L, (long) maximumY - minimumY + 1L);
    }

    /**
     * Returns whether this region contains exactly one tile.
     *
     * @return whether this is a single-tile region
     */
    public boolean isSingleTile() {
        return minimumX == maximumX && minimumY == maximumY;
    }

    Envelope singleTileBounds() {
        if (!isSingleTile()) {
            throw new IllegalStateException("Region does not contain exactly one tile");
        }
        double world = WebMercatorProjection.WORLD_LIMIT;
        double span = 2.0 * world / (1 << zoom);
        double west = -world + minimumX * span;
        double east = west + span;
        double north = world - minimumY * span;
        double south = north - span;
        return new Envelope(west, south, east, north);
    }
}
