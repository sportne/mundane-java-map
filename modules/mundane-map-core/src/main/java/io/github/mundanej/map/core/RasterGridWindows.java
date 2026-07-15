package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import java.util.Objects;
import java.util.Optional;

/** Exact raster grid-edge and conservative visible-window operations. */
public final class RasterGridWindows {
    private RasterGridWindows() {}

    /**
     * Returns the minimum contiguous positive-overlap cell window.
     *
     * @param metadata placed raster metadata
     * @param visibleDisplayBounds visible bounds in the raster's exact display CRS
     * @return conservative source window, or empty for no positive-area overlap
     */
    public static Optional<RasterWindow> visibleWindow(
            RasterSourceMetadata metadata, Envelope visibleDisplayBounds) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(visibleDisplayBounds, "visibleDisplayBounds");
        RasterGridPlacement placement = requirePlacement(metadata);
        if (placement.kind() == RasterGridPlacement.Kind.AFFINE) {
            return affineVisibleWindow(metadata, visibleDisplayBounds, placement);
        }
        return axisAlignedVisibleWindow(metadata, visibleDisplayBounds);
    }

    private static Optional<RasterWindow> axisAlignedVisibleWindow(
            RasterSourceMetadata metadata, Envelope visibleDisplayBounds) {
        Envelope bounds = requireBounds(metadata);
        double minX = Math.max(bounds.minX(), visibleDisplayBounds.minX());
        double maxX = Math.min(bounds.maxX(), visibleDisplayBounds.maxX());
        double minY = Math.max(bounds.minY(), visibleDisplayBounds.minY());
        double maxY = Math.min(bounds.maxY(), visibleDisplayBounds.maxY());
        if (minX >= maxX || minY >= maxY) {
            return Optional.empty();
        }
        int startColumn = firstAscendingGreater(metadata, minX, 1, metadata.width()) - 1;
        int endColumn = firstAscendingAtLeast(metadata, maxX, 1, metadata.width());
        int startRow = firstDescendingLess(metadata, maxY, 1, metadata.height()) - 1;
        int endRow = firstDescendingAtMost(metadata, minY, 1, metadata.height());
        if (startColumn >= endColumn || startRow >= endRow) {
            return Optional.empty();
        }
        return Optional.of(
                new RasterWindow(
                        startColumn,
                        startRow,
                        Math.subtractExact(endColumn, startColumn),
                        Math.subtractExact(endRow, startRow)));
    }

    /**
     * Returns complete map edges for a wholly contained source window.
     *
     * @param metadata placed raster metadata
     * @param window wholly contained source window
     * @return conservative map envelope for the window's outer edges
     */
    public static Envelope mapBounds(RasterSourceMetadata metadata, RasterWindow window) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(window, "window");
        RasterGridPlacement placement = requirePlacement(metadata);
        if (window.endColumn() > metadata.width() || window.endRow() > metadata.height()) {
            throw new IllegalArgumentException(
                    "window must be wholly contained by metadata dimensions");
        }
        if (placement.kind() == RasterGridPlacement.Kind.AFFINE) {
            return affineMapBounds(window, placement.affineTransform().orElseThrow());
        }
        return new Envelope(
                columnEdge(metadata, window.column()),
                rowEdge(metadata, Math.toIntExact(window.endRow())),
                columnEdge(metadata, Math.toIntExact(window.endColumn())),
                rowEdge(metadata, window.row()));
    }

    /**
     * Plans a contained visible window's output density without inventing source detail.
     *
     * @param metadata placed raster metadata
     * @param window wholly contained source window
     * @param viewport current display viewport in the raster CRS
     * @return positive output dimensions capped to the source-window dimensions
     */
    public static OutputSize outputSize(
            RasterSourceMetadata metadata, RasterWindow window, MapViewport viewport) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(viewport, "viewport");
        if (window.endColumn() > metadata.width() || window.endRow() > metadata.height()) {
            throw new IllegalArgumentException(
                    "window must be wholly contained by metadata dimensions");
        }
        RasterGridPlacement placement = requirePlacement(metadata);
        int width;
        int height;
        if (placement.kind() == RasterGridPlacement.Kind.AFFINE) {
            RasterAffineTransform transform = placement.affineTransform().orElseThrow();
            width =
                    capVectorOutput(
                            window.width(),
                            transform.a(),
                            transform.d(),
                            viewport.worldUnitsPerPixel());
            height =
                    capVectorOutput(
                            window.height(),
                            transform.b(),
                            transform.e(),
                            viewport.worldUnitsPerPixel());
        } else {
            Envelope bounds = requireBounds(metadata);
            width =
                    capMapOutput(
                            window.width(),
                            bounds.width() / metadata.width(),
                            viewport.worldUnitsPerPixel());
            height =
                    capMapOutput(
                            window.height(),
                            bounds.height() / metadata.height(),
                            viewport.worldUnitsPerPixel());
        }
        return new OutputSize(width, height);
    }

    private static int capVectorOutput(
            int sourceSize, double first, double second, double worldUnitsPerPixel) {
        double absoluteFirst = Math.abs(first);
        double absoluteSecond = Math.abs(second);
        if (absoluteFirst >= worldUnitsPerPixel || absoluteSecond >= worldUnitsPerPixel) {
            return sourceSize;
        }
        return capMapOutput(
                sourceSize, Math.hypot(absoluteFirst, absoluteSecond), worldUnitsPerPixel);
    }

    private static int capMapOutput(
            int sourceSize, double mapBasisLength, double worldUnitsPerPixel) {
        if (!Double.isFinite(mapBasisLength) || mapBasisLength < 0.0) {
            throw new ArithmeticException("Raster map basis must be finite and non-negative");
        }
        if (mapBasisLength >= worldUnitsPerPixel) {
            return sourceSize;
        }
        double basisLength = mapBasisLength / worldUnitsPerPixel;
        double scaled = sourceSize * basisLength;
        if (!Double.isFinite(scaled)) {
            throw new ArithmeticException("Raster output density must be finite");
        }
        return Math.max(1, (int) Math.ceil(scaled));
    }

    /**
     * Positive output dimensions selected by screen-density planning.
     *
     * @param width positive output width
     * @param height positive output height
     */
    public record OutputSize(int width, int height) {
        /** Validates positive output dimensions. */
        public OutputSize {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Raster output dimensions must be positive");
            }
        }
    }

    private static Envelope requireBounds(RasterSourceMetadata metadata) {
        return metadata.mapBounds()
                .orElseThrow(
                        () -> new IllegalArgumentException("metadata.mapBounds must be present"));
    }

    private static RasterGridPlacement requirePlacement(RasterSourceMetadata metadata) {
        return metadata.gridPlacement()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "metadata.gridPlacement must be present"));
    }

    private static Envelope affineMapBounds(RasterWindow window, RasterAffineTransform transform) {
        double minColumn = window.column() - 0.5;
        double minRow = window.row() - 0.5;
        double maxColumn = window.endColumn() - 0.5;
        double maxRow = window.endRow() - 0.5;
        Coordinate northWest = transform.gridToMap(minColumn, minRow);
        Coordinate northEast = transform.gridToMap(maxColumn, minRow);
        Coordinate southEast = transform.gridToMap(maxColumn, maxRow);
        Coordinate southWest = transform.gridToMap(minColumn, maxRow);
        return envelope(northWest, northEast, southEast, southWest);
    }

    private static Optional<RasterWindow> affineVisibleWindow(
            RasterSourceMetadata metadata,
            Envelope visibleDisplayBounds,
            RasterGridPlacement placement) {
        Envelope bounds = requireBounds(metadata);
        double intersectionMinX = Math.max(bounds.minX(), visibleDisplayBounds.minX());
        double intersectionMaxX = Math.min(bounds.maxX(), visibleDisplayBounds.maxX());
        double intersectionMinY = Math.max(bounds.minY(), visibleDisplayBounds.minY());
        double intersectionMaxY = Math.min(bounds.maxY(), visibleDisplayBounds.maxY());
        if (intersectionMinX >= intersectionMaxX || intersectionMinY >= intersectionMaxY) {
            return Optional.empty();
        }

        RasterAffineTransform transform = placement.affineTransform().orElseThrow();
        Coordinate[] inverse = {
            transform.mapToGrid(new Coordinate(intersectionMinX, intersectionMaxY)),
            transform.mapToGrid(new Coordinate(intersectionMaxX, intersectionMaxY)),
            transform.mapToGrid(new Coordinate(intersectionMaxX, intersectionMinY)),
            transform.mapToGrid(new Coordinate(intersectionMinX, intersectionMinY))
        };
        double maximumMagnitude =
                Math.max(
                        Math.max(metadata.width() - 0.5, metadata.height() - 0.5),
                        maximumMagnitude(inverse));
        if (!(maximumMagnitude > 0.0) || !Double.isFinite(maximumMagnitude)) {
            throw new ArithmeticException("Affine raster normalization is not finite");
        }
        double normalization = Math.scalb(1.0, -Math.getExponent(maximumMagnitude));
        if (!(normalization > 0.0) || !Double.isFinite(normalization)) {
            throw new ArithmeticException("Affine raster normalization is not finite");
        }

        double[] x = new double[8];
        double[] y = new double[8];
        for (int index = 0; index < inverse.length; index++) {
            x[index] = checkedProduct(inverse[index].x(), normalization);
            y[index] = checkedProduct(inverse[index].y(), normalization);
        }
        int count = inverse.length;
        double minimumColumn = checkedProduct(-0.5, normalization);
        double maximumColumn = checkedProduct(metadata.width() - 0.5, normalization);
        double minimumRow = checkedProduct(-0.5, normalization);
        double maximumRow = checkedProduct(metadata.height() - 0.5, normalization);
        count = clip(x, y, count, 0, minimumColumn);
        count = clip(x, y, count, 1, maximumColumn);
        count = clip(x, y, count, 2, minimumRow);
        count = clip(x, y, count, 3, maximumRow);
        if (count < 3 || !hasPositiveArea(x, y, count)) {
            return Optional.empty();
        }

        double clippedMinX = Double.POSITIVE_INFINITY;
        double clippedMaxX = Double.NEGATIVE_INFINITY;
        double clippedMinY = Double.POSITIVE_INFINITY;
        double clippedMaxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            clippedMinX = Math.min(clippedMinX, x[index]);
            clippedMaxX = Math.max(clippedMaxX, x[index]);
            clippedMinY = Math.min(clippedMinY, y[index]);
            clippedMaxY = Math.max(clippedMaxY, y[index]);
        }
        clippedMinX = checkedQuotient(clippedMinX, normalization);
        clippedMaxX = checkedQuotient(clippedMaxX, normalization);
        clippedMinY = checkedQuotient(clippedMinY, normalization);
        clippedMaxY = checkedQuotient(clippedMaxY, normalization);
        requireWithinGrid(clippedMinX, clippedMaxX, -0.5, metadata.width() - 0.5);
        requireWithinGrid(clippedMinY, clippedMaxY, -0.5, metadata.height() - 0.5);

        int startColumn = outwardStart(clippedMinX, metadata.width());
        int endColumn = outwardEnd(clippedMaxX, metadata.width());
        int startRow = outwardStart(clippedMinY, metadata.height());
        int endRow = outwardEnd(clippedMaxY, metadata.height());
        if (startColumn >= endColumn || startRow >= endRow) {
            return Optional.empty();
        }
        return Optional.of(
                new RasterWindow(
                        startColumn,
                        startRow,
                        Math.subtractExact(endColumn, startColumn),
                        Math.subtractExact(endRow, startRow)));
    }

    private static Envelope envelope(Coordinate... coordinates) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Coordinate coordinate : coordinates) {
            minX = Math.min(minX, coordinate.x());
            minY = Math.min(minY, coordinate.y());
            maxX = Math.max(maxX, coordinate.x());
            maxY = Math.max(maxY, coordinate.y());
        }
        return new Envelope(minX, minY, maxX, maxY);
    }

    private static double maximumMagnitude(Coordinate[] coordinates) {
        double maximum = 0.0;
        for (Coordinate coordinate : coordinates) {
            maximum =
                    Math.max(maximum, Math.max(Math.abs(coordinate.x()), Math.abs(coordinate.y())));
        }
        return maximum;
    }

    private static int clip(double[] x, double[] y, int count, int boundary, double boundaryValue) {
        if (count == 0) {
            return 0;
        }
        double[] outputX = new double[x.length];
        double[] outputY = new double[y.length];
        int outputCount = 0;
        int previous = count - 1;
        boolean previousInside = inside(x[previous], y[previous], boundary, boundaryValue);
        for (int current = 0; current < count; current++) {
            boolean currentInside = inside(x[current], y[current], boundary, boundaryValue);
            if (currentInside != previousInside) {
                double[] intersection =
                        intersection(
                                x[previous],
                                y[previous],
                                x[current],
                                y[current],
                                boundary,
                                boundaryValue);
                outputCount =
                        appendDistinct(
                                outputX, outputY, outputCount, intersection[0], intersection[1]);
            }
            if (currentInside) {
                outputCount = appendDistinct(outputX, outputY, outputCount, x[current], y[current]);
            }
            previous = current;
            previousInside = currentInside;
        }
        if (outputCount > 1
                && outputX[0] == outputX[outputCount - 1]
                && outputY[0] == outputY[outputCount - 1]) {
            outputCount--;
        }
        System.arraycopy(outputX, 0, x, 0, outputCount);
        System.arraycopy(outputY, 0, y, 0, outputCount);
        return outputCount;
    }

    private static boolean inside(double x, double y, int boundary, double value) {
        return switch (boundary) {
            case 0 -> x >= value;
            case 1 -> x <= value;
            case 2 -> y >= value;
            case 3 -> y <= value;
            default -> throw new AssertionError("Unknown clipping boundary");
        };
    }

    private static double[] intersection(
            double startX, double startY, double endX, double endY, int boundary, double value) {
        double x;
        double y;
        if (boundary < 2) {
            double ratio = (value - startX) / (endX - startX);
            x = value;
            y = Math.fma(ratio, endY - startY, startY);
        } else {
            double ratio = (value - startY) / (endY - startY);
            x = Math.fma(ratio, endX - startX, startX);
            y = value;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new ArithmeticException("Affine raster clip intersection is not finite");
        }
        return new double[] {x, y};
    }

    private static int appendDistinct(
            double[] x, double[] y, int count, double nextX, double nextY) {
        if (count > 0 && x[count - 1] == nextX && y[count - 1] == nextY) {
            return count;
        }
        if (count == x.length) {
            throw new ArithmeticException("Affine raster clip exceeded its vertex bound");
        }
        x[count] = nextX;
        y[count] = nextY;
        return count + 1;
    }

    private static boolean hasPositiveArea(double[] x, double[] y, int count) {
        double doubledArea = 0.0;
        for (int index = 1; index < count - 1; index++) {
            double firstX = x[index] - x[0];
            double firstY = y[index] - y[0];
            double secondX = x[index + 1] - x[0];
            double secondY = y[index + 1] - y[0];
            doubledArea = Math.fma(firstX, secondY, doubledArea - firstY * secondX);
        }
        if (!Double.isFinite(doubledArea)) {
            throw new ArithmeticException("Affine raster clip area is not finite");
        }
        return Math.abs(doubledArea) > 0.0;
    }

    private static int outwardStart(double minimum, int dimension) {
        double value = Math.floor(Math.nextDown(minimum + 0.5));
        return clampedInteger(value, dimension);
    }

    private static int outwardEnd(double maximum, int dimension) {
        double value = Math.ceil(Math.nextUp(maximum + 0.5));
        return clampedInteger(value, dimension);
    }

    private static int clampedInteger(double value, int dimension) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Affine raster integer bound is not finite");
        }
        if (value <= 0.0) {
            return 0;
        }
        if (value >= dimension) {
            return dimension;
        }
        return (int) value;
    }

    private static void requireWithinGrid(
            double minimum, double maximum, double gridMinimum, double gridMaximum) {
        if (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum < gridMinimum
                || maximum > gridMaximum
                || minimum > maximum) {
            throw new ArithmeticException("Affine raster clipped bounds left the source grid");
        }
    }

    private static double checkedProduct(double first, double second) {
        double result = first * second;
        if (!Double.isFinite(result)) {
            throw new ArithmeticException("Affine raster normalized coordinate is not finite");
        }
        return result;
    }

    private static double checkedQuotient(double value, double divisor) {
        double result = value / divisor;
        if (!Double.isFinite(result)) {
            throw new ArithmeticException("Affine raster coordinate rescaling is not finite");
        }
        return result;
    }

    private static double columnEdge(RasterSourceMetadata metadata, int edge) {
        Envelope bounds = requireBounds(metadata);
        if (edge == 0) {
            return bounds.minX();
        }
        if (edge == metadata.width()) {
            return bounds.maxX();
        }
        return checkedEdge(bounds.minX(), bounds.maxX(), (double) edge / metadata.width());
    }

    private static double rowEdge(RasterSourceMetadata metadata, int edge) {
        Envelope bounds = requireBounds(metadata);
        if (edge == 0) {
            return bounds.maxY();
        }
        if (edge == metadata.height()) {
            return bounds.minY();
        }
        return checkedEdge(bounds.maxY(), bounds.minY(), (double) edge / metadata.height());
    }

    private static double checkedEdge(double start, double end, double ratio) {
        double value;
        if (Math.copySign(1.0, start) == Math.copySign(1.0, end)) {
            value = start + (end - start) * ratio;
        } else {
            value = start * (1.0 - ratio) + end * ratio;
        }
        if (!Double.isFinite(value)
                || value < Math.min(start, end)
                || value > Math.max(start, end)) {
            throw new ArithmeticException("Raster grid edge is not finite and contained");
        }
        return value;
    }

    private static int firstAscendingGreater(
            RasterSourceMetadata metadata, double value, int low, int high) {
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (columnEdge(metadata, middle) > value) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static int firstAscendingAtLeast(
            RasterSourceMetadata metadata, double value, int low, int high) {
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (columnEdge(metadata, middle) >= value) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static int firstDescendingLess(
            RasterSourceMetadata metadata, double value, int low, int high) {
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (rowEdge(metadata, middle) < value) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static int firstDescendingAtMost(
            RasterSourceMetadata metadata, double value, int low, int high) {
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (rowEdge(metadata, middle) <= value) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }
}
