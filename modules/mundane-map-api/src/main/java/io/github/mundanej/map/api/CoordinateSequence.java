package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** An immutable packed sequence of positions in one explicit dimensional model. */
public final class CoordinateSequence {
    private final GeometryDimension dimension;
    private final double[] ordinates;
    private final Envelope envelope;

    private CoordinateSequence(
            GeometryDimension dimension, double[] ordinates, boolean emptyAllowed) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if ((!emptyAllowed && ordinates.length == 0)
                || ordinates.length % dimension.stride() != 0) {
            throw new IllegalArgumentException(
                    "A coordinate sequence requires complete positions for its dimension");
        }
        this.ordinates = ordinates.clone();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < this.ordinates.length; index += dimension.stride()) {
            double x = this.ordinates[index];
            double y = this.ordinates[index + 1];
            for (int ordinate = 0; ordinate < dimension.stride(); ordinate++) {
                if (!Double.isFinite(this.ordinates[index + ordinate])) {
                    throw new IllegalArgumentException(
                            "Coordinate sequence ordinates must be finite");
                }
            }
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Coordinate sequence ordinates must be finite");
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        envelope = this.ordinates.length == 0 ? null : new Envelope(minX, minY, maxX, maxY);
    }

    /**
     * Creates a coordinate sequence from packed {@code x, y} pairs.
     *
     * @param ordinates finite alternating x/y ordinates; the array is copied
     * @return immutable non-empty sequence
     */
    public static CoordinateSequence of(double... ordinates) {
        return new CoordinateSequence(GeometryDimension.XY, ordinates, false);
    }

    /**
     * Creates a coordinate sequence from positions packed for an explicit model.
     *
     * @param dimension dimensional model
     * @param ordinates finite packed ordinates; the array is copied
     * @return immutable non-empty sequence
     */
    public static CoordinateSequence of(GeometryDimension dimension, double... ordinates) {
        return new CoordinateSequence(dimension, ordinates, false);
    }

    /**
     * Creates an empty coordinate sequence retaining an explicit model.
     *
     * @param dimension dimensional model
     * @return immutable empty sequence
     */
    public static CoordinateSequence empty(GeometryDimension dimension) {
        return new CoordinateSequence(dimension, new double[0], true);
    }

    /**
     * Returns the dimensional model.
     *
     * @return coordinate dimension
     */
    public GeometryDimension dimension() {
        return dimension;
    }

    /**
     * Returns whether the sequence contains no positions.
     *
     * @return whether empty
     */
    public boolean isEmpty() {
        return ordinates.length == 0;
    }

    /**
     * Returns the number of coordinates.
     *
     * @return coordinate count
     */
    public int size() {
        return ordinates.length / dimension.stride();
    }

    /**
     * Returns the x ordinate at the specified coordinate index.
     *
     * @param index zero-based coordinate index
     * @return x ordinate
     */
    public double x(int index) {
        checkIndex(index);
        return ordinates[index * dimension.stride()];
    }

    /**
     * Returns the y ordinate at the specified coordinate index.
     *
     * @param index zero-based coordinate index
     * @return y ordinate
     */
    public double y(int index) {
        checkIndex(index);
        return ordinates[index * dimension.stride() + 1];
    }

    /**
     * Returns the z ordinate at the specified coordinate index.
     *
     * @param index zero-based coordinate index
     * @return z ordinate
     * @throws GeometryException when the dimensional model has no z ordinate
     */
    public double z(int index) {
        checkIndex(index);
        return ordinates[index * dimension.stride() + dimension.zOffset()];
    }

    /**
     * Returns the m ordinate at the specified coordinate index.
     *
     * @param index zero-based coordinate index
     * @return m ordinate
     * @throws GeometryException when the dimensional model has no m ordinate
     */
    public double m(int index) {
        checkIndex(index);
        return ordinates[index * dimension.stride() + dimension.mOffset()];
    }

    /**
     * Returns the coordinate at the specified index.
     *
     * @param index zero-based coordinate index
     * @return immutable coordinate
     */
    public Coordinate coordinate(int index) {
        return new Coordinate(x(index), y(index));
    }

    /**
     * Returns the precomputed sequence envelope.
     *
     * @return immutable coordinate-space envelope
     */
    public Envelope envelope() {
        if (envelope == null) {
            throw GeometryException.emptyEnvelope(GeometryKind.POINT);
        }
        return envelope;
    }

    /**
     * Returns the precomputed x/y bounds when positions exist.
     *
     * @return optional bounds
     */
    public Optional<Envelope> bounds() {
        return Optional.ofNullable(envelope);
    }

    /**
     * Returns whether the first and last coordinates are equal.
     *
     * @return whether the sequence is closed
     */
    public boolean isClosed() {
        if (isEmpty()) {
            return false;
        }
        int last = size() - 1;
        int stride = dimension.stride();
        for (int ordinate = 0; ordinate < stride; ordinate++) {
            if (Double.compare(ordinates[ordinate], ordinates[last * stride + ordinate]) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a defensive packed-array copy.
     *
     * @return newly allocated alternating x/y ordinates
     */
    public double[] toArray() {
        return ordinates.clone();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CoordinateSequence sequence
                && dimension == sequence.dimension
                && Arrays.equals(ordinates, sequence.ordinates);
    }

    @Override
    public int hashCode() {
        return 31 * dimension.hashCode() + Arrays.hashCode(ordinates);
    }

    @Override
    public String toString() {
        return "CoordinateSequence[dimension="
                + dimension
                + ", ordinates="
                + Arrays.toString(ordinates)
                + "]";
    }
}
