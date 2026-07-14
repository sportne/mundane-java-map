package io.github.mundanej.map.api;

import java.util.Objects;

/** An immutable axis-aligned two-dimensional bounding box. */
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

    /** Returns an envelope containing a single coordinate. */
    public static Envelope at(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return new Envelope(coordinate.x(), coordinate.y(), coordinate.x(), coordinate.y());
    }

    /** Returns the envelope width. */
    public double width() {
        return maxX - minX;
    }

    /** Returns the envelope height. */
    public double height() {
        return maxY - minY;
    }

    /** Returns the center coordinate. */
    public Coordinate center() {
        return new Coordinate(minX + width() / 2.0, minY + height() / 2.0);
    }

    /** Returns whether this envelope contains the coordinate, including the boundary. */
    public boolean contains(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return coordinate.x() >= minX
                && coordinate.x() <= maxX
                && coordinate.y() >= minY
                && coordinate.y() <= maxY;
    }

    /** Returns the union of this envelope and another envelope. */
    public Envelope union(Envelope other) {
        Objects.requireNonNull(other, "other");
        return new Envelope(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY));
    }
}
