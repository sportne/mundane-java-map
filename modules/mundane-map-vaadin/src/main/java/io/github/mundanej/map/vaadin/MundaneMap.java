package io.github.mundanej.map.vaadin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureOverlaySymbols;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Layer;
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
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.api.VectorExportSnapshotLimits;
import io.github.mundanej.map.api.VectorExportSnapshotProblem;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapToolRouter;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.RouteOutcome;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A Vaadin Flow component that paints bounded toolkit-neutral vectors on a local Canvas.
 *
 * <p>All high-frequency navigation runs in the bundled custom element. Java receives only settled,
 * generation-checked viewport values. Snapshot layers and explicitly bound feature sources accept
 * all six Level 1 geometry families and the bounded built-in vector profile: vector markers, solid
 * lines with endpoint markers, solid and hatch fills, outlines, and role-homogeneous composites.
 * Bounded point labels use the bundled Canvas closed-font measurement handshake. Explicit-catalog
 * raster icons are served as expiring same-origin session resources. A settled acknowledged vector
 * scene can be captured through the existing detached export snapshot boundary; legacy and custom
 * renderer values are not forwarded to the browser.
 */
@Tag("mundane-map-canvas")
@JsModule("./mundane-map-canvas.js")
@SuppressWarnings("serial")
public final class MundaneMap extends Component implements HasSize, HasEnabled, AutoCloseable {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

    /** Default logical-pixel tolerance used by primary-click selection. */
    public static final double DEFAULT_SELECTION_TOLERANCE_PIXELS = 4.0;

    /** Default logical-pixel tolerance used by bounded hover probes. */
    public static final double DEFAULT_HOVER_TOLERANCE_PIXELS = 4.0;

    /** The single bounded private wire encoder. */
    private final SceneProtocol protocol;

    /** Monotonic clock used by the authoritative settled-event bucket. */
    private final LongSupplier nanoTime;

    /** Settled-viewport listeners in deterministic registration order. */
    private final List<Consumer<MapViewport>> viewportListeners = new ArrayList<>();

    /** Adapter-owned immutable scene layers. */
    private List<Layer> layers = List.of();

    /** Installed feature bindings in deterministic browser layer order. */
    private List<FeatureSourceQueryEngine.RequestBinding> featureBindings = List.of();

    /** Latest atomically accepted transformed source layers. */
    private List<Layer> sourceLayers = List.of();

    /** Latest non-empty source reports by binding identity. */
    private Map<String, DiagnosticReport> sourceReports = Map.of();

    /** Source report listeners in deterministic registration order. */
    private final List<MapSourceReportListener> sourceReportListeners = new ArrayList<>();

    /** Source report events waiting until the owning state transition has completed. */
    private final ArrayDeque<MapSourceReportEvent> sourceReportNotifications = new ArrayDeque<>();

    /** Prevents a reentrant listener callback from recursively draining notifications. */
    private boolean drainingSourceReportNotifications;

    /** Explicit registry used for source, map, and display operations. */
    private CrsRegistry crsRegistry = CrsRegistry.level1();

    /** Logical map CRS between source and display operations. */
    private CrsDefinition mapCrs = CrsDefinitions.EPSG_3857;

    /** CRS of browser viewport and encoded scene coordinates. */
    private CrsDefinition displayCrs = CrsDefinitions.EPSG_3857;

    /** Pure synchronous feature query engine executed only on the serialized lane. */
    private final FeatureSourceQueryEngine sourceQueryEngine = new FeatureSourceQueryEngine();

    /** Session-owned immutable icon resources for the currently published scene. */
    private IconResourceBatch iconResources = IconResourceBatch.empty();

    /** Session access shared by resource registration and destruction cleanup wiring. */
    private final IconSessionAccess iconSessionAccess;

    /** Removes the current session cleanup callback on detach or close. */
    private Registration iconSessionCleanup;

    /** Immutable layers and candidates belonging to the currently published scene. */
    private List<Layer> currentSceneLayers = List.of();

    /** Server-selected point-label candidates belonging to the currently published scene. */
    private List<SceneLabelCandidate> currentLabelCandidates = List.of();

    /** Current generation waiting for closed-font browser measurements. */
    private PendingLabelMeasurements pendingLabelMeasurements;

    /** Current server placements waiting for browser acceptance acknowledgement. */
    private PendingPlacedLabels pendingPlacedLabels;

    /** Last browser-acknowledged immutable scene state eligible for vector capture. */
    private BrowserCaptureState browserCaptureState;

    /** Per-component serialized source-query executor. */
    private final Executor queryExecutor;

    /** Executor service owned by this component, absent for injected deterministic executors. */
    private final ExecutorService ownedQueryExecutor;

    /** Dispatches staged query completions to the owning UI or a deterministic test lane. */
    private final Consumer<Runnable> queryCompletionDispatcher;

    /** Whether injected tests explicitly allow queries while unattached. */
    private final boolean queryWhenDetached;

    /** Current cancellable source query. */
    private CancellationSource activeQueryCancellation = new CancellationSource();

    /** Monotonic source-query generation. */
    private long queryGeneration;

    /** Accepted Canvas background. */
    private Rgba background = Rgba.rgb(255, 255, 255);

    /** Latest accepted finite viewport. */
    private MapViewport viewport = MapViewport.initial(DEFAULT_WIDTH, DEFAULT_HEIGHT);

    /** Aggregate scene bounds retained for fit operations. */
    private Optional<Envelope> sceneEnvelope = Optional.empty();

    /** Latest stable adapter or client diagnostic. */
    private Optional<MundaneMapException> diagnostic = Optional.empty();

    /** Current attach lifecycle generation. */
    private long componentGeneration;

    /** Current accepted full-scene generation. */
    private long sceneGeneration;

    /** Current accepted viewport generation. */
    private long viewportGeneration;

    /** Latest accepted strictly increasing browser event sequence. */
    private long clientEventSequence = -1;

    /** Next sequence authorized for one transient PASS-navigation viewport synchronization. */
    private long transientViewportPermitSequence = -1;

    /** One toolkit-neutral router owned by this component. */
    private final MapToolRouter toolRouter = new MapToolRouter();

    /** Strict server-assigned sequence shared by tool events and commands. */
    private long toolEventSequence;

    /** Last validated pointer state used for lifecycle cancellation. */
    private double lastPointerX;

    /** Last validated pointer y ordinate. */
    private double lastPointerY;

    /** Last validated post-event button set. */
    private Set<MapPointerButton> lastButtonsDown = Set.of();

    /** Last validated modifier set. */
    private Set<MapInputModifier> lastModifiers = Set.of();

    /** Current reconciled interaction identities. */
    private Optional<FeatureSelection> selection = Optional.empty();

    /** Current reconciled topmost hover identity. */
    private Optional<MapHit> hover = Optional.empty();

    /** Current ordinary-scene identities with at least one encoded paint primitive. */
    private Set<FeatureSelection> paintedFeatures = Set.of();

    /** Interaction listeners in deterministic registration order. */
    private final List<MapPointerListener> pointerListeners = new ArrayList<>();

    /** Hover listeners in deterministic registration order. */
    private final List<MapHoverListener> hoverListeners = new ArrayList<>();

    /** Selection listeners in deterministic registration order. */
    private final List<MapSelectionListener> selectionListeners = new ArrayList<>();

    /** Queued interaction transitions awaiting deterministic delivery. */
    private final ArrayDeque<InteractionNotification> interactionNotifications = new ArrayDeque<>();

    /** Prevents recursive interaction-notification draining. */
    private boolean drainingInteractionNotifications;

    /** Browser-supported overlay symbol bundles. */
    private FeatureOverlaySymbols hoverOverlay = FeatureOverlaySymbols.defaultHover();

    /** Browser-supported selection overlay symbol bundle. */
    private FeatureOverlaySymbols selectionOverlay = FeatureOverlaySymbols.defaultSelection();

    /** Authoritative twenty-per-second hover bucket and newest coalesced probe. */
    private double hoverTokens = 20.0;

    /** Last monotonic hover-bucket refill sample. */
    private long hoverRefillNanos;

    /** Authoritative 120-per-second non-coalescible tool-pointer bucket. */
    private double toolPointerTokens = 120.0;

    /** Last monotonic tool-pointer-bucket refill sample. */
    private long toolPointerRefillNanos;

    /** Whether tool-pointer input is ignored pending the client's cancellation acknowledgement. */
    private boolean toolPointerRateQuarantined;

    /** Newest hover probe waiting for an authoritative bucket token. */
    private HoverProbe pendingHoverProbe;

    /** Whether a pending hover flush has been scheduled. */
    private boolean hoverFlushScheduled;

    /** Invalidates already-dispatched hover flush callbacks. */
    private long hoverScheduleEpoch;

    /** Authoritative settled-event bucket tokens. */
    private double settledTokens = 10.0;

    /** Last monotonic refill sample. */
    private long settledRefillNanos;

    /** Delayed UI dispatcher used to flush the newest server-side coalesced viewport. */
    private final Consumer<Runnable> settledScheduler;

    /** Newest validated viewport waiting for an authoritative bucket token. */
    private MapViewport pendingSettledViewport;

    /** Whether a pending settled-viewport flush has already been scheduled. */
    private boolean settledFlushScheduled;

    /** Invalidates already-dispatched flush callbacks after lifecycle or server-driven changes. */
    private long settledScheduleEpoch;

    /** Whether terminal cleanup has completed. */
    private boolean closed;

    /** Creates an empty map using the fixed protocol limits and an 800 by 600 logical viewport. */
    public MundaneMap() {
        this(System::nanoTime, null, null, null, null);
    }

    MundaneMap(LongSupplier nanoTime) {
        this(nanoTime, null, null, null, null);
    }

    MundaneMap(LongSupplier nanoTime, Consumer<Runnable> settledScheduler) {
        this(nanoTime, settledScheduler, null, null, null);
    }

    MundaneMap(
            LongSupplier nanoTime,
            Consumer<Runnable> settledScheduler,
            Executor queryExecutor,
            Consumer<Runnable> queryCompletionDispatcher) {
        this(nanoTime, settledScheduler, queryExecutor, queryCompletionDispatcher, null);
    }

    MundaneMap(
            LongSupplier nanoTime,
            Consumer<Runnable> settledScheduler,
            Executor queryExecutor,
            Consumer<Runnable> queryCompletionDispatcher,
            IconSessionAccess iconSessionAccess) {
        protocol = new SceneProtocol(SceneProtocol.DEFAULT_LIMITS);
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.iconSessionAccess =
                iconSessionAccess != null ? iconSessionAccess : IconSessionAccess.vaadin();
        this.settledScheduler =
                settledScheduler != null
                        ? settledScheduler
                        : action ->
                                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                                        .execute(() -> dispatchToUi(action));
        if (queryExecutor == null) {
            ownedQueryExecutor =
                    Executors.newSingleThreadExecutor(
                            action -> {
                                Thread thread = new Thread(action, "mundane-map-source-query");
                                thread.setDaemon(true);
                                return thread;
                            });
            this.queryExecutor = ownedQueryExecutor;
        } else {
            ownedQueryExecutor = null;
            this.queryExecutor = queryExecutor;
        }
        queryWhenDetached = queryCompletionDispatcher != null;
        this.queryCompletionDispatcher =
                queryCompletionDispatcher != null ? queryCompletionDispatcher : this::dispatchToUi;
        settledRefillNanos = nanoTime.getAsLong();
        hoverRefillNanos = settledRefillNanos;
        toolPointerRefillNanos = settledRefillNanos;
        setWidth("100%");
        setHeight("400px");
    }

    /**
     * Atomically validates and replaces the ordered immutable layer snapshot.
     *
     * @param sourceLayers layers in deterministic paint order
     * @throws MundaneMapException if the component is closed or the scene is unsupported or over a
     *     limit
     */
    public void setSnapshotLayers(List<? extends Layer> sourceLayers) {
        requireOpen();
        long nextGeneration = Math.incrementExact(sceneGeneration);
        SceneProtocol.Result snapshot =
                protocol.encode(
                        sourceLayers,
                        background,
                        viewport,
                        componentGeneration,
                        nextGeneration,
                        viewportGeneration);
        validateSnapshotBindingIds(snapshot.layers());
        protocol.encode(
                combine(snapshot.layers(), configuredFeatureLayers(featureSourceBindings())),
                background,
                viewport,
                componentGeneration,
                nextGeneration,
                viewportGeneration);
        StagedScene staged =
                stageScene(
                        combine(snapshot.layers(), this.sourceLayers), background, nextGeneration);
        SceneProtocol.Result result = staged.result();
        layers = snapshot.layers();
        cancelPendingSettledViewport();
        sceneEnvelope = result.envelope();
        sceneGeneration = nextGeneration;
        diagnostic = Optional.empty();
        publishStagedScene(staged);
    }

    /**
     * Returns the adapter-owned immutable layer snapshot.
     *
     * @return immutable ordered layers
     */
    public List<Layer> snapshotLayers() {
        return layers;
    }

    /**
     * Atomically replaces the ordered feature-source bindings and starts a superseding query.
     *
     * <p>Removed owned bindings are closed on the serialized source lane after any live cursor has
     * observed cancellation. Borrowed bindings remain caller-owned.
     *
     * @param bindings unique open bindings in deterministic paint and query order
     */
    public void setFeatureSourceBindings(List<FeatureSourceBinding> bindings) {
        requireOpen();
        List<FeatureSourceBinding> candidates = List.copyOf(bindings);
        validateFeatureBindings(candidates);
        protocol.encode(
                combine(layers, configuredFeatureLayers(candidates)),
                background,
                viewport,
                componentGeneration,
                sceneGeneration,
                viewportGeneration);
        Set<FeatureSourceBinding> retained = identitySet(candidates);
        Set<FeatureSourceBinding> previous = identitySet(featureSourceBindings());
        List<FeatureSourceBinding> newlyAttached = new ArrayList<>();
        try {
            for (FeatureSourceBinding binding : candidates) {
                if (!previous.contains(binding)) {
                    binding.attach(this);
                    newlyAttached.add(binding);
                }
            }
        } catch (RuntimeException | Error failure) {
            newlyAttached.forEach(binding -> binding.detach(this));
            throw failure;
        }
        cancelFeatureQuery();
        IdentityHashMap<FeatureSourceBinding, Boolean> priorVisibility = new IdentityHashMap<>();
        for (FeatureSourceQueryEngine.RequestBinding installed : featureBindings) {
            priorVisibility.put(installed.binding(), installed.visible());
        }
        List<FeatureSourceBinding> removed =
                featureSourceBindings().stream()
                        .filter(binding -> !retained.contains(binding))
                        .toList();
        featureBindings =
                candidates.stream()
                        .map(
                                binding ->
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding,
                                                priorVisibility.getOrDefault(binding, true)))
                        .toList();
        sourceLayers = List.of();
        releaseIconResources();
        long replacementGeneration = Math.incrementExact(sceneGeneration);
        StagedScene replacement = stageScene(layers, background, replacementGeneration);
        sceneEnvelope = replacement.result().envelope();
        sceneGeneration = replacementGeneration;
        publishStagedScene(replacement);
        for (FeatureSourceBinding removedBinding : removed) {
            transitionSourceReport(removedBinding.id(), Optional.empty());
        }
        for (FeatureSourceBinding candidate : candidates) {
            DiagnosticReport opening = candidate.source().openingDiagnostics();
            if (!opening.entries().isEmpty() || opening.omittedWarningCount() != 0) {
                transitionSourceReport(candidate.id(), Optional.of(opening));
            }
        }
        if (!removed.isEmpty()) {
            queryExecutor.execute(() -> releaseBindings(removed));
        }
        scheduleSourceQuery();
        drainSourceReportNotifications();
    }

    /**
     * Returns installed feature-source bindings in paint order.
     *
     * @return immutable binding list
     */
    public List<FeatureSourceBinding> featureSourceBindings() {
        return featureBindings.stream()
                .map(FeatureSourceQueryEngine.RequestBinding::binding)
                .toList();
    }

    /**
     * Changes one installed source layer's visibility and starts a superseding query.
     *
     * @param bindingId installed binding identity
     * @param visible whether records from the binding are painted and queried
     * @throws IllegalArgumentException if no binding has the identity
     */
    public void setFeatureSourceVisible(String bindingId, boolean visible) {
        requireOpen();
        Objects.requireNonNull(bindingId, "bindingId");
        boolean found = false;
        List<FeatureSourceQueryEngine.RequestBinding> replacement = new ArrayList<>();
        for (FeatureSourceQueryEngine.RequestBinding binding : featureBindings) {
            if (binding.binding().id().equals(bindingId)) {
                replacement.add(
                        new FeatureSourceQueryEngine.RequestBinding(binding.binding(), visible));
                found = true;
            } else {
                replacement.add(binding);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown feature-source binding identity");
        }
        featureBindings = List.copyOf(replacement);
        scheduleSourceQuery();
    }

    /**
     * Returns whether one installed source layer is visible.
     *
     * @param bindingId installed binding identity
     * @return current visibility
     * @throws IllegalArgumentException if no binding has the identity
     */
    public boolean isFeatureSourceVisible(String bindingId) {
        Objects.requireNonNull(bindingId, "bindingId");
        return featureBindings.stream()
                .filter(binding -> binding.binding().id().equals(bindingId))
                .findFirst()
                .map(FeatureSourceQueryEngine.RequestBinding::visible)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown feature-source binding identity"));
    }

    /**
     * Replaces the explicit CRS registry and source-to-map-to-display configuration.
     *
     * @param registry immutable explicit registry
     * @param nextMapCrs exact registered logical map CRS
     * @param nextDisplayCrs exact registered browser display CRS
     */
    public void setCoordinateReferenceSystems(
            CrsRegistry registry, CrsDefinition nextMapCrs, CrsDefinition nextDisplayCrs) {
        requireOpen();
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(nextMapCrs, "nextMapCrs");
        Objects.requireNonNull(nextDisplayCrs, "nextDisplayCrs");
        registry.operation(nextMapCrs, nextDisplayCrs);
        registry.operation(nextDisplayCrs, nextMapCrs);
        crsRegistry = registry;
        mapCrs = nextMapCrs;
        displayCrs = nextDisplayCrs;
        scheduleSourceQuery();
    }

    /**
     * Returns the configured logical map CRS.
     *
     * @return exact registered map CRS
     */
    public CrsDefinition mapCrs() {
        return mapCrs;
    }

    /**
     * Returns the configured browser display CRS.
     *
     * @return exact registered display CRS
     */
    public CrsDefinition displayCrs() {
        return displayCrs;
    }

    /**
     * Returns latest non-empty source reports by binding identity.
     *
     * @return immutable insertion-ordered report map
     */
    public Map<String, DiagnosticReport> sourceReports() {
        return sourceReports;
    }

    /**
     * Registers a listener for real source-report transitions.
     *
     * @param listener source report listener
     * @return idempotent removal registration
     */
    public Registration addSourceReportListener(MapSourceReportListener listener) {
        requireOpen();
        Objects.requireNonNull(listener, "listener");
        return Registration.addAndRemove(sourceReportListeners, listener);
    }

    /**
     * Replaces the Canvas background color and republishes the current scene atomically.
     *
     * @param color non-null background color
     */
    public void setBackground(Rgba color) {
        requireOpen();
        Objects.requireNonNull(color, "color");
        long nextGeneration = Math.incrementExact(sceneGeneration);
        StagedScene staged = stageScene(combinedLayers(), color, nextGeneration);
        SceneProtocol.Result result = staged.result();
        background = color;
        cancelPendingSettledViewport();
        sceneEnvelope = result.envelope();
        sceneGeneration = nextGeneration;
        diagnostic = Optional.empty();
        publishStagedScene(staged);
    }

    /**
     * Returns the accepted Canvas background.
     *
     * @return immutable color
     */
    public Rgba background() {
        return background;
    }

    /**
     * Replaces the finite projected-world viewport.
     *
     * @param nextViewport immutable viewport
     */
    public void setViewport(MapViewport nextViewport) {
        requireOpen();
        Objects.requireNonNull(nextViewport, "nextViewport");
        protocol.validateViewport(nextViewport);
        cancelPendingSettledViewport();
        viewport = nextViewport;
        viewportGeneration = Math.incrementExact(viewportGeneration);
        boolean clearedHover = hover.isPresent();
        Throwable primary = null;
        diagnostic = Optional.empty();
        primary = cleanup(primary, this::publishViewport);
        primary = cleanup(primary, () -> transitionInteraction(selection, Optional.empty()));
        if (!clearedHover) {
            primary = cleanup(primary, this::publishInteractionOverlay);
        }
        primary = cleanup(primary, this::scheduleSourceQuery);
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    /**
     * Returns the latest accepted Java-driven or settled browser viewport.
     *
     * @return immutable finite viewport
     */
    public MapViewport viewport() {
        return viewport;
    }

    /**
     * Converts a finite logical-screen sample to the configured map CRS when representable.
     *
     * @param screenX logical-screen x ordinate
     * @param screenY logical-screen y ordinate
     * @return map coordinate, or empty outside the registered inverse domain
     * @throws IllegalArgumentException if either ordinate is non-finite
     */
    public Optional<io.github.mundanej.map.api.Coordinate> screenToMap(
            double screenX, double screenY) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("Screen coordinates must be finite");
        }
        try {
            return Optional.of(
                    crsRegistry
                            .operation(displayCrs, mapCrs)
                            .transform(viewport.screenToWorld(screenX, screenY)));
        } catch (io.github.mundanej.map.api.CrsException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Converts a map-CRS coordinate to logical screen space when representable.
     *
     * @param coordinate coordinate in {@link #mapCrs()}
     * @return logical-screen coordinate, or empty outside the registered forward domain
     */
    public Optional<io.github.mundanej.map.api.Coordinate> mapToScreen(
            io.github.mundanej.map.api.Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        try {
            return Optional.of(
                    viewport.worldToScreen(
                            crsRegistry.operation(mapCrs, displayCrs).transform(coordinate)));
        } catch (io.github.mundanej.map.api.CrsException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Returns visible feature hits in deterministic topmost-first paint order.
     *
     * <p>Point labels and interaction overlays are intentionally not hit targets.
     *
     * @param screenX finite logical-screen x ordinate
     * @param screenY finite logical-screen y ordinate
     * @param tolerancePixels finite non-negative logical-pixel tolerance
     * @return immutable ordered hits from the currently published ordinary scene
     * @throws IllegalArgumentException if coordinates or tolerance are invalid
     */
    public MapHitResults hitTest(double screenX, double screenY, double tolerancePixels) {
        requireOpen();
        return BrowserSceneHits.hitTest(
                currentSceneLayers, viewport, screenX, screenY, tolerancePixels);
    }

    /**
     * Returns the current selected stable feature identity, if any.
     *
     * @return current reconciled selection
     */
    public Optional<FeatureSelection> selection() {
        reconcileInteractionIdentities();
        return selection;
    }

    /**
     * Selects a feature that exists uniquely in the currently published scene.
     *
     * @param requested current stable feature identity
     * @throws IllegalArgumentException if the identity is absent
     */
    public void setSelection(FeatureSelection requested) {
        requireOpen();
        Objects.requireNonNull(requested, "requested");
        if (!contains(requested.layerId(), requested.featureId())) {
            throw new IllegalArgumentException("selection must identify a current feature");
        }
        transitionInteraction(Optional.of(requested), hover);
    }

    /** Clears the current selection. */
    public void clearSelection() {
        requireOpen();
        transitionInteraction(Optional.empty(), hover);
    }

    /**
     * Returns the current bounded topmost hover identity, if any.
     *
     * @return current reconciled hover
     */
    public Optional<MapHit> hover() {
        reconcileInteractionIdentities();
        return hover;
    }

    /**
     * Adds a pointer-coordinate listener in deterministic registration order.
     *
     * @param listener listener to register
     * @return idempotent removal registration
     */
    public Registration addMapPointerListener(MapPointerListener listener) {
        requireOpen();
        pointerListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> removeIdentical(pointerListeners, listener);
    }

    /**
     * Adds a hover-transition listener in deterministic registration order.
     *
     * @param listener listener to register
     * @return idempotent removal registration
     */
    public Registration addMapHoverListener(MapHoverListener listener) {
        requireOpen();
        hoverListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> removeIdentical(hoverListeners, listener);
    }

    /**
     * Adds a selection-transition listener in deterministic registration order.
     *
     * @param listener listener to register
     * @return idempotent removal registration
     */
    public Registration addMapSelectionListener(MapSelectionListener listener) {
        requireOpen();
        selectionListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> removeIdentical(selectionListeners, listener);
    }

    /**
     * Returns the browser hover overlay symbols.
     *
     * @return immutable role-complete symbol bundle
     */
    public FeatureOverlaySymbols hoverOverlaySymbols() {
        return hoverOverlay;
    }

    /**
     * Replaces the browser hover overlay with a closed-profile symbol bundle.
     *
     * @param overlay immutable role-complete symbol bundle
     */
    public void setHoverOverlaySymbols(FeatureOverlaySymbols overlay) {
        requireOpen();
        FeatureOverlaySymbols accepted = requireBrowserOverlay(overlay);
        if (!hoverOverlay.equals(accepted)) {
            hoverOverlay = accepted;
            publishInteractionOverlay();
        }
    }

    /**
     * Returns the browser selection overlay symbols.
     *
     * @return immutable role-complete symbol bundle
     */
    public FeatureOverlaySymbols selectionOverlaySymbols() {
        return selectionOverlay;
    }

    /**
     * Replaces the browser selection overlay with a closed-profile symbol bundle.
     *
     * @param overlay immutable role-complete symbol bundle
     */
    public void setSelectionOverlaySymbols(FeatureOverlaySymbols overlay) {
        requireOpen();
        FeatureOverlaySymbols accepted = requireBrowserOverlay(overlay);
        if (!selectionOverlay.equals(accepted)) {
            selectionOverlay = accepted;
            publishInteractionOverlay();
        }
    }

    /**
     * Installs one toolkit-neutral active tool, replacing a distinct instance by identity.
     *
     * @param tool non-null tool to install
     */
    public void setActiveTool(MapTool tool) {
        requireOpen();
        Objects.requireNonNull(tool, "tool");
        boolean sessionChanged = toolRouter.activeTool().orElse(null) != tool;
        try {
            RouteOutcome outcome =
                    toolRouter.setActiveTool(
                            tool, cancelEvent(MapToolCancelReason.TOOL_REPLACED), toolContext());
            if (sessionChanged) {
                applyExternalToolOutcome(outcome);
            } else {
                applyToolOutcome(outcome);
            }
        } catch (RuntimeException | Error failure) {
            Throwable primary = cleanup(failure, this::publishCurrentToolState);
            throwUnchecked(primary);
        }
    }

    /** Clears and deactivates the active toolkit-neutral tool, if any. */
    public void clearActiveTool() {
        requireOpen();
        boolean sessionChanged = toolRouter.activeTool().isPresent();
        try {
            RouteOutcome outcome =
                    toolRouter.clearActiveTool(
                            cancelEvent(MapToolCancelReason.TOOL_CLEARED), toolContext());
            if (sessionChanged) {
                applyExternalToolOutcome(outcome);
            } else {
                applyToolOutcome(outcome);
            }
        } catch (RuntimeException | Error failure) {
            Throwable primary = cleanup(failure, this::publishCurrentToolState);
            throwUnchecked(primary);
        }
    }

    /**
     * Returns the currently installed toolkit-neutral tool, if any.
     *
     * @return current tool without transferring ownership
     */
    public Optional<MapTool> activeTool() {
        return toolRouter.activeTool();
    }

    /**
     * Fits all current snapshot geometries into the viewport.
     *
     * @param paddingPixels finite non-negative logical-pixel padding
     * @return whether a non-empty scene was fitted
     * @throws IllegalArgumentException if padding is not finite and non-negative
     */
    public boolean fitToContents(double paddingPixels) {
        requireOpen();
        if (sceneEnvelope.isEmpty()) {
            if (!Double.isFinite(paddingPixels) || paddingPixels < 0.0) {
                throw new IllegalArgumentException("Padding must be finite and non-negative");
            }
            return false;
        }
        setViewport(
                MapViewport.fit(
                        viewport.width(),
                        viewport.height(),
                        sceneEnvelope.orElseThrow(),
                        paddingPixels));
        return true;
    }

    /**
     * Registers a listener invoked for accepted settled browser viewport changes.
     *
     * @param listener listener invoked in registration order
     * @return idempotent removal registration
     */
    public Registration addViewportChangeListener(Consumer<MapViewport> listener) {
        requireOpen();
        Objects.requireNonNull(listener, "listener");
        return Registration.addAndRemove(viewportListeners, listener);
    }

    /**
     * Returns the latest rejected client or scene diagnostic, if any.
     *
     * @return optional stable failure
     */
    public Optional<MundaneMapException> diagnostic() {
        return diagnostic;
    }

    /**
     * Captures the latest browser-acknowledged vector scene using default export limits.
     *
     * <p>Capture is available only after the current scene and viewport generation has completed
     * its bounded label handshake, including scenes with no labels. Raster icons retain the
     * existing {@code VECTOR_EXPORT_SYMBOL_UNSUPPORTED} failure from the API snapshot boundary.
     *
     * @return immutable detached vector-export snapshot
     * @throws MundaneMapException if this component is closed
     * @throws VectorExportSnapshotException if the current generation is pending or contains
     *     content outside the existing vector-export profile
     */
    public VectorExportSnapshot captureVectorExportSnapshot() {
        return captureVectorExportSnapshot(
                VectorExportSnapshotLimits.defaults(), CancellationToken.none());
    }

    /**
     * Captures the latest browser-acknowledged vector scene with explicit limits.
     *
     * @param limits bounded detached-snapshot limits
     * @return immutable detached vector-export snapshot
     * @throws MundaneMapException if this component is closed
     * @throws VectorExportSnapshotException if the current generation is pending, over limit, or
     *     contains content outside the existing vector-export profile
     */
    public VectorExportSnapshot captureVectorExportSnapshot(VectorExportSnapshotLimits limits) {
        return captureVectorExportSnapshot(limits, CancellationToken.none());
    }

    /**
     * Captures the latest browser-acknowledged vector scene with explicit limits and cancellation.
     *
     * @param limits bounded detached-snapshot limits
     * @param cancellation cancellation signal observed throughout capture
     * @return immutable detached vector-export snapshot
     * @throws MundaneMapException if this component is closed
     * @throws VectorExportSnapshotException if the current generation is pending, cancelled, over
     *     limit, or contains content outside the existing vector-export profile
     */
    public VectorExportSnapshot captureVectorExportSnapshot(
            VectorExportSnapshotLimits limits, CancellationToken cancellation) {
        requireOpen();
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        BrowserCaptureState state = browserCaptureState;
        if (state == null
                || state.componentGeneration() != componentGeneration
                || state.sceneGeneration() != sceneGeneration
                || state.viewportGeneration() != viewportGeneration) {
            throw new VectorExportSnapshotException(
                    "The current browser scene has not completed label acceptance",
                    new VectorExportSnapshotProblem(
                            "VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID",
                            Map.of("field", "labelMeasurements", "reason", "pending")));
        }
        return BrowserVectorCapture.capture(
                state.layers(),
                state.labels(),
                state.viewport(),
                state.background(),
                limits,
                cancellation);
    }

    /**
     * Accepts one closed pointer, wheel, or cancellation event and returns router host state.
     *
     * <p>The browser supplies no logical feature identity. Hit, hover, and selection identities are
     * always derived from the current Java-owned scene.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     * @param clientSequence strictly increasing safe-integer event sequence
     * @param eventType closed {@link MapToolEvent.Type} name
     * @param screenX finite logical-screen x ordinate
     * @param screenY finite logical-screen y ordinate
     * @param changedButton closed-profile changed button number
     * @param buttonsMask closed-profile post-event button mask
     * @param modifiersMask closed-profile modifier mask
     * @param clickCount non-negative click count
     * @param wheelRotation finite signed wheel rotation
     * @param popupTrigger whether the event is a popup trigger
     * @param cancelReason cancellation reason name, or empty for ordinary events
     * @return immutable router outcome for client capture, cursor, and default handling
     */
    @ClientCallable
    public Map<String, Object> acceptMapInteraction(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double clientSequence,
            String eventType,
            double screenX,
            double screenY,
            int changedButton,
            int buttonsMask,
            int modifiersMask,
            int clickCount,
            double wheelRotation,
            boolean popupTrigger,
            String cancelReason) {
        if (!validateInteractionEnvelope(
                protocolVersion,
                clientComponentGeneration,
                clientSceneGeneration,
                clientViewportGeneration,
                clientSequence,
                "mapTool")) {
            return rejectedToolOutcome();
        }
        clientEventSequence = (long) clientSequence;
        transientViewportPermitSequence = -1;
        if (toolPointerRateQuarantined) {
            if (validRateCancellationAcknowledgement(
                    eventType,
                    screenX,
                    screenY,
                    changedButton,
                    buttonsMask,
                    modifiersMask,
                    clickCount,
                    wheelRotation,
                    popupTrigger,
                    cancelReason)) {
                toolPointerRateQuarantined = false;
            }
            return rejectedToolOutcome();
        }
        if (!takeToolPointerToken()) {
            return quarantineRateExceededToolInput("mapTool");
        }
        MapToolEvent event;
        ToolContextSnapshot context;
        try {
            MapToolEvent.Type type = MapToolEvent.Type.valueOf(requireWireText(eventType));
            MapPointerButton button = pointerButton(changedButton);
            Set<MapPointerButton> down = pointerButtons(buttonsMask);
            Set<MapInputModifier> modifiers = modifiers(modifiersMask);
            MapToolCancelReason reason =
                    type == MapToolEvent.Type.CANCEL
                            ? MapToolCancelReason.valueOf(requireWireText(cancelReason))
                            : null;
            if (type != MapToolEvent.Type.CANCEL
                    && cancelReason != null
                    && !cancelReason.isEmpty()) {
                throw new IllegalArgumentException("Ordinary events must not carry cancellation");
            }
            MapViewport eventViewport = interactionViewport();
            context = toolContext(eventViewport);
            event =
                    new MapToolEvent(
                            nextToolSequence(),
                            type,
                            screenX,
                            screenY,
                            context.screenToMap(screenX, screenY),
                            button,
                            down,
                            modifiers,
                            clickCount,
                            wheelRotation,
                            popupTrigger,
                            Optional.ofNullable(reason));
            lastPointerX = screenX;
            lastPointerY = screenY;
            lastButtonsDown = down;
            lastModifiers = modifiers;
        } catch (IllegalArgumentException exception) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.UNSUPPORTED_VALUE,
                                    "Browser interaction event is malformed",
                                    "eventClass",
                                    "mapTool"));
            return rejectedToolOutcome();
        }
        RouteOutcome outcome;
        try {
            outcome = toolRouter.route(event, context);
        } catch (RuntimeException | Error failure) {
            Throwable primary = cleanup(failure, this::publishCurrentToolState);
            throw propagated(primary);
        }
        applyToolOutcome(outcome);
        if (event.type() != MapToolEvent.Type.MOVE || outcome.suppressDefault()) {
            cancelPendingHover();
        }
        if (!outcome.suppressDefault()) {
            acceptDefaultInteraction(event, context.viewport());
            if (event.type() == MapToolEvent.Type.DRAG || event.type() == MapToolEvent.Type.WHEEL) {
                transientViewportPermitSequence = Math.incrementExact(clientEventSequence);
            }
        } else if (event.type() == MapToolEvent.Type.MOVE
                || event.type() == MapToolEvent.Type.CLICK) {
            transitionInteraction(selection, Optional.empty());
        }
        if (event.type() != MapToolEvent.Type.MOVE && event.type() != MapToolEvent.Type.CLICK) {
            transitionInteraction(selection, Optional.empty());
        }
        diagnostic = Optional.empty();
        return toolOutcome(outcome);
    }

    /**
     * Resumes the installed browser tool after Canvas focus returns.
     *
     * <p>This protocol endpoint is public only so Flow can invoke it.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     * @param clientSequence strictly increasing safe-integer event sequence
     * @return immutable router outcome for client cursor reconciliation
     */
    @ClientCallable
    public Map<String, Object> acceptMapToolResume(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double clientSequence) {
        if (!validateInteractionEnvelope(
                protocolVersion,
                clientComponentGeneration,
                clientSceneGeneration,
                clientViewportGeneration,
                clientSequence,
                "mapToolResume")) {
            return rejectedToolOutcome();
        }
        clientEventSequence = (long) clientSequence;
        transientViewportPermitSequence = -1;
        if (toolPointerRateQuarantined) {
            return rejectedToolOutcome();
        }
        if (!takeToolPointerToken()) {
            return quarantineRateExceededToolInput("mapToolResume");
        }
        RouteOutcome outcome;
        try {
            outcome = toolRouter.resume();
        } catch (RuntimeException | Error failure) {
            Throwable primary = cleanup(failure, this::publishCurrentToolState);
            throw propagated(primary);
        }
        applyToolOutcome(outcome);
        diagnostic = Optional.empty();
        return toolOutcome(outcome);
    }

    /**
     * Accepts one bounded keyboard semantic command for the current active tool.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     * @param clientSequence strictly increasing safe-integer event sequence
     * @param command closed {@link MapToolCommand} name
     * @return immutable router outcome for client cursor and default handling
     */
    @ClientCallable
    public Map<String, Object> acceptMapCommand(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double clientSequence,
            String command) {
        if (!validateInteractionEnvelope(
                protocolVersion,
                clientComponentGeneration,
                clientSceneGeneration,
                clientViewportGeneration,
                clientSequence,
                "mapCommand")) {
            return rejectedToolOutcome();
        }
        clientEventSequence = (long) clientSequence;
        transientViewportPermitSequence = -1;
        if (toolPointerRateQuarantined) {
            return rejectedToolOutcome();
        }
        if (!takeToolPointerToken()) {
            return quarantineRateExceededToolInput("mapCommand");
        }
        MapToolCommandEvent event;
        try {
            event =
                    new MapToolCommandEvent(
                            nextToolSequence(), MapToolCommand.valueOf(requireWireText(command)));
        } catch (IllegalArgumentException exception) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.UNSUPPORTED_VALUE,
                                    "Browser semantic command is malformed",
                                    "eventClass",
                                    "mapCommand"));
            return rejectedToolOutcome();
        }
        RouteOutcome outcome;
        try {
            outcome = toolRouter.routeCommand(event, toolContext());
        } catch (RuntimeException | Error failure) {
            Throwable primary = cleanup(failure, this::publishCurrentToolState);
            throw propagated(primary);
        }
        applyToolOutcome(outcome);
        diagnostic = Optional.empty();
        return toolOutcome(outcome);
    }

    /**
     * Accepts one conversion-only viewport synchronized after PASS tool navigation.
     *
     * <p>This protocol endpoint updates only the viewport used by subsequent tool conversion. It
     * does not publish settled viewport listeners, labels, or feature-source queries.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     * @param clientSequence strictly increasing safe-integer event sequence
     * @param width positive logical width
     * @param height positive logical height
     * @param centerX finite projected center x
     * @param centerY finite projected center y
     * @param worldUnitsPerPixel finite positive scale
     */
    @ClientCallable
    public void acceptTransientViewport(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double clientSequence,
            int width,
            int height,
            double centerX,
            double centerY,
            double worldUnitsPerPixel) {
        if (!validateInteractionEnvelope(
                        protocolVersion,
                        clientComponentGeneration,
                        clientSceneGeneration,
                        clientViewportGeneration,
                        clientSequence,
                        "transientViewport")
                || (long) clientSequence != transientViewportPermitSequence) {
            return;
        }
        clientEventSequence = (long) clientSequence;
        transientViewportPermitSequence = -1;
        MapViewport accepted;
        try {
            accepted = new MapViewport(width, height, centerX, centerY, worldUnitsPerPixel);
            protocol.validateViewport(accepted);
        } catch (MundaneMapException exception) {
            diagnostic = Optional.of(exception);
            return;
        } catch (IllegalArgumentException exception) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.NON_FINITE_VALUE,
                                    "Transient browser viewport is not finite and bounded",
                                    "eventClass",
                                    "transientViewport"));
            return;
        }
        viewportGeneration = Math.incrementExact(viewportGeneration);
        pendingSettledViewport = accepted;
        Throwable primary = cleanup(null, () -> transitionInteraction(selection, Optional.empty()));
        if (primary != null) {
            throwUnchecked(primary);
        }
        diagnostic = Optional.empty();
    }

    /**
     * Accepts a settled viewport reported by the bundled element after navigation or resize.
     *
     * <p>This protocol endpoint is public only so Flow can invoke it. Applications should use
     * {@link #addViewportChangeListener(Consumer)}.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     * @param clientSequence strictly increasing safe-integer event sequence
     * @param width positive logical width
     * @param height positive logical height
     * @param centerX finite projected center x
     * @param centerY finite projected center y
     * @param worldUnitsPerPixel finite positive scale
     */
    @ClientCallable
    public void acceptSettledViewport(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double clientSequence,
            int width,
            int height,
            double centerX,
            double centerY,
            double worldUnitsPerPixel) {
        if (closed) {
            diagnostic = Optional.of(closedFailure());
            return;
        }
        if (!isEnabled()) {
            diagnostic = Optional.of(disabledFailure());
            return;
        }
        if (protocolVersion != SceneProtocol.VERSION) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                                    "Browser protocol version is unsupported",
                                    "actual",
                                    Integer.toString(protocolVersion)));
            return;
        }
        if (!exactGeneration(clientComponentGeneration, componentGeneration)
                || !exactGeneration(clientSceneGeneration, sceneGeneration)
                || !exactGeneration(clientViewportGeneration, viewportGeneration)) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.STALE_GENERATION,
                                    "Browser viewport belongs to a stale generation",
                                    "sceneGeneration",
                                    Double.toString(clientSceneGeneration)));
            return;
        }
        if (!exactSequence(clientSequence, clientEventSequence)) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.EVENT_SEQUENCE_INVALID,
                                    "Browser event sequence is invalid",
                                    "eventClass",
                                    "settledViewport"));
            return;
        }
        clientEventSequence = (long) clientSequence;
        MapViewport accepted;
        try {
            accepted = new MapViewport(width, height, centerX, centerY, worldUnitsPerPixel);
            protocol.validateViewport(accepted);
        } catch (MundaneMapException exception) {
            diagnostic = Optional.of(exception);
            return;
        } catch (IllegalArgumentException exception) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.NON_FINITE_VALUE,
                                    "Browser viewport is not finite and bounded",
                                    "value",
                                    exception.getMessage()));
            return;
        }
        viewportGeneration = Math.incrementExact(viewportGeneration);
        if (!takeSettledToken()) {
            pendingSettledViewport = accepted;
            scheduleSettledFlush();
            return;
        }
        pendingSettledViewport = null;
        acceptViewport(accepted);
    }

    /**
     * Accepts one packed closed-font metric vector for the current label generation.
     *
     * <p>This protocol endpoint is public only so Flow can invoke it. Each candidate contributes
     * advance, minimum x/y, and maximum x/y values in server-declared order.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     * @param measurements flat packed metric values
     */
    @ClientCallable
    public void acceptLabelMeasurements(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double[] measurements) {
        if (!validateLabelMessage(
                protocolVersion,
                clientComponentGeneration,
                clientSceneGeneration,
                clientViewportGeneration,
                pendingLabelMeasurements)) {
            return;
        }
        PendingLabelMeasurements pending = pendingLabelMeasurements;
        double[] values;
        try {
            Objects.requireNonNull(measurements, "measurements");
            int length = measurements.length;
            int expected =
                    Math.multiplyExact(
                            pending.candidates().size(), BrowserLabelPlacement.METRIC_VALUES);
            if (length != expected) {
                throw new MundaneMapException(
                        MundaneMapException.LIMIT_EXCEEDED,
                        "Browser label metric count does not match the pending candidates",
                        Map.of("limit", "labelMetrics"));
            }
            values = measurements.clone();
            List<PlacedPointLabel> placed =
                    BrowserLabelPlacement.place(pending.candidates(), values, pending.viewport());
            List<Map<String, Object>> encoded = new ArrayList<>(placed.size());
            for (PlacedPointLabel label : placed) {
                encoded.add(
                        Map.of(
                                "text",
                                label.text(),
                                "color",
                                List.of(
                                        label.style().color().red(),
                                        label.style().color().green(),
                                        label.style().color().blue(),
                                        label.style().color().alpha()),
                                "weight",
                                label.style().weight().name(),
                                "sizePixels",
                                label.style().sizePixels(),
                                "baselineX",
                                label.baselineX(),
                                "baselineY",
                                label.baselineY(),
                                "advance",
                                label.advance(),
                                "ordinal",
                                label.ordinaryPaintOrdinal()));
            }
            PendingPlacedLabels staged =
                    new PendingPlacedLabels(
                            pending.componentGeneration(),
                            pending.sceneGeneration(),
                            pending.viewportGeneration(),
                            pending.layers(),
                            pending.viewport(),
                            pending.background(),
                            placed);
            getElement()
                    .callJsFunction(
                            "setPlacedLabels",
                            SceneProtocol.VERSION,
                            componentGeneration,
                            sceneGeneration,
                            viewportGeneration,
                            List.copyOf(encoded));
            pendingLabelMeasurements = null;
            pendingPlacedLabels = staged;
            diagnostic = Optional.empty();
        } catch (MundaneMapException exception) {
            diagnostic = Optional.of(exception);
        } catch (RuntimeException exception) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.UNSUPPORTED_VALUE,
                                    "Browser label metrics are malformed",
                                    "valueKind",
                                    "labelMetrics"));
        }
    }

    /**
     * Acknowledges that the bundled element accepted the server-placed label generation.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param clientViewportGeneration client viewport generation
     */
    @ClientCallable
    public void acceptPlacedLabels(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration) {
        if (!validateLabelMessage(
                protocolVersion,
                clientComponentGeneration,
                clientSceneGeneration,
                clientViewportGeneration,
                pendingPlacedLabels)) {
            return;
        }
        PendingPlacedLabels pending = pendingPlacedLabels;
        browserCaptureState =
                new BrowserCaptureState(
                        pending.componentGeneration(),
                        pending.sceneGeneration(),
                        pending.viewportGeneration(),
                        pending.layers(),
                        pending.viewport(),
                        pending.background(),
                        pending.labels());
        pendingPlacedLabels = null;
        diagnostic = Optional.empty();
    }

    /**
     * Records a bounded failure reported by the bundled element.
     *
     * <p>This protocol endpoint is public only so Flow can invoke it.
     *
     * @param protocolVersion client protocol version
     * @param clientComponentGeneration client component generation
     * @param clientSceneGeneration client scene generation
     * @param message browser failure summary
     */
    @ClientCallable
    public void acceptClientFailure(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            String message) {
        if (closed) {
            diagnostic = Optional.of(closedFailure());
            return;
        }
        if (!isEnabled()) {
            diagnostic = Optional.of(disabledFailure());
            return;
        }
        if (protocolVersion != SceneProtocol.VERSION) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                                    "Browser protocol version is unsupported",
                                    "actual",
                                    Integer.toString(protocolVersion)));
            return;
        }
        if (!exactGeneration(clientComponentGeneration, componentGeneration)
                || !exactGeneration(clientSceneGeneration, sceneGeneration)) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.STALE_GENERATION,
                                    "Browser failure belongs to a stale generation",
                                    "eventClass",
                                    "clientFailure"));
            return;
        }
        String clientCode = Objects.requireNonNullElse(message, MundaneMapException.CLIENT_FAILURE);
        String code =
                switch (clientCode) {
                    case MundaneMapException.LIMIT_EXCEEDED,
                            MundaneMapException.NON_FINITE_VALUE,
                            MundaneMapException.DUPLICATE_ID,
                            MundaneMapException.UNSUPPORTED_VALUE,
                            SymbolException.HATCH_SEGMENT_LIMIT_EXCEEDED,
                            MundaneMapException.BROWSER_CAPABILITY_UNSUPPORTED,
                            MundaneMapException.RESOURCE_UNAVAILABLE,
                            MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                            MundaneMapException.STALE_GENERATION ->
                            clientCode;
                    default -> MundaneMapException.CLIENT_FAILURE;
                };
        browserCaptureState = null;
        diagnostic =
                Optional.of(
                        failure(
                                code,
                                "Browser failed to accept or paint the scene",
                                "phase",
                                "canvas"));
    }

    /**
     * Enables or disables browser input and releases active client gestures when disabled.
     *
     * @param enabled whether the component accepts input
     */
    @Override
    public void setEnabled(boolean enabled) {
        requireOpen();
        Throwable primary = null;
        if (!enabled && isEnabled()) {
            primary =
                    cleanup(
                            primary,
                            () -> cancelToolInteraction(MapToolCancelReason.VIEW_DISABLED));
            cancelPendingHover();
            primary = cleanup(primary, () -> transitionInteraction(selection, Optional.empty()));
        }
        primary = cleanup(primary, () -> setEnabledNow(enabled));
        if (!enabled) {
            cancelPendingSettledViewport();
            cancelFeatureQuery();
            viewportGeneration = Math.incrementExact(viewportGeneration);
            primary = cleanup(primary, () -> clearBrowserLabelState(false));
        } else {
            primary = cleanup(primary, this::scheduleSourceQuery);
        }
        primary = cleanup(primary, () -> setClientEnabled(enabled));
        if (enabled) {
            primary = cleanup(primary, () -> applyToolOutcome(toolRouter.resume()));
            primary = cleanup(primary, this::publishViewport);
        }
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    /**
     * Releases browser listeners, pending paints, registered listeners, and snapshot state.
     *
     * <p>{@inheritDoc}
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        Throwable primary = null;
        primary =
                cleanup(
                        primary,
                        () -> {
                            if (toolRouter.activeTool().isPresent()) {
                                clearActiveTool();
                            }
                        });
        closed = true;
        componentGeneration = Math.incrementExact(componentGeneration);
        primary = cleanup(primary, this::closeClient);
        viewportListeners.clear();
        pointerListeners.clear();
        hoverListeners.clear();
        selectionListeners.clear();
        interactionNotifications.clear();
        selection = Optional.empty();
        hover = Optional.empty();
        cancelPendingHover();
        sourceReportListeners.clear();
        sourceReportNotifications.clear();
        layers = List.of();
        sourceLayers = List.of();
        paintedFeatures = Set.of();
        primary = cleanup(primary, this::releaseIconResources);
        primary = cleanup(primary, () -> clearBrowserLabelState(true));
        primary = cleanup(primary, this::removeIconSessionCleanup);
        sceneEnvelope = Optional.empty();
        cancelPendingSettledViewport();
        cancelFeatureQuery();
        List<FeatureSourceBinding> released = featureSourceBindings();
        featureBindings = List.of();
        sourceReports = Map.of();
        if (!released.isEmpty()) {
            primary =
                    cleanup(primary, () -> queryExecutor.execute(() -> releaseBindings(released)));
        }
        if (ownedQueryExecutor != null) {
            primary = cleanup(primary, ownedQueryExecutor::shutdown);
        }
        diagnostic = Optional.empty();
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (closed) {
            return;
        }
        componentGeneration = Math.incrementExact(componentGeneration);
        resetClientEventState();
        sceneGeneration = Math.incrementExact(sceneGeneration);
        removeIconSessionCleanup();
        iconSessionCleanup =
                iconSessionAccess.addDestroyListener(this, this::handleIconSessionDestroy);
        StagedScene staged = stageScene(combinedLayers(), background, sceneGeneration);
        getElement()
                .callJsFunction(
                        "activateMap", SceneProtocol.VERSION, componentGeneration, sceneGeneration);
        publishStagedScene(staged);
        publishViewport();
        applyToolOutcome(toolRouter.resume());
        scheduleSourceQuery();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        Throwable primary = null;
        if (!closed) {
            primary =
                    cleanup(primary, () -> cancelToolInteraction(MapToolCancelReason.VIEW_REMOVED));
            cancelPendingHover();
            primary = cleanup(primary, () -> transitionInteraction(selection, Optional.empty()));
            primary = cleanup(primary, this::deactivateClient);
            componentGeneration = Math.incrementExact(componentGeneration);
            resetClientEventState();
            cancelFeatureQuery();
            sourceLayers = List.of();
            primary = cleanup(primary, this::releaseIconResources);
            primary = cleanup(primary, () -> clearBrowserLabelState(true));
            primary = cleanup(primary, this::removeIconSessionCleanup);
        }
        primary = cleanup(primary, () -> super.onDetach(detachEvent));
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    Map<String, Object> encodedSceneForTest() {
        return protocol.encode(
                        combinedLayers(),
                        background,
                        viewport,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration,
                        iconResources)
                .scene();
    }

    long componentGenerationForTest() {
        return componentGeneration;
    }

    long sceneGenerationForTest() {
        return sceneGeneration;
    }

    long viewportGenerationForTest() {
        return viewportGeneration;
    }

    boolean paintsFeatureForTest(FeatureSelection identity) {
        return paintedFeatures.contains(identity);
    }

    private void publishScene(Map<String, Object> scene) {
        getElement().callJsFunction("setScene", scene);
    }

    private void setClientEnabled(boolean enabled) {
        getElement().callJsFunction("setMapEnabled", enabled);
    }

    private void setEnabledNow(boolean enabled) {
        HasEnabled.super.setEnabled(enabled);
    }

    private void closeClient() {
        getElement().callJsFunction("closeMap", SceneProtocol.VERSION, componentGeneration);
    }

    private void deactivateClient() {
        getElement().callJsFunction("deactivateMap", SceneProtocol.VERSION, componentGeneration);
    }

    private void publishViewport() {
        prepareCurrentLabelState();
        getElement()
                .callJsFunction(
                        "setMapViewport",
                        SceneProtocol.VERSION,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration,
                        viewport.width(),
                        viewport.height(),
                        viewport.centerX(),
                        viewport.centerY(),
                        viewport.worldUnitsPerPixel());
    }

    private void requestCurrentLabelMeasurements() {
        if (closed || !isEnabled()) {
            return;
        }
        prepareCurrentLabelState();
        getElement()
                .callJsFunction(
                        "remeasureLabels",
                        SceneProtocol.VERSION,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration);
    }

    private void prepareCurrentLabelState() {
        if (labelStateMatchesCurrent(pendingLabelMeasurements)
                || labelStateMatchesCurrent(pendingPlacedLabels)
                || labelStateMatchesCurrent(browserCaptureState)) {
            return;
        }
        pendingLabelMeasurements = null;
        pendingPlacedLabels = null;
        browserCaptureState = null;
        if (currentLabelCandidates.isEmpty()) {
            pendingPlacedLabels =
                    new PendingPlacedLabels(
                            componentGeneration,
                            sceneGeneration,
                            viewportGeneration,
                            currentSceneLayers,
                            viewport,
                            background,
                            List.of());
        } else {
            pendingLabelMeasurements =
                    new PendingLabelMeasurements(
                            componentGeneration,
                            sceneGeneration,
                            viewportGeneration,
                            currentSceneLayers,
                            currentLabelCandidates,
                            viewport,
                            background);
        }
    }

    private boolean labelStateMatchesCurrent(LabelGeneration state) {
        return state != null
                && state.componentGeneration() == componentGeneration
                && state.sceneGeneration() == sceneGeneration
                && state.viewportGeneration() == viewportGeneration;
    }

    private boolean validateInteractionEnvelope(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            double clientSequence,
            String eventClass) {
        if (closed) {
            diagnostic = Optional.of(closedFailure());
            return false;
        }
        if (!isEnabled()) {
            diagnostic = Optional.of(disabledFailure());
            return false;
        }
        if (protocolVersion != SceneProtocol.VERSION) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                                    "Browser protocol version is unsupported",
                                    "actual",
                                    Integer.toString(protocolVersion)));
            return false;
        }
        if (!exactGeneration(clientComponentGeneration, componentGeneration)
                || !exactGeneration(clientSceneGeneration, sceneGeneration)
                || !exactGeneration(clientViewportGeneration, viewportGeneration)) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.STALE_GENERATION,
                                    "Browser interaction belongs to a stale generation",
                                    "eventClass",
                                    eventClass));
            return false;
        }
        if (!exactSequence(clientSequence, clientEventSequence)) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.EVENT_SEQUENCE_INVALID,
                                    "Browser event sequence is invalid",
                                    "eventClass",
                                    eventClass));
            return false;
        }
        return true;
    }

    private void acceptDefaultInteraction(MapToolEvent event, MapViewport eventViewport) {
        if (event.type() == MapToolEvent.Type.MOVE) {
            acceptHoverProbe(event, eventViewport);
            return;
        }
        if (event.type() != MapToolEvent.Type.CLICK) {
            return;
        }
        Optional<FeatureSelection> next = selection;
        if (event.button().equals(MapPointerButton.PRIMARY)
                && event.clickCount() == 1
                && !event.popupTrigger()
                && event.modifiers().isEmpty()) {
            next =
                    BrowserSceneHits.hitTest(
                                    currentSceneLayers,
                                    eventViewport,
                                    event.screenX(),
                                    event.screenY(),
                                    DEFAULT_SELECTION_TOLERANCE_PIXELS)
                            .topmost()
                            .map(hit -> new FeatureSelection(hit.layerId(), hit.featureId()));
        }
        transitionInteraction(next, Optional.empty());
        firePointer(MapPointerEvent.Type.CLICKED, event);
    }

    private void acceptHoverProbe(MapToolEvent event, MapViewport eventViewport) {
        HoverProbe probe =
                new HoverProbe(
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration,
                        event.screenX(),
                        event.screenY(),
                        eventViewport,
                        event.mapCoordinate());
        if (!takeHoverToken()) {
            pendingHoverProbe = probe;
            scheduleHoverFlush();
            return;
        }
        pendingHoverProbe = null;
        applyHoverProbe(probe);
    }

    private void applyHoverProbe(HoverProbe probe) {
        if (closed
                || !isEnabled()
                || probe.componentGeneration() != componentGeneration
                || probe.sceneGeneration() != sceneGeneration
                || probe.viewportGeneration() != viewportGeneration) {
            return;
        }
        Optional<MapHit> next =
                BrowserSceneHits.hitTest(
                                currentSceneLayers,
                                probe.viewport(),
                                probe.screenX(),
                                probe.screenY(),
                                DEFAULT_HOVER_TOLERANCE_PIXELS)
                        .topmost();
        transitionInteraction(selection, next);
        MapPointerEvent event =
                new MapPointerEvent(
                        MapPointerEvent.Type.MOVED,
                        probe.screenX(),
                        probe.screenY(),
                        probe.mapCoordinate());
        for (MapPointerListener listener : List.copyOf(pointerListeners)) {
            listener.onMapPointerEvent(event);
        }
    }

    private void firePointer(MapPointerEvent.Type type, MapToolEvent source) {
        MapPointerEvent event =
                new MapPointerEvent(
                        type, source.screenX(), source.screenY(), source.mapCoordinate());
        for (MapPointerListener listener : List.copyOf(pointerListeners)) {
            listener.onMapPointerEvent(event);
        }
    }

    private boolean takeHoverToken() {
        long current = nanoTime.getAsLong();
        long elapsed = Math.max(0, current - hoverRefillNanos);
        hoverRefillNanos = current;
        hoverTokens = Math.min(20.0, hoverTokens + elapsed * (20.0 / 1_000_000_000.0));
        if (hoverTokens < 1.0) {
            return false;
        }
        hoverTokens -= 1.0;
        return true;
    }

    private boolean takeToolPointerToken() {
        long current = nanoTime.getAsLong();
        long elapsed = Math.max(0, current - toolPointerRefillNanos);
        toolPointerRefillNanos = current;
        toolPointerTokens =
                Math.min(120.0, toolPointerTokens + elapsed * (120.0 / 1_000_000_000.0));
        if (toolPointerTokens < 1.0) {
            return false;
        }
        toolPointerTokens -= 1.0;
        return true;
    }

    private void scheduleHoverFlush() {
        if (hoverFlushScheduled) {
            return;
        }
        hoverFlushScheduled = true;
        hoverScheduleEpoch = Math.incrementExact(hoverScheduleEpoch);
        long epoch = hoverScheduleEpoch;
        settledScheduler.accept(() -> flushHover(epoch));
    }

    private void flushHover(long epoch) {
        if (epoch != hoverScheduleEpoch) {
            return;
        }
        hoverFlushScheduled = false;
        if (closed || pendingHoverProbe == null) {
            return;
        }
        if (!takeHoverToken()) {
            scheduleHoverFlush();
            return;
        }
        HoverProbe probe = pendingHoverProbe;
        pendingHoverProbe = null;
        applyHoverProbe(probe);
    }

    private void cancelPendingHover() {
        pendingHoverProbe = null;
        hoverFlushScheduled = false;
        hoverScheduleEpoch = Math.incrementExact(hoverScheduleEpoch);
    }

    private void reconcileInteractionIdentities() {
        Optional<FeatureSelection> nextSelection =
                selection.filter(value -> contains(value.layerId(), value.featureId()));
        Optional<MapHit> nextHover =
                hover.filter(value -> contains(value.layerId(), value.featureId()));
        transitionInteraction(nextSelection, nextHover);
    }

    private boolean contains(String layerId, String featureId) {
        return findFeature(layerId, featureId).isPresent();
    }

    private Optional<Feature> findFeature(String layerId, String featureId) {
        for (Layer layer : currentSceneLayers) {
            if (!layer.id().equals(layerId)) {
                continue;
            }
            for (Feature feature : layer.features()) {
                if (feature.id().equals(featureId)) {
                    return Optional.of(feature);
                }
            }
        }
        return Optional.empty();
    }

    private void transitionInteraction(
            Optional<FeatureSelection> nextSelection, Optional<MapHit> nextHover) {
        Objects.requireNonNull(nextSelection, "nextSelection");
        Objects.requireNonNull(nextHover, "nextHover");
        Optional<FeatureSelection> previousSelection = selection;
        Optional<MapHit> previousHover = hover;
        if (previousSelection.equals(nextSelection) && previousHover.equals(nextHover)) {
            return;
        }
        selection = nextSelection;
        hover = nextHover;
        publishInteractionOverlay();
        if (!previousSelection.equals(nextSelection)) {
            interactionNotifications.addLast(
                    InteractionNotification.selection(
                            new MapSelectionEvent(previousSelection, nextSelection)));
        }
        if (!previousHover.equals(nextHover)) {
            interactionNotifications.addLast(
                    InteractionNotification.hover(new MapHoverEvent(previousHover, nextHover)));
        }
        drainInteractionNotifications();
    }

    private void drainInteractionNotifications() {
        if (drainingInteractionNotifications) {
            return;
        }
        drainingInteractionNotifications = true;
        RuntimeException first = null;
        try {
            while (!interactionNotifications.isEmpty()) {
                InteractionNotification notification = interactionNotifications.removeFirst();
                if (notification.selection() != null) {
                    for (MapSelectionListener listener : List.copyOf(selectionListeners)) {
                        try {
                            listener.onMapSelectionChanged(notification.selection());
                        } catch (RuntimeException failure) {
                            if (first == null) {
                                first = failure;
                            } else if (first != failure) {
                                first.addSuppressed(failure);
                            }
                        } catch (Error failure) {
                            interactionNotifications.clear();
                            throw failure;
                        }
                    }
                } else {
                    for (MapHoverListener listener : List.copyOf(hoverListeners)) {
                        try {
                            listener.onMapHoverChanged(notification.hover());
                        } catch (RuntimeException failure) {
                            if (first == null) {
                                first = failure;
                            } else if (first != failure) {
                                first.addSuppressed(failure);
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
        if (first != null) {
            throw first;
        }
    }

    private void publishInteractionOverlay() {
        if (closed) {
            return;
        }
        List<Layer> overlays = new ArrayList<>(2);
        hover.ifPresent(
                value ->
                        paintedFeature(value.layerId(), value.featureId())
                                .ifPresent(
                                        feature ->
                                                overlays.add(
                                                        overlayLayer(
                                                                "__hover",
                                                                feature,
                                                                hoverOverlay))));
        selection.ifPresent(
                value ->
                        paintedFeature(value.layerId(), value.featureId())
                                .ifPresent(
                                        feature ->
                                                overlays.add(
                                                        overlayLayer(
                                                                "__selection",
                                                                feature,
                                                                selectionOverlay))));
        SceneProtocol.Result encoded =
                protocol.encode(
                        overlays,
                        Rgba.TRANSPARENT,
                        viewport,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration);
        getElement()
                .callJsFunction(
                        "setInteractionOverlay",
                        SceneProtocol.VERSION,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration,
                        encoded.scene().get("layers"));
    }

    private static Layer overlayLayer(String id, Feature source, FeatureOverlaySymbols symbols) {
        Geometry geometry = source.geometry();
        SymbolRole role = source.symbol().role();
        io.github.mundanej.map.api.Symbol symbol =
                switch (role) {
                    case MARKER -> symbols.marker();
                    case LINE -> symbols.line();
                    case FILL -> symbols.fill();
                    case LEGACY_GEOMETRY -> throw new IllegalArgumentException("legacy overlay");
                };
        Feature feature =
                new Feature(source.id(), source.name(), geometry, source.attributes(), symbol);
        return new InMemoryLayer(id, id, List.of(feature));
    }

    private Optional<Feature> paintedFeature(String layerId, String featureId) {
        return paintedFeatures.contains(new FeatureSelection(layerId, featureId))
                ? findFeature(layerId, featureId)
                : Optional.empty();
    }

    private static Set<FeatureSelection> paintedFeatures(
            Map<String, Object> scene, List<? extends Layer> sourceLayers) {
        Set<FeatureSelection> result = new HashSet<>();
        IdentityHashMap<RasterIconSymbol, Boolean> rasterVisibility = new IdentityHashMap<>();
        List<?> layers = (List<?>) scene.get("layers");
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            Map<?, ?> layer = (Map<?, ?>) layers.get(layerIndex);
            String layerId = (String) layer.get("id");
            List<?> features = (List<?>) layer.get("features");
            List<Feature> sourceFeatures = sourceLayers.get(layerIndex).features();
            for (int featureIndex = 0; featureIndex < features.size(); featureIndex++) {
                Map<?, ?> feature = (Map<?, ?>) features.get(featureIndex);
                Feature source = sourceFeatures.get(featureIndex);
                boolean visibleRaster =
                        containsVisibleRaster(source.symbol(), 1.0, rasterVisibility);
                if (((List<?>) feature.get("primitives"))
                        .stream()
                                .map(Map.class::cast)
                                .anyMatch(
                                        primitive -> visiblePrimitive(primitive, visibleRaster))) {
                    result.add(new FeatureSelection(layerId, (String) feature.get("id")));
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean visiblePrimitive(Map<?, ?> primitive, boolean visibleRaster) {
        if (((Number) primitive.get("opacity")).doubleValue() == 0.0) {
            return false;
        }
        return switch ((String) primitive.get("kind")) {
            case "point" ->
                    colorVisible((List<?>) primitive.get("fill"))
                            || optionalStrokeVisible((Map<?, ?>) primitive.get("stroke"));
            case "icon" -> visibleRaster;
            case "line", "hatch" ->
                    colorVisible((List<?>) ((Map<?, ?>) primitive.get("stroke")).get("color"));
            case "polygon" -> colorVisible((List<?>) primitive.get("fill"));
            default -> false;
        };
    }

    private static boolean optionalStrokeVisible(Map<?, ?> stroke) {
        return Boolean.TRUE.equals(stroke.get("present"))
                && colorVisible((List<?>) ((Map<?, ?>) stroke.get("value")).get("color"));
    }

    private static boolean colorVisible(List<?> color) {
        return ((Number) color.get(3)).intValue() > 0;
    }

    private static boolean containsVisibleRaster(
            Symbol symbol,
            double inheritedOpacity,
            IdentityHashMap<RasterIconSymbol, Boolean> visibility) {
        if (symbol instanceof CompositeSymbol composite) {
            double opacity = inheritedOpacity * composite.opacity();
            return composite.children().stream()
                    .anyMatch(component -> containsVisibleRaster(component, opacity, visibility));
        }
        if (!(symbol instanceof RasterIconSymbol icon)
                || inheritedOpacity * icon.opacity() == 0.0) {
            return false;
        }
        Boolean retained = visibility.get(icon);
        if (retained != null) {
            return retained;
        }
        for (int y = 0; y < icon.height(); y++) {
            for (int x = 0; x < icon.width(); x++) {
                if ((icon.rgbaAt(x, y) & 0xff) != 0) {
                    visibility.put(icon, true);
                    return true;
                }
            }
        }
        visibility.put(icon, false);
        return false;
    }

    private static FeatureOverlaySymbols requireBrowserOverlay(FeatureOverlaySymbols overlay) {
        Objects.requireNonNull(overlay, "overlay");
        SceneProtocol.requireBuiltInSymbol(
                overlay.marker(), SymbolRole.MARKER, "interaction", "overlay marker");
        SceneProtocol.requireBuiltInSymbol(
                overlay.line(), SymbolRole.LINE, "interaction", "overlay line");
        SceneProtocol.requireBuiltInSymbol(
                overlay.fill(), SymbolRole.FILL, "interaction", "overlay fill");
        return overlay;
    }

    private ToolContextSnapshot toolContext() {
        return toolContext(viewport);
    }

    private ToolContextSnapshot toolContext(MapViewport contextViewport) {
        return new ToolContextSnapshot(contextViewport, crsRegistry, mapCrs, displayCrs);
    }

    private MapViewport interactionViewport() {
        return pendingSettledViewport == null ? viewport : pendingSettledViewport;
    }

    private MapToolEvent cancelEvent(MapToolCancelReason reason) {
        ToolContextSnapshot context = toolContext();
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

    private boolean validRateCancellationAcknowledgement(
            String eventType,
            double screenX,
            double screenY,
            int changedButton,
            int buttonsMask,
            int modifiersMask,
            int clickCount,
            double wheelRotation,
            boolean popupTrigger,
            String cancelReason) {
        try {
            if (MapToolEvent.Type.valueOf(requireWireText(eventType)) != MapToolEvent.Type.CANCEL
                    || MapToolCancelReason.valueOf(requireWireText(cancelReason))
                            != MapToolCancelReason.POINTER_STATE_LOST) {
                return false;
            }
            Set<MapPointerButton> down = pointerButtons(buttonsMask);
            Set<MapInputModifier> modifiers = modifiers(modifiersMask);
            MapToolEvent acknowledgement =
                    new MapToolEvent(
                            nextToolSequence(),
                            MapToolEvent.Type.CANCEL,
                            screenX,
                            screenY,
                            toolContext().screenToMap(screenX, screenY),
                            pointerButton(changedButton),
                            down,
                            modifiers,
                            clickCount,
                            wheelRotation,
                            popupTrigger,
                            Optional.of(MapToolCancelReason.POINTER_STATE_LOST));
            lastPointerX = acknowledgement.screenX();
            lastPointerY = acknowledgement.screenY();
            lastButtonsDown = acknowledgement.buttonsDown();
            lastModifiers = acknowledgement.modifiers();
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void cancelToolInteraction(MapToolCancelReason reason) {
        if (toolRouter.activeTool().isEmpty()) {
            return;
        }
        applyToolOutcome(toolRouter.cancelInteraction(cancelEvent(reason), toolContext()));
    }

    private Map<String, Object> quarantineRateExceededToolInput(String eventClass) {
        toolPointerRateQuarantined = true;
        Throwable cancellationFailure = null;
        try {
            cancelToolInteraction(MapToolCancelReason.POINTER_STATE_LOST);
        } catch (RuntimeException | Error failure) {
            cancellationFailure = failure;
        }
        lastButtonsDown = Set.of();
        MundaneMapException rateFailure =
                failure(
                        MundaneMapException.EVENT_RATE_EXCEEDED,
                        "Browser tool input rate exceeded",
                        "eventClass",
                        eventClass);
        if (cancellationFailure != null) {
            rateFailure.addSuppressed(cancellationFailure);
        }
        diagnostic = Optional.of(rateFailure);
        return rateExceededToolOutcome();
    }

    private void applyToolOutcome(RouteOutcome outcome) {
        getElement()
                .callJsFunction(
                        "setToolState",
                        toolRouter.activeTool().isPresent(),
                        outcome.captured(),
                        outcome.cursorIntent().name());
    }

    private void applyExternalToolOutcome(RouteOutcome outcome) {
        getElement()
                .callJsFunction(
                        "resetToolState",
                        toolRouter.activeTool().isPresent(),
                        outcome.captured(),
                        outcome.cursorIntent().name());
    }

    private void publishCurrentToolState() {
        applyExternalToolOutcome(
                new RouteOutcome(true, toolRouter.captured(), toolRouter.currentCursorIntent()));
    }

    private static Map<String, Object> toolOutcome(RouteOutcome outcome) {
        return Map.of(
                "accepted", true,
                "suppressDefault", outcome.suppressDefault(),
                "captured", outcome.captured(),
                "cursor", outcome.cursorIntent().name());
    }

    private Map<String, Object> rejectedToolOutcome() {
        return Map.of(
                "accepted",
                false,
                "suppressDefault",
                true,
                "captured",
                toolRouter.captured(),
                "cursor",
                toolRouter.currentCursorIntent().name());
    }

    private Map<String, Object> rateExceededToolOutcome() {
        return Map.of(
                "accepted",
                false,
                "suppressDefault",
                true,
                "captured",
                false,
                "cursor",
                toolRouter.currentCursorIntent().name(),
                "rateExceeded",
                true);
    }

    private long nextToolSequence() {
        if (toolEventSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Map-tool event sequence exhausted");
        }
        return ++toolEventSequence;
    }

    private static String requireWireText(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("Wire enum value is invalid");
        }
        return value;
    }

    private static MapPointerButton pointerButton(int value) {
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException("Pointer button is outside the closed profile");
        }
        return new MapPointerButton(value);
    }

    private static Set<MapPointerButton> pointerButtons(int mask) {
        if (mask < 0 || (mask & ~7) != 0) {
            throw new IllegalArgumentException("Pointer button mask is outside the closed profile");
        }
        Set<MapPointerButton> result = new HashSet<>();
        if ((mask & 1) != 0) {
            result.add(MapPointerButton.PRIMARY);
        }
        if ((mask & 2) != 0) {
            result.add(MapPointerButton.SECONDARY);
        }
        if ((mask & 4) != 0) {
            result.add(MapPointerButton.MIDDLE);
        }
        return Set.copyOf(result);
    }

    private static Set<MapInputModifier> modifiers(int mask) {
        if (mask < 0 || (mask & ~31) != 0) {
            throw new IllegalArgumentException("Modifier mask is outside the closed profile");
        }
        Set<MapInputModifier> result = new HashSet<>();
        MapInputModifier[] values = MapInputModifier.values();
        for (int index = 0; index < values.length; index++) {
            if ((mask & (1 << index)) != 0) {
                result.add(values[index]);
            }
        }
        return Set.copyOf(result);
    }

    private static <T> void removeIdentical(List<T> values, T requested) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) == requested) {
                values.remove(index);
                return;
            }
        }
    }

    private boolean validateLabelMessage(
            int protocolVersion,
            double clientComponentGeneration,
            double clientSceneGeneration,
            double clientViewportGeneration,
            LabelGeneration pending) {
        if (closed) {
            diagnostic = Optional.of(closedFailure());
            return false;
        }
        if (!isEnabled()) {
            diagnostic = Optional.of(disabledFailure());
            return false;
        }
        if (protocolVersion != SceneProtocol.VERSION) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                                    "Browser protocol version is unsupported",
                                    "actual",
                                    Integer.toString(protocolVersion)));
            return false;
        }
        if (pending == null
                || !exactGeneration(clientComponentGeneration, componentGeneration)
                || !exactGeneration(clientSceneGeneration, sceneGeneration)
                || !exactGeneration(clientViewportGeneration, viewportGeneration)
                || !exactGeneration(clientComponentGeneration, pending.componentGeneration())
                || !exactGeneration(clientSceneGeneration, pending.sceneGeneration())
                || !exactGeneration(clientViewportGeneration, pending.viewportGeneration())) {
            diagnostic =
                    Optional.of(
                            failure(
                                    MundaneMapException.STALE_GENERATION,
                                    "Browser label message belongs to a stale generation",
                                    "eventClass",
                                    "labels"));
            return false;
        }
        return true;
    }

    private void clearBrowserLabelState(boolean clearScene) {
        pendingLabelMeasurements = null;
        pendingPlacedLabels = null;
        browserCaptureState = null;
        if (clearScene) {
            currentSceneLayers = List.of();
            currentLabelCandidates = List.of();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw closedFailure();
        }
    }

    private static boolean exactGeneration(double supplied, long expected) {
        return Double.isFinite(supplied)
                && supplied >= 0.0
                && supplied <= 9_007_199_254_740_991.0
                && supplied == expected;
    }

    private static boolean exactSequence(double supplied, long previous) {
        return Double.isFinite(supplied)
                && supplied >= 0.0
                && supplied <= 9_007_199_254_740_991.0
                && supplied == Math.rint(supplied)
                && supplied > previous;
    }

    private boolean takeSettledToken() {
        long current = nanoTime.getAsLong();
        long elapsed = Math.max(0, current - settledRefillNanos);
        settledRefillNanos = current;
        settledTokens = Math.min(10.0, settledTokens + elapsed * (10.0 / 1_000_000_000.0));
        if (settledTokens < 1.0) {
            return false;
        }
        settledTokens -= 1.0;
        return true;
    }

    private void scheduleSettledFlush() {
        if (settledFlushScheduled) {
            return;
        }
        settledFlushScheduled = true;
        settledScheduleEpoch = Math.incrementExact(settledScheduleEpoch);
        long scheduledEpoch = settledScheduleEpoch;
        settledScheduler.accept(() -> flushSettledViewport(scheduledEpoch));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void dispatchToUi(Runnable action) {
        getUI().ifPresent(ui -> ui.access(action::run));
    }

    private void flushSettledViewport(long scheduledEpoch) {
        if (scheduledEpoch != settledScheduleEpoch) {
            return;
        }
        settledFlushScheduled = false;
        if (closed || pendingSettledViewport == null) {
            return;
        }
        if (!takeSettledToken()) {
            scheduleSettledFlush();
            return;
        }
        MapViewport accepted = pendingSettledViewport;
        pendingSettledViewport = null;
        acceptViewport(accepted);
    }

    private void acceptViewport(MapViewport accepted) {
        viewport = accepted;
        boolean clearedHover = hover.isPresent();
        Throwable primary = null;
        primary = cleanup(primary, () -> transitionInteraction(selection, Optional.empty()));
        diagnostic = Optional.empty();
        primary = cleanup(primary, this::requestCurrentLabelMeasurements);
        if (!clearedHover) {
            primary = cleanup(primary, this::publishInteractionOverlay);
        }
        for (Consumer<MapViewport> listener : List.copyOf(viewportListeners)) {
            primary = cleanup(primary, () -> listener.accept(accepted));
        }
        primary = cleanup(primary, this::scheduleSourceQuery);
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    private void scheduleSourceQuery() {
        cancelFeatureQuery();
        if (featureBindings.isEmpty()) {
            if (!sourceLayers.isEmpty()) {
                activeQueryCancellation = new CancellationSource();
                applySourceQueryResult(
                        queryGeneration,
                        new FeatureSourceQueryEngine.Result(List.of(), Map.of(), false));
            }
            return;
        }
        if (!isEnabled() || (!queryWhenDetached && getUI().isEmpty())) {
            return;
        }
        long generation = queryGeneration;
        CancellationSource cancellation = new CancellationSource();
        activeQueryCancellation = cancellation;
        List<FeatureSourceQueryEngine.RequestBinding> bindings = List.copyOf(featureBindings);
        MapViewport requestedViewport = viewport;
        CrsRegistry requestedRegistry = crsRegistry;
        CrsDefinition requestedMapCrs = mapCrs;
        CrsDefinition requestedDisplayCrs = displayCrs;
        queryExecutor.execute(
                () -> {
                    FeatureSourceQueryEngine.Result result =
                            sourceQueryEngine.query(
                                    bindings,
                                    requestedViewport,
                                    requestedRegistry,
                                    requestedMapCrs,
                                    requestedDisplayCrs,
                                    cancellation.token());
                    queryCompletionDispatcher.accept(
                            () -> applySourceQueryResult(generation, result));
                });
    }

    private void applySourceQueryResult(
            long generation, FeatureSourceQueryEngine.Result queryResult) {
        if (closed
                || generation != queryGeneration
                || queryResult.cancelled()
                || activeQueryCancellation.token().isCancellationRequested()) {
            return;
        }
        long nextSceneGeneration = Math.incrementExact(sceneGeneration);
        try {
            StagedScene staged =
                    stageScene(
                            combine(layers, queryResult.layers()), background, nextSceneGeneration);
            SceneProtocol.Result encoded = staged.result();
            int snapshotCount = layers.size();
            sourceLayers =
                    List.copyOf(encoded.layers().subList(snapshotCount, encoded.layers().size()));
            sceneEnvelope = encoded.envelope();
            sceneGeneration = nextSceneGeneration;
            diagnostic = Optional.empty();
            reconcileSourceReports(queryResult.reports());
            publishStagedScene(staged);
            publishViewport();
            drainSourceReportNotifications();
        } catch (MundaneMapException exception) {
            diagnostic = Optional.of(exception);
        }
    }

    private StagedScene stageScene(
            List<? extends Layer> stagedLayers, Rgba stagedBackground, long stagedGeneration) {
        IconResourceBatch.Registrar registrar = iconSessionAccess.resourceRegistrar(this);
        IconResourceBatch resources =
                IconResourceBatch.prepare(stagedLayers, this::isAuthorizedIcon, registrar);
        try {
            SceneProtocol.Result result =
                    protocol.encode(
                            stagedLayers,
                            stagedBackground,
                            viewport,
                            componentGeneration,
                            stagedGeneration,
                            viewportGeneration,
                            resources);
            return new StagedScene(result, resources);
        } catch (RuntimeException | Error failure) {
            resources.close();
            throw failure;
        }
    }

    private boolean isAuthorizedIcon(RasterIconSymbol icon) {
        for (FeatureSourceQueryEngine.RequestBinding binding : featureBindings) {
            if (binding.binding().authorizes(icon)) {
                return true;
            }
        }
        return false;
    }

    private void acceptIconResources(IconResourceBatch replacement) {
        IconResourceBatch previous = iconResources;
        iconResources = replacement;
        previous.close();
    }

    private void publishStagedScene(StagedScene staged) {
        Throwable cancellationFailure = null;
        if (toolRouter.activeTool().isPresent()
                && (toolRouter.captured() || !lastButtonsDown.isEmpty())) {
            cancellationFailure =
                    cleanup(
                            null,
                            () -> cancelToolInteraction(MapToolCancelReason.POINTER_STATE_LOST));
        }
        toolPointerRateQuarantined = false;
        toolPointerTokens = 120.0;
        toolPointerRefillNanos = nanoTime.getAsLong();
        lastButtonsDown = Set.of();
        try {
            publishScene(staged.result().scene());
        } catch (RuntimeException | Error failure) {
            staged.resources().close();
            if (cancellationFailure != null && cancellationFailure != failure) {
                failure.addSuppressed(cancellationFailure);
            }
            throw failure;
        }
        try {
            acceptIconResources(staged.resources());
            currentSceneLayers = staged.result().layers();
            paintedFeatures = paintedFeatures(staged.result().scene(), currentSceneLayers);
            currentLabelCandidates = staged.result().labelCandidates();
            prepareCurrentLabelState();
            reconcileInteractionIdentities();
            publishInteractionOverlay();
        } catch (RuntimeException | Error failure) {
            if (cancellationFailure != null && cancellationFailure != failure) {
                failure.addSuppressed(cancellationFailure);
            }
            throw failure;
        }
        if (cancellationFailure != null) {
            throwUnchecked(cancellationFailure);
        }
    }

    private void releaseIconResources() {
        IconResourceBatch previous = iconResources;
        iconResources = IconResourceBatch.empty();
        previous.close();
    }

    void handleIconSessionDestroy() {
        releaseIconResources();
        clearBrowserLabelState(true);
    }

    private void removeIconSessionCleanup() {
        if (iconSessionCleanup != null) {
            iconSessionCleanup.remove();
            iconSessionCleanup = null;
        }
    }

    private void cancelFeatureQuery() {
        activeQueryCancellation.cancel();
        queryGeneration = Math.incrementExact(queryGeneration);
    }

    private void releaseBindings(List<FeatureSourceBinding> bindingsToRelease) {
        Throwable primary = null;
        for (FeatureSourceBinding binding : bindingsToRelease) {
            try {
                binding.release(this);
            } catch (RuntimeException | Error failure) {
                if (primary == null) {
                    primary = failure;
                } else if (primary != failure) {
                    primary.addSuppressed(failure);
                }
            }
        }
        if (primary instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (primary instanceof Error errorFailure) {
            throw errorFailure;
        }
    }

    private void reconcileSourceReports(Map<String, DiagnosticReport> nextReports) {
        for (String existing : List.copyOf(sourceReports.keySet())) {
            if (!nextReports.containsKey(existing)) {
                transitionSourceReport(existing, Optional.empty());
            }
        }
        for (FeatureSourceQueryEngine.RequestBinding binding : featureBindings) {
            String id = binding.binding().id();
            if (nextReports.containsKey(id)) {
                transitionSourceReport(id, Optional.ofNullable(nextReports.get(id)));
            }
        }
    }

    private void transitionSourceReport(String bindingId, Optional<DiagnosticReport> next) {
        Optional<DiagnosticReport> previous = Optional.ofNullable(sourceReports.get(bindingId));
        if (previous.equals(next)) {
            return;
        }
        LinkedHashMap<String, DiagnosticReport> replacement = new LinkedHashMap<>(sourceReports);
        if (next.isPresent()) {
            replacement.put(bindingId, next.orElseThrow());
        } else {
            replacement.remove(bindingId);
        }
        sourceReports = java.util.Collections.unmodifiableMap(replacement);
        sourceReportNotifications.addLast(new MapSourceReportEvent(bindingId, previous, next));
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

    private void validateFeatureBindings(List<FeatureSourceBinding> candidates) {
        Set<String> ids = new HashSet<>();
        for (Layer layer : layers) {
            ids.add(layer.id());
        }
        IdentityHashMap<io.github.mundanej.map.api.FeatureSource, FeatureSourceBinding> installed =
                new IdentityHashMap<>();
        for (FeatureSourceBinding binding : featureSourceBindings()) {
            installed.put(binding.source(), binding);
        }
        Set<io.github.mundanej.map.api.FeatureSource> sources =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (FeatureSourceBinding binding : candidates) {
            Objects.requireNonNull(binding, "binding");
            if (!ids.add(binding.id())) {
                throw duplicateLayerIdentity();
            }
            if (!sources.add(binding.source())) {
                throw new IllegalArgumentException(
                        "A feature source may appear in only one installed binding");
            }
            FeatureSourceBinding existing = installed.get(binding.source());
            if (existing != null && existing != binding) {
                throw new IllegalArgumentException(
                        "An installed feature source must retain its binding instance");
            }
        }
    }

    private void validateSnapshotBindingIds(List<? extends Layer> snapshots) {
        Set<String> ids = new HashSet<>();
        for (FeatureSourceQueryEngine.RequestBinding binding : featureBindings) {
            ids.add(binding.binding().id());
        }
        for (Layer snapshot : snapshots) {
            if (!ids.add(snapshot.id())) {
                throw duplicateLayerIdentity();
            }
        }
    }

    private static MundaneMapException duplicateLayerIdentity() {
        return new MundaneMapException(
                MundaneMapException.DUPLICATE_ID,
                "Duplicate browser layer identity",
                Map.of("identityNamespace", "layer"));
    }

    private List<Layer> combinedLayers() {
        return combine(layers, sourceLayers);
    }

    private static List<Layer> combine(List<? extends Layer> first, List<? extends Layer> second) {
        ArrayList<Layer> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    private static Set<FeatureSourceBinding> identitySet(List<FeatureSourceBinding> values) {
        Set<FeatureSourceBinding> identities =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(values);
        return identities;
    }

    private static List<Layer> configuredFeatureLayers(List<FeatureSourceBinding> bindings) {
        return bindings.stream()
                .<Layer>map(binding -> new ConfiguredFeatureLayer(binding.id(), binding.name()))
                .toList();
    }

    private void resetClientEventState() {
        clientEventSequence = -1;
        transientViewportPermitSequence = -1;
        settledTokens = 10.0;
        settledRefillNanos = nanoTime.getAsLong();
        hoverTokens = 20.0;
        hoverRefillNanos = settledRefillNanos;
        toolPointerTokens = 120.0;
        toolPointerRefillNanos = settledRefillNanos;
        toolPointerRateQuarantined = false;
        cancelPendingHover();
        cancelPendingSettledViewport();
    }

    private static Throwable cleanup(Throwable primary, Runnable action) {
        try {
            action.run();
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

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("Unexpected checked failure", failure);
    }

    private static RuntimeException propagated(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Unexpected checked failure", failure);
    }

    private void cancelPendingSettledViewport() {
        pendingSettledViewport = null;
        settledFlushScheduled = false;
        settledScheduleEpoch = Math.incrementExact(settledScheduleEpoch);
    }

    private static MundaneMapException closedFailure() {
        return new MundaneMapException(
                MundaneMapException.CLOSED,
                "MundaneMap is closed",
                Map.of("component", MundaneMap.class.getSimpleName()));
    }

    private static MundaneMapException disabledFailure() {
        return new MundaneMapException(
                MundaneMapException.DISABLED,
                "MundaneMap is disabled",
                Map.of("component", MundaneMap.class.getSimpleName()));
    }

    private static MundaneMapException failure(
            String code, String message, String contextName, String contextValue) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put(contextName, Objects.requireNonNullElse(contextValue, "unspecified"));
        return new MundaneMapException(code, message, context);
    }

    private interface LabelGeneration {
        long componentGeneration();

        long sceneGeneration();

        long viewportGeneration();
    }

    private record PendingLabelMeasurements(
            long componentGeneration,
            long sceneGeneration,
            long viewportGeneration,
            List<Layer> layers,
            List<SceneLabelCandidate> candidates,
            MapViewport viewport,
            Rgba background)
            implements LabelGeneration {
        private PendingLabelMeasurements {
            layers = List.copyOf(layers);
            candidates = List.copyOf(candidates);
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(background, "background");
        }
    }

    private record PendingPlacedLabels(
            long componentGeneration,
            long sceneGeneration,
            long viewportGeneration,
            List<Layer> layers,
            MapViewport viewport,
            Rgba background,
            List<PlacedPointLabel> labels)
            implements LabelGeneration {
        private PendingPlacedLabels {
            layers = List.copyOf(layers);
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(background, "background");
            labels = List.copyOf(labels);
        }
    }

    private record BrowserCaptureState(
            long componentGeneration,
            long sceneGeneration,
            long viewportGeneration,
            List<Layer> layers,
            MapViewport viewport,
            Rgba background,
            List<PlacedPointLabel> labels)
            implements LabelGeneration {
        private BrowserCaptureState {
            layers = List.copyOf(layers);
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(background, "background");
            labels = List.copyOf(labels);
        }
    }

    private record HoverProbe(
            long componentGeneration,
            long sceneGeneration,
            long viewportGeneration,
            double screenX,
            double screenY,
            MapViewport viewport,
            Optional<io.github.mundanej.map.api.Coordinate> mapCoordinate) {
        private HoverProbe {
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(mapCoordinate, "mapCoordinate");
        }
    }

    private record InteractionNotification(MapSelectionEvent selection, MapHoverEvent hover) {
        private static InteractionNotification selection(MapSelectionEvent event) {
            return new InteractionNotification(Objects.requireNonNull(event, "event"), null);
        }

        private static InteractionNotification hover(MapHoverEvent event) {
            return new InteractionNotification(null, Objects.requireNonNull(event, "event"));
        }
    }

    private final class ToolContextSnapshot implements MapToolContext {
        private final MapViewport contextViewport;
        private final CrsRegistry contextRegistry;
        private final CrsDefinition contextMapCrs;
        private final CrsDefinition contextDisplayCrs;

        private ToolContextSnapshot(
                MapViewport contextViewport,
                CrsRegistry contextRegistry,
                CrsDefinition contextMapCrs,
                CrsDefinition contextDisplayCrs) {
            this.contextViewport = contextViewport;
            this.contextRegistry = contextRegistry;
            this.contextMapCrs = contextMapCrs;
            this.contextDisplayCrs = contextDisplayCrs;
        }

        @Override
        public CrsDefinition mapCrs() {
            return contextMapCrs;
        }

        private MapViewport viewport() {
            return contextViewport;
        }

        @Override
        public CrsDefinition displayCrs() {
            return contextDisplayCrs;
        }

        @Override
        public Optional<io.github.mundanej.map.api.Coordinate> mapToScreen(
                io.github.mundanej.map.api.Coordinate coordinate) {
            try {
                return Optional.of(
                        contextViewport.worldToScreen(
                                contextRegistry
                                        .operation(contextMapCrs, contextDisplayCrs)
                                        .transform(coordinate)));
            } catch (io.github.mundanej.map.api.CrsException | IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<io.github.mundanej.map.api.Coordinate> screenToMap(
                double screenX, double screenY) {
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
                throw new IllegalArgumentException("Screen coordinates must be finite");
            }
            try {
                return Optional.of(
                        contextRegistry
                                .operation(contextDisplayCrs, contextMapCrs)
                                .transform(contextViewport.screenToWorld(screenX, screenY)));
            } catch (io.github.mundanej.map.api.CrsException | IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        @Override
        public void requestRepaint() {
            getElement().callJsFunction("requestMapPaint");
        }
    }

    private record ConfiguredFeatureLayer(String id, String name) implements Layer {
        @Override
        public List<io.github.mundanej.map.api.Feature> features() {
            return List.of();
        }

        @Override
        public Optional<Envelope> envelope() {
            return Optional.empty();
        }
    }

    private record StagedScene(SceneProtocol.Result result, IconResourceBatch resources) {
        private StagedScene {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(resources, "resources");
        }
    }

    interface IconSessionAccess {
        IconResourceBatch.Registrar resourceRegistrar(MundaneMap map);

        Registration addDestroyListener(MundaneMap map, Runnable listener);

        static IconSessionAccess vaadin() {
            return new IconSessionAccess() {
                @Override
                public IconResourceBatch.Registrar resourceRegistrar(MundaneMap map) {
                    return map.getUI()
                            .<IconResourceBatch.Registrar>map(
                                    ui -> IconResourceBatch.vaadin(ui.getSession()))
                            .orElse(
                                    bytes -> {
                                        throw new MundaneMapException(
                                                MundaneMapException.RESOURCE_UNAVAILABLE,
                                                "Raster icon resources require an attached session",
                                                Map.of("resourceKind", "catalog-icon"));
                                    });
                }

                @Override
                public Registration addDestroyListener(MundaneMap map, Runnable listener) {
                    return map.getUI()
                            .<Registration>map(
                                    ui ->
                                            ui.getSession()
                                                    .addSessionDestroyListener(
                                                            event -> listener.run()))
                            .orElse(null);
                }
            };
        }
    }
}
