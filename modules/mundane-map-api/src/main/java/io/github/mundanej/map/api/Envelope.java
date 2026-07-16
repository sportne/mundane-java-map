package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * An immutable axis-aligned two-dimensional bounding box.
 *
 * <p>Units and axis meanings are defined by the coordinate space that owns the envelope.
 *
 * @param minX finite inclusive first-axis minimum
 * @param minY finite inclusive second-axis minimum
 * @param maxX finite inclusive first-axis maximum
 * @param maxY finite inclusive second-axis maximum
 */
public record Envelope(double minX, double minY, double maxX, double maxY) {
    /**
     * Creates an envelope.
     *
     * @throws IllegalArgumentException if values are not finite or minima exceed maxima
     */
    public Envelope {
        if (!Double.isFinite(minX)
                || !Double.isFinite(minY)
                || !Double.isFinite(maxX)
                || !Double.isFinite(maxY)) {
            throw new IllegalArgumentException("Envelope ordinates must be finite");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Envelope minima must not exceed maxima");
        }
        if (!Double.isFinite(maxX - minX) || !Double.isFinite(maxY - minY)) {
            throw new IllegalArgumentException("Envelope spans must be finite");
        }
    }

    /**
     * Returns an envelope containing a single coordinate.
     *
     * @param coordinate coordinate to contain
     * @return zero-span envelope at the coordinate
     */
    public static Envelope at(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return new Envelope(coordinate.x(), coordinate.y(), coordinate.x(), coordinate.y());
    }

    /**
     * Returns the envelope width.
     *
     * @return first-axis span in owning coordinate units
     */
    public double width() {
        return maxX - minX;
    }

    /**
     * Returns the envelope height.
     *
     * @return second-axis span in owning coordinate units
     */
    public double height() {
        return maxY - minY;
    }

    /**
     * Returns the center coordinate.
     *
     * @return immutable center coordinate
     */
    public Coordinate center() {
        return new Coordinate(minX + width() / 2.0, minY + height() / 2.0);
    }

    /**
     * Returns whether this envelope contains the coordinate, including the boundary.
     *
     * @param coordinate coordinate in the same coordinate space
     * @return whether the coordinate is contained
     */
    public boolean contains(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return coordinate.x() >= minX
                && coordinate.x() <= maxX
                && coordinate.y() >= minY
                && coordinate.y() <= maxY;
    }

    /**
     * Returns the union of this envelope and another envelope.
     *
     * @param other envelope in the same coordinate space
     * @return smallest envelope containing both
     */
    public Envelope union(Envelope other) {
        Objects.requireNonNull(other, "other");
        return new Envelope(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY));
    }
}
