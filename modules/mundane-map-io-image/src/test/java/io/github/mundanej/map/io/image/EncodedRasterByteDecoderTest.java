package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EncodedRasterByteDecoderTest {
    private static final SourceIdentity ID = new SourceIdentity("byte-image", "Byte image");
    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVR4XmP4z8DwHwgb"
                                    + "GIA0kM3AAAA63AV8XUHyMwAAAABJRU5ErkJggg==");

    @Test
    void optionsRequirePairedPositiveDimensionsWithinBothLimitFamilies() {
        EncodedRasterDecodeOptions defaults = EncodedRasterDecodeOptions.defaults();
        assertTrue(defaults.expectedFormat().isEmpty());
        assertTrue(defaults.expectedWidth().isEmpty());
        assertEquals(
                EncodedRasterFormat.PNG,
                defaults.expecting(EncodedRasterFormat.PNG).expectedFormat().orElseThrow());
        assertEquals(2, defaults.expectingDimensions(2, 2).expectedWidth().orElseThrow());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EncodedRasterDecodeOptions(
                                Optional.empty(),
                                java.util.OptionalInt.of(2),
                                java.util.OptionalInt.empty(),
                                ImageSourceLimits.defaults(),
                                RasterRequestLimits.LEVEL_1));
        assertThrows(
                IllegalArgumentException.class,
                () -> failIfConstructed(defaults.expectingDimensions(0, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        failIfConstructed(
                                defaults.expectingDimensions(2, 2)
                                        .withImageLimits(
                                                ImageSourceLimits.defaults()
                                                        .withMaximumPixels(3))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        failIfConstructed(
                                defaults.expectingDimensions(2, 2)
                                        .withDecodeLimits(
                                                new RasterRequestLimits(4, 1, 4, 1_000, 16, 1))));
    }

    @Test
    void decodesNativeSizeWithAbsentOrMatchingExpectationsAndIndependentResults() {
        EncodedRasterDecoderRegistry registry = registry(claimingDecoder());
        RgbaPixelBuffer first =
                RasterImages.decode(
                        PNG,
                        ID,
                        EncodedRasterDecodeOptions.defaults(),
                        registry,
                        CancellationToken.none());
        RgbaPixelBuffer second =
                RasterImages.decode(
                        PNG,
                        ID,
                        EncodedRasterDecodeOptions.defaults()
                                .expecting(EncodedRasterFormat.PNG)
                                .expectingDimensions(2, 2),
                        registry,
                        CancellationToken.none());

        assertEquals(2, first.width());
        assertEquals(2, first.height());
        assertEquals(0x0000_00ff, first.rgbaAt(0, 0));
        assertEquals(0x0100_00ff, first.rgbaAt(1, 0));
        assertNotSame(first, second);
        assertEquals(first, second);
    }

    @Test
    void snapshotsCallerBytesBeforeDecoderAndNeverExposesMutation() {
        byte[] caller = PNG.clone();
        byte expected = caller[0];
        AtomicReference<Byte> decodedFirst = new AtomicReference<>();
        EncodedRasterDecoder decoder =
                (input, context) -> {
                    caller[0] = 0;
                    try {
                        decodedFirst.set((byte) input.read());
                    } catch (IOException failure) {
                        throw new AssertionError(failure);
                    }
                    return claimed(context);
                };

        RasterImages.decode(
                caller,
                ID,
                EncodedRasterDecodeOptions.defaults(),
                registry(decoder),
                CancellationToken.none());

        assertEquals(expected, decodedFirst.get());
        assertEquals(0, caller[0]);
    }

    @Test
    void expectedFormatAndDimensionsUseClosedExactDiagnosticsBeforeDecoder() {
        AtomicInteger decodes = new AtomicInteger();
        EncodedRasterDecoder decoder =
                (input, context) -> {
                    decodes.incrementAndGet();
                    return claimed(context);
                };
        SourceException format =
                assertThrows(
                        SourceException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults()
                                                .expecting(EncodedRasterFormat.JPEG),
                                        decoder));
        assertDiagnostic(
                format,
                "IMAGE_EXPECTED_FORMAT_MISMATCH",
                Map.of("expectedFormat", "JPEG", "signature", "PNG"));

        SourceException unknown =
                assertThrows(
                        SourceException.class,
                        () ->
                                decode(
                                        new byte[] {1, 2, 3},
                                        EncodedRasterDecodeOptions.defaults()
                                                .expecting(EncodedRasterFormat.PNG),
                                        decoder));
        assertDiagnostic(
                unknown,
                "IMAGE_EXPECTED_FORMAT_MISMATCH",
                Map.of("expectedFormat", "PNG", "signature", "unknown"));

        SourceException dimensions =
                assertThrows(
                        SourceException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults()
                                                .expectingDimensions(1, 1),
                                        decoder));
        assertDiagnostic(
                dimensions,
                "IMAGE_DIMENSIONS_MISMATCH",
                Map.of(
                        "expectedWidth", "1",
                        "expectedHeight", "1",
                        "width", "2",
                        "height", "2"));
        assertEquals(0, decodes.get());
    }

    @Test
    void exactPrimitiveAccountingSucceedsAndEveryOneUnderCeilingFailsProspectively() {
        long pixels = 4;
        long intermediate = 2L * PNG.length + 12L * pixels;
        long published = 4L * pixels;
        ImageSourceLimits imageExact =
                ImageSourceLimits.defaults()
                        .withMaximumEncodedBytes(PNG.length)
                        .withMaximumWidth(2)
                        .withMaximumHeight(2)
                        .withMaximumPixels(pixels);
        RasterRequestLimits decodeExact =
                new RasterRequestLimits(pixels, 2, pixels, intermediate, published, 1);
        EncodedRasterDecodeOptions exact =
                EncodedRasterDecodeOptions.defaults()
                        .withImageLimits(imageExact)
                        .withDecodeLimits(decodeExact);
        assertEquals(2, decode(PNG, exact, claimingDecoder()).width());

        assertLimit(
                PNG,
                exact.withImageLimits(imageExact.withMaximumEncodedBytes(PNG.length - 1)),
                "encodedBytes",
                Integer.toString(PNG.length),
                Integer.toString(PNG.length - 1));
        assertLimit(
                PNG,
                exact.withImageLimits(imageExact.withMaximumPixels(pixels - 1)),
                "pixels",
                Long.toString(pixels),
                Long.toString(pixels - 1));
        assertLimit(
                PNG,
                exact.withDecodeLimits(
                        new RasterRequestLimits(
                                pixels - 1, 1, pixels - 1, intermediate, published, 1)),
                "sourceWindowPixels",
                Long.toString(pixels),
                Long.toString(pixels - 1));
        assertLimit(
                PNG,
                exact.withDecodeLimits(
                        new RasterRequestLimits(pixels, 1, pixels - 1, intermediate, published, 1)),
                "outputWidth",
                "2",
                "1");
        assertLimit(
                PNG,
                exact.withDecodeLimits(
                        new RasterRequestLimits(pixels, 2, pixels - 1, intermediate, published, 1)),
                "outputPixels",
                Long.toString(pixels),
                Long.toString(pixels - 1));
        assertLimit(
                PNG,
                exact.withDecodeLimits(
                        new RasterRequestLimits(pixels, 2, pixels, intermediate - 1, published, 1)),
                "decodedIntermediateBytes",
                Long.toString(intermediate),
                Long.toString(intermediate - 1));
        assertLimit(
                PNG,
                exact.withDecodeLimits(
                        new RasterRequestLimits(pixels, 2, pixels, intermediate, published - 1, 1)),
                "ownedPayloadBytes",
                Long.toString(published),
                Long.toString(published - 1));
    }

    @Test
    void completeContainerValidationPrecedesDecoderInvocation() {
        byte[] corruptCrc = PNG.clone();
        corruptCrc[corruptCrc.length - 1] ^= 1;
        AtomicInteger decodes = new AtomicInteger();
        EncodedRasterDecoder decoder =
                (input, context) -> {
                    decodes.incrementAndGet();
                    return claimed(context);
                };

        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () -> decode(corruptCrc, EncodedRasterDecodeOptions.defaults(), decoder));

        assertDiagnostic(
                failure, "IMAGE_CONTAINER_INVALID", Map.of("format", "PNG", "reason", "chunkCrc"));
        assertEquals(0, decodes.get());
    }

    @Test
    void cancellationAndUnexpectedTokenOrDecoderFailuresPropagateByContract() {
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.decode(
                                        PNG,
                                        ID,
                                        EncodedRasterDecodeOptions.defaults(),
                                        registry(claimingDecoder()),
                                        () -> true));
        assertDiagnostic(cancelled, "SOURCE_CANCELLED", Map.of("operation", "image-decode"));

        AtomicBoolean cancelDuringDecode = new AtomicBoolean();
        SourceException midDecode =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.decode(
                                        PNG,
                                        ID,
                                        EncodedRasterDecodeOptions.defaults(),
                                        registry(
                                                (input, context) -> {
                                                    cancelDuringDecode.set(true);
                                                    context.checkpoint();
                                                    throw new AssertionError();
                                                }),
                                        cancelDuringDecode::get));
        assertDiagnostic(midDecode, "SOURCE_CANCELLED", Map.of("operation", "image-decode"));

        IllegalArgumentException tokenFailure = new IllegalArgumentException("token-canary");
        IllegalArgumentException observedToken =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                RasterImages.decode(
                                        PNG,
                                        ID,
                                        EncodedRasterDecodeOptions.defaults(),
                                        registry(claimingDecoder()),
                                        () -> {
                                            throw tokenFailure;
                                        }));
        assertSame(tokenFailure, observedToken);

        SourceException tokenSourceFailure = maliciousFailure(ID.id());
        SourceException observedTokenSource =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.decode(
                                        PNG,
                                        ID,
                                        EncodedRasterDecodeOptions.defaults(),
                                        registry(claimingDecoder()),
                                        () -> {
                                            throw tokenSourceFailure;
                                        }));
        assertSame(tokenSourceFailure, observedTokenSource);

        AssertionError tokenError = new AssertionError("token-error-canary");
        AssertionError observedTokenError =
                assertThrows(
                        AssertionError.class,
                        () ->
                                RasterImages.decode(
                                        PNG,
                                        ID,
                                        EncodedRasterDecodeOptions.defaults(),
                                        registry(claimingDecoder()),
                                        () -> {
                                            throw tokenError;
                                        }));
        assertSame(tokenError, observedTokenError);

        IllegalStateException decoderFailure = new IllegalStateException("decoder-canary");
        IllegalStateException observedDecoder =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults(),
                                        (input, context) -> {
                                            throw decoderFailure;
                                        }));
        assertSame(decoderFailure, observedDecoder);

        AssertionError decoderError = new AssertionError("decoder-error-canary");
        AssertionError observedError =
                assertThrows(
                        AssertionError.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults(),
                                        (input, context) -> {
                                            throw decoderError;
                                        }));
        assertSame(decoderError, observedError);
    }

    @Test
    void decoderContractRejectsMissingSupportClaimsShapeAndCheckedLeakCanaries() {
        SourceException missing =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.decode(
                                        PNG,
                                        ID,
                                        EncodedRasterDecodeOptions.defaults(),
                                        EncodedRasterDecoderRegistry.builder().build(),
                                        CancellationToken.none()));
        assertDiagnostic(missing, "IMAGE_DECODER_NOT_REGISTERED", Map.of("format", "PNG"));

        EncodedRasterDecoder unsupported =
                new EncodedRasterDecoder() {
                    @Override
                    public boolean supportsInterpolation(
                            io.github.mundanej.map.api.RasterInterpolation interpolation) {
                        return false;
                    }

                    @Override
                    public RgbaPixelBuffer decode(
                            InputStream input, EncodedRasterDecodeContext context) {
                        throw new AssertionError();
                    }
                };
        assertThrows(
                IllegalStateException.class,
                () -> decode(PNG, EncodedRasterDecodeOptions.defaults(), unsupported));

        EncodedRasterDecoder leakingSupportProbe =
                new EncodedRasterDecoder() {
                    @Override
                    public boolean supportsInterpolation(
                            io.github.mundanej.map.api.RasterInterpolation interpolation) {
                        throw maliciousFailure(ID.id());
                    }

                    @Override
                    public RgbaPixelBuffer decode(
                            InputStream input, EncodedRasterDecodeContext context) {
                        throw new AssertionError();
                    }
                };
        IllegalStateException supportContract =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults(),
                                        leakingSupportProbe));
        assertEquals(
                "Decoder emitted an unsupported checked diagnostic", supportContract.getMessage());
        assertFalse(supportContract.toString().contains("secret-byte-canary"));
        assertTrue(supportContract.getCause() == null);

        assertThrows(
                IllegalStateException.class,
                () ->
                        decode(
                                PNG,
                                EncodedRasterDecodeOptions.defaults(),
                                (input, context) -> RgbaPixelBuffer.builder(1, 1).build()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        decode(
                                PNG,
                                EncodedRasterDecodeOptions.defaults(),
                                (input, context) ->
                                        RgbaPixelBuffer.builder(context.width(), context.height())
                                                .build()));

        IllegalStateException contract =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults(),
                                        (input, context) -> {
                                            throw maliciousFailure(context.sourceIdentity().id());
                                        }));
        assertFalse(contract.toString().contains("secret-byte-canary"));
        assertTrue(contract.getSuppressed().length == 0);
        assertTrue(contract.getCause() == null);

        IllegalStateException missingLocation =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults(),
                                        (input, context) -> {
                                            throw allowedFailureWithoutLocation(
                                                    context.sourceIdentity().id());
                                        }));
        assertEquals(
                "Decoder emitted an unsupported checked diagnostic", missingLocation.getMessage());

        SourceException sanitized =
                assertThrows(
                        SourceException.class,
                        () ->
                                decode(
                                        PNG,
                                        EncodedRasterDecodeOptions.defaults(),
                                        (input, context) -> {
                                            SourceException declared =
                                                    ImageDiagnostics.failure(
                                                            context.sourceIdentity().id(),
                                                            "IMAGE_DECODE_FAILED",
                                                            "decoder",
                                                            "secret-byte-canary",
                                                            Map.of(
                                                                    "format",
                                                                    "PNG",
                                                                    "reason",
                                                                    "codec",
                                                                    "causeKind",
                                                                    "IOException"));
                                            throw new SourceException(
                                                    declared.report(),
                                                    declared.terminal(),
                                                    new IOException("secret-byte-canary"));
                                        }));
        assertDiagnostic(
                sanitized,
                "IMAGE_DECODE_FAILED",
                Map.of("format", "PNG", "reason", "codec", "causeKind", "IOException"));
        assertEquals("Image decoder failed", sanitized.getMessage());
        assertTrue(sanitized.getCause() == null);
        assertFalse(sanitized.toString().contains("secret-byte-canary"));
    }

    private static RgbaPixelBuffer decode(
            byte[] bytes, EncodedRasterDecodeOptions options, EncodedRasterDecoder decoder) {
        return RasterImages.decode(bytes, ID, options, registry(decoder), CancellationToken.none());
    }

    private static void failIfConstructed(Object unexpected) {
        throw new AssertionError("Invalid options were constructed: " + unexpected);
    }

    private static EncodedRasterDecoderRegistry registry(EncodedRasterDecoder decoder) {
        return EncodedRasterDecoderRegistry.builder()
                .register(EncodedRasterFormat.PNG, decoder)
                .build();
    }

    private static EncodedRasterDecoder claimingDecoder() {
        return (input, context) -> claimed(context);
    }

    private static RgbaPixelBuffer claimed(EncodedRasterDecodeContext context) {
        long pixels = Math.multiplyExact((long) context.width(), context.height());
        context.claimReservedIntermediateBytes(context.encodedByteLength());
        context.claimReservedIntermediateBytes(Math.multiplyExact(pixels, 8));
        context.claimReservedIntermediateBytes(Math.multiplyExact(pixels, 4));
        RgbaPixelBuffer.Builder result =
                RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight());
        for (int row = 0; row < context.outputHeight(); row++) {
            for (int column = 0; column < context.outputWidth(); column++) {
                result.setRgba(column, row, column << 24 | row << 16 | 0xff);
            }
        }
        return result.build();
    }

    private static SourceException maliciousFailure(String sourceId) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "IMAGE_CONTAINER_INVALID",
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        "secret-byte-canary",
                        Map.of("raw", "secret-byte-canary"));
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static SourceException allowedFailureWithoutLocation(String sourceId) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "IMAGE_DECODE_FAILED",
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.empty(),
                        "not trusted",
                        Map.of("format", "PNG", "reason", "codec"));
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static void assertLimit(
            byte[] bytes,
            EncodedRasterDecodeOptions options,
            String limit,
            String requested,
            String maximum) {
        SourceException failure =
                assertThrows(
                        SourceException.class, () -> decode(bytes, options, claimingDecoder()));
        assertDiagnostic(
                failure,
                "SOURCE_LIMIT_EXCEEDED",
                Map.of(
                        "scope",
                        "imageDecode",
                        "limit",
                        limit,
                        "requested",
                        requested,
                        "maximum",
                        maximum));
    }

    private static void assertDiagnostic(
            SourceException failure, String code, Map<String, String> context) {
        assertEquals(code, failure.terminal().code());
        assertEquals(ID.id(), failure.terminal().sourceId());
        assertEquals(DiagnosticSeverity.ERROR, failure.terminal().severity());
        assertEquals(context, failure.terminal().context());
        assertEquals(List.of(failure.terminal()), failure.report().entries());
        assertEquals(0, failure.report().omittedWarningCount());
    }
}
