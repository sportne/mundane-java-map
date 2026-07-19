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
    private byte[] snapshot;
    private long[] offsets;
    private long[] counts;
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
            long[] offsets,
            long[] counts) {
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
        this.offsets = offsets;
        this.counts = counts;
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
                sourceWork = Math.addExact(sourceWork, counts[segment] / samplesPerPixel);
                largestSegment = Math.max(largestSegment, counts[segment]);
            }
        }
        GeoTiffFailures.limit(
                metadata.identity().id(),
                "geoTiffRead",
                "workingBytes",
                largestSegment,
                formatLimits.maximumWorkingBytes());
        accounting.chargeSourcePixels(sourceWork);
        long outputPixels =
                accounting.validateOutput(request.outputWidth(), request.outputHeight());
        long outputBytes = Math.multiplyExact(outputPixels, 4L);
        accounting.chargeIntermediateBytes(outputBytes);
        accounting.chargePublishedBytes(outputBytes);
        RasterResampling.validatePlan(
                window.width(),
                window.height(),
                request.outputWidth(),
                request.outputHeight(),
                request.interpolation());
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
                    rgba = pixel(column, row);
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
                                    pixel(west, north),
                                    pixel(east, north),
                                    pixel(west, south),
                                    pixel(east, south),
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
    }

    private int pixel(int column, int row) {
        int segmentColumn = tiled ? column / segmentWidth : 0;
        int segmentRow = row / segmentHeight;
        int segment = segmentRow * segmentsAcross + segmentColumn;
        int localColumn = tiled ? column - segmentColumn * segmentWidth : column;
        int localRow = row - segmentRow * segmentHeight;
        int index =
                Math.toIntExact(
                        offsets[segment]
                                + ((long) localRow * segmentWidth + localColumn) * samplesPerPixel);
        return switch (colorProfile) {
            case WHITE_GRAY -> gray(index, true, false);
            case BLACK_GRAY -> gray(index, false, false);
            case WHITE_GRAY_ALPHA -> gray(index, true, true);
            case BLACK_GRAY_ALPHA -> gray(index, false, true);
            case RGB -> rgba(index, false);
            case RGBA -> rgba(index, true);
        };
    }

    private int gray(int index, boolean invert, boolean alpha) {
        int gray = snapshot[index] & 0xff;
        if (invert) {
            gray = 255 - gray;
        }
        int alphaValue = alpha ? snapshot[index + 1] & 0xff : 255;
        return (gray << 24) | (gray << 16) | (gray << 8) | alphaValue;
    }

    private int rgba(int index, boolean alpha) {
        return ((snapshot[index] & 0xff) << 24)
                | ((snapshot[index + 1] & 0xff) << 16)
                | ((snapshot[index + 2] & 0xff) << 8)
                | (alpha ? snapshot[index + 3] & 0xff : 255);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Raster source is closed");
        }
    }
}
