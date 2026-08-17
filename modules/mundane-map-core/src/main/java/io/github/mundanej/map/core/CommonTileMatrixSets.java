package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.Envelope;
import java.util.ArrayList;
import java.util.List;

/** Explicit constructors and XYZ adapters for reviewed OGC common TileMatrixSet definitions. */
public final class CommonTileMatrixSets {
    /** Highest reviewed common-quad level. */
    public static final int MAXIMUM_COMMON_QUAD_LEVEL = 24;

    /** Highest legacy HTTP XYZ level retained by the current adapter. */
    public static final int MAXIMUM_LEGACY_XYZ_LEVEL = 22;

    private static final double STANDARDIZED_PIXEL_SIZE_METRES = 0.00028;

    private CommonTileMatrixSets() {}

    /**
     * Returns the OGC WebMercatorQuad definition through the requested level.
     *
     * @param maximumLevel inclusive level in {@code [0,24]}
     * @return immutable EPSG:3857 top-left 256-cell quad set
     */
    public static TileMatrixSet webMercatorQuad(int maximumLevel) {
        requireLevel(maximumLevel);
        double limit = WebMercatorProjection.WORLD_LIMIT;
        return new TileMatrixSet(
                "WebMercatorQuad",
                CrsDefinitions.EPSG_3857,
                TileMatrixAxisOrder.XY,
                new Envelope(-limit, -limit, limit, limit),
                quadMatrices(maximumLevel, new Coordinate(-limit, limit), limit * 2, 1));
    }

    /**
     * Returns the OGC WorldCRS84Quad definition through the requested level.
     *
     * @param maximumLevel inclusive level in {@code [0,24]}
     * @return immutable CRS84 longitude/latitude top-left 256-cell quad set
     */
    public static TileMatrixSet worldCrs84Quad(int maximumLevel) {
        requireLevel(maximumLevel);
        CrsDefinition crs84 =
                new CrsDefinition(
                        "OGC:CRS84",
                        CrsKind.GEOGRAPHIC,
                        new CrsAxis(CrsAxisMeaning.LONGITUDE, CrsUnit.DEGREE),
                        new CrsAxis(CrsAxisMeaning.LATITUDE, CrsUnit.DEGREE),
                        new Envelope(-180, -90, 180, 90));
        return new TileMatrixSet(
                "WorldCRS84Quad",
                crs84,
                TileMatrixAxisOrder.XY,
                crs84.coordinateDomain(),
                quadMatrices(maximumLevel, new Coordinate(-180, 90), 360, 2));
    }

    /**
     * Returns the exact legacy Web Mercator XYZ profile through zoom 22.
     *
     * @return immutable WebMercatorQuad set
     */
    public static TileMatrixSet legacyXyz() {
        return webMercatorQuad(MAXIMUM_LEGACY_XYZ_LEVEL);
    }

    /**
     * Returns one legacy XYZ tile envelope without changing x/y/zoom semantics.
     *
     * @param zoom zoom in {@code [0,22]}
     * @param x zero-based XYZ column
     * @param y zero-based XYZ top-origin row
     * @return exact Web Mercator tile envelope
     * @throws IllegalArgumentException when zoom or coordinates are outside the legacy grid
     */
    public static Envelope xyzEnvelope(int zoom, long x, long y) {
        if (zoom < 0 || zoom > MAXIMUM_LEGACY_XYZ_LEVEL) {
            throw new IllegalArgumentException("XYZ zoom must be in [0,22]");
        }
        long axis = 1L << zoom;
        if (x < 0 || x >= axis || y < 0 || y >= axis) {
            throw new IllegalArgumentException("XYZ tile coordinates are outside the zoom grid");
        }
        double world = WebMercatorProjection.WORLD_LIMIT;
        double span = 2.0 * world / axis;
        double west = -world + x * span;
        double east = -world + (x + 1L) * span;
        double north = world - y * span;
        double south = world - (y + 1L) * span;
        return new Envelope(west, south, east, north);
    }

    private static List<TileMatrix> quadMatrices(
            int maximumLevel, Coordinate origin, double worldWidth, int baseWidth) {
        List<TileMatrix> matrices = new ArrayList<>(maximumLevel + 1);
        for (int level = 0; level <= maximumLevel; level++) {
            long scale = 1L << level;
            long matrixWidth = Math.multiplyExact(baseWidth, scale);
            long matrixHeight = scale;
            double cellSize = worldWidth / (256.0 * matrixWidth);
            double scaleDenominator =
                    baseWidth == 1
                            ? cellSize / STANDARDIZED_PIXEL_SIZE_METRES
                            : 279_541_132.014358 * Math.scalb(1.0, -level);
            matrices.add(
                    new TileMatrix(
                            Integer.toString(level),
                            scaleDenominator,
                            cellSize,
                            origin,
                            TileMatrixCorner.TOP_LEFT,
                            256,
                            256,
                            matrixWidth,
                            matrixHeight,
                            List.of()));
        }
        return List.copyOf(matrices);
    }

    private static void requireLevel(int maximumLevel) {
        if (maximumLevel < 0 || maximumLevel > MAXIMUM_COMMON_QUAD_LEVEL) {
            throw new IllegalArgumentException("Common quad level must be in [0,24]");
        }
    }
}
