package io.github.mundanej.map.core;

import java.util.Arrays;

/** Immutable packed clipped hatch segments. */
public final class HatchSegments {
    private final double[] ordinates;
    private final int segmentCount;

    HatchSegments(double[] ordinates, int segmentCount) {
        this.ordinates = ordinates;
        this.segmentCount = segmentCount;
    }

    /** Returns the number of non-zero clipped segments. */
    public int segmentCount() {
        return segmentCount;
    }

    /** Returns the first x ordinate of a segment. */
    public double x1(int segmentIndex) {
        return ordinate(segmentIndex, 0);
    }

    /** Returns the first y ordinate of a segment. */
    public double y1(int segmentIndex) {
        return ordinate(segmentIndex, 1);
    }

    /** Returns the second x ordinate of a segment. */
    public double x2(int segmentIndex) {
        return ordinate(segmentIndex, 2);
    }

    /** Returns the second y ordinate of a segment. */
    public double y2(int segmentIndex) {
        return ordinate(segmentIndex, 3);
    }

    /** Returns a defensive packed {@code x1,y1,x2,y2} copy. */
    public double[] toArray() {
        return Arrays.copyOf(ordinates, segmentCount * 4);
    }

    private double ordinate(int segmentIndex, int offset) {
        if (segmentIndex < 0 || segmentIndex >= segmentCount) {
            throw new IndexOutOfBoundsException(segmentIndex);
        }
        return ordinates[segmentIndex * 4 + offset];
    }
}
