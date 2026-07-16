package io.github.mundanej.map.api;

/**
 * A two-dimensional coordinate. Units are defined by the context that owns the coordinate.
 *
 * @param x finite first-axis ordinate
 * @param y finite second-axis ordinate
 */
public record Coordinate(double x, double y) {
    /**
     * Creates a coordinate.
     *
     * @throws IllegalArgumentException if either ordinate is not finite
     */
    public Coordinate {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Coordinate ordinates must be finite");
        }
    }
}
