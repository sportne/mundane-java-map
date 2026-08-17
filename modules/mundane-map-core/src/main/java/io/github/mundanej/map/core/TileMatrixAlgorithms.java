package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded deterministic coordinate, envelope, clipping, and coverage algorithms for tile matrices.
 */
public final class TileMatrixAlgorithms {
    private TileMatrixAlgorithms() {}

    /**
     * Returns the complete nominal matrix extent in library x/y presentation.
     *
     * @param set owning set
     * @param matrixIdentifier exact matrix identifier
     * @return immutable matrix envelope
     */
    public static Envelope matrixEnvelope(TileMatrixSet set, String matrixIdentifier) {
        Objects.requireNonNull(set, "set");
        return matrixEnvelope(set.orderedAxes(), set.matrix(matrixIdentifier));
    }

    static Envelope matrixEnvelope(TileMatrixAxisOrder axes, TileMatrix matrix) {
        double originX = originX(axes, matrix);
        double originY = originY(axes, matrix);
        double width = worldTileWidth(matrix, 1) * matrix.matrixWidth();
        double height = worldTileHeight(matrix) * matrix.matrixHeight();
        return switch (matrix.cornerOfOrigin()) {
            case TOP_LEFT -> new Envelope(originX, originY - height, originX + width, originY);
            case BOTTOM_LEFT -> new Envelope(originX, originY, originX + width, originY + height);
        };
    }

    /**
     * Resolves the physical tile containing a coordinate under the matrix's half-open partition.
     *
     * <p>The set's maximum x/y boundary is assigned to its final row or column. Other shared tile
     * boundaries are assigned to the tile on the increasing-axis side.
     *
     * @param set owning set
     * @param matrixIdentifier exact matrix identifier
     * @param coordinate coordinate in library x/y presentation
     * @return valid physical tile address
     * @throws TileMatrixException when the coordinate is outside set or matrix bounds
     */
    public static TileMatrixIndex tileAt(
            TileMatrixSet set, String matrixIdentifier, Coordinate coordinate) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(coordinate, "coordinate");
        TileMatrix matrix = set.matrix(matrixIdentifier);
        Envelope supported = intersection(set.boundingBox(), matrixEnvelope(set, matrixIdentifier));
        if (supported == null || !supported.contains(coordinate)) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        long row = rowAt(set.orderedAxes(), matrix, coordinate.y());
        int coalesce = matrix.coalesce(row);
        long column = columnAt(set.orderedAxes(), matrix, coordinate.x(), row, coalesce);
        return new TileMatrixIndex(matrix.identifier(), row, column);
    }

    /**
     * Returns one physical tile envelope in library x/y presentation.
     *
     * @param set owning set
     * @param index exact physical tile address
     * @return immutable tile envelope
     * @throws TileMatrixException when the row or column is outside the matrix
     */
    public static Envelope tileEnvelope(TileMatrixSet set, TileMatrixIndex index) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(index, "index");
        TileMatrix matrix = set.matrix(index.matrixIdentifier());
        if (index.row() >= matrix.matrixHeight()
                || index.column() >= matrix.columnCount(index.row())) {
            throw failure(
                    "TILE_MATRIX_INDEX_OUT_OF_DOMAIN",
                    Map.of(
                            "column", Long.toString(index.column()),
                            "row", Long.toString(index.row())));
        }
        int coalesce = matrix.coalesce(index.row());
        double width = worldTileWidth(matrix, coalesce);
        double height = worldTileHeight(matrix);
        double minimumX = originX(set.orderedAxes(), matrix) + index.column() * width;
        double originY = originY(set.orderedAxes(), matrix);
        double minimumY;
        if (matrix.cornerOfOrigin() == TileMatrixCorner.TOP_LEFT) {
            minimumY = originY - (index.row() + 1.0) * height;
        } else {
            minimumY = originY + index.row() * height;
        }
        return new Envelope(minimumX, minimumY, minimumX + width, minimumY + height);
    }

    /**
     * Clips and enumerates one ordinary non-wrapping query envelope.
     *
     * @param set owning set
     * @param matrixIdentifier exact matrix identifier
     * @param request query in library x/y presentation
     * @param limits prospective materialization limit
     * @return complete atomic coverage result
     * @throws TileMatrixException before publication when enumeration exceeds its limit
     */
    public static TileCoverage coverage(
            TileMatrixSet set,
            String matrixIdentifier,
            Envelope request,
            TileCoverageLimits limits) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(limits, "limits");
        TileMatrix matrix = set.matrix(matrixIdentifier);
        Envelope clipped = intersection(request, set.boundingBox());
        if (clipped != null) {
            clipped = intersection(clipped, matrixEnvelope(set, matrixIdentifier));
        }
        if (clipped == null) {
            return new TileCoverage(TileCoverageStatus.OUTSIDE, List.of(), List.of());
        }
        List<TileMatrixIndex> tiles = enumerate(set, matrix, clipped, limits.maximumTiles());
        TileCoverageStatus status =
                clipped.equals(request) ? TileCoverageStatus.COMPLETE : TileCoverageStatus.CLIPPED;
        return new TileCoverage(status, List.of(clipped), tiles);
    }

    /**
     * Enumerates a geographic request that explicitly crosses the set's horizontal seam.
     *
     * <p>{@code west} must be greater than {@code east}; no implicit longitude wrapping or CRS
     * inference occurs. Both ordinates must lie in the set bounding box. Duplicate low-resolution
     * tiles touched by both parts are returned once in deterministic first-part order.
     *
     * @param set cyclic horizontal set
     * @param matrixIdentifier exact matrix identifier
     * @param west western part's inclusive minimum x
     * @param south inclusive minimum y
     * @param east eastern part's inclusive maximum x
     * @param north inclusive maximum y
     * @param limits prospective combined materialization limit
     * @return complete atomic seam-split coverage
     */
    public static TileCoverage coverageAcrossHorizontalSeam(
            TileMatrixSet set,
            String matrixIdentifier,
            double west,
            double south,
            double east,
            double north,
            TileCoverageLimits limits) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(limits, "limits");
        Envelope bounds = set.boundingBox();
        if (!Double.isFinite(west)
                || !Double.isFinite(south)
                || !Double.isFinite(east)
                || !Double.isFinite(north)
                || west <= east
                || south > north) {
            throw failure("TILE_MATRIX_SEAM_RANGE_INVALID", Map.of());
        }
        if (west < bounds.minX()
                || west > bounds.maxX()
                || east < bounds.minX()
                || east > bounds.maxX()) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        Envelope westPart = new Envelope(west, south, bounds.maxX(), north);
        Envelope eastPart = new Envelope(bounds.minX(), south, east, north);
        TileCoverage first = coverage(set, matrixIdentifier, westPart, limits);
        TileCoverage second = coverage(set, matrixIdentifier, eastPart, limits);
        if (first.status() == TileCoverageStatus.OUTSIDE
                && second.status() == TileCoverageStatus.OUTSIDE) {
            return new TileCoverage(TileCoverageStatus.OUTSIDE, List.of(), List.of());
        }
        LinkedHashSet<TileMatrixIndex> combined = new LinkedHashSet<>(first.tiles());
        combined.addAll(second.tiles());
        if (combined.size() > limits.maximumTiles()) {
            throw limit(limits.maximumTiles());
        }
        List<Envelope> intersections = new ArrayList<>(2);
        intersections.addAll(first.intersections());
        intersections.addAll(second.intersections());
        TileCoverageStatus status =
                first.status() == TileCoverageStatus.COMPLETE
                                && second.status() == TileCoverageStatus.COMPLETE
                        ? TileCoverageStatus.COMPLETE
                        : TileCoverageStatus.CLIPPED;
        return new TileCoverage(status, intersections, List.copyOf(combined));
    }

    private static List<TileMatrixIndex> enumerate(
            TileMatrixSet set, TileMatrix matrix, Envelope clipped, int maximumTiles) {
        long firstRow = rowAt(set.orderedAxes(), matrix, verticalStart(matrix, clipped));
        long lastRow =
                clipped.height() == 0
                        ? rowAt(set.orderedAxes(), matrix, verticalEnd(matrix, clipped))
                        : rowBefore(set.orderedAxes(), matrix, verticalEnd(matrix, clipped));
        long rows;
        try {
            rows = Math.addExact(Math.subtractExact(lastRow, firstRow), 1L);
        } catch (ArithmeticException exception) {
            throw limit(maximumTiles);
        }
        if (rows > maximumTiles) {
            throw limit(maximumTiles);
        }
        List<TileMatrixIndex> result = new ArrayList<>((int) rows);
        for (long row = firstRow; row <= lastRow; row++) {
            int coalesce = matrix.coalesce(row);
            long firstColumn = columnAt(set.orderedAxes(), matrix, clipped.minX(), row, coalesce);
            long lastColumn =
                    clipped.width() == 0
                            ? columnAt(set.orderedAxes(), matrix, clipped.maxX(), row, coalesce)
                            : columnBefore(
                                    set.orderedAxes(), matrix, clipped.maxX(), row, coalesce);
            long columns = lastColumn - firstColumn + 1L;
            if (columns > maximumTiles - result.size()) {
                throw limit(maximumTiles);
            }
            for (long column = firstColumn; column <= lastColumn; column++) {
                result.add(new TileMatrixIndex(matrix.identifier(), row, column));
            }
        }
        return List.copyOf(result);
    }

    private static double verticalStart(TileMatrix matrix, Envelope envelope) {
        return matrix.cornerOfOrigin() == TileMatrixCorner.TOP_LEFT
                ? envelope.maxY()
                : envelope.minY();
    }

    private static double verticalEnd(TileMatrix matrix, Envelope envelope) {
        if (envelope.height() == 0) {
            return envelope.minY();
        }
        return matrix.cornerOfOrigin() == TileMatrixCorner.TOP_LEFT
                ? envelope.minY()
                : envelope.maxY();
    }

    private static long rowAt(TileMatrixAxisOrder axes, TileMatrix matrix, double y) {
        double origin = originY(axes, matrix);
        double offset =
                matrix.cornerOfOrigin() == TileMatrixCorner.TOP_LEFT ? origin - y : y - origin;
        return boundedIndex(offset, worldTileHeight(matrix), matrix.matrixHeight());
    }

    private static long rowBefore(TileMatrixAxisOrder axes, TileMatrix matrix, double y) {
        double origin = originY(axes, matrix);
        double offset =
                matrix.cornerOfOrigin() == TileMatrixCorner.TOP_LEFT ? origin - y : y - origin;
        return boundedIndexBefore(offset, worldTileHeight(matrix), matrix.matrixHeight());
    }

    private static long columnAt(
            TileMatrixAxisOrder axes, TileMatrix matrix, double x, long row, int coalesce) {
        double offset = x - originX(axes, matrix);
        return boundedIndex(offset, worldTileWidth(matrix, coalesce), matrix.columnCount(row));
    }

    private static long columnBefore(
            TileMatrixAxisOrder axes, TileMatrix matrix, double x, long row, int coalesce) {
        double offset = x - originX(axes, matrix);
        return boundedIndexBefore(
                offset, worldTileWidth(matrix, coalesce), matrix.columnCount(row));
    }

    private static long boundedIndex(double offset, double span, long count) {
        if (!Double.isFinite(offset) || offset < 0) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        double quotient = offset / span;
        if (!Double.isFinite(quotient) || quotient > count) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        if (Double.compare(quotient, (double) count) == 0) {
            return count - 1;
        }
        long index = (long) StrictMath.floor(quotient);
        if (index < 0 || index >= count) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        return index;
    }

    private static long boundedIndexBefore(double offset, double span, long count) {
        if (!Double.isFinite(offset) || offset <= 0) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        double quotient = offset / span;
        if (!Double.isFinite(quotient) || quotient > count) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        long index = (long) StrictMath.floor(Math.nextDown(quotient));
        if (index < 0 || index >= count) {
            throw failure("TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN", Map.of());
        }
        return index;
    }

    private static double worldTileWidth(TileMatrix matrix, int coalesce) {
        return matrix.cellSize() * matrix.tileWidth() * coalesce;
    }

    private static double worldTileHeight(TileMatrix matrix) {
        return matrix.cellSize() * matrix.tileHeight();
    }

    private static double originX(TileMatrixAxisOrder axes, TileMatrix matrix) {
        return axes == TileMatrixAxisOrder.XY
                ? matrix.pointOfOrigin().x()
                : matrix.pointOfOrigin().y();
    }

    private static double originY(TileMatrixAxisOrder axes, TileMatrix matrix) {
        return axes == TileMatrixAxisOrder.XY
                ? matrix.pointOfOrigin().y()
                : matrix.pointOfOrigin().x();
    }

    private static Envelope intersection(Envelope first, Envelope second) {
        double minX = Math.max(first.minX(), second.minX());
        double minY = Math.max(first.minY(), second.minY());
        double maxX = Math.min(first.maxX(), second.maxX());
        double maxY = Math.min(first.maxY(), second.maxY());
        return minX <= maxX && minY <= maxY ? new Envelope(minX, minY, maxX, maxY) : null;
    }

    private static TileMatrixException limit(int maximum) {
        return failure(
                "TILE_MATRIX_COVERAGE_LIMIT", Map.of("maximumTiles", Integer.toString(maximum)));
    }

    private static TileMatrixException failure(String code, Map<String, String> context) {
        return new TileMatrixException(new TileMatrixProblem(code, context));
    }
}
