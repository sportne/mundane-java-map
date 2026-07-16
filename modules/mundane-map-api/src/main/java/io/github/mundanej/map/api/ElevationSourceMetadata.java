package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable metadata for an axis-aligned regularly sampled elevation grid.
 *
 * <p>The bounds identify sample centers rather than pixel edges. Columns increase from west to
 * east, rows increase from north to south, and storage is row-major.
 *
 * @param identity bounded logical source identity
 * @param columnCount number of west-to-east sample columns, at least two
 * @param rowCount number of north-to-south sample rows, at least two
 * @param sampleBounds finite positive-span bounds of the first and last sample centers
 * @param crs required recognized or retained-unknown coordinate-reference metadata
 * @param elevationUnit declared vertical sample unit
 */
public record ElevationSourceMetadata(
        SourceIdentity identity,
        int columnCount,
        int rowCount,
        Envelope sampleBounds,
        CrsMetadata crs,
        ElevationUnit elevationUnit) {
    /** Validates dimensions, bounds, spacing, and adjacent-post representability. */
    public ElevationSourceMetadata {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(sampleBounds, "sampleBounds");
        Objects.requireNonNull(crs, "crs");
        Objects.requireNonNull(elevationUnit, "elevationUnit");
        if (columnCount < 2) {
            throw new IllegalArgumentException("columnCount must be at least two");
        }
        if (rowCount < 2) {
            throw new IllegalArgumentException("rowCount must be at least two");
        }
        requirePositiveSpacing(sampleBounds.width(), columnCount, "column");
        requirePositiveSpacing(sampleBounds.height(), rowCount, "row");
        requireAdjacentPosts(sampleBounds.minX(), sampleBounds.maxX(), columnCount, "column");
        requireAdjacentPosts(sampleBounds.maxY(), sampleBounds.minY(), rowCount, "row");
    }

    /**
     * Returns the checked number of samples.
     *
     * @return rows multiplied by columns as a positive long
     */
    public long sampleCount() {
        return Math.multiplyExact((long) columnCount, rowCount);
    }

    /**
     * Returns the positive spacing between adjacent columns.
     *
     * @return spacing in source-CRS coordinate units
     */
    public double columnSpacing() {
        return sampleBounds.width() / (columnCount - 1L);
    }

    /**
     * Returns the positive magnitude of spacing between adjacent rows.
     *
     * @return spacing in source-CRS coordinate units
     */
    public double rowSpacing() {
        return sampleBounds.height() / (rowCount - 1L);
    }

    /**
     * Returns the coordinate of one sample post.
     *
     * @param column zero-based west-to-east column
     * @param row zero-based north-to-south row
     * @return exact endpoint or convexly interpolated source coordinate
     * @throws IndexOutOfBoundsException if either index is outside the grid
     */
    public Coordinate sampleCoordinate(int column, int row) {
        requireIndex(column, columnCount, "column");
        requireIndex(row, rowCount, "row");
        return new Coordinate(
                interpolate(column, columnCount, sampleBounds.minX(), sampleBounds.maxX()),
                interpolate(row, rowCount, sampleBounds.maxY(), sampleBounds.minY()));
    }

    private static void requirePositiveSpacing(double span, int count, String axis) {
        double spacing = span / (count - 1L);
        if (!(spacing > 0.0) || !Double.isFinite(spacing)) {
            throw new IllegalArgumentException(axis + " spacing must be finite and positive");
        }
    }

    private static void requireAdjacentPosts(double first, double last, int count, String axis) {
        if (interpolate(1, count, first, last) == first
                || interpolate(count - 2, count, first, last) == last) {
            throw new IllegalArgumentException(axis + " adjacent posts are not representable");
        }
    }

    private static double interpolate(int index, int count, double first, double last) {
        if (index == 0) {
            return first;
        }
        if (index == count - 1) {
            return last;
        }
        double ratio = (double) index / (count - 1L);
        double value = Math.fma(ratio, last - first, first);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Sample coordinate must be finite");
        }
        return value;
    }

    private static void requireIndex(int index, int count, String name) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(name + " is outside the elevation grid");
        }
    }
}
