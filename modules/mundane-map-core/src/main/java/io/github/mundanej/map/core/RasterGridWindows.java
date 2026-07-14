package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import java.util.Objects;
import java.util.Optional;

/** Exact axis-aligned raster grid-edge and visible-window operations. */
public final class RasterGridWindows {
    private RasterGridWindows() {}

    /** Returns the minimum contiguous positive-overlap cell window. */
    public static Optional<RasterWindow> visibleWindow(
            RasterSourceMetadata metadata, Envelope visibleDisplayBounds) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(visibleDisplayBounds, "visibleDisplayBounds");
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

    /** Returns complete map edges for a wholly contained source window. */
    public static Envelope mapBounds(RasterSourceMetadata metadata, RasterWindow window) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(window, "window");
        requireBounds(metadata);
        if (window.endColumn() > metadata.width() || window.endRow() > metadata.height()) {
            throw new IllegalArgumentException(
                    "window must be wholly contained by metadata dimensions");
        }
        return new Envelope(
                columnEdge(metadata, window.column()),
                rowEdge(metadata, Math.toIntExact(window.endRow())),
                columnEdge(metadata, Math.toIntExact(window.endColumn())),
                rowEdge(metadata, window.row()));
    }

    private static Envelope requireBounds(RasterSourceMetadata metadata) {
        return metadata.mapBounds()
                .orElseThrow(
                        () -> new IllegalArgumentException("metadata.mapBounds must be present"));
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
