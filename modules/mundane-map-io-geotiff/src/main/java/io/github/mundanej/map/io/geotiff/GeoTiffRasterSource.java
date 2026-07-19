package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.core.RasterRequestAccounting;
import io.github.mundanej.map.core.RasterResampling;
import java.util.Objects;

final class GeoTiffRasterSource implements RasterSource {
    private final RasterSourceMetadata metadata;
    private final RasterSourceLimits requestLimits;
    private final GeoTiffLimits formatLimits;
    private final boolean tiled;
    private final int segmentWidth;
    private final int segmentHeight;
    private final int segmentsAcross;
    private final int samplesPerPixel;
    private final GeoTiffParser.ColorProfile colorProfile;
    private final int compression;
    private byte[] snapshot;
    private long[] offsets;
    private long[] counts;
    private long[] decodedCounts;
    private boolean closed;

    GeoTiffRasterSource(
            byte[] snapshot,
            RasterSourceMetadata metadata,
            RasterSourceLimits requestLimits,
            GeoTiffLimits formatLimits,
            boolean tiled,
            int segmentWidth,
            int segmentHeight,
            int segmentsAcross,
            int samplesPerPixel,
            GeoTiffParser.ColorProfile colorProfile,
            int compression,
            long[] offsets,
            long[] counts,
            long[] decodedCounts) {
        this.snapshot = snapshot;
        this.metadata = metadata;
        this.requestLimits = requestLimits;
        this.formatLimits = formatLimits;
        this.tiled = tiled;
        this.segmentWidth = segmentWidth;
        this.segmentHeight = segmentHeight;
        this.segmentsAcross = segmentsAcross;
        this.samplesPerPixel = samplesPerPixel;
        this.colorProfile = colorProfile;
        this.compression = compression;
        this.offsets = offsets;
        this.counts = counts;
        this.decodedCounts = decodedCounts;
    }

    @Override
    public RasterSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public RasterSourceLimits limits() {
        return requestLimits;
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return DiagnosticReport.empty();
    }

    @Override
    public synchronized RasterRead read(RasterRequest request, CancellationToken cancellation) {
        requireOpen();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        RasterRequestLimits effective =
                request.tighterLimits().orElse(requestLimits.requestLimits());
        if (!effective.tightens(requestLimits.requestLimits())) {
            throw new IllegalArgumentException("Request limits may only tighten source limits");
        }
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(metadata.identity().id(), effective, cancellation);
        accounting.checkpoint();
        RasterWindow window = request.sourceWindow();
        accounting.validateWindow(metadata, window);
        int firstSegmentColumn = tiled ? window.column() / segmentWidth : 0;
        int lastSegmentColumn =
                tiled ? Math.toIntExact((window.endColumn() - 1) / segmentWidth) : 0;
        int firstSegmentRow = window.row() / segmentHeight;
        int lastSegmentRow = Math.toIntExact((window.endRow() - 1) / segmentHeight);
        long sourceWork = 0;
        long largestSegment = 0;
        int planned = 0;
        for (int segmentRow = firstSegmentRow; segmentRow <= lastSegmentRow; segmentRow++) {
            for (int segmentColumn = firstSegmentColumn;
                    segmentColumn <= lastSegmentColumn;
                    segmentColumn++) {
                if ((planned++ & 4095) == 0) {
                    accounting.checkpoint();
                }
                int segment = segmentRow * segmentsAcross + segmentColumn;
                sourceWork = Math.addExact(sourceWork, decodedCounts[segment] / samplesPerPixel);
                largestSegment = Math.max(largestSegment, decodedCounts[segment]);
            }
        }
        accounting.chargeSourcePixels(sourceWork);
        long outputPixels =
                accounting.validateOutput(request.outputWidth(), request.outputHeight());
        long windowPixels = Math.multiplyExact((long) window.width(), window.height());
        long windowBytes = Math.multiplyExact(windowPixels, 4L);
        long outputBytes = Math.multiplyExact(outputPixels, 4L);
        long formatWorking = Math.addExact(largestSegment, windowBytes);
        GeoTiffFailures.limit(
                metadata.identity().id(),
                "geoTiffRead",
                "workingBytes",
                formatWorking,
                formatLimits.maximumWorkingBytes());
        accounting.chargeIntermediateBytes(windowBytes);
        accounting.chargeIntermediateBytes(outputBytes);
        accounting.chargePublishedBytes(outputBytes);
        RasterResampling.validatePlan(
                window.width(),
                window.height(),
                request.outputWidth(),
                request.outputHeight(),
                request.interpolation());
        byte[] decoded = new byte[Math.toIntExact(largestSegment)];
        int[] sourcePixels = new int[Math.toIntExact(windowPixels)];
        decodeWindow(
                window,
                firstSegmentColumn,
                lastSegmentColumn,
                firstSegmentRow,
                lastSegmentRow,
                decoded,
                sourcePixels,
                cancellation);
        RgbaPixelBuffer.Builder builder =
                RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
        long generated = 0;
        for (int outputRow = 0; outputRow < request.outputHeight(); outputRow++) {
            accounting.checkpoint();
            RasterResampling.AxisWeights y =
                    request.interpolation() == RasterInterpolation.BILINEAR
                            ? RasterResampling.bilinearAxis(
                                    outputRow, window.height(), request.outputHeight())
                            : null;
            for (int outputColumn = 0; outputColumn < request.outputWidth(); outputColumn++) {
                if ((generated++ & 4095) == 0) {
                    accounting.checkpoint();
                }
                int rgba;
                if (request.interpolation() == RasterInterpolation.NEAREST) {
                    int column =
                            window.column()
                                    + RasterResampling.nearestIndex(
                                            outputColumn, window.width(), request.outputWidth());
                    int row =
                            window.row()
                                    + RasterResampling.nearestIndex(
                                            outputRow, window.height(), request.outputHeight());
                    rgba = stagedPixel(sourcePixels, window, column, row);
                } else {
                    RasterResampling.AxisWeights x =
                            RasterResampling.bilinearAxis(
                                    outputColumn, window.width(), request.outputWidth());
                    int west = window.column() + x.lowerIndex();
                    int east = window.column() + x.upperIndex();
                    int north = window.row() + y.lowerIndex();
                    int south = window.row() + y.upperIndex();
                    rgba =
                            RasterResampling.bilinearRgba(
                                    stagedPixel(sourcePixels, window, west, north),
                                    stagedPixel(sourcePixels, window, east, north),
                                    stagedPixel(sourcePixels, window, west, south),
                                    stagedPixel(sourcePixels, window, east, south),
                                    x,
                                    y);
                }
                builder.setRgba(outputColumn, outputRow, rgba);
            }
        }
        accounting.checkpoint();
        return new RasterRead(window, builder.build(), DiagnosticReport.empty());
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        snapshot = null;
        offsets = null;
        counts = null;
        decodedCounts = null;
    }

    private void decodeWindow(
            RasterWindow window,
            int firstSegmentColumn,
            int lastSegmentColumn,
            int firstSegmentRow,
            int lastSegmentRow,
            byte[] decoded,
            int[] sourcePixels,
            CancellationToken cancellation) {
        for (int segmentRow = firstSegmentRow; segmentRow <= lastSegmentRow; segmentRow++) {
            for (int segmentColumn = firstSegmentColumn;
                    segmentColumn <= lastSegmentColumn;
                    segmentColumn++) {
                int segment = segmentRow * segmentsAcross + segmentColumn;
                GeoTiffSegmentDecoder.decode(
                        metadata.identity().id(),
                        segment,
                        compression,
                        snapshot,
                        Math.toIntExact(offsets[segment]),
                        Math.toIntExact(counts[segment]),
                        decoded,
                        Math.toIntExact(decodedCounts[segment]),
                        "geoTiffRead",
                        cancellation);
                copyIntersection(
                        window, segmentColumn, segmentRow, decoded, sourcePixels, cancellation);
            }
        }
    }

    private void copyIntersection(
            RasterWindow window,
            int segmentColumn,
            int segmentRow,
            byte[] decoded,
            int[] sourcePixels,
            CancellationToken cancellation) {
        int segmentStartColumn = tiled ? segmentColumn * segmentWidth : 0;
        int segmentStartRow = segmentRow * segmentHeight;
        int segmentEndColumn = Math.min(metadata.width(), segmentStartColumn + segmentWidth);
        int segmentEndRow = Math.min(metadata.height(), segmentStartRow + segmentHeight);
        int firstColumn = Math.max(window.column(), segmentStartColumn);
        int lastColumn = Math.min(Math.toIntExact(window.endColumn()), segmentEndColumn);
        int firstRow = Math.max(window.row(), segmentStartRow);
        int lastRow = Math.min(Math.toIntExact(window.endRow()), segmentEndRow);
        long copied = 0;
        for (int row = firstRow; row < lastRow; row++) {
            GeoTiffFailures.checkpoint(metadata.identity().id(), cancellation, "geoTiffRead");
            int decodedCell =
                    (row - segmentStartRow) * segmentWidth + firstColumn - segmentStartColumn;
            int target = (row - window.row()) * window.width() + firstColumn - window.column();
            for (int column = firstColumn; column < lastColumn; column++) {
                if ((copied++ & 4095) == 0) {
                    GeoTiffFailures.checkpoint(
                            metadata.identity().id(), cancellation, "geoTiffRead");
                }
                sourcePixels[target++] = decodedRgba(decoded, decodedCell++ * samplesPerPixel);
            }
        }
    }

    private static int stagedPixel(int[] pixels, RasterWindow window, int column, int row) {
        return pixels[(row - window.row()) * window.width() + column - window.column()];
    }

    private int decodedRgba(byte[] decoded, int index) {
        return switch (colorProfile) {
            case WHITE_GRAY -> gray(decoded, index, true, false);
            case BLACK_GRAY -> gray(decoded, index, false, false);
            case WHITE_GRAY_ALPHA -> gray(decoded, index, true, true);
            case BLACK_GRAY_ALPHA -> gray(decoded, index, false, true);
            case RGB -> rgba(decoded, index, false);
            case RGBA -> rgba(decoded, index, true);
        };
    }

    private static int gray(byte[] decoded, int index, boolean invert, boolean alpha) {
        int gray = decoded[index] & 0xff;
        if (invert) {
            gray = 255 - gray;
        }
        int alphaValue = alpha ? decoded[index + 1] & 0xff : 255;
        return (gray << 24) | (gray << 16) | (gray << 8) | alphaValue;
    }

    private static int rgba(byte[] decoded, int index, boolean alpha) {
        return ((decoded[index] & 0xff) << 24)
                | ((decoded[index + 1] & 0xff) << 16)
                | ((decoded[index + 2] & 0xff) << 8)
                | (alpha ? decoded[index + 3] & 0xff : 255);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Raster source is closed");
        }
    }
}
