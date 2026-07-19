package io.github.mundanej.map.example.geotiff;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Runnable viewer for the bounded GeoTIFF raster and elevation profiles. */
public final class GeoTiffViewer {
    private GeoTiffViewer() {}

    /**
     * Opens the supplied local GeoTIFF and launches a Swing map view.
     *
     * @param arguments either one raster path or {@code --elevation-unit UNIT terrain.tif}
     */
    public static void main(String[] arguments) {
        if (arguments.length == 3 && "--elevation-unit".equals(arguments[0])) {
            runElevationMain(
                    arguments,
                    System.err::println,
                    source -> EventQueue.invokeLater(() -> show(source)));
            return;
        }
        runMain(
                arguments,
                System.err::println,
                source -> EventQueue.invokeLater(() -> show(source)));
    }

    static boolean runElevationMain(
            String[] arguments, Consumer<String> failureSink, Consumer<ElevationSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        try {
            ElevationSource source = loadElevation(arguments);
            try {
                launcher.accept(source);
            } catch (RuntimeException | Error failure) {
                source.close();
                throw failure;
            }
            return true;
        } catch (RuntimeException failure) {
            failureSink.accept(summary(failure));
            return false;
        }
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<RasterSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        try {
            RasterSource source = load(parsePath(arguments));
            try {
                launcher.accept(source);
            } catch (RuntimeException | Error failure) {
                source.close();
                throw failure;
            }
            return true;
        } catch (RuntimeException failure) {
            failureSink.accept(summary(failure));
            return false;
        }
    }

    static Path parsePath(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: geotiff-viewer <image.tif>");
        }
        return Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
    }

    static RasterSource load(Path path) {
        Objects.requireNonNull(path, "path");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "GeoTIFF loading must run off the event dispatch thread");
        }
        return GeoTiffFiles.openRaster(
                new SourceIdentity("geotiff-viewer", "GeoTIFF raster"),
                path,
                GeoTiffRasterOptions.defaults());
    }

    static ElevationSource loadElevation(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 3 || !"--elevation-unit".equals(arguments[0])) {
            throw new IllegalArgumentException(
                    "Usage: geotiff-viewer --elevation-unit <METRE|INTERNATIONAL_FOOT|US_SURVEY_FOOT> <terrain.tif>");
        }
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "GeoTIFF loading must run off the event dispatch thread");
        }
        ElevationUnit unit =
                ElevationUnit.valueOf(Objects.requireNonNull(arguments[1], "arguments[1]"));
        Path path = Path.of(Objects.requireNonNull(arguments[2], "arguments[2]"));
        return GeoTiffFiles.openElevation(
                new SourceIdentity("geotiff-viewer", "GeoTIFF elevation"),
                path,
                GeoTiffElevationOptions.of(unit));
    }

    private static void show(RasterSource source) {
        MapView view = createView(source);
        JFrame frame = new JFrame("mundane-java-map — GeoTIFF viewer");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        frame.setSize(900, 640);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        view.close();
                    }
                });
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static void show(ElevationSource source) {
        MapView view = createElevationView(source);
        JFrame frame = new JFrame("mundane-java-map — GeoTIFF elevation viewer");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        frame.setSize(900, 640);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        view.close();
                    }
                });
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    static MapView createView(RasterSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "GeoTIFF view creation must run on the event dispatch thread");
        }
        var crs =
                source.metadata()
                        .crs()
                        .flatMap(CrsMetadata::definition)
                        .orElse(CrsDefinitions.EPSG_4326);
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        try {
            view.setLayerBindings(
                    List.of(MapLayerBinding.ownedRaster("geotiff", "GeoTIFF raster", source)));
            view.setSize(900, 640);
            view.fitToData(20);
            return view;
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
    }

    static MapView createElevationView(ElevationSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "GeoTIFF view creation must run on the event dispatch thread");
        }
        var crs = source.metadata().crs().definition().orElse(CrsDefinitions.EPSG_4326);
        ElevationUnit unit = source.metadata().elevationUnit();
        ElevationRasterStyle style =
                ElevationRasterStyle.of(
                        new ElevationColorRamp(
                                unit,
                                List.of(
                                        new ElevationColorStop(-12_000, Rgba.rgb(12, 48, 96)),
                                        new ElevationColorStop(0, Rgba.rgb(42, 120, 62)),
                                        new ElevationColorStop(9_000, Rgba.rgb(248, 248, 248)))));
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        try {
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.ownedElevation(
                                    "geotiff-elevation", "GeoTIFF elevation", source, style)));
            view.setSize(900, 640);
            view.fitToData(20);
            return view;
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof io.github.mundanej.map.api.SourceException sourceFailure) {
            String context =
                    sourceFailure.terminal().context().entrySet().stream()
                            .sorted(java.util.Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + '=' + entry.getValue())
                            .collect(java.util.stream.Collectors.joining(","));
            return sourceFailure.terminal().code()
                    + (context.isEmpty() ? "" : " [" + context + ']')
                    + ": "
                    + sourceFailure.terminal().message();
        }
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
