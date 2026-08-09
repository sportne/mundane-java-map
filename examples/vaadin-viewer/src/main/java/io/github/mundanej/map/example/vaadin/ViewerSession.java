package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.shared.Registration;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.DistanceStrategies;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.svg.SvgExportException;
import io.github.mundanej.map.io.svg.SvgExportLimits;
import io.github.mundanej.map.io.svg.SvgMapExports;
import io.github.mundanej.map.vaadin.BrowserMeasurementTool;
import io.github.mundanej.map.vaadin.BrowserPointEditController;
import io.github.mundanej.map.vaadin.FeatureEditBinding;
import io.github.mundanej.map.vaadin.MundaneMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Per-route owner of the example component, edit lane, controls, and listener registrations. */
final class ViewerSession implements AutoCloseable {
    static final SvgExportLimits SVG_EXPORT_LIMITS =
            SvgExportLimits.defaults().withMaximumOutputBytes(ViewerSvgDownloads.MAXIMUM_BYTES);

    enum ToolMode {
        NAVIGATE,
        MEASURE,
        CREATE_POINT,
        MOVE_POINT
    }

    private final MundaneMap map = new MundaneMap();
    private final List<Layer> allLayers = new ArrayList<>();
    private final Map<String, Boolean> layerVisibility = new LinkedHashMap<>();
    private final FeatureEditBinding editBinding;
    private final BrowserPointEditController editor;
    private final BrowserMeasurementTool measurement;
    private final Registration measurementRegistration;
    private final ViewerSourceWorkflows sources;
    private final ViewerUploadStaging uploads = new ViewerUploadStaging();
    private final ViewerSvgDownloads downloads = new ViewerSvgDownloads();
    private final List<Runnable> observers = new CopyOnWriteArrayList<>();
    private String coordinateText = "Move the pointer over the map";
    private String selectionText = "Nothing selected";
    private String diagnosticText = "No source diagnostics";
    private ToolMode toolMode = ToolMode.NAVIGATE;
    private long nextPointId = 1;
    private volatile boolean closed;

    ViewerSession() {
        this(Runnable::run);
    }

    ViewerSession(Consumer<Runnable> dispatcher) {
        allLayers.add(regionLayer());
        allLayers.add(routeLayer());
        for (Layer layer : allLayers) {
            layerVisibility.put(layer.id(), true);
        }
        editBinding =
                FeatureEditBinding.open(
                        "editable-points",
                        "Editable points",
                        new FeatureEditSnapshot(
                                0,
                                CrsDefinitions.EPSG_3857,
                                List.of(
                                        record("point-a", "Editable A", -260, 100),
                                        record("point-b", "Editable B", 280, -120))),
                        FeaturePortrayal.markers(
                                new FixedSymbolSelector(
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.CIRCLE,
                                                Rgba.rgb(32, 112, 196),
                                                13,
                                                1))));
        map.setViewport(new MapViewport(800, 560, 0, 0, 2));
        map.setSnapshotLayers(allLayers);
        map.setFeatureEditBindings(List.of(editBinding));
        editor = new BrowserPointEditController(map, editBinding);
        measurement =
                new BrowserMeasurementTool(
                        map, DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        measurementRegistration = measurement.addStateListener(this::measurementChanged);
        map.addMapPointerListener(this::pointerChanged);
        map.addMapSelectionListener(
                event -> {
                    selectionText =
                            event.current().map(ViewerSession::describe).orElse("Nothing selected");
                    notifyObservers();
                });
        map.addSourceReportListener(
                ignored -> {
                    diagnosticText = summarizeDiagnostics();
                    notifyObservers();
                });
        sources = new ViewerSourceWorkflows(map, dispatcher, this::sourceWorkflowChanged);
        map.fitToContents(48);
    }

    MundaneMap map() {
        return map;
    }

    List<Layer> layers() {
        return List.copyOf(allLayers);
    }

    boolean isLayerVisible(String layerId) {
        return Boolean.TRUE.equals(layerVisibility.get(layerId));
    }

    String coordinateText() {
        return coordinateText;
    }

    String selectionText() {
        return selectionText;
    }

    String diagnosticText() {
        return diagnosticText;
    }

    ToolMode toolMode() {
        return toolMode;
    }

    MeasurementState measurementState() {
        return measurement.state();
    }

    BrowserMeasurementTool measurementTool() {
        return measurement;
    }

    FeatureEditSnapshot editSnapshot() {
        return editBinding.snapshot();
    }

    boolean isClosed() {
        return closed;
    }

    List<ViewerSourceWorkflows.SourceLayer> sourceLayers() {
        return sources.layers();
    }

    boolean sourceBusy() {
        return sources.busy();
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openShapefile(Path path) {
        requireOpen();
        return sources.openShapefile(path);
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openRaster(Path path) {
        requireOpen();
        return sources.openRaster(path);
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openElevation(Path path) {
        requireOpen();
        return sources.openElevation(path);
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openWorkspace(Path path) {
        requireOpen();
        return sources.openWorkspace(path);
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> rejectInvalidSourcePath() {
        requireOpen();
        return sources.rejectInvalidPath();
    }

    ViewerUploadStaging uploads() {
        return uploads;
    }

    ViewerSvgDownloads downloads() {
        return downloads;
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openUploaded(
            ViewerUploadStaging.UploadSelection upload) {
        requireOpen();
        return switch (upload.kind()) {
            case SHAPEFILE -> openShapefile(upload.entry());
            case RASTER -> openRaster(upload.entry());
            case ELEVATION -> openElevation(upload.entry());
            case WORKSPACE -> openWorkspace(upload.entry());
        };
    }

    boolean prepareSvgExport() {
        requireOpen();
        downloads.invalidate();
        try {
            return publishSvg(map.captureVectorExportSnapshot());
        } catch (VectorExportSnapshotException failure) {
            diagnosticText = failure.problem().code();
        } catch (SvgExportException failure) {
            diagnosticText = failure.problem().code();
        }
        notifyObservers();
        return false;
    }

    boolean prepareSvgExport(VectorExportSnapshot snapshot) {
        requireOpen();
        downloads.invalidate();
        try {
            return publishSvg(Objects.requireNonNull(snapshot, "snapshot"));
        } catch (SvgExportException failure) {
            diagnosticText = failure.problem().code();
            notifyObservers();
            return false;
        }
    }

    private boolean publishSvg(VectorExportSnapshot snapshot) {
        downloads.publish(SvgMapExports.encode(snapshot, SVG_EXPORT_LIMITS));
        diagnosticText = "SVG_EXPORT_READY";
        notifyObservers();
        return true;
    }

    void reportDiagnostic(String code) {
        requireOpen();
        diagnosticText = Objects.requireNonNull(code, "code");
        notifyObservers();
    }

    void clearSources() {
        sources.clear();
    }

    void setSourceVisible(String id, boolean visible) {
        sources.setVisible(id, visible);
    }

    void moveSource(String id, int delta) {
        sources.move(id, delta);
    }

    void addObserver(Runnable observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    void setLayerVisible(String layerId, boolean visible) {
        requireOpen();
        requireLayer(layerId);
        layerVisibility.put(layerId, visible);
        publishLayers();
    }

    void moveLayer(String layerId, int delta) {
        requireOpen();
        int index = indexOf(layerId);
        int target = Math.max(0, Math.min(allLayers.size() - 1, index + delta));
        if (target != index) {
            Layer layer = allLayers.remove(index);
            allLayers.add(target, layer);
            publishLayers();
        }
    }

    void fit() {
        requireOpen();
        map.fitToContents(48);
    }

    void zoom(double factor) {
        requireOpen();
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("factor must be finite and positive");
        }
        MapViewport current = map.viewport();
        map.setViewport(
                new MapViewport(
                        current.width(),
                        current.height(),
                        current.centerX(),
                        current.centerY(),
                        current.worldUnitsPerPixel() * factor));
    }

    void setWrapEnabled(boolean enabled) {
        requireOpen();
        if (enabled) {
            map.setHorizontalWrap(HorizontalWrap.webMercator());
            try {
                sources.setWrapEnabled(true);
            } catch (RuntimeException | Error failure) {
                map.clearHorizontalWrap();
                throw failure;
            }
        } else {
            sources.setWrapEnabled(false);
            map.clearHorizontalWrap();
        }
        notifyObservers();
    }

    boolean wrapEnabled() {
        return map.horizontalWrap().isPresent();
    }

    void navigate() {
        requireOpen();
        map.clearActiveTool();
        editor.clearMode();
        toolMode = ToolMode.NAVIGATE;
        notifyObservers();
    }

    void measure() {
        requireOpen();
        map.setActiveTool(measurement);
        toolMode = ToolMode.MEASURE;
        notifyObservers();
    }

    void createPoint() {
        requireOpen();
        editor.create(
                new PointFeatureDraft(
                        "created-" + nextPointId,
                        "Created point " + nextPointId,
                        Map.of("origin", "viewer")));
        nextPointId++;
        map.setActiveTool(editor);
        toolMode = ToolMode.CREATE_POINT;
        notifyObservers();
    }

    void movePoint() {
        requireOpen();
        editor.moveSelected();
        map.setActiveTool(editor);
        toolMode = ToolMode.MOVE_POINT;
        notifyObservers();
    }

    void undo() {
        requireOpen();
        editor.undo();
        notifyObservers();
    }

    void redo() {
        requireOpen();
        editor.redo();
        notifyObservers();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        observers.clear();
        Throwable primary = null;
        primary = cleanup(primary, measurementRegistration::remove);
        primary = cleanup(primary, sources::close);
        primary = cleanup(primary, map::close);
        primary = cleanup(primary, editBinding::close);
        primary = cleanup(primary, downloads::close);
        primary = cleanup(primary, uploads::close);
        throwIfPresent(primary);
    }

    private void publishLayers() {
        map.setSnapshotLayers(
                allLayers.stream().filter(layer -> isLayerVisible(layer.id())).toList());
        notifyObservers();
    }

    private void pointerChanged(MapPointerEvent event) {
        coordinateText =
                event.mapCoordinate()
                        .map(ViewerSession::formatCoordinate)
                        .orElse("Pointer is outside the map domain");
        notifyObservers();
    }

    private void measurementChanged(MeasurementState state) {
        Optional<Coordinate> endpoint = state.preview();
        if (endpoint.isEmpty() && state.vertexCount() > 0) {
            endpoint = Optional.of(state.vertex(state.vertexCount() - 1));
        }
        endpoint.map(ViewerSession::formatCoordinate).ifPresent(value -> coordinateText = value);
        notifyObservers();
    }

    private void sourceWorkflowChanged() {
        diagnosticText =
                "NO_SOURCE_DIAGNOSTICS".equals(sources.diagnosticCode())
                        ? summarizeDiagnostics()
                        : sources.diagnosticCode();
        notifyObservers();
    }

    private String summarizeDiagnostics() {
        if (map.sourceReports().isEmpty()) {
            return "No source diagnostics";
        }
        String codes =
                map.sourceReports().values().stream()
                        .flatMap(report -> report.entries().stream())
                        .map(diagnostic -> diagnostic.code())
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(", "));
        return codes.isEmpty() ? "No source diagnostics" : codes;
    }

    private int indexOf(String layerId) {
        for (int index = 0; index < allLayers.size(); index++) {
            if (allLayers.get(index).id().equals(layerId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("unknown layer");
    }

    private void requireLayer(String layerId) {
        indexOf(layerId);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("viewer session is closed");
        }
    }

    private void notifyObservers() {
        for (Runnable observer : observers) {
            observer.run();
        }
    }

    private static Throwable cleanup(Throwable primary, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException | Error failure) {
            if (primary == null) {
                return failure;
            }
            if (primary != failure) {
                primary.addSuppressed(failure);
            }
        }
        return primary;
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private static String describe(FeatureSelection selection) {
        return selection.layerId() + " / " + selection.featureId();
    }

    private static String formatCoordinate(Coordinate coordinate) {
        return String.format(Locale.ROOT, "x %.2f, y %.2f", coordinate.x(), coordinate.y());
    }

    private static FeatureRecord record(String id, String name, double x, double y) {
        return new FeatureRecord(
                id, name, new PointGeometry(new Coordinate(x, y)), Map.of("editable", true));
    }

    private static Layer regionLayer() {
        Feature region =
                new Feature(
                        "region",
                        "In-memory study area",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        -520, -320, 520, -320, 520, 320, -520, 320, -520, -320)),
                        Map.of("kind", "area"),
                        SolidFillSymbol.of(
                                new Rgba(70, 150, 96, 65),
                                Optional.of(
                                        SolidLineSymbol.of(stroke(Rgba.rgb(42, 105, 64), 2), 1)),
                                1));
        return new InMemoryLayer("study-area", "Study area", List.of(region));
    }

    private static Layer routeLayer() {
        Feature route =
                new Feature(
                        "route",
                        "In-memory route",
                        new LineStringGeometry(
                                CoordinateSequence.of(-420, -170, -120, 180, 150, 60, 430, 220)),
                        Map.of("kind", "route"),
                        SolidLineSymbol.of(stroke(Rgba.rgb(194, 58, 52), 4), 1));
        return new InMemoryLayer("route", "Route", List.of(route));
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }
}
