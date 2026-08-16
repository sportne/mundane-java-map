package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * An ordinate-aware primitive or homogeneous multi-geometry backed by packed primitive arrays.
 *
 * <p>Point and multi-point values use no offsets. Line strings use {@code [0, size]}; multilines
 * use one coordinate fencepost per part. Polygons use coordinate fenceposts in {@link
 * #partOffsets()} and {@code [0, ringCount]} in {@link #polygonPartOffsets()}; multipolygons use
 * both arrays as ring and polygon fenceposts.
 */
public final class DimensionalGeometry implements Geometry {
    private final GeometryKind kind;
    private final CoordinateSequence coordinates;
    private final int[] partOffsets;
    private final int[] polygonPartOffsets;

    private DimensionalGeometry(
            GeometryKind kind,
            CoordinateSequence coordinates,
            int[] partOffsets,
            int[] polygonPartOffsets,
            GeometryLimits limits) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.partOffsets = Objects.requireNonNull(partOffsets, "partOffsets").clone();
        this.polygonPartOffsets =
                Objects.requireNonNull(polygonPartOffsets, "polygonPartOffsets").clone();
        validate(Objects.requireNonNull(limits, "limits"));
    }

    /**
     * Creates one ordinate-aware point.
     *
     * @param coordinate exactly one packed position
     * @return immutable point
     */
    public static DimensionalGeometry point(CoordinateSequence coordinate) {
        return point(coordinate, GeometryLimits.DEFAULT);
    }

    /**
     * Creates one bounded ordinate-aware point.
     *
     * @param coordinate exactly one packed position
     * @param limits construction limits
     * @return immutable point
     */
    public static DimensionalGeometry point(CoordinateSequence coordinate, GeometryLimits limits) {
        return new DimensionalGeometry(
                GeometryKind.POINT, coordinate, new int[0], new int[0], limits);
    }

    /**
     * Creates one ordinate-aware line string.
     *
     * @param coordinates at least two packed positions
     * @return immutable line string
     */
    public static DimensionalGeometry lineString(CoordinateSequence coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        return new DimensionalGeometry(
                GeometryKind.LINE_STRING,
                coordinates,
                new int[] {0, coordinates.size()},
                new int[0],
                GeometryLimits.DEFAULT);
    }

    /**
     * Creates one ordinate-aware polygon.
     *
     * @param coordinates packed rings
     * @param ringOffsets coordinate fenceposts
     * @return immutable polygon
     */
    public static DimensionalGeometry polygon(CoordinateSequence coordinates, int[] ringOffsets) {
        Objects.requireNonNull(ringOffsets, "ringOffsets");
        return new DimensionalGeometry(
                GeometryKind.POLYGON,
                coordinates,
                ringOffsets,
                new int[] {0, ringOffsets.length - 1},
                GeometryLimits.DEFAULT);
    }

    /**
     * Creates an ordinate-aware multipoint.
     *
     * @param coordinates one or more packed positions
     * @return immutable multipoint
     */
    public static DimensionalGeometry multiPoint(CoordinateSequence coordinates) {
        return new DimensionalGeometry(
                GeometryKind.MULTI_POINT,
                coordinates,
                new int[0],
                new int[0],
                GeometryLimits.DEFAULT);
    }

    /**
     * Creates an ordinate-aware multiline.
     *
     * @param coordinates packed positions
     * @param partOffsets coordinate fenceposts
     * @return immutable multiline
     */
    public static DimensionalGeometry multiLineString(
            CoordinateSequence coordinates, int[] partOffsets) {
        return new DimensionalGeometry(
                GeometryKind.MULTI_LINE_STRING,
                coordinates,
                partOffsets,
                new int[0],
                GeometryLimits.DEFAULT);
    }

    /**
     * Creates a bounded ordinate-aware multipolygon.
     *
     * @param coordinates packed positions
     * @param ringOffsets coordinate fenceposts
     * @param polygonRingOffsets ring fenceposts
     * @param limits construction limits
     * @return immutable multipolygon
     */
    public static DimensionalGeometry multiPolygon(
            CoordinateSequence coordinates,
            int[] ringOffsets,
            int[] polygonRingOffsets,
            GeometryLimits limits) {
        return new DimensionalGeometry(
                GeometryKind.MULTI_POLYGON, coordinates, ringOffsets, polygonRingOffsets, limits);
    }

    @Override
    public GeometryKind kind() {
        return kind;
    }

    @Override
    public GeometryDimension dimension() {
        return coordinates.dimension();
    }

    /**
     * Returns the immutable packed coordinates.
     *
     * @return coordinate sequence
     */
    public CoordinateSequence coordinates() {
        return coordinates;
    }

    /**
     * Returns coordinate or ring fenceposts.
     *
     * @return defensive offset copy
     */
    public int[] partOffsets() {
        return partOffsets.clone();
    }

    /**
     * Returns polygon-to-ring fenceposts.
     *
     * @return defensive offset copy
     */
    public int[] polygonPartOffsets() {
        return polygonPartOffsets.clone();
    }

    /**
     * Returns the number of line parts or polygon rings.
     *
     * @return part count, or zero for points
     */
    public int partCount() {
        return partOffsets.length == 0 ? 0 : partOffsets.length - 1;
    }

    @Override
    public Envelope envelope() {
        return coordinates.envelope();
    }

    private void validate(GeometryLimits limits) {
        if (kind == GeometryKind.GEOMETRY_COLLECTION) {
            throw new IllegalArgumentException("Use GeometryCollection for heterogeneous values");
        }
        if (coordinates.isEmpty()) {
            throw new IllegalArgumentException("Use EmptyGeometry for an empty value");
        }
        if (coordinates.size() > limits.maxCoordinates()) {
            throw GeometryException.limit(
                    "maxCoordinates", coordinates.size(), limits.maxCoordinates());
        }
        switch (kind) {
            case POINT -> requirePoint();
            case LINE_STRING -> requireLines(false);
            case POLYGON -> requirePolygons(false);
            case MULTI_POINT -> requireNoOffsets();
            case MULTI_LINE_STRING -> requireLines(true);
            case MULTI_POLYGON -> requirePolygons(true);
            case GEOMETRY_COLLECTION ->
                    throw new IllegalArgumentException(
                            "Use GeometryCollection for heterogeneous values");
        }
        if (partCount() > limits.maxParts()) {
            throw GeometryException.limit("maxParts", partCount(), limits.maxParts());
        }
    }

    private void requirePoint() {
        requireNoOffsets();
        if (coordinates.size() != 1) {
            throw new IllegalArgumentException("A point requires exactly one position");
        }
    }

    private void requireNoOffsets() {
        if (partOffsets.length != 0 || polygonPartOffsets.length != 0) {
            throw new IllegalArgumentException("Point geometry must not contain part offsets");
        }
    }

    private void requireLines(boolean multiple) {
        if (polygonPartOffsets.length != 0) {
            throw new IllegalArgumentException("Line geometry must not contain polygon offsets");
        }
        validateFenceposts(partOffsets, coordinates.size(), "Line part");
        if (!multiple && partOffsets.length != 2) {
            throw new IllegalArgumentException("A line string requires exactly one part");
        }
        for (int index = 1; index < partOffsets.length; index++) {
            if (partOffsets[index] - partOffsets[index - 1] < 2) {
                throw new IllegalArgumentException(
                        "Every line part requires at least two positions");
            }
        }
    }

    private void requirePolygons(boolean multiple) {
        validateFenceposts(partOffsets, coordinates.size(), "Polygon ring");
        validateFenceposts(polygonPartOffsets, partCount(), "Polygon");
        if (!multiple && polygonPartOffsets.length != 2) {
            throw new IllegalArgumentException("A polygon requires exactly one polygon part");
        }
        for (int index = 1; index < partOffsets.length; index++) {
            int start = partOffsets[index - 1];
            int end = partOffsets[index];
            if (end - start < 4 || !positionsEqual(start, end - 1)) {
                throw new IllegalArgumentException(
                        "Every polygon ring requires four positions and exact closure");
            }
        }
    }

    private boolean positionsEqual(int first, int second) {
        int stride = dimension().stride();
        double[] packed = coordinates.toArray();
        for (int ordinate = 0; ordinate < stride; ordinate++) {
            if (Double.compare(
                            packed[first * stride + ordinate], packed[second * stride + ordinate])
                    != 0) {
                return false;
            }
        }
        return true;
    }

    private static void validateFenceposts(int[] offsets, int size, String role) {
        if (offsets.length < 2 || offsets[0] != 0 || offsets[offsets.length - 1] != size) {
            throw new IllegalArgumentException(role + " offsets must span the exact value count");
        }
        for (int index = 1; index < offsets.length; index++) {
            if (offsets[index] <= offsets[index - 1]) {
                throw new IllegalArgumentException(role + " offsets must increase exactly");
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DimensionalGeometry geometry
                && kind == geometry.kind
                && coordinates.equals(geometry.coordinates)
                && Arrays.equals(partOffsets, geometry.partOffsets)
                && Arrays.equals(polygonPartOffsets, geometry.polygonPartOffsets);
    }

    @Override
    public int hashCode() {
        int result = 31 * kind.hashCode() + coordinates.hashCode();
        result = 31 * result + Arrays.hashCode(partOffsets);
        return 31 * result + Arrays.hashCode(polygonPartOffsets);
    }

    @Override
    public String toString() {
        return "DimensionalGeometry[kind="
                + kind
                + ", coordinates="
                + coordinates
                + ", partOffsets="
                + Arrays.toString(partOffsets)
                + ", polygonPartOffsets="
                + Arrays.toString(polygonPartOffsets)
                + "]";
    }
}
