package io.github.mundanej.map.example.shapefile;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** PRJ-aware viewer for the supported read-only shapefile profile. */
public final class ShapefileViewer {
    static final int PREVIEW_LIMIT = 20;
    private static final String LAYER_ID = "shapefile";
    private static final String SOURCE_ID = "shapefile-source";

    private ShapefileViewer() {}

    /**
     * Validates arguments, loads a bounded preview off the EDT, and launches the Swing window.
     *
     * @param arguments the SHP path and optional explicit supported CRS identifier
     */
    public static void main(String[] arguments) {
        runMain(arguments, System.err::println);
    }

    static boolean runMain(String[] arguments, Consumer<String> failureSink) {
        Objects.requireNonNull(failureSink, "failureSink");
        try {
            LaunchArguments launch = parseArguments(arguments);
            LoadedDataset loaded = load(launch.path(), launch.crsOverride());
            launchLoaded(loaded, EventQueue::invokeLater);
            return true;
        } catch (RuntimeException failure) {
            failureSink.accept(failureSummary(failure));
            return false;
        }
    }

    static LaunchArguments parseArguments(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length < 1 || arguments.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: shapefile-viewer <path.shp> [EPSG:4326|EPSG:3857]");
        }
        Path path = Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
        Optional<CrsDefinition> crs =
                arguments.length == 1
                        ? Optional.empty()
                        : Optional.of(resolveOverride(arguments[1]));
        return new LaunchArguments(path, crs);
    }

    static LoadedDataset load(Path shapefile, Optional<CrsDefinition> crsOverride) {
        Objects.requireNonNull(shapefile, "shapefile");
        Objects.requireNonNull(crsOverride, "crsOverride");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Shapefile loading must run off the event dispatch thread");
        }
        ShapefileOpenOptions options = ShapefileOpenOptions.defaults();
        if (crsOverride.isPresent()) {
            options = options.withCrsOverride(crsOverride.orElseThrow());
        }
        FeatureSource source =
                Shapefiles.open(new SourceIdentity(SOURCE_ID, "Shapefile"), shapefile, options);
        return preview(source);
    }

    static LoadedDataset preview(FeatureSource source) {
        Objects.requireNonNull(source, "source");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Shapefile loading must run off the event dispatch thread");
        }
        FeatureCursor cursor = null;
        Throwable primary = null;
        try {
            FeatureSourceMetadata metadata = source.metadata();
            DiagnosticReport opening = source.openingDiagnostics();
            cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            List<FeatureRecord> preview = new ArrayList<>(PREVIEW_LIMIT);
            boolean truncated = false;
            while (cursor.advance()) {
                if (preview.size() == PREVIEW_LIMIT) {
                    truncated = true;
                    break;
                }
                preview.add(cursor.current());
            }
            DiagnosticReport query = cursor.diagnostics();
            FeatureCursor closingCursor = cursor;
            cursor = null;
            closingCursor.close();
            LoadedDataset result =
                    new LoadedDataset(
                            source, metadata, opening, query, List.copyOf(preview), truncated);
            source = null;
            return result;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (cursor != null) {
                primary = closeSuppressing(cursor, primary);
            }
            if (source != null) {
                primary = closeSuppressing(source, primary);
            }
        }
    }

    static MapView createMapView(Path shapefile, Optional<CrsDefinition> crsOverride) {
        return createMapView(shapefile, crsOverride, ignored -> {});
    }

    static MapView createMapView(
            Path shapefile,
            Optional<CrsDefinition> crsOverride,
            Consumer<MapView> beforeBindingTransfer) {
        LoadedDataset loaded = load(shapefile, crsOverride);
        try {
            return awaitEdt(
                            () -> start(loaded, false, beforeBindingTransfer),
                            EventQueue::invokeLater)
                    .view();
        } catch (RuntimeException | Error failure) {
            if (!loaded.source().isClosed()) {
                closeSuppressing(loaded.source(), failure);
            }
            throw failure;
        }
    }

    static ViewerSession launchLoaded(LoadedDataset loaded, Consumer<Runnable> scheduler) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(scheduler, "scheduler");
        boolean submitted = false;
        try {
            FutureTask<ViewerSession> task =
                    new FutureTask<>(() -> start(loaded, true, ignored -> {}));
            scheduler.accept(task);
            submitted = true;
            return await(task);
        } catch (RuntimeException | Error failure) {
            if (!submitted) {
                closeSuppressing(loaded.source(), failure);
            }
            throw failure;
        }
    }

    static <T> T awaitEdt(Callable<T> callable, Consumer<Runnable> scheduler) {
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(scheduler, "scheduler");
        if (EventQueue.isDispatchThread()) {
            try {
                return callable.call();
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("EDT callable failed", failure);
            }
        }
        FutureTask<T> task = new FutureTask<>(callable);
        scheduler.accept(task);
        return await(task);
    }

    private static <T> T await(FutureTask<T> task) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return task.get();
                } catch (InterruptedException failure) {
                    interrupted = true;
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException("EDT callable failed", cause);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ViewerSession start(
            LoadedDataset loaded, boolean installWindow, Consumer<MapView> beforeBindingTransfer) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Viewer startup must run on the event dispatch thread");
        }
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(beforeBindingTransfer, "beforeBindingTransfer");
        FeatureSource source = loaded.source();
        MapView view = null;
        MapLayerBinding binding = null;
        JFrame frame = null;
        boolean installed = false;
        try {
            view =
                    new MapView(
                            CrsRegistry.level1(),
                            CrsDefinitions.EPSG_4326,
                            CrsDefinitions.EPSG_3857);
            ShapefilePreviewPanel panel = new ShapefilePreviewPanel(loaded);
            view.addMapSourceReportListener(panel);
            beforeBindingTransfer.accept(view);
            binding =
                    MapLayerBinding.ownedFeature(
                            LAYER_ID, "Shapefile", source, marker(), line(), fill());
            source = null;
            view.setLayerBindings(List.of(binding));
            installed = true;
            view.fitToData(32.0);
            panel.selectFirstPreview();
            if (installWindow) {
                frame = installWindow(view, panel);
            }
            return new ViewerSession(view, panel, Optional.ofNullable(frame));
        } catch (RuntimeException | Error failure) {
            if (frame != null) {
                frame.dispose();
            }
            if (!installed && view != null && binding != null) {
                MapLayerBinding candidate = binding;
                installed = view.layerBindings().stream().anyMatch(value -> value == candidate);
            }
            if (installed) {
                closeSuppressing(view, failure);
            } else {
                if (binding != null) {
                    closeSuppressing(binding, failure);
                } else if (source != null) {
                    closeSuppressing(source, failure);
                }
                if (view != null) {
                    closeSuppressing(view, failure);
                }
            }
            throw failure;
        }
    }

    private static JFrame installWindow(MapView map, ShapefilePreviewPanel panel) {
        JFrame frame = new JFrame("mundane-java-map — shapefile viewer");
        try {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.add(map, BorderLayout.CENTER);
            frame.add(panel, BorderLayout.EAST);
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent event) {
                            try {
                                map.close();
                            } finally {
                                frame.dispose();
                            }
                        }
                    });
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            return frame;
        } catch (RuntimeException | Error failure) {
            frame.dispose();
            throw failure;
        }
    }

    private static VectorMarkerSymbol marker() {
        return BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(35, 105, 190), 10.0, 1.0);
    }

    private static SolidLineSymbol line() {
        return SolidLineSymbol.of(stroke(Rgba.rgb(35, 105, 190), 2.0), 1.0);
    }

    private static SolidFillSymbol fill() {
        return SolidFillSymbol.of(new Rgba(35, 105, 190, 70), Optional.of(line()), 1.0);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static CrsDefinition resolveOverride(String value) {
        String crsKey = Objects.requireNonNull(value, "arguments[1]");
        if (!crsKey.equals("EPSG:4326") && !crsKey.equals("EPSG:3857")) {
            throw new IllegalArgumentException("CRS must be EPSG:4326 or EPSG:3857");
        }
        return CrsRegistry.level1().resolve(crsKey);
    }

    private static String failureSummary(RuntimeException failure) {
        if (failure instanceof SourceException sourceFailure
                && !sourceFailure.report().entries().isEmpty()) {
            String diagnostics =
                    sourceFailure.report().entries().stream()
                            .map(
                                    diagnostic -> {
                                        StringBuilder value =
                                                new StringBuilder(diagnostic.severity().name())
                                                        .append(' ')
                                                        .append(diagnostic.code());
                                        diagnostic
                                                .location()
                                                .ifPresent(
                                                        location -> {
                                                            location.component()
                                                                    .ifPresent(
                                                                            component ->
                                                                                    value.append(
                                                                                                    " component=")
                                                                                            .append(
                                                                                                    component));
                                                            if (location.recordNumber()
                                                                    .isPresent()) {
                                                                value.append(" record=")
                                                                        .append(
                                                                                location.recordNumber()
                                                                                        .getAsLong());
                                                            }
                                                            if (location.fieldName().isPresent()) {
                                                                value.append(" field=")
                                                                        .append(
                                                                                location.fieldName()
                                                                                        .orElseThrow());
                                                            }
                                                            if (location.byteOffset().isPresent()) {
                                                                value.append(" offset=")
                                                                        .append(
                                                                                location.byteOffset()
                                                                                        .getAsLong());
                                                            }
                                                        });
                                        appendContext(value, diagnostic.context());
                                        return value.toString();
                                    })
                            .collect(java.util.stream.Collectors.joining("; "));
            return "shapefile-viewer: " + diagnostics;
        }
        if (failure instanceof CrsException crsFailure) {
            StringBuilder value =
                    new StringBuilder("shapefile-viewer: ERROR ")
                            .append(crsFailure.problem().code());
            appendContext(value, crsFailure.problem().context());
            return value.toString();
        }
        if (failure instanceof IllegalArgumentException) {
            return "shapefile-viewer: SHAPEFILE_VIEWER_ARGUMENT_INVALID";
        }
        return "shapefile-viewer: SHAPEFILE_VIEWER_STARTUP_FAILED";
    }

    private static void appendContext(StringBuilder value, java.util.Map<String, String> context) {
        if (context.isEmpty()) {
            return;
        }
        value.append(" context={");
        value.append(
                context.entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + '=' + bounded(entry.getValue()))
                        .collect(java.util.stream.Collectors.joining(", ")));
        value.append('}');
    }

    private static String bounded(String value) {
        int limit = 160;
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    private static Throwable closeSuppressing(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Throwable cleanup) {
            if (primary == null) {
                if (cleanup instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cleanup instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Close failed", cleanup);
            }
            if (cleanup != primary) {
                primary.addSuppressed(cleanup);
            }
        }
        return primary;
    }

    record LaunchArguments(Path path, Optional<CrsDefinition> crsOverride) {}

    record LoadedDataset(
            FeatureSource source,
            FeatureSourceMetadata metadata,
            DiagnosticReport opening,
            DiagnosticReport query,
            List<FeatureRecord> preview,
            boolean truncated) {
        LoadedDataset {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(opening, "opening");
            Objects.requireNonNull(query, "query");
            preview = List.copyOf(Objects.requireNonNull(preview, "preview"));
            if (preview.size() > PREVIEW_LIMIT) {
                throw new IllegalArgumentException("Preview exceeds the fixed limit");
            }
        }
    }

    record ViewerSession(MapView view, ShapefilePreviewPanel panel, Optional<JFrame> window) {
        ViewerSession {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(panel, "panel");
            Objects.requireNonNull(window, "window");
        }
    }
}
