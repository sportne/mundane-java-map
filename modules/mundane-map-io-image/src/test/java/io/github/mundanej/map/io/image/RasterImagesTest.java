package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
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
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterImagesTest {
    @TempDir Path temporaryDirectory;

    @Test
    void opensCaseInsensitivePngAndReadsStrictNearestWindows() throws Exception {
        Path path = temporaryDirectory.resolve("sample.PnG");
        byte[] header = pngHeader(4, 3, 8, 6);
        Files.write(path, header);
        EncodedRasterDecoder decoder =
                (input, context) -> {
                    context.claimReservedIntermediateBytes(context.encodedByteLength());
                    context.claimReservedIntermediateBytes(8L * context.width() * context.height());
                    context.claimReservedIntermediateBytes(
                            4L * context.outputWidth() * context.outputHeight());
                    try {
                        assertArrayEquals(header, input.readAllBytes());
                    } catch (IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                    RgbaPixelBuffer.Builder pixels =
                            RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight());
                    for (int row = 0; row < context.outputHeight(); row++) {
                        for (int column = 0; column < context.outputWidth(); column++) {
                            pixels.setRgba(column, row, 0x11223344);
                        }
                    }
                    return pixels.build();
                };
        RasterSource source =
                RasterImages.open(
                        path,
                        new SourceIdentity("image", "image"),
                        ImageOpenOptions.defaults(),
                        registry(EncodedRasterFormat.PNG, decoder));

        assertEquals(4, source.metadata().width());
        assertTrue(source.metadata().mapBounds().isEmpty());
        var read =
                source.read(
                        new RasterRequest(new RasterWindow(1, 1, 2, 2), 1, 3, Optional.empty()),
                        CancellationToken.none());
        assertEquals(0x11223344, read.pixels().rgbaAt(0, 2));
        assertFalse(source.isClosed());
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.read(
                                new RasterRequest(
                                        new RasterWindow(0, 0, 1, 1), 1, 1, Optional.empty()),
                                CancellationToken.none()));
    }

    @Test
    void validatesProfileLimitsFormatDecoderAndMutation() throws Exception {
        Path path = temporaryDirectory.resolve("sample.png");
        Files.write(path, pngHeader(4, 3, 8, 6));
        SourceException missing =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        path,
                                        identity(),
                                        ImageOpenOptions.defaults(),
                                        EncodedRasterDecoderRegistry.builder().build()));
        assertEquals("IMAGE_DECODER_NOT_REGISTERED", missing.terminal().code());

        ImageOpenOptions limited =
                ImageOpenOptions.defaults()
                        .withImageLimits(ImageSourceLimits.defaults().withMaximumWidth(3));
        SourceException limit =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        path,
                                        identity(),
                                        limited,
                                        registry(EncodedRasterFormat.PNG, solidDecoder())));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limit.terminal().code());

        Path mismatch = temporaryDirectory.resolve("wrong.jpg");
        Files.write(mismatch, pngHeader(1, 1, 8, 6));
        assertEquals(
                "IMAGE_FORMAT_MISMATCH",
                assertThrows(
                                SourceException.class,
                                () ->
                                        RasterImages.open(
                                                mismatch,
                                                identity(),
                                                ImageOpenOptions.defaults(),
                                                registry(EncodedRasterFormat.PNG, solidDecoder())))
                        .terminal()
                        .code());

        RasterSource source =
                RasterImages.open(
                        path,
                        identity(),
                        ImageOpenOptions.defaults(),
                        registry(EncodedRasterFormat.PNG, solidDecoder()));
        byte[] changed = Files.readAllBytes(path);
        changed[16] = 1;
        Files.write(path, changed);
        assertEquals(
                "IMAGE_DECODE_MISMATCH",
                assertThrows(
                                SourceException.class,
                                () -> source.read(request(4, 3), CancellationToken.none()))
                        .terminal()
                        .code());
        source.close();
    }

    @Test
    void rejectsUnsupportedAndMalformedHeadersAndCancellation() throws Exception {
        Path unsupported = temporaryDirectory.resolve("sixteen.png");
        Files.write(unsupported, pngHeader(1, 1, 16, 6));
        assertEquals(
                "IMAGE_PROFILE_UNSUPPORTED",
                openFailure(unsupported, EncodedRasterFormat.PNG).terminal().code());
        Path truncated = temporaryDirectory.resolve("short.jpeg");
        Files.write(truncated, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        assertEquals(
                "IMAGE_HEADER_INVALID",
                openFailure(truncated, EncodedRasterFormat.JPEG).terminal().code());
        Path png = temporaryDirectory.resolve("cancel.png");
        Files.write(png, pngHeader(1, 1, 8, 6));
        assertEquals(
                "SOURCE_CANCELLED",
                assertThrows(
                                SourceException.class,
                                () ->
                                        RasterImages.open(
                                                png,
                                                identity(),
                                                ImageOpenOptions.defaults(),
                                                registry(EncodedRasterFormat.PNG, solidDecoder()),
                                                () -> true))
                        .terminal()
                        .code());
    }

    @Test
    void acceptsBaselineAndProgressiveJpegProfilesAtTheHeaderBoundary() throws Exception {
        for (int marker : new int[] {0xc0, 0xc2}) {
            for (int components : new int[] {1, 3}) {
                Path path =
                        temporaryDirectory.resolve("sample-" + marker + '-' + components + ".jpeg");
                Files.write(path, jpegHeader(marker, 3, 2, components));
                EncodedRasterDecoder decoder =
                        (input, context) -> {
                            assertEquals(components, context.channelCount());
                            assertEquals(8, context.bitsPerSample());
                            return claimedSolid(context);
                        };
                try (RasterSource source =
                        RasterImages.open(
                                path,
                                identity(),
                                ImageOpenOptions.defaults(),
                                registry(EncodedRasterFormat.JPEG, decoder))) {
                    assertEquals(3, source.metadata().width());
                    assertEquals(2, source.metadata().height());
                    source.read(request(1, 1), CancellationToken.none());
                }
            }
        }
    }

    @Test
    void acceptsEveryLevelOnePngDepthAndColorPair() throws Exception {
        int[][] profiles = {
            {1, 0, 1}, {2, 0, 1}, {4, 0, 1}, {8, 0, 1}, {8, 2, 3}, {1, 3, 4}, {2, 3, 4}, {4, 3, 4},
            {8, 3, 4}, {8, 4, 2}, {8, 6, 4}
        };
        for (int[] profile : profiles) {
            int depth = profile[0];
            int color = profile[1];
            int channels = profile[2];
            Path path = temporaryDirectory.resolve("profile-" + depth + '-' + color + ".png");
            Files.write(path, pngHeader(1, 1, depth, color));
            EncodedRasterDecoder decoder =
                    (input, context) -> {
                        assertEquals(depth, context.bitsPerSample());
                        assertEquals(channels, context.channelCount());
                        return claimedSolid(context);
                    };
            try (RasterSource source =
                    RasterImages.open(
                            path,
                            identity(),
                            ImageOpenOptions.defaults(),
                            registry(EncodedRasterFormat.PNG, decoder))) {
                source.read(request(1, 1), CancellationToken.none());
            }
        }
    }

    @Test
    void distinguishesUnsupportedProfilesFromCorruptPngAndJpegHeaders() throws Exception {
        Path unsupportedPng = temporaryDirectory.resolve("unsupported.png");
        Files.write(unsupportedPng, pngHeader(1, 1, 16, 6));
        assertEquals(
                "IMAGE_PROFILE_UNSUPPORTED",
                openFailure(unsupportedPng, EncodedRasterFormat.PNG).terminal().code());

        byte[] corruptPngBytes = pngHeader(1, 1, 8, 6);
        corruptPngBytes[29] ^= 1;
        Path corruptPng = temporaryDirectory.resolve("corrupt.png");
        Files.write(corruptPng, corruptPngBytes);
        SourceException corruptPngFailure = openFailure(corruptPng, EncodedRasterFormat.PNG);
        assertEquals("IMAGE_HEADER_INVALID", corruptPngFailure.terminal().code());
        assertEquals("IHDR", corruptPngFailure.terminal().context().get("field"));
        assertEquals("crc", corruptPngFailure.terminal().context().get("reason"));

        int sequence = 0;
        for (byte[] unsupported :
                new byte[][] {jpegHeader(0xc1, 1, 1, 3), jpegHeader(0xc0, 1, 1, 2)}) {
            Path path = temporaryDirectory.resolve("unsupported-" + sequence++ + ".jpeg");
            Files.write(path, unsupported);
            assertEquals(
                    "IMAGE_PROFILE_UNSUPPORTED",
                    openFailure(path, EncodedRasterFormat.JPEG).terminal().code());
        }

        byte[] invalidLength = jpegHeader(0xc0, 1, 1, 3);
        invalidLength[4] = 0;
        invalidLength[5] = 1;
        Path corruptLength = temporaryDirectory.resolve("corrupt-length.jpeg");
        Files.write(corruptLength, invalidLength);
        SourceException lengthFailure = openFailure(corruptLength, EncodedRasterFormat.JPEG);
        assertEquals("IMAGE_HEADER_INVALID", lengthFailure.terminal().code());
        assertEquals("segmentLength", lengthFailure.terminal().context().get("field"));

        byte[] duplicateComponent = jpegHeader(0xc0, 1, 1, 3);
        duplicateComponent[15] = duplicateComponent[12];
        Path corruptComponent = temporaryDirectory.resolve("corrupt-component.jpeg");
        Files.write(corruptComponent, duplicateComponent);
        SourceException componentFailure = openFailure(corruptComponent, EncodedRasterFormat.JPEG);
        assertEquals("IMAGE_HEADER_INVALID", componentFailure.terminal().code());
        assertEquals("component", componentFailure.terminal().context().get("field"));
    }

    @Test
    void enforcesEveryJpegHeaderBoundaryAndClassifiesTruncationAndStandaloneMarkers()
            throws Exception {
        byte[] jpeg = jpegHeader(0xc0, 3, 2, 3);
        Path exact = temporaryDirectory.resolve("exact.jpeg");
        Files.write(exact, jpeg);
        ImageOpenOptions exactOptions =
                ImageOpenOptions.defaults()
                        .withImageLimits(
                                ImageSourceLimits.defaults().withMaximumHeaderBytes(jpeg.length));
        RasterImages.open(
                        exact,
                        identity(),
                        exactOptions,
                        registry(EncodedRasterFormat.JPEG, solidDecoder()))
                .close();
        SourceException shortLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        exact,
                                        identity(),
                                        exactOptions.withImageLimits(
                                                exactOptions
                                                        .imageLimits()
                                                        .withMaximumHeaderBytes(jpeg.length - 1)),
                                        registry(EncodedRasterFormat.JPEG, solidDecoder())));
        assertEquals("SOURCE_LIMIT_EXCEEDED", shortLimit.terminal().code());
        assertEquals("headerBytes", shortLimit.terminal().context().get("limit"));
        assertEquals(
                Integer.toString(jpeg.length), shortLimit.terminal().context().get("requested"));

        Path fill = temporaryDirectory.resolve("fill.jpeg");
        Files.write(fill, insertAfterSoi(jpeg, new byte[] {(byte) 0xff}));
        SourceException fillLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        fill,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withImageLimits(
                                                        ImageSourceLimits.defaults()
                                                                .withMaximumHeaderBytes(3)),
                                        registry(EncodedRasterFormat.JPEG, solidDecoder())));
        assertEquals("SOURCE_LIMIT_EXCEEDED", fillLimit.terminal().code());
        assertEquals("4", fillLimit.terminal().context().get("requested"));

        Path skipped = temporaryDirectory.resolve("skipped.jpeg");
        Files.write(
                skipped,
                insertAfterSoi(jpeg, new byte[] {(byte) 0xff, (byte) 0xe1, 0, 6, 1, 2, 3, 4}));
        SourceException skipLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        skipped,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withImageLimits(
                                                        ImageSourceLimits.defaults()
                                                                .withMaximumHeaderBytes(9)),
                                        registry(EncodedRasterFormat.JPEG, solidDecoder())));
        assertEquals("SOURCE_LIMIT_EXCEEDED", skipLimit.terminal().code());
        assertEquals("10", skipLimit.terminal().context().get("requested"));

        Path truncated = temporaryDirectory.resolve("segment.jpeg");
        Files.write(truncated, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe1, 0});
        SourceException segmentFailure = openFailure(truncated, EncodedRasterFormat.JPEG);
        assertEquals("IMAGE_HEADER_INVALID", segmentFailure.terminal().code());
        assertEquals("segmentLength", segmentFailure.terminal().context().get("field"));
        assertEquals("truncated", segmentFailure.terminal().context().get("reason"));
        assertEquals(
                4, segmentFailure.terminal().location().orElseThrow().byteOffset().orElseThrow());

        for (int marker : new int[] {0xd8, 0xd0, 0x01}) {
            Path standalone = temporaryDirectory.resolve("standalone-" + marker + ".jpeg");
            Files.write(
                    standalone, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) marker});
            SourceException failure = openFailure(standalone, EncodedRasterFormat.JPEG);
            assertEquals("IMAGE_HEADER_INVALID", failure.terminal().code());
            assertEquals("marker", failure.terminal().context().get("field"));
            assertEquals("standaloneBeforeSof", failure.terminal().context().get("reason"));
        }
    }

    @Test
    void checksEveryImageLimitAtEqualityAndPlusOneAndReportsOverflowProspectively()
            throws Exception {
        Path path = temporaryDirectory.resolve("limits.png");
        byte[] header = pngHeader(4, 3, 8, 6);
        Files.write(path, header);
        ImageSourceLimits exact = new ImageSourceLimits(header.length, 1, 4, 3, 12, 4);
        RasterImages.open(
                        path,
                        identity(),
                        ImageOpenOptions.defaults().withImageLimits(exact),
                        registry(EncodedRasterFormat.PNG, solidDecoder()))
                .close();
        assertImageLimit(path, exact.withMaximumEncodedBytes(header.length - 1), "encodedBytes");
        assertImageLimit(path, exact.withMaximumWidth(3), "width");
        assertImageLimit(path, exact.withMaximumHeight(2), "height");
        assertImageLimit(path, exact.withMaximumPixels(11), "pixels");
        assertImageLimit(path, exact.withMaximumLogicalChannels(3), "logicalChannels");

        Path huge = temporaryDirectory.resolve("huge.png");
        Files.write(huge, pngHeader(Integer.MAX_VALUE, Integer.MAX_VALUE, 8, 6));
        ImageOpenOptions hugeOptions =
                new ImageOpenOptions(
                        new ImageSourceLimits(
                                33, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, 4),
                        new RasterSourceLimits(
                                new RasterRequestLimits(
                                        Long.MAX_VALUE, 1, 1, Long.MAX_VALUE, Long.MAX_VALUE, 1)),
                        ImagePlacement.unplaced());
        try (RasterSource source =
                RasterImages.open(
                        huge,
                        identity(),
                        hugeOptions,
                        registry(EncodedRasterFormat.PNG, solidDecoder()))) {
            SourceException overflow =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            new RasterRequest(
                                                    new RasterWindow(
                                                            0,
                                                            0,
                                                            Integer.MAX_VALUE,
                                                            Integer.MAX_VALUE),
                                                    1,
                                                    1,
                                                    Optional.empty()),
                                            CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", overflow.terminal().code());
            assertEquals("decodedIntermediateBytes", overflow.terminal().context().get("limit"));
            assertEquals(
                    Long.toString(Long.MAX_VALUE), overflow.terminal().context().get("requested"));
        }
    }

    @Test
    void accountsFullDecodeReservationsAtEqualityAndRejectsEachCeilingAtPlusOne() throws Exception {
        Path path = temporaryDirectory.resolve("accounting.png");
        Files.write(path, pngHeader(2, 2, 8, 6));
        RasterRequestLimits exact = new RasterRequestLimits(4, 1, 1, 69, 4, 1);
        try (RasterSource source = openWithRequestLimits(path, exact)) {
            assertEquals(
                    0, source.read(request(1, 1), CancellationToken.none()).pixels().rgbaAt(0, 0));
        }

        assertRequestLimit(path, new RasterRequestLimits(3, 1, 1, 69, 4, 1), "sourceWindowPixels");
        assertRequestLimit(
                path, new RasterRequestLimits(4, 1, 1, 68, 4, 1), "decodedIntermediateBytes");
        assertRequestLimit(path, new RasterRequestLimits(4, 1, 1, 69, 3, 1), "ownedPayloadBytes");

        assertRequestLimit(
                path,
                new RasterRequestLimits(4, 1, 4, 1_000, 1_000, 1),
                new RasterRequest(new RasterWindow(0, 0, 2, 2), 2, 1, Optional.empty()),
                "outputWidth");
        assertRequestLimit(
                path,
                new RasterRequestLimits(4, 1, 4, 1_000, 1_000, 1),
                new RasterRequest(new RasterWindow(0, 0, 2, 2), 1, 2, Optional.empty()),
                "outputHeight");
        assertRequestLimit(
                path,
                new RasterRequestLimits(4, 2, 3, 1_000, 1_000, 1),
                new RasterRequest(new RasterWindow(0, 0, 2, 2), 2, 2, Optional.empty()),
                "outputPixels");
    }

    @Test
    void enforcesDecoderClaimsResultShapeHardFenceAndSourceReuse() throws Exception {
        Path path = temporaryDirectory.resolve("contracts.png");
        byte[] header = pngHeader(2, 2, 8, 6);
        Files.write(path, header);
        for (EncodedRasterDecoder decoder :
                java.util.List.<EncodedRasterDecoder>of(
                        (input, context) -> RgbaPixelBuffer.builder(1, 1).build(),
                        (input, context) -> null,
                        (input, context) -> {
                            context.claimReservedIntermediateBytes(context.encodedByteLength());
                            context.claimReservedIntermediateBytes(
                                    8L * context.width() * context.height());
                            context.claimReservedIntermediateBytes(
                                    4L * context.outputWidth() * context.outputHeight());
                            return RgbaPixelBuffer.builder(2, 1).build();
                        },
                        (input, context) -> {
                            context.claimReservedIntermediateBytes(Long.MAX_VALUE);
                            return RgbaPixelBuffer.builder(1, 1).build();
                        })) {
            try (RasterSource source =
                    RasterImages.open(
                            path,
                            identity(),
                            ImageOpenOptions.defaults(),
                            registry(EncodedRasterFormat.PNG, decoder))) {
                assertThrows(
                        IllegalStateException.class,
                        () -> source.read(request(1, 1), CancellationToken.none()));
            }
        }

        AtomicBoolean cancelFirst = new AtomicBoolean(true);
        AtomicBoolean cancelled = new AtomicBoolean();
        EncodedRasterDecoder cancellable =
                (input, context) -> {
                    if (cancelFirst.getAndSet(false)) {
                        cancelled.set(true);
                        context.checkpoint();
                    }
                    return claimedSolid(context);
                };
        try (RasterSource source =
                RasterImages.open(
                        path,
                        identity(),
                        ImageOpenOptions.defaults(),
                        registry(EncodedRasterFormat.PNG, cancellable))) {
            assertEquals(
                    "SOURCE_CANCELLED",
                    assertThrows(
                                    SourceException.class,
                                    () -> source.read(request(1, 1), cancelled::get))
                            .terminal()
                            .code());
            cancelled.set(false);
            assertEquals(
                    0, source.read(request(1, 1), CancellationToken.none()).pixels().rgbaAt(0, 0));
        }

        AtomicBoolean growFirst = new AtomicBoolean(true);
        EncodedRasterDecoder fenced =
                (input, context) -> {
                    if (growFirst.getAndSet(false)) {
                        try {
                            Files.write(path, new byte[] {9, 8, 7}, StandardOpenOption.APPEND);
                            assertEquals(header.length, input.available());
                            assertEquals(header.length, input.skip(Long.MAX_VALUE));
                            assertEquals(0, input.available());
                            assertEquals(-1, input.read());
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                    return claimedSolid(context);
                };
        try (RasterSource source =
                RasterImages.open(
                        path,
                        identity(),
                        ImageOpenOptions.defaults(),
                        registry(EncodedRasterFormat.PNG, fenced))) {
            assertEquals(
                    "IMAGE_FILE_LENGTH_MISMATCH",
                    assertThrows(
                                    SourceException.class,
                                    () -> source.read(request(1, 1), CancellationToken.none()))
                            .terminal()
                            .code());
            Files.write(path, header);
            assertEquals(
                    0, source.read(request(1, 1), CancellationToken.none()).pixels().rgbaAt(0, 0));
        }
    }

    @Test
    void preservesOpeningFailureAndSuppressesChannelCleanupAndMapsPublishedCloseFailure()
            throws Exception {
        byte[] header = pngHeader(1, 1, 8, 6);
        TestImageChannel failingOpen = new TestImageChannel(header, true);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        temporaryDirectory.resolve("failure.png"),
                                        identity(),
                                        ImageOpenOptions.defaults(),
                                        EncodedRasterDecoderRegistry.builder().build(),
                                        CancellationToken.none(),
                                        ignored -> failingOpen));
        assertEquals("IMAGE_DECODER_NOT_REGISTERED", failure.terminal().code());
        assertTrue(failingOpen.closed);
        assertEquals(1, failure.getSuppressed().length);

        TestImageChannel failingClose = new TestImageChannel(header, true);
        RasterSource source =
                RasterImages.open(
                        temporaryDirectory.resolve("close.png"),
                        identity(),
                        ImageOpenOptions.defaults(),
                        registry(EncodedRasterFormat.PNG, solidDecoder()),
                        CancellationToken.none(),
                        ignored -> failingClose);
        SourceException close = assertThrows(SourceException.class, source::close);
        assertEquals("SOURCE_CLOSE_FAILED", close.terminal().code());
        assertTrue(source.isClosed());
        source.close();
    }

    private SourceException openFailure(Path path, EncodedRasterFormat format) {
        return assertThrows(
                SourceException.class,
                () ->
                        RasterImages.open(
                                path,
                                identity(),
                                ImageOpenOptions.defaults(),
                                registry(format, solidDecoder())));
    }

    private static EncodedRasterDecoder solidDecoder() {
        return (input, context) -> claimedSolid(context);
    }

    private static RgbaPixelBuffer claimedSolid(
            io.github.mundanej.map.api.EncodedRasterDecodeContext context) {
        context.claimReservedIntermediateBytes(context.encodedByteLength());
        context.claimReservedIntermediateBytes(8L * context.width() * context.height());
        context.claimReservedIntermediateBytes(4L * context.outputWidth() * context.outputHeight());
        return RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight()).build();
    }

    private static EncodedRasterDecoderRegistry registry(
            EncodedRasterFormat format, EncodedRasterDecoder decoder) {
        return EncodedRasterDecoderRegistry.builder().register(format, decoder).build();
    }

    private static SourceIdentity identity() {
        return new SourceIdentity("image", "Image");
    }

    private static RasterRequest request(int width, int height) {
        return new RasterRequest(
                new RasterWindow(0, 0, width, height), width, height, Optional.empty());
    }

    private static byte[] pngHeader(int width, int height, int depth, int color) {
        ByteBuffer bytes = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN);
        bytes.put(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        bytes.putInt(13).put(new byte[] {'I', 'H', 'D', 'R'});
        bytes.putInt(width).putInt(height).put((byte) depth).put((byte) color);
        bytes.put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(bytes.array(), 12, 17);
        bytes.putInt((int) crc.getValue());
        return bytes.array();
    }

    private static byte[] jpegHeader(int marker, int width, int height, int components) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) marker});
        bytes.write((8 + 3 * components) >>> 8);
        bytes.write(8 + 3 * components);
        bytes.write(8);
        bytes.write(height >>> 8);
        bytes.write(height);
        bytes.write(width >>> 8);
        bytes.write(width);
        bytes.write(components);
        for (int index = 0; index < components; index++) {
            bytes.write(index + 1);
            bytes.write(0x11);
            bytes.write(index);
        }
        return bytes.toByteArray();
    }

    private void assertImageLimit(Path path, ImageSourceLimits limits, String expectedLimit) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        path,
                                        identity(),
                                        ImageOpenOptions.defaults().withImageLimits(limits),
                                        registry(EncodedRasterFormat.PNG, solidDecoder())));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(expectedLimit, failure.terminal().context().get("limit"));
    }

    private RasterSource openWithRequestLimits(Path path, RasterRequestLimits limits) {
        return RasterImages.open(
                path,
                identity(),
                ImageOpenOptions.defaults().withRequestLimits(new RasterSourceLimits(limits)),
                registry(EncodedRasterFormat.PNG, solidDecoder()));
    }

    private void assertRequestLimit(Path path, RasterRequestLimits limits, String expectedLimit)
            throws Exception {
        assertRequestLimit(path, limits, request(1, 1), expectedLimit);
    }

    private void assertRequestLimit(
            Path path, RasterRequestLimits limits, RasterRequest request, String expectedLimit)
            throws Exception {
        try (RasterSource source = openWithRequestLimits(path, limits)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(request, CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals(expectedLimit, failure.terminal().context().get("limit"));
        }
    }

    private static byte[] insertAfterSoi(byte[] jpeg, byte[] inserted) {
        byte[] result = new byte[jpeg.length + inserted.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(inserted, 0, result, 2, inserted.length);
        System.arraycopy(jpeg, 2, result, 2 + inserted.length, jpeg.length - 2);
        return result;
    }

    private static final class TestImageChannel implements ImageChannel {
        private final byte[] bytes;
        private final boolean failClose;
        private boolean closed;

        private TestImageChannel(byte[] bytes, boolean failClose) {
            this.bytes = bytes.clone();
            this.failClose = failClose;
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
        public void close() throws IOException {
            closed = true;
            if (failClose) {
                throw new IOException("close failed");
            }
        }
    }
}
