package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Detached encoded-byte implementation behind {@link RasterImages}. */
final class EncodedRasterByteDecoder {
    private static final String SCOPE = "imageDecode";
    private static final String OPERATION = "image-decode";

    private EncodedRasterByteDecoder() {}

    static RgbaPixelBuffer decode(
            byte[] encoded,
            SourceIdentity identity,
            EncodedRasterDecodeOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        try {
            return decodeGuarded(encoded, identity, options, decoders, cancellation);
        } catch (TokenFailure failure) {
            throw failure.original();
        }
    }

    private static RgbaPixelBuffer decodeGuarded(
            byte[] encoded,
            SourceIdentity identity,
            EncodedRasterDecodeOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        Accounting accounting = new Accounting(identity.id(), options.decodeLimits(), cancellation);
        CancellationToken guardedCancellation =
                () -> TokenFailure.isCancellationRequested(cancellation);
        accounting.checkpoint();
        int length = encoded.length;
        if (length > options.imageLimits().maximumEncodedBytes()) {
            throw ImageDiagnostics.limit(
                    identity.id(),
                    SCOPE,
                    "encodedBytes",
                    length,
                    options.imageLimits().maximumEncodedBytes());
        }
        accounting.chargeIntermediate(length);
        byte[] snapshot = encoded.clone();
        accounting.checkpoint();

        EncodedRasterFormat signature = signature(snapshot);
        if (options.expectedFormat().isPresent()
                && options.expectedFormat().orElseThrow() != signature) {
            throw ImageDiagnostics.failure(
                    identity.id(),
                    "IMAGE_EXPECTED_FORMAT_MISMATCH",
                    "image",
                    "Encoded image signature does not match the expected format",
                    Map.of(
                            "expectedFormat",
                            options.expectedFormat().orElseThrow().name(),
                            "signature",
                            signature == null ? "unknown" : signature.name()));
        }
        if (signature == null) {
            throw headerFailure(identity.id(), snapshot.length < 2 ? "truncated" : "value");
        }

        ImageHeader header;
        try {
            header =
                    ImageHeaderProbe.probe(
                            new MemoryChannel(snapshot),
                            signature == EncodedRasterFormat.PNG ? "png" : "jpeg",
                            identity.id(),
                            options.imageLimits(),
                            guardedCancellation);
        } catch (SourceException failure) {
            throw translateInternal(failure, identity.id());
        } catch (IOException impossible) {
            throw ImageDiagnostics.failure(
                    identity.id(),
                    "IMAGE_IO_FAILED",
                    "image",
                    "Encoded image memory read failed",
                    Map.of("operation", "decode", "causeKind", "IOException"));
        }
        requireExpectedDimensions(header, options, identity.id());
        accounting.validateShape(header.width(), header.height());
        try {
            ImageContainerValidator.validate(
                    snapshot, header, options.imageLimits(), guardedCancellation, identity.id());
        } catch (SourceException failure) {
            throw translateInternal(failure, identity.id());
        }

        EncodedRasterDecoder decoder =
                decoders.find(header.format())
                        .orElseThrow(
                                () ->
                                        ImageDiagnostics.failure(
                                                identity.id(),
                                                "IMAGE_DECODER_NOT_REGISTERED",
                                                "decoder",
                                                "No decoder is registered for the image format",
                                                Map.of("format", header.format().name())));
        boolean firstSupport;
        boolean secondSupport;
        try {
            firstSupport = decoder.supportsInterpolation(RasterInterpolation.NEAREST);
            secondSupport = decoder.supportsInterpolation(RasterInterpolation.NEAREST);
        } catch (SourceException ignored) {
            throw decoderContractFailure();
        }
        if (!firstSupport || firstSupport != secondSupport) {
            throw new IllegalStateException(
                    "Detached image decoder must stably support nearest native-size decoding");
        }

        long pixels = Math.multiplyExact((long) header.width(), header.height());
        long outputBytes = Math.multiplyExact(pixels, 4);
        long reservation = Math.addExact(header.encodedLength(), Math.multiplyExact(pixels, 12));
        accounting.chargeIntermediate(reservation);
        accounting.chargePublished(outputBytes);
        ImageDecodeContext context =
                new ImageDecodeContext(
                        identity,
                        header,
                        new RasterWindow(0, 0, header.width(), header.height()),
                        header.width(),
                        header.height(),
                        RasterInterpolation.NEAREST,
                        accounting::checkpoint,
                        reservation);
        RgbaPixelBuffer result;
        try {
            result = decoder.decode(new ByteArrayInputStream(snapshot), context);
        } catch (SourceException failure) {
            throw sanitizeDecoderFailure(failure, identity.id(), header.format());
        }
        context.checkpoint();
        if (result == null
                || result.width() != header.width()
                || result.height() != header.height()) {
            throw new IllegalStateException("Decoder returned an unexpected result shape");
        }
        context.requireFullyClaimed();
        return result;
    }

    private static EncodedRasterFormat signature(byte[] snapshot) {
        EncodedRasterFormat signature = ImageHeaderProbe.signature(snapshot);
        if (snapshot.length < 2) {
            return null;
        }
        return signature;
    }

    private static void requireExpectedDimensions(
            ImageHeader header, EncodedRasterDecodeOptions options, String sourceId) {
        if (options.expectedWidth().isEmpty()) {
            return;
        }
        int expectedWidth = options.expectedWidth().orElseThrow();
        int expectedHeight = options.expectedHeight().orElseThrow();
        if (header.width() != expectedWidth || header.height() != expectedHeight) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "IMAGE_DIMENSIONS_MISMATCH",
                    "image",
                    "Encoded image dimensions do not match the expected dimensions",
                    Map.of(
                            "expectedWidth", Integer.toString(expectedWidth),
                            "expectedHeight", Integer.toString(expectedHeight),
                            "width", Integer.toString(header.width()),
                            "height", Integer.toString(header.height())));
        }
    }

    private static SourceException translateInternal(SourceException failure, String sourceId) {
        SourceDiagnostic terminal = failure.terminal();
        Map<String, String> context = terminal.context();
        if (terminal.code().equals("SOURCE_LIMIT_EXCEEDED")) {
            java.util.LinkedHashMap<String, String> updated =
                    new java.util.LinkedHashMap<>(context);
            updated.put("scope", SCOPE);
            context = Map.copyOf(updated);
        } else if (terminal.code().equals("SOURCE_CANCELLED")) {
            context = Map.of("operation", OPERATION);
        }
        SourceDiagnostic translated =
                new SourceDiagnostic(
                        terminal.code(),
                        terminal.severity(),
                        sourceId,
                        terminal.location(),
                        terminal.message(),
                        context);
        return new SourceException(new DiagnosticReport(List.of(translated), 0), translated);
    }

    private static SourceException sanitizeDecoderFailure(
            SourceException failure, String sourceId, EncodedRasterFormat format) {
        SourceDiagnostic terminal = failure.terminal();
        if (failure.report().entries().size() != 1
                || !failure.report().entries().getFirst().equals(terminal)
                || failure.report().omittedWarningCount() != 0
                || terminal.severity() != DiagnosticSeverity.ERROR
                || !terminal.sourceId().equals(sourceId)) {
            throw decoderContractFailure();
        }
        return switch (terminal.code()) {
            case "SOURCE_CANCELLED" -> sanitizeCancellation(terminal, sourceId);
            case "IMAGE_DECODE_FAILED" -> sanitizeDecodeFailure(terminal, sourceId, format);
            case "IMAGE_DECODE_MISMATCH" -> sanitizeDecodeMismatch(terminal, sourceId);
            case "IMAGE_IO_FAILED" -> sanitizeIoFailure(terminal, sourceId);
            default -> throw decoderContractFailure();
        };
    }

    private static SourceException sanitizeCancellation(SourceDiagnostic value, String sourceId) {
        if (!value.context().equals(Map.of("operation", OPERATION))
                || !component(value).equals("image")) {
            throw decoderContractFailure();
        }
        return ImageDiagnostics.failure(
                sourceId,
                "SOURCE_CANCELLED",
                "image",
                "Image operation was cancelled",
                value.context());
    }

    private static SourceException sanitizeDecodeFailure(
            SourceDiagnostic value, String sourceId, EncodedRasterFormat format) {
        Map<String, String> context = value.context();
        if (!component(value).equals("decoder")
                || !format.name().equals(context.get("format"))
                || !List.of("codec", "bufferCapacity").contains(context.get("reason"))
                || !(context.keySet().equals(java.util.Set.of("format", "reason"))
                        || (context.keySet()
                                        .equals(java.util.Set.of("format", "reason", "causeKind"))
                                && "IOException".equals(context.get("causeKind"))))) {
            throw decoderContractFailure();
        }
        return ImageDiagnostics.failure(
                sourceId, "IMAGE_DECODE_FAILED", "decoder", "Image decoder failed", context);
    }

    private static SourceException sanitizeDecodeMismatch(SourceDiagnostic value, String sourceId) {
        Map<String, String> context = value.context();
        if (!component(value).equals("decoder")
                || !context.keySet().equals(java.util.Set.of("field", "expected", "actual"))
                || !List.of("dimensions", "decodedDimensions").contains(context.get("field"))
                || !context.get("expected").matches("[1-9][0-9]*x[1-9][0-9]*")
                || !(context.get("actual").equals("null")
                        || context.get("actual").matches("[1-9][0-9]*x[1-9][0-9]*"))) {
            throw decoderContractFailure();
        }
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_DECODE_MISMATCH",
                "decoder",
                "Decoded image facts do not match the bounded header",
                context);
    }

    private static SourceException sanitizeIoFailure(SourceDiagnostic value, String sourceId) {
        if (!component(value).equals("decoder")
                || !value.context()
                        .equals(Map.of("operation", "close", "causeKind", "IOException"))) {
            throw decoderContractFailure();
        }
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_IO_FAILED",
                "decoder",
                "Image decoder cleanup failed",
                value.context());
    }

    private static String component(SourceDiagnostic diagnostic) {
        if (diagnostic.location().isEmpty()) {
            throw decoderContractFailure();
        }
        DiagnosticLocation location = diagnostic.location().orElseThrow();
        if (location.recordNumber().isPresent()
                || location.partIndex().isPresent()
                || location.fieldIndex().isPresent()
                || location.fieldName().isPresent()
                || location.byteOffset().isPresent()) {
            throw decoderContractFailure();
        }
        if (location.component().isEmpty()) {
            throw decoderContractFailure();
        }
        return location.component().orElseThrow();
    }

    private static IllegalStateException decoderContractFailure() {
        return new IllegalStateException("Decoder emitted an unsupported checked diagnostic");
    }

    private static SourceException headerFailure(String sourceId, String reason) {
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_HEADER_INVALID",
                "image",
                OptionalLong.of(0),
                "Encoded image header is invalid",
                Map.of("format", "unknown", "field", "signature", "reason", reason),
                null);
    }

    private static final class Accounting {
        private final String sourceId;
        private final RasterRequestLimits limits;
        private final CancellationToken cancellation;
        private long intermediate;
        private long published;

        private Accounting(
                String sourceId, RasterRequestLimits limits, CancellationToken cancellation) {
            this.sourceId = sourceId;
            this.limits = limits;
            this.cancellation = cancellation;
        }

        private void validateShape(int width, int height) {
            long pixels = Math.multiplyExact((long) width, height);
            limit("sourceWindowPixels", pixels, limits.sourceWindowPixels());
            limit("outputWidth", width, limits.outputDimension());
            limit("outputHeight", height, limits.outputDimension());
            limit("outputPixels", pixels, Math.min(limits.outputPixels(), Integer.MAX_VALUE));
        }

        private void chargeIntermediate(long bytes) {
            intermediate =
                    charge(
                            "decodedIntermediateBytes",
                            intermediate,
                            bytes,
                            limits.decodedIntermediateBytes());
        }

        private void chargePublished(long bytes) {
            published = charge("ownedPayloadBytes", published, bytes, limits.ownedPayloadBytes());
        }

        private long charge(String limit, long current, long amount, long maximum) {
            long requested;
            try {
                requested = Math.addExact(current, amount);
            } catch (ArithmeticException ignored) {
                requested = Long.MAX_VALUE;
            }
            limit(limit, requested, maximum);
            return requested;
        }

        private void limit(String limit, long requested, long maximum) {
            if (requested > maximum) {
                throw ImageDiagnostics.limit(sourceId, SCOPE, limit, requested, maximum);
            }
        }

        private void checkpoint() {
            if (TokenFailure.isCancellationRequested(cancellation)) {
                throw ImageDiagnostics.failure(
                        sourceId,
                        "SOURCE_CANCELLED",
                        "image",
                        "Image operation was cancelled",
                        Map.of("operation", OPERATION));
            }
        }
    }

    @SuppressWarnings("serial")
    private static final class TokenFailure extends RuntimeException {
        private final RuntimeException runtimeFailure;
        private final Error errorFailure;

        private TokenFailure(RuntimeException failure) {
            super(null, null, true, false);
            runtimeFailure = failure;
            errorFailure = null;
        }

        private TokenFailure(Error failure) {
            super(null, null, true, false);
            runtimeFailure = null;
            errorFailure = failure;
        }

        private static boolean isCancellationRequested(CancellationToken cancellation) {
            try {
                return cancellation.isCancellationRequested();
            } catch (RuntimeException failure) {
                throw new TokenFailure(failure);
            } catch (Error failure) {
                throw new TokenFailure(failure);
            }
        }

        private RuntimeException original() {
            Throwable original = errorFailure == null ? runtimeFailure : errorFailure;
            for (Throwable cleanupFailure : getSuppressed()) {
                original.addSuppressed(cleanupFailure);
            }
            if (errorFailure != null) {
                throw errorFailure;
            }
            return runtimeFailure;
        }
    }

    private static final class MemoryChannel implements ImageChannel {
        private final byte[] bytes;

        private MemoryChannel(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public int read(ByteBuffer target, long position) {
            if (position >= bytes.length) {
                return -1;
            }
            int count = Math.min(target.remaining(), bytes.length - Math.toIntExact(position));
            target.put(bytes, Math.toIntExact(position), count);
            return count;
        }

        @Override
        public void close() {}
    }
}
