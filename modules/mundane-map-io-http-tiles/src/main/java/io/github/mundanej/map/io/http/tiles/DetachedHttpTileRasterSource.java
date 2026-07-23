package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.RasterRequestAccounting;
import io.github.mundanej.map.core.RasterResampling;
import java.util.Optional;

final class DetachedHttpTileRasterSource implements RasterSource {
    private final RasterSourceMetadata metadata;
    private final RasterSourceLimits limits;
    private RgbaPixelBuffer pixels;
    private boolean closed;

    DetachedHttpTileRasterSource(
            SourceIdentity identity,
            Envelope bounds,
            RasterSourceLimits limits,
            RgbaPixelBuffer pixels) {
        this.metadata =
                new RasterSourceMetadata(
                        identity,
                        pixels.width(),
                        pixels.height(),
                        Optional.of(bounds),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())));
        this.limits = limits;
        this.pixels = pixels;
    }

    @Override
    public RasterSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public RasterSourceLimits limits() {
        return limits;
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return DiagnosticReport.empty();
    }

    @Override
    public synchronized RasterRead read(RasterRequest request, CancellationToken cancellation) {
        if (closed) {
            throw new IllegalStateException("Raster source is closed");
        }
        RasterRequestLimits effective = request.tighterLimits().orElse(limits.requestLimits());
        if (!effective.tightens(limits.requestLimits())) {
            throw new IllegalArgumentException("Request limits may only tighten source limits");
        }
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(metadata.identity().id(), effective, cancellation);
        accounting.checkpoint();
        RasterWindow window = request.sourceWindow();
        accounting.validateWindow(metadata, window);
        accounting.chargeSourcePixels(Math.multiplyExact((long) window.width(), window.height()));
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
        RgbaPixelBuffer.Builder output =
                RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
        long produced = 0;
        for (int row = 0; row < request.outputHeight(); row++) {
            RasterResampling.AxisWeights y =
                    request.interpolation() == RasterInterpolation.BILINEAR
                            ? RasterResampling.bilinearAxis(
                                    row, window.height(), request.outputHeight())
                            : null;
            for (int column = 0; column < request.outputWidth(); column++) {
                if ((produced++ & 4095L) == 0) {
                    accounting.checkpoint();
                }
                output.setRgba(column, row, sample(request, window, column, row, y));
            }
        }
        accounting.checkpoint();
        return new RasterRead(window, output.build(), DiagnosticReport.empty());
    }

    private int sample(
            RasterRequest request,
            RasterWindow window,
            int outputColumn,
            int outputRow,
            RasterResampling.AxisWeights y) {
        if (request.interpolation() == RasterInterpolation.NEAREST) {
            int sourceColumn =
                    window.column()
                            + RasterResampling.nearestIndex(
                                    outputColumn, window.width(), request.outputWidth());
            int sourceRow =
                    window.row()
                            + RasterResampling.nearestIndex(
                                    outputRow, window.height(), request.outputHeight());
            return pixels.rgbaAt(sourceColumn, sourceRow);
        }
        RasterResampling.AxisWeights x =
                RasterResampling.bilinearAxis(outputColumn, window.width(), request.outputWidth());
        int west = window.column() + x.lowerIndex();
        int east = window.column() + x.upperIndex();
        int north = window.row() + y.lowerIndex();
        int south = window.row() + y.upperIndex();
        return RasterResampling.bilinearRgba(
                pixels.rgbaAt(west, north),
                pixels.rgbaAt(east, north),
                pixels.rgbaAt(west, south),
                pixels.rgbaAt(east, south),
                x,
                y);
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        pixels = null;
    }
}
