package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.RasterRenderOptions;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Assertion-bearing PNG/JPEG scenario shared by JVM tests and the native executable. */
@SuppressWarnings("ReferenceEquality")
final class NativeRasterSmokeScenario {
    static final String PNG_SOURCE_ID = "native-raster-png";
    static final String JPEG_SOURCE_ID = "native-raster-jpeg";
    static final String MALFORMED_SOURCE_ID = "native-raster-malformed";
    static final List<EncodedRasterFormat> REGISTRY_ORDER =
            List.of(EncodedRasterFormat.PNG, EncodedRasterFormat.JPEG);
    static final Envelope PNG_ENVELOPE = new Envelope(988.0, 1934.5, 1084.0, 2026.5);
    static final Envelope JPEG_ENVELOPE = new Envelope(2959.5, 884.0, 3187.5, 1036.0);
    private static final int PNG_WIDTH = 192;
    private static final int PNG_HEIGHT = 160;
    private static final int JPEG_WIDTH = 192;
    private static final int JPEG_HEIGHT = 144;
    private static final int PNG_TOLERANCE = 20;
    private static final int JPEG_TOLERANCE = 20;

    private NativeRasterSmokeScenario() {}

    static Result run(NativeRasterPaths paths) {
        require(
                !SwingUtilities.isEventDispatchThread(),
                "raster-registry",
                "source work ran on EDT");
        EncodedRasterDecoderRegistry registry = AwtRasterDecoders.level1();
        require(
                registry.formats().equals(REGISTRY_ORDER),
                "raster-registry",
                "decoder order changed");
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        PngResult png = runPng(paths.png(), crs, registry);
        JpegResult jpeg = runJpeg(paths.jpeg(), crs, registry);
        DiagnosticReport malformed = assertMalformed(paths.malformed(), registry);
        require(png.metadataRetained(), "raster-cache-ownership", "PNG retained values changed");
        require(jpeg.metadataRetained(), "raster-cache-ownership", "JPEG retained values changed");
        require(
                malformed.entries().getLast().code().equals("IMAGE_CONTAINER_INVALID"),
                "raster-diagnostic",
                "malformed report changed after cleanup");
        return new Result(
                registry.formats(), png.nonWhitePixels(), jpeg.nonWhitePixels(), malformed);
    }

    private static PngResult runPng(
            java.nio.file.Path path, CrsMetadata crs, EncodedRasterDecoderRegistry registry) {
        RasterSource source = open(path, PNG_SOURCE_ID, crs, registry);
        var metadata = source.metadata();
        assertMetadata(
                metadata.width(),
                metadata.height(),
                metadata.mapBounds().orElseThrow(),
                metadata.gridPlacement().orElseThrow().affineTransform().orElseThrow(),
                metadata.crs().orElseThrow(),
                4,
                4,
                PNG_ENVELOPE,
                RasterAffineTransform.of(20, 5, 4, -18, 1000, 2000),
                "raster-png-metadata");
        require(
                source.openingDiagnostics().equals(DiagnosticReport.empty()),
                "raster-png-metadata",
                "PNG opening diagnostics were not clean");

        SourceException cancelled =
                expectFailure(
                        () ->
                                source.read(
                                        request(4, 4, 4, 4, RasterInterpolation.NEAREST),
                                        () -> true),
                        "raster-cancel");
        require(
                cancelled.terminal().code().equals("SOURCE_CANCELLED"),
                "raster-cancel",
                "cancelled PNG read diagnostic changed");

        RasterRequest request = request(4, 4, 2, 2, RasterInterpolation.BILINEAR);
        RasterRead first = source.read(request, CancellationToken.none());
        RasterRead second = source.read(request, CancellationToken.none());
        require(first != second, "raster-cache-ownership", "PNG reads reused result identity");
        require(
                first.pixels() != second.pixels() && first.pixels().equals(second.pixels()),
                "raster-cache-ownership",
                "PNG cache did not return independent equal pixels");
        assertReadShape(first, request, "raster-png-bilinear");
        int[] expected = {
            rgba(220, 40, 40, 255),
            rgba(30, 180, 80, 255),
            rgba(40, 90, 220, 255),
            rgba(240, 190, 30, 128)
        };
        assertSamples(first, expected, 0, "raster-png-bilinear");

        int nonWhite =
                renderOwned(
                        source,
                        "png",
                        new RasterRenderOptions(RasterInterpolation.BILINEAR, 0.5),
                        PNG_WIDTH,
                        PNG_HEIGHT,
                        new Probe[] {
                            probe(0, 0, 238, 148, 148),
                            probe(3, 0, 143, 218, 168),
                            probe(0, 3, 148, 173, 238),
                            probe(3, 3, 251, 239, 199)
                        },
                        new Coordinate(990, 1950),
                        PNG_TOLERANCE,
                        "raster-png-opacity");
        require(source.isClosed(), "raster-cache-ownership", "owned PNG source remained open");
        boolean closedReadRejected = false;
        try {
            source.read(request, CancellationToken.none());
        } catch (IllegalStateException expectedFailure) {
            require(
                    expectedFailure.getMessage().contains("closed"),
                    "raster-cache-ownership",
                    "closed PNG source failure changed");
            closedReadRejected = true;
        }
        require(closedReadRejected, "raster-cache-ownership", "closed PNG source accepted a read");
        return new PngResult(nonWhite, metadata.mapBounds().orElseThrow().equals(PNG_ENVELOPE));
    }

    private static JpegResult runJpeg(
            java.nio.file.Path path, CrsMetadata crs, EncodedRasterDecoderRegistry registry) {
        RasterSource source = open(path, JPEG_SOURCE_ID, crs, registry);
        var metadata = source.metadata();
        assertMetadata(
                metadata.width(),
                metadata.height(),
                metadata.mapBounds().orElseThrow(),
                metadata.gridPlacement().orElseThrow().affineTransform().orElseThrow(),
                metadata.crs().orElseThrow(),
                16,
                12,
                JPEG_ENVELOPE,
                RasterAffineTransform.of(12, 2, -3, -10, 3000, 1000),
                "raster-jpeg-decode");
        require(
                source.openingDiagnostics().equals(DiagnosticReport.empty()),
                "raster-jpeg-decode",
                "JPEG opening diagnostics were not clean");

        RasterRequest request = request(16, 12, 4, 3, RasterInterpolation.NEAREST);
        RasterRead first = source.read(request, CancellationToken.none());
        RasterRead second = source.read(request, CancellationToken.none());
        require(
                first.pixels() != second.pixels() && first.pixels().equals(second.pixels()),
                "raster-cache-ownership",
                "JPEG cache did not return independent equal pixels");
        assertReadShape(first, request, "raster-jpeg-decode");
        int[] expected = {
            rgba(200, 50, 50, 255),
            rgba(50, 180, 70, 255),
            rgba(50, 80, 200, 255),
            rgba(220, 190, 40, 255)
        };
        assertCornerSamples(first, expected, JPEG_TOLERANCE, "raster-jpeg-decode");

        int nonWhite =
                renderOwned(
                        source,
                        "jpeg",
                        new RasterRenderOptions(RasterInterpolation.NEAREST, 1.0),
                        JPEG_WIDTH,
                        JPEG_HEIGHT,
                        new Probe[] {
                            probe(2, 2, 200, 50, 50),
                            probe(14, 2, 50, 180, 70),
                            probe(2, 10, 50, 80, 200),
                            probe(14, 10, 220, 190, 40)
                        },
                        new Coordinate(2965, 1020),
                        JPEG_TOLERANCE,
                        "raster-jpeg-affine");
        require(source.isClosed(), "raster-cache-ownership", "owned JPEG source remained open");
        return new JpegResult(nonWhite, metadata.mapBounds().orElseThrow().equals(JPEG_ENVELOPE));
    }

    private static RasterSource open(
            java.nio.file.Path path,
            String sourceId,
            CrsMetadata crs,
            EncodedRasterDecoderRegistry registry) {
        return RasterImages.open(
                path,
                new SourceIdentity(sourceId, "Native raster fixture"),
                ImageOpenOptions.defaults().withPlacement(ImagePlacement.worldFile(crs)),
                registry);
    }

    static DiagnosticReport assertMalformed(
            java.nio.file.Path path, EncodedRasterDecoderRegistry registry) {
        SourceException failure =
                expectFailure(
                        () ->
                                RasterImages.open(
                                        path,
                                        new SourceIdentity(
                                                MALFORMED_SOURCE_ID, "Malformed native PNG"),
                                        ImageOpenOptions.defaults(),
                                        registry),
                        "raster-diagnostic");
        assertMalformedDiagnostic(failure, path);
        return failure.report();
    }

    static void assertMalformedDiagnostic(SourceException failure, java.nio.file.Path path) {
        var terminal = failure.terminal();
        var expectedLocation =
                new DiagnosticLocation(
                        Optional.of("image"),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        OptionalLong.of(54));
        var expected =
                new SourceDiagnostic(
                        "IMAGE_CONTAINER_INVALID",
                        DiagnosticSeverity.ERROR,
                        MALFORMED_SOURCE_ID,
                        Optional.of(expectedLocation),
                        "Encoded image container is invalid",
                        Map.of("format", "PNG", "reason", "chunkCrc"));
        var expectedReport = new DiagnosticReport(java.util.List.of(expected), 0);
        String diagnosticSurface =
                (failure.getMessage() + ' ' + failure.report()).toLowerCase(java.util.Locale.ROOT);
        String normalizedPath =
                path.toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
        String fileName =
                java.util.Objects.requireNonNull(path.getFileName())
                        .toString()
                        .toLowerCase(java.util.Locale.ROOT);
        require(
                !diagnosticSurface.contains(normalizedPath)
                        && !diagnosticSurface.contains(fileName)
                        && !diagnosticSurface.contains("provider")
                        && !diagnosticSurface.contains("imagereader")
                        && !diagnosticSurface.contains("com.sun")
                        && !diagnosticSurface.contains("sun.")
                        && !containsRawByteDump(diagnosticSurface),
                "raster-diagnostic",
                "malformed diagnostic leaked implementation detail");
        require(terminal.equals(expected), "raster-diagnostic", "malformed terminal changed");
        require(
                failure.report().equals(expectedReport),
                "raster-diagnostic",
                "malformed report shape changed");
    }

    private static boolean containsRawByteDump(String value) {
        return java.util.regex.Pattern.compile(
                        "(?:0x[0-9a-f]{2}|(?:[0-9a-f]{2}[\\s,:-]){3}[0-9a-f]{2})")
                .matcher(value)
                .find();
    }

    private static int renderOwned(
            RasterSource source,
            String layerId,
            RasterRenderOptions options,
            int width,
            int height,
            Probe[] probes,
            Coordinate envelopeOnly,
            int tolerance,
            String invariant) {
        return NativeShapefileSmokeScenario.onEdt(
                () -> {
                    require(SwingUtilities.isEventDispatchThread(), invariant, "paint was off EDT");
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857,
                                    SymbolRendererRegistry.builtIn());
                    MapLayerBinding binding =
                            MapLayerBinding.ownedRaster(layerId, layerId, source, options);
                    try (view) {
                        view.setDoubleBuffered(false);
                        view.setSize(width, height);
                        view.setLayerBindings(List.of(binding));
                        view.fitToData(12.0);
                        BufferedImage image = paint(view, width, height);
                        RasterAffineTransform transform =
                                source.metadata()
                                        .gridPlacement()
                                        .orElseThrow()
                                        .affineTransform()
                                        .orElseThrow();
                        for (Probe probe : probes) {
                            Coordinate world = transform.gridToMap(probe.column(), probe.row());
                            assertMajority(
                                    image,
                                    view.mapToScreen(world).orElseThrow(),
                                    probe.color(),
                                    tolerance,
                                    invariant);
                        }
                        assertMajority(
                                image,
                                view.mapToScreen(envelopeOnly).orElseThrow(),
                                Color.WHITE,
                                0,
                                invariant);
                        return assertPaintBounds(
                                image,
                                view,
                                source.metadata().mapBounds().orElseThrow(),
                                invariant);
                    }
                });
    }

    private static BufferedImage paint(MapView view, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        RepaintManager manager = RepaintManager.currentManager(view);
        boolean enabled = manager.isDoubleBufferingEnabled();
        manager.setDoubleBufferingEnabled(false);
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                Graphics2D child = (Graphics2D) graphics.create();
                try {
                    child.setComposite(AlphaComposite.SrcOver);
                    view.paint(child);
                } finally {
                    child.dispose();
                }
            } finally {
                graphics.dispose();
            }
        } finally {
            manager.setDoubleBufferingEnabled(enabled);
        }
        return image;
    }

    private static int assertPaintBounds(
            BufferedImage image, MapView view, Envelope envelope, String invariant) {
        Coordinate first =
                view.mapToScreen(new Coordinate(envelope.minX(), envelope.minY())).orElseThrow();
        Coordinate second =
                view.mapToScreen(new Coordinate(envelope.maxX(), envelope.maxY())).orElseThrow();
        int minX = (int) Math.floor(Math.min(first.x(), second.x())) - 2;
        int maxX = (int) Math.ceil(Math.max(first.x(), second.x())) + 2;
        int minY = (int) Math.floor(Math.min(first.y(), second.y())) - 2;
        int maxY = (int) Math.ceil(Math.max(first.y(), second.y())) + 2;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ff_ffff) != 0x00ff_ffff) {
                    count++;
                    require(
                            x >= minX && x <= maxX && y >= minY && y <= maxY,
                            invariant,
                            "paint escaped affine envelope");
                }
            }
        }
        require(count >= 100 && count <= 25_000, invariant, "paint count outside bound");
        return count;
    }

    static void assertMetadata(
            int width,
            int height,
            Envelope actualEnvelope,
            RasterAffineTransform actualTransform,
            CrsMetadata actualCrs,
            int expectedWidth,
            int expectedHeight,
            Envelope expectedEnvelope,
            RasterAffineTransform expectedTransform,
            String invariant) {
        require(
                width == expectedWidth && height == expectedHeight,
                invariant,
                "dimensions changed");
        require(actualEnvelope.equals(expectedEnvelope), invariant, "envelope changed");
        require(actualTransform.equals(expectedTransform), invariant, "coefficients changed");
        require(
                actualCrs.canonicalIdentifier().equals(Optional.of("EPSG:3857"))
                        && actualCrs.declaredIdentifier().equals(Optional.of("EPSG:3857")),
                invariant,
                "CRS changed");
    }

    private static RasterRequest request(
            int width,
            int height,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation) {
        return new RasterRequest(
                new RasterWindow(0, 0, width, height),
                outputWidth,
                outputHeight,
                interpolation,
                Optional.empty());
    }

    private static void assertReadShape(RasterRead read, RasterRequest request, String invariant) {
        require(read.sourceWindow().equals(request.sourceWindow()), invariant, "window changed");
        require(
                read.pixels().width() == request.outputWidth()
                        && read.pixels().height() == request.outputHeight(),
                invariant,
                "output shape changed");
        require(
                read.diagnostics().equals(DiagnosticReport.empty()),
                invariant,
                "read warnings changed");
    }

    static void assertSamples(RasterRead read, int[] expected, int tolerance, String invariant) {
        for (int index = 0; index < expected.length; index++) {
            require(
                    colorNear(
                            read.pixels().rgbaAt(index % 2, index / 2), expected[index], tolerance),
                    invariant,
                    "sample changed");
        }
    }

    private static void assertCornerSamples(
            RasterRead read, int[] expected, int tolerance, String invariant) {
        int[][] cells = {{0, 0}, {3, 0}, {0, 2}, {3, 2}};
        for (int index = 0; index < cells.length; index++) {
            require(
                    colorNear(
                            read.pixels().rgbaAt(cells[index][0], cells[index][1]),
                            expected[index],
                            tolerance),
                    invariant,
                    "JPEG sample changed");
        }
    }

    private static void assertMajority(
            BufferedImage image,
            Coordinate center,
            Color expected,
            int tolerance,
            String invariant) {
        int centerX = (int) Math.round(center.x());
        int centerY = (int) Math.round(center.y());
        int matches = 0;
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (x >= 0
                        && x < image.getWidth()
                        && y >= 0
                        && y < image.getHeight()
                        && colorNear(image.getRGB(x, y), expected.getRGB(), tolerance)) {
                    matches++;
                }
            }
        }
        require(matches >= 5, invariant, "render probe changed");
    }

    static void assertColorInvariant(int actual, int expected, int tolerance, String invariant) {
        require(colorNear(actual, expected, tolerance), invariant, "sample changed");
    }

    private static boolean colorNear(int actual, int expected, int tolerance) {
        return Math.abs((actual >>> 16 & 0xff) - (expected >>> 16 & 0xff)) <= tolerance
                && Math.abs((actual >>> 8 & 0xff) - (expected >>> 8 & 0xff)) <= tolerance
                && Math.abs((actual & 0xff) - (expected & 0xff)) <= tolerance;
    }

    private static int rgba(int red, int green, int blue, int alpha) {
        return red << 24 | green << 16 | blue << 8 | alpha;
    }

    private static Probe probe(int column, int row, int red, int green, int blue) {
        return new Probe(column, row, new Color(red, green, blue));
    }

    private static SourceException expectFailure(Runnable operation, String invariant) {
        try {
            operation.run();
        } catch (SourceException failure) {
            return failure;
        }
        throw new IllegalStateException(invariant + ": expected source failure");
    }

    private static void require(boolean condition, String invariant, String detail) {
        if (!condition) {
            throw new IllegalStateException(invariant + ": " + detail);
        }
    }

    private record Probe(int column, int row, Color color) {}

    private record PngResult(int nonWhitePixels, boolean metadataRetained) {}

    private record JpegResult(int nonWhitePixels, boolean metadataRetained) {}

    record Result(
            List<EncodedRasterFormat> registryOrder,
            int pngNonWhitePixels,
            int jpegNonWhitePixels,
            DiagnosticReport malformedReport) {
        Result {
            registryOrder = List.copyOf(registryOrder);
        }
    }
}
