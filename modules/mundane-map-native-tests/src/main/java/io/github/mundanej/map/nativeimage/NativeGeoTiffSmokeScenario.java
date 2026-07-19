package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.ElevationQueries;
import io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Assertion-bearing GeoTIFF scenario shared by JVM tests and the native executable. */
final class NativeGeoTiffSmokeScenario {
    static final int IMAGE_SIZE = 144;
    private static final ElevationRasterStyle ELEVATION_STYLE =
            ElevationRasterStyle.of(
                    new ElevationColorRamp(
                            ElevationUnit.METRE,
                            List.of(
                                    new ElevationColorStop(-25_000, Rgba.rgb(30, 70, 170)),
                                    new ElevationColorStop(-10_000, Rgba.rgb(40, 175, 90)),
                                    new ElevationColorStop(0, Rgba.rgb(235, 150, 40)))));

    private NativeGeoTiffSmokeScenario() {}

    static Result run(NativeGeoTiffPaths paths) {
        require(
                !SwingUtilities.isEventDispatchThread(),
                "geotiff-native",
                "source work ran on EDT");
        RasterResult rasterNone =
                runRaster(
                        paths.rasterNone(),
                        "native-geotiff-raster-none",
                        24,
                        16,
                        rgba(51, 145, 88));
        RasterResult rasterDeflate =
                runRaster(
                        paths.rasterDeflate(),
                        "native-geotiff-raster-deflate",
                        16,
                        16,
                        rgba(74, 74, 74));
        ElevationResult elevationPackBits =
                runElevation(
                        paths.elevationPackBits(), "native-geotiff-elevation-packbits", -21_250.0);
        ElevationResult elevationDeflate =
                runElevation(paths.elevationDeflate(), "native-geotiff-elevation-deflate", -106.25);
        DiagnosticReport malformed = assertMalformed(paths.rasterNone());
        return new Result(
                rasterNone, rasterDeflate, elevationPackBits, elevationDeflate, malformed);
    }

    private static RasterResult runRaster(
            Path path, String sourceId, int width, int height, int expectedProbe) {
        RasterSource source =
                GeoTiffFiles.openRaster(
                        new SourceIdentity(sourceId, "Native GeoTIFF raster"),
                        path,
                        GeoTiffRasterOptions.defaults());
        int nonWhite;
        try {
            require(
                    source.metadata().width() == width && source.metadata().height() == height,
                    "geotiff-raster",
                    "dimensions changed");
            int probe =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, width, height),
                                            width,
                                            height,
                                            java.util.Optional.empty()),
                                    CancellationToken.none())
                            .pixels()
                            .rgbaAt(3, 5);
            require(probe == expectedProbe, "geotiff-raster", "decoded sample changed");
            nonWhite = NativeShapefileSmokeScenario.onEdt(() -> renderRasterOwned(source));
        } finally {
            if (!source.isClosed()) {
                source.close();
            }
        }
        require(source.isClosed(), "geotiff-cleanup", "owned raster remained open");
        return new RasterResult(width, height, expectedProbe, nonWhite, source.isClosed());
    }

    private static ElevationResult runElevation(Path path, String sourceId, double expectedProbe) {
        ElevationSource source =
                GeoTiffFiles.openElevation(
                        new SourceIdentity(sourceId, "Native GeoTIFF elevation"),
                        path,
                        GeoTiffElevationOptions.of(ElevationUnit.METRE));
        double first;
        int nonWhite;
        try {
            require(
                    source.metadata().columnCount() == 16 && source.metadata().rowCount() == 16,
                    "geotiff-elevation",
                    "dimensions changed");
            require(
                    source.sample(3, 5).orElseThrow() == expectedProbe,
                    "geotiff-elevation",
                    "decoded sample changed");
            Envelope bounds = source.metadata().sampleBounds();
            first =
                    ElevationQueries.query(
                                    source,
                                    source.metadata().crs().definition().orElseThrow(),
                                    new Coordinate(bounds.minX(), bounds.maxY()),
                                    ElevationQueryMode.NEAREST)
                            .orElseThrow()
                            .value();
            nonWhite = NativeShapefileSmokeScenario.onEdt(() -> renderElevationOwned(source));
        } finally {
            if (!source.isClosed()) {
                source.close();
            }
        }
        require(source.isClosed(), "geotiff-cleanup", "owned elevation remained open");
        return new ElevationResult(expectedProbe, first, nonWhite, source.isClosed());
    }

    static DiagnosticReport assertMalformed(Path valid) {
        byte[] malformed;
        try {
            malformed = Files.readAllBytes(valid);
        } catch (IOException failure) {
            throw new IllegalStateException("geotiff-diagnostic: fixture read failed", failure);
        }
        malformed[2] = 41;
        SourceException failure;
        try {
            GeoTiffFiles.openRaster(
                            new SourceIdentity(
                                    "native-geotiff-malformed", "Malformed native GeoTIFF"),
                            malformed,
                            GeoTiffRasterOptions.defaults())
                    .close();
            throw new IllegalStateException("geotiff-diagnostic: malformed header was accepted");
        } catch (SourceException expected) {
            failure = expected;
        }
        var terminal = failure.terminal();
        require(
                terminal.code().equals("GEOTIFF_HEADER_INVALID")
                        && terminal.severity() == DiagnosticSeverity.ERROR
                        && terminal.sourceId().equals("native-geotiff-malformed")
                        && terminal.location()
                                .orElseThrow()
                                .equals(io.github.mundanej.map.api.DiagnosticLocation.empty())
                        && terminal.message().equals("GeoTIFF header is invalid")
                        && terminal.context().equals(Map.of("field", "version", "reason", "value")),
                "geotiff-diagnostic",
                "terminal diagnostic changed");
        require(
                failure.report().entries().equals(List.of(terminal))
                        && failure.report().omittedWarningCount() == 0,
                "geotiff-diagnostic",
                "terminal report changed");
        return failure.report();
    }

    private static int renderRasterOwned(RasterSource source) {
        require(SwingUtilities.isEventDispatchThread(), "geotiff-render", "paint was off EDT");
        var crs = source.metadata().crs().orElseThrow().definition().orElseThrow();
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        MapLayerBinding binding =
                MapLayerBinding.ownedRaster("native-geotiff-raster", "Native GeoTIFF", source);
        return renderOwned(view, binding);
    }

    private static int renderElevationOwned(ElevationSource source) {
        require(SwingUtilities.isEventDispatchThread(), "geotiff-render", "paint was off EDT");
        var crs = source.metadata().crs().definition().orElseThrow();
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        MapLayerBinding binding =
                MapLayerBinding.ownedElevation(
                        "native-geotiff-elevation",
                        "Native GeoTIFF elevation",
                        source,
                        ELEVATION_STYLE);
        return renderOwned(view, binding);
    }

    private static int renderOwned(MapView view, MapLayerBinding binding) {
        return NativeShapefileSmokeScenario.withRenderOwnership(
                () -> {
                    view.setDoubleBuffered(false);
                    view.setSize(IMAGE_SIZE, IMAGE_SIZE);
                    view.setLayerBindings(List.of(binding));
                    view.fitToData(12);
                    BufferedImage image = paint(view);
                    int count = countNonWhite(image);
                    require(
                            count >= 8_000 && count <= 18_000,
                            "geotiff-render",
                            "rendered footprint changed");
                    return count;
                },
                () -> view.layerBindings().stream().anyMatch(candidate -> candidate == binding),
                binding::close,
                view::close);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        RepaintManager manager = RepaintManager.currentManager(view);
        boolean previous = manager.isDoubleBufferingEnabled();
        manager.setDoubleBufferingEnabled(false);
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setComposite(AlphaComposite.SrcOver);
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            manager.setDoubleBufferingEnabled(previous);
        }
        return image;
    }

    private static int countNonWhite(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int rgba(int red, int green, int blue) {
        return red << 24 | green << 16 | blue << 8 | 0xff;
    }

    private static void require(boolean condition, String invariant, String detail) {
        if (!condition) {
            throw new IllegalStateException(invariant + ": " + detail);
        }
    }

    record RasterResult(
            int width, int height, int probeRgba, int nonWhitePixels, boolean sourceClosed) {}

    record ElevationResult(
            double probeValue, double firstQueryValue, int nonWhitePixels, boolean sourceClosed) {}

    record Result(
            RasterResult rasterNone,
            RasterResult rasterDeflate,
            ElevationResult elevationPackBits,
            ElevationResult elevationDeflate,
            DiagnosticReport malformed) {}
}
