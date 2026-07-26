package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.WebMercatorProjection;

record MbTilesTileProfile(
        int zoom,
        int minimumX,
        int minimumY,
        int maximumX,
        int maximumY,
        EncodedRasterFormat format) {
    MbTilesTileProfile {
        if (zoom < 0 || zoom > 22) {
            throw new IllegalArgumentException("Invalid MBTiles tile profile");
        }
        int axis = 1 << zoom;
        if (minimumX < 0
                || minimumY < 0
                || maximumX < minimumX
                || maximumY < minimumY
                || maximumX >= axis
                || maximumY >= axis) {
            throw new IllegalArgumentException("Invalid MBTiles tile profile");
        }
    }

    int matrixWidth() {
        return maximumX - minimumX + 1;
    }

    int matrixHeight() {
        return maximumY - minimumY + 1;
    }

    int tmsRowForXyz(int xyzY) {
        return Math.toIntExact((1L << zoom) - 1L - xyzY);
    }

    int xyzRowForTms(int tmsY) {
        return Math.toIntExact((1L << zoom) - 1L - tmsY);
    }

    Envelope bounds() {
        double world = WebMercatorProjection.WORLD_LIMIT;
        double span = 2.0 * world / (1 << zoom);
        double west = -world + minimumX * span;
        double east = -world + ((long) maximumX + 1L) * span;
        double north = world - minimumY * span;
        double south = world - ((long) maximumY + 1L) * span;
        return new Envelope(west, south, east, north);
    }
}
