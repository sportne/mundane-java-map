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
     * Returns the smallest tile-aligned region covering an EPSG:3857 envelope.
     *
     * @param bounds positive-area canonical Web Mercator bounds
     * @param zoom canonical XYZ zoom
     * @return covering tile region
     */
    public static XyzTileRegion covering(Envelope bounds, int zoom) {
        if (bounds == null) {
            throw new NullPointerException("bounds");
        }
        if (zoom < 0 || zoom > 22) {
            throw new IllegalArgumentException("XYZ zoom must be in [0,22]");
        }
        double limit = WebMercatorProjection.WORLD_LIMIT;
        if (!(bounds.maxX() > bounds.minX()) || !(bounds.maxY() > bounds.minY())) {
            throw new IllegalArgumentException("Bounds must have positive area");
        }
        if (bounds.minX() < -limit
                || bounds.maxX() > limit
                || bounds.minY() < -limit
                || bounds.maxY() > limit) {
            throw new IllegalArgumentException("Bounds exceed the canonical Web Mercator world");
        }
        int axis = 1 << zoom;
        int west = lowerCell(bounds.minX(), axis, -limit, limit);
        int east = upperCell(bounds.maxX(), axis, -limit, limit);
        int north = lowerCell(-bounds.maxY(), axis, -limit, limit);
        int south = upperCell(-bounds.minY(), axis, -limit, limit);
        return new XyzTileRegion(zoom, west, north, east, south);
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

    /**
     * Returns the number of tile columns.
     *
     * @return positive tile-column count
     */
    public int widthInTiles() {
        return maximumX - minimumX + 1;
    }

    /**
     * Returns the number of tile rows.
     *
     * @return positive tile-row count
     */
    public int heightInTiles() {
        return maximumY - minimumY + 1;
    }

    /**
     * Returns the exact tile-aligned EPSG:3857 bounds.
     *
     * @return canonical Web Mercator bounds
     */
    public Envelope bounds() {
        double world = WebMercatorProjection.WORLD_LIMIT;
        double span = 2.0 * world / (1 << zoom);
        double west = -world + minimumX * span;
        double east = -world + ((long) maximumX + 1L) * span;
        double north = world - minimumY * span;
        double south = world - ((long) maximumY + 1L) * span;
        return new Envelope(west, south, east, north);
    }

    Envelope singleTileBounds() {
        if (!isSingleTile()) {
            throw new IllegalStateException("Region does not contain exactly one tile");
        }
        return bounds();
    }

    private static int lowerCell(double coordinate, int axis, double minimum, double maximum) {
        if (coordinate == maximum) {
            return axis - 1;
        }
        int low = 0;
        int high = axis;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (edge(middle, axis, minimum, maximum) <= coordinate) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperCell(double coordinate, int axis, double minimum, double maximum) {
        if (coordinate == maximum) {
            return axis - 1;
        }
        int low = 0;
        int high = axis;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (edge(middle, axis, minimum, maximum) < coordinate) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return Math.max(0, low - 1);
    }

    private static double edge(int index, int axis, double minimum, double maximum) {
        return minimum + (maximum - minimum) * ((double) index / axis);
    }
}
