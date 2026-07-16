package io.github.mundanej.map.api;

/**
 * Immutable half-open raster-grid window.
 *
 * @param column non-negative zero-based source-grid column
 * @param row non-negative zero-based source-grid row
 * @param width positive window width in pixels
 * @param height positive window height in pixels
 */
public record RasterWindow(int column, int row, int width, int height) {
    /** Validates non-negative origins, positive dimensions, and checked ends. */
    public RasterWindow {
        if (column < 0 || row < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Raster window origins must be non-negative and dimensions positive");
        }
        if (Math.addExact((long) column, width) <= column
                || Math.addExact((long) row, height) <= row) {
            throw new IllegalArgumentException("Raster window ends must follow their origins");
        }
    }

    /**
     * Returns the exclusive column end.
     *
     * @return column plus width
     */
    public long endColumn() {
        return (long) column + width;
    }

    /**
     * Returns the exclusive row end.
     *
     * @return row plus height
     */
    public long endRow() {
        return (long) row + height;
    }
}
