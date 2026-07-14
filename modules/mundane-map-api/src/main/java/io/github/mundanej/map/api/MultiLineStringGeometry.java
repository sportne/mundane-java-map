package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable packed multiline geometry with coordinate fencepost offsets. */
public final class MultiLineStringGeometry implements Geometry {
    private final CoordinateSequence coordinates;
    private final int[] partOffsets;

    private MultiLineStringGeometry(CoordinateSequence coordinates, int[] offsets) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.partOffsets = offsets.clone();
        validate();
    }

    /** Creates a packed multiline. */
    public static MultiLineStringGeometry of(CoordinateSequence coordinates, int[] partOffsets) {
        return new MultiLineStringGeometry(
                coordinates, Objects.requireNonNull(partOffsets, "partOffsets"));
    }

    /** Flattens ordered line parts. */
    public static MultiLineStringGeometry ofParts(List<CoordinateSequence> parts) {
        List<CoordinateSequence> copy = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A multiline requires at least one part");
        }
        long count = 0;
        int[] offsets = new int[copy.size() + 1];
        for (int index = 0; index < copy.size(); index++) {
            CoordinateSequence part = Objects.requireNonNull(copy.get(index), "part");
            if (part.size() < 2) {
                throw new IllegalArgumentException(
                        "Each line part requires at least two coordinates");
            }
            count = Math.addExact(count, part.size());
            if (count > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Too many multiline coordinates");
            }
            offsets[index + 1] = (int) count;
        }
        double[] packed = new double[Math.toIntExact(Math.multiplyExact(count, 2))];
        int target = 0;
        for (CoordinateSequence part : copy) {
            double[] values = part.toArray();
            System.arraycopy(values, 0, packed, target, values.length);
            target += values.length;
        }
        return new MultiLineStringGeometry(CoordinateSequence.of(packed), offsets);
    }

    /** Returns packed coordinates. */
    public CoordinateSequence coordinates() {
        return coordinates;
    }

    /** Returns the number of parts. */
    public int partCount() {
        return partOffsets.length - 1;
    }

    /** Returns a coordinate fencepost offset. */
    public int partOffset(int fenceIndex) {
        return partOffsets[fenceIndex];
    }

    /** Returns defensive coordinate fencepost offsets. */
    public int[] partOffsets() {
        return partOffsets.clone();
    }

    @Override
    public Envelope envelope() {
        return coordinates.envelope();
    }

    private void validate() {
        if (partOffsets.length < 2
                || partOffsets[0] != 0
                || partOffsets[partOffsets.length - 1] != coordinates.size()) {
            throw new IllegalArgumentException("Part offsets must span the exact coordinate count");
        }
        for (int index = 1; index < partOffsets.length; index++) {
            if (partOffsets[index] - partOffsets[index - 1] < 2) {
                throw new IllegalArgumentException(
                        "Each line part requires at least two coordinates");
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MultiLineStringGeometry value
                && coordinates.equals(value.coordinates)
                && Arrays.equals(partOffsets, value.partOffsets);
    }

    @Override
    public int hashCode() {
        return 31 * coordinates.hashCode() + Arrays.hashCode(partOffsets);
    }

    @Override
    public String toString() {
        return "MultiLineStringGeometry[coordinates="
                + coordinates
                + ", partOffsets="
                + Arrays.toString(partOffsets)
                + "]";
    }
}
