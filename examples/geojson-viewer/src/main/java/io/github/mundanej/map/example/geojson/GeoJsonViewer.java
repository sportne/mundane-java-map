package io.github.mundanej.map.example.geojson;

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
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable bounded RFC 7946 viewer for local files and the bundled review fixture. */
public final class GeoJsonViewer {
    static final String DEFAULT_FIXTURE =
            "/io/github/mundanej/map/example/geojson/fixtures/all-geometries.geojson";

    private GeoJsonViewer() {}

    /**
     * Opens the requested local file, or the bundled geometry fixture when no argument is given.
     *
     * @param arguments zero arguments or one local GeoJSON path
     */
    public static void main(String[] arguments) {
        runMain(arguments, System.err::println, GeoJsonViewer::launch);
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<FeatureSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        FeatureSource source = null;
        try {
            Objects.requireNonNull(arguments, "arguments");
            if (arguments.length > 1) {
                throw new IllegalArgumentException("Usage: GeoJsonViewer [file.geojson]");
            }
            source =
                    arguments.length == 0
                            ? openResource(DEFAULT_FIXTURE, "bundled-geojson")
                            : open(Path.of(Objects.requireNonNull(arguments[0], "arguments[0]")));
            launcher.accept(source);
            source = null;
            return true;
        } catch (RuntimeException failure) {
            if (source != null) {
                closeSuppressing(source, failure);
            }
            failureSink.accept(summary(failure));
            return false;
        }
    }

    /**
     * Opens a local GeoJSON file and transfers source ownership to a configured map view.
     *
     * @param path regular local GeoJSON file
     * @return configured map view that owns the opened source
     */
    public static MapView createMapView(Path path) {
        return createMapView(open(Objects.requireNonNull(path, "path")));
    }

    static MapView createMapView(FeatureSource source) {
        Objects.requireNonNull(source, "source");
        FutureTask<MapView> task = new FutureTask<>(() -> configure(source));
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
        try {
            return task.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            source.close();
            throw new IllegalStateException("Interrupted while creating the GeoJSON view", failure);
        } catch (ExecutionException failure) {
            source.close();
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Could not create the GeoJSON view", failure.getCause());
        }
    }

    static FeatureSource openResource(String resource, String id) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(id, "id");
        try (InputStream input = GeoJsonViewer.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing GeoJSON resource: " + resource);
            }
            return GeoJsonFiles.open(
                    input.readAllBytes(),
                    new SourceIdentity(id, "Bundled GeoJSON"),
                    GeoJsonOpenOptions.defaults(),
                    CancellationToken.none());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read bundled GeoJSON", failure);
        }
    }

    private static FeatureSource open(Path path) {
        Path fileName = Objects.requireNonNull(path.getFileName(), "path file name");
        return GeoJsonFiles.open(
                path,
                new SourceIdentity("geojson-viewer", fileName.toString()),
                GeoJsonOpenOptions.defaults(),
                CancellationToken.none());
    }

    private static void launch(FeatureSource source) {
        SwingUtilities.invokeLater(() -> installWindow(source, GeoJsonViewer::showWindow));
    }

    static void installWindow(FeatureSource source, Consumer<MapView> installer) {
        Objects.requireNonNull(installer, "installer");
        MapView view = configure(source);
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
            frame = new JFrame("mundane-java-map — GeoJSON viewer");
            JFrame installed = frame;
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent event) {
                            try {
                                view.close();
                            } finally {
                                installed.dispose();
                            }
                        }
                    });
            frame.add(view, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        } catch (RuntimeException | Error failure) {
            if (frame != null) {
                frame.dispose();
            }
            throw failure;
        }
    }

    private static MapView configure(FeatureSource source) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        try {
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.ownedFeature(
                                    "geojson",
                                    "GeoJSON",
                                    source,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.CIRCLE, Rgba.rgb(28, 108, 184), 10, 1),
                                    line(),
                                    SolidFillSymbol.of(
                                            new Rgba(35, 105, 190, 70), Optional.of(line()), 1))));
            view.fitToData(32);
            return view;
        } catch (RuntimeException | Error failure) {
            view.close();
            if (!source.isClosed()) {
                source.close();
            }
            throw failure;
        }
    }

    private static SolidLineSymbol line() {
        return SolidLineSymbol.of(
                new SymbolStroke(
                        Rgba.rgb(35, 105, 190), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                1);
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof SourceException sourceFailure) {
            return diagnosticSummary(sourceFailure.terminal());
        }
        return "geojson-viewer: ERROR INPUT_INVALID";
    }

    private static String diagnosticSummary(SourceDiagnostic diagnostic) {
        StringBuilder value =
                new StringBuilder("geojson-viewer: ")
                        .append(diagnostic.severity())
                        .append(' ')
                        .append(diagnostic.code());
        diagnostic
                .location()
                .flatMap(location -> location.component())
                .ifPresent(component -> value.append(" component=").append(component));
        if (!diagnostic.context().isEmpty()) {
            value.append(" context={")
                    .append(
                            diagnostic.context().entrySet().stream()
                                    .map(entry -> entry.getKey() + '=' + entry.getValue())
                                    .collect(java.util.stream.Collectors.joining(", ")))
                    .append('}');
        }
        return value.toString();
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Exception failure) {
            primary.addSuppressed(failure);
        }
    }
}
