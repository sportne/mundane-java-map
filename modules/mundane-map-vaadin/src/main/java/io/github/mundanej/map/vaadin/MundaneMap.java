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
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.MapSourceReportEvent;
import io.github.mundanej.map.api.MapSourceReportListener;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
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
 * Explicit-catalog raster icons are served as expiring same-origin session resources. Legacy and
 * custom renderer values are not forwarded to the browser.
 */
@Tag("mundane-map-canvas")
@JsModule("./mundane-map-canvas.js")
@SuppressWarnings("serial")
public final class MundaneMap extends Component implements HasSize, HasEnabled, AutoCloseable {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

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
        diagnostic = Optional.empty();
        publishViewport();
        scheduleSourceQuery();
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
        HasEnabled.super.setEnabled(enabled);
        if (!enabled) {
            cancelPendingSettledViewport();
            cancelFeatureQuery();
        } else {
            scheduleSourceQuery();
        }
        getElement().callJsFunction("setMapEnabled", enabled);
    }

    /** Releases browser listeners, pending paints, registered listeners, and snapshot state. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        componentGeneration = Math.incrementExact(componentGeneration);
        getElement().callJsFunction("closeMap", SceneProtocol.VERSION, componentGeneration);
        viewportListeners.clear();
        sourceReportListeners.clear();
        sourceReportNotifications.clear();
        layers = List.of();
        sourceLayers = List.of();
        releaseIconResources();
        removeIconSessionCleanup();
        sceneEnvelope = Optional.empty();
        cancelPendingSettledViewport();
        cancelFeatureQuery();
        List<FeatureSourceBinding> released = featureSourceBindings();
        featureBindings = List.of();
        sourceReports = Map.of();
        if (!released.isEmpty()) {
            queryExecutor.execute(() -> releaseBindings(released));
        }
        if (ownedQueryExecutor != null) {
            ownedQueryExecutor.shutdown();
        }
        diagnostic = Optional.empty();
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
        scheduleSourceQuery();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (!closed) {
            getElement()
                    .callJsFunction("deactivateMap", SceneProtocol.VERSION, componentGeneration);
            componentGeneration = Math.incrementExact(componentGeneration);
            resetClientEventState();
            cancelFeatureQuery();
            sourceLayers = List.of();
            releaseIconResources();
            removeIconSessionCleanup();
        }
        super.onDetach(detachEvent);
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

    private void publishScene(Map<String, Object> scene) {
        getElement().callJsFunction("setScene", scene);
    }

    private void publishViewport() {
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
        diagnostic = Optional.empty();
        for (Consumer<MapViewport> listener : List.copyOf(viewportListeners)) {
            listener.accept(accepted);
        }
        scheduleSourceQuery();
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
        try {
            publishScene(staged.result().scene());
        } catch (RuntimeException | Error failure) {
            staged.resources().close();
            throw failure;
        }
        acceptIconResources(staged.resources());
    }

    private void releaseIconResources() {
        IconResourceBatch previous = iconResources;
        iconResources = IconResourceBatch.empty();
        previous.close();
    }

    void handleIconSessionDestroy() {
        releaseIconResources();
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
        settledTokens = 10.0;
        settledRefillNanos = nanoTime.getAsLong();
        cancelPendingSettledViewport();
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
