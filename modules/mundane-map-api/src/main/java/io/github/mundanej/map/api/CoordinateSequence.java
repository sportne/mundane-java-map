package io.github.mundanej.map.api;

import java.util.Arrays;

/** An immutable packed sequence of {@code x, y} coordinate pairs. */
public final class CoordinateSequence {
    private final double[] ordinates;
    private final Envelope envelope;

    private CoordinateSequence(double[] ordinates) {
        if (ordinates.length < 2 || ordinates.length % 2 != 0) {
            throw new IllegalArgumentException("A coordinate sequence requires complete x/y pairs");
        }
        this.ordinates = ordinates.clone();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < this.ordinates.length; index += 2) {
            double x = this.ordinates[index];
            double y = this.ordinates[index + 1];
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Coordinate sequence ordinates must be finite");
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        envelope = new Envelope(minX, minY, maxX, maxY);
    }

    /** Creates a coordinate sequence from packed {@code x, y} pairs. */
    public static CoordinateSequence of(double... ordinates) {
        return new CoordinateSequence(ordinates);
    }

    /** Returns the number of coordinates. */
    public int size() {
        return ordinates.length / 2;
    }

    /** Returns the x ordinate at the specified coordinate index. */
    public double x(int index) {
        checkIndex(index);
        return ordinates[index * 2];
    }

    /** Returns the y ordinate at the specified coordinate index. */
    public double y(int index) {
        checkIndex(index);
        return ordinates[index * 2 + 1];
    }

    /** Returns the coordinate at the specified index. */
    public Coordinate coordinate(int index) {
        return new Coordinate(x(index), y(index));
    }

    /** Returns the precomputed sequence envelope. */
    public Envelope envelope() {
        return envelope;
    }

    /** Returns whether the first and last coordinates are equal. */
    public boolean isClosed() {
        int last = size() - 1;
        return Double.compare(x(0), x(last)) == 0 && Double.compare(y(0), y(last)) == 0;
    }

    /** Returns a defensive packed-array copy. */
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
                && Arrays.equals(ordinates, sequence.ordinates);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ordinates);
    }

    @Override
    public String toString() {
        return "CoordinateSequence" + Arrays.toString(ordinates);
    }
}

