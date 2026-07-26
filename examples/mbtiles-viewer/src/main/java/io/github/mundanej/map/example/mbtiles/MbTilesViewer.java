package io.github.mundanej.map.example.mbtiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.mbtiles.MbTiles;
import io.github.mundanej.map.io.mbtiles.MbTilesOpenOptions;
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

/** Runnable viewer for one bounded MBTiles raster zoom. */
public final class MbTilesViewer {
    private MbTilesViewer() {}

    /**
     * Opens {@code <absolute.mbtiles> <zoom>} and launches a Swing map view.
     *
     * @param arguments absolute MBTiles path and explicit zoom
     */
    public static void main(String[] arguments) {
        runMain(
                arguments,
                System.err::println,
                source -> EventQueue.invokeLater(() -> launchWindow(source, System.err::println)));
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<RasterSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        try {
            RasterSource source = open(parseArguments(arguments));
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

    static Arguments parseArguments(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: mbtiles-viewer <absolute.mbtiles> <zoom>");
        }
        Path path =
                Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"))
                        .toAbsolutePath()
                        .normalize();
        int zoom;
        try {
            zoom = Integer.parseInt(Objects.requireNonNull(arguments[1], "arguments[1]"));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("zoom must be an integer from 0 through 22");
        }
        if (zoom < 0 || zoom > 22) {
            throw new IllegalArgumentException("zoom must be an integer from 0 through 22");
        }
        return new Arguments(path, zoom);
    }

    static RasterSource open(Arguments arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "MBTiles loading must run off the event dispatch thread");
        }
        return MbTiles.open(
                arguments.path(),
                new SourceIdentity("mbtiles-viewer", "MBTiles raster"),
                arguments.zoom(),
                MbTilesOpenOptions.defaults(),
                AwtRasterDecoders.level1(),
                CancellationToken.none());
    }

    static MapView createView(RasterSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "MBTiles view creation must run on the event dispatch thread");
        }
        CrsDefinition crs =
                source.metadata()
                        .crs()
                        .flatMap(metadata -> metadata.definition())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "MBTiles raster CRS is not recognized"));
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        try {
            view.setLayerBindings(
                    List.of(MapLayerBinding.ownedRaster("mbtiles", "MBTiles raster", source)));
            view.setSize(900, 640);
            view.fitToData(20);
            return view;
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
    }

    static void launchWindow(RasterSource source, Consumer<String> failureSink) {
        launchWindow(source, failureSink, MbTilesViewer::showWindow);
    }

    static void launchWindow(
            RasterSource source, Consumer<String> failureSink, Consumer<MapView> installer) {
        Objects.requireNonNull(installer, "installer");
        try {
            MapView view = createView(source);
            boolean installed = false;
            try {
                installer.accept(view);
                installed = true;
            } finally {
                if (!installed) {
                    view.close();
                }
            }
        } catch (RuntimeException failure) {
            if (!source.isClosed()) {
                source.close();
            }
            failureSink.accept(summary(failure));
        }
    }

    private static void showWindow(MapView view) {
        JFrame frame = new JFrame("mundane-java-map — MBTiles viewer");
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

    record Arguments(Path path, int zoom) {}
}
