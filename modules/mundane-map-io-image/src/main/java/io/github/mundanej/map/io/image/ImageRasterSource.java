package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.RasterRequestAccounting;
import io.github.mundanej.map.core.RasterResampling;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

final class ImageRasterSource implements RasterSource {
    private final ImageChannel channel;
    private final ImageHeader header;
    private final RasterSourceMetadata metadata;
    private final RasterSourceLimits limits;
    private final EncodedRasterDecoder decoder;
    private boolean closed;

    ImageRasterSource(
            ImageChannel channel,
            ImageHeader header,
            RasterSourceMetadata metadata,
            ImageOpenOptions options,
            EncodedRasterDecoder decoder) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.header = Objects.requireNonNull(header, "header");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        limits = options.requestLimits();
        this.decoder = Objects.requireNonNull(decoder, "decoder");
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
        requireOpen();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        RasterRequestLimits effective = request.tighterLimits().orElse(limits.requestLimits());
        if (!effective.tightens(limits.requestLimits())) {
            throw new IllegalArgumentException("Request limits may only tighten source limits");
        }
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(metadata.identity().id(), effective, cancellation);
        accounting.checkpoint();
        accounting.validateWindow(metadata, request.sourceWindow());
        long fullPixels = Math.multiplyExact((long) header.width(), header.height());
        accounting.chargeSourcePixels(fullPixels);
        long outputPixels =
                accounting.validateOutput(request.outputWidth(), request.outputHeight());
        RasterResampling.validatePlan(
                request.sourceWindow().width(),
                request.sourceWindow().height(),
                request.outputWidth(),
                request.outputHeight(),
                request.interpolation());
        long outputBytes = Math.multiplyExact(outputPixels, 4);
        long reservation;
        try {
            long backingBytes = Math.multiplyExact(fullPixels, 8);
            reservation =
                    Math.addExact(Math.addExact(header.encodedLength(), backingBytes), outputBytes);
        } catch (ArithmeticException overflow) {
            accounting.chargeIntermediateBytes(Long.MAX_VALUE);
            accounting.chargeIntermediateBytes(1);
            throw new AssertionError("Overflow accounting must fail", overflow);
        }
        accounting.chargeIntermediateBytes(reservation);
        accounting.chargePublishedBytes(outputBytes);
        boolean firstSupport = decoder.supportsInterpolation(request.interpolation());
        boolean secondSupport = decoder.supportsInterpolation(request.interpolation());
        if (firstSupport != secondSupport) {
            throw new IllegalStateException(
                    "Decoder interpolation capability must be deterministic");
        }
        if (!firstSupport) {
            throw ImageDiagnostics.failure(
                    metadata.identity().id(),
                    "IMAGE_DECODER_INTERPOLATION_UNSUPPORTED",
                    "decoder",
                    "Image decoder does not support the requested interpolation",
                    Map.of(
                            "format", header.format().name(),
                            "interpolation", request.interpolation().name()));
        }
        verifySnapshot(accounting, cancellation);
        ImageDecodeContext context =
                new ImageDecodeContext(
                        metadata.identity(),
                        header,
                        request.sourceWindow(),
                        request.outputWidth(),
                        request.outputHeight(),
                        request.interpolation(),
                        accounting,
                        reservation);
        RgbaPixelBuffer result;
        try {
            result =
                    decoder.decode(
                            new BoundedChannelInputStream(channel, header.encodedLength(), context),
                            context);
        } catch (SourceException | IllegalStateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure;
        }
        context.checkpoint();
        if (result == null
                || result.width() != request.outputWidth()
                || result.height() != request.outputHeight()) {
            throw new IllegalStateException("Decoder returned an unexpected result shape");
        }
        context.requireFullyClaimed();
        verifySnapshot(accounting, cancellation);
        context.checkpoint();
        return new RasterRead(request.sourceWindow(), result, DiagnosticReport.empty());
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            channel.close();
        } catch (IOException failure) {
            throw ImageDiagnostics.failure(
                    metadata.identity().id(),
                    "SOURCE_CLOSE_FAILED",
                    "image",
                    "Image source close failed",
                    Map.of("operation", "close", "causeKind", "IOException"));
        }
    }

    private void verifySnapshot(
            RasterRequestAccounting accounting, CancellationToken cancellation) {
        accounting.checkpoint();
        try {
            long actualLength = channel.size();
            if (actualLength != header.encodedLength()) {
                throw ImageDiagnostics.failure(
                        metadata.identity().id(),
                        "IMAGE_FILE_LENGTH_MISMATCH",
                        "image",
                        "Encoded image length changed",
                        Map.of(
                                "capturedBytes", Long.toString(header.encodedLength()),
                                "actualBytes", Long.toString(actualLength),
                                "reason", "sizeChanged"));
            }
            byte[] expected = header.snapshot();
            byte[] actual =
                    ImageHeaderProbe.readExact(
                            channel, 0, expected.length, cancellation, metadata.identity().id());
            if (!Arrays.equals(expected, actual)) {
                throw ImageDiagnostics.failure(
                        metadata.identity().id(),
                        "IMAGE_DECODE_MISMATCH",
                        "decoder",
                        "Encoded image header changed",
                        Map.of(
                                "field",
                                "headerSnapshot",
                                "expected",
                                "captured",
                                "actual",
                                "changed"));
            }
        } catch (IOException failure) {
            throw ImageDiagnostics.failure(
                    metadata.identity().id(),
                    "IMAGE_IO_FAILED",
                    "image",
                    "Image read failed",
                    Map.of("operation", "read", "causeKind", "IOException"));
        }
        accounting.checkpoint();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Raster source is closed");
        }
    }
}
