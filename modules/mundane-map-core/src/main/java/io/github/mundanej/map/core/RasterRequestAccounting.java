package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Operation-local checked raster-request accounting for sources and consumers. */
public final class RasterRequestAccounting {
    private final String sourceId;
    private final RasterRequestLimits limits;
    private final CancellationToken cancellation;
    private long sourcePixels;
    private long intermediateBytes;
    private long publishedBytes;

    /**
     * Creates accounting for exact already-resolved limits and token.
     *
     * @param sourceId stable source identifier used in diagnostics
     * @param effectiveLimits limits already resolved against source defaults
     * @param cancellation operation-local cancellation token
     */
    public RasterRequestAccounting(
            String sourceId, RasterRequestLimits effectiveLimits, CancellationToken cancellation) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        limits = Objects.requireNonNull(effectiveLimits, "effectiveLimits");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    /**
     * Validates that a strict window lies wholly inside the source dimensions.
     *
     * @param metadata authoritative source dimensions
     * @param window zero-based source-pixel window to validate
     * @throws SourceException when the window extends outside the source
     */
    public void validateWindow(RasterSourceMetadata metadata, RasterWindow window) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(window, "window");
        if (window.endColumn() > metadata.width() || window.endRow() > metadata.height()) {
            throw failure(
                    "RASTER_WINDOW_OUT_OF_RANGE",
                    "Raster window is outside source dimensions",
                    Map.of(
                            "column", Integer.toString(window.column()),
                            "row", Integer.toString(window.row()),
                            "width", Integer.toString(window.width()),
                            "height", Integer.toString(window.height()),
                            "rasterWidth", Integer.toString(metadata.width()),
                            "rasterHeight", Integer.toString(metadata.height())));
        }
    }

    /**
     * Charges cumulative source pixels examined.
     *
     * @param pixels non-negative number of additional source pixels
     * @throws SourceException when the cumulative source-pixel limit is exceeded
     */
    public void chargeSourcePixels(long pixels) {
        sourcePixels =
                charge(
                        "sourceWindowPixels",
                        sourcePixels,
                        requireNonNegative(pixels, "pixels"),
                        limits.sourceWindowPixels());
    }

    /**
     * Validates output dimensions/product and returns the output pixel count.
     *
     * @param width positive output width in pixels
     * @param height positive output height in pixels
     * @return checked output pixel count
     * @throws IllegalArgumentException when a dimension is not positive
     * @throws SourceException when an output limit is exceeded
     */
    public long validateOutput(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Output dimensions must be positive");
        }
        if (width > limits.outputDimension()) {
            throw limit("outputWidth", width, limits.outputDimension());
        }
        if (height > limits.outputDimension()) {
            throw limit("outputHeight", height, limits.outputDimension());
        }
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException ignored) {
            throw limit("outputPixels", Long.MAX_VALUE, limits.outputPixels());
        }
        if (pixels > Integer.MAX_VALUE || pixels > limits.outputPixels()) {
            throw limit("outputPixels", pixels, Math.min(limits.outputPixels(), Integer.MAX_VALUE));
        }
        return pixels;
    }

    /**
     * Charges cumulative decoded or intermediate bytes.
     *
     * @param bytes non-negative number of additional bytes
     * @throws SourceException when the cumulative intermediate-byte limit is exceeded
     */
    public void chargeIntermediateBytes(long bytes) {
        intermediateBytes =
                charge(
                        "decodedIntermediateBytes",
                        intermediateBytes,
                        requireNonNegative(bytes, "bytes"),
                        limits.decodedIntermediateBytes());
    }

    /**
     * Charges cumulative conservatively owned published bytes.
     *
     * @param bytes non-negative number of additional bytes
     * @throws SourceException when the cumulative owned-payload limit is exceeded
     */
    public void chargePublishedBytes(long bytes) {
        publishedBytes =
                charge(
                        "ownedPayloadBytes",
                        publishedBytes,
                        requireNonNegative(bytes, "bytes"),
                        limits.ownedPayloadBytes());
    }

    /**
     * Fails with the stable cancellation diagnostic when requested.
     *
     * @throws SourceException when cancellation has been requested
     */
    public void checkpoint() {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    "SOURCE_CANCELLED",
                    "Raster request was cancelled",
                    Map.of("operation", "raster-read"));
        }
    }

    private long charge(String limit, long current, long amount, long maximum) {
        long requested;
        boolean overflow = false;
        try {
            requested = Math.addExact(current, amount);
        } catch (ArithmeticException ignored) {
            requested = Long.MAX_VALUE;
            overflow = true;
        }
        if (overflow || requested > maximum) {
            throw limit(limit, requested, maximum);
        }
        return requested;
    }

    private SourceException limit(String limit, long requested, long maximum) {
        return failure(
                "SOURCE_LIMIT_EXCEEDED",
                "Raster request limit exceeded",
                Map.of(
                        "scope",
                        "raster-request",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    private SourceException failure(String code, String message, Map<String, String> context) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
