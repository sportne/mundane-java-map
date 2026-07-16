package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureOverlaySymbols;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MapHitResults;
import io.github.mundanej.map.api.MapHoverEvent;
import io.github.mundanej.map.api.MapHoverListener;
import io.github.mundanej.map.api.MapInputModifier;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MapPointerListener;
import io.github.mundanej.map.api.MapSelectionEvent;
import io.github.mundanej.map.api.MapSelectionListener;
import io.github.mundanej.map.api.MapSourceReportEvent;
import io.github.mundanej.map.api.MapSourceReportListener;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolCommand;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.ElevationRasterization;
import io.github.mundanej.map.core.FeatureQueryAccounting;
import io.github.mundanej.map.core.HatchLayouts;
import io.github.mundanej.map.core.HatchSegments;
import io.github.mundanej.map.core.LineEndpointBearings;
import io.github.mundanej.map.core.LineTangents;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapToolRouter;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.QueryEnvelopeStatus;
import io.github.mundanej.map.core.QueryEnvelopeTransform;
import io.github.mundanej.map.core.RasterGridWindows;
import io.github.mundanej.map.core.RasterRequestAccounting;
import io.github.mundanej.map.core.RouteOutcome;
import io.github.mundanej.map.core.ScreenGeometryHits;
import io.github.mundanej.map.core.ScreenGeometryOptimizationLimits;
import io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome;
import io.github.mundanej.map.core.ScreenGeometryOptimizer;
import io.github.mundanej.map.core.SymbolTransforms;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * A lightweight Swing map component for projected vector features.
 *
 * <p>Mutation and listener callbacks follow the normal Swing event-dispatch-thread contract. Swing
 * serialization is inherited for framework compatibility and is not a persistence format. The view
 * owns only sources transferred through owned {@link MapLayerBinding} instances; callers must close
 * borrowed sources themselves. After {@link #close()}, mutating and rendering operations are
 * invalid and installed owned bindings have been closed.
 */
@SuppressWarnings({"deprecation", "serial"})
public final class MapView extends JComponent implements AutoCloseable {
    /** Default logical-pixel tolerance for click selection ({@value}). */
    public static final double DEFAULT_SELECTION_TOLERANCE_PIXELS = 4.0;

    /** Default logical-pixel tolerance for pointer hover ({@value}). */
    public static final double DEFAULT_HOVER_TOLERANCE_PIXELS = 4.0;

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final double ZOOM_STEP = 1.2;
    private static final double SCREEN_PATH_TOLERANCE = 0.25;

    /** Explicit CRS registry; Swing serialization is not a persistence contract. */
    private final CrsRegistry crsRegistry;

    /** Map-coordinate CRS. */
    private final CrsDefinition mapCrs;

    /** Display-world CRS. */
    private final CrsDefinition displayCrs;

    /** Resolved forward CRS operation. */
    private final CrsOperation mapToDisplay;

    /** Resolved inverse CRS operation. */
    private final CrsOperation displayToMap;

    /** Explicit symbol renderer registry. */
    private final SymbolRendererRegistry symbolRenderers;

    /** Screen-geometry optimization mode. */
    private final ScreenGeometryOptimizationMode screenGeometryOptimizationMode;

    /** Private render-cache mode. */
    private final AwtRenderCacheMode renderCacheMode;

    /** Private view-owned render cache. */
    private final AwtRenderCache renderCache = new AwtRenderCache();

    /** Active test/evidence collector, normally {@code null}. */
    private ScreenGeometryPaintCollector activeScreenGeometryPaintCollector;

    /** Active private cache collector, normally {@code null}. */
    private AwtRenderCache.CacheEventCollector activeRenderCacheCollector;

    /** Active-tool router. */
    private final MapToolRouter toolRouter = new MapToolRouter();

    /** Ordered pointer-listener registrations. */
    private final List<MapPointerListener> pointerListeners = new ArrayList<>();

    /** Ordered hover-listener registrations. */
    private final List<MapHoverListener> hoverListeners = new ArrayList<>();

    /** Ordered selection-listener registrations. */
    private final List<MapSelectionListener> selectionListeners = new ArrayList<>();

    /** Ordered source-report-listener registrations. */
    private final List<MapSourceReportListener> sourceReportListeners = new ArrayList<>();

    /** Queued source-report notifications. */
    private final Deque<MapSourceReportEvent> sourceReportNotifications = new ArrayDeque<>();

    /** Queued interaction notifications. */
    private final Deque<InteractionNotification> interactionNotifications = new ArrayDeque<>();

    /** Identity set of measurement tools claimed by this view. */
    private final Set<MeasurementTool> measurementClaims =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** Immutable ordered installed binding list. */
    private List<MapLayerBinding> bindings = List.of();

    /** Immutable resolved feature-binding map. */
    private Map<MapLayerBinding, ResolvedFeatureBinding> resolvedFeatureBindings = Map.of();

    /** Identity-keyed view-owned raster presentation options. */
    private final IdentityHashMap<MapLayerBinding, RasterRenderOptions> rasterRenderOptions =
            new IdentityHashMap<>();

    /** Identity-keyed view-owned elevation styles. */
    private final IdentityHashMap<MapLayerBinding, ElevationRasterStyle> elevationRasterStyles =
            new IdentityHashMap<>();

    /** Stable source reports keyed by layer identifier. */
    private final LinkedHashMap<String, DiagnosticReport> sourceReports = new LinkedHashMap<>();

    /** Source availability keyed by layer identifier. */
    private final Map<String, Boolean> sourceAvailability = new LinkedHashMap<>();

    /** Current immutable selection identity. */
    private Optional<FeatureSelection> selection = Optional.empty();

    /** Current immutable hover identity. */
    private Optional<MapHit> hover = Optional.empty();

    /** Last hover query used for deterministic reprobe. */
    private Optional<HoverProbe> hoverProbe = Optional.empty();

    /** Logical-paint state for the current hover. */
    private Optional<AwtLogicalPaintPresence> hoverPaintState = Optional.empty();

    /** Logical-paint state for the current selection. */
    private Optional<AwtLogicalPaintPresence> selectionPaintState = Optional.empty();

    /** Immutable hover overlay symbols. */
    private FeatureOverlaySymbols hoverOverlay = FeatureOverlaySymbols.defaultHover();

    /** Immutable selection overlay symbols. */
    private FeatureOverlaySymbols selectionOverlay = FeatureOverlaySymbols.defaultSelection();

    /** Interaction-notification reentrancy guard. */
    private boolean drainingInteractionNotifications;

    /** Current immutable viewport. */
    private MapViewport viewport = MapViewport.initial(DEFAULT_WIDTH, DEFAULT_HEIGHT);

    /** Active pan anchor in logical-screen pixels, or {@code null}. */
    private Point dragAnchor;

    /** Last pointer x ordinate in logical-screen pixels. */
    private double lastPointerX = DEFAULT_WIDTH / 2.0;

    /** Last pointer y ordinate in logical-screen pixels. */
    private double lastPointerY = DEFAULT_HEIGHT / 2.0;

    /** Immutable identity set of currently pressed pointer buttons. */
    private Set<MapPointerButton> lastButtonsDown = Set.of();

    /** Immutable set of current pointer modifiers. */
    private Set<MapInputModifier> lastModifiers = Set.of();

    /** Monotonically increasing tool-event sequence. */
    private long toolEventSequence;

    /** Current tool-router callback depth. */
    private int routerCallDepth;

    /** Permanent closed state. */
    private boolean closed;

    /** Source-report-notification reentrancy guard. */
    private boolean drainingSourceReportNotifications;

    /**
     * Creates an empty view using the supplied source-to-world projection.
     *
     * @param projection non-null projection used by compatibility snapshot layers
     * @throws NullPointerException if {@code projection} is {@code null}
     */
    public MapView(Projection projection) {
        this(projection, SymbolRendererRegistry.builtIn());
    }

    /**
     * Creates an empty view using an explicit immutable symbol renderer registry.
     *
     * @param projection non-null projection used by compatibility snapshot layers
     * @param symbolRenderers non-null explicit immutable renderer registry
     * @throws NullPointerException if an argument is {@code null}
     */
    public MapView(Projection projection, SymbolRendererRegistry symbolRenderers) {
        this(configuration(projection), symbolRenderers, ScreenGeometryOptimizationMode.LEVEL1);
    }

    /**
     * Creates an empty view with explicit map and display coordinate reference systems.
     *
     * @param crsRegistry non-null explicit operation registry
     * @param mapCrs CRS used by compatibility layers and pointer coordinates
     * @param displayCrs CRS used by viewport world coordinates
     * @throws NullPointerException if an argument is {@code null}
     * @throws CrsException if either required direction is not registered
     */
    public MapView(CrsRegistry crsRegistry, CrsDefinition mapCrs, CrsDefinition displayCrs) {
        this(crsRegistry, mapCrs, displayCrs, SymbolRendererRegistry.builtIn());
    }

    /**
     * Creates an explicitly configured view and symbol-renderer registry.
     *
     * @param crsRegistry non-null explicit operation registry
     * @param mapCrs CRS used by compatibility layers and pointer coordinates
     * @param displayCrs CRS used by viewport world coordinates
     * @param symbolRenderers non-null explicit immutable renderer registry
     * @throws NullPointerException if an argument is {@code null}
     * @throws CrsException if either required CRS operation is not registered
     */
    public MapView(
            CrsRegistry crsRegistry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            SymbolRendererRegistry symbolRenderers) {
        this(
                new CoordinateConfiguration(
                        Objects.requireNonNull(crsRegistry, "crsRegistry"),
                        Objects.requireNonNull(mapCrs, "mapCrs"),
                        Objects.requireNonNull(displayCrs, "displayCrs")),
                symbolRenderers,
                ScreenGeometryOptimizationMode.LEVEL1);
    }

    MapView(
            Projection projection,
            SymbolRendererRegistry symbolRenderers,
            ScreenGeometryOptimizationMode optimizationMode) {
        this(configuration(projection), symbolRenderers, optimizationMode);
    }

    MapView(
            CrsRegistry crsRegistry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            SymbolRendererRegistry symbolRenderers,
            ScreenGeometryOptimizationMode optimizationMode) {
        this(
                new CoordinateConfiguration(
                        Objects.requireNonNull(crsRegistry, "crsRegistry"),
                        Objects.requireNonNull(mapCrs, "mapCrs"),
                        Objects.requireNonNull(displayCrs, "displayCrs")),
                symbolRenderers,
                optimizationMode);
    }

    private MapView(
            CoordinateConfiguration configuration,
            SymbolRendererRegistry symbolRenderers,
            ScreenGeometryOptimizationMode optimizationMode) {
        this(configuration, symbolRenderers, optimizationMode, AwtRenderCacheMode.VECTOR_TEMPLATE);
    }

    MapView(
            CrsRegistry crsRegistry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            SymbolRendererRegistry symbolRenderers,
            ScreenGeometryOptimizationMode optimizationMode,
            AwtRenderCacheMode cacheMode) {
        this(
                new CoordinateConfiguration(
                        Objects.requireNonNull(crsRegistry, "crsRegistry"),
                        Objects.requireNonNull(mapCrs, "mapCrs"),
                        Objects.requireNonNull(displayCrs, "displayCrs")),
                symbolRenderers,
                optimizationMode,
                cacheMode);
    }

    private MapView(
            CoordinateConfiguration configuration,
            SymbolRendererRegistry symbolRenderers,
            ScreenGeometryOptimizationMode optimizationMode,
            AwtRenderCacheMode cacheMode) {
        this.crsRegistry = configuration.registry();
        this.mapCrs = configuration.mapCrs();
        this.displayCrs = configuration.displayCrs();
        this.mapToDisplay = crsRegistry.operation(mapCrs, displayCrs);
        this.displayToMap = crsRegistry.operation(displayCrs, mapCrs);
        this.symbolRenderers = Objects.requireNonNull(symbolRenderers, "symbolRenderers");
        this.screenGeometryOptimizationMode =
                Objects.requireNonNull(optimizationMode, "optimizationMode");
        this.renderCacheMode = Objects.requireNonNull(cacheMode, "cacheMode");
        setOpaque(true);
        setFocusable(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        installInteraction();
    }

    /**
     * Returns the map-coordinate CRS used by compatibility layers and pointer values.
     *
     * @return immutable map CRS fixed at construction
     */
    public CrsDefinition mapCrs() {
        return mapCrs;
    }

    /**
     * Returns the world/display CRS used by the viewport.
     *
     * @return immutable display CRS fixed at construction
     */
    public CrsDefinition displayCrs() {
        return displayCrs;
    }

    /**
     * Installs one active tool, replacing a distinct active instance by identity.
     *
     * <p>A replacement receives the documented cancellation/deactivation lifecycle before the new
     * tool is activated. A {@link MeasurementTool} can be installed in only one view at a time.
     *
     * @param tool non-null tool to install
     * @throws NullPointerException if {@code tool} is {@code null}
     * @throws IllegalStateException if this view is closed or the tool belongs to another view
     */
    public void setActiveTool(MapTool tool) {
        requireOpen();
        Objects.requireNonNull(tool, "tool");
        if (tool instanceof MeasurementTool measurement) {
            measurement.claim(this);
            measurementClaims.add(measurement);
        }
        try {
            ToolContextSnapshot context = toolContextSnapshot();
            applyToolOutcome(
                    callRouter(
                            () ->
                                    toolRouter.setActiveTool(
                                            tool,
                                            cancelEvent(MapToolCancelReason.TOOL_REPLACED, context),
                                            context)));
        } catch (RuntimeException | Error failure) {
            dragAnchor = null;
            applyCurrentToolCursor();
            throw failure;
        } finally {
            dragAnchor = null;
            if (routerCallDepth == 0) {
                reconcileMeasurementClaims();
            }
        }
    }

    /**
     * Clears the active tool, if present, after cancellation and deactivation callbacks.
     *
     * @throws IllegalStateException if this view is closed
     */
    public void clearActiveTool() {
        requireOpen();
        ToolContextSnapshot context = toolContextSnapshot();
        try {
            applyToolOutcome(
                    callRouter(
                            () ->
                                    toolRouter.clearActiveTool(
                                            cancelEvent(MapToolCancelReason.TOOL_CLEARED, context),
                                            context)));
        } catch (RuntimeException | Error failure) {
            dragAnchor = null;
            applyCurrentToolCursor();
            throw failure;
        } finally {
            dragAnchor = null;
        }
    }

    /**
     * Returns the active tool, if any.
     *
     * @return currently installed tool by identity, or empty
     */
    public Optional<MapTool> activeTool() {
        return toolRouter.activeTool();
    }

    /**
     * Replaces the ordered eager-layer compatibility snapshot.
     *
     * <p>The input list is defensively copied and mapped to non-owning snapshot bindings.
     *
     * @param layers ordered non-null eager layers
     * @throws NullPointerException if the list or an element is {@code null}
     * @throws IllegalArgumentException if layer identifiers are duplicated
     * @throws IllegalStateException if this view is closed
     */
    public void setLayers(List<Layer> layers) {
        List<Layer> candidate = List.copyOf(Objects.requireNonNull(layers, "layers"));
        List<MapLayerBinding> mapped = new ArrayList<>(candidate.size());
        for (Layer layer : candidate) {
            mapped.add(MapLayerBinding.snapshot(Objects.requireNonNull(layer, "layer")));
        }
        setLayerBindings(mapped);
    }

    /**
     * Returns eager layers in relative order, excluding source-backed bindings.
     *
     * @return immutable snapshot of installed compatibility layers
     */
    public List<Layer> layers() {
        List<Layer> result = new ArrayList<>();
        for (MapLayerBinding binding : bindings) {
            if (binding.kind() == MapLayerBinding.Kind.SNAPSHOT) {
                result.add(binding.layer());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Replaces the complete ordered layer-binding stack transactionally.
     *
     * <p>The list is defensively copied. New bindings are claimed before the state changes; removed
     * owned bindings are closed after a successful replacement. Each identity may occur once and
     * identifiers must be unique.
     *
     * @param requested ordered non-null bindings
     * @throws NullPointerException if the list or an element is {@code null}
     * @throws IllegalArgumentException if an identity or identifier is duplicated
     * @throws IllegalStateException if this view is closed, a binding is closed, or a binding is
     *     attached to another view
     */
    public void setLayerBindings(List<MapLayerBinding> requested) {
        requireOpen();
        List<MapLayerBinding> candidate =
                List.copyOf(Objects.requireNonNull(requested, "bindings"));
        CandidateBindings validated = validateBindings(candidate);
        Set<MapLayerBinding> oldIdentities = identitySet(bindings);
        List<MapLayerBinding> acquired = new ArrayList<>();
        try {
            for (MapLayerBinding binding : candidate) {
                if (!oldIdentities.contains(binding)) {
                    binding.claim(this);
                    acquired.add(binding);
                }
            }
        } catch (RuntimeException | Error failure) {
            for (int index = acquired.size() - 1; index >= 0; index--) {
                acquired.get(index).release(this);
            }
            throw failure;
        }

        List<MapLayerBinding> previous = bindings;
        Optional<FeatureSelection> reconciled =
                reconcileAfterBindingReplacement(selection, previous, candidate);
        bindings = candidate;
        resolvedFeatureBindings = validated.resolved();
        reconcileRasterRenderOptions(candidate);
        hoverProbe = Optional.empty();
        hoverPaintState = Optional.empty();
        selectionPaintState = Optional.empty();
        Throwable interactionFailure = null;
        try {
            boolean interactionChanged = transitionInteraction(reconciled, Optional.empty(), true);
            if (!interactionChanged) {
                repaint();
            }
        } catch (RuntimeException | Error failure) {
            interactionFailure = failure;
        }

        Throwable failure = releaseRemoved(previous, candidate);
        if (failure == null) {
            failure = interactionFailure;
        } else if (interactionFailure != null) {
            suppressDistinct(failure, interactionFailure);
        }
        reconcileReportsAfterReplacement(previous, candidate);
        failure = drainSourceReportNotifications(failure);
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    /**
     * Returns the complete immutable ordered binding stack.
     *
     * @return immutable binding-list snapshot in paint order
     */
    public List<MapLayerBinding> layerBindings() {
        return bindings;
    }

    /**
     * Replaces one installed raster layer's view-owned presentation options and repaints.
     *
     * <p>This method follows Swing's event-dispatch-thread mutation contract. It neither replaces
     * nor closes the binding or source.
     *
     * @param layerId installed raster layer identifier
     * @param options immutable options snapshot
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code layerId} does not identify an installed raster
     *     binding
     * @throws IllegalStateException if the view is closed or the caller is not on the
     *     event-dispatch thread
     */
    public void setRasterRenderOptions(String layerId, RasterRenderOptions options) {
        requireOpen();
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Raster render options must be changed on the event dispatch thread");
        }
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(options, "options");
        MapLayerBinding binding = bindingById(bindings, layerId);
        if (binding == null
                || (binding.kind() != MapLayerBinding.Kind.RASTER
                        && binding.kind() != MapLayerBinding.Kind.ELEVATION)) {
            throw new IllegalArgumentException(
                    "layerId must identify an installed raster or elevation binding");
        }
        rasterRenderOptions.put(binding, options);
        repaint();
    }

    /**
     * Replaces one installed elevation layer's immutable colorization style and repaints.
     *
     * <p>This method follows Swing's event-dispatch-thread mutation contract. It neither replaces
     * nor closes the binding or source.
     *
     * @param layerId installed elevation layer identifier
     * @param style immutable style whose ramp unit matches the source unit
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the identifier is not an installed elevation binding or
     *     the ramp unit differs from the source unit
     * @throws IllegalStateException if the view is closed or the caller is not on the
     *     event-dispatch thread
     */
    public void setElevationRasterStyle(String layerId, ElevationRasterStyle style) {
        requireOpen();
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Elevation raster style must be changed on the event dispatch thread");
        }
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(style, "style");
        MapLayerBinding binding = bindingById(bindings, layerId);
        if (binding == null || binding.kind() != MapLayerBinding.Kind.ELEVATION) {
            throw new IllegalArgumentException(
                    "layerId must identify an installed elevation binding");
        }
        if (style.colorRamp().unit() != binding.elevationSource().metadata().elevationUnit()) {
            throw new IllegalArgumentException("color-ramp unit must equal source elevation unit");
        }
        elevationRasterStyles.put(binding, style);
        repaint();
    }

    /**
     * Returns non-empty source reports in installed layer order.
     *
     * @return immutable map from stable layer identifier to immutable structured report
     */
    public Map<String, DiagnosticReport> sourceReports() {
        LinkedHashMap<String, DiagnosticReport> ordered = new LinkedHashMap<>();
        for (MapLayerBinding binding : bindings) {
            DiagnosticReport report = sourceReports.get(binding.id());
            if (binding.kind() != MapLayerBinding.Kind.SNAPSHOT
                    && report != null
                    && !report.entries().isEmpty()) {
                ordered.put(binding.id(), report);
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    /**
     * Adds a source-report listener; duplicate instances receive duplicate callbacks.
     *
     * @param listener non-null listener called on the event-dispatch thread
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public void addMapSourceReportListener(MapSourceReportListener listener) {
        sourceReportListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes the first identical source-report listener registration.
     *
     * @param listener identity to remove; {@code null} simply matches no registration
     */
    public void removeMapSourceReportListener(MapSourceReportListener listener) {
        removeIdentical(sourceReportListeners, listener);
    }

    /**
     * Returns the selected stable feature identity after reconciling current content.
     *
     * @return current immutable layer/feature identity, or empty
     */
    public Optional<FeatureSelection> selection() {
        ViewContentSnapshot snapshot = captureContentAtBoundary(bindings, viewport());
        reconcileInteraction(snapshot, true, true);
        drainSourceReportNotifications();
        return selection;
    }

    /**
     * Selects an identity that exists uniquely in the current content snapshot.
     *
     * @param requested non-null stable layer/feature identity
     * @throws NullPointerException if {@code requested} is {@code null}
     * @throws IllegalArgumentException if the identity is not uniquely present
     * @throws IllegalStateException if this view is closed
     */
    public void setSelection(FeatureSelection requested) {
        requireOpen();
        Objects.requireNonNull(requested, "requested");
        ViewContentSnapshot snapshot = captureContentAtBoundary(bindings, viewport());
        if (!contains(snapshot, requested)) {
            throw new IllegalArgumentException("selection must identify a current feature");
        }
        transitionInteraction(Optional.of(requested), hover, true);
        drainSourceReportNotifications();
    }

    /**
     * Clears selection without consulting potentially mutable layer content.
     *
     * @throws IllegalStateException if this view is closed
     */
    public void clearSelection() {
        requireOpen();
        transitionInteraction(Optional.empty(), hover, true);
    }

    /**
     * Returns the current stable hover identity after reconciling current content.
     *
     * @return current immutable topmost hit identity, or empty
     */
    public Optional<MapHit> hover() {
        ViewContentSnapshot snapshot = captureContentAtBoundary(bindings, viewport());
        reconcileInteraction(snapshot, true, true);
        drainSourceReportNotifications();
        return hover;
    }

    /**
     * Adds a hover listener; duplicate instances receive duplicate callbacks.
     *
     * @param listener non-null listener called on the event-dispatch thread
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public void addMapHoverListener(MapHoverListener listener) {
        hoverListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes the first identical hover-listener registration.
     *
     * @param listener identity to remove; {@code null} simply matches no registration
     */
    public void removeMapHoverListener(MapHoverListener listener) {
        removeIdentical(hoverListeners, listener);
    }

    /**
     * Adds a selection listener; duplicate instances receive duplicate callbacks.
     *
     * @param listener non-null listener called on the event-dispatch thread
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public void addMapSelectionListener(MapSelectionListener listener) {
        selectionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes the first identical selection-listener registration.
     *
     * @param listener identity to remove; {@code null} simply matches no registration
     */
    public void removeMapSelectionListener(MapSelectionListener listener) {
        removeIdentical(selectionListeners, listener);
    }

    /**
     * Returns the immutable hover overlay symbol bundle.
     *
     * @return current marker/line/fill overlay symbols
     */
    public FeatureOverlaySymbols hoverOverlaySymbols() {
        return hoverOverlay;
    }

    /**
     * Replaces the hover overlay symbols and repaints on a real change.
     *
     * @param overlay non-null immutable overlay symbol bundle
     * @throws NullPointerException if {@code overlay} is {@code null}
     * @throws IllegalStateException if this view is closed
     */
    public void setHoverOverlaySymbols(FeatureOverlaySymbols overlay) {
        requireOpen();
        FeatureOverlaySymbols requested = Objects.requireNonNull(overlay, "overlay");
        if (!hoverOverlay.equals(requested)) {
            hoverOverlay = requested;
            repaint();
        }
    }

    /**
     * Returns the immutable selection overlay symbol bundle.
     *
     * @return current marker/line/fill overlay symbols
     */
    public FeatureOverlaySymbols selectionOverlaySymbols() {
        return selectionOverlay;
    }

    /**
     * Replaces the selection overlay symbols and repaints on a real change.
     *
     * @param overlay non-null immutable overlay symbol bundle
     * @throws NullPointerException if {@code overlay} is {@code null}
     * @throws IllegalStateException if this view is closed
     */
    public void setSelectionOverlaySymbols(FeatureOverlaySymbols overlay) {
        requireOpen();
        FeatureOverlaySymbols requested = Objects.requireNonNull(overlay, "overlay");
        if (!selectionOverlay.equals(requested)) {
            selectionOverlay = requested;
            repaint();
        }
    }

    /**
     * Returns visible feature hits in deterministic topmost-first paint order.
     *
     * <p>Coordinates and tolerance use logical-screen pixels. Queries outside the component return
     * an empty result, and the tolerance is capped to the component diagonal.
     *
     * @param screenX finite logical-screen x ordinate
     * @param screenY finite logical-screen y ordinate
     * @param tolerancePixels finite non-negative logical-pixel tolerance
     * @return immutable ordered hit results
     * @throws IllegalArgumentException if an ordinate is non-finite or tolerance is negative
     */
    public MapHitResults hitTest(double screenX, double screenY, double tolerancePixels) {
        if (!Double.isFinite(screenX)
                || !Double.isFinite(screenY)
                || !Double.isFinite(tolerancePixels)
                || tolerancePixels < 0.0) {
            throw new IllegalArgumentException(
                    "Hit coordinates and non-negative tolerance must be finite");
        }
        Dimension size = effectiveSize();
        ViewContentSnapshot snapshot = captureContentAtBoundary(bindings, viewport());
        reconcileInteraction(snapshot, true, true);
        if (screenX < 0.0 || screenX >= size.width || screenY < 0.0 || screenY >= size.height) {
            drainSourceReportNotifications();
            return MapHitResults.of(List.of());
        }
        double cappedTolerance = Math.min(tolerancePixels, Math.hypot(size.width, size.height));
        MapViewport viewportSnapshot = viewport();
        MapHitResults result =
                hitTestSnapshot(
                        snapshot, viewportSnapshot, size, screenX, screenY, cappedTolerance);
        drainSourceReportNotifications();
        return result;
    }

    private MapHitResults hitTestSnapshot(
            ViewContentSnapshot snapshot,
            MapViewport viewportSnapshot,
            Dimension size,
            double screenX,
            double screenY,
            double tolerancePixels) {
        Rectangle2D clip = new Rectangle2D.Double(0.0, 0.0, size.width, size.height);
        List<MapHit> hits = new ArrayList<>();
        for (int layerIndex = snapshot.layers().size() - 1; layerIndex >= 0; layerIndex--) {
            LayerSnapshot layer = snapshot.layers().get(layerIndex);
            for (int featureIndex = layer.features().size() - 1;
                    featureIndex >= 0;
                    featureIndex--) {
                VisualFeature feature = layer.features().get(featureIndex);
                if (hitFeature(
                        feature, viewportSnapshot, clip, screenX, screenY, tolerancePixels)) {
                    hits.add(new MapHit(layer.id(), feature.id()));
                }
            }
        }
        return MapHitResults.of(hits);
    }

    /**
     * Returns the current viewport, resized to the component's current dimensions.
     *
     * @return immutable viewport in the display CRS and logical-screen pixels
     */
    public MapViewport viewport() {
        synchronizeViewportSize();
        return viewport;
    }

    /**
     * Replaces the viewport state and repaints the component.
     *
     * @param viewport non-null immutable display-CRS viewport
     * @throws NullPointerException if {@code viewport} is {@code null}
     * @throws IllegalStateException if this view is closed
     */
    public void setViewport(MapViewport viewport) {
        requireOpen();
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        if (!clearHover()) {
            repaint();
        }
    }

    /**
     * Fits all representable non-empty layers using the requested logical-screen-pixel padding.
     *
     * <p>Feature-source CRS failures become structured source reports; compatibility-layer CRS
     * failures are thrown. An empty layer stack leaves the viewport unchanged.
     *
     * @param paddingPixels finite non-negative padding in logical pixels
     * @throws IllegalArgumentException if padding is invalid for viewport fitting
     * @throws IllegalStateException if this view is closed
     * @throws CrsException if a compatibility layer extent cannot be transformed
     */
    public void fitToData(double paddingPixels) {
        requireOpen();
        Envelope projected = null;
        for (MapLayerBinding binding : bindings) {
            if (binding.kind() == MapLayerBinding.Kind.RASTER) {
                Envelope rasterBounds = binding.rasterSource().metadata().mapBounds().orElseThrow();
                projected = projected == null ? rasterBounds : projected.union(rasterBounds);
                continue;
            }
            if (binding.kind() == MapLayerBinding.Kind.ELEVATION) {
                Envelope elevationBounds = binding.elevationSource().metadata().sampleBounds();
                projected = projected == null ? elevationBounds : projected.union(elevationBounds);
                continue;
            }
            Optional<Envelope> extent;
            CrsOperation operation;
            if (binding.kind() == MapLayerBinding.Kind.SNAPSHOT) {
                extent = binding.layer().envelope();
                operation = mapToDisplay;
            } else {
                extent = binding.source().metadata().extent();
                operation = resolvedFeatureBindings.get(binding).sourceToDisplay();
            }
            if (extent.isEmpty()) {
                continue;
            }
            try {
                Envelope next = operation.transformEnvelopeStrict(extent.orElseThrow());
                projected = projected == null ? next : projected.union(next);
                if (binding.kind() == MapLayerBinding.Kind.FEATURE) {
                    updateSourceResult(binding.id(), DiagnosticReport.empty(), true);
                }
            } catch (CrsException failure) {
                if (binding.kind() == MapLayerBinding.Kind.SNAPSHOT) {
                    throw failure;
                }
                updateSourceResult(binding.id(), crsFailure(binding.source(), failure), false);
            }
        }
        if (projected != null) {
            Dimension size = effectiveSize();
            viewport = MapViewport.fit(size.width, size.height, projected, paddingPixels);
            if (!clearHover()) {
                repaint();
            }
        }
        drainSourceReportNotifications();
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean disabling = isEnabled() && !enabled;
        if (disabling) {
            runThenClearHover(() -> super.setEnabled(false));
        } else {
            super.setEnabled(enabled);
        }
    }

    /**
     * Converts a screen location into map coordinates when it lies in the inverse domain.
     *
     * @param screenX finite logical-screen x ordinate in pixels
     * @param screenY finite logical-screen y ordinate in pixels
     * @return immutable map-CRS coordinate, or empty outside the supported inverse domain
     * @throws IllegalArgumentException if an ordinate is non-finite
     * @throws CrsException for a registry/operation failure other than an expected domain miss
     */
    public Optional<Coordinate> screenToMap(double screenX, double screenY) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("Screen coordinates must be finite");
        }
        Coordinate world;
        try {
            world = viewport().screenToWorld(screenX, screenY);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        try {
            return Optional.of(displayToMap.transform(world));
        } catch (CrsException exception) {
            if (isExpectedConversionMiss(exception)) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /**
     * Converts a map coordinate into logical-screen coordinates when it is representable.
     *
     * @param coordinate non-null coordinate in the map CRS
     * @return immutable logical-screen coordinate in pixels, or empty outside the supported domain
     * @throws NullPointerException if {@code coordinate} is {@code null}
     * @throws CrsException for a registry/operation failure other than an expected domain miss
     */
    public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        Coordinate world;
        try {
            world = mapToDisplay.transform(coordinate);
        } catch (CrsException exception) {
            if (isExpectedConversionMiss(exception)) {
                return Optional.empty();
            }
            throw exception;
        }
        try {
            return Optional.of(viewport().worldToScreen(world));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Adds a pointer listener; duplicate instances receive duplicate callbacks.
     *
     * @param listener non-null listener called on the event-dispatch thread
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public void addMapPointerListener(MapPointerListener listener) {
        pointerListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes the first identical pointer-listener registration.
     *
     * @param listener identity to remove; {@code null} simply matches no registration
     */
    public void removeMapPointerListener(MapPointerListener listener) {
        for (int index = 0; index < pointerListeners.size(); index++) {
            if (pointerListeners.get(index) == listener) {
                pointerListeners.remove(index);
                return;
            }
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (isEnabled()) {
            resumeTool();
        }
    }

    @Override
    public void removeNotify() {
        runThenClearHover(this::cancelAndRemoveNotify);
    }

    /**
     * Permanently disposes this view and closes installed owned bindings.
     *
     * <p>Close is idempotent. It cancels/deactivates the active tool, releases borrowed bindings,
     * closes owned sources, and clears view-owned caches. If multiple callbacks or sources fail,
     * the first failure is thrown and distinct later failures are suppressed.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        Throwable primary = null;
        try {
            clearActiveTool();
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        List<MapLayerBinding> previous = bindings;
        closed = true;
        bindings = List.of();
        resolvedFeatureBindings = Map.of();
        rasterRenderOptions.clear();
        elevationRasterStyles.clear();
        if (renderCacheMode.enabled()) {
            try {
                clearRenderCacheForClose();
            } catch (RuntimeException | Error failure) {
                if (primary == null) {
                    primary = failure;
                } else {
                    suppressDistinct(primary, failure);
                }
            }
        }
        selection = Optional.empty();
        hover = Optional.empty();
        hoverProbe = Optional.empty();
        hoverPaintState = Optional.empty();
        selectionPaintState = Optional.empty();
        Throwable closeFailure = releaseRemoved(previous, List.of());
        sourceAvailability.clear();
        if (primary == null) {
            primary = closeFailure;
        } else if (closeFailure != null) {
            suppressDistinct(primary, closeFailure);
        }
        repaint();
        primary = drainSourceReportNotifications(primary);
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    private void clearRenderCacheForClose() {
        Throwable cleanupFailure = EdtCompletion.runAndWait(renderCache::clear);
        if (cleanupFailure != null) {
            throwUnchecked(cleanupFailure);
        }
    }

    private void cancelAndRemoveNotify() {
        Throwable primary = null;
        try {
            cancelInteraction(MapToolCancelReason.VIEW_REMOVED, lastButtonsDown);
        } catch (RuntimeException | Error failure) {
            primary = failure;
        } finally {
            dragAnchor = null;
        }
        try {
            super.removeNotify();
        } catch (RuntimeException | Error failure) {
            if (primary == null) {
                primary = failure;
            } else {
                suppressDistinct(primary, failure);
            }
        }
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        synchronizeViewportSize();
        boolean ownsCacheCollector = activeRenderCacheCollector == null;
        if (ownsCacheCollector) {
            activeRenderCacheCollector = new AwtRenderCache.CacheEventCollector();
        }
        if (renderCacheMode.enabled() && EventQueue.isDispatchThread()) {
            renderCache.snapshotState(activeRenderCacheCollector);
        }
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        boolean interactionChanged = false;
        boolean paintStateChanged = false;
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        try {
            ViewContentSnapshot content = captureContent(bindings, viewport, true);
            interactionChanged = reconcileInteraction(content, false, false);
            MapViewport viewportSnapshot = viewport;
            MapScreenBasis basisSnapshot = screenBasis(viewportSnapshot);
            Optional<MapHit> hoverSnapshot = hover;
            Optional<FeatureSelection> selectionSnapshot = selection;
            MapTool activeToolSnapshot = toolRouter.activeTool().orElse(null);
            Optional<MeasurementState> measurementSnapshot =
                    activeToolSnapshot instanceof MeasurementTool measurement
                            ? Optional.of(measurement.state())
                            : Optional.empty();
            OverlayCandidate hoverCandidate = null;
            OverlayCandidate selectionCandidate = null;
            if (isOpaque()) {
                graphics2D.setColor(getBackground());
                graphics2D.fillRect(0, 0, getWidth(), getHeight());
            }
            graphics2D.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (LayerSnapshot layer : content.layers()) {
                layer.raster()
                        .ifPresent(
                                raster -> renderRasterLayer(graphics2D, raster, viewportSnapshot));
                for (VisualFeature feature : layer.features()) {
                    SymbolRenderResult result =
                            renderFeature(graphics2D, feature, viewportSnapshot, basisSnapshot);
                    if (hoverSnapshot.isPresent()
                            && matches(hoverSnapshot.orElseThrow(), layer.id(), feature.id())) {
                        hoverCandidate = new OverlayCandidate(feature, result.paintPresence());
                    }
                    if (selectionSnapshot.isPresent()
                            && matches(selectionSnapshot.orElseThrow(), layer.id(), feature.id())) {
                        selectionCandidate = new OverlayCandidate(feature, result.paintPresence());
                    }
                }
            }
            boolean hoverPaintChanged =
                    retainInteractionPaintState(hoverSnapshot, hoverCandidate, true);
            boolean selectionPaintChanged =
                    retainInteractionPaintState(selectionSnapshot, selectionCandidate, false);
            paintStateChanged = hoverPaintChanged || selectionPaintChanged;
            renderOverlay(
                    graphics2D, hoverCandidate, hoverOverlay, viewportSnapshot, basisSnapshot);
            renderOverlay(
                    graphics2D,
                    selectionCandidate,
                    selectionOverlay,
                    viewportSnapshot,
                    basisSnapshot);
            measurementSnapshot.ifPresent(
                    state ->
                            MeasurementOverlayRenderer.render(
                                    graphics2D,
                                    state,
                                    mapToDisplay,
                                    viewportSnapshot,
                                    getWidth(),
                                    getHeight()));
        } catch (RuntimeException failure) {
            runtimeFailure = failure;
        } catch (Error failure) {
            errorFailure = failure;
        } finally {
            if (renderCacheMode.enabled() && EventQueue.isDispatchThread()) {
                renderCache.snapshotState(activeRenderCacheCollector);
            }
            if (ownsCacheCollector) {
                activeRenderCacheCollector = null;
            }
            graphics2D.dispose();
            if (interactionChanged || paintStateChanged) {
                repaint();
            }
        }
        try {
            drainInteractionNotifications();
        } catch (RuntimeException notificationFailure) {
            if (runtimeFailure == null && errorFailure == null) {
                runtimeFailure = notificationFailure;
            } else {
                suppressDistinct(
                        errorFailure != null ? errorFailure : runtimeFailure, notificationFailure);
            }
        } catch (Error notificationFailure) {
            if (runtimeFailure == null && errorFailure == null) {
                errorFailure = notificationFailure;
            } else {
                suppressDistinct(
                        errorFailure != null ? errorFailure : runtimeFailure, notificationFailure);
            }
        }
        try {
            drainSourceReportNotifications();
        } catch (RuntimeException notificationFailure) {
            if (runtimeFailure == null && errorFailure == null) {
                runtimeFailure = notificationFailure;
            } else {
                suppressDistinct(
                        errorFailure != null ? errorFailure : runtimeFailure, notificationFailure);
            }
        } catch (Error notificationFailure) {
            if (runtimeFailure == null && errorFailure == null) {
                errorFailure = notificationFailure;
            } else {
                suppressDistinct(
                        errorFailure != null ? errorFailure : runtimeFailure, notificationFailure);
            }
        }
        if (errorFailure != null) {
            throw errorFailure;
        }
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
    }

    @Override
    protected boolean processKeyBinding(
            KeyStroke keyStroke, KeyEvent event, int condition, boolean pressed) {
        if (!pressed
                || event.getModifiersEx() != 0
                || !isEnabled()
                || !isDisplayable()
                || !isFocusOwner()) {
            return super.processKeyBinding(keyStroke, event, condition, pressed);
        }
        if (routeFocusedKey(event.getKeyCode())) {
            return true;
        }
        return super.processKeyBinding(keyStroke, event, condition, pressed);
    }

    boolean routeFocusedKey(int keyCode) {
        if (keyCode == KeyEvent.VK_BACK_SPACE && activeTool().isPresent()) {
            ToolContextSnapshot context = toolContextSnapshot();
            try {
                RouteOutcome outcome =
                        callRouter(
                                () ->
                                        toolRouter.routeCommand(
                                                new MapToolCommandEvent(
                                                        nextToolSequence(),
                                                        MapToolCommand.DELETE_BACKWARD),
                                                context));
                applyToolOutcome(outcome);
                if (outcome.suppressDefault()) {
                    return true;
                }
            } catch (RuntimeException | Error failure) {
                applyCurrentToolCursor();
                clearHoverSuppressing(failure);
                throw failure;
            }
        } else if (keyCode == KeyEvent.VK_ESCAPE
                && (activeTool().isPresent() || dragAnchor != null)) {
            ToolContextSnapshot context = toolContextSnapshot();
            MapToolEvent cancel = cancelEvent(MapToolCancelReason.USER_CANCEL, context);
            boolean hadNavigation = dragAnchor != null;
            try {
                RouteOutcome outcome =
                        callRouter(() -> toolRouter.cancelInteraction(cancel, context));
                dragAnchor = null;
                applyToolOutcome(outcome);
                clearHover();
                if (outcome.suppressDefault() || hadNavigation) {
                    return true;
                }
            } catch (RuntimeException | Error failure) {
                dragAnchor = null;
                applyCurrentToolCursor();
                clearHoverSuppressing(failure);
                throw failure;
            }
        }
        return false;
    }

    private static void suppressDistinct(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }

    private SymbolRenderResult renderFeature(
            Graphics2D graphics,
            VisualFeature feature,
            MapViewport viewportSnapshot,
            MapScreenBasis basisSnapshot) {
        Symbol symbol = feature.symbol();
        if (!(symbol instanceof FeatureStyle)
                && symbol.role() != geometryRole(feature.geometry())) {
            throw roleMismatch(feature, new SymbolSnapshot(symbol.role(), symbol.rendererKey()));
        }
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(
                                toScreen(
                                        point.coordinate(),
                                        feature.sourceToDisplay(),
                                        viewportSnapshot))
                        : Optional.empty();
        ScreenRenderPlan screenPlan =
                buildScreenRenderPlan(
                        symbol,
                        feature.geometry(),
                        feature.sourceToDisplay(),
                        viewportSnapshot,
                        basisSnapshot);
        AwtSymbolRenderContext context =
                contextWithScreenPlan(
                        graphics,
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
                        feature.sourceToDisplay(),
                        1.0,
                        false,
                        OptionalDouble.empty(),
                        markerAnchor,
                        viewportSnapshot,
                        basisSnapshot,
                        screenPlan,
                        -1);
        SymbolRenderResult result = dispatch(symbol, context);
        if (feature.geometry() instanceof PointGeometry) {
            Envelope bounds = result.nominalMarkerBounds().orElseThrow();
            renderPointLabel(graphics, feature.name(), rectangle(bounds));
        }
        return result;
    }

    private void renderOverlay(
            Graphics2D graphics,
            OverlayCandidate candidate,
            FeatureOverlaySymbols overlay,
            MapViewport viewportSnapshot,
            MapScreenBasis basisSnapshot) {
        if (candidate == null || candidate.presence() != AwtLogicalPaintPresence.PRESENT) {
            return;
        }
        VisualFeature feature = candidate.feature();
        Symbol symbol = overlaySymbol(overlay, feature.geometry());
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(
                                toScreen(
                                        point.coordinate(),
                                        feature.sourceToDisplay(),
                                        viewportSnapshot))
                        : Optional.empty();
        ScreenRenderPlan screenPlan =
                buildScreenRenderPlan(
                        symbol,
                        feature.geometry(),
                        feature.sourceToDisplay(),
                        viewportSnapshot,
                        basisSnapshot);
        dispatch(
                symbol,
                contextWithScreenPlan(
                        graphics,
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
                        feature.sourceToDisplay(),
                        1.0,
                        false,
                        OptionalDouble.empty(),
                        markerAnchor,
                        viewportSnapshot,
                        basisSnapshot,
                        screenPlan,
                        -1));
    }

    private static Symbol overlaySymbol(FeatureOverlaySymbols overlay, Geometry geometry) {
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            return overlay.marker();
        }
        if (geometry instanceof LineStringGeometry || geometry instanceof MultiLineStringGeometry) {
            return overlay.line();
        }
        if (geometry instanceof PolygonGeometry || geometry instanceof MultiPolygonGeometry) {
            return overlay.fill();
        }
        throw new IllegalArgumentException("Unsupported overlay geometry");
    }

    private static boolean matches(MapHit hit, String layerId, String featureId) {
        return hit.layerId().equals(layerId) && hit.featureId().equals(featureId);
    }

    private static boolean matches(FeatureSelection selection, String layerId, String featureId) {
        return selection.layerId().equals(layerId) && selection.featureId().equals(featureId);
    }

    private AwtSymbolRenderContext context(
            Graphics2D graphics,
            SymbolRole role,
            String featureId,
            Geometry featureGeometry,
            Geometry renderGeometry,
            CrsOperation sourceToDisplay,
            double inheritedOpacity,
            boolean closedRing,
            OptionalDouble endpointBearing,
            Optional<Coordinate> markerAnchor,
            MapViewport viewportSnapshot,
            MapScreenBasis basisSnapshot) {
        return new AwtSymbolRenderContext(
                graphics,
                role,
                featureId,
                featureGeometry,
                renderGeometry,
                sourceToDisplay,
                viewportSnapshot,
                inheritedOpacity,
                closedRing,
                endpointBearing,
                markerAnchor,
                basisSnapshot,
                this,
                null,
                -1);
    }

    private AwtSymbolRenderContext contextWithScreenPlan(
            Graphics2D graphics,
            SymbolRole role,
            String featureId,
            Geometry featureGeometry,
            Geometry renderGeometry,
            CrsOperation sourceToDisplay,
            double inheritedOpacity,
            boolean closedRing,
            OptionalDouble endpointBearing,
            Optional<Coordinate> markerAnchor,
            MapViewport viewportSnapshot,
            MapScreenBasis basisSnapshot,
            ScreenRenderPlan screenPlan,
            int sourceComponent) {
        return new AwtSymbolRenderContext(
                graphics,
                role,
                featureId,
                featureGeometry,
                renderGeometry,
                sourceToDisplay,
                viewportSnapshot,
                inheritedOpacity,
                closedRing,
                endpointBearing,
                markerAnchor,
                basisSnapshot,
                this,
                screenPlan,
                sourceComponent);
    }

    private SymbolRenderResult dispatch(Symbol symbol, AwtSymbolRenderContext context) {
        SymbolSnapshot snapshot = new SymbolSnapshot(symbol.role(), symbol.rendererKey());
        AwtSymbolRenderer renderer = symbolRenderers.find(snapshot.role(), snapshot.rendererKey());
        if (renderer == null) {
            throw rendererNotRegistered(snapshot);
        }
        if (!renderer.supports(symbol)) {
            throw rendererValueMismatch(snapshot);
        }
        SymbolRenderResult result = renderer.render(symbol, context);
        boolean markerResult = result != null && result.nominalMarkerBounds().isPresent();
        boolean valid =
                result != null
                        && (context.role() == SymbolRole.LEGACY_GEOMETRY
                                ? markerResult
                                        == (context.renderGeometry() instanceof PointGeometry)
                                : markerResult == (context.role() == SymbolRole.MARKER));
        if (!valid) {
            throw rendererFailure(
                    SymbolException.RENDERER_INVALID_RESULT,
                    "Renderer returned an incompatible result",
                    snapshot);
        }
        return result;
    }

    private boolean hitFeature(
            VisualFeature feature,
            MapViewport viewportSnapshot,
            Rectangle2D clip,
            double queryX,
            double queryY,
            double tolerance) {
        Symbol symbol = feature.symbol();
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(
                                toScreen(
                                        point.coordinate(),
                                        feature.sourceToDisplay(),
                                        viewportSnapshot))
                        : Optional.empty();
        AwtSymbolHitContext context =
                hitContext(
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
                        feature.sourceToDisplay(),
                        viewportSnapshot,
                        1.0,
                        false,
                        OptionalDouble.empty(),
                        markerAnchor,
                        queryX,
                        queryY,
                        tolerance,
                        clip);
        return dispatchHit(symbol, context);
    }

    private AwtSymbolHitContext hitContext(
            SymbolRole role,
            String featureId,
            Geometry featureGeometry,
            Geometry renderGeometry,
            CrsOperation sourceToDisplay,
            MapViewport viewportSnapshot,
            double inheritedOpacity,
            boolean closedRing,
            OptionalDouble endpointBearing,
            Optional<Coordinate> markerAnchor,
            double queryX,
            double queryY,
            double tolerance,
            Rectangle2D clip) {
        return new AwtSymbolHitContext(
                role,
                featureId,
                featureGeometry,
                renderGeometry,
                sourceToDisplay,
                viewportSnapshot,
                inheritedOpacity,
                closedRing,
                endpointBearing,
                markerAnchor,
                screenBasis(viewportSnapshot),
                queryX,
                queryY,
                tolerance,
                clip,
                this);
    }

    private boolean dispatchHit(Symbol symbol, AwtSymbolHitContext context) {
        SymbolSnapshot snapshot = new SymbolSnapshot(symbol.role(), symbol.rendererKey());
        AwtSymbolRenderer renderer = symbolRenderers.find(snapshot.role(), snapshot.rendererKey());
        if (renderer == null) {
            throw rendererNotRegistered(snapshot);
        }
        if (!renderer.supports(symbol)) {
            throw rendererValueMismatch(snapshot);
        }
        return renderer.hitTest(symbol, context);
    }

    boolean hitChild(Symbol child, AwtSymbolHitContext parent, double multiplier) {
        return dispatchHit(
                child,
                hitContext(
                        parent.role(),
                        parent.featureId(),
                        parent.featureGeometry(),
                        parent.renderGeometry(),
                        parent.sourceToDisplayOperation(),
                        parent.viewport(),
                        parent.inheritedOpacity() * multiplier,
                        parent.closedRing(),
                        parent.endpointBearingDegrees(),
                        parent.markerAnchorScreen(),
                        parent.queryX(),
                        parent.queryY(),
                        parent.tolerancePixels(),
                        parent.componentClip()));
    }

    @SuppressWarnings("deprecation")
    boolean hitBuiltIn(Symbol value, AwtSymbolHitContext context) {
        if (value instanceof CompositeSymbol composite) {
            if (context.inheritedOpacity() * composite.opacity() == 0.0) {
                return false;
            }
            List<Symbol> children = composite.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                if (context.hitChild(children.get(index), composite.opacity())) {
                    return true;
                }
            }
            return false;
        }
        if (isMultipart(context.renderGeometry())) {
            return hitMultipart(value, context);
        }
        if (value instanceof FeatureStyle style) {
            return hitLegacy(style, context);
        }
        if (value instanceof VectorMarkerSymbol marker) {
            return hitVectorMarker(marker, context);
        }
        if (value instanceof RasterIconSymbol icon) {
            return hitRasterIcon(icon, context);
        }
        if (value instanceof SolidLineSymbol line
                && context.renderGeometry() instanceof LineStringGeometry geometry) {
            return hitLine(line, geometry, context);
        }
        if (value instanceof SolidFillSymbol fill
                && context.renderGeometry() instanceof PolygonGeometry polygon) {
            if (hitOutline(fill.outline(), polygon, context, fill.opacity())) {
                return true;
            }
            ScreenPolygon screenPolygon =
                    screenPolygon(polygon, context.sourceToDisplayOperation(), context.viewport());
            boolean fillHit =
                    context.inheritedOpacity() * fill.opacity() > 0.0
                            && fill.fill().alpha() > 0
                            && ScreenGeometryHits.filledPolygonWithin(
                                    screenPolygon.rings().getFirst(),
                                    screenPolygon.rings().subList(1, screenPolygon.rings().size()),
                                    context.queryX(),
                                    context.queryY(),
                                    context.tolerancePixels());
            return analyticOrClipped(fillHit, screenPolygon.path(), context);
        }
        if (value instanceof HatchFillSymbol hatch
                && context.renderGeometry() instanceof PolygonGeometry polygon) {
            if (hitOutline(hatch.outline(), polygon, context, hatch.opacity())) {
                return true;
            }
            return hitHatch(hatch, polygon, context);
        }
        throw rendererValueMismatch(new SymbolSnapshot(value.role(), value.rendererKey()));
    }

    private boolean hitMultipart(Symbol value, AwtSymbolHitContext context) {
        List<Geometry> components = singularComponents(context.renderGeometry());
        for (int index = components.size() - 1; index >= 0; index--) {
            Geometry component = components.get(index);
            Optional<Coordinate> anchor =
                    component instanceof PointGeometry point
                            ? Optional.of(context.sourceToScreen(point.coordinate()))
                            : Optional.empty();
            if (dispatchHit(
                    value,
                    hitContext(
                            context.role(),
                            context.featureId(),
                            context.featureGeometry(),
                            component,
                            context.sourceToDisplayOperation(),
                            context.viewport(),
                            context.inheritedOpacity(),
                            false,
                            OptionalDouble.empty(),
                            anchor,
                            context.queryX(),
                            context.queryY(),
                            context.tolerancePixels(),
                            context.componentClip()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hitLegacy(FeatureStyle style, AwtSymbolHitContext context) {
        Geometry geometry = context.renderGeometry();
        if (geometry instanceof PointGeometry point) {
            Coordinate screen =
                    toScreen(
                            point.coordinate(),
                            context.sourceToDisplayOperation(),
                            context.viewport());
            double radius = style.pointDiameter() / 2.0;
            Shape circle =
                    new Ellipse2D.Double(
                            screen.x() - radius, screen.y() - radius, radius * 2.0, radius * 2.0);
            if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
                Shape stroke =
                        new BasicStroke(
                                        (float) style.strokeWidth(),
                                        BasicStroke.CAP_ROUND,
                                        BasicStroke.JOIN_ROUND)
                                .createStrokedShape(circle);
                double centerDistance =
                        Math.hypot(context.queryX() - screen.x(), context.queryY() - screen.y());
                boolean strokeHit =
                        ScreenGeometryHits.pointWithin(
                                        screen.x(),
                                        screen.y(),
                                        context.queryX(),
                                        context.queryY(),
                                        radius
                                                + style.strokeWidth() / 2.0
                                                + context.tolerancePixels())
                                && centerDistance
                                        >= Math.max(
                                                0.0,
                                                radius
                                                        - style.strokeWidth() / 2.0
                                                        - context.tolerancePixels());
                if (analyticOrClipped(strokeHit, stroke, context)) {
                    return true;
                }
            }
            boolean fillHit =
                    style.fill().alpha() > 0
                            && ScreenGeometryHits.pointWithin(
                                    screen.x(),
                                    screen.y(),
                                    context.queryX(),
                                    context.queryY(),
                                    radius + context.tolerancePixels());
            return analyticOrClipped(fillHit, circle, context);
        }
        if (geometry instanceof LineStringGeometry line) {
            CoordinateSequence screen =
                    toScreen(
                            line.coordinates(),
                            context.sourceToDisplayOperation(),
                            context.viewport());
            LineEndpointBearings bearings =
                    LineTangents.outwardScreenBearings(screen, context.featureId(), 0);
            if (bearings.startBearingDegrees().isEmpty()
                    && bearings.endBearingDegrees().isEmpty()) {
                return false;
            }
            Shape footprint =
                    new BasicStroke(
                                    (float) style.strokeWidth(),
                                    BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND)
                            .createStrokedShape(screenPath(screen, false));
            boolean hit =
                    style.stroke().alpha() > 0
                            && style.strokeWidth() > 0.0
                            && ScreenGeometryHits.polylineWithin(
                                    screen,
                                    false,
                                    context.queryX(),
                                    context.queryY(),
                                    style.strokeWidth() / 2.0 + context.tolerancePixels());
            return analyticOrClipped(hit, footprint, context);
        }
        PolygonGeometry polygon = (PolygonGeometry) geometry;
        ScreenPolygon screenPolygon =
                screenPolygon(polygon, context.sourceToDisplayOperation(), context.viewport());
        Shape path = screenPolygon.path();
        if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
            Shape stroke =
                    new BasicStroke(
                                    (float) style.strokeWidth(),
                                    BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND)
                            .createStrokedShape(path);
            double strokeRadius = style.strokeWidth() / 2.0 + context.tolerancePixels();
            for (CoordinateSequence ring : screenPolygon.rings()) {
                boolean strokeHit =
                        ScreenGeometryHits.polylineWithin(
                                ring, true, context.queryX(), context.queryY(), strokeRadius);
                if (analyticOrClipped(strokeHit, stroke, context)) {
                    return true;
                }
            }
        }
        boolean fillHit =
                style.fill().alpha() > 0
                        && ScreenGeometryHits.filledPolygonWithin(
                                screenPolygon.rings().getFirst(),
                                screenPolygon.rings().subList(1, screenPolygon.rings().size()),
                                context.queryX(),
                                context.queryY(),
                                context.tolerancePixels());
        return analyticOrClipped(fillHit, path, context);
    }

    private boolean hitVectorMarker(VectorMarkerSymbol marker, AwtSymbolHitContext context) {
        if (context.inheritedOpacity() * marker.opacity() == 0.0) {
            return false;
        }
        MarkerTransform markerTransform =
                markerTransform(marker.viewBox(), marker.placement(), context);
        AffineTransform transform = affine(markerTransform);
        VectorPath2D.Converted converted = VectorPath2D.convert(marker.path());
        if (marker.stroke().isPresent() && marker.stroke().orElseThrow().color().alpha() > 0) {
            BasicStroke stroke =
                    screenStroke(marker.stroke().orElseThrow(), context.mapScreenBasis());
            if (context.visibleShapeHit(
                    stroke.createStrokedShape(
                            transform.createTransformedShape(converted.strokePath())))) {
                return true;
            }
        }
        return marker.fill().alpha() > 0
                && context.visibleShapeHit(transform.createTransformedShape(converted.fillPath()));
    }

    private boolean hitRasterIcon(RasterIconSymbol icon, AwtSymbolHitContext context) {
        if (context.inheritedOpacity() * icon.opacity() == 0.0) {
            return false;
        }
        MarkerTransform markerTransform =
                markerTransform(
                        new Envelope(0.0, 0.0, icon.width(), icon.height()),
                        icon.placement(),
                        context);
        AffineTransform transform = affine(markerTransform);
        if (context.tolerancePixels() == 0.0) {
            Coordinate local =
                    markerTransform.screenToLocal(
                            new Coordinate(context.queryX(), context.queryY()));
            if (local.x() < 0.0
                    || local.x() >= icon.width()
                    || local.y() < 0.0
                    || local.y() >= icon.height()) {
                return false;
            }
            for (int y = 0; y < icon.height(); y++) {
                for (int x = 0; x < icon.width(); x++) {
                    if ((icon.rgbaAt(x, y) & 0xff) != 0
                            && rasterSupportContains(icon.interpolation(), x, y, local)) {
                        return true;
                    }
                }
            }
            return false;
        }
        double expansion = icon.interpolation() == RasterInterpolation.BILINEAR ? 0.5 : 0.0;
        for (int y = 0; y < icon.height(); y++) {
            for (int x = 0; x < icon.width(); x++) {
                if ((icon.rgbaAt(x, y) & 0xff) != 0) {
                    Rectangle2D support =
                            new Rectangle2D.Double(
                                    Math.max(0.0, x - expansion),
                                    Math.max(0.0, y - expansion),
                                    Math.min(icon.width(), x + 1.0 + expansion)
                                            - Math.max(0.0, x - expansion),
                                    Math.min(icon.height(), y + 1.0 + expansion)
                                            - Math.max(0.0, y - expansion));
                    double[] quad = transformedQuad(markerTransform, support);
                    boolean hit =
                            ScreenGeometryHits.convexQuadWithin(
                                    quad,
                                    context.queryX(),
                                    context.queryY(),
                                    context.tolerancePixels());
                    if (analyticOrClipped(
                            hit, transform.createTransformedShape(support), context)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean rasterSupportContains(
            RasterInterpolation interpolation, int x, int y, Coordinate local) {
        if (interpolation == RasterInterpolation.NEAREST) {
            return local.x() >= x && local.x() < x + 1.0 && local.y() >= y && local.y() < y + 1.0;
        }
        return local.x() > x - 0.5
                && local.x() < x + 1.5
                && local.y() > y - 0.5
                && local.y() < y + 1.5;
    }

    private static double[] transformedQuad(MarkerTransform transform, Rectangle2D local) {
        double[] result = new double[8];
        transformPoint(transform, local.getMinX(), local.getMinY(), result, 0);
        transformPoint(transform, local.getMaxX(), local.getMinY(), result, 2);
        transformPoint(transform, local.getMaxX(), local.getMaxY(), result, 4);
        transformPoint(transform, local.getMinX(), local.getMaxY(), result, 6);
        return result;
    }

    private static void transformPoint(
            MarkerTransform transform, double x, double y, double[] target, int offset) {
        target[offset] = transform.m00() * x + transform.m01() * y + transform.m02();
        target[offset + 1] = transform.m10() * x + transform.m11() * y + transform.m12();
    }

    private boolean hitLine(
            SolidLineSymbol line, LineStringGeometry geometry, AwtSymbolHitContext context) {
        double opacity = context.inheritedOpacity() * line.opacity();
        CoordinateSequence screen =
                toScreen(
                        geometry.coordinates(),
                        context.sourceToDisplayOperation(),
                        context.viewport());
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(screen, context.featureId(), 0);
        if (bearings.startBearingDegrees().isEmpty() && bearings.endBearingDegrees().isEmpty()) {
            return false;
        }
        int last = geometry.coordinates().size() - 1;
        if (!context.closedRing()
                && line.endMarker().isPresent()
                && bearings.endBearingDegrees().isPresent()
                && hitEndpoint(
                        line.endMarker().orElseThrow(),
                        geometry.coordinates().coordinate(last),
                        screen.coordinate(last),
                        bearings.endBearingDegrees().orElseThrow(),
                        context,
                        opacity)) {
            return true;
        }
        if (!context.closedRing()
                && line.startMarker().isPresent()
                && bearings.startBearingDegrees().isPresent()
                && hitEndpoint(
                        line.startMarker().orElseThrow(),
                        geometry.coordinates().coordinate(0),
                        screen.coordinate(0),
                        bearings.startBearingDegrees().orElseThrow(),
                        context,
                        opacity)) {
            return true;
        }
        BasicStroke stroke = screenStroke(line.stroke(), context.mapScreenBasis());
        Shape footprint = stroke.createStrokedShape(screenPath(screen, context.closedRing()));
        boolean centerlineHit =
                opacity > 0.0
                        && line.stroke().color().alpha() > 0
                        && ScreenGeometryHits.polylineWithin(
                                screen,
                                context.closedRing(),
                                context.queryX(),
                                context.queryY(),
                                stroke.getLineWidth() / 2.0 + context.tolerancePixels());
        return analyticOrClipped(centerlineHit, footprint, context);
    }

    private boolean hitEndpoint(
            Symbol marker,
            Coordinate source,
            Coordinate screen,
            double bearing,
            AwtSymbolHitContext owner,
            double opacity) {
        return dispatchHit(
                marker,
                hitContext(
                        SymbolRole.MARKER,
                        owner.featureId(),
                        owner.featureGeometry(),
                        new PointGeometry(source),
                        owner.sourceToDisplayOperation(),
                        owner.viewport(),
                        opacity,
                        false,
                        OptionalDouble.of(bearing),
                        Optional.of(screen),
                        owner.queryX(),
                        owner.queryY(),
                        owner.tolerancePixels(),
                        owner.componentClip()));
    }

    private boolean hitOutline(
            Optional<? extends Symbol> outline,
            PolygonGeometry polygon,
            AwtSymbolHitContext owner,
            double opacity) {
        if (outline.isEmpty()) {
            return false;
        }
        List<CoordinateSequence> rings = new ArrayList<>();
        rings.add(polygon.exterior());
        rings.addAll(polygon.holes());
        for (int index = rings.size() - 1; index >= 0; index--) {
            if (dispatchHit(
                    outline.orElseThrow(),
                    hitContext(
                            SymbolRole.LINE,
                            owner.featureId(),
                            owner.featureGeometry(),
                            new LineStringGeometry(rings.get(index)),
                            owner.sourceToDisplayOperation(),
                            owner.viewport(),
                            owner.inheritedOpacity() * opacity,
                            true,
                            OptionalDouble.empty(),
                            Optional.empty(),
                            owner.queryX(),
                            owner.queryY(),
                            owner.tolerancePixels(),
                            owner.componentClip()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hitHatch(
            HatchFillSymbol hatch, PolygonGeometry polygon, AwtSymbolHitContext context) {
        double opacity = context.inheritedOpacity() * hatch.opacity();
        if (opacity == 0.0 || hatch.stroke().color().alpha() == 0) {
            return false;
        }
        ScreenPolygon screenPolygon =
                screenPolygon(polygon, context.sourceToDisplayOperation(), context.viewport());
        Rectangle2D work =
                screenPolygon.path().getBounds2D().createIntersection(context.componentClip());
        if (work.isEmpty()) {
            return false;
        }
        boolean mapRelative =
                hatch.rotationMode() == io.github.mundanej.map.api.SymbolRotationMode.MAP_RELATIVE;
        Coordinate origin =
                mapRelative
                        ? context.viewport().worldToScreen(new Coordinate(0.0, 0.0))
                        : new Coordinate(0.0, 0.0);
        double bearing = mapRelative ? context.mapScreenBasis().xAxisScreenBearingDegrees() : 0.0;
        HatchSegments segments =
                HatchLayouts.cover(
                        hatch.pattern(),
                        envelope(work),
                        origin,
                        bearing,
                        SymbolTransforms.screenLength(hatch.spacing(), context.mapScreenBasis()),
                        hatch.maxSegments(),
                        context.featureId());
        Path2D lines = new Path2D.Double();
        for (int index = 0; index < segments.segmentCount(); index++) {
            lines.moveTo(segments.x1(index), segments.y1(index));
            lines.lineTo(segments.x2(index), segments.y2(index));
        }
        Area footprint =
                new Area(
                        screenStroke(hatch.stroke(), context.mapScreenBasis())
                                .createStrokedShape(lines));
        footprint.intersect(new Area(screenPolygon.path()));
        return context.visibleShapeHit(footprint);
    }

    private static MarkerTransform markerTransform(
            Envelope viewBox,
            io.github.mundanej.map.api.MarkerPlacement placement,
            AwtSymbolHitContext context) {
        return context.endpointBearingDegrees().isPresent()
                ? SymbolTransforms.markerAtScreenBearing(
                        viewBox,
                        placement,
                        context.markerAnchorScreen().orElseThrow(),
                        context.mapScreenBasis(),
                        context.endpointBearingDegrees().orElseThrow())
                : SymbolTransforms.marker(
                        viewBox,
                        placement,
                        context.markerAnchorScreen().orElseThrow(),
                        context.mapScreenBasis());
    }

    private static AffineTransform affine(MarkerTransform transform) {
        return new AffineTransform(
                transform.m00(),
                transform.m10(),
                transform.m01(),
                transform.m11(),
                transform.m02(),
                transform.m12());
    }

    private static boolean analyticOrClipped(
            boolean analyticHit, Shape logicalPaintFootprint, AwtSymbolHitContext context) {
        if (!analyticHit) {
            return false;
        }
        Rectangle2D clip = context.componentClip();
        double tolerance = context.tolerancePixels();
        boolean diskInside =
                context.queryX() - tolerance >= clip.getMinX()
                        && context.queryY() - tolerance >= clip.getMinY()
                        && context.queryX() + tolerance < clip.getMaxX()
                        && context.queryY() + tolerance < clip.getMaxY();
        return diskInside || context.visibleShapeHit(logicalPaintFootprint);
    }

    SymbolRenderResult renderChild(Symbol child, AwtSymbolRenderContext parent, double multiplier) {
        AwtSymbolRenderContext childContext =
                contextWithScreenPlan(
                        parent.parentGraphics(),
                        parent.role(),
                        parent.featureId(),
                        parent.featureGeometry(),
                        parent.renderGeometry(),
                        parent.mapToDisplayOperation(),
                        parent.inheritedOpacity() * multiplier,
                        parent.closedRing(),
                        parent.endpointBearingDegrees(),
                        parent.markerAnchorScreen(),
                        parent.viewport(),
                        parent.mapScreenBasis(),
                        parent.screenPlan(),
                        parent.sourceComponent());
        return dispatch(child, childContext);
    }

    SymbolRenderResult renderBuiltIn(Symbol value, AwtSymbolRenderContext context) {
        if (value instanceof CompositeSymbol composite) {
            SymbolRenderResult result = SymbolRenderResult.none(AwtLogicalPaintPresence.EMPTY);
            for (Symbol child : composite.children()) {
                result = result.union(context.renderChild(child, composite.opacity()));
            }
            return result;
        }
        if (isMultipart(context.renderGeometry())
                && (context.screenPlan() == null || context.sourceComponent() < 0)) {
            return renderMultipart(value, context);
        }
        if (value instanceof FeatureStyle style) {
            Rectangle2D bounds =
                    renderLegacyFeature(
                            context.parentGraphics(),
                            context.renderGeometry(),
                            style,
                            context.featureId(),
                            context.mapToDisplayOperation(),
                            context.viewport(),
                            context.screenPlan(),
                            componentIndex(context));
            AwtLogicalPaintPresence presence = legacyPresence(style, context);
            return bounds == null
                    ? SymbolRenderResult.none(presence)
                    : SymbolRenderResult.markerBounds(envelope(bounds), presence);
        }
        if (value instanceof VectorMarkerSymbol marker) {
            Coordinate anchor = context.markerAnchorScreen().orElseThrow();
            Rectangle2D bounds =
                    renderMarkerSymbol(
                            context.parentGraphics(),
                            marker,
                            new SymbolSnapshot(marker.role(), marker.rendererKey()),
                            anchor,
                            context.mapScreenBasis(),
                            context.inheritedOpacity(),
                            context.endpointBearingDegrees());
            boolean present =
                    context.inheritedOpacity() * marker.opacity() > 0.0
                            && (marker.fill().alpha() > 0
                                    || marker.stroke()
                                                    .map(SymbolStroke::color)
                                                    .orElse(Rgba.TRANSPARENT)
                                                    .alpha()
                                            > 0);
            return SymbolRenderResult.markerBounds(
                    envelope(bounds),
                    present ? AwtLogicalPaintPresence.PRESENT : AwtLogicalPaintPresence.EMPTY);
        }
        if (value instanceof RasterIconSymbol icon) {
            return renderRasterIcon(icon, context);
        }
        if (value instanceof SolidLineSymbol line
                && (context.renderGeometry() instanceof LineStringGeometry
                        || (context.screenPlan() != null
                                && context.renderGeometry() instanceof MultiLineStringGeometry))) {
            return SymbolRenderResult.none(renderRegisteredLine(line, context));
        }
        if ((value instanceof SolidFillSymbol || value instanceof HatchFillSymbol)
                && (context.renderGeometry() instanceof PolygonGeometry
                        || (context.screenPlan() != null
                                && context.renderGeometry() instanceof MultiPolygonGeometry))) {
            return SymbolRenderResult.none(renderRegisteredFill(value, context));
        }
        throw rendererValueMismatch(new SymbolSnapshot(value.role(), value.rendererKey()));
    }

    private SymbolRenderResult renderMultipart(Symbol value, AwtSymbolRenderContext context) {
        SymbolRenderResult result = SymbolRenderResult.none(AwtLogicalPaintPresence.EMPTY);
        if (context.screenPlan() != null) {
            for (int component = 0;
                    component < context.screenPlan().sourceComponentCount();
                    component++) {
                result =
                        result.union(
                                dispatch(
                                        value,
                                        contextWithScreenPlan(
                                                context.parentGraphics(),
                                                context.role(),
                                                context.featureId(),
                                                context.featureGeometry(),
                                                context.renderGeometry(),
                                                context.mapToDisplayOperation(),
                                                context.inheritedOpacity(),
                                                false,
                                                OptionalDouble.empty(),
                                                Optional.empty(),
                                                context.viewport(),
                                                context.mapScreenBasis(),
                                                context.screenPlan(),
                                                component)));
            }
            return result;
        }
        List<Geometry> components = singularComponents(context.renderGeometry());
        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            Geometry component = components.get(componentIndex);
            Optional<Coordinate> anchor =
                    component instanceof PointGeometry point
                            ? Optional.of(context.sourceToScreen(point.coordinate()))
                            : Optional.empty();
            result =
                    result.union(
                            dispatch(
                                    value,
                                    contextWithScreenPlan(
                                            context.parentGraphics(),
                                            context.role(),
                                            context.featureId(),
                                            context.featureGeometry(),
                                            component,
                                            context.mapToDisplayOperation(),
                                            context.inheritedOpacity(),
                                            false,
                                            OptionalDouble.empty(),
                                            anchor,
                                            context.viewport(),
                                            context.mapScreenBasis(),
                                            context.screenPlan(),
                                            componentIndex)));
        }
        return result;
    }

    private static boolean isMultipart(Geometry geometry) {
        return geometry instanceof MultiPointGeometry
                || geometry instanceof MultiLineStringGeometry
                || geometry instanceof MultiPolygonGeometry;
    }

    private static List<Geometry> singularComponents(Geometry geometry) {
        if (geometry instanceof MultiPointGeometry points) {
            List<Geometry> result = new ArrayList<>(points.coordinates().size());
            for (int index = 0; index < points.coordinates().size(); index++) {
                result.add(new PointGeometry(points.coordinates().coordinate(index)));
            }
            return result;
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            List<Geometry> result = new ArrayList<>(lines.partCount());
            for (int index = 0; index < lines.partCount(); index++) {
                result.add(
                        new LineStringGeometry(
                                coordinateSlice(
                                        lines.coordinates(),
                                        lines.partOffset(index),
                                        lines.partOffset(index + 1))));
            }
            return result;
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            List<Geometry> result = new ArrayList<>(polygons.polygonCount());
            for (int polygonIndex = 0; polygonIndex < polygons.polygonCount(); polygonIndex++) {
                int firstRing = polygons.polygonRingOffset(polygonIndex);
                int lastRing = polygons.polygonRingOffset(polygonIndex + 1);
                CoordinateSequence exterior =
                        coordinateSlice(
                                polygons.coordinates(),
                                polygons.ringOffset(firstRing),
                                polygons.ringOffset(firstRing + 1));
                List<CoordinateSequence> holes = new ArrayList<>(lastRing - firstRing - 1);
                for (int ringIndex = firstRing + 1; ringIndex < lastRing; ringIndex++) {
                    holes.add(
                            coordinateSlice(
                                    polygons.coordinates(),
                                    polygons.ringOffset(ringIndex),
                                    polygons.ringOffset(ringIndex + 1)));
                }
                result.add(new PolygonGeometry(exterior, holes));
            }
            return result;
        }
        throw new IllegalArgumentException("Geometry is not multipart");
    }

    private static CoordinateSequence coordinateSlice(
            CoordinateSequence coordinates, int start, int end) {
        double[] ordinates = new double[Math.multiplyExact(end - start, 2)];
        for (int sourceIndex = start, targetIndex = 0;
                sourceIndex < end;
                sourceIndex++, targetIndex += 2) {
            ordinates[targetIndex] = coordinates.x(sourceIndex);
            ordinates[targetIndex + 1] = coordinates.y(sourceIndex);
        }
        return CoordinateSequence.of(ordinates);
    }

    private SymbolRenderResult renderRasterIcon(
            RasterIconSymbol icon, AwtSymbolRenderContext context) {
        Envelope viewBox = new Envelope(0.0, 0.0, icon.width(), icon.height());
        MarkerTransform transform =
                context.endpointBearingDegrees().isPresent()
                        ? SymbolTransforms.markerAtScreenBearing(
                                viewBox,
                                icon.placement(),
                                context.markerAnchorScreen().orElseThrow(),
                                context.mapScreenBasis(),
                                context.endpointBearingDegrees().orElseThrow())
                        : SymbolTransforms.marker(
                                viewBox,
                                icon.placement(),
                                context.markerAnchorScreen().orElseThrow(),
                                context.mapScreenBasis());
        double opacity = context.inheritedOpacity() * icon.opacity();
        boolean positiveAlpha = false;
        if (opacity > 0.0) {
            BufferedImage image =
                    new BufferedImage(icon.width(), icon.height(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < icon.height(); y++) {
                for (int x = 0; x < icon.width(); x++) {
                    int rgba = icon.rgbaAt(x, y);
                    positiveAlpha |= (rgba & 0xff) != 0;
                    image.setRGB(x, y, AwtRgbaPixels.toArgb(rgba));
                }
            }
            Graphics2D child = context.createGraphics();
            try {
                child.setComposite(
                        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                child.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        icon.interpolation() == RasterInterpolation.NEAREST
                                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                child.drawImage(
                        image,
                        new AffineTransform(
                                transform.m00(),
                                transform.m10(),
                                transform.m01(),
                                transform.m11(),
                                transform.m02(),
                                transform.m12()),
                        null);
            } finally {
                child.dispose();
            }
        }
        return SymbolRenderResult.markerBounds(
                transform.nominalScreenBounds(),
                opacity > 0.0 && positiveAlpha
                        ? AwtLogicalPaintPresence.PRESENT
                        : AwtLogicalPaintPresence.EMPTY);
    }

    private static void renderRasterLayer(
            Graphics2D graphics, RasterSnapshot raster, MapViewport viewportSnapshot) {
        if (raster.placement().isPresent()
                && raster.placement().orElseThrow().kind() == RasterGridPlacement.Kind.AFFINE) {
            renderAffineRasterLayer(graphics, raster, viewportSnapshot);
            return;
        }
        Envelope bounds = raster.mapBounds();
        Coordinate topLeft =
                viewportSnapshot.worldToScreen(new Coordinate(bounds.minX(), bounds.maxY()));
        Coordinate bottomRight =
                viewportSnapshot.worldToScreen(new Coordinate(bounds.maxX(), bounds.minY()));
        double scaleX = (bottomRight.x() - topLeft.x()) / raster.image().getWidth();
        double scaleY = (bottomRight.y() - topLeft.y()) / raster.image().getHeight();
        if (!Double.isFinite(scaleX)
                || !Double.isFinite(scaleY)
                || !Double.isFinite(topLeft.x())
                || !Double.isFinite(topLeft.y())
                || scaleX <= 0.0
                || scaleY <= 0.0) {
            throw new IllegalStateException("Raster screen transform must be finite and positive");
        }
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            applyRasterClip(child, raster, viewportSnapshot);
            child.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER, (float) raster.options().opacity()));
            child.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            child.drawImage(
                    raster.image(),
                    new AffineTransform(scaleX, 0.0, 0.0, scaleY, topLeft.x(), topLeft.y()),
                    null);
        } finally {
            child.dispose();
        }
    }

    private static void renderAffineRasterLayer(
            Graphics2D graphics, RasterSnapshot raster, MapViewport viewportSnapshot) {
        var transform = raster.placement().orElseThrow().affineTransform().orElseThrow();
        RasterWindow window = raster.window();
        double left = window.column() - 0.5;
        double top = window.row() - 0.5;
        Coordinate origin = viewportSnapshot.worldToScreen(transform.gridToMap(left, top));
        Coordinate right =
                viewportSnapshot.worldToScreen(
                        transform.gridToMap(window.column() + window.width() - 0.5, top));
        Coordinate bottom =
                viewportSnapshot.worldToScreen(
                        transform.gridToMap(left, window.row() + window.height() - 0.5));
        double m00 = (right.x() - origin.x()) / raster.image().getWidth();
        double m10 = (right.y() - origin.y()) / raster.image().getWidth();
        double m01 = (bottom.x() - origin.x()) / raster.image().getHeight();
        double m11 = (bottom.y() - origin.y()) / raster.image().getHeight();
        if (!Double.isFinite(m00)
                || !Double.isFinite(m10)
                || !Double.isFinite(m01)
                || !Double.isFinite(m11)
                || !Double.isFinite(origin.x())
                || !Double.isFinite(origin.y())) {
            throw new IllegalStateException("Affine raster screen transform must be finite");
        }
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            applyRasterClip(child, raster, viewportSnapshot);
            child.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER, (float) raster.options().opacity()));
            child.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            child.drawImage(
                    raster.image(),
                    new AffineTransform(m00, m10, m01, m11, origin.x(), origin.y()),
                    null);
        } finally {
            child.dispose();
        }
    }

    private static void applyRasterClip(
            Graphics2D graphics, RasterSnapshot raster, MapViewport viewportSnapshot) {
        if (raster.clipMapBounds().equals(raster.mapBounds())) {
            return;
        }
        Coordinate topLeft =
                viewportSnapshot.worldToScreen(
                        new Coordinate(
                                raster.clipMapBounds().minX(), raster.clipMapBounds().maxY()));
        Coordinate bottomRight =
                viewportSnapshot.worldToScreen(
                        new Coordinate(
                                raster.clipMapBounds().maxX(), raster.clipMapBounds().minY()));
        graphics.clip(
                new Rectangle2D.Double(
                        topLeft.x(),
                        topLeft.y(),
                        bottomRight.x() - topLeft.x(),
                        bottomRight.y() - topLeft.y()));
    }

    private AwtLogicalPaintPresence legacyPresence(
            FeatureStyle style, AwtSymbolRenderContext context) {
        if (context.renderGeometry() instanceof PointGeometry) {
            return style.fill().alpha() > 0
                            || (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0)
                    ? AwtLogicalPaintPresence.PRESENT
                    : AwtLogicalPaintPresence.EMPTY;
        }
        if (context.renderGeometry() instanceof LineStringGeometry
                || (context.screenPlan() != null
                        && context.renderGeometry() instanceof MultiLineStringGeometry)) {
            LineEndpointBearings bearings;
            if (context.screenPlan() == null) {
                LineStringGeometry line = (LineStringGeometry) context.renderGeometry();
                CoordinateSequence screen =
                        toScreen(
                                line.coordinates(),
                                context.mapToDisplayOperation(),
                                context.viewport());
                bearings = LineTangents.outwardScreenBearings(screen, context.featureId(), 0);
            } else {
                int component = componentIndex(context);
                bearings =
                        LineTangents.outwardScreenBearings(
                                context.screenPlan().authoritativeLineCoordinates(),
                                context.screenPlan().authoritativeLineStart(component),
                                context.screenPlan().authoritativeLineEnd(component),
                                context.featureId(),
                                component);
            }
            return style.stroke().alpha() > 0
                            && style.strokeWidth() > 0.0
                            && (bearings.startBearingDegrees().isPresent()
                                    || bearings.endBearingDegrees().isPresent())
                    ? AwtLogicalPaintPresence.PRESENT
                    : AwtLogicalPaintPresence.EMPTY;
        }
        return style.fill().alpha() > 0 || (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0)
                ? AwtLogicalPaintPresence.PRESENT
                : AwtLogicalPaintPresence.EMPTY;
    }

    private Rectangle2D renderLegacyFeature(
            Graphics2D graphics,
            Geometry geometry,
            FeatureStyle style,
            String featureId,
            CrsOperation sourceToDisplayOperation,
            MapViewport viewportSnapshot,
            ScreenRenderPlan screenPlan,
            int sourceComponent) {
        graphics.setStroke(
                new BasicStroke(
                        (float) style.strokeWidth(),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));

        if (geometry instanceof PointGeometry point) {
            return renderLegacyPoint(graphics, point, style, viewportSnapshot);
        } else if (geometry instanceof LineStringGeometry
                || (screenPlan != null && geometry instanceof MultiLineStringGeometry)) {
            if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
                CoordinateSequence screen;
                int first;
                int last;
                if (screenPlan == null) {
                    LineStringGeometry line = (LineStringGeometry) geometry;
                    screen =
                            toScreen(
                                    line.coordinates(), sourceToDisplayOperation, viewportSnapshot);
                    first = 0;
                    last = screen.size();
                } else {
                    screen = screenPlan.authoritativeLineCoordinates();
                    first = screenPlan.authoritativeLineStart(sourceComponent);
                    last = screenPlan.authoritativeLineEnd(sourceComponent);
                }
                LineEndpointBearings bearings =
                        LineTangents.outwardScreenBearings(
                                screen, first, last, featureId, sourceComponent);
                if (bearings.startBearingDegrees().isEmpty()
                        && bearings.endBearingDegrees().isEmpty()) {
                    return null;
                }
                graphics.setColor(color(style.stroke()));
                if (screenPlan == null) {
                    graphics.draw(screenPath(screen, false));
                } else {
                    drawPlannedLineGeometry(graphics, screenPlan, sourceComponent);
                }
            }
        } else if (geometry instanceof PolygonGeometry
                || (screenPlan != null && geometry instanceof MultiPolygonGeometry)) {
            Path2D renderingPath =
                    screenPlan == null
                            ? screenPolygon(
                                            (PolygonGeometry) geometry,
                                            sourceToDisplayOperation,
                                            viewportSnapshot)
                                    .path()
                            : plannedPolygonPath(screenPlan, sourceComponent);
            if (renderingPath != null) {
                renderLegacyScreenPolygon(graphics, renderingPath, style);
            }
        }
        return null;
    }

    private static void drawPlannedLineGeometry(
            Graphics2D graphics, ScreenRenderPlan plan, int sourceComponent) {
        int first = plan.renderComponentOffset(sourceComponent);
        int last = plan.renderComponentOffset(sourceComponent + 1);
        for (int component = first; component < last; component++) {
            graphics.draw(
                    screenPath(
                            plan.renderingLineCoordinates(),
                            plan.renderingLineStart(component),
                            plan.renderingLineEnd(component),
                            false));
        }
    }

    private static void renderLegacyScreenPolygon(
            Graphics2D graphics, Path2D path, FeatureStyle style) {
        if (style.fill().alpha() > 0) {
            graphics.setColor(color(style.fill()));
            graphics.fill(path);
        }
        if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
            graphics.setColor(color(style.stroke()));
            graphics.draw(path);
        }
    }

    private Rectangle2D renderLegacyPoint(
            Graphics2D graphics,
            PointGeometry point,
            FeatureStyle style,
            MapViewport viewportSnapshot) {
        Coordinate screen = toScreen(point.coordinate(), viewportSnapshot);
        double diameter = style.pointDiameter();
        double radius = diameter / 2.0;
        Ellipse2D marker =
                new Ellipse2D.Double(screen.x() - radius, screen.y() - radius, diameter, diameter);
        if (style.fill().alpha() > 0) {
            graphics.setColor(color(style.fill()));
            graphics.fill(marker);
        }
        if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
            graphics.setColor(color(style.stroke()));
            graphics.draw(marker);
        }
        return marker.getBounds2D();
    }

    private Rectangle2D renderMarkerSymbol(
            Graphics2D graphics,
            Symbol symbol,
            SymbolSnapshot symbolSnapshot,
            Coordinate featureScreen,
            MapScreenBasis basis,
            double inheritedOpacity,
            OptionalDouble outwardBearing) {
        if (symbolSnapshot.role() != SymbolRole.MARKER) {
            throw rendererValueMismatch(symbolSnapshot);
        }
        if (!VectorMarkerSymbol.RENDERER_KEY.equals(symbolSnapshot.rendererKey())) {
            throw rendererNotRegistered(symbolSnapshot);
        }
        if (!(symbol instanceof VectorMarkerSymbol marker)) {
            throw rendererValueMismatch(symbolSnapshot);
        }
        MarkerTransform markerTransform =
                outwardBearing.isPresent()
                        ? SymbolTransforms.markerAtScreenBearing(
                                marker.viewBox(),
                                marker.placement(),
                                featureScreen,
                                basis,
                                outwardBearing.orElseThrow())
                        : SymbolTransforms.marker(
                                marker.viewBox(), marker.placement(), featureScreen, basis);
        Rectangle2D nominal = rectangle(markerTransform.nominalScreenBounds());
        double effectiveOpacity = inheritedOpacity * marker.opacity();
        if (effectiveOpacity == 0.0
                || (marker.fill().alpha() == 0
                        && marker.stroke().map(SymbolStroke::color).orElse(Rgba.TRANSPARENT).alpha()
                                == 0)) {
            return nominal;
        }
        VectorPath path = marker.path();
        AwtRenderCache.VectorLookup vectorLookup = null;
        VectorPath2D.Converted converted;
        if (renderCacheMode.vectorTemplates()
                && activeRenderCacheCollector != null
                && EventQueue.isDispatchThread()) {
            vectorLookup = renderCache.lookupVectorTemplate(path, activeRenderCacheCollector);
            converted = vectorLookup.hit();
            if (converted == null) {
                converted = renderCache.buildVectorTemplate(path, activeRenderCacheCollector);
            }
        } else {
            if (activeRenderCacheCollector != null) {
                renderCache.recordUncachedVectorBuild(path, activeRenderCacheCollector);
            }
            converted = VectorPath2D.convert(path);
        }
        AffineTransform transform =
                new AffineTransform(
                        markerTransform.m00(),
                        markerTransform.m10(),
                        markerTransform.m01(),
                        markerTransform.m11(),
                        markerTransform.m02(),
                        markerTransform.m12());
        Graphics2D childGraphics = (Graphics2D) graphics.create();
        try {
            childGraphics.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) effectiveOpacity));
            if (marker.fill().alpha() > 0) {
                Shape fill = transform.createTransformedShape(converted.fillPath());
                childGraphics.setColor(color(marker.fill()));
                childGraphics.fill(fill);
            }
            if (marker.stroke().isPresent() && marker.stroke().orElseThrow().color().alpha() > 0) {
                SymbolStroke stroke = marker.stroke().orElseThrow();
                double width = SymbolTransforms.screenLength(stroke.width(), basis);
                float floatWidth = (float) width;
                if (!Float.isFinite(floatWidth) || floatWidth <= 0.0f) {
                    throw transformFailure("symbol-screen-stroke-width");
                }
                childGraphics.setStroke(
                        new BasicStroke(floatWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                childGraphics.setColor(color(stroke.color()));
                Shape strokePath = transform.createTransformedShape(converted.strokePath());
                childGraphics.draw(strokePath);
            }
        } finally {
            childGraphics.dispose();
        }
        if (vectorLookup != null) {
            renderCache.completeVectorTemplate(
                    vectorLookup, path, converted, true, activeRenderCacheCollector);
        }
        return nominal;
    }

    Rectangle2D renderMarkerSymbol(
            Graphics2D graphics, Symbol symbol, Coordinate featureScreen, MapScreenBasis basis) {
        Objects.requireNonNull(symbol, "symbol");
        return renderMarkerSymbol(
                graphics,
                symbol,
                new SymbolSnapshot(symbol.role(), symbol.rendererKey()),
                featureScreen,
                basis,
                1.0,
                OptionalDouble.empty());
    }

    private AwtLogicalPaintPresence renderRegisteredLine(
            SolidLineSymbol line, AwtSymbolRenderContext context) {
        Geometry geometry = context.renderGeometry();
        CoordinateSequence sourceCoordinates = sourceLineCoordinates(geometry);
        int component = componentIndex(context);
        int sourceFirst = sourceLineStart(geometry, component);
        int sourceLast = sourceLineEnd(geometry, component);
        CoordinateSequence authoritativeScreen;
        int first;
        int last;
        if (context.screenPlan() == null) {
            authoritativeScreen =
                    toScreen(
                            sourceCoordinates, context.mapToDisplayOperation(), context.viewport());
            first = 0;
            last = authoritativeScreen.size();
        } else {
            authoritativeScreen = context.screenPlan().authoritativeLineCoordinates();
            first = context.screenPlan().authoritativeLineStart(component);
            last = context.screenPlan().authoritativeLineEnd(component);
        }
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(
                        authoritativeScreen, first, last, context.featureId(), component);
        if (bearings.startBearingDegrees().isEmpty() && bearings.endBearingDegrees().isEmpty()) {
            return AwtLogicalPaintPresence.EMPTY;
        }
        double opacity = context.inheritedOpacity() * line.opacity();
        AwtLogicalPaintPresence presence =
                opacity > 0.0 && line.stroke().color().alpha() > 0
                        ? AwtLogicalPaintPresence.PRESENT
                        : AwtLogicalPaintPresence.EMPTY;
        if (context.screenPlan() == null) {
            paintLinePart(
                    context.parentGraphics(),
                    authoritativeScreen,
                    line.stroke(),
                    context.mapScreenBasis(),
                    opacity);
        } else {
            paintPlannedLineGeometry(
                    context.parentGraphics(),
                    context.screenPlan(),
                    component,
                    line.stroke(),
                    context.mapScreenBasis(),
                    opacity);
        }
        if (context.closedRing()) {
            return presence;
        }
        if (line.startMarker().isPresent() && bearings.startBearingDegrees().isPresent()) {
            presence =
                    unionPresence(
                            presence,
                            dispatchEndpoint(
                                            line.startMarker().orElseThrow(),
                                            context,
                                            opacity,
                                            sourceCoordinates.coordinate(sourceFirst),
                                            authoritativeScreen.coordinate(first),
                                            bearings.startBearingDegrees().orElseThrow())
                                    .paintPresence());
        }
        if (line.endMarker().isPresent() && bearings.endBearingDegrees().isPresent()) {
            presence =
                    unionPresence(
                            presence,
                            dispatchEndpoint(
                                            line.endMarker().orElseThrow(),
                                            context,
                                            opacity,
                                            sourceCoordinates.coordinate(sourceLast - 1),
                                            authoritativeScreen.coordinate(last - 1),
                                            bearings.endBearingDegrees().orElseThrow())
                                    .paintPresence());
        }
        return presence;
    }

    private static void paintPlannedLineGeometry(
            Graphics2D graphics,
            ScreenRenderPlan plan,
            int sourceComponent,
            SymbolStroke stroke,
            MapScreenBasis basis,
            double opacity) {
        int first = plan.renderComponentOffset(sourceComponent);
        int last = plan.renderComponentOffset(sourceComponent + 1);
        for (int component = first; component < last; component++) {
            paintLinePart(
                    graphics,
                    plan.renderingLineCoordinates(),
                    plan.renderingLineStart(component),
                    plan.renderingLineEnd(component),
                    stroke,
                    basis,
                    opacity);
        }
    }

    private SymbolRenderResult dispatchEndpoint(
            Symbol marker,
            AwtSymbolRenderContext owner,
            double inheritedOpacity,
            Coordinate sourceEndpoint,
            Coordinate screenEndpoint,
            double bearing) {
        return dispatch(
                marker,
                context(
                        owner.parentGraphics(),
                        SymbolRole.MARKER,
                        owner.featureId(),
                        owner.featureGeometry(),
                        new PointGeometry(sourceEndpoint),
                        owner.mapToDisplayOperation(),
                        inheritedOpacity,
                        false,
                        OptionalDouble.of(bearing),
                        Optional.of(screenEndpoint),
                        owner.viewport(),
                        owner.mapScreenBasis()));
    }

    private static void paintLinePart(
            Graphics2D graphics,
            CoordinateSequence part,
            SymbolStroke stroke,
            MapScreenBasis basis,
            double opacity) {
        paintLinePart(graphics, part, 0, part.size(), stroke, basis, opacity);
    }

    private static void paintLinePart(
            Graphics2D graphics,
            CoordinateSequence part,
            int first,
            int last,
            SymbolStroke stroke,
            MapScreenBasis basis,
            double opacity) {
        if (opacity == 0.0 || stroke.color().alpha() == 0) {
            return;
        }
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            child.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
            child.setStroke(screenStroke(stroke, basis));
            child.setColor(color(stroke.color()));
            child.draw(screenPath(part, first, last, false));
        } finally {
            child.dispose();
        }
    }

    private AwtLogicalPaintPresence renderRegisteredFill(
            Symbol symbol, AwtSymbolRenderContext context) {
        Geometry geometry = context.renderGeometry();
        boolean eligible = context.screenPlan() != null;
        Path2D polygonPath =
                eligible
                        ? plannedPolygonPath(context.screenPlan(), componentIndex(context))
                        : screenPolygon(
                                        (PolygonGeometry) geometry,
                                        context.mapToDisplayOperation(),
                                        context.viewport())
                                .path();
        if (symbol instanceof SolidFillSymbol fill) {
            double opacity = context.inheritedOpacity() * fill.opacity();
            AwtLogicalPaintPresence presence =
                    opacity > 0.0 && fill.fill().alpha() > 0
                            ? AwtLogicalPaintPresence.PRESENT
                            : AwtLogicalPaintPresence.EMPTY;
            if (polygonPath != null && opacity > 0.0 && fill.fill().alpha() > 0) {
                Graphics2D child = context.createGraphics();
                try {
                    child.setComposite(
                            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                    child.setColor(color(fill.fill()));
                    child.fill(polygonPath);
                } finally {
                    child.dispose();
                }
            }
            if (polygonPath != null && fill.outline().isPresent()) {
                presence =
                        unionPresence(
                                presence,
                                eligible
                                        ? renderScreenOutline(
                                                fill.outline().orElseThrow(),
                                                context.screenPlan(),
                                                componentIndex(context),
                                                context,
                                                opacity)
                                        : renderRegisteredOutline(
                                                fill.outline().orElseThrow(),
                                                (PolygonGeometry) geometry,
                                                context,
                                                opacity));
            }
            return presence;
        } else if (symbol instanceof HatchFillSymbol hatch) {
            double opacity = context.inheritedOpacity() * hatch.opacity();
            AwtLogicalPaintPresence presence =
                    polygonPath != null
                                    && paintHatch(
                                            context.parentGraphics(),
                                            hatch,
                                            polygonPath,
                                            context.mapScreenBasis(),
                                            context.viewport(),
                                            context.featureId(),
                                            opacity)
                            ? AwtLogicalPaintPresence.PRESENT
                            : AwtLogicalPaintPresence.EMPTY;
            if (polygonPath != null && hatch.outline().isPresent()) {
                presence =
                        unionPresence(
                                presence,
                                eligible
                                        ? renderScreenOutline(
                                                hatch.outline().orElseThrow(),
                                                context.screenPlan(),
                                                componentIndex(context),
                                                context,
                                                opacity)
                                        : renderRegisteredOutline(
                                                hatch.outline().orElseThrow(),
                                                (PolygonGeometry) geometry,
                                                context,
                                                opacity));
            }
            return presence;
        }
        throw new IllegalArgumentException("Unsupported built-in fill symbol");
    }

    private ScreenRenderPlan buildScreenRenderPlan(
            Symbol symbol,
            Geometry sourceGeometry,
            CrsOperation sourceToDisplay,
            MapViewport viewportSnapshot,
            MapScreenBasis basis) {
        if (!screenOptimizationEligible(symbol, sourceGeometry)) {
            return null;
        }
        Geometry authoritativeScreenGeometry =
                toScreenPathGeometry(sourceGeometry, sourceToDisplay, viewportSnapshot);
        double maximumHalfStroke = maximumSymbolHalfStroke(symbol, basis);
        if (!Double.isFinite(maximumHalfStroke) || maximumHalfStroke < 0.0) {
            throw transformFailure("symbol-screen-stroke-width");
        }
        if (screenGeometryOptimizationMode == ScreenGeometryOptimizationMode.DISABLED) {
            recordScreenGeometryWork(
                    authoritativeScreenGeometry,
                    Optional.of(authoritativeScreenGeometry),
                    ScreenGeometryOptimizationOutcome.UNCHANGED,
                    componentCount(authoritativeScreenGeometry),
                    0,
                    0L);
            return new ScreenRenderPlan(authoritativeScreenGeometry);
        }
        double margin = 1.0 + maximumHalfStroke;
        var optimization =
                ScreenGeometryOptimizer.optimize(
                        authoritativeScreenGeometry,
                        new Envelope(
                                -margin,
                                -margin,
                                viewportSnapshot.width() + margin,
                                viewportSnapshot.height() + margin),
                        SCREEN_PATH_TOLERANCE,
                        ScreenGeometryOptimizationLimits.defaults());
        Optional<Geometry> renderingGeometry = optimization.renderingGeometry();
        long retainedBytes =
                renderingGeometry.isPresent()
                                && renderingGeometry.orElseThrow() != authoritativeScreenGeometry
                        ? logicalGeometryBytes(renderingGeometry.orElseThrow())
                        : 0L;
        int culledComponents = 0;
        for (int component = 0; component < optimization.sourceComponentCount(); component++) {
            if (optimization.renderComponentOffset(component)
                    == optimization.renderComponentOffset(component + 1)) {
                culledComponents++;
            }
        }
        recordScreenGeometryWork(
                authoritativeScreenGeometry,
                renderingGeometry,
                optimization.outcome(),
                optimization.renderComponentCount(),
                culledComponents,
                retainedBytes);
        return new ScreenRenderPlan(optimization);
    }

    private Geometry toScreenPathGeometry(
            Geometry geometry, CrsOperation operation, MapViewport viewportSnapshot) {
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(
                    toScreen(line.coordinates(), operation, viewportSnapshot));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    toScreen(lines.coordinates(), operation, viewportSnapshot),
                    lines.partOffsets());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            return polygonGeometry(screenPolygon(polygon, operation, viewportSnapshot));
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        return MultiPolygonGeometry.of(
                toScreen(polygons.coordinates(), operation, viewportSnapshot),
                polygons.ringOffsets(),
                polygons.polygonRingOffsets());
    }

    private static boolean screenOptimizationEligible(Symbol symbol, Geometry geometry) {
        boolean pathGeometry =
                geometry instanceof LineStringGeometry
                        || geometry instanceof MultiLineStringGeometry
                        || geometry instanceof PolygonGeometry
                        || geometry instanceof MultiPolygonGeometry;
        if (!pathGeometry) {
            return false;
        }
        if (symbol instanceof FeatureStyle) {
            return true;
        }
        return symbol.role() == SymbolRole.LINE
                ? isBuiltInLineTree(symbol)
                : symbol.role() == SymbolRole.FILL && isBuiltInFillTree(symbol);
    }

    private static boolean isBuiltInFillTree(Symbol symbol) {
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                if (!isBuiltInFillTree(child)) {
                    return false;
                }
            }
            return true;
        }
        if (symbol instanceof SolidFillSymbol || symbol instanceof HatchFillSymbol) {
            return fillOutline(symbol).map(MapView::isBuiltInLineTree).orElse(true);
        }
        return false;
    }

    private static double maximumSymbolHalfStroke(Symbol symbol, MapScreenBasis basis) {
        if (symbol instanceof FeatureStyle style) {
            return style.stroke().alpha() > 0 && style.strokeWidth() > 0.0
                    ? style.strokeWidth() / 2.0
                    : 0.0;
        }
        if (symbol.role() == SymbolRole.LINE) {
            return maximumLineHalfStroke(symbol, basis);
        }
        if (symbol instanceof CompositeSymbol composite) {
            double result = 0.0;
            if (composite.opacity() > 0.0) {
                for (Symbol child : composite.children()) {
                    result = Math.max(result, maximumSymbolHalfStroke(child, basis));
                }
            }
            return result;
        }
        return maximumFillHalfStroke(symbol, basis);
    }

    ScreenGeometryPaintResult paintWithScreenGeometryResult(Graphics2D graphics) {
        Objects.requireNonNull(graphics, "graphics");
        if (activeScreenGeometryPaintCollector != null) {
            throw new IllegalStateException("Screen geometry evidence paint is already active");
        }
        if (activeRenderCacheCollector != null) {
            throw new IllegalStateException("Render-cache evidence paint is already active");
        }
        ScreenGeometryPaintCollector collector = new ScreenGeometryPaintCollector();
        AwtRenderCache.CacheEventCollector cacheCollector =
                new AwtRenderCache.CacheEventCollector();
        activeScreenGeometryPaintCollector = collector;
        activeRenderCacheCollector = cacheCollector;
        try {
            paint(graphics);
            return collector.result(cacheCollector.result());
        } finally {
            activeScreenGeometryPaintCollector = null;
            activeRenderCacheCollector = null;
        }
    }

    void clearVectorTemplateCacheForEvidence() {
        renderCache.clearVectorTemplates();
    }

    private void recordScreenGeometryWork(
            Geometry authoritative,
            Optional<Geometry> rendering,
            ScreenGeometryOptimizationOutcome outcome,
            int renderComponents,
            int culledComponents,
            long retainedBytes) {
        if (activeScreenGeometryPaintCollector != null) {
            activeScreenGeometryPaintCollector.add(
                    coordinateCount(authoritative),
                    rendering.map(MapView::coordinateCount).orElse(0),
                    authoritative instanceof LineStringGeometry
                                    || authoritative instanceof MultiLineStringGeometry
                            ? renderComponents
                            : 0,
                    culledComponents,
                    outcome == ScreenGeometryOptimizationOutcome.FALLBACK ? 1 : 0,
                    retainedBytes);
        }
    }

    private static int coordinateCount(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return 1;
        }
        if (geometry instanceof MultiPointGeometry points) {
            return points.coordinates().size();
        }
        if (geometry instanceof LineStringGeometry line) {
            return line.coordinates().size();
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof PolygonGeometry polygon) {
            int result = polygon.exterior().size();
            for (CoordinateSequence hole : polygon.holes()) {
                result = Math.addExact(result, hole.size());
            }
            return result;
        }
        return ((MultiPolygonGeometry) geometry).coordinates().size();
    }

    private static int componentCount(Geometry geometry) {
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.partCount();
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.polygonCount();
        }
        return 1;
    }

    private static int componentIndex(AwtSymbolRenderContext context) {
        return context.sourceComponent() < 0 ? 0 : context.sourceComponent();
    }

    private static CoordinateSequence sourceLineCoordinates(Geometry geometry) {
        return geometry instanceof LineStringGeometry line
                ? line.coordinates()
                : ((MultiLineStringGeometry) geometry).coordinates();
    }

    private static int sourceLineStart(Geometry geometry, int component) {
        return geometry instanceof LineStringGeometry
                ? 0
                : ((MultiLineStringGeometry) geometry).partOffset(component);
    }

    private static int sourceLineEnd(Geometry geometry, int component) {
        return geometry instanceof LineStringGeometry line
                ? line.coordinates().size()
                : ((MultiLineStringGeometry) geometry).partOffset(component + 1);
    }

    private static long logicalGeometryBytes(Geometry geometry) {
        long result = Math.multiplyExact((long) coordinateCount(geometry), 16L);
        if (geometry instanceof MultiLineStringGeometry lines) {
            result = Math.addExact(result, Math.multiplyExact((long) lines.partCount() + 1L, 4L));
        } else if (geometry instanceof PolygonGeometry polygon) {
            result =
                    Math.addExact(
                            result, Math.multiplyExact((long) polygon.holes().size() + 2L, 4L));
        } else if (geometry instanceof MultiPolygonGeometry polygons) {
            result =
                    Math.addExact(result, Math.multiplyExact((long) polygons.ringCount() + 1L, 4L));
            result =
                    Math.addExact(
                            result, Math.multiplyExact((long) polygons.polygonCount() + 1L, 4L));
        }
        return result;
    }

    private static final class ScreenGeometryPaintCollector {
        private long inputCoordinates;
        private long renderCoordinates;
        private long lineFragments;
        private long culledPaths;
        private long fallbackPlans;
        private long retainedRenderGeometryBytes;

        void add(
                long input,
                long rendered,
                long fragments,
                long culled,
                long fallbacks,
                long retainedBytes) {
            inputCoordinates = Math.addExact(inputCoordinates, input);
            renderCoordinates = Math.addExact(renderCoordinates, rendered);
            lineFragments = Math.addExact(lineFragments, fragments);
            culledPaths = Math.addExact(culledPaths, culled);
            fallbackPlans = Math.addExact(fallbackPlans, fallbacks);
            retainedRenderGeometryBytes = Math.addExact(retainedRenderGeometryBytes, retainedBytes);
        }

        ScreenGeometryPaintResult result(RenderCachePaintMetrics cacheMetrics) {
            return new ScreenGeometryPaintResult(
                    inputCoordinates,
                    inputCoordinates,
                    renderCoordinates,
                    lineFragments,
                    culledPaths,
                    fallbackPlans,
                    retainedRenderGeometryBytes,
                    cacheMetrics);
        }
    }

    private static double maximumFillHalfStroke(Symbol symbol, MapScreenBasis basis) {
        double symbolOpacity =
                symbol instanceof SolidFillSymbol fill
                        ? fill.opacity()
                        : ((HatchFillSymbol) symbol).opacity();
        if (symbolOpacity == 0.0) {
            return 0.0;
        }
        double result = 0.0;
        if (symbol instanceof HatchFillSymbol hatch
                && hatch.opacity() > 0.0
                && hatch.stroke().color().alpha() > 0) {
            result = SymbolTransforms.screenLength(hatch.stroke().width(), basis) / 2.0;
        }
        Optional<Symbol> outline = fillOutline(symbol);
        return Math.max(
                result, outline.map(value -> maximumLineHalfStroke(value, basis)).orElse(0.0));
    }

    private static Optional<Symbol> fillOutline(Symbol symbol) {
        return symbol instanceof SolidFillSymbol fill
                ? fill.outline()
                : ((HatchFillSymbol) symbol).outline();
    }

    private static boolean isBuiltInLineTree(Symbol symbol) {
        if (symbol instanceof SolidLineSymbol) {
            return true;
        }
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                if (!isBuiltInLineTree(child)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static double maximumLineHalfStroke(Symbol symbol, MapScreenBasis basis) {
        if (symbol instanceof SolidLineSymbol line) {
            return line.opacity() > 0.0 && line.stroke().color().alpha() > 0
                    ? SymbolTransforms.screenLength(line.stroke().width(), basis) / 2.0
                    : 0.0;
        }
        if (symbol instanceof CompositeSymbol composite) {
            double result = 0.0;
            if (composite.opacity() > 0.0) {
                for (Symbol child : composite.children()) {
                    result = Math.max(result, maximumLineHalfStroke(child, basis));
                }
            }
            return result;
        }
        return 0.0;
    }

    private AwtLogicalPaintPresence renderRegisteredOutline(
            Symbol outline,
            PolygonGeometry polygon,
            AwtSymbolRenderContext owner,
            double inheritedOpacity) {
        List<CoordinateSequence> rings = new ArrayList<>();
        rings.add(polygon.exterior());
        rings.addAll(polygon.holes());
        AwtLogicalPaintPresence presence = AwtLogicalPaintPresence.EMPTY;
        for (CoordinateSequence ring : rings) {
            presence =
                    unionPresence(
                            presence,
                            dispatch(
                                            outline,
                                            context(
                                                    owner.parentGraphics(),
                                                    SymbolRole.LINE,
                                                    owner.featureId(),
                                                    owner.featureGeometry(),
                                                    new LineStringGeometry(ring),
                                                    owner.mapToDisplayOperation(),
                                                    inheritedOpacity,
                                                    true,
                                                    OptionalDouble.empty(),
                                                    Optional.empty(),
                                                    owner.viewport(),
                                                    owner.mapScreenBasis()))
                                    .paintPresence());
        }
        return presence;
    }

    private static AwtLogicalPaintPresence renderScreenOutline(
            Symbol outline,
            ScreenRenderPlan plan,
            int sourceComponent,
            AwtSymbolRenderContext context,
            double inheritedOpacity) {
        if (outline instanceof CompositeSymbol composite) {
            AwtLogicalPaintPresence result = AwtLogicalPaintPresence.EMPTY;
            for (Symbol child : composite.children()) {
                result =
                        unionPresence(
                                result,
                                renderScreenOutline(
                                        child,
                                        plan,
                                        sourceComponent,
                                        context,
                                        inheritedOpacity * composite.opacity()));
            }
            return result;
        }
        SolidLineSymbol line = (SolidLineSymbol) outline;
        double opacity = inheritedOpacity * line.opacity();
        AwtLogicalPaintPresence presence =
                opacity > 0.0 && line.stroke().color().alpha() > 0
                        ? AwtLogicalPaintPresence.PRESENT
                        : AwtLogicalPaintPresence.EMPTY;
        int rings = plan.renderingRingCount(sourceComponent);
        for (int ring = 0; ring < rings; ring++) {
            paintLinePart(
                    context.parentGraphics(),
                    plan.renderingRingCoordinates(sourceComponent, ring),
                    plan.renderingRingStart(sourceComponent, ring),
                    plan.renderingRingEnd(sourceComponent, ring),
                    line.stroke(),
                    context.mapScreenBasis(),
                    opacity);
        }
        return presence;
    }

    private boolean paintHatch(
            Graphics2D graphics,
            HatchFillSymbol hatch,
            Path2D polygon,
            MapScreenBasis basis,
            MapViewport viewportSnapshot,
            String featureId,
            double opacity) {
        if (opacity == 0.0 || hatch.stroke().color().alpha() == 0) {
            return false;
        }
        Rectangle2D work =
                polygon.getBounds2D()
                        .createIntersection(
                                new Rectangle2D.Double(0.0, 0.0, getWidth(), getHeight()));
        if (graphics.getClip() != null) {
            work = work.createIntersection(graphics.getClip().getBounds2D());
        }
        if (work.isEmpty()) {
            return false;
        }
        Envelope bounds =
                new Envelope(work.getMinX(), work.getMinY(), work.getMaxX(), work.getMaxY());
        boolean mapRelative =
                hatch.rotationMode() == io.github.mundanej.map.api.SymbolRotationMode.MAP_RELATIVE;
        Coordinate origin =
                mapRelative
                        ? viewportSnapshot.worldToScreen(new Coordinate(0.0, 0.0))
                        : new Coordinate(0.0, 0.0);
        double bearing = mapRelative ? basis.xAxisScreenBearingDegrees() : 0.0;
        double spacing = SymbolTransforms.screenLength(hatch.spacing(), basis);
        HatchSegments segments =
                HatchLayouts.cover(
                        hatch.pattern(),
                        bounds,
                        origin,
                        bearing,
                        spacing,
                        hatch.maxSegments(),
                        featureId);
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            child.clip(polygon);
            child.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
            child.setStroke(screenStroke(hatch.stroke(), basis));
            child.setColor(color(hatch.stroke().color()));
            Path2D lines = new Path2D.Double();
            for (int index = 0; index < segments.segmentCount(); index++) {
                lines.moveTo(segments.x1(index), segments.y1(index));
                lines.lineTo(segments.x2(index), segments.y2(index));
            }
            child.draw(lines);
        } finally {
            child.dispose();
        }
        return segments.segmentCount() > 0;
    }

    private static AwtLogicalPaintPresence unionPresence(
            AwtLogicalPaintPresence first, AwtLogicalPaintPresence second) {
        if (first == AwtLogicalPaintPresence.PRESENT || second == AwtLogicalPaintPresence.PRESENT) {
            return AwtLogicalPaintPresence.PRESENT;
        }
        if (first == AwtLogicalPaintPresence.UNKNOWN || second == AwtLogicalPaintPresence.UNKNOWN) {
            return AwtLogicalPaintPresence.UNKNOWN;
        }
        return AwtLogicalPaintPresence.EMPTY;
    }

    private static void renderPointLabel(
            Graphics2D graphics, String featureName, Rectangle2D nominalBounds) {
        if (featureName.isBlank()) {
            return;
        }
        graphics.setColor(new Color(32, 32, 32));
        Point2D baseline = pointLabelBaseline(nominalBounds);
        graphics.drawString(featureName, (float) baseline.getX(), (float) baseline.getY());
    }

    static Point2D pointLabelBaseline(Rectangle2D nominalBounds) {
        Objects.requireNonNull(nominalBounds, "nominalBounds");
        return new Point2D.Double(nominalBounds.getMaxX() + 4.0, nominalBounds.getMinY() - 2.0);
    }

    private CoordinateSequence toScreen(
            CoordinateSequence source,
            CrsOperation sourceToDisplayOperation,
            MapViewport viewportSnapshot) {
        double[] ordinates = new double[source.size() * 2];
        for (int index = 0; index < source.size(); index++) {
            Coordinate screen =
                    toScreen(source.coordinate(index), sourceToDisplayOperation, viewportSnapshot);
            ordinates[index * 2] = screen.x();
            ordinates[index * 2 + 1] = screen.y();
        }
        return CoordinateSequence.of(ordinates);
    }

    private ScreenPolygon screenPolygon(
            PolygonGeometry polygon,
            CrsOperation sourceToDisplayOperation,
            MapViewport viewportSnapshot) {
        List<CoordinateSequence> rings = new ArrayList<>();
        rings.add(toScreen(polygon.exterior(), sourceToDisplayOperation, viewportSnapshot));
        for (CoordinateSequence hole : polygon.holes()) {
            rings.add(toScreen(hole, sourceToDisplayOperation, viewportSnapshot));
        }
        Path2D screenPath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (CoordinateSequence ring : rings) {
            appendScreen(screenPath, ring, true);
        }
        return new ScreenPolygon(screenPath, List.copyOf(rings));
    }

    private static PolygonGeometry polygonGeometry(ScreenPolygon polygon) {
        return new PolygonGeometry(
                polygon.rings().getFirst(), polygon.rings().subList(1, polygon.rings().size()));
    }

    private static MapScreenBasis screenBasis(MapViewport viewportSnapshot) {
        double screenPixelsPerMapUnit = 1.0 / viewportSnapshot.worldUnitsPerPixel();
        return MapScreenBasis.of(
                new Coordinate(screenPixelsPerMapUnit, 0.0),
                new Coordinate(0.0, -screenPixelsPerMapUnit));
    }

    private static Path2D screenPath(CoordinateSequence coordinates, boolean close) {
        return screenPath(coordinates, 0, coordinates.size(), close);
    }

    private static Path2D screenPath(
            CoordinateSequence coordinates, int first, int last, boolean close) {
        Path2D result = new Path2D.Double();
        appendScreen(result, coordinates, first, last, close);
        return result;
    }

    private static void appendScreen(Path2D path, CoordinateSequence coordinates, boolean close) {
        appendScreen(path, coordinates, 0, coordinates.size(), close);
    }

    private static void appendScreen(
            Path2D path, CoordinateSequence coordinates, int first, int last, boolean close) {
        path.moveTo(coordinates.x(first), coordinates.y(first));
        for (int index = first + 1; index < last; index++) {
            path.lineTo(coordinates.x(index), coordinates.y(index));
        }
        if (close) {
            path.closePath();
        }
    }

    private static Path2D plannedPolygonPath(ScreenRenderPlan plan, int sourceComponent) {
        int rings = plan.renderingRingCount(sourceComponent);
        if (rings == 0) {
            return null;
        }
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (int ring = 0; ring < rings; ring++) {
            appendScreen(
                    path,
                    plan.renderingRingCoordinates(sourceComponent, ring),
                    plan.renderingRingStart(sourceComponent, ring),
                    plan.renderingRingEnd(sourceComponent, ring),
                    true);
        }
        return path;
    }

    Coordinate toScreen(Coordinate source, MapViewport viewportSnapshot) {
        return toScreen(source, mapToDisplay, viewportSnapshot);
    }

    Coordinate toScreen(
            Coordinate source,
            CrsOperation sourceToDisplayOperation,
            MapViewport viewportSnapshot) {
        return viewportSnapshot.worldToScreen(sourceToDisplayOperation.transform(source));
    }

    private static Color color(Rgba color) {
        return new Color(color.red(), color.green(), color.blue(), color.alpha());
    }

    private static BasicStroke screenStroke(SymbolStroke stroke, MapScreenBasis basis) {
        double width = SymbolTransforms.screenLength(stroke.width(), basis);
        float floatWidth = (float) width;
        if (!Float.isFinite(floatWidth) || floatWidth <= 0.0f) {
            throw transformFailure("symbol-screen-stroke-width");
        }
        return new BasicStroke(floatWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private static Rectangle2D rectangle(Envelope bounds) {
        return new Rectangle2D.Double(
                bounds.minX(), bounds.minY(), bounds.width(), bounds.height());
    }

    private static Envelope envelope(Rectangle2D bounds) {
        return new Envelope(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
    }

    private static SymbolRole geometryRole(Geometry geometry) {
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            return SymbolRole.MARKER;
        }
        if (geometry instanceof LineStringGeometry || geometry instanceof MultiLineStringGeometry) {
            return SymbolRole.LINE;
        }
        if (geometry instanceof PolygonGeometry || geometry instanceof MultiPolygonGeometry) {
            return SymbolRole.FILL;
        }
        return null;
    }

    private static SymbolException transformFailure(String quantity) {
        return new SymbolException(
                SymbolException.TRANSFORM_NON_FINITE,
                "Symbol transform produced a non-finite value",
                Map.of("quantity", quantity));
    }

    private static SymbolException roleMismatch(
            VisualFeature feature, SymbolSnapshot symbolSnapshot) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("featureId", feature.id());
        context.put("geometryKind", geometryKind(feature.geometry()));
        context.put("symbolRole", roleText(symbolSnapshot.role()));
        return new SymbolException(
                SymbolException.ROLE_MISMATCH,
                "Symbol role does not match feature geometry",
                context);
    }

    private static SymbolException rendererNotRegistered(SymbolSnapshot symbolSnapshot) {
        return rendererFailure(
                SymbolException.RENDERER_NOT_REGISTERED,
                "No renderer is registered for symbol",
                symbolSnapshot);
    }

    private static SymbolException rendererValueMismatch(SymbolSnapshot symbolSnapshot) {
        return rendererFailure(
                SymbolException.RENDERER_VALUE_MISMATCH,
                "Renderer does not support symbol value",
                symbolSnapshot);
    }

    private static SymbolException rendererFailure(
            String code, String message, SymbolSnapshot symbolSnapshot) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("role", roleText(symbolSnapshot.role()));
        context.put("key", keyText(symbolSnapshot.rendererKey()));
        return new SymbolException(code, message, context);
    }

    private static String geometryKind(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return "POINT";
        }
        if (geometry instanceof LineStringGeometry) {
            return "LINE_STRING";
        }
        if (geometry instanceof PolygonGeometry) {
            return "POLYGON";
        }
        if (geometry instanceof MultiPointGeometry) {
            return "MULTI_POINT";
        }
        if (geometry instanceof MultiLineStringGeometry) {
            return "MULTI_LINE_STRING";
        }
        if (geometry instanceof MultiPolygonGeometry) {
            return "MULTI_POLYGON";
        }
        return "UNSUPPORTED";
    }

    private static String roleText(SymbolRole role) {
        return role == null ? "null" : role.name();
    }

    private static String keyText(SymbolRendererKey key) {
        return key == null ? "null" : key.value();
    }

    private record SymbolSnapshot(SymbolRole role, SymbolRendererKey rendererKey) {}

    private record ScreenPolygon(Path2D path, List<CoordinateSequence> rings) {}

    private void installInteraction() {
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent event) {
                        requestFocusInWindow();
                        runThenClearHover(
                                () -> {
                                    RoutedMouse routed = routeMouse(event, MapToolEvent.Type.PRESS);
                                    if (!routed.outcome().suppressDefault()
                                            && button(event.getButton())
                                                    .equals(MapPointerButton.PRIMARY)
                                            && buttonsDown(event, MapToolEvent.Type.PRESS)
                                                    .equals(Set.of(MapPointerButton.PRIMARY))) {
                                        dragAnchor = event.getPoint();
                                    } else {
                                        dragAnchor = null;
                                    }
                                });
                    }

                    @Override
                    public void mouseReleased(MouseEvent event) {
                        runThenClearHover(
                                () -> {
                                    try {
                                        routeMouse(event, MapToolEvent.Type.RELEASE);
                                    } finally {
                                        if (button(event.getButton())
                                                .equals(MapPointerButton.PRIMARY)) {
                                            dragAnchor = null;
                                        }
                                    }
                                });
                    }

                    @Override
                    public void mouseClicked(MouseEvent event) {
                        RoutedMouse routed;
                        try {
                            routed = routeMouse(event, MapToolEvent.Type.CLICK);
                        } catch (RuntimeException | Error failure) {
                            clearHoverSuppressing(failure);
                            throw failure;
                        }
                        if (routed.outcome().suppressDefault()) {
                            clearHover();
                            return;
                        }
                        try {
                            updateClickInteraction(event);
                            firePointer(
                                    MapPointerEvent.Type.CLICKED,
                                    event.getX(),
                                    event.getY(),
                                    routed.event().mapCoordinate());
                        } catch (RuntimeException | Error failure) {
                            clearHoverSuppressing(failure);
                            throw failure;
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent event) {
                        rememberPointer(event, buttonsDown(event, null));
                        if (isEnabled() && isDisplayable()) {
                            resumeTool();
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent event) {
                        Set<MapPointerButton> down = buttonsDown(event, null);
                        rememberPointer(event, down);
                        runThenClearHover(
                                () -> {
                                    try {
                                        if (!toolRouter.captured()) {
                                            cancelInteraction(
                                                    MapToolCancelReason.POINTER_EXITED, down);
                                        }
                                    } finally {
                                        dragAnchor = null;
                                    }
                                });
                    }
                });
        addMouseMotionListener(
                new MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(MouseEvent event) {
                        runThenClearHover(
                                () -> {
                                    Set<MapPointerButton> down =
                                            buttonsDown(event, MapToolEvent.Type.DRAG);
                                    if (down.isEmpty()) {
                                        rememberPointer(event, down);
                                        try {
                                            cancelInteraction(
                                                    MapToolCancelReason.POINTER_STATE_LOST, down);
                                        } finally {
                                            dragAnchor = null;
                                        }
                                        return;
                                    }
                                    RouteOutcome outcome =
                                            routeMouse(event, MapToolEvent.Type.DRAG).outcome();
                                    boolean solePrimary =
                                            down.equals(Set.of(MapPointerButton.PRIMARY));
                                    if (dragAnchor != null && solePrimary) {
                                        double deltaX = event.getX() - dragAnchor.x;
                                        double deltaY = event.getY() - dragAnchor.y;
                                        dragAnchor = event.getPoint();
                                        if (!outcome.suppressDefault()) {
                                            viewport = viewport().panByPixels(deltaX, deltaY);
                                            repaint();
                                        }
                                    } else if (!solePrimary) {
                                        dragAnchor = null;
                                    }
                                });
                    }

                    @Override
                    public void mouseMoved(MouseEvent event) {
                        Set<MapPointerButton> down = buttonsDown(event, MapToolEvent.Type.MOVE);
                        if (!down.isEmpty()) {
                            rememberPointer(event, down);
                            runThenClearHover(
                                    () -> {
                                        try {
                                            cancelInteraction(
                                                    MapToolCancelReason.POINTER_STATE_LOST, down);
                                        } finally {
                                            dragAnchor = null;
                                        }
                                    });
                            return;
                        }
                        RoutedMouse routed;
                        try {
                            routed = routeMouse(event, MapToolEvent.Type.MOVE);
                        } catch (RuntimeException | Error failure) {
                            clearHoverSuppressing(failure);
                            throw failure;
                        }
                        if (!routed.outcome().suppressDefault()) {
                            updateHoverFromMove(event);
                            firePointer(
                                    MapPointerEvent.Type.MOVED,
                                    event.getX(),
                                    event.getY(),
                                    routed.event().mapCoordinate());
                        } else {
                            clearHover();
                        }
                    }
                });
        addMouseWheelListener(
                event ->
                        runThenClearHover(
                                () -> {
                                    RouteOutcome outcome = routeWheel(event);
                                    if (!outcome.suppressDefault()) {
                                        zoom(event);
                                    }
                                }));
        addFocusListener(
                new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent event) {
                        try {
                            cancelInteraction(MapToolCancelReason.FOCUS_LOST, lastButtonsDown);
                        } finally {
                            dragAnchor = null;
                        }
                    }

                    @Override
                    public void focusGained(FocusEvent event) {
                        if (isEnabled() && isDisplayable()) {
                            resumeTool();
                        }
                    }
                });
        addPropertyChangeListener(
                "enabled",
                event -> {
                    if (Boolean.FALSE.equals(event.getNewValue())) {
                        try {
                            cancelInteraction(MapToolCancelReason.VIEW_DISABLED, lastButtonsDown);
                        } finally {
                            dragAnchor = null;
                        }
                    } else if (isDisplayable()) {
                        resumeTool();
                    }
                });
    }

    private void zoom(MouseWheelEvent event) {
        double factor = Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation());
        viewport = viewport().zoomAt(event.getX(), event.getY(), factor);
        repaint();
    }

    private void updateHoverFromMove(MouseEvent event) {
        synchronizeViewportSize();
        Dimension size = effectiveSize();
        ViewContentSnapshot content = captureContentAtBoundary(bindings, viewport);
        MapViewport viewportSnapshot = viewport;
        Optional<FeatureSelection> reconciledSelection = reconcile(selection, content);
        Optional<MapHit> nextHover = Optional.empty();
        if (event.getX() >= 0
                && event.getX() < size.width
                && event.getY() >= 0
                && event.getY() < size.height) {
            nextHover =
                    hitTestSnapshot(
                                    content,
                                    viewportSnapshot,
                                    size,
                                    event.getX(),
                                    event.getY(),
                                    DEFAULT_HOVER_TOLERANCE_PIXELS)
                            .topmost();
        }
        hoverProbe =
                content.allSourcesAvailable()
                        ? Optional.of(new HoverProbe(event.getX(), event.getY()))
                        : Optional.empty();
        transitionInteraction(reconciledSelection, nextHover, true);
        drainSourceReportNotifications();
    }

    private void updateClickInteraction(MouseEvent event) {
        synchronizeViewportSize();
        Dimension size = effectiveSize();
        ViewContentSnapshot content = captureContentAtBoundary(bindings, viewport);
        Optional<FeatureSelection> nextSelection = reconcile(selection, content);
        if (button(event.getButton()).equals(MapPointerButton.PRIMARY)
                && event.getClickCount() == 1
                && !event.isPopupTrigger()
                && modifiers(event).isEmpty()
                && event.getX() >= 0
                && event.getX() < size.width
                && event.getY() >= 0
                && event.getY() < size.height) {
            nextSelection =
                    hitTestSnapshot(
                                    content,
                                    viewport,
                                    size,
                                    event.getX(),
                                    event.getY(),
                                    DEFAULT_SELECTION_TOLERANCE_PIXELS)
                            .topmost()
                            .map(hit -> new FeatureSelection(hit.layerId(), hit.featureId()));
        }
        hoverProbe = Optional.empty();
        transitionInteraction(nextSelection, Optional.empty(), true);
        drainSourceReportNotifications();
    }

    private RoutedMouse routeMouse(MouseEvent event, MapToolEvent.Type type) {
        Set<MapPointerButton> down = buttonsDown(event, type);
        rememberPointer(event, down);
        ToolContextSnapshot context = toolContextSnapshot();
        MapToolEvent converted =
                new MapToolEvent(
                        nextToolSequence(),
                        type,
                        event.getX(),
                        event.getY(),
                        context.screenToMap(event.getX(), event.getY()),
                        changedButton(event, type),
                        down,
                        modifiers(event),
                        (type == MapToolEvent.Type.PRESS
                                        || type == MapToolEvent.Type.RELEASE
                                        || type == MapToolEvent.Type.CLICK)
                                ? event.getClickCount()
                                : 0,
                        0.0,
                        (type == MapToolEvent.Type.PRESS
                                        || type == MapToolEvent.Type.RELEASE
                                        || type == MapToolEvent.Type.CLICK)
                                ? event.isPopupTrigger()
                                : false,
                        Optional.empty());
        try {
            RouteOutcome outcome = callRouter(() -> toolRouter.route(converted, context));
            applyToolOutcome(outcome);
            return new RoutedMouse(outcome, converted);
        } catch (RuntimeException | Error failure) {
            dragAnchor = null;
            applyCurrentToolCursor();
            throw failure;
        }
    }

    private RouteOutcome routeWheel(MouseWheelEvent event) {
        Set<MapPointerButton> down = buttonsDown(event, MapToolEvent.Type.WHEEL);
        rememberPointer(event, down);
        ToolContextSnapshot context = toolContextSnapshot();
        MapToolEvent converted =
                new MapToolEvent(
                        nextToolSequence(),
                        MapToolEvent.Type.WHEEL,
                        event.getX(),
                        event.getY(),
                        context.screenToMap(event.getX(), event.getY()),
                        MapPointerButton.NONE,
                        down,
                        modifiers(event),
                        0,
                        event.getPreciseWheelRotation(),
                        false,
                        Optional.empty());
        try {
            RouteOutcome outcome = callRouter(() -> toolRouter.route(converted, context));
            applyToolOutcome(outcome);
            return outcome;
        } catch (RuntimeException | Error failure) {
            dragAnchor = null;
            applyCurrentToolCursor();
            throw failure;
        }
    }

    private void cancelInteraction(MapToolCancelReason reason, Set<MapPointerButton> buttonsDown) {
        ToolContextSnapshot context = toolContextSnapshot();
        MapToolEvent cancel =
                new MapToolEvent(
                        nextToolSequence(),
                        MapToolEvent.Type.CANCEL,
                        lastPointerX,
                        lastPointerY,
                        context.screenToMap(lastPointerX, lastPointerY),
                        MapPointerButton.NONE,
                        buttonsDown,
                        lastModifiers,
                        0,
                        0.0,
                        false,
                        Optional.of(reason));
        try {
            applyToolOutcome(callRouter(() -> toolRouter.cancelInteraction(cancel, context)));
        } catch (RuntimeException | Error failure) {
            applyCurrentToolCursor();
            throw failure;
        }
    }

    private MapToolEvent cancelEvent(MapToolCancelReason reason, ToolContextSnapshot context) {
        return new MapToolEvent(
                nextToolSequence(),
                MapToolEvent.Type.CANCEL,
                lastPointerX,
                lastPointerY,
                context.screenToMap(lastPointerX, lastPointerY),
                MapPointerButton.NONE,
                lastButtonsDown,
                lastModifiers,
                0,
                0.0,
                false,
                Optional.of(reason));
    }

    private void firePointer(
            MapPointerEvent.Type type,
            double screenX,
            double screenY,
            Optional<Coordinate> mapCoordinate) {
        MapPointerEvent event = new MapPointerEvent(type, screenX, screenY, mapCoordinate);
        List<MapPointerListener> snapshot = List.copyOf(pointerListeners);
        for (MapPointerListener listener : snapshot) {
            listener.onMapPointerEvent(event);
        }
    }

    private ToolContextSnapshot toolContextSnapshot() {
        synchronizeViewportSize();
        return new ToolContextSnapshot(viewport);
    }

    private void applyToolOutcome(RouteOutcome outcome) {
        setCursor(isEnabled() ? cursor(outcome.cursorIntent()) : Cursor.getDefaultCursor());
    }

    private <T> T callRouter(Supplier<T> operation) {
        routerCallDepth++;
        try {
            return operation.get();
        } finally {
            routerCallDepth--;
            if (routerCallDepth == 0) {
                reconcileMeasurementClaims();
            }
        }
    }

    private void reconcileMeasurementClaims() {
        MapTool active = toolRouter.activeTool().orElse(null);
        var iterator = measurementClaims.iterator();
        while (iterator.hasNext()) {
            MeasurementTool measurement = iterator.next();
            if (measurement != active) {
                measurement.release(this);
                iterator.remove();
            }
        }
    }

    private void resumeTool() {
        try {
            applyToolOutcome(toolRouter.resume());
        } catch (RuntimeException | Error failure) {
            applyCurrentToolCursor();
            throw failure;
        }
    }

    private void applyCurrentToolCursor() {
        setCursor(
                isEnabled() ? cursor(toolRouter.currentCursorIntent()) : Cursor.getDefaultCursor());
    }

    private static Cursor cursor(MapCursorIntent intent) {
        return switch (intent) {
            case DEFAULT -> Cursor.getDefaultCursor();
            case CROSSHAIR -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            case HAND -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            case MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        };
    }

    private long nextToolSequence() {
        if (toolEventSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Map-tool event sequence exhausted");
        }
        return ++toolEventSequence;
    }

    private void rememberPointer(MouseEvent event, Set<MapPointerButton> buttonsDown) {
        lastPointerX = event.getX();
        lastPointerY = event.getY();
        lastButtonsDown = Set.copyOf(buttonsDown);
        lastModifiers = modifiers(event);
    }

    private static MapPointerButton changedButton(MouseEvent event, MapToolEvent.Type type) {
        return switch (type) {
            case PRESS, RELEASE, CLICK -> button(event.getButton());
            default -> MapPointerButton.NONE;
        };
    }

    private static MapPointerButton button(int number) {
        return number == 0 ? MapPointerButton.NONE : new MapPointerButton(number);
    }

    private static Set<MapPointerButton> buttonsDown(MouseEvent event, MapToolEvent.Type type) {
        Set<MapPointerButton> buttons = new HashSet<>();
        int modifiers = event.getModifiersEx();
        for (int number = 1; number <= 20; number++) {
            if ((modifiers & InputEvent.getMaskForButton(number)) != 0) {
                buttons.add(new MapPointerButton(number));
            }
        }
        if (type == MapToolEvent.Type.PRESS && event.getButton() > 0) {
            buttons.add(button(event.getButton()));
        }
        if ((type == MapToolEvent.Type.RELEASE || type == MapToolEvent.Type.CLICK)
                && event.getButton() > 0) {
            buttons.remove(button(event.getButton()));
        }
        return Set.copyOf(buttons);
    }

    private static Set<MapInputModifier> modifiers(InputEvent event) {
        EnumSet<MapInputModifier> modifiers = EnumSet.noneOf(MapInputModifier.class);
        int mask = event.getModifiersEx();
        if ((mask & InputEvent.SHIFT_DOWN_MASK) != 0) {
            modifiers.add(MapInputModifier.SHIFT);
        }
        if ((mask & InputEvent.CTRL_DOWN_MASK) != 0) {
            modifiers.add(MapInputModifier.CONTROL);
        }
        if ((mask & InputEvent.ALT_DOWN_MASK) != 0) {
            modifiers.add(MapInputModifier.ALT);
        }
        if ((mask & InputEvent.META_DOWN_MASK) != 0) {
            modifiers.add(MapInputModifier.META);
        }
        if ((mask & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) {
            modifiers.add(MapInputModifier.ALT_GRAPH);
        }
        return Set.copyOf(modifiers);
    }

    private static CoordinateConfiguration configuration(Projection projection) {
        Projection required = Objects.requireNonNull(projection, "projection");
        CrsRegistry registry =
                CrsRegistry.builder()
                        .registerDefinition(required.sourceCrs(), List.of())
                        .registerDefinition(required.targetCrs(), List.of())
                        .registerProjection(required)
                        .build();
        return new CoordinateConfiguration(registry, required.sourceCrs(), required.targetCrs());
    }

    private static boolean isExpectedConversionMiss(CrsException exception) {
        String code = exception.problem().code();
        return code.equals("CRS_COORDINATE_OUT_OF_DOMAIN")
                || code.equals("CRS_TRANSFORM_NON_FINITE");
    }

    private ViewContentSnapshot captureContent(
            List<MapLayerBinding> sourceBindings, MapViewport viewportSnapshot) {
        return captureContent(sourceBindings, viewportSnapshot, false);
    }

    private ViewContentSnapshot captureContent(
            List<MapLayerBinding> sourceBindings,
            MapViewport viewportSnapshot,
            boolean readRasters) {
        if (closed) {
            return new ViewContentSnapshot(List.of());
        }
        List<LayerSnapshot> captured = new ArrayList<>(sourceBindings.size());
        for (MapLayerBinding binding : sourceBindings) {
            captured.add(
                    switch (binding.kind()) {
                        case SNAPSHOT -> captureSnapshot(binding);
                        case FEATURE -> captureFeatureSource(binding, viewportSnapshot);
                        case RASTER ->
                                readRasters
                                        ? captureRasterSource(
                                                binding,
                                                viewportSnapshot,
                                                rasterRenderOptions.get(binding))
                                        : emptyRasterSnapshot(binding.id());
                        case ELEVATION ->
                                readRasters
                                        ? captureElevationSource(
                                                binding,
                                                viewportSnapshot,
                                                rasterRenderOptions.get(binding),
                                                elevationRasterStyles.get(binding))
                                        : emptyRasterSnapshot(binding.id());
                    });
        }
        return new ViewContentSnapshot(List.copyOf(captured));
    }

    private ViewContentSnapshot captureContentAtBoundary(
            List<MapLayerBinding> sourceBindings, MapViewport viewportSnapshot) {
        try {
            return captureContent(sourceBindings, viewportSnapshot);
        } catch (RuntimeException | Error failure) {
            throwUnchecked(drainSourceReportNotifications(failure));
            throw new AssertionError("unreachable");
        }
    }

    private LayerSnapshot captureSnapshot(MapLayerBinding binding) {
        Layer layer = binding.layer();
        String currentLayerId = requireContentId(layer.id(), "layer.id");
        if (!binding.id().equals(currentLayerId)) {
            throw new IllegalArgumentException("layer.id changed after binding");
        }
        List<Feature> features =
                List.copyOf(Objects.requireNonNull(layer.features(), "layer.features"));
        Set<String> featureIds = new HashSet<>();
        List<VisualFeature> visual = new ArrayList<>(features.size());
        for (Feature feature : features) {
            Objects.requireNonNull(feature, "feature");
            String featureId = requireContentId(feature.id(), "feature.id");
            if (!featureIds.add(featureId)) {
                throw new IllegalArgumentException(
                        "feature.id must be unique within layer "
                                + currentLayerId
                                + ": "
                                + featureId);
            }
            visual.add(
                    new VisualFeature(
                            feature.id(),
                            feature.name(),
                            feature.geometry(),
                            feature.symbol(),
                            mapToDisplay));
        }
        return new LayerSnapshot(
                currentLayerId, List.copyOf(visual), Optional.empty(), false, true);
    }

    private LayerSnapshot captureFeatureSource(
            MapLayerBinding binding, MapViewport viewportSnapshot) {
        FeatureSource source = binding.source();
        ResolvedFeatureBinding resolved = resolvedFeatureBindings.get(binding);
        MapLayerBinding.Operation operation = binding.beginOperation();
        DiagnosticReport planning = DiagnosticReport.empty();
        DiagnosticReport encountered = DiagnosticReport.empty();
        try {
            QueryEnvelopeTransform transformed =
                    resolved.displayToSource()
                            .transformQueryEnvelope(viewportSnapshot.visibleWorldEnvelope());
            if (transformed.status() == QueryEnvelopeStatus.OUTSIDE) {
                planning = queryEnvelopeWarning(source, "CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN");
                encountered = planning;
                MapLayerBinding.Phase phase = operation.finish(true);
                if (phase == MapLayerBinding.Phase.CANCELLED) {
                    DiagnosticReport cancelled = cancellationReport(source);
                    updateSourceResult(binding.id(), cancelled, false);
                    return new LayerSnapshot(
                            binding.id(), List.of(), Optional.empty(), true, false);
                }
                updateSourceResult(binding.id(), planning, true);
                return new LayerSnapshot(binding.id(), List.of(), Optional.empty(), true, true);
            }
            if (transformed.status() == QueryEnvelopeStatus.CLIPPED) {
                planning = queryEnvelopeWarning(source, "CRS_QUERY_ENVELOPE_CLIPPED");
            }
            encountered = planning;

            FeatureQuery query =
                    new FeatureQuery(
                            transformed.transformedEnvelope(),
                            AttributeSelection.NONE,
                            Optional.empty());
            List<FeatureRecord> staged = new ArrayList<>();
            FeatureQueryAccounting accounting =
                    new FeatureQueryAccounting(
                            source.metadata().identity().id(), source.limits().queryLimits());
            DiagnosticReport cursorReport;
            try (FeatureCursor cursor = source.openCursor(query, operation.token())) {
                while (cursor.advance()) {
                    FeatureRecord record = cursor.current();
                    encountered = mergeReports(planning, cursor.diagnostics(), source);
                    accounting.recordReturned(record, 1, operation.token());
                    staged.add(record);
                }
                cursorReport = cursor.diagnostics();
            }
            encountered = mergeReports(planning, cursorReport, source);
            for (FeatureRecord record : staged) {
                preflight(record.geometry(), resolved.sourceToDisplay(), operation.token(), source);
            }
            MapLayerBinding.Phase phase = operation.finish(true);
            if (phase == MapLayerBinding.Phase.CANCELLED) {
                DiagnosticReport cancelled =
                        mergeReports(encountered, cancellationReport(source), source);
                updateSourceResult(binding.id(), cancelled, false);
                return new LayerSnapshot(binding.id(), List.of(), Optional.empty(), true, false);
            }
            DiagnosticReport report = encountered;
            updateSourceResult(binding.id(), report, true);
            List<VisualFeature> visual = new ArrayList<>(staged.size());
            for (FeatureRecord record : staged) {
                visual.add(
                        new VisualFeature(
                                record.id(),
                                record.name(),
                                record.geometry(),
                                sourceSymbol(binding, record.geometry()),
                                resolved.sourceToDisplay()));
            }
            return new LayerSnapshot(
                    binding.id(), List.copyOf(visual), Optional.empty(), true, true);
        } catch (SourceException failure) {
            MapLayerBinding.Phase phase = operation.finish(false);
            DiagnosticReport failureWarnings = withoutTerminal(failure.report());
            DiagnosticReport observed =
                    failureWarnings.entries().isEmpty()
                                    && failureWarnings.omittedWarningCount() == 0
                            ? encountered
                            : mergeReports(planning, failureWarnings, source);
            DiagnosticReport report =
                    phase == MapLayerBinding.Phase.CANCELLED
                            ? mergeReports(observed, cancellationReport(source), source)
                            : mergeReports(observed, terminalOnly(failure.report()), source);
            updateSourceResult(binding.id(), report, false);
            return new LayerSnapshot(binding.id(), List.of(), Optional.empty(), true, false);
        } catch (CrsException failure) {
            MapLayerBinding.Phase phase = operation.finish(false);
            DiagnosticReport report =
                    phase == MapLayerBinding.Phase.CANCELLED
                            ? mergeReports(encountered, cancellationReport(source), source)
                            : mergeReports(encountered, crsFailure(source, failure), source);
            updateSourceResult(binding.id(), report, false);
            return new LayerSnapshot(binding.id(), List.of(), Optional.empty(), true, false);
        } catch (RuntimeException | Error failure) {
            operation.finish(false);
            throw failure;
        } finally {
            binding.endOperation(operation);
        }
    }

    private LayerSnapshot captureRasterSource(
            MapLayerBinding binding, MapViewport viewportSnapshot, RasterRenderOptions options) {
        Objects.requireNonNull(options, "options");
        if (options.opacity() == 0.0) {
            return emptyRasterSnapshot(binding.id());
        }
        RasterSource source = binding.rasterSource();
        RasterSourceMetadata metadata = source.metadata();
        MapLayerBinding.Operation operation = binding.beginOperation();
        DiagnosticReport encountered = DiagnosticReport.empty();
        try {
            Optional<RasterWindow> visible =
                    RasterGridWindows.visibleWindow(
                            metadata, viewportSnapshot.visibleWorldEnvelope());
            if (visible.isEmpty()) {
                MapLayerBinding.Phase phase = operation.finish(true);
                if (phase == MapLayerBinding.Phase.CANCELLED) {
                    updateSourceResult(binding.id(), rasterCancellationReport(source), false);
                    return emptyRasterSnapshot(binding.id());
                }
                updateSourceResult(binding.id(), DiagnosticReport.empty(), true);
                return emptyRasterSnapshot(binding.id());
            }
            RasterWindow window = visible.orElseThrow();
            RasterGridWindows.OutputSize output =
                    RasterGridWindows.outputSize(metadata, window, viewportSnapshot);
            RasterRequest request =
                    new RasterRequest(
                            window,
                            output.width(),
                            output.height(),
                            options.interpolation(),
                            Optional.empty());
            RasterRequestAccounting accounting =
                    new RasterRequestAccounting(
                            metadata.identity().id(),
                            source.limits().requestLimits(),
                            operation.token());
            accounting.checkpoint();
            accounting.validateWindow(metadata, window);
            long sourcePixels = Math.multiplyExact((long) window.width(), window.height());
            accounting.chargeSourcePixels(sourcePixels);
            long outputPixels =
                    accounting.validateOutput(request.outputWidth(), request.outputHeight());
            accounting.chargeIntermediateBytes(Math.multiplyExact(outputPixels, 8L));
            accounting.chargePublishedBytes(Math.multiplyExact(outputPixels, 4L));
            RasterRead read = source.read(request, operation.token());
            encountered = read.diagnostics();
            requireRasterReadShape(request, read);
            BufferedImage image =
                    AwtRgbaPixels.toBufferedImage(read.pixels(), accounting::checkpoint);
            MapLayerBinding.Phase phase = operation.finish(true);
            if (phase == MapLayerBinding.Phase.CANCELLED) {
                DiagnosticReport cancelled =
                        mergeReports(encountered, rasterCancellationReport(source), source);
                updateSourceResult(binding.id(), cancelled, false);
                return emptyRasterSnapshot(binding.id());
            }
            updateSourceResult(binding.id(), encountered, true);
            return new LayerSnapshot(
                    binding.id(),
                    List.of(),
                    Optional.of(
                            new RasterSnapshot(
                                    image,
                                    RasterGridWindows.mapBounds(metadata, window),
                                    Optional.of(metadata.gridPlacement().orElseThrow()),
                                    window,
                                    RasterGridWindows.mapBounds(metadata, window),
                                    options)),
                    false,
                    true);
        } catch (SourceException failure) {
            MapLayerBinding.Phase phase = operation.finish(false);
            DiagnosticReport warnings = withoutTerminal(failure.report());
            DiagnosticReport observed =
                    warnings.entries().isEmpty() && warnings.omittedWarningCount() == 0
                            ? encountered
                            : mergeReports(encountered, warnings, source);
            DiagnosticReport report =
                    phase == MapLayerBinding.Phase.CANCELLED
                                    || failure.terminal().code().equals("SOURCE_CANCELLED")
                            ? mergeReports(observed, rasterCancellationReport(source), source)
                            : mergeReports(observed, terminalOnly(failure.report()), source);
            updateSourceResult(binding.id(), report, false);
            return emptyRasterSnapshot(binding.id());
        } catch (RuntimeException | Error failure) {
            operation.finish(false);
            throw failure;
        } finally {
            binding.endOperation(operation);
        }
    }

    private LayerSnapshot captureElevationSource(
            MapLayerBinding binding,
            MapViewport viewportSnapshot,
            RasterRenderOptions options,
            ElevationRasterStyle style) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(style, "style");
        if (options.opacity() == 0.0) {
            return emptyRasterSnapshot(binding.id());
        }
        ElevationSource source = binding.elevationSource();
        ElevationSourceMetadata metadata = source.metadata();
        MapLayerBinding.Operation operation = binding.beginOperation();
        DiagnosticReport encountered = DiagnosticReport.empty();
        try {
            Optional<ElevationRasterization.Plan> planned =
                    ElevationRasterization.plan(
                            metadata,
                            viewportSnapshot.visibleWorldEnvelope(),
                            viewportSnapshot.worldUnitsPerPixel(),
                            options.interpolation(),
                            binding.rasterLimits());
            if (planned.isEmpty()) {
                MapLayerBinding.Phase phase = operation.finish(true);
                if (phase == MapLayerBinding.Phase.CANCELLED) {
                    updateSourceResult(binding.id(), elevationCancellationReport(source), false);
                    return emptyRasterSnapshot(binding.id());
                }
                updateSourceResult(binding.id(), DiagnosticReport.empty(), true);
                return emptyRasterSnapshot(binding.id());
            }
            ElevationRasterization.Plan plan = planned.orElseThrow();
            RasterRequest request = plan.request();
            RasterRequestAccounting accounting =
                    new RasterRequestAccounting(
                            metadata.identity().id(), binding.rasterLimits(), operation.token());
            accounting.checkpoint();
            accounting.validateWindow(
                    metadata.columnCount(), metadata.rowCount(), request.sourceWindow());
            long outputPixels =
                    accounting.validateOutput(request.outputWidth(), request.outputHeight());
            accounting.chargeIntermediateBytes(Math.multiplyExact(outputPixels, 8L));
            accounting.chargePublishedBytes(Math.multiplyExact(outputPixels, 4L));
            RasterRead read =
                    ElevationRasterization.rasterize(source, plan, style, operation.token());
            encountered = read.diagnostics();
            requireRasterReadShape(request, read);
            BufferedImage image =
                    AwtRgbaPixels.toBufferedImage(read.pixels(), accounting::checkpoint);
            MapLayerBinding.Phase phase = operation.finish(true);
            if (phase == MapLayerBinding.Phase.CANCELLED) {
                DiagnosticReport cancelled =
                        mergeReports(
                                encountered,
                                elevationCancellationReport(source),
                                binding.rasterLimits().retainedWarnings());
                updateSourceResult(binding.id(), cancelled, false);
                return emptyRasterSnapshot(binding.id());
            }
            updateSourceResult(binding.id(), encountered, true);
            return new LayerSnapshot(
                    binding.id(),
                    List.of(),
                    Optional.of(
                            new RasterSnapshot(
                                    image,
                                    plan.imageMapBounds(),
                                    Optional.empty(),
                                    request.sourceWindow(),
                                    plan.clipMapBounds(),
                                    options)),
                    false,
                    true);
        } catch (SourceException failure) {
            MapLayerBinding.Phase phase = operation.finish(false);
            DiagnosticReport warnings = withoutTerminal(failure.report());
            DiagnosticReport observed =
                    warnings.entries().isEmpty() && warnings.omittedWarningCount() == 0
                            ? encountered
                            : mergeReports(
                                    encountered,
                                    warnings,
                                    binding.rasterLimits().retainedWarnings());
            DiagnosticReport report =
                    phase == MapLayerBinding.Phase.CANCELLED
                                    || failure.terminal().code().equals("SOURCE_CANCELLED")
                            ? mergeReports(
                                    observed,
                                    elevationCancellationReport(source),
                                    binding.rasterLimits().retainedWarnings())
                            : mergeReports(
                                    observed,
                                    terminalOnly(failure.report()),
                                    binding.rasterLimits().retainedWarnings());
            updateSourceResult(binding.id(), report, false);
            return emptyRasterSnapshot(binding.id());
        } catch (RuntimeException | Error failure) {
            operation.finish(false);
            throw failure;
        } finally {
            binding.endOperation(operation);
        }
    }

    private static LayerSnapshot emptyRasterSnapshot(String layerId) {
        return new LayerSnapshot(layerId, List.of(), Optional.empty(), false, true);
    }

    private static void requireRasterReadShape(RasterRequest request, RasterRead read) {
        Objects.requireNonNull(read, "read");
        if (!request.sourceWindow().equals(read.sourceWindow())
                || request.outputWidth() != read.pixels().width()
                || request.outputHeight() != read.pixels().height()) {
            throw new IllegalStateException("Raster source returned a mismatched read shape");
        }
    }

    private static Symbol sourceSymbol(MapLayerBinding binding, Geometry geometry) {
        return switch (geometryRole(geometry)) {
            case MARKER -> binding.marker();
            case LINE -> binding.line();
            case FILL -> binding.fill();
            case LEGACY_GEOMETRY ->
                    throw new IllegalArgumentException("Unsupported source geometry");
        };
    }

    private static void preflight(
            Geometry geometry,
            CrsOperation sourceToDisplay,
            io.github.mundanej.map.api.CancellationToken cancellation,
            FeatureSource source) {
        int index = 0;
        for (CoordinateSequence sequence : coordinateSequences(geometry)) {
            for (int coordinateIndex = 0; coordinateIndex < sequence.size(); coordinateIndex++) {
                if ((index++ & 4095) == 0 && cancellation.isCancellationRequested()) {
                    throw cancellationException(source);
                }
                sourceToDisplay.transform(sequence.coordinate(coordinateIndex));
            }
        }
        if (cancellation.isCancellationRequested()) {
            throw cancellationException(source);
        }
    }

    private static List<CoordinateSequence> coordinateSequences(Geometry geometry) {
        if (geometry instanceof PointGeometry point) {
            return List.of(CoordinateSequence.of(point.coordinate().x(), point.coordinate().y()));
        }
        if (geometry instanceof LineStringGeometry line) {
            return List.of(line.coordinates());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            List<CoordinateSequence> sequences = new ArrayList<>();
            sequences.add(polygon.exterior());
            sequences.addAll(polygon.holes());
            return sequences;
        }
        if (geometry instanceof MultiPointGeometry points) {
            return List.of(points.coordinates());
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return List.of(lines.coordinates());
        }
        return List.of(((MultiPolygonGeometry) geometry).coordinates());
    }

    private CandidateBindings validateBindings(List<MapLayerBinding> candidate) {
        Set<String> ids = new HashSet<>();
        Set<MapLayerBinding> identities = identitySet(List.of());
        Set<Object> sources = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> sourceIds = new HashSet<>();
        IdentityHashMap<MapLayerBinding, ResolvedFeatureBinding> resolved = new IdentityHashMap<>();
        for (MapLayerBinding binding : candidate) {
            Objects.requireNonNull(binding, "binding");
            if (!identities.add(binding)) {
                throw new IllegalArgumentException("A binding instance may appear only once");
            }
            if (!ids.add(requireContentId(binding.id(), "binding.id"))) {
                throw new IllegalArgumentException("binding.id must be unique: " + binding.id());
            }
            if (binding.isClosed()) {
                throw new IllegalStateException("binding is closed: " + binding.id());
            }
            if (binding.kind() == MapLayerBinding.Kind.SNAPSHOT) {
                validateSnapshotFeatures(binding);
                continue;
            }
            Object source = null;
            String sourceId = null;
            boolean sourceClosed = false;
            switch (binding.kind()) {
                case FEATURE -> {
                    source = binding.source();
                    sourceId = binding.source().metadata().identity().id();
                    sourceClosed = binding.source().isClosed();
                }
                case RASTER -> {
                    source = binding.rasterSource();
                    sourceId = binding.rasterSource().metadata().identity().id();
                    sourceClosed = binding.rasterSource().isClosed();
                }
                case ELEVATION -> {
                    source = binding.elevationSource();
                    sourceId = binding.elevationSource().metadata().identity().id();
                    sourceClosed = binding.elevationSource().isClosed();
                }
                case SNAPSHOT -> throw new AssertionError("snapshot handled above");
            }
            if (sourceClosed) {
                throw new IllegalStateException("source is closed: " + binding.id());
            }
            if (!sources.add(source)) {
                throw new IllegalArgumentException("A source instance may be bound only once");
            }
            if (!sourceIds.add(sourceId)) {
                throw new IllegalArgumentException("source identity must be unique: " + sourceId);
            }
            for (MapLayerBinding installed : bindings) {
                if (installed != binding && installed.owned() && sameSource(installed, source)) {
                    throw new IllegalStateException(
                            "An owned source cannot be transferred to a replacement binding");
                }
            }
            if (binding.kind() == MapLayerBinding.Kind.RASTER) {
                validateRasterAttachment(binding);
                continue;
            }
            if (binding.kind() == MapLayerBinding.Kind.ELEVATION) {
                validateElevationAttachment(binding);
                continue;
            }
            FeatureSource featureSource = binding.source();
            CrsOperation sourceToDisplay =
                    crsRegistry.operationFromMetadata(featureSource.metadata().crs(), displayCrs);
            CrsOperation displayToSource =
                    crsRegistry.operation(displayCrs, sourceToDisplay.sourceCrs());
            validateSourceSymbol(binding.marker(), SymbolRole.MARKER);
            validateSourceSymbol(binding.line(), SymbolRole.LINE);
            validateSourceSymbol(binding.fill(), SymbolRole.FILL);
            resolved.put(binding, new ResolvedFeatureBinding(sourceToDisplay, displayToSource));
        }
        return new CandidateBindings(Collections.unmodifiableMap(resolved));
    }

    private static boolean sameSource(MapLayerBinding binding, Object source) {
        return switch (binding.kind()) {
            case SNAPSHOT -> false;
            case FEATURE -> binding.source() == source;
            case RASTER -> binding.rasterSource() == source;
            case ELEVATION -> binding.elevationSource() == source;
        };
    }

    private void validateRasterAttachment(MapLayerBinding binding) {
        RasterSource source = binding.rasterSource();
        RasterSourceMetadata metadata = source.metadata();
        Envelope bounds = metadata.mapBounds().orElseThrow(() -> rasterBoundsMissing(source));
        if (metadata.crs().isEmpty() || metadata.crs().orElseThrow().definition().isEmpty()) {
            crsRegistry.operationFromMetadata(metadata.crs(), displayCrs);
            throw new AssertionError("unreachable");
        }
        CrsDefinition rasterCrs = metadata.crs().orElseThrow().definition().orElseThrow();
        CrsOperation identity = crsRegistry.operation(rasterCrs, rasterCrs);
        if (!identity.sourceCrs().equals(displayCrs)) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_RASTER_WARP_UNSUPPORTED",
                            "Raster rendering requires the display coordinate reference system",
                            Map.of(
                                    "sourceCrs", rasterCrs.canonicalIdentifier(),
                                    "displayCrs", displayCrs.canonicalIdentifier())));
        }
        identity.transformEnvelopeStrict(bounds);
    }

    private void validateElevationAttachment(MapLayerBinding binding) {
        ElevationSource source = binding.elevationSource();
        ElevationSourceMetadata metadata = source.metadata();
        if (binding.initialElevationStyle().colorRamp().unit() != metadata.elevationUnit()) {
            throw new IllegalArgumentException("color-ramp unit must equal source elevation unit");
        }
        CrsOperation identity =
                crsRegistry.operationFromMetadata(Optional.of(metadata.crs()), displayCrs);
        if (!identity.sourceCrs().equals(displayCrs)) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_RASTER_WARP_UNSUPPORTED",
                            "Elevation rendering requires the display coordinate reference system",
                            Map.of(
                                    "sourceCrs", identity.sourceCrs().canonicalIdentifier(),
                                    "displayCrs", displayCrs.canonicalIdentifier())));
        }
        identity.transformEnvelopeStrict(metadata.sampleBounds());
    }

    private static SourceException rasterBoundsMissing(RasterSource source) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "RASTER_MAP_BOUNDS_MISSING",
                        DiagnosticSeverity.ERROR,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        "Raster map bounds are required for rendering",
                        Map.of());
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static void validateSnapshotFeatures(MapLayerBinding binding) {
        List<Feature> features =
                List.copyOf(Objects.requireNonNull(binding.layer().features(), "layer.features"));
        Set<String> featureIds = new HashSet<>();
        for (Feature feature : features) {
            Objects.requireNonNull(feature, "feature");
            String featureId = requireContentId(feature.id(), "feature.id");
            if (!featureIds.add(featureId)) {
                throw new IllegalArgumentException(
                        "feature.id must be unique within layer "
                                + binding.id()
                                + ": "
                                + featureId);
            }
        }
    }

    private void validateSourceSymbol(Symbol symbol, SymbolRole role) {
        if (symbol.role() != role) {
            throw new IllegalArgumentException("Source symbol role must be " + role);
        }
        AwtSymbolRenderer renderer = symbolRenderers.find(role, symbol.rendererKey());
        if (renderer == null) {
            throw rendererNotRegistered(new SymbolSnapshot(role, symbol.rendererKey()));
        }
        if (!renderer.supports(symbol)) {
            throw rendererValueMismatch(new SymbolSnapshot(role, symbol.rendererKey()));
        }
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                validateSourceSymbol(child, role);
            }
        }
    }

    private static Set<MapLayerBinding> identitySet(List<MapLayerBinding> values) {
        Set<MapLayerBinding> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(values);
        return result;
    }

    private static Optional<FeatureSelection> reconcileAfterBindingReplacement(
            Optional<FeatureSelection> current,
            List<MapLayerBinding> previous,
            List<MapLayerBinding> candidate) {
        if (current.isEmpty()) {
            return current;
        }
        FeatureSelection selected = current.orElseThrow();
        MapLayerBinding old = bindingById(previous, selected.layerId());
        if (old == null) {
            return Optional.empty();
        }
        if (old.kind() != MapLayerBinding.Kind.SNAPSHOT) {
            return old.kind() == MapLayerBinding.Kind.FEATURE
                            && identitySet(candidate).contains(old)
                    ? current
                    : Optional.empty();
        }
        MapLayerBinding replacement = bindingById(candidate, selected.layerId());
        if (replacement == null || replacement.kind() != MapLayerBinding.Kind.SNAPSHOT) {
            return Optional.empty();
        }
        return replacement.layer().features().stream()
                        .anyMatch(feature -> feature.id().equals(selected.featureId()))
                ? current
                : Optional.empty();
    }

    private static MapLayerBinding bindingById(List<MapLayerBinding> values, String id) {
        for (MapLayerBinding binding : values) {
            if (binding.id().equals(id)) {
                return binding;
            }
        }
        return null;
    }

    private void reconcileRasterRenderOptions(List<MapLayerBinding> candidate) {
        Set<MapLayerBinding> retained = identitySet(candidate);
        rasterRenderOptions.keySet().removeIf(binding -> !retained.contains(binding));
        for (MapLayerBinding binding : candidate) {
            if (binding.kind() == MapLayerBinding.Kind.RASTER
                    || binding.kind() == MapLayerBinding.Kind.ELEVATION) {
                rasterRenderOptions.putIfAbsent(binding, binding.initialRasterOptions());
            }
        }
        elevationRasterStyles.keySet().removeIf(binding -> !retained.contains(binding));
        for (MapLayerBinding binding : candidate) {
            if (binding.kind() == MapLayerBinding.Kind.ELEVATION) {
                elevationRasterStyles.putIfAbsent(binding, binding.initialElevationStyle());
            }
        }
    }

    private void reconcileReportsAfterReplacement(
            List<MapLayerBinding> previous, List<MapLayerBinding> candidate) {
        Set<MapLayerBinding> oldIdentities = identitySet(previous);
        for (MapLayerBinding binding : candidate) {
            if (binding.kind() != MapLayerBinding.Kind.SNAPSHOT
                    && !oldIdentities.contains(binding)) {
                DiagnosticReport opening =
                        switch (binding.kind()) {
                            case FEATURE -> binding.source().openingDiagnostics();
                            case RASTER -> binding.rasterSource().openingDiagnostics();
                            case ELEVATION -> binding.elevationSource().openingDiagnostics();
                            case SNAPSHOT -> throw new AssertionError("snapshot excluded above");
                        };
                if (!opening.entries().isEmpty()) {
                    transitionSourceReport(binding.id(), Optional.of(opening));
                }
                sourceAvailability.put(binding.id(), true);
            }
        }
    }

    private Throwable releaseRemoved(
            List<MapLayerBinding> previous, List<MapLayerBinding> candidate) {
        Set<MapLayerBinding> retained = identitySet(candidate);
        Throwable primary = null;
        for (int index = previous.size() - 1; index >= 0; index--) {
            MapLayerBinding binding = previous.get(index);
            if (retained.contains(binding)) {
                continue;
            }
            try {
                if (binding.owned()) {
                    binding.closeFromOwner(this);
                } else {
                    binding.release(this);
                }
            } catch (RuntimeException | Error failure) {
                if (failure instanceof SourceException sourceFailure) {
                    transitionSourceReport(binding.id(), Optional.of(sourceFailure.report()));
                }
                if (primary == null) {
                    primary = failure;
                } else {
                    suppressDistinct(primary, failure);
                }
            }
            if (binding.kind() != MapLayerBinding.Kind.SNAPSHOT) {
                transitionSourceReport(binding.id(), Optional.empty());
                sourceAvailability.remove(binding.id());
            }
        }
        return primary;
    }

    private void updateSourceResult(String layerId, DiagnosticReport report, boolean available) {
        transitionSourceReport(
                layerId, report.entries().isEmpty() ? Optional.empty() : Optional.of(report));
        Boolean previous = sourceAvailability.put(layerId, available);
        if (previous != null && previous.booleanValue() != available) {
            repaint();
        }
    }

    private void transitionSourceReport(String layerId, Optional<DiagnosticReport> next) {
        Optional<DiagnosticReport> previous = Optional.ofNullable(sourceReports.get(layerId));
        if (previous.equals(next)) {
            return;
        }
        if (next.isPresent()) {
            sourceReports.put(layerId, next.orElseThrow());
        } else {
            sourceReports.remove(layerId);
        }
        sourceReportNotifications.add(new MapSourceReportEvent(layerId, previous, next));
    }

    private void drainSourceReportNotifications() {
        if (drainingSourceReportNotifications) {
            return;
        }
        drainingSourceReportNotifications = true;
        try {
            while (!sourceReportNotifications.isEmpty()) {
                MapSourceReportEvent event = sourceReportNotifications.removeFirst();
                for (MapSourceReportListener listener : List.copyOf(sourceReportListeners)) {
                    listener.onMapSourceReportChanged(event);
                }
            }
        } finally {
            drainingSourceReportNotifications = false;
        }
    }

    private Throwable drainSourceReportNotifications(Throwable primary) {
        try {
            drainSourceReportNotifications();
        } catch (RuntimeException | Error failure) {
            if (primary == null) {
                return failure;
            }
            suppressDistinct(primary, failure);
        }
        return primary;
    }

    private static DiagnosticReport queryEnvelopeWarning(FeatureSource source, String code) {
        SourceDiagnostic warning =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.WARNING,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        code.equals("CRS_QUERY_ENVELOPE_CLIPPED")
                                ? "Visible query envelope was clipped to the CRS domain"
                                : "Visible query envelope is outside the CRS domain",
                        Map.of());
        return new DiagnosticReport(List.of(warning), 0);
    }

    private static DiagnosticReport crsFailure(FeatureSource source, CrsException failure) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        failure.problem().code(),
                        DiagnosticSeverity.ERROR,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        failure.problem().message(),
                        failure.problem().context());
        return new DiagnosticReport(List.of(terminal), 0);
    }

    private static DiagnosticReport cancellationReport(FeatureSource source) {
        return cancellationException(source).report();
    }

    private static SourceException cancellationException(FeatureSource source) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SOURCE_CANCELLED",
                        DiagnosticSeverity.ERROR,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        "Source operation was cancelled",
                        Map.of("operation", "map-view-query"));
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static DiagnosticReport rasterCancellationReport(RasterSource source) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SOURCE_CANCELLED",
                        DiagnosticSeverity.ERROR,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        "Raster request was cancelled",
                        Map.of("operation", "raster-read"));
        return new DiagnosticReport(List.of(terminal), 0);
    }

    private static DiagnosticReport elevationCancellationReport(ElevationSource source) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SOURCE_CANCELLED",
                        DiagnosticSeverity.ERROR,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        "Elevation rasterization was cancelled",
                        Map.of("operation", "raster-read"));
        return new DiagnosticReport(List.of(terminal), 0);
    }

    private static DiagnosticReport mergeReports(
            DiagnosticReport first, DiagnosticReport second, FeatureSource source) {
        return mergeReports(first, second, source.limits().queryLimits().retainedWarnings());
    }

    private static DiagnosticReport mergeReports(
            DiagnosticReport first, DiagnosticReport second, RasterSource source) {
        return mergeReports(first, second, source.limits().requestLimits().retainedWarnings());
    }

    private static DiagnosticReport mergeReports(
            DiagnosticReport first, DiagnosticReport second, int maximum) {
        List<SourceDiagnostic> entries = new ArrayList<>();
        SourceDiagnostic terminal = null;
        long omitted = saturatingAdd(first.omittedWarningCount(), second.omittedWarningCount());
        for (DiagnosticReport report : List.of(first, second)) {
            for (SourceDiagnostic diagnostic : report.entries()) {
                if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
                    terminal = diagnostic;
                } else if (entries.size() < maximum) {
                    entries.add(diagnostic);
                } else {
                    omitted = omitted == Long.MAX_VALUE ? omitted : omitted + 1;
                }
            }
        }
        if (terminal != null) {
            entries.add(terminal);
        }
        return new DiagnosticReport(entries, omitted);
    }

    private static DiagnosticReport withoutTerminal(DiagnosticReport report) {
        List<SourceDiagnostic> warnings = new ArrayList<>();
        for (SourceDiagnostic diagnostic : report.entries()) {
            if (diagnostic.severity() == DiagnosticSeverity.WARNING) {
                warnings.add(diagnostic);
            }
        }
        return new DiagnosticReport(warnings, report.omittedWarningCount());
    }

    private static DiagnosticReport terminalOnly(DiagnosticReport report) {
        SourceDiagnostic terminal = report.entries().getLast();
        if (terminal.severity() != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException("A failure report requires a terminal error");
        }
        return new DiagnosticReport(List.of(terminal), 0);
    }

    private static long saturatingAdd(long first, long second) {
        long result = first + second;
        return result < 0 || result < first ? Long.MAX_VALUE : result;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("MapView is closed");
        }
    }

    private static String requireContentId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Optional<FeatureSelection> reconcile(
            Optional<FeatureSelection> current, ViewContentSnapshot snapshot) {
        return current.filter(selection -> contains(snapshot, selection));
    }

    private static boolean contains(ViewContentSnapshot snapshot, FeatureSelection requested) {
        for (LayerSnapshot layer : snapshot.layers()) {
            if (layer.id().equals(requested.layerId())) {
                return layer.sourceBacked()
                        || layer.features().stream()
                                .anyMatch(feature -> feature.id().equals(requested.featureId()));
            }
        }
        return false;
    }

    private static boolean contains(ViewContentSnapshot snapshot, MapHit requested) {
        for (LayerSnapshot layer : snapshot.layers()) {
            if (layer.id().equals(requested.layerId())) {
                return layer.features().stream()
                        .anyMatch(feature -> feature.id().equals(requested.featureId()));
            }
        }
        return false;
    }

    private boolean reconcileInteraction(
            ViewContentSnapshot snapshot, boolean repaintOnChange, boolean drainNotifications) {
        Optional<FeatureSelection> reconciledSelection = reconcile(selection, snapshot);
        Optional<MapHit> reconciledHover = hover.filter(hit -> contains(snapshot, hit));
        Optional<HoverProbe> transactionProbe = hoverProbe;
        if (transactionProbe.isPresent()) {
            HoverProbe probe = transactionProbe.orElseThrow();
            Dimension size = effectiveSize();
            if (probe.screenX() >= 0.0
                    && probe.screenX() < size.width
                    && probe.screenY() >= 0.0
                    && probe.screenY() < size.height) {
                reconciledHover =
                        hitTestSnapshot(
                                        snapshot,
                                        viewport,
                                        size,
                                        probe.screenX(),
                                        probe.screenY(),
                                        DEFAULT_HOVER_TOLERANCE_PIXELS)
                                .topmost();
            } else {
                reconciledHover = Optional.empty();
            }
        }
        if (!snapshot.allSourcesAvailable()) {
            hoverProbe = Optional.empty();
        }
        return transitionInteraction(
                reconciledSelection, reconciledHover, repaintOnChange, drainNotifications);
    }

    private boolean transitionInteraction(
            Optional<FeatureSelection> nextSelection,
            Optional<MapHit> nextHover,
            boolean repaintOnChange) {
        return transitionInteraction(nextSelection, nextHover, repaintOnChange, true);
    }

    private boolean transitionInteraction(
            Optional<FeatureSelection> nextSelection,
            Optional<MapHit> nextHover,
            boolean repaintOnChange,
            boolean drainNotifications) {
        Objects.requireNonNull(nextSelection, "nextSelection");
        Objects.requireNonNull(nextHover, "nextHover");
        Optional<FeatureSelection> previousSelection = selection;
        Optional<MapHit> previousHover = hover;
        boolean selectionChanged = !previousSelection.equals(nextSelection);
        boolean hoverChanged = !previousHover.equals(nextHover);
        if (!selectionChanged && !hoverChanged) {
            return false;
        }
        selection = nextSelection;
        hover = nextHover;
        if (!sameSelectionIdentity(previousSelection, nextSelection)) {
            selectionPaintState = Optional.empty();
        }
        if (!sameHoverIdentity(previousHover, nextHover)) {
            hoverPaintState = Optional.empty();
        }
        if (nextHover.isEmpty()) {
            hoverProbe = Optional.empty();
        }
        if (repaintOnChange) {
            repaint();
        }
        if (selectionChanged) {
            interactionNotifications.addLast(
                    InteractionNotification.selection(
                            new MapSelectionEvent(previousSelection, nextSelection)));
        }
        if (hoverChanged) {
            interactionNotifications.addLast(
                    InteractionNotification.hover(new MapHoverEvent(previousHover, nextHover)));
        }
        if (drainNotifications) {
            drainInteractionNotifications();
        }
        return true;
    }

    private boolean clearHover() {
        hoverProbe = Optional.empty();
        return transitionInteraction(selection, Optional.empty(), true);
    }

    private boolean retainInteractionPaintState(
            Optional<?> interaction, OverlayCandidate candidate, boolean hoverState) {
        Optional<AwtLogicalPaintPresence> previous =
                hoverState ? hoverPaintState : selectionPaintState;
        Optional<AwtLogicalPaintPresence> next =
                interaction.isPresent() && candidate != null
                        ? Optional.of(candidate.presence())
                        : Optional.empty();
        if (hoverState) {
            hoverPaintState = next;
        } else {
            selectionPaintState = next;
        }
        return previous.isPresent() && !previous.equals(next);
    }

    private static boolean sameSelectionIdentity(
            Optional<FeatureSelection> first, Optional<FeatureSelection> second) {
        return first.equals(second);
    }

    private static boolean sameHoverIdentity(Optional<MapHit> first, Optional<MapHit> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        MapHit firstHit = first.orElseThrow();
        MapHit secondHit = second.orElseThrow();
        return firstHit.layerId().equals(secondHit.layerId())
                && firstHit.featureId().equals(secondHit.featureId());
    }

    private void runThenClearHover(Runnable action) {
        Throwable primary = null;
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        if (primary == null) {
            clearHover();
            return;
        }
        clearHoverSuppressing(primary);
        throwUnchecked(primary);
    }

    private void clearHoverSuppressing(Throwable primary) {
        try {
            clearHover();
        } catch (RuntimeException | Error clearFailure) {
            suppressDistinct(primary, clearFailure);
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) failure;
    }

    private void drainInteractionNotifications() {
        if (drainingInteractionNotifications) {
            return;
        }
        drainingInteractionNotifications = true;
        RuntimeException firstFailure = null;
        List<RuntimeException> distinctFailures = new ArrayList<>();
        try {
            while (!interactionNotifications.isEmpty()) {
                InteractionNotification notification = interactionNotifications.removeFirst();
                if (notification.selectionEvent() != null) {
                    for (MapSelectionListener listener : List.copyOf(selectionListeners)) {
                        try {
                            listener.onMapSelectionChanged(notification.selectionEvent());
                        } catch (RuntimeException failure) {
                            if (!containsIdentity(distinctFailures, failure)) {
                                distinctFailures.add(failure);
                                if (firstFailure == null) {
                                    firstFailure = failure;
                                } else {
                                    firstFailure.addSuppressed(failure);
                                }
                            }
                        } catch (Error failure) {
                            interactionNotifications.clear();
                            throw failure;
                        }
                    }
                } else {
                    for (MapHoverListener listener : List.copyOf(hoverListeners)) {
                        try {
                            listener.onMapHoverChanged(notification.hoverEvent());
                        } catch (RuntimeException failure) {
                            if (!containsIdentity(distinctFailures, failure)) {
                                distinctFailures.add(failure);
                                if (firstFailure == null) {
                                    firstFailure = failure;
                                } else {
                                    firstFailure.addSuppressed(failure);
                                }
                            }
                        } catch (Error failure) {
                            interactionNotifications.clear();
                            throw failure;
                        }
                    }
                }
            }
        } finally {
            drainingInteractionNotifications = false;
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private static boolean containsIdentity(
            List<RuntimeException> failures, RuntimeException candidate) {
        for (RuntimeException failure : failures) {
            if (failure == candidate) {
                return true;
            }
        }
        return false;
    }

    private static <T> void removeIdentical(List<T> listeners, T requested) {
        for (int index = 0; index < listeners.size(); index++) {
            if (listeners.get(index) == requested) {
                listeners.remove(index);
                return;
            }
        }
    }

    private void synchronizeViewportSize() {
        Dimension size = effectiveSize();
        if (viewport.width() != size.width || viewport.height() != size.height) {
            viewport = viewport.resized(size.width, size.height);
            clearHover();
        }
    }

    private Dimension effectiveSize() {
        int width = getWidth() > 0 ? getWidth() : getPreferredSize().width;
        int height = getHeight() > 0 ? getHeight() : getPreferredSize().height;
        return new Dimension(Math.max(1, width), Math.max(1, height));
    }

    private final class ToolContextSnapshot implements MapToolContext {
        private final MapViewport snapshotViewport;

        private ToolContextSnapshot(MapViewport snapshotViewport) {
            this.snapshotViewport = snapshotViewport;
        }

        @Override
        public CrsDefinition mapCrs() {
            return mapCrs;
        }

        @Override
        public CrsDefinition displayCrs() {
            return displayCrs;
        }

        @Override
        public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
            Objects.requireNonNull(coordinate, "coordinate");
            Coordinate world;
            try {
                world = mapToDisplay.transform(coordinate);
            } catch (CrsException exception) {
                if (isExpectedConversionMiss(exception)) {
                    return Optional.empty();
                }
                throw exception;
            }
            try {
                return Optional.of(snapshotViewport.worldToScreen(world));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Coordinate> screenToMap(double screenX, double screenY) {
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
                throw new IllegalArgumentException("Screen coordinates must be finite");
            }
            Coordinate world;
            try {
                world = snapshotViewport.screenToWorld(screenX, screenY);
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
            try {
                return Optional.of(displayToMap.transform(world));
            } catch (CrsException exception) {
                if (isExpectedConversionMiss(exception)) {
                    return Optional.empty();
                }
                throw exception;
            }
        }

        @Override
        public void requestRepaint() {
            repaint();
        }
    }

    private record RoutedMouse(RouteOutcome outcome, MapToolEvent event) {}

    private record HoverProbe(double screenX, double screenY) {}

    private record OverlayCandidate(VisualFeature feature, AwtLogicalPaintPresence presence) {}

    private record InteractionNotification(
            MapSelectionEvent selectionEvent, MapHoverEvent hoverEvent) {
        private static InteractionNotification selection(MapSelectionEvent event) {
            return new InteractionNotification(Objects.requireNonNull(event, "event"), null);
        }

        private static InteractionNotification hover(MapHoverEvent event) {
            return new InteractionNotification(null, Objects.requireNonNull(event, "event"));
        }
    }

    private record VisualFeature(
            String id,
            String name,
            Geometry geometry,
            Symbol symbol,
            CrsOperation sourceToDisplay) {}

    private record LayerSnapshot(
            String id,
            List<VisualFeature> features,
            Optional<RasterSnapshot> raster,
            boolean sourceBacked,
            boolean sourceAvailable) {}

    private record RasterSnapshot(
            BufferedImage image,
            Envelope mapBounds,
            Optional<RasterGridPlacement> placement,
            RasterWindow window,
            Envelope clipMapBounds,
            RasterRenderOptions options) {}

    private record ViewContentSnapshot(List<LayerSnapshot> layers) {
        private boolean allSourcesAvailable() {
            for (LayerSnapshot layer : layers) {
                if (layer.sourceBacked() && !layer.sourceAvailable()) {
                    return false;
                }
            }
            return true;
        }
    }

    private record ResolvedFeatureBinding(
            CrsOperation sourceToDisplay, CrsOperation displayToSource) {}

    private record CandidateBindings(Map<MapLayerBinding, ResolvedFeatureBinding> resolved) {}

    private record CoordinateConfiguration(
            CrsRegistry registry, CrsDefinition mapCrs, CrsDefinition displayCrs) {}
}
