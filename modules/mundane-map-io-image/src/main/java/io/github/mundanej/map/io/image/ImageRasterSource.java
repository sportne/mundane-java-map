package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.RasterRequestAccounting;
import io.github.mundanej.map.core.RasterResampling;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ImageRasterSource implements RasterSource {
    private final ImageChannel channel;
    private final ImageHeader header;
    private final RasterSourceMetadata metadata;
    private final RasterSourceLimits limits;
    private final EncodedRasterDecoder decoder;
    private final ImageCachePolicy cachePolicy;
    private final ImageContentVersion version;
    private final LinkedHashMap<CacheKey, CacheEntry> cache = new LinkedHashMap<>();
    private volatile boolean closed;
    private long retainedBytes;
    private long hits;
    private long misses;
    private long admissions;
    private long evictions;
    private long disabledBypasses;
    private long oversizedBypasses;
    private long accountingBypasses;
    private long invalidations;

    ImageRasterSource(
            ImageChannel channel,
            ImageHeader header,
            RasterSourceMetadata metadata,
            ImageOpenOptions options,
            EncodedRasterDecoder decoder,
            ImageContentVersion version) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.header = Objects.requireNonNull(header, "header");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        limits = options.requestLimits();
        cachePolicy = options.cachePolicy();
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.version = Objects.requireNonNull(version, "version");
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
        requireOpen();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        RasterRequestLimits effective = request.tighterLimits().orElse(limits.requestLimits());
        if (!effective.tightens(limits.requestLimits())) {
            throw new IllegalArgumentException("Request limits may only tighten source limits");
        }
        Plan plan = validateAndPreflight(request, cancellation, effective);
        RasterRequestAccounting accounting = accounting(effective, cancellation);
        accounting.chargeSourcePixels(plan.fullPixels());
        accounting.chargeIntermediateBytes(4096);
        verifyFingerprint(cancellation);
        CacheKey key =
                new CacheKey(
                        version,
                        request.sourceWindow(),
                        request.outputWidth(),
                        request.outputHeight(),
                        request.interpolation());
        CacheEntry existing = cachePolicy.enabled() ? cache.get(key) : null;
        if (existing != null) {
            accounting.chargeIntermediateBytes(plan.outputBytes());
            accounting.chargePublishedBytes(plan.outputBytes());
            RgbaPixelBuffer copy = copy(existing.pixels(), cancellation);
            RasterRead result =
                    new RasterRead(request.sourceWindow(), copy, DiagnosticReport.empty());
            checkpoint(cancellation);
            cache.remove(key);
            cache.put(key, existing);
            hits = increment(hits);
            return result;
        }

        accounting.chargeIntermediateBytes(header.encodedLength());
        accounting.chargeIntermediateBytes(header.encodedLength());
        accounting.chargeIntermediateBytes(plan.decoderReservation() - header.encodedLength());
        accounting.chargePublishedBytes(plan.outputBytes());
        byte[] snapshot = operationSnapshot(cancellation);
        ImageDecodeContext context =
                new ImageDecodeContext(
                        metadata.identity(),
                        header,
                        request.sourceWindow(),
                        request.outputWidth(),
                        request.outputHeight(),
                        request.interpolation(),
                        accounting,
                        plan.decoderReservation());
        RgbaPixelBuffer decoded = decoder.decode(new ByteArrayInputStream(snapshot), context);
        context.checkpoint();
        if (decoded == null
                || decoded.width() != request.outputWidth()
                || decoded.height() != request.outputHeight()) {
            throw new IllegalStateException("Decoder returned an unexpected result shape");
        }
        context.requireFullyClaimed();
        RasterRead result;
        boolean disabled = !cachePolicy.enabled();
        boolean oversized =
                !disabled
                        && (plan.outputBytes() > cachePolicy.maximumPixelBytes().orElseThrow()
                                || cachePolicy.maximumEntries().orElseThrow() < 1);
        boolean accountingConstrained =
                !disabled
                        && !oversized
                        && plan.preflightBytesWithAdmission()
                                > effective.decodedIntermediateBytes();
        RgbaPixelBuffer retained = null;
        if (!disabled && !oversized && !accountingConstrained) {
            accounting.chargeIntermediateBytes(plan.outputBytes());
            RgbaPixelBuffer consumer = copy(decoded, cancellation);
            retained = decoded;
            result = new RasterRead(request.sourceWindow(), consumer, DiagnosticReport.empty());
        } else {
            result = new RasterRead(request.sourceWindow(), decoded, DiagnosticReport.empty());
        }
        checkpoint(cancellation);
        if (disabled) {
            disabledBypasses = increment(disabledBypasses);
        } else {
            misses = increment(misses);
            if (oversized) {
                oversizedBypasses = increment(oversizedBypasses);
            } else if (accountingConstrained) {
                accountingBypasses = increment(accountingBypasses);
            } else {
                admit(key, retained, plan.outputBytes());
            }
        }
        return result;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cache.clear();
        retainedBytes = 0;
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

    CacheMetrics cacheMetrics() {
        synchronized (this) {
            return new CacheMetrics(
                    hits,
                    misses,
                    admissions,
                    evictions,
                    disabledBypasses,
                    oversizedBypasses,
                    accountingBypasses,
                    invalidations,
                    cache.size(),
                    retainedBytes);
        }
    }

    private Plan validateAndPreflight(
            RasterRequest request, CancellationToken cancellation, RasterRequestLimits effective) {
        RasterRequestAccounting accounting = accounting(effective, cancellation);
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
        boolean firstSupport = decoder.supportsInterpolation(request.interpolation());
        boolean secondSupport = decoder.supportsInterpolation(request.interpolation());
        if (!firstSupport || firstSupport != secondSupport) {
            throw ImageDiagnostics.failure(
                    metadata.identity().id(),
                    "IMAGE_DECODER_INTERPOLATION_UNSUPPORTED",
                    "decoder",
                    "Image decoder does not support the requested interpolation",
                    Map.of(
                            "format",
                            header.format().name(),
                            "interpolation",
                            request.interpolation().name()));
        }
        long outputBytes = multiply(outputPixels, 4, accounting);
        long decoderReservation =
                add(header.encodedLength(), multiply(fullPixels, 8, accounting), accounting);
        decoderReservation = add(decoderReservation, outputBytes, accounting);
        long preflight = add(4096, header.encodedLength(), accounting);
        preflight = add(preflight, decoderReservation, accounting);
        accounting.chargeIntermediateBytes(preflight);
        accounting.chargePublishedBytes(outputBytes);
        return new Plan(
                fullPixels, outputBytes, decoderReservation, safeAdd(preflight, outputBytes));
    }

    private RasterRequestAccounting accounting(
            RasterRequestLimits effective, CancellationToken cancellation) {
        return new RasterRequestAccounting(metadata.identity().id(), effective, cancellation);
    }

    private void verifyFingerprint(CancellationToken cancellation) {
        try {
            ImageContentVersion observed =
                    ImageSnapshots.fingerprint(
                            channel,
                            version.length(),
                            header,
                            metadata.identity().id(),
                            "readFingerprint",
                            cancellation);
            if (!version.equals(observed)) {
                invalidate();
                ImageSnapshots.requireVersion(
                        version, observed, metadata.identity().id(), "readFingerprint");
            }
        } catch (SourceException failure) {
            if (failure.terminal().code().equals("IMAGE_FILE_LENGTH_MISMATCH")
                    || failure.terminal().code().equals("IMAGE_DECODE_MISMATCH")) {
                invalidate();
            }
            throw failure;
        } catch (IOException failure) {
            throw ioFailure("read");
        }
    }

    private byte[] operationSnapshot(CancellationToken cancellation) {
        try {
            byte[] snapshot =
                    ImageSnapshots.exact(
                            channel,
                            version.length(),
                            metadata.identity().id(),
                            "operationSnapshot",
                            cancellation);
            ImageSnapshots.requireHeader(
                    snapshot, header, metadata.identity().id(), "operationSnapshot");
            ImageContentVersion observed = ImageSnapshots.version(snapshot);
            if (!version.equals(observed)) {
                invalidate();
                ImageSnapshots.requireVersion(
                        version, observed, metadata.identity().id(), "operationSnapshot");
            }
            return snapshot;
        } catch (SourceException failure) {
            if (failure.terminal().code().equals("IMAGE_FILE_LENGTH_MISMATCH")
                    || failure.terminal().code().equals("IMAGE_DECODE_MISMATCH")) {
                invalidate();
            }
            throw failure;
        } catch (IOException failure) {
            throw ioFailure("read");
        }
    }

    private RgbaPixelBuffer copy(RgbaPixelBuffer pixels, CancellationToken cancellation) {
        RgbaPixelBuffer.Builder copy = RgbaPixelBuffer.builder(pixels.width(), pixels.height());
        long copied = 0;
        for (int row = 0; row < pixels.height(); row++) {
            for (int column = 0; column < pixels.width(); column++) {
                if ((copied++ & 4095) == 0) {
                    checkpoint(cancellation);
                }
                copy.setRgba(column, row, pixels.rgbaAt(column, row));
            }
        }
        return copy.build();
    }

    private void admit(CacheKey key, RgbaPixelBuffer pixels, long bytes) {
        int maximumEntries = cachePolicy.maximumEntries().orElseThrow();
        long maximumBytes = cachePolicy.maximumPixelBytes().orElseThrow();
        long removed = 0;
        while (!cache.isEmpty()
                && (cache.size() >= maximumEntries || retainedBytes + bytes > maximumBytes)) {
            Map.Entry<CacheKey, CacheEntry> eldest = cache.entrySet().iterator().next();
            cache.remove(eldest.getKey());
            retainedBytes -= eldest.getValue().bytes();
            removed++;
        }
        cache.put(key, new CacheEntry(pixels, bytes));
        retainedBytes += bytes;
        admissions = increment(admissions);
        evictions = addSaturated(evictions, removed);
    }

    private void invalidate() {
        cache.clear();
        retainedBytes = 0;
        invalidations = increment(invalidations);
    }

    private void checkpoint(CancellationToken cancellation) {
        ImageHeaderProbe.checkpoint(cancellation, metadata.identity().id(), "raster-read");
    }

    private SourceException ioFailure(String operation) {
        return ImageDiagnostics.failure(
                metadata.identity().id(),
                "IMAGE_IO_FAILED",
                "image",
                "Image read failed",
                Map.of("operation", operation, "causeKind", "IOException"));
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Raster source is closed");
        }
    }

    private static long multiply(long left, long right, RasterRequestAccounting accounting) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException failure) {
            accounting.chargeIntermediateBytes(Long.MAX_VALUE);
            throw new AssertionError(failure);
        }
    }

    private static long add(long left, long right, RasterRequestAccounting accounting) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException failure) {
            accounting.chargeIntermediateBytes(Long.MAX_VALUE);
            throw new AssertionError(failure);
        }
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    static long addSaturated(long value, long amount) {
        return Long.MAX_VALUE - value < amount ? Long.MAX_VALUE : value + amount;
    }

    private record CacheKey(
            ImageContentVersion version,
            RasterWindow window,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation) {}

    private record CacheEntry(RgbaPixelBuffer pixels, long bytes) {}

    private record Plan(
            long fullPixels,
            long outputBytes,
            long decoderReservation,
            long preflightBytesWithAdmission) {}

    record CacheMetrics(
            long hits,
            long misses,
            long admissions,
            long evictions,
            long disabledBypasses,
            long oversizedBypasses,
            long accountingBypasses,
            long invalidations,
            int entries,
            long retainedPixelBytes) {}
}
