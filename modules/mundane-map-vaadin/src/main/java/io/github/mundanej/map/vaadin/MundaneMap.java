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
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A Vaadin Flow component that paints a bounded toolkit-neutral vector snapshot on a local Canvas.
 *
 * <p>All high-frequency navigation runs in the bundled custom element. Java receives only settled,
 * generation-checked viewport values. This first slice accepts points with centered fill-only
 * screen-pixel {@link io.github.mundanej.map.api.VectorMarkerSymbol}s, line strings with plain
 * screen-pixel {@link io.github.mundanej.map.api.SolidLineSymbol}s, and polygons with plain {@link
 * io.github.mundanej.map.api.SolidFillSymbol}s.
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
        this(System::nanoTime, null);
    }

    MundaneMap(LongSupplier nanoTime) {
        this(nanoTime, null);
    }

    MundaneMap(LongSupplier nanoTime, Consumer<Runnable> settledScheduler) {
        protocol = new SceneProtocol(SceneProtocol.DEFAULT_LIMITS);
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.settledScheduler =
                settledScheduler != null
                        ? settledScheduler
                        : action ->
                                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                                        .execute(() -> dispatchToUi(action));
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
        SceneProtocol.Result result =
                protocol.encode(
                        sourceLayers,
                        background,
                        viewport,
                        componentGeneration,
                        nextGeneration,
                        viewportGeneration);
        layers = result.layers();
        cancelPendingSettledViewport();
        sceneEnvelope = result.envelope();
        sceneGeneration = nextGeneration;
        diagnostic = Optional.empty();
        publishScene(result.scene());
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
     * Replaces the Canvas background color and republishes the current scene atomically.
     *
     * @param color non-null background color
     */
    public void setBackground(Rgba color) {
        requireOpen();
        Objects.requireNonNull(color, "color");
        long nextGeneration = Math.incrementExact(sceneGeneration);
        SceneProtocol.Result result =
                protocol.encode(
                        layers,
                        color,
                        viewport,
                        componentGeneration,
                        nextGeneration,
                        viewportGeneration);
        background = color;
        cancelPendingSettledViewport();
        sceneEnvelope = result.envelope();
        sceneGeneration = nextGeneration;
        diagnostic = Optional.empty();
        publishScene(result.scene());
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
                            MundaneMapException.BROWSER_CAPABILITY_UNSUPPORTED,
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
        layers = List.of();
        sceneEnvelope = Optional.empty();
        cancelPendingSettledViewport();
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
        SceneProtocol.Result result =
                protocol.encode(
                        layers,
                        background,
                        viewport,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration);
        getElement()
                .callJsFunction(
                        "activateMap", SceneProtocol.VERSION, componentGeneration, sceneGeneration);
        publishScene(result.scene());
        publishViewport();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (!closed) {
            getElement()
                    .callJsFunction("deactivateMap", SceneProtocol.VERSION, componentGeneration);
            componentGeneration = Math.incrementExact(componentGeneration);
            resetClientEventState();
        }
        super.onDetach(detachEvent);
    }

    Map<String, Object> encodedSceneForTest() {
        return protocol.encode(
                        layers,
                        background,
                        viewport,
                        componentGeneration,
                        sceneGeneration,
                        viewportGeneration)
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
}
