package io.github.mundanej.map.io.mbtiles;

/**
 * Descriptive WGS 84 center and preferred zoom retained from MBTiles metadata.
 *
 * @param longitude longitude in degrees
 * @param latitude latitude in degrees
 * @param zoom preferred canonical zoom
 */
public record MbTilesCenter(double longitude, double latitude, int zoom) {
    /** Validates finite WGS 84 coordinates and canonical zoom. */
    public MbTilesCenter {
        if (!Double.isFinite(longitude)
                || !Double.isFinite(latitude)
                || longitude < -180
                || longitude > 180
                || latitude < -90
                || latitude > 90
                || zoom < 0
                || zoom > 22) {
            throw new IllegalArgumentException("MBTiles center is outside its supported domain");
        }
    }
}
