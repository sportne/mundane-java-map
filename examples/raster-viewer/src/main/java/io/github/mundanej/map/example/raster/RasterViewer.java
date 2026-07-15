package io.github.mundanej.map.example.raster;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

/** Runnable bounded PNG/JPEG viewer with explicit normalized demonstration placement. */
public final class RasterViewer {
    static final String PLACEMENT_LABEL = "Normalized demonstration placement — not georeferenced";

    private RasterViewer() {}

    /** Validates one image argument, opens it off EDT, and launches the Swing viewer. */
    public static void main(String[] arguments) {
        runMain(arguments, System.err::println);
    }

    static boolean runMain(String[] arguments, Consumer<String> failureSink) {
        return runMain(arguments, failureSink, RasterViewer::launch);
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<RasterSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        try {
            RasterSource source = load(parseArguments(arguments));
            try {
                launcher.accept(source);
            } catch (RuntimeException | Error failure) {
                closeSuppressing(source, failure);
                throw failure;
            }
            return true;
        } catch (RuntimeException failure) {
            failureSink.accept(summary(failure));
            return false;
        }
    }

    static Path parseArguments(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: raster-viewer <image.png-or-jpeg>");
        }
        return Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
    }

    static RasterSource load(Path path) {
        Objects.requireNonNull(path, "path");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Raster loading must run off the event dispatch thread");
        }
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        ImageOpenOptions options =
                ImageOpenOptions.defaults()
                        .withPlacement(ImagePlacement.axisAligned(new Envelope(0, 0, 1, 1), crs));
        return RasterImages.open(
                path,
                new SourceIdentity("raster-viewer-source", "Raster image"),
                options,
                AwtRasterDecoders.level1());
    }

    static MapView createView(RasterSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Raster view creation must run on the event dispatch thread");
        }
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        MapLayerBinding binding = null;
        try {
            view.setSize(800, 600);
            binding = MapLayerBinding.ownedRaster("image", "Raster image", source);
            view.setLayerBindings(List.of(binding));
            binding = null;
            view.fitToData(16);
            return view;
        } catch (RuntimeException | Error failure) {
            if (binding != null) {
                closeSuppressing(binding, failure);
            } else {
                closeSuppressing(view, failure);
                if (!source.isClosed()) {
                    closeSuppressing(source, failure);
                }
            }
            throw failure;
        }
    }

    private static void launch(RasterSource source) {
        EventQueue.invokeLater(() -> show(source));
    }

    private static void show(RasterSource source) {
        show(source, RasterViewer::showWindow);
    }

    static void show(RasterSource source, Consumer<MapView> presenter) {
        MapView view = createView(source);
        try {
            presenter.accept(view);
        } catch (RuntimeException | Error failure) {
            closeSuppressing(view, failure);
            throw failure;
        }
    }

    private static void showWindow(MapView view) {
        JFrame frame = new JFrame("Mundane raster viewer");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        frame.add(new JLabel(PLACEMENT_LABEL), BorderLayout.SOUTH);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        view.close();
                    }
                });
        frame.setSize(900, 650);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof SourceException source) {
            return source.terminal().code() + ": " + source.terminal().message();
        }
        return failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Exception failure) {
            primary.addSuppressed(failure);
        }
    }
}
