package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageHardeningTest {
    @TempDir Path directory;

    @Test
    void cachesSuccessfulResultsWithFreshOwnershipAndDeterministicLru() throws Exception {
        Path path = directory.resolve("cache.png");
        Files.write(path, png(3, 1));
        AtomicInteger decodes = new AtomicInteger();
        AtomicReference<RgbaPixelBuffer> decoderOwned = new AtomicReference<>();
        EncodedRasterDecoder observingDecoder =
                (input, context) -> {
                    decodes.incrementAndGet();
                    RgbaPixelBuffer result = claimed(context);
                    decoderOwned.set(result);
                    return result;
                };
        ImageRasterSource source = open(path, ImageCachePolicy.bounded(2, 8), observingDecoder);
        RasterRequest first = request(0, 1);
        RasterRequest second = request(1, 1);
        RasterRequest third = request(2, 1);

        RgbaPixelBuffer firstResult = source.read(first, CancellationToken.none()).pixels();
        RgbaPixelBuffer firstHit = source.read(first, CancellationToken.none()).pixels();
        assertNotSame(decoderOwned.get(), firstResult);
        assertNotSame(decoderOwned.get(), firstHit);
        assertNotSame(firstResult, firstHit);
        assertEquals(firstResult, firstHit);
        source.read(second, CancellationToken.none());
        source.read(first, CancellationToken.none());
        source.read(third, CancellationToken.none());
        source.read(second, CancellationToken.none());

        assertEquals(4, decodes.get());
        ImageRasterSource.CacheMetrics metrics = source.cacheMetrics();
        assertEquals(2, metrics.hits());
        assertEquals(4, metrics.misses());
        assertEquals(4, metrics.admissions());
        assertEquals(2, metrics.evictions());
        assertEquals(2, metrics.entries());
        assertEquals(8, metrics.retainedPixelBytes());
        source.close();
        assertEquals(0, source.cacheMetrics().entries());
    }

    @Test
    void disabledOversizedAndAccountingBypassesNeverFailOrAdmit() throws Exception {
        Path path = directory.resolve("bypass.png");
        Files.write(path, png(2, 1));
        AtomicInteger decodes = new AtomicInteger();
        try (ImageRasterSource disabled =
                open(path, ImageCachePolicy.disabled(), decoder(decodes))) {
            disabled.read(request(0, 1), CancellationToken.none());
            disabled.read(request(0, 1), CancellationToken.none());
            assertEquals(2, disabled.cacheMetrics().disabledBypasses());
            assertEquals(0, disabled.cacheMetrics().misses());
        }
        try (ImageRasterSource oversized =
                open(path, ImageCachePolicy.bounded(1, 3), decoder(decodes))) {
            oversized.read(request(0, 1), CancellationToken.none());
            assertEquals(1, oversized.cacheMetrics().oversizedBypasses());
            assertEquals(0, oversized.cacheMetrics().entries());
        }
    }

    @Test
    void enforcesColdHitAndAdmissionAccountingAtEqualityAndPlusOne() throws Exception {
        Path path = directory.resolve("accounting-cache.png");
        byte[] bytes = png(1, 1);
        Files.write(path, bytes);
        long cold = 4096 + 2L * bytes.length + 8 + 4;
        RasterRequestLimits coldOnly = new RasterRequestLimits(1, 1, 1, cold, 4, 1);
        ImageOpenOptions bypassOptions =
                ImageOpenOptions.defaults().withRequestLimits(new RasterSourceLimits(coldOnly));
        try (ImageRasterSource source = open(path, bypassOptions, decoder(new AtomicInteger()))) {
            source.read(request(0, 1), CancellationToken.none());
            assertEquals(1, source.cacheMetrics().accountingBypasses());
            assertEquals(0, source.cacheMetrics().entries());
        }

        RasterRequestLimits admission = new RasterRequestLimits(1, 1, 1, cold + 4, 4, 1);
        AtomicInteger decodes = new AtomicInteger();
        try (ImageRasterSource source =
                open(
                        path,
                        ImageOpenOptions.defaults()
                                .withRequestLimits(new RasterSourceLimits(admission)),
                        decoder(decodes))) {
            source.read(request(0, 1), CancellationToken.none());
            source.read(request(0, 1), CancellationToken.none());
            assertEquals(1, decodes.get());
            assertEquals(1, source.cacheMetrics().admissions());
            assertEquals(1, source.cacheMetrics().hits());

            RasterRequestLimits exactHit = new RasterRequestLimits(1, 1, 1, cold, 4, 1);
            RasterRequest hitAtEquality =
                    new RasterRequest(
                            new RasterWindow(0, 0, 1, 1),
                            1,
                            1,
                            io.github.mundanej.map.api.RasterInterpolation.NEAREST,
                            Optional.of(exactHit));
            source.read(hitAtEquality, CancellationToken.none());
            RasterRequest hitShortByOne =
                    new RasterRequest(
                            new RasterWindow(0, 0, 1, 1),
                            1,
                            1,
                            io.github.mundanej.map.api.RasterInterpolation.NEAREST,
                            Optional.of(new RasterRequestLimits(1, 1, 1, cold - 1, 4, 1)));
            SourceException hitLimit =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(hitShortByOne, CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", hitLimit.terminal().code());
            assertEquals("decodedIntermediateBytes", hitLimit.terminal().context().get("limit"));
        }

        RasterRequestLimits shortByOne = new RasterRequestLimits(1, 1, 1, cold - 1, 4, 1);
        try (ImageRasterSource source =
                open(
                        path,
                        ImageOpenOptions.defaults()
                                .withRequestLimits(new RasterSourceLimits(shortByOne)),
                        decoder(new AtomicInteger()))) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("decodedIntermediateBytes", failure.terminal().context().get("limit"));
        }
    }

    @Test
    void keysInterpolationButExcludeInvocationLimitsAndDuplicateSourceIds() throws Exception {
        Path path = directory.resolve("keys.png");
        Files.write(path, png(1, 1));
        AtomicInteger decodes = new AtomicInteger();
        EncodedRasterDecoder dual =
                new EncodedRasterDecoder() {
                    @Override
                    public boolean supportsInterpolation(
                            io.github.mundanej.map.api.RasterInterpolation interpolation) {
                        return true;
                    }

                    @Override
                    public RgbaPixelBuffer decode(
                            java.io.InputStream input,
                            io.github.mundanej.map.api.EncodedRasterDecodeContext context) {
                        decodes.incrementAndGet();
                        return claimed(context);
                    }
                };
        RasterRequestLimits sameLimits = RasterSourceLimits.LEVEL_1.requestLimits();
        RasterRequest nearestWithLimits =
                new RasterRequest(
                        new RasterWindow(0, 0, 1, 1),
                        1,
                        1,
                        io.github.mundanej.map.api.RasterInterpolation.NEAREST,
                        Optional.of(sameLimits));
        RasterRequest bilinear =
                new RasterRequest(
                        new RasterWindow(0, 0, 1, 1),
                        1,
                        1,
                        io.github.mundanej.map.api.RasterInterpolation.BILINEAR,
                        Optional.empty());
        try (ImageRasterSource first = open(path, ImageCachePolicy.defaults(), dual);
                ImageRasterSource duplicate = open(path, ImageCachePolicy.defaults(), dual)) {
            first.read(request(0, 1), CancellationToken.none());
            first.read(nearestWithLimits, CancellationToken.none());
            first.read(bilinear, CancellationToken.none());
            duplicate.read(request(0, 1), CancellationToken.none());
            assertEquals(3, decodes.get());
            assertEquals(1, first.cacheMetrics().hits());
            assertEquals(2, first.cacheMetrics().entries());
            assertEquals(1, duplicate.cacheMetrics().entries());
        }
    }

    @Test
    void keysOutputShapeAndIgnoresSnapshottedPlacementSidecars() throws Exception {
        Path path = directory.resolve("shape.png");
        Files.write(path, png(2, 1));
        AtomicInteger decodes = new AtomicInteger();
        RasterRequest downsampled =
                new RasterRequest(new RasterWindow(0, 0, 2, 1), 1, 1, Optional.empty());
        RasterRequest nativeSize =
                new RasterRequest(new RasterWindow(0, 0, 2, 1), 2, 1, Optional.empty());
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), decoder(decodes))) {
            source.read(downsampled, CancellationToken.none());
            source.read(nativeSize, CancellationToken.none());
            source.read(downsampled, CancellationToken.none());
            assertEquals(2, decodes.get());
            assertEquals(1, source.cacheMetrics().hits());
            assertEquals(2, source.cacheMetrics().entries());
        }

        Path worldPath = directory.resolve("placed.png");
        Path sidecar = directory.resolve("placed.pgw");
        Files.write(worldPath, png(1, 1));
        Files.writeString(sidecar, "1\n0\n0\n-1\n0\n0\n");
        AtomicInteger placedDecodes = new AtomicInteger();
        ImageOpenOptions placed =
                ImageOpenOptions.defaults().withPlacement(ImagePlacement.worldFile());
        try (ImageRasterSource source = open(worldPath, placed, decoder(placedDecodes))) {
            Envelope captured = source.metadata().mapBounds().orElseThrow();
            source.read(request(0, 1), CancellationToken.none());
            Files.writeString(sidecar, "2\n0\n0\n-2\n100\n100\n");
            source.read(request(0, 1), CancellationToken.none());
            assertEquals(captured, source.metadata().mapBounds().orElseThrow());
            assertEquals(1, placedDecodes.get());
            assertEquals(1, source.cacheMetrics().hits());
        }
    }

    @Test
    void failedAndCancelledWorkNeverPromotesOrAdmits() throws Exception {
        Path path = directory.resolve("failure.png");
        Files.write(path, png(1, 1));
        EncodedRasterDecoder failed =
                (input, context) -> {
                    throw new IllegalStateException("expected");
                };
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), failed)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals(0, source.cacheMetrics().misses());
            assertEquals(0, source.cacheMetrics().admissions());
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        EncodedRasterDecoder cancellation =
                (input, context) -> {
                    RgbaPixelBuffer result = claimed(context);
                    cancelled.set(true);
                    return result;
                };
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), cancellation)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), cancelled::get));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            assertEquals(0, source.cacheMetrics().misses());
            assertEquals(0, source.cacheMetrics().admissions());
        }

        EncodedRasterDecoder partial =
                (input, context) ->
                        RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight())
                                .build();
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), partial)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals(0, source.cacheMetrics().misses());
            assertEquals(0, source.cacheMetrics().admissions());
        }

        EncodedRasterDecoder wrongShape =
                (input, context) -> {
                    context.claimReservedIntermediateBytes(context.encodedByteLength());
                    context.claimReservedIntermediateBytes(8L * context.width() * context.height());
                    context.claimReservedIntermediateBytes(
                            4L * context.outputWidth() * context.outputHeight());
                    return RgbaPixelBuffer.builder(
                                    context.outputWidth() + 1, context.outputHeight())
                            .build();
                };
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), wrongShape)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals(0, source.cacheMetrics().misses());
            assertEquals(0, source.cacheMetrics().admissions());
        }
    }

    @Test
    void metricArithmeticSaturatesWithoutWrapping() {
        assertEquals(Long.MAX_VALUE, ImageRasterSource.increment(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, ImageRasterSource.increment(Long.MAX_VALUE - 1));
        assertEquals(Long.MAX_VALUE, ImageRasterSource.addSaturated(Long.MAX_VALUE - 2, 3));
        assertEquals(Long.MAX_VALUE - 1, ImageRasterSource.addSaturated(Long.MAX_VALUE - 2, 1));
    }

    @Test
    void detectsBodyMutationClearsCacheAndAllowsExactRestoration() throws Exception {
        Path path = directory.resolve("version.png");
        byte[] baseline = png(1, 1);
        Files.write(path, baseline);
        AtomicInteger decodes = new AtomicInteger();
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), decoder(decodes))) {
            source.read(request(0, 1), CancellationToken.none());
            byte[] changed = baseline.clone();
            changed[changed.length - 9] ^= 1;
            Files.write(path, changed);
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals("IMAGE_CONTENT_CHANGED", failure.terminal().code());
            assertEquals("readFingerprint", failure.terminal().context().get("reason"));
            assertEquals(1, source.cacheMetrics().invalidations());
            assertEquals(0, source.cacheMetrics().entries());
            Files.write(path, baseline);
            source.read(request(0, 1), CancellationToken.none());
            assertEquals(2, decodes.get());
        }
    }

    @Test
    void reportsTheExactMutationObservationStage() throws Exception {
        byte[] baseline = png(1, 1);
        byte[] changed = baseline.clone();
        changed[changed.length - 9] ^= 1;

        SourceException openFailure =
                assertThrows(
                        SourceException.class,
                        () -> open(new SwitchingImageChannel(baseline, changed, 2)));
        assertEquals("IMAGE_CONTENT_CHANGED", openFailure.terminal().code());
        assertEquals("openSnapshot", openFailure.terminal().context().get("reason"));

        try (ImageRasterSource source = open(new SwitchingImageChannel(baseline, changed, 3))) {
            SourceException readFailure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals("IMAGE_CONTENT_CHANGED", readFailure.terminal().code());
            assertEquals("readFingerprint", readFailure.terminal().context().get("reason"));
        }

        try (ImageRasterSource source = open(new SwitchingImageChannel(baseline, changed, 4))) {
            SourceException operationFailure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals("IMAGE_CONTENT_CHANGED", operationFailure.terminal().code());
            assertEquals("operationSnapshot", operationFailure.terminal().context().get("reason"));
        }

        byte[] changedHeader = baseline.clone();
        changedHeader[24] ^= 1;
        assertHeaderStage(baseline, changedHeader, 1, "openSnapshot");
        assertHeaderStage(baseline, changedHeader, 3, "readFingerprint");
        assertHeaderStage(baseline, changedHeader, 4, "operationSnapshot");

        assertLengthStage(baseline, 7, "readFingerprint");
        assertLengthStage(baseline, 9, "operationSnapshot");
    }

    @Test
    void reopeningIsRequiredToAdoptAValidNewVersion() throws Exception {
        Path path = directory.resolve("reopen.png");
        Files.write(path, png(1, 1));
        ImageRasterSource original =
                open(path, ImageCachePolicy.defaults(), decoder(new AtomicInteger()));
        original.read(request(0, 1), CancellationToken.none());
        Files.write(path, png(2, 1));
        SourceException oldFailure =
                assertThrows(
                        SourceException.class,
                        () -> original.read(request(0, 1), CancellationToken.none()));
        assertEquals("IMAGE_DECODE_MISMATCH", oldFailure.terminal().code());
        assertEquals("readFingerprint", oldFailure.terminal().context().get("reason"));
        original.close();

        try (ImageRasterSource reopened =
                open(path, ImageCachePolicy.defaults(), decoder(new AtomicInteger()))) {
            assertEquals(2, reopened.metadata().width());
            reopened.read(request(0, 1), CancellationToken.none());
        }
    }

    @Test
    void rejectsStablePhysicalContainerFailuresAndLimits() throws Exception {
        byte[] png = png(1, 1);
        assertContainerFailure("crc.png", flip(png, png.length - 5), "chunkCrc");
        assertContainerFailure("trailing.png", append(png, (byte) 1), "trailingData");
        assertContainerFailure(
                "apng.png",
                insertChunkBeforeEnd(png, "acTL", new byte[8]),
                "IMAGE_PROFILE_UNSUPPORTED",
                "animation");

        Path limit = directory.resolve("limit.png");
        Files.write(limit, png);
        ImageOpenOptions options =
                ImageOpenOptions.defaults()
                        .withImageLimits(
                                ImageSourceLimits.defaults().withMaximumContainerElements(2));
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        limit,
                                        identity(),
                                        options,
                                        registry(decoder(new AtomicInteger()))));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("containerElements", failure.terminal().context().get("limit"));
    }

    @Test
    void serializesIdenticalReadsAndReadCloseWinner() throws Exception {
        Path path = directory.resolve("race.png");
        Files.write(path, png(1, 1));
        AtomicInteger decodes = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EncodedRasterDecoder decoder =
                (input, context) -> {
                    decodes.incrementAndGet();
                    maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    active.decrementAndGet();
                    return claimed(context);
                };
        ImageRasterSource source = open(path, ImageCachePolicy.defaults(), decoder);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var readOne =
                    executor.submit(() -> source.read(request(0, 1), CancellationToken.none()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var readTwo =
                    executor.submit(() -> source.read(request(0, 1), CancellationToken.none()));
            var close = executor.submit(source::close);
            release.countDown();
            readOne.get(5, TimeUnit.SECONDS);
            try {
                readTwo.get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException expectedCloseWinner) {
                assertEquals(
                        IllegalStateException.class, expectedCloseWinner.getCause().getClass());
            }
            close.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, maximum.get());
        assertEquals(1, decodes.get());
        assertThrows(
                IllegalStateException.class,
                () -> source.read(request(0, 1), CancellationToken.none()));
    }

    @Test
    void serializesDistinctReadsAndCloseFailureBehindTheReadWinner() throws Exception {
        byte[] bytes = png(2, 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        EncodedRasterDecoder blocking =
                (input, context) -> {
                    maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    active.decrementAndGet();
                    return claimed(context);
                };
        FailingCloseChannel channel = new FailingCloseChannel(bytes);
        ImageRasterSource source = open(channel, blocking);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var first = executor.submit(() -> source.read(request(0, 1), CancellationToken.none()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var distinct =
                    executor.submit(() -> source.read(request(1, 1), CancellationToken.none()));
            var close = executor.submit(source::close);
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            try {
                distinct.get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException closeWinner) {
                assertEquals(IllegalStateException.class, closeWinner.getCause().getClass());
            }
            java.util.concurrent.ExecutionException closeFailure =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> close.get(5, TimeUnit.SECONDS));
            assertEquals(SourceException.class, closeFailure.getCause().getClass());
        }
        assertEquals(1, maximum.get());
        assertTrue(source.isClosed());
        assertThrows(
                IllegalStateException.class,
                () -> source.read(request(0, 1), CancellationToken.none()));
    }

    @Test
    void cancellationDuringHitAndAdmissionCopiesCannotPromoteOrEvict() throws Exception {
        Path path = directory.resolve("copy-cancel.png");
        Files.write(path, png(1, 1));
        RasterRequest large =
                new RasterRequest(new RasterWindow(0, 0, 1, 1), 5_000, 1, Optional.empty());

        try (ImageRasterSource source =
                open(path, ImageCachePolicy.defaults(), decoder(new AtomicInteger()))) {
            source.read(large, CancellationToken.none());
            AtomicInteger successfulPolls = new AtomicInteger();
            source.read(large, counting(successfulPolls));
            ImageRasterSource.CacheMetrics before = source.cacheMetrics();
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            large,
                                            cancelAt(Math.max(1, successfulPolls.get() - 1))));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            assertEquals(before, source.cacheMetrics());
        }

        AtomicInteger admissionPolls = new AtomicInteger();
        try (ImageRasterSource calibration =
                open(path, ImageCachePolicy.defaults(), decoder(new AtomicInteger()))) {
            calibration.read(large, counting(admissionPolls));
        }
        try (ImageRasterSource source =
                open(path, ImageCachePolicy.bounded(1, 30_000), decoder(new AtomicInteger()))) {
            source.read(request(0, 1), CancellationToken.none());
            ImageRasterSource.CacheMetrics before = source.cacheMetrics();
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            large,
                                            cancelAt(Math.max(1, admissionPolls.get() - 1))));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            assertEquals(before, source.cacheMetrics());
        }
    }

    @Test
    void cancellationBeforeLookupAndWhileWaitingCannotHitOrAdmit() throws Exception {
        Path path = directory.resolve("waiting-cancel.png");
        Files.write(path, png(1, 1));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EncodedRasterDecoder blocking =
                (input, context) -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    return claimed(context);
                };
        try (ImageRasterSource source = open(path, ImageCachePolicy.defaults(), blocking);
                var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> source.read(request(0, 1), CancellationToken.none()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            AtomicBoolean cancelled = new AtomicBoolean(true);
            var waiting = executor.submit(() -> source.read(request(0, 1), cancelled::get));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            java.util.concurrent.ExecutionException waitingFailure =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> waiting.get(5, TimeUnit.SECONDS));
            assertEquals(SourceException.class, waitingFailure.getCause().getClass());
            assertEquals(1, source.cacheMetrics().misses());
            assertEquals(1, source.cacheMetrics().admissions());
            assertEquals(0, source.cacheMetrics().hits());

            SourceException hitCancellation =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), cancelled::get));
            assertEquals("SOURCE_CANCELLED", hitCancellation.terminal().code());
            assertEquals(0, source.cacheMetrics().hits());
        }
    }

    @Test
    void closeFirstAlwaysRejectsLaterReads() throws Exception {
        Path path = directory.resolve("close-first.png");
        Files.write(path, png(1, 1));
        ImageRasterSource source =
                open(path, ImageCachePolicy.defaults(), decoder(new AtomicInteger()));
        source.close();
        assertThrows(
                IllegalStateException.class,
                () -> source.read(request(0, 1), CancellationToken.none()));
        assertEquals(0, source.cacheMetrics().entries());
    }

    private ImageRasterSource open(
            Path path, ImageCachePolicy policy, EncodedRasterDecoder decoder) {
        return open(path, ImageOpenOptions.defaults().withCachePolicy(policy), decoder);
    }

    private ImageRasterSource open(
            Path path, ImageOpenOptions options, EncodedRasterDecoder decoder) {
        return (ImageRasterSource) RasterImages.open(path, identity(), options, registry(decoder));
    }

    private ImageRasterSource open(ImageChannel channel) {
        return open(channel, decoder(new AtomicInteger()));
    }

    private ImageRasterSource open(ImageChannel channel, EncodedRasterDecoder decoder) {
        return (ImageRasterSource)
                RasterImages.open(
                        directory.resolve("scripted.png"),
                        identity(),
                        ImageOpenOptions.defaults(),
                        registry(decoder),
                        CancellationToken.none(),
                        ignored -> channel);
    }

    private static EncodedRasterDecoder decoder(AtomicInteger decodes) {
        return (input, context) -> {
            decodes.incrementAndGet();
            return claimed(context);
        };
    }

    private static CancellationToken counting(AtomicInteger polls) {
        return () -> {
            polls.incrementAndGet();
            return false;
        };
    }

    private static CancellationToken cancelAt(int threshold) {
        AtomicInteger polls = new AtomicInteger();
        return () -> polls.incrementAndGet() >= threshold;
    }

    private void assertHeaderStage(
            byte[] baseline, byte[] changed, int switchRead, String expectedStage) {
        SwitchingImageChannel channel = new SwitchingImageChannel(baseline, changed, switchRead);
        if (switchRead == 1) {
            SourceException failure = assertThrows(SourceException.class, () -> open(channel));
            assertEquals("IMAGE_DECODE_MISMATCH", failure.terminal().code());
            assertEquals(expectedStage, failure.terminal().context().get("reason"));
            return;
        }
        try (ImageRasterSource source = open(channel)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals("IMAGE_DECODE_MISMATCH", failure.terminal().code());
            assertEquals(expectedStage, failure.terminal().context().get("reason"));
        }
    }

    private void assertLengthStage(byte[] baseline, int switchSizeCall, String expectedStage) {
        try (ImageRasterSource source =
                open(new SwitchingSizeImageChannel(baseline, switchSizeCall))) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request(0, 1), CancellationToken.none()));
            assertEquals("IMAGE_FILE_LENGTH_MISMATCH", failure.terminal().code());
            assertEquals(expectedStage, failure.terminal().context().get("reason"));
        }
    }

    private static RgbaPixelBuffer claimed(
            io.github.mundanej.map.api.EncodedRasterDecodeContext context) {
        context.claimReservedIntermediateBytes(context.encodedByteLength());
        context.claimReservedIntermediateBytes(8L * context.width() * context.height());
        context.claimReservedIntermediateBytes(4L * context.outputWidth() * context.outputHeight());
        return RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight())
                .setRgba(0, 0, 0x102030ff)
                .build();
    }

    private static EncodedRasterDecoderRegistry registry(EncodedRasterDecoder decoder) {
        return EncodedRasterDecoderRegistry.builder()
                .register(EncodedRasterFormat.PNG, decoder)
                .build();
    }

    private static SourceIdentity identity() {
        return new SourceIdentity("hardening", "Hardening");
    }

    private static RasterRequest request(int column, int width) {
        return new RasterRequest(new RasterWindow(column, 0, width, 1), 1, 1, Optional.empty());
    }

    private void assertContainerFailure(String name, byte[] bytes, String reason) throws Exception {
        assertContainerFailure(name, bytes, "IMAGE_CONTAINER_INVALID", reason);
    }

    private void assertContainerFailure(String name, byte[] bytes, String code, String reason)
            throws Exception {
        Path path = directory.resolve(name);
        Files.write(path, bytes);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        path,
                                        identity(),
                                        ImageOpenOptions.defaults(),
                                        registry(decoder(new AtomicInteger()))));
        assertEquals(code, failure.terminal().code());
        assertEquals(
                reason,
                failure.terminal()
                        .context()
                        .get(code.equals("IMAGE_PROFILE_UNSUPPORTED") ? "field" : "reason"));
    }

    private static byte[] png(int width, int height) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        ByteBuffer header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
        header.putInt(width)
                .putInt(height)
                .put((byte) 8)
                .put((byte) 6)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        chunk(output, "IHDR", header.array());
        byte[] filtered = new byte[(width * 4 + 1) * height];
        Deflater deflater = new Deflater();
        deflater.setInput(filtered);
        deflater.finish();
        byte[] compressed = new byte[filtered.length + 64];
        int count = deflater.deflate(compressed);
        deflater.end();
        chunk(output, "IDAT", java.util.Arrays.copyOf(compressed, count));
        chunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream output, String type, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(12 + payload.length).order(ByteOrder.BIG_ENDIAN);
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        buffer.putInt(payload.length).put(name).put(payload);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        buffer.putInt((int) crc.getValue());
        output.writeBytes(buffer.array());
    }

    private static byte[] flip(byte[] input, int offset) {
        byte[] changed = input.clone();
        changed[offset] ^= 1;
        return changed;
    }

    private static byte[] append(byte[] input, byte value) {
        byte[] changed = java.util.Arrays.copyOf(input, input.length + 1);
        changed[input.length] = value;
        return changed;
    }

    private static byte[] insertChunkBeforeEnd(byte[] input, String type, byte[] payload) {
        ByteArrayOutputStream inserted = new ByteArrayOutputStream();
        inserted.writeBytes(java.util.Arrays.copyOf(input, input.length - 12));
        chunk(inserted, type, payload);
        inserted.writeBytes(java.util.Arrays.copyOfRange(input, input.length - 12, input.length));
        return inserted.toByteArray();
    }

    private static final class SwitchingImageChannel implements ImageChannel {
        private final byte[] baseline;
        private final byte[] changed;
        private final int switchOnFullRead;
        private int fullReads;

        private SwitchingImageChannel(byte[] baseline, byte[] changed, int switchOnFullRead) {
            this.baseline = baseline.clone();
            this.changed = changed.clone();
            this.switchOnFullRead = switchOnFullRead;
        }

        @Override
        public long size() {
            return baseline.length;
        }

        @Override
        public int read(ByteBuffer target, long position) {
            boolean fullRead = position == 0 && target.remaining() == baseline.length;
            if (fullRead) {
                fullReads++;
            }
            byte[] selected = fullReads >= switchOnFullRead ? changed : baseline;
            if (position >= selected.length) {
                return -1;
            }
            int offset = Math.toIntExact(position);
            int count = Math.min(target.remaining(), selected.length - offset);
            target.put(selected, offset, count);
            return count;
        }

        @Override
        public void close() {}
    }

    private static final class SwitchingSizeImageChannel implements ImageChannel {
        private final byte[] bytes;
        private final int switchSizeCall;
        private int sizeCalls;

        private SwitchingSizeImageChannel(byte[] bytes, int switchSizeCall) {
            this.bytes = bytes.clone();
            this.switchSizeCall = switchSizeCall;
        }

        @Override
        public long size() {
            return ++sizeCalls >= switchSizeCall ? bytes.length + 1L : bytes.length;
        }

        @Override
        public int read(ByteBuffer target, long position) {
            if (position >= bytes.length) {
                return -1;
            }
            int offset = Math.toIntExact(position);
            int count = Math.min(target.remaining(), bytes.length - offset);
            target.put(bytes, offset, count);
            return count;
        }

        @Override
        public void close() {}
    }

    private static final class FailingCloseChannel implements ImageChannel {
        private final byte[] bytes;

        private FailingCloseChannel(byte[] bytes) {
            this.bytes = bytes.clone();
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
            int offset = Math.toIntExact(position);
            int count = Math.min(target.remaining(), bytes.length - offset);
            target.put(bytes, offset, count);
            return count;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("expected close failure");
        }
    }
}
