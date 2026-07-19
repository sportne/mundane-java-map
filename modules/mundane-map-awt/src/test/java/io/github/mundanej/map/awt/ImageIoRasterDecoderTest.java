package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.RasterResampling;
import io.github.mundanej.map.io.image.EncodedRasterDecodeOptions;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageIoRasterDecoderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void detachedByteHelperDecodesRealPngAndJpegAtNativeSize() throws Exception {
        var png =
                RasterImages.decode(
                        fixtureBytes("rgba-2x2.png.b64"),
                        new SourceIdentity("detached-png", "Detached PNG"),
                        EncodedRasterDecodeOptions.defaults()
                                .expecting(EncodedRasterFormat.PNG)
                                .expectingDimensions(2, 2),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none());
        assertEquals(0xff0000ff, png.rgbaAt(0, 0));
        assertEquals(0x00ff0080, png.rgbaAt(1, 0));
        assertEquals(0x0000ffff, png.rgbaAt(0, 1));
        assertEquals(0x00000000, png.rgbaAt(1, 1));

        var jpeg =
                RasterImages.decode(
                        fixtureBytes("rgb-regions-32x16.jpeg.b64"),
                        new SourceIdentity("detached-jpeg", "Detached JPEG"),
                        EncodedRasterDecodeOptions.defaults()
                                .expecting(EncodedRasterFormat.JPEG)
                                .expectingDimensions(32, 16),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none());
        assertColorNear(0xff0000ff, jpeg.rgbaAt(4, 8), 20);
        assertColorNear(0x0000ffff, jpeg.rgbaAt(27, 8), 20);
    }

    @Test
    void detachedByteHelperPreservesTokenIdentityAndCleanupFailures() throws Exception {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicBoolean disposed = new AtomicBoolean();
        AtomicBoolean inputClosed = new AtomicBoolean();
        ImageIoRasterDecoder decoder =
                new ImageIoRasterDecoder(
                        Map.of(
                                EncodedRasterFormat.PNG,
                                new CancellationCleanupReaderProvider(armed, disposed)),
                        ignored -> new CloseFailingInput(inputClosed));
        IllegalStateException tokenFailure = new IllegalStateException("token failure");

        IllegalStateException observed =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RasterImages.decode(
                                        fixtureBytes("rgba-2x2.png.b64"),
                                        new SourceIdentity("cleanup-token", "Cleanup token"),
                                        EncodedRasterDecodeOptions.defaults(),
                                        EncodedRasterDecoderRegistry.builder()
                                                .register(EncodedRasterFormat.PNG, decoder)
                                                .build(),
                                        () -> {
                                            if (armed.get()) {
                                                throw tokenFailure;
                                            }
                                            return false;
                                        }));

        assertSame(tokenFailure, observed);
        assertTrue(disposed.get());
        assertTrue(inputClosed.get());
        assertEquals(2, observed.getSuppressed().length);
        assertEquals("dispose after cancellation", observed.getSuppressed()[0].getMessage());
        assertEquals("input close failed", observed.getSuppressed()[1].getMessage());
    }

    @Test
    void explicitlyDecodesExactPngAlphaAndNearestWindow() throws Exception {
        Path path =
                fixture(
                        "rgba-2x2.png.b64",
                        "colors.png",
                        79,
                        "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe");

        try (RasterSource source = open(path)) {
            var pixels =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 2, 2), 4, 4, Optional.empty()),
                                    CancellationToken.none())
                            .pixels();
            assertEquals(0xff0000ff, pixels.rgbaAt(0, 0));
            assertEquals(0x00ff0080, pixels.rgbaAt(3, 0));
            assertEquals(0x0000ffff, pixels.rgbaAt(0, 3));
            assertEquals(0x00000000, pixels.rgbaAt(3, 3));
        }
    }

    @Test
    void decodesJpegWithBoundedColorTolerance() throws Exception {
        Path path =
                fixture(
                        "rgb-regions-32x16.jpeg.b64",
                        "colors.JPEG",
                        642,
                        "c24dac6ae511de2680b0b66a83b003058dc3b0e150cb1fc46873243854752990");

        try (RasterSource source = open(path)) {
            var pixels =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 32, 16),
                                            32,
                                            16,
                                            Optional.empty()),
                                    CancellationToken.none())
                            .pixels();
            assertColorNear(0xff0000ff, pixels.rgbaAt(4, 8), 20);
            assertColorNear(0x0000ffff, pixels.rgbaAt(27, 8), 20);
        }
    }

    @Test
    void appliesStrictRegionsExactNearestSubsamplingAndProjectBilinearMath() throws Exception {
        Path png =
                fixture(
                        "rgba-2x2.png.b64",
                        "sampling.png",
                        79,
                        "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe");
        EncodedRasterDecoder decoder =
                AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow();
        assertTrue(decoder.supportsInterpolation(RasterInterpolation.NEAREST));
        assertTrue(decoder.supportsInterpolation(RasterInterpolation.BILINEAR));
        try (RasterSource source = open(png)) {
            var nearest =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 2, 2),
                                            1,
                                            1,
                                            RasterInterpolation.NEAREST,
                                            Optional.empty()),
                                    CancellationToken.none())
                            .pixels();
            assertEquals(0, nearest.rgbaAt(0, 0));

            var bilinear =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 2, 2),
                                            1,
                                            1,
                                            RasterInterpolation.BILINEAR,
                                            Optional.empty()),
                                    CancellationToken.none())
                            .pixels();
            var weights = RasterResampling.bilinearAxis(0, 2, 1);
            assertEquals(
                    RasterResampling.bilinearRgba(
                            0xff0000ff, 0x00ff0080, 0x0000ffff, 0x00000000, weights, weights),
                    bilinear.rgbaAt(0, 0));

            var strictRegion =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(1, 0, 1, 2),
                                            1,
                                            1,
                                            RasterInterpolation.NEAREST,
                                            Optional.empty()),
                                    CancellationToken.none())
                            .pixels();
            assertEquals(0, strictRegion.rgbaAt(0, 0));
        }
    }

    @Test
    void plansIndependentNearestFactorsAndNeverSubsamplesBilinear() {
        var divisible =
                ImageIoRasterDecoder.Subsampling.forContext(
                        new PlanDecodeContext(
                                new RasterWindow(3, 4, 8, 9), 2, 3, RasterInterpolation.NEAREST));
        assertEquals(new ImageIoRasterDecoder.Subsampling(4, 3, 2, 1, 2, 3), divisible);

        var independent =
                ImageIoRasterDecoder.Subsampling.forContext(
                        new PlanDecodeContext(
                                new RasterWindow(3, 4, 7, 9), 2, 3, RasterInterpolation.NEAREST));
        assertEquals(new ImageIoRasterDecoder.Subsampling(1, 3, 0, 1, 7, 3), independent);

        var bilinear =
                ImageIoRasterDecoder.Subsampling.forContext(
                        new PlanDecodeContext(
                                new RasterWindow(3, 4, 8, 9), 2, 3, RasterInterpolation.BILINEAR));
        assertEquals(new ImageIoRasterDecoder.Subsampling(1, 1, 0, 0, 8, 9), bilinear);
    }

    @Test
    void controlledReaderReceivesStrictRegionAndRejectsReturnedShapeMismatch() {
        AtomicReference<ReadHints> observed = new AtomicReference<>();
        ImageIoRasterDecoder decoder =
                new ImageIoRasterDecoder(
                        Map.of(
                                EncodedRasterFormat.PNG,
                                new RecordingReaderProvider(observed, 2, 3)));
        PlanDecodeContext context =
                new PlanDecodeContext(
                        new RasterWindow(3, 4, 8, 9), 2, 3, RasterInterpolation.NEAREST);
        decoder.decode(new ByteArrayInputStream(new byte[] {1}), context);
        assertEquals(new ReadHints(new Rectangle(3, 4, 8, 9), 4, 3, 2, 1), observed.get());

        ImageIoRasterDecoder wrongShape =
                new ImageIoRasterDecoder(
                        Map.of(
                                EncodedRasterFormat.PNG,
                                new RecordingReaderProvider(new AtomicReference<>(), 1, 1)));
        SourceException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        SourceException.class,
                        () -> wrongShape.decode(new ByteArrayInputStream(new byte[] {1}), context));
        assertEquals("IMAGE_DECODE_MISMATCH", failure.terminal().code());
        assertEquals("decodedDimensions", failure.terminal().context().get("field"));
    }

    @Test
    void realPngAndJpegWindowsMatchIndependentExtractedMatrixOracles() throws Exception {
        Path png =
                fixture(
                        "rgba-2x2.png.b64",
                        "matrix.png",
                        79,
                        "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe");
        try (RasterSource source = open(png)) {
            RgbaPixelBuffer matrix =
                    read(source, new RasterWindow(0, 0, 2, 2), 2, 2, RasterInterpolation.NEAREST);
            for (RasterInterpolation interpolation : RasterInterpolation.values()) {
                RasterWindow partial = new RasterWindow(1, 0, 1, 2);
                assertEquals(
                        oracle(matrix, partial, 3, 5, interpolation),
                        read(source, partial, 3, 5, interpolation));
            }
            assertEquals(
                    oracle(
                            matrix,
                            new RasterWindow(0, 0, 2, 2),
                            3,
                            3,
                            RasterInterpolation.BILINEAR),
                    read(source, new RasterWindow(0, 0, 2, 2), 3, 3, RasterInterpolation.BILINEAR));
        }

        Path jpeg =
                fixture(
                        "rgb-regions-32x16.jpeg.b64",
                        "matrix.jpeg",
                        642,
                        "c24dac6ae511de2680b0b66a83b003058dc3b0e150cb1fc46873243854752990");
        try (RasterSource source = open(jpeg)) {
            RgbaPixelBuffer matrix =
                    read(
                            source,
                            new RasterWindow(0, 0, 32, 16),
                            32,
                            16,
                            RasterInterpolation.NEAREST);
            RasterWindow mixedAxis = new RasterWindow(0, 1, 32, 15);
            assertEquals(
                    oracle(matrix, mixedAxis, 8, 4, RasterInterpolation.NEAREST),
                    read(source, mixedAxis, 8, 4, RasterInterpolation.NEAREST));
            RasterWindow partial = new RasterWindow(3, 2, 25, 11);
            assertEquals(
                    oracle(matrix, partial, 7, 5, RasterInterpolation.BILINEAR),
                    read(source, partial, 7, 5, RasterInterpolation.BILINEAR));
        }
    }

    @Test
    void bothModesRetainConservativeOpaqueStageAccounting() throws Exception {
        byte[] png = fixtureBytes("rgba-2x2.png.b64");
        EncodedRasterDecoder decoder =
                AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow();
        for (RasterInterpolation interpolation : RasterInterpolation.values()) {
            AccountingDecodeContext context =
                    new AccountingDecodeContext(png.length, interpolation);
            decoder.decode(new ByteArrayInputStream(png), context);
            assertEquals(png.length + 8L * 4 + 4L, context.claimedBytes());
        }
    }

    @Test
    void returnsFreshExplicitRegistries() {
        var first = AwtRasterDecoders.level1();
        var second = AwtRasterDecoders.level1();
        assertEquals(
                java.util.List.of(
                        io.github.mundanej.map.api.EncodedRasterFormat.PNG,
                        io.github.mundanej.map.api.EncodedRasterFormat.JPEG),
                first.formats());
        assertTrue(
                first.find(io.github.mundanej.map.api.EncodedRasterFormat.PNG).orElseThrow()
                        != second.find(io.github.mundanej.map.api.EncodedRasterFormat.PNG)
                                .orElseThrow());
    }

    @Test
    void rendersConcretePngSourceThroughAnOffscreenMapView() throws Exception {
        Path path =
                fixture(
                        "rgba-2x2.png.b64",
                        "map.png",
                        79,
                        "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe");
        RasterSource source =
                RasterImages.open(
                        path,
                        new SourceIdentity("mapped", "Mapped PNG"),
                        ImageOpenOptions.defaults()
                                .withPlacement(
                                        ImagePlacement.axisAligned(
                                                new Envelope(0, 0, 1, 1), webMercator())),
                        AwtRasterDecoders.level1());
        BufferedImage canvas = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    try {
                        view.setSize(200, 120);
                        view.setLayerBindings(
                                java.util.List.of(
                                        MapLayerBinding.ownedRaster("image", "image", source)));
                        view.fitToData(10);
                        Graphics2D graphics = canvas.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    } finally {
                        view.close();
                    }
                });

        assertColorNear(0xff0000ff, argbToRgba(canvas.getRGB(75, 35)), 2);
        assertColorNear(0x0000ffff, argbToRgba(canvas.getRGB(75, 85)), 2);
        assertTrue(source.isClosed());
    }

    @Test
    void rendersConcreteJpegAtTransformedBoundsThroughAnOffscreenMapView() throws Exception {
        Path path =
                fixture(
                        "rgb-regions-32x16.jpeg.b64",
                        "mapped.jpeg",
                        642,
                        "c24dac6ae511de2680b0b66a83b003058dc3b0e150cb1fc46873243854752990");
        RasterSource source =
                RasterImages.open(
                        path,
                        new SourceIdentity("mapped-jpeg", "Mapped JPEG"),
                        ImageOpenOptions.defaults()
                                .withPlacement(
                                        ImagePlacement.axisAligned(
                                                new Envelope(100, 200, 420, 360), webMercator())),
                        AwtRasterDecoders.level1());
        BufferedImage canvas = new BufferedImage(240, 160, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    try {
                        view.setSize(240, 160);
                        view.setLayerBindings(
                                java.util.List.of(
                                        MapLayerBinding.ownedRaster("jpeg", "jpeg", source)));
                        view.setViewport(new MapViewport(240, 160, 260, 280, 2));
                        Graphics2D graphics = canvas.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    } finally {
                        view.close();
                    }
                });

        assertEquals(0xffffffff, canvas.getRGB(5, 5));
        assertEquals(0xffffffff, canvas.getRGB(120, 20));
        assertColorNear(0xff0000ff, argbToRgba(canvas.getRGB(70, 80)), 20);
        assertColorNear(0x0000ffff, argbToRgba(canvas.getRGB(170, 80)), 20);
        int painted = countPixelsDifferentFrom(canvas, 0xffffffff);
        assertTrue(painted >= 12_700 && painted <= 12_900, () -> "painted=" + painted);
        assertTrue(source.isClosed());
    }

    @Test
    void ignoresBoundedOrdinaryAndCompressedPngMetadataAndRecordsTrailingJpegNonClaim()
            throws Exception {
        byte[] png = fixtureBytes("rgba-2x2.png.b64");
        byte[] ordinaryData = new byte[8 + 65_536];
        System.arraycopy(
                "Comment\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0,
                ordinaryData,
                0,
                8);
        java.util.Arrays.fill(ordinaryData, 8, ordinaryData.length, (byte) 'A');
        byte[] ordinary = insertPngChunk(png, "tEXt", ordinaryData);
        assertDecodeWithHash(
                ordinary,
                "ordinary.png",
                65_635,
                "663833c257d714d39d115614b69e5abd9355b8524cd6fe841c9c44e4bcc7a6fd");

        ByteArrayOutputStream compressedPayload = new ByteArrayOutputStream();
        compressedPayload.writeBytes(
                "Comment\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(compressedPayload)) {
            byte[] expanded = new byte[1_048_576];
            java.util.Arrays.fill(expanded, (byte) 'B');
            compressed.write(expanded);
        }
        byte[] compressed = insertPngChunk(png, "zTXt", compressedPayload.toByteArray());
        assertDecodeWithHash(
                compressed,
                "compressed.png",
                1_140,
                "e6231599ff14b19ac25542ab384719a3899cca4bc18396e6c50746c2391dad92");

        byte[] jpeg = fixtureBytes("rgb-regions-32x16.jpeg.b64");
        byte[] concatenated = new byte[jpeg.length * 2];
        System.arraycopy(jpeg, 0, concatenated, 0, jpeg.length);
        System.arraycopy(jpeg, 0, concatenated, jpeg.length, jpeg.length);
        Path path = temporaryDirectory.resolve("trailing.jpeg");
        Files.write(path, concatenated);
        assertEquals(
                "08b25bac4382e59884b14bb010d5ebcb697309bb0d1b6e422fc577bdf5acd9e7",
                sha256(concatenated));
        SourceException trailingFailure = assertThrows(SourceException.class, () -> open(path));
        assertEquals("IMAGE_CONTAINER_INVALID", trailingFailure.terminal().code());
        assertEquals("trailingData", trailingFailure.terminal().context().get("reason"));
    }

    @Test
    void ignoresLargeJpegAppAndCommentSegmentsWithinTheExactDecodeReservation() throws Exception {
        byte[] jpeg = fixtureBytes("rgb-regions-32x16.jpeg.b64");
        byte[] metadata =
                insertAfterSoi(
                        jpeg,
                        jpegSegment(0xe1, 60_000, (byte) 'C'),
                        jpegSegment(0xfe, 50_000, (byte) 'D'));
        assertEquals(110_650, metadata.length);
        assertEquals(
                "7dd8446150dfeb06fbd43887a7a9e8234c6c01221251ca181228b4afeac26fb0",
                sha256(metadata));
        Path path = temporaryDirectory.resolve("large-app-comment.jpeg");
        Files.write(path, metadata);
        long outputBytes = 4L * 32 * 16;
        long exactIntermediate = 4096 + 2L * metadata.length + 8L * 32 * 16 + outputBytes;
        RasterRequestLimits exact =
                new RasterRequestLimits(32L * 16, 32, 32L * 16, exactIntermediate, outputBytes, 1);
        ImageOpenOptions options =
                ImageOpenOptions.defaults().withRequestLimits(new RasterSourceLimits(exact));
        try (RasterSource source =
                RasterImages.open(
                        path,
                        new SourceIdentity("metadata", "Metadata JPEG"),
                        options,
                        AwtRasterDecoders.level1())) {
            var pixels =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 32, 16),
                                            32,
                                            16,
                                            Optional.empty()),
                                    CancellationToken.none())
                            .pixels();
            assertColorNear(0xff0000ff, pixels.rgbaAt(4, 8), 20);
            assertColorNear(0x0000ffff, pixels.rgbaAt(27, 8), 20);
        }
    }

    @Test
    void checksCancellationBeforeAndAfterRealImageIoAndDuringPixelConversion() throws Exception {
        byte[] png = fixtureBytes("rgba-2x2.png.b64");
        EncodedRasterDecoder decoder =
                AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow();

        PhaseDecodeContext before = new PhaseDecodeContext(1, png.length);
        assertCancelled(decoder, png, before);
        assertEquals(0, before.claimedBytes());

        PhaseDecodeContext after = new PhaseDecodeContext(3, png.length);
        assertCancelled(decoder, png, after);
        assertEquals(png.length + 32, after.claimedBytes());

        PhaseDecodeContext conversion = new PhaseDecodeContext(6, png.length);
        assertCancelled(decoder, png, conversion);
        assertEquals(png.length + 32 + 16, conversion.claimedBytes());
    }

    @Test
    void bothModesCancelAtEveryControlledImageIoAndResamplingStage() throws Exception {
        byte[] png = fixtureBytes("rgba-2x2.png.b64");
        EncodedRasterDecoder decoder =
                AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow();
        for (RasterInterpolation interpolation : RasterInterpolation.values()) {
            for (int checkpoint = 1; checkpoint <= 7; checkpoint++) {
                assertCancelled(
                        decoder,
                        png,
                        new PhaseDecodeContext(checkpoint, png.length, interpolation));
            }
            AccountingDecodeContext successfulContext =
                    new AccountingDecodeContext(png.length, interpolation);
            RgbaPixelBuffer successful =
                    decoder.decode(new ByteArrayInputStream(png), successfulContext);
            assertEquals(1, successful.width());
            assertEquals(1, successful.height());
            int expected =
                    interpolation == RasterInterpolation.NEAREST
                            ? 0
                            : RasterResampling.bilinearRgba(
                                    0xff0000ff,
                                    0x00ff0080,
                                    0x0000ffff,
                                    0x00000000,
                                    RasterResampling.bilinearAxis(0, 2, 1),
                                    RasterResampling.bilinearAxis(0, 2, 1));
            assertEquals(expected, successful.rgbaAt(0, 0));
            assertEquals(png.length + 32 + 4, successfulContext.claimedBytes());
        }
    }

    @Test
    void preservesDecodePrimaryAndAlwaysRunsReverseReaderAndInputCleanup() {
        AtomicBoolean disposed = new AtomicBoolean();
        AtomicBoolean inputClosed = new AtomicBoolean();
        ImageReaderSpi provider = new FailingReaderProvider(disposed);
        ImageIoRasterDecoder decoder =
                new ImageIoRasterDecoder(
                        Map.of(EncodedRasterFormat.PNG, provider),
                        ignored -> new CloseFailingInput(inputClosed));

        SourceException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        SourceException.class,
                        () ->
                                decoder.decode(
                                        new ByteArrayInputStream(new byte[] {1}),
                                        new FixedDecodeContext()));

        assertEquals("IMAGE_DECODE_FAILED", failure.terminal().code());
        assertTrue(disposed.get());
        assertTrue(inputClosed.get());
        assertEquals(2, failure.getSuppressed().length);
        assertEquals("dispose failed", failure.getSuppressed()[0].getMessage());
        assertEquals("input close failed", failure.getSuppressed()[1].getMessage());
    }

    private static RasterSource open(Path path) {
        return RasterImages.open(
                path,
                new SourceIdentity("image", "Image"),
                ImageOpenOptions.defaults(),
                AwtRasterDecoders.level1());
    }

    private Path fixture(String resource, String filename, int length, String sha256)
            throws Exception {
        byte[] bytes = fixtureBytes(resource);
        assertEquals(length, bytes.length);
        assertEquals(
                sha256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Path path = temporaryDirectory.resolve(filename);
        Files.write(path, bytes);
        return path;
    }

    private static byte[] fixtureBytes(String resource) throws Exception {
        try (var input =
                java.util.Objects.requireNonNull(
                        ImageIoRasterDecoderTest.class.getResourceAsStream(
                                "image-fixtures/" + resource))) {
            return Base64.getMimeDecoder().decode(input.readAllBytes());
        }
    }

    private void assertDecodeWithHash(
            byte[] bytes, String filename, int expectedLength, String expectedHash)
            throws Exception {
        assertEquals(expectedLength, bytes.length);
        assertEquals(expectedHash, sha256(bytes));
        Path path = temporaryDirectory.resolve(filename);
        Files.write(path, bytes);
        try (RasterSource source = open(path)) {
            assertEquals(
                    0xff0000ff,
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 2, 2), 2, 2, Optional.empty()),
                                    CancellationToken.none())
                            .pixels()
                            .rgbaAt(0, 0));
        }
    }

    private static byte[] insertPngChunk(byte[] png, String type, byte[] data) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(png.length + data.length + 12);
        output.write(png, 0, 33);
        DataOutputStream chunk = new DataOutputStream(output);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        chunk.writeInt(data.length);
        chunk.write(typeBytes);
        chunk.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        chunk.writeInt((int) crc.getValue());
        output.write(png, 33, png.length - 33);
        return output.toByteArray();
    }

    private static CrsMetadata webMercator() {
        return CrsMetadata.recognized(
                CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
    }

    private static int argbToRgba(int argb) {
        return (argb << 8) | (argb >>> 24);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] jpegSegment(int marker, int payloadLength, byte value) {
        if (payloadLength < 0 || payloadLength > 65_533) {
            throw new IllegalArgumentException("JPEG segment payload length is out of range");
        }
        byte[] segment = new byte[payloadLength + 4];
        segment[0] = (byte) 0xff;
        segment[1] = (byte) marker;
        int declaredLength = payloadLength + 2;
        segment[2] = (byte) (declaredLength >>> 8);
        segment[3] = (byte) declaredLength;
        java.util.Arrays.fill(segment, 4, segment.length, value);
        return segment;
    }

    private static byte[] insertAfterSoi(byte[] jpeg, byte[]... segments) {
        int insertedLength =
                java.util.Arrays.stream(segments).mapToInt(value -> value.length).sum();
        byte[] result = new byte[jpeg.length + insertedLength];
        System.arraycopy(jpeg, 0, result, 0, 2);
        int offset = 2;
        for (byte[] segment : segments) {
            System.arraycopy(segment, 0, result, offset, segment.length);
            offset += segment.length;
        }
        System.arraycopy(jpeg, 2, result, offset, jpeg.length - 2);
        return result;
    }

    private static int countPixelsDifferentFrom(BufferedImage image, int background) {
        int count = 0;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                if (image.getRGB(column, row) != background) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void assertCancelled(
            EncodedRasterDecoder decoder, byte[] bytes, PhaseDecodeContext context) {
        SourceException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        SourceException.class,
                        () -> decoder.decode(new ByteArrayInputStream(bytes), context));
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
    }

    private static void assertColorNear(int expected, int actual, int tolerance) {
        for (int shift : new int[] {24, 16, 8, 0}) {
            int expectedChannel = expected >>> shift & 0xff;
            int actualChannel = actual >>> shift & 0xff;
            assertTrue(
                    Math.abs(expectedChannel - actualChannel) <= tolerance,
                    () -> Integer.toHexString(expected) + " != " + Integer.toHexString(actual));
        }
    }

    private static RgbaPixelBuffer read(
            RasterSource source,
            RasterWindow window,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation) {
        return source.read(
                        new RasterRequest(
                                window, outputWidth, outputHeight, interpolation, Optional.empty()),
                        CancellationToken.none())
                .pixels();
    }

    private static RgbaPixelBuffer oracle(
            RgbaPixelBuffer matrix,
            RasterWindow window,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation) {
        RgbaPixelBuffer.Builder output = RgbaPixelBuffer.builder(outputWidth, outputHeight);
        for (int row = 0; row < outputHeight; row++) {
            TestWeights y = testWeights(row, window.height(), outputHeight);
            for (int column = 0; column < outputWidth; column++) {
                TestWeights x = testWeights(column, window.width(), outputWidth);
                int rgba;
                if (interpolation == RasterInterpolation.NEAREST) {
                    rgba =
                            matrix.rgbaAt(
                                    window.column()
                                            + nearestOracle(column, window.width(), outputWidth),
                                    window.row()
                                            + nearestOracle(row, window.height(), outputHeight));
                } else {
                    rgba =
                            blendOracle(
                                    matrix.rgbaAt(
                                            window.column() + x.lower(), window.row() + y.lower()),
                                    matrix.rgbaAt(
                                            window.column() + x.upper(), window.row() + y.lower()),
                                    matrix.rgbaAt(
                                            window.column() + x.lower(), window.row() + y.upper()),
                                    matrix.rgbaAt(
                                            window.column() + x.upper(), window.row() + y.upper()),
                                    x,
                                    y);
                }
                output.setRgba(column, row, rgba);
            }
        }
        return output.build();
    }

    private static int nearestOracle(int outputIndex, int sourceSize, int outputSize) {
        return (int) (((2L * outputIndex + 1L) * sourceSize) / (2L * outputSize));
    }

    private static TestWeights testWeights(int outputIndex, int sourceSize, int outputSize) {
        long denominator = 2L * outputSize;
        long numerator = (2L * outputIndex + 1L) * sourceSize - outputSize;
        if (sourceSize == 1 || numerator <= 0) {
            return new TestWeights(0, 0, denominator, 0, denominator);
        }
        if (numerator >= (sourceSize - 1L) * denominator) {
            return new TestWeights(sourceSize - 1, sourceSize - 1, denominator, 0, denominator);
        }
        int lower = (int) Math.floorDiv(numerator, denominator);
        long upper = Math.floorMod(numerator, denominator);
        return new TestWeights(lower, lower + 1, denominator - upper, upper, denominator);
    }

    private static int blendOracle(
            int northWest,
            int northEast,
            int southWest,
            int southEast,
            TestWeights x,
            TestWeights y) {
        int[] samples = {northWest, northEast, southWest, southEast};
        long[] weights = {
            x.lowerWeight() * y.lowerWeight(),
            x.upperWeight() * y.lowerWeight(),
            x.lowerWeight() * y.upperWeight(),
            x.upperWeight() * y.upperWeight()
        };
        long total = x.denominator() * y.denominator();
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int index = 0; index < samples.length; index++) {
            int sampleAlpha = samples[index] & 0xff;
            alpha += weights[index] * sampleAlpha;
            red += weights[index] * sampleAlpha * ((samples[index] >>> 24) & 0xff);
            green += weights[index] * sampleAlpha * ((samples[index] >>> 16) & 0xff);
            blue += weights[index] * sampleAlpha * ((samples[index] >>> 8) & 0xff);
        }
        int outputAlpha = (int) ((alpha + total / 2) / total);
        if (outputAlpha == 0) {
            return 0;
        }
        return ((int) ((red + alpha / 2) / alpha) << 24)
                | ((int) ((green + alpha / 2) / alpha) << 16)
                | ((int) ((blue + alpha / 2) / alpha) << 8)
                | outputAlpha;
    }

    private record TestWeights(
            int lower, int upper, long lowerWeight, long upperWeight, long denominator) {}

    private static final class PhaseDecodeContext implements EncodedRasterDecodeContext {
        private final int cancelAtCheckpoint;
        private final long encodedLength;
        private final RasterInterpolation interpolation;
        private int checkpoints;
        private long claimedBytes;

        private PhaseDecodeContext(int cancelAtCheckpoint, long encodedLength) {
            this(cancelAtCheckpoint, encodedLength, RasterInterpolation.NEAREST);
        }

        private PhaseDecodeContext(
                int cancelAtCheckpoint, long encodedLength, RasterInterpolation interpolation) {
            this.cancelAtCheckpoint = cancelAtCheckpoint;
            this.encodedLength = encodedLength;
            this.interpolation = interpolation;
        }

        @Override
        public SourceIdentity sourceIdentity() {
            return new SourceIdentity("cancellation", "cancellation");
        }

        @Override
        public EncodedRasterFormat format() {
            return EncodedRasterFormat.PNG;
        }

        @Override
        public long encodedByteLength() {
            return encodedLength;
        }

        @Override
        public int width() {
            return 2;
        }

        @Override
        public int height() {
            return 2;
        }

        @Override
        public int channelCount() {
            return 4;
        }

        @Override
        public int bitsPerSample() {
            return 8;
        }

        @Override
        public RasterWindow sourceWindow() {
            return new RasterWindow(0, 0, 2, 2);
        }

        @Override
        public int outputWidth() {
            return 2;
        }

        @Override
        public int outputHeight() {
            return 2;
        }

        @Override
        public RasterInterpolation interpolation() {
            return interpolation;
        }

        @Override
        public void checkpoint() {
            if (++checkpoints == cancelAtCheckpoint) {
                SourceDiagnostic diagnostic =
                        new SourceDiagnostic(
                                "SOURCE_CANCELLED",
                                DiagnosticSeverity.ERROR,
                                "cancellation",
                                Optional.empty(),
                                "Cancellation checkpoint reached",
                                Map.of("operation", "test-decode"));
                throw new SourceException(
                        new DiagnosticReport(java.util.List.of(diagnostic), 0), diagnostic);
            }
        }

        @Override
        public void claimReservedIntermediateBytes(long bytes) {
            claimedBytes = Math.addExact(claimedBytes, bytes);
        }

        private long claimedBytes() {
            return claimedBytes;
        }
    }

    private static final class AccountingDecodeContext implements EncodedRasterDecodeContext {
        private final long encodedLength;
        private final RasterInterpolation interpolation;
        private long claimed;

        private AccountingDecodeContext(long encodedLength, RasterInterpolation interpolation) {
            this.encodedLength = encodedLength;
            this.interpolation = interpolation;
        }

        @Override
        public SourceIdentity sourceIdentity() {
            return new SourceIdentity("accounting", "accounting");
        }

        @Override
        public EncodedRasterFormat format() {
            return EncodedRasterFormat.PNG;
        }

        @Override
        public long encodedByteLength() {
            return encodedLength;
        }

        @Override
        public int width() {
            return 2;
        }

        @Override
        public int height() {
            return 2;
        }

        @Override
        public int channelCount() {
            return 4;
        }

        @Override
        public int bitsPerSample() {
            return 8;
        }

        @Override
        public RasterWindow sourceWindow() {
            return new RasterWindow(0, 0, 2, 2);
        }

        @Override
        public int outputWidth() {
            return 1;
        }

        @Override
        public int outputHeight() {
            return 1;
        }

        @Override
        public RasterInterpolation interpolation() {
            return interpolation;
        }

        @Override
        public void checkpoint() {}

        @Override
        public void claimReservedIntermediateBytes(long bytes) {
            claimed = Math.addExact(claimed, bytes);
        }

        private long claimedBytes() {
            return claimed;
        }
    }

    private static final class CloseFailingInput extends MemoryCacheImageInputStream {
        private final AtomicBoolean closed;

        private CloseFailingInput(AtomicBoolean closed) {
            super(new ByteArrayInputStream(new byte[] {1}));
            this.closed = closed;
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            super.close();
            throw new IOException("input close failed");
        }
    }

    private static final class CancellationCleanupReaderProvider extends ImageReaderSpi {
        private final AtomicBoolean armed;
        private final AtomicBoolean disposed;

        private CancellationCleanupReaderProvider(AtomicBoolean armed, AtomicBoolean disposed) {
            this.armed = armed;
            this.disposed = disposed;
        }

        @Override
        public Class<?>[] getInputTypes() {
            return new Class<?>[] {javax.imageio.stream.ImageInputStream.class};
        }

        @Override
        public boolean canDecodeInput(Object source) {
            return true;
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new CancellationCleanupReader(this, armed, disposed);
        }

        @Override
        public String getDescription(java.util.Locale locale) {
            return "cancellation cleanup test reader";
        }
    }

    private static final class CancellationCleanupReader extends ImageReader {
        private final AtomicBoolean armed;
        private final AtomicBoolean disposed;

        private CancellationCleanupReader(
                ImageReaderSpi provider, AtomicBoolean armed, AtomicBoolean disposed) {
            super(provider);
            this.armed = armed;
            this.disposed = disposed;
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            armed.set(true);
            return 2;
        }

        @Override
        public int getHeight(int imageIndex) {
            return 2;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return java.util.List.<ImageTypeSpecifier>of().iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, javax.imageio.ImageReadParam param) {
            throw new AssertionError("read must not be reached");
        }

        @Override
        public void dispose() {
            disposed.set(true);
            throw new IllegalStateException("dispose after cancellation");
        }
    }

    private static final class FailingReaderProvider extends ImageReaderSpi {
        private final AtomicBoolean disposed;

        private FailingReaderProvider(AtomicBoolean disposed) {
            this.disposed = disposed;
        }

        @Override
        public Class<?>[] getInputTypes() {
            return new Class<?>[] {javax.imageio.stream.ImageInputStream.class};
        }

        @Override
        public boolean canDecodeInput(Object source) {
            return true;
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new FailingReader(this, disposed);
        }

        @Override
        public String getDescription(java.util.Locale locale) {
            return "failing test reader";
        }
    }

    private static final class FailingReader extends ImageReader {
        private final AtomicBoolean disposed;

        private FailingReader(ImageReaderSpi provider, AtomicBoolean disposed) {
            super(provider);
            this.disposed = disposed;
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) throws IOException {
            throw new IOException("decode failed");
        }

        @Override
        public int getHeight(int imageIndex) {
            return 1;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return java.util.List.<ImageTypeSpecifier>of().iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, javax.imageio.ImageReadParam param) {
            throw new AssertionError("read must not be reached");
        }

        @Override
        public void dispose() {
            disposed.set(true);
            throw new IllegalStateException("dispose failed");
        }
    }

    private static final class RecordingReaderProvider extends ImageReaderSpi {
        private final AtomicReference<ReadHints> observed;
        private final int returnedWidth;
        private final int returnedHeight;

        private RecordingReaderProvider(
                AtomicReference<ReadHints> observed, int returnedWidth, int returnedHeight) {
            this.observed = observed;
            this.returnedWidth = returnedWidth;
            this.returnedHeight = returnedHeight;
        }

        @Override
        public Class<?>[] getInputTypes() {
            return new Class<?>[] {javax.imageio.stream.ImageInputStream.class};
        }

        @Override
        public boolean canDecodeInput(Object source) {
            return true;
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new RecordingReader(this, observed, returnedWidth, returnedHeight);
        }

        @Override
        public String getDescription(java.util.Locale locale) {
            return "recording test reader";
        }
    }

    private static final class RecordingReader extends ImageReader {
        private final AtomicReference<ReadHints> observed;
        private final int returnedWidth;
        private final int returnedHeight;

        private RecordingReader(
                ImageReaderSpi provider,
                AtomicReference<ReadHints> observed,
                int returnedWidth,
                int returnedHeight) {
            super(provider);
            this.observed = observed;
            this.returnedWidth = returnedWidth;
            this.returnedHeight = returnedHeight;
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            return 11;
        }

        @Override
        public int getHeight(int imageIndex) {
            return 13;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return java.util.List.<ImageTypeSpecifier>of().iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, javax.imageio.ImageReadParam param) {
            observed.set(
                    new ReadHints(
                            param.getSourceRegion(),
                            param.getSourceXSubsampling(),
                            param.getSourceYSubsampling(),
                            param.getSubsamplingXOffset(),
                            param.getSubsamplingYOffset()));
            return new BufferedImage(returnedWidth, returnedHeight, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private record ReadHints(
            Rectangle region, int xFactor, int yFactor, int xOffset, int yOffset) {}

    private static class FixedDecodeContext implements EncodedRasterDecodeContext {
        @Override
        public SourceIdentity sourceIdentity() {
            return new SourceIdentity("cleanup", "cleanup");
        }

        @Override
        public EncodedRasterFormat format() {
            return EncodedRasterFormat.PNG;
        }

        @Override
        public long encodedByteLength() {
            return 1;
        }

        @Override
        public int width() {
            return 1;
        }

        @Override
        public int height() {
            return 1;
        }

        @Override
        public int channelCount() {
            return 4;
        }

        @Override
        public int bitsPerSample() {
            return 8;
        }

        @Override
        public RasterWindow sourceWindow() {
            return new RasterWindow(0, 0, 1, 1);
        }

        @Override
        public int outputWidth() {
            return 1;
        }

        @Override
        public int outputHeight() {
            return 1;
        }

        @Override
        public void checkpoint() {}

        @Override
        public void claimReservedIntermediateBytes(long bytes) {}
    }

    private static final class PlanDecodeContext extends FixedDecodeContext {
        private final RasterWindow window;
        private final int outputWidth;
        private final int outputHeight;
        private final RasterInterpolation interpolation;

        private PlanDecodeContext(
                RasterWindow window,
                int outputWidth,
                int outputHeight,
                RasterInterpolation interpolation) {
            this.window = window;
            this.outputWidth = outputWidth;
            this.outputHeight = outputHeight;
            this.interpolation = interpolation;
        }

        @Override
        public RasterWindow sourceWindow() {
            return window;
        }

        @Override
        public int outputWidth() {
            return outputWidth;
        }

        @Override
        public int outputHeight() {
            return outputHeight;
        }

        @Override
        public RasterInterpolation interpolation() {
            return interpolation;
        }

        @Override
        public int width() {
            return 11;
        }

        @Override
        public int height() {
            return 13;
        }
    }
}
