package io.github.mundanej.map.example.kml;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.kml.KmlFiles;
import io.github.mundanej.map.io.kml.KmlOpenOptions;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Runnable local-file viewer for the bounded static KML 2.2 geometry profile. */
public final class KmlViewer {
    private KmlViewer() {}

    /**
     * Opens one local KML file and launches a Swing map view.
     *
     * @param arguments exactly one local KML path
     */
    public static void main(String[] arguments) {
        runMain(
                arguments,
                System.err::println,
                source -> EventQueue.invokeLater(() -> show(source)));
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<FeatureSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        FeatureSource source = null;
        try {
            source = open(parsePath(arguments));
            launcher.accept(source);
            source = null;
            return true;
        } catch (RuntimeException failure) {
            if (source != null) {
                source.close();
            }
            failureSink.accept(summary(failure));
            return false;
        }
    }

    /**
     * Opens one local KML file and creates a map view that owns the source.
     *
     * @param path local KML file
     * @return configured owning map view
     * @throws IllegalStateException if the operation is not valid in the current state or thread
     */
    public static MapView createMapView(Path path) {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("KML loading must run off the event dispatch thread");
        }
        FeatureSource source = open(Objects.requireNonNull(path, "path"));
        FutureTask<MapView> task = new FutureTask<>(() -> createMapView(source));
        EventQueue.invokeLater(task);
        try {
            return task.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            source.close();
            throw new IllegalStateException("Interrupted while creating the KML view", failure);
        } catch (ExecutionException failure) {
            if (!source.isClosed()) {
                source.close();
            }
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Could not create the KML view", failure.getCause());
        }
    }

    static Path parsePath(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: kml-viewer <features.kml>");
        }
        return Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
    }

    static FeatureSource open(Path path) {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("KML loading must run off the event dispatch thread");
        }
        return KmlFiles.open(
                path,
                new SourceIdentity("kml-viewer", "KML file"),
                KmlOpenOptions.defaults(),
                CancellationToken.none());
    }

    static MapView createMapView(FeatureSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "KML view creation must run on the event dispatch thread");
        }
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(28, 108, 184),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        try {
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.ownedFeature(
                                    "kml",
                                    "KML",
                                    source,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.CIRCLE, Rgba.rgb(224, 78, 45), 10, 1),
                                    line,
                                    SolidFillSymbol.of(
                                            new Rgba(28, 108, 184, 90), Optional.of(line), 1))));
            view.setSize(900, 640);
            view.fitToData(24);
            return view;
        } catch (RuntimeException | Error failure) {
            view.close();
            if (!source.isClosed()) {
                source.close();
            }
            throw failure;
        }
    }

    private static void show(FeatureSource source) {
        installWindow(source, KmlViewer::showWindow);
    }

    static void installWindow(FeatureSource source, Consumer<MapView> installer) {
        Objects.requireNonNull(installer, "installer");
        MapView view = createMapView(source);
        try {
            installer.accept(view);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
    }

    private static void showWindow(MapView view) {
        JFrame frame = null;
        try {
            frame = new JFrame("mundane-java-map — KML viewer");
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
        } catch (RuntimeException | Error failure) {
            if (frame != null) {
                frame.dispose();
            }
            throw failure;
        }
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof SourceException sourceFailure) {
            SourceDiagnostic diagnostic = sourceFailure.terminal();
            String context =
                    diagnostic.context().isEmpty()
                            ? ""
                            : " context={"
                                    + diagnostic.context().entrySet().stream()
                                            .map(entry -> entry.getKey() + '=' + entry.getValue())
                                            .collect(java.util.stream.Collectors.joining(", "))
                                    + '}';
            return "kml-viewer: " + diagnostic.severity() + ' ' + diagnostic.code() + context;
        }
        return "kml-viewer: ERROR INPUT_INVALID";
    }
}
