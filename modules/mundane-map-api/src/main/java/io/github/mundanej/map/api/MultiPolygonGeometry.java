package io.github.mundanej.map.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable packed multipolygon geometry with ring and polygon fencepost offsets. */
public final class MultiPolygonGeometry implements Geometry {
    private final CoordinateSequence coordinates;
    private final int[] ringOffsets;
    private final int[] polygonRingOffsets;

    private MultiPolygonGeometry(CoordinateSequence coordinates, int[] rings, int[] polygons) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.ringOffsets = rings.clone();
        this.polygonRingOffsets = polygons.clone();
        validate();
    }

    /** Creates a packed multipolygon. */
    public static MultiPolygonGeometry of(
            CoordinateSequence coordinates, int[] ringOffsets, int[] polygonRingOffsets) {
        return new MultiPolygonGeometry(
                coordinates,
                Objects.requireNonNull(ringOffsets, "ringOffsets"),
                Objects.requireNonNull(polygonRingOffsets, "polygonRingOffsets"));
    }

    /** Flattens ordered polygons, preserving exterior-then-hole order. */
    public static MultiPolygonGeometry ofPolygons(List<PolygonGeometry> polygons) {
        List<PolygonGeometry> copy = List.copyOf(Objects.requireNonNull(polygons, "polygons"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A multipolygon requires at least one polygon");
        }
        List<CoordinateSequence> rings = new ArrayList<>();
        int[] polygonOffsets = new int[copy.size() + 1];
        for (int index = 0; index < copy.size(); index++) {
            PolygonGeometry polygon = Objects.requireNonNull(copy.get(index), "polygon");
            rings.add(polygon.exterior());
            rings.addAll(polygon.holes());
            polygonOffsets[index + 1] = rings.size();
        }
        long count = 0;
        int[] offsets = new int[rings.size() + 1];
        for (int index = 0; index < rings.size(); index++) {
            count = Math.addExact(count, rings.get(index).size());
            if (count > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Too many multipolygon coordinates");
            }
            offsets[index + 1] = (int) count;
        }
        double[] packed = new double[Math.toIntExact(Math.multiplyExact(count, 2))];
        int target = 0;
        for (CoordinateSequence ring : rings) {
            double[] values = ring.toArray();
            System.arraycopy(values, 0, packed, target, values.length);
            target += values.length;
        }
        return new MultiPolygonGeometry(CoordinateSequence.of(packed), offsets, polygonOffsets);
    }

    /** Returns packed coordinates. */
    public CoordinateSequence coordinates() {
        return coordinates;
    }

    /** Returns the number of rings. */
    public int ringCount() {
        return ringOffsets.length - 1;
    }

    /** Returns the number of polygons. */
    public int polygonCount() {
        return polygonRingOffsets.length - 1;
    }

    /** Returns a coordinate fencepost offset. */
    public int ringOffset(int fenceIndex) {
        return ringOffsets[fenceIndex];
    }

    /** Returns a ring fencepost offset. */
    public int polygonRingOffset(int fenceIndex) {
        return polygonRingOffsets[fenceIndex];
    }

    /** Returns defensive coordinate fencepost offsets. */
    public int[] ringOffsets() {
        return ringOffsets.clone();
    }

    /** Returns defensive ring fencepost offsets. */
    public int[] polygonRingOffsets() {
        return polygonRingOffsets.clone();
    }

    @Override
    public Envelope envelope() {
        return coordinates.envelope();
    }

    private void validate() {
        if (ringOffsets.length < 2
                || ringOffsets[0] != 0
                || ringOffsets[ringOffsets.length - 1] != coordinates.size()) {
            throw new IllegalArgumentException("Ring offsets must span the exact coordinate count");
        }
        for (int index = 1; index < ringOffsets.length; index++) {
            int start = ringOffsets[index - 1];
            int end = ringOffsets[index];
            if (end - start < 4
                    || Double.compare(coordinates.x(start), coordinates.x(end - 1)) != 0
                    || Double.compare(coordinates.y(start), coordinates.y(end - 1)) != 0) {
                throw new IllegalArgumentException(
                        "Every polygon ring requires at least four coordinates and exact closure");
            }
        }
        if (polygonRingOffsets.length < 2
                || polygonRingOffsets[0] != 0
                || polygonRingOffsets[polygonRingOffsets.length - 1] != ringCount()) {
            throw new IllegalArgumentException("Polygon offsets must span the exact ring count");
        }
        for (int index = 1; index < polygonRingOffsets.length; index++) {
            if (polygonRingOffsets[index] <= polygonRingOffsets[index - 1]) {
                throw new IllegalArgumentException("Every polygon requires at least one ring");
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MultiPolygonGeometry value
                && coordinates.equals(value.coordinates)
                && Arrays.equals(ringOffsets, value.ringOffsets)
                && Arrays.equals(polygonRingOffsets, value.polygonRingOffsets);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * coordinates.hashCode() + Arrays.hashCode(ringOffsets))
                + Arrays.hashCode(polygonRingOffsets);
    }

    @Override
    public String toString() {
        return "MultiPolygonGeometry[coordinates="
                + coordinates
                + ", ringOffsets="
                + Arrays.toString(ringOffsets)
                + ", polygonRingOffsets="
                + Arrays.toString(polygonRingOffsets)
                + "]";
    }
}
