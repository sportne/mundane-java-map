package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Objects;
import java.util.Optional;

/** Allocation-free-at-open deterministic procedural raster source. */
public final class SyntheticRasterSource implements RasterSource {
    private final RasterSourceMetadata metadata;
    private final RasterSourceLimits limits;
    private boolean closed;

    private SyntheticRasterSource(
            SourceIdentity identity,
            int width,
            int height,
            Optional<Envelope> mapBounds,
            Optional<CrsMetadata> crs,
            RasterSourceLimits limits) {
        metadata = new RasterSourceMetadata(identity, width, height, mapBounds, crs);
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Opens a fully described procedural source. */
    public static SyntheticRasterSource open(
            SourceIdentity identity,
            int width,
            int height,
            Optional<Envelope> mapBounds,
            Optional<CrsMetadata> crs,
            RasterSourceLimits limits) {
        return new SyntheticRasterSource(
                Objects.requireNonNull(identity, "identity"),
                width,
                height,
                Objects.requireNonNull(mapBounds, "mapBounds"),
                Objects.requireNonNull(crs, "crs"),
                limits);
    }

    /** Opens a mapped procedural source with Level 1 limits. */
    public static SyntheticRasterSource open(
            SourceIdentity identity, int width, int height, Envelope mapBounds, CrsMetadata crs) {
        return open(
                identity,
                width,
                height,
                Optional.of(mapBounds),
                Optional.of(crs),
                RasterSourceLimits.LEVEL_1);
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
    public RasterRead read(RasterRequest request, CancellationToken cancellation) {
        if (closed) {
            throw new IllegalStateException("Raster source is closed");
        }
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
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
        long packedBytes = Math.multiplyExact(outputPixels, 4);
        accounting.chargeIntermediateBytes(packedBytes);
        accounting.chargePublishedBytes(packedBytes);
        accounting.checkpoint();
        RgbaPixelBuffer.Builder builder =
                RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
        long generated = 0;
        for (int outputRow = 0; outputRow < request.outputHeight(); outputRow++) {
            accounting.checkpoint();
            int sourceRow =
                    window.row() + nearest(outputRow, window.height(), request.outputHeight());
            for (int outputColumn = 0; outputColumn < request.outputWidth(); outputColumn++) {
                if ((generated++ & 4095) == 0) {
                    accounting.checkpoint();
                }
                int sourceColumn =
                        window.column()
                                + nearest(outputColumn, window.width(), request.outputWidth());
                builder.setRgba(outputColumn, outputRow, proceduralPixel(sourceColumn, sourceRow));
            }
        }
        accounting.checkpoint();
        return new RasterRead(window, builder.build(), DiagnosticReport.empty());
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    private static int nearest(int outputIndex, int sourceSize, int outputSize) {
        long numerator =
                Math.multiplyExact(
                        Math.addExact(Math.multiplyExact(2L, outputIndex), 1L), sourceSize);
        long denominator = Math.multiplyExact(2L, outputSize);
        return Math.toIntExact(numerator / denominator);
    }

    private static int proceduralPixel(int column, int row) {
        int red = column & 0xff;
        int green = row & 0xff;
        int blue = (column ^ row) & 0xff;
        return (red << 24) | (green << 16) | (blue << 8) | 0xff;
    }
}
