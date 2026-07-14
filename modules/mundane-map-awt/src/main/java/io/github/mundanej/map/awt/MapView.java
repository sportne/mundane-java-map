package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureOverlaySymbols;
import io.github.mundanej.map.api.FeatureSelection;
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
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HatchLayouts;
import io.github.mundanej.map.core.HatchSegments;
import io.github.mundanej.map.core.LineEndpointBearings;
import io.github.mundanej.map.core.LineTangents;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapToolRouter;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.RouteOutcome;
import io.github.mundanej.map.core.ScreenGeometryHits;
import io.github.mundanej.map.core.SymbolTransforms;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
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
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import javax.swing.JComponent;

/**
 * A lightweight Swing map component for projected vector features.
 *
 * <p>Mutation and listener callbacks follow the normal Swing event-dispatch-thread contract. Swing
 * serialization is inherited for framework compatibility and is not a persistence format.
 */
@SuppressWarnings({"deprecation", "serial"})
public final class MapView extends JComponent {
    /** Default logical-pixel tolerance for click selection. */
    public static final double DEFAULT_SELECTION_TOLERANCE_PIXELS = 4.0;

    /** Default logical-pixel tolerance for pointer hover. */
    public static final double DEFAULT_HOVER_TOLERANCE_PIXELS = 4.0;

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final double ZOOM_STEP = 1.2;

    private final CrsRegistry crsRegistry;
    private final CrsDefinition mapCrs;
    private final CrsDefinition displayCrs;
    private final CrsOperation mapToDisplay;
    private final CrsOperation displayToMap;
    private final SymbolRendererRegistry symbolRenderers;
    private final MapToolRouter toolRouter = new MapToolRouter();
    private final List<MapPointerListener> pointerListeners = new ArrayList<>();
    private final List<MapHoverListener> hoverListeners = new ArrayList<>();
    private final List<MapSelectionListener> selectionListeners = new ArrayList<>();
    private final Deque<InteractionNotification> interactionNotifications = new ArrayDeque<>();
    private List<Layer> layers = List.of();
    private Optional<FeatureSelection> selection = Optional.empty();
    private Optional<MapHit> hover = Optional.empty();
    private Optional<HoverProbe> hoverProbe = Optional.empty();
    private Optional<AwtLogicalPaintPresence> hoverPaintState = Optional.empty();
    private Optional<AwtLogicalPaintPresence> selectionPaintState = Optional.empty();
    private FeatureOverlaySymbols hoverOverlay = FeatureOverlaySymbols.defaultHover();
    private FeatureOverlaySymbols selectionOverlay = FeatureOverlaySymbols.defaultSelection();
    private boolean drainingInteractionNotifications;
    private MapViewport viewport = MapViewport.initial(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point dragAnchor;
    private double lastPointerX = DEFAULT_WIDTH / 2.0;
    private double lastPointerY = DEFAULT_HEIGHT / 2.0;
    private Set<MapPointerButton> lastButtonsDown = Set.of();
    private Set<MapInputModifier> lastModifiers = Set.of();
    private long toolEventSequence;

    /** Creates an empty view using the supplied source-to-world projection. */
    public MapView(Projection projection) {
        this(projection, SymbolRendererRegistry.builtIn());
    }

    /** Creates an empty view using an explicit immutable symbol renderer registry. */
    public MapView(Projection projection, SymbolRendererRegistry symbolRenderers) {
        this(configuration(projection), symbolRenderers);
    }

    /** Creates an empty view with explicit map and display coordinate reference systems. */
    public MapView(CrsRegistry crsRegistry, CrsDefinition mapCrs, CrsDefinition displayCrs) {
        this(crsRegistry, mapCrs, displayCrs, SymbolRendererRegistry.builtIn());
    }

    /** Creates an explicitly configured view and symbol-renderer registry. */
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
                symbolRenderers);
    }

    private MapView(CoordinateConfiguration configuration, SymbolRendererRegistry symbolRenderers) {
        this.crsRegistry = configuration.registry();
        this.mapCrs = configuration.mapCrs();
        this.displayCrs = configuration.displayCrs();
        this.mapToDisplay = crsRegistry.operation(mapCrs, displayCrs);
        this.displayToMap = crsRegistry.operation(displayCrs, mapCrs);
        this.symbolRenderers = Objects.requireNonNull(symbolRenderers, "symbolRenderers");
        setOpaque(true);
        setFocusable(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        installInteraction();
    }

    /** Returns the map-coordinate CRS used by legacy layers and pointer values. */
    public CrsDefinition mapCrs() {
        return mapCrs;
    }

    /** Returns the world/display CRS used by the viewport. */
    public CrsDefinition displayCrs() {
        return displayCrs;
    }

    /** Installs one active tool, replacing a distinct active instance by identity. */
    public void setActiveTool(MapTool tool) {
        Objects.requireNonNull(tool, "tool");
        ToolContextSnapshot context = toolContextSnapshot();
        try {
            applyToolOutcome(
                    toolRouter.setActiveTool(
                            tool,
                            cancelEvent(MapToolCancelReason.TOOL_REPLACED, context),
                            context));
        } catch (RuntimeException | Error failure) {
            dragAnchor = null;
            applyCurrentToolCursor();
            throw failure;
        } finally {
            dragAnchor = null;
        }
    }

    /** Clears the active tool, if present. */
    public void clearActiveTool() {
        ToolContextSnapshot context = toolContextSnapshot();
        try {
            applyToolOutcome(
                    toolRouter.clearActiveTool(
                            cancelEvent(MapToolCancelReason.TOOL_CLEARED, context), context));
        } catch (RuntimeException | Error failure) {
            dragAnchor = null;
            applyCurrentToolCursor();
            throw failure;
        } finally {
            dragAnchor = null;
        }
    }

    /** Returns the active tool, if any. */
    public Optional<MapTool> activeTool() {
        return toolRouter.activeTool();
    }

    /** Replaces the ordered layer snapshot and repaints the component. */
    public void setLayers(List<Layer> layers) {
        List<Layer> candidate = List.copyOf(Objects.requireNonNull(layers, "layers"));
        ViewContentSnapshot snapshot = captureContent(candidate);
        Optional<FeatureSelection> reconciled = reconcile(selection, snapshot);
        this.layers = candidate;
        hoverProbe = Optional.empty();
        hoverPaintState = Optional.empty();
        selectionPaintState = Optional.empty();
        boolean interactionChanged = transitionInteraction(reconciled, Optional.empty(), true);
        if (!interactionChanged) {
            repaint();
        }
    }

    /** Returns the ordered layer snapshot. */
    public List<Layer> layers() {
        return layers;
    }

    /** Returns the selected stable feature identity after reconciling current content. */
    public Optional<FeatureSelection> selection() {
        ViewContentSnapshot snapshot = captureContent(layers);
        reconcileInteraction(snapshot, true, true);
        return selection;
    }

    /** Selects an identity that exists uniquely in the current content snapshot. */
    public void setSelection(FeatureSelection requested) {
        Objects.requireNonNull(requested, "requested");
        ViewContentSnapshot snapshot = captureContent(layers);
        if (!contains(snapshot, requested)) {
            throw new IllegalArgumentException("selection must identify a current feature");
        }
        transitionInteraction(Optional.of(requested), hover, true);
    }

    /** Clears selection without consulting potentially mutable layer content. */
    public void clearSelection() {
        transitionInteraction(Optional.empty(), hover, true);
    }

    /** Returns the current stable hover identity after reconciling current content. */
    public Optional<MapHit> hover() {
        ViewContentSnapshot snapshot = captureContent(layers);
        reconcileInteraction(snapshot, true, true);
        return hover;
    }

    /** Adds a hover listener; duplicate instances receive duplicate callbacks. */
    public void addMapHoverListener(MapHoverListener listener) {
        hoverListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes the first identical hover-listener registration. */
    public void removeMapHoverListener(MapHoverListener listener) {
        removeIdentical(hoverListeners, listener);
    }

    /** Adds a selection listener; duplicate instances receive duplicate callbacks. */
    public void addMapSelectionListener(MapSelectionListener listener) {
        selectionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes the first identical selection-listener registration. */
    public void removeMapSelectionListener(MapSelectionListener listener) {
        removeIdentical(selectionListeners, listener);
    }

    /** Returns the immutable hover overlay symbol bundle. */
    public FeatureOverlaySymbols hoverOverlaySymbols() {
        return hoverOverlay;
    }

    /** Replaces the hover overlay symbols and repaints on a real change. */
    public void setHoverOverlaySymbols(FeatureOverlaySymbols overlay) {
        FeatureOverlaySymbols requested = Objects.requireNonNull(overlay, "overlay");
        if (!hoverOverlay.equals(requested)) {
            hoverOverlay = requested;
            repaint();
        }
    }

    /** Returns the immutable selection overlay symbol bundle. */
    public FeatureOverlaySymbols selectionOverlaySymbols() {
        return selectionOverlay;
    }

    /** Replaces the selection overlay symbols and repaints on a real change. */
    public void setSelectionOverlaySymbols(FeatureOverlaySymbols overlay) {
        FeatureOverlaySymbols requested = Objects.requireNonNull(overlay, "overlay");
        if (!selectionOverlay.equals(requested)) {
            selectionOverlay = requested;
            repaint();
        }
    }

    /** Returns visible feature hits in deterministic topmost-first paint order. */
    public MapHitResults hitTest(double screenX, double screenY, double tolerancePixels) {
        if (!Double.isFinite(screenX)
                || !Double.isFinite(screenY)
                || !Double.isFinite(tolerancePixels)
                || tolerancePixels < 0.0) {
            throw new IllegalArgumentException(
                    "Hit coordinates and non-negative tolerance must be finite");
        }
        Dimension size = effectiveSize();
        ViewContentSnapshot snapshot = captureContent(layers);
        reconcileInteraction(snapshot, true, true);
        if (screenX < 0.0 || screenX >= size.width || screenY < 0.0 || screenY >= size.height) {
            return MapHitResults.of(List.of());
        }
        double cappedTolerance = Math.min(tolerancePixels, Math.hypot(size.width, size.height));
        MapViewport viewportSnapshot = viewport();
        return hitTestSnapshot(snapshot, viewportSnapshot, size, screenX, screenY, cappedTolerance);
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
                Feature feature = layer.features().get(featureIndex);
                if (hitFeature(
                        feature, viewportSnapshot, clip, screenX, screenY, tolerancePixels)) {
                    hits.add(new MapHit(layer.id(), feature.id()));
                }
            }
        }
        return MapHitResults.of(hits);
    }

    /** Returns the current viewport, resized to the component's current dimensions. */
    public MapViewport viewport() {
        synchronizeViewportSize();
        return viewport;
    }

    /** Replaces the viewport state and repaints the component. */
    public void setViewport(MapViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        if (!clearHover()) {
            repaint();
        }
    }

    /** Fits all non-empty layers using the requested screen-pixel padding. */
    public void fitToData(double paddingPixels) {
        Envelope projected = null;
        for (Layer layer : layers) {
            if (layer.envelope().isPresent()) {
                Envelope next =
                        mapToDisplay.transformEnvelopeStrict(layer.envelope().orElseThrow());
                projected = projected == null ? next : projected.union(next);
            }
        }
        if (projected != null) {
            Dimension size = effectiveSize();
            viewport = MapViewport.fit(size.width, size.height, projected, paddingPixels);
            if (!clearHover()) {
                repaint();
            }
        }
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

    /** Converts a screen location into map coordinates when it lies in the inverse domain. */
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

    /** Converts a map coordinate into screen coordinates when it is representable. */
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

    /** Adds a pointer listener. Duplicate instances receive duplicate callbacks. */
    public void addMapPointerListener(MapPointerListener listener) {
        pointerListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes one matching pointer-listener instance. */
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
        ViewContentSnapshot content = captureContent(layers);
        boolean interactionChanged = reconcileInteraction(content, false, false);
        MapViewport viewportSnapshot = viewport;
        MapScreenBasis basisSnapshot = screenBasis(viewportSnapshot);
        Optional<MapHit> hoverSnapshot = hover;
        Optional<FeatureSelection> selectionSnapshot = selection;
        OverlayCandidate hoverCandidate = null;
        OverlayCandidate selectionCandidate = null;
        boolean paintStateChanged = false;
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        try {
            if (isOpaque()) {
                graphics2D.setColor(getBackground());
                graphics2D.fillRect(0, 0, getWidth(), getHeight());
            }
            graphics2D.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (LayerSnapshot layer : content.layers()) {
                for (Feature feature : layer.features()) {
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
        } catch (RuntimeException failure) {
            runtimeFailure = failure;
        } catch (Error failure) {
            errorFailure = failure;
        } finally {
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
        if (errorFailure != null) {
            throw errorFailure;
        }
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
    }

    private static void suppressDistinct(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }

    private SymbolRenderResult renderFeature(
            Graphics2D graphics,
            Feature feature,
            MapViewport viewportSnapshot,
            MapScreenBasis basisSnapshot) {
        Symbol symbol = feature.symbol();
        if (!(symbol instanceof FeatureStyle)
                && symbol.role() != geometryRole(feature.geometry())) {
            throw roleMismatch(feature, new SymbolSnapshot(symbol.role(), symbol.rendererKey()));
        }
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(toScreen(point.coordinate(), viewportSnapshot))
                        : Optional.empty();
        AwtSymbolRenderContext context =
                context(
                        graphics,
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
                        1.0,
                        false,
                        OptionalDouble.empty(),
                        markerAnchor,
                        viewportSnapshot,
                        basisSnapshot);
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
        Feature feature = candidate.feature();
        Symbol symbol = overlaySymbol(overlay, feature.geometry());
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(toScreen(point.coordinate(), viewportSnapshot))
                        : Optional.empty();
        dispatch(
                symbol,
                context(
                        graphics,
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
                        1.0,
                        false,
                        OptionalDouble.empty(),
                        markerAnchor,
                        viewportSnapshot,
                        basisSnapshot));
    }

    private static Symbol overlaySymbol(FeatureOverlaySymbols overlay, Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return overlay.marker();
        }
        if (geometry instanceof LineStringGeometry) {
            return overlay.line();
        }
        if (geometry instanceof PolygonGeometry) {
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
                mapToDisplay,
                viewportSnapshot,
                inheritedOpacity,
                closedRing,
                endpointBearing,
                markerAnchor,
                basisSnapshot,
                this);
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
            Feature feature,
            MapViewport viewportSnapshot,
            Rectangle2D clip,
            double queryX,
            double queryY,
            double tolerance) {
        Symbol symbol = feature.symbol();
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(toScreen(point.coordinate(), viewportSnapshot))
                        : Optional.empty();
        AwtSymbolHitContext context =
                hitContext(
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
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
            ScreenPolygon screenPolygon = screenPolygon(polygon, context.viewport());
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

    private boolean hitLegacy(FeatureStyle style, AwtSymbolHitContext context) {
        Geometry geometry = context.renderGeometry();
        if (geometry instanceof PointGeometry point) {
            Coordinate screen = toScreen(point.coordinate(), context.viewport());
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
            CoordinateSequence screen = toScreen(line.coordinates(), context.viewport());
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
        ScreenPolygon screenPolygon = screenPolygon(polygon, context.viewport());
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
        CoordinateSequence screen = toScreen(geometry.coordinates(), context.viewport());
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
        ScreenPolygon screenPolygon = screenPolygon(polygon, context.viewport());
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
                context(
                        parent.parentGraphics(),
                        parent.role(),
                        parent.featureId(),
                        parent.featureGeometry(),
                        parent.renderGeometry(),
                        parent.inheritedOpacity() * multiplier,
                        parent.closedRing(),
                        parent.endpointBearingDegrees(),
                        parent.markerAnchorScreen(),
                        parent.viewport(),
                        parent.mapScreenBasis());
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
        if (value instanceof FeatureStyle style) {
            Rectangle2D bounds =
                    renderLegacyFeature(
                            context.parentGraphics(),
                            context.renderGeometry(),
                            style,
                            context.featureId(),
                            context.viewport());
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
                && context.renderGeometry() instanceof LineStringGeometry) {
            return SymbolRenderResult.none(renderRegisteredLine(line, context));
        }
        if ((value instanceof SolidFillSymbol || value instanceof HatchFillSymbol)
                && context.renderGeometry() instanceof PolygonGeometry) {
            return SymbolRenderResult.none(renderRegisteredFill(value, context));
        }
        throw rendererValueMismatch(new SymbolSnapshot(value.role(), value.rendererKey()));
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
                    image.setRGB(x, y, (rgba << 24) | ((rgba >>> 8) & 0x00ff_ffff));
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

    private AwtLogicalPaintPresence legacyPresence(
            FeatureStyle style, AwtSymbolRenderContext context) {
        if (context.renderGeometry() instanceof PointGeometry) {
            return style.fill().alpha() > 0
                            || (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0)
                    ? AwtLogicalPaintPresence.PRESENT
                    : AwtLogicalPaintPresence.EMPTY;
        }
        if (context.renderGeometry() instanceof LineStringGeometry line) {
            CoordinateSequence screen = toScreen(line.coordinates(), context.viewport());
            LineEndpointBearings bearings =
                    LineTangents.outwardScreenBearings(screen, context.featureId(), 0);
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
            MapViewport viewportSnapshot) {
        graphics.setStroke(
                new BasicStroke(
                        (float) style.strokeWidth(),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));

        if (geometry instanceof PointGeometry point) {
            return renderLegacyPoint(graphics, point, style, viewportSnapshot);
        } else if (geometry instanceof LineStringGeometry line) {
            if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
                CoordinateSequence screen = toScreen(line.coordinates(), viewportSnapshot);
                LineEndpointBearings bearings =
                        LineTangents.outwardScreenBearings(screen, featureId, 0);
                if (bearings.startBearingDegrees().isEmpty()
                        && bearings.endBearingDegrees().isEmpty()) {
                    return null;
                }
                Path2D path = screenPath(screen, false);
                graphics.setColor(color(style.stroke()));
                graphics.draw(path);
            }
        } else if (geometry instanceof PolygonGeometry polygon) {
            renderPolygon(graphics, polygon, style, viewportSnapshot);
        }
        return null;
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
        VectorPath2D.Converted converted = VectorPath2D.convert(marker.path());
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
        LineStringGeometry geometry = (LineStringGeometry) context.renderGeometry();
        CoordinateSequence screen = toScreen(geometry.coordinates(), context.viewport());
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(screen, context.featureId(), 0);
        if (bearings.startBearingDegrees().isEmpty() && bearings.endBearingDegrees().isEmpty()) {
            return AwtLogicalPaintPresence.EMPTY;
        }
        double opacity = context.inheritedOpacity() * line.opacity();
        AwtLogicalPaintPresence presence =
                opacity > 0.0 && line.stroke().color().alpha() > 0
                        ? AwtLogicalPaintPresence.PRESENT
                        : AwtLogicalPaintPresence.EMPTY;
        paintLinePart(
                context.parentGraphics(), screen, line.stroke(), context.mapScreenBasis(), opacity);
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
                                            geometry.coordinates().coordinate(0),
                                            screen.coordinate(0),
                                            bearings.startBearingDegrees().orElseThrow())
                                    .paintPresence());
        }
        if (line.endMarker().isPresent() && bearings.endBearingDegrees().isPresent()) {
            int last = geometry.coordinates().size() - 1;
            presence =
                    unionPresence(
                            presence,
                            dispatchEndpoint(
                                            line.endMarker().orElseThrow(),
                                            context,
                                            opacity,
                                            geometry.coordinates().coordinate(last),
                                            screen.coordinate(last),
                                            bearings.endBearingDegrees().orElseThrow())
                                    .paintPresence());
        }
        return presence;
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
        if (opacity == 0.0 || stroke.color().alpha() == 0) {
            return;
        }
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            child.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
            child.setStroke(screenStroke(stroke, basis));
            child.setColor(color(stroke.color()));
            child.draw(screenPath(part, false));
        } finally {
            child.dispose();
        }
    }

    private AwtLogicalPaintPresence renderRegisteredFill(
            Symbol symbol, AwtSymbolRenderContext context) {
        PolygonGeometry geometry = (PolygonGeometry) context.renderGeometry();
        ScreenPolygon polygon = screenPolygon(geometry, context.viewport());
        if (symbol instanceof SolidFillSymbol fill) {
            double opacity = context.inheritedOpacity() * fill.opacity();
            AwtLogicalPaintPresence presence =
                    opacity > 0.0 && fill.fill().alpha() > 0
                            ? AwtLogicalPaintPresence.PRESENT
                            : AwtLogicalPaintPresence.EMPTY;
            if (opacity > 0.0 && fill.fill().alpha() > 0) {
                Graphics2D child = context.createGraphics();
                try {
                    child.setComposite(
                            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                    child.setColor(color(fill.fill()));
                    child.fill(polygon.path());
                } finally {
                    child.dispose();
                }
            }
            if (fill.outline().isPresent()) {
                presence =
                        unionPresence(
                                presence,
                                renderRegisteredOutline(
                                        fill.outline().orElseThrow(), geometry, context, opacity));
            }
            return presence;
        } else if (symbol instanceof HatchFillSymbol hatch) {
            double opacity = context.inheritedOpacity() * hatch.opacity();
            AwtLogicalPaintPresence presence =
                    paintHatch(
                                    context.parentGraphics(),
                                    hatch,
                                    polygon,
                                    context.mapScreenBasis(),
                                    context.viewport(),
                                    context.featureId(),
                                    opacity)
                            ? AwtLogicalPaintPresence.PRESENT
                            : AwtLogicalPaintPresence.EMPTY;
            if (hatch.outline().isPresent()) {
                presence =
                        unionPresence(
                                presence,
                                renderRegisteredOutline(
                                        hatch.outline().orElseThrow(), geometry, context, opacity));
            }
            return presence;
        }
        throw new IllegalArgumentException("Unsupported built-in fill symbol");
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

    private boolean paintHatch(
            Graphics2D graphics,
            HatchFillSymbol hatch,
            ScreenPolygon polygon,
            MapScreenBasis basis,
            MapViewport viewportSnapshot,
            String featureId,
            double opacity) {
        if (opacity == 0.0 || hatch.stroke().color().alpha() == 0) {
            return false;
        }
        Rectangle2D work =
                polygon.path()
                        .getBounds2D()
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
            child.clip(polygon.path());
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

    private void renderPolygon(
            Graphics2D graphics,
            PolygonGeometry polygon,
            FeatureStyle style,
            MapViewport viewportSnapshot) {
        Path2D path = screenPolygon(polygon, viewportSnapshot).path();
        if (style.fill().alpha() > 0) {
            graphics.setColor(color(style.fill()));
            graphics.fill(path);
        }
        if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
            graphics.setColor(color(style.stroke()));
            graphics.draw(path);
        }
    }

    private CoordinateSequence toScreen(CoordinateSequence source, MapViewport viewportSnapshot) {
        double[] ordinates = new double[source.size() * 2];
        for (int index = 0; index < source.size(); index++) {
            Coordinate screen = toScreen(source.coordinate(index), viewportSnapshot);
            ordinates[index * 2] = screen.x();
            ordinates[index * 2 + 1] = screen.y();
        }
        return CoordinateSequence.of(ordinates);
    }

    private ScreenPolygon screenPolygon(PolygonGeometry polygon, MapViewport viewportSnapshot) {
        List<CoordinateSequence> rings = new ArrayList<>();
        rings.add(toScreen(polygon.exterior(), viewportSnapshot));
        for (CoordinateSequence hole : polygon.holes()) {
            rings.add(toScreen(hole, viewportSnapshot));
        }
        Path2D screenPath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (CoordinateSequence ring : rings) {
            appendScreen(screenPath, ring, true);
        }
        return new ScreenPolygon(screenPath, List.copyOf(rings));
    }

    private static MapScreenBasis screenBasis(MapViewport viewportSnapshot) {
        double screenPixelsPerMapUnit = 1.0 / viewportSnapshot.worldUnitsPerPixel();
        return MapScreenBasis.of(
                new Coordinate(screenPixelsPerMapUnit, 0.0),
                new Coordinate(0.0, -screenPixelsPerMapUnit));
    }

    private static Path2D screenPath(CoordinateSequence coordinates, boolean close) {
        Path2D result = new Path2D.Double();
        appendScreen(result, coordinates, close);
        return result;
    }

    private static void appendScreen(Path2D path, CoordinateSequence coordinates, boolean close) {
        path.moveTo(coordinates.x(0), coordinates.y(0));
        for (int index = 1; index < coordinates.size(); index++) {
            path.lineTo(coordinates.x(index), coordinates.y(index));
        }
        if (close) {
            path.closePath();
        }
    }

    Coordinate toScreen(Coordinate source, MapViewport viewportSnapshot) {
        return viewportSnapshot.worldToScreen(mapToDisplay.transform(source));
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
        if (geometry instanceof PointGeometry) {
            return SymbolRole.MARKER;
        }
        if (geometry instanceof LineStringGeometry) {
            return SymbolRole.LINE;
        }
        if (geometry instanceof PolygonGeometry) {
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

    private static SymbolException roleMismatch(Feature feature, SymbolSnapshot symbolSnapshot) {
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
        ViewContentSnapshot content = captureContent(layers);
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
        hoverProbe = Optional.of(new HoverProbe(event.getX(), event.getY()));
        transitionInteraction(reconciledSelection, nextHover, true);
    }

    private void updateClickInteraction(MouseEvent event) {
        synchronizeViewportSize();
        Dimension size = effectiveSize();
        ViewContentSnapshot content = captureContent(layers);
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
            RouteOutcome outcome = toolRouter.route(converted, context);
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
            RouteOutcome outcome = toolRouter.route(converted, context);
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
            applyToolOutcome(toolRouter.cancelInteraction(cancel, context));
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

    private static ViewContentSnapshot captureContent(List<Layer> sourceLayers) {
        List<LayerSnapshot> captured = new ArrayList<>(sourceLayers.size());
        Set<String> layerIds = new HashSet<>();
        for (Layer layer : sourceLayers) {
            Objects.requireNonNull(layer, "layer");
            String layerId = requireContentId(layer.id(), "layer.id");
            if (!layerIds.add(layerId)) {
                throw new IllegalArgumentException("layer.id must be unique: " + layerId);
            }
            List<Feature> features =
                    List.copyOf(Objects.requireNonNull(layer.features(), "layer.features"));
            Set<String> featureIds = new HashSet<>();
            for (Feature feature : features) {
                Objects.requireNonNull(feature, "feature");
                String featureId = requireContentId(feature.id(), "feature.id");
                if (!featureIds.add(featureId)) {
                    throw new IllegalArgumentException(
                            "feature.id must be unique within layer " + layerId + ": " + featureId);
                }
            }
            captured.add(new LayerSnapshot(layerId, features));
        }
        return new ViewContentSnapshot(List.copyOf(captured));
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
                return layer.features().stream()
                        .anyMatch(feature -> feature.id().equals(requested.featureId()));
            }
        }
        return false;
    }

    private static boolean contains(ViewContentSnapshot snapshot, MapHit requested) {
        return contains(snapshot, new FeatureSelection(requested.layerId(), requested.featureId()));
    }

    private boolean reconcileInteraction(
            ViewContentSnapshot snapshot, boolean repaintOnChange, boolean drainNotifications) {
        Optional<FeatureSelection> reconciledSelection = reconcile(selection, snapshot);
        Optional<MapHit> reconciledHover = hover.filter(hit -> contains(snapshot, hit));
        if (hoverProbe.isPresent()) {
            HoverProbe probe = hoverProbe.orElseThrow();
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

    private record OverlayCandidate(Feature feature, AwtLogicalPaintPresence presence) {}

    private record InteractionNotification(
            MapSelectionEvent selectionEvent, MapHoverEvent hoverEvent) {
        private static InteractionNotification selection(MapSelectionEvent event) {
            return new InteractionNotification(Objects.requireNonNull(event, "event"), null);
        }

        private static InteractionNotification hover(MapHoverEvent event) {
            return new InteractionNotification(null, Objects.requireNonNull(event, "event"));
        }
    }

    private record LayerSnapshot(String id, List<Feature> features) {}

    private record ViewContentSnapshot(List<LayerSnapshot> layers) {}

    private record CoordinateConfiguration(
            CrsRegistry registry, CrsDefinition mapCrs, CrsDefinition displayCrs) {}
}
