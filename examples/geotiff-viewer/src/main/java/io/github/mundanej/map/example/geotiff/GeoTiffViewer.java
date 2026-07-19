package io.github.mundanej.map.example.geotiff;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
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

/** Minimal viewer for the first bounded GeoTIFF raster profile. */
public final class GeoTiffViewer {
    private GeoTiffViewer() {}

    /**
     * Opens the supplied local GeoTIFF and launches a Swing map view.
     *
     * @param arguments one local GeoTIFF path
     */
    public static void main(String[] arguments) {
        runMain(
                arguments,
                System.err::println,
                source -> EventQueue.invokeLater(() -> show(source)));
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

    private static String summary(RuntimeException failure) {
        if (failure instanceof io.github.mundanej.map.api.SourceException sourceFailure) {
            return sourceFailure.terminal().code() + ": " + sourceFailure.terminal().message();
        }
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
