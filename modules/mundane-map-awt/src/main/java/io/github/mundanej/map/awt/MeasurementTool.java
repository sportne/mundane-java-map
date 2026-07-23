package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.DistanceStrategy;
import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolCommand;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.core.DistanceStrategies;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Swing measurement tool with packed vertices and an immutable public state snapshot.
 *
 * <p>The tool is confined to Swing's event-dispatch thread and may be installed in at most one
 * {@link MapView}. Its {@link MeasurementState} snapshots remain immutable after later interaction.
 * Distances and cumulative totals are expressed in metres as required by {@link DistanceStrategy}.
 */
public final class MeasurementTool implements MapTool {
    /** Default maximum committed vertex count ({@value}). */
    public static final int DEFAULT_VERTEX_LIMIT = 10_000;

    private final DistanceStrategy strategy;
    private final int vertexLimit;
    private double[] vertices;
    private double[] cumulativeMetres;
    private double[] segmentMetres;
    private double[] displayReferenceXs;
    private int vertexCount;
    private Optional<Coordinate> preview = Optional.empty();
    private Optional<DistanceResult> previewDistance = Optional.empty();
    private OptionalDouble previewDisplayReferenceX = OptionalDouble.empty();
    private MeasurementPhase phase = MeasurementPhase.EMPTY;
    private MeasurementState state = MeasurementState.empty();
    private MapView owner;

    /**
     * Creates a tool with the default bounded vertex limit.
     *
     * @param strategy non-null CRS-bound distance strategy
     * @throws NullPointerException if {@code strategy} is {@code null}
     */
    public MeasurementTool(DistanceStrategy strategy) {
        this(strategy, DEFAULT_VERTEX_LIMIT);
    }

    /**
     * Creates a tool with an explicit vertex limit of at least two.
     *
     * @param strategy non-null CRS-bound distance strategy
     * @param vertexLimit maximum committed vertices, at least two
     * @throws NullPointerException if {@code strategy} is {@code null}
     * @throws IllegalArgumentException if {@code vertexLimit} is less than two
     */
    public MeasurementTool(DistanceStrategy strategy, int vertexLimit) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        if (vertexLimit < 2) {
            throw new IllegalArgumentException("vertexLimit must be at least two");
        }
        this.vertexLimit = vertexLimit;
        int initial = Math.min(16, vertexLimit);
        vertices = new double[initial * 2];
        cumulativeMetres = new double[initial];
        segmentMetres = new double[initial];
        displayReferenceXs = new double[initial];
        Arrays.fill(displayReferenceXs, Double.NaN);
    }

    /**
     * Returns the CRS-bound distance strategy.
     *
     * @return strategy supplied at construction
     */
    public DistanceStrategy distanceStrategy() {
        return strategy;
    }

    /**
     * Returns the immutable current state snapshot.
     *
     * @return state from the most recently processed interaction
     */
    public MeasurementState state() {
        return state;
    }

    /**
     * Returns the configured maximum vertex count.
     *
     * @return positive maximum number of committed vertices
     */
    public int vertexLimit() {
        return vertexLimit;
    }

    @Override
    public void onActivate(MapToolContext context) {
        DistanceStrategies.requireCoordinateCrs(strategy, context.mapCrs());
    }

    @Override
    public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        return switch (event.type()) {
            case MOVE -> handleMove(event, context);
            case CLICK -> handleClick(event, context);
            case PRESS -> {
                clearPreview(context);
                yield MapToolResult.PASS;
            }
            case CANCEL -> handleCancel(event.cancelReason().orElseThrow(), context);
            case DRAG, RELEASE, WHEEL -> MapToolResult.PASS;
        };
    }

    @Override
    public MapToolResult onMapToolCommand(MapToolCommandEvent event, MapToolContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        if (event.command() != MapToolCommand.DELETE_BACKWARD || vertexCount == 0) {
            return MapToolResult.PASS;
        }
        vertexCount--;
        preview = Optional.empty();
        previewDistance = Optional.empty();
        previewDisplayReferenceX = OptionalDouble.empty();
        phase = vertexCount == 0 ? MeasurementPhase.EMPTY : MeasurementPhase.MEASURING;
        publish();
        context.requestRepaint();
        return MapToolResult.CONSUME;
    }

    @Override
    public void onDeactivate(MapToolContext context) {
        if (clearState()) {
            context.requestRepaint();
        }
    }

    @Override
    public MapCursorIntent cursorIntent() {
        return MapCursorIntent.CROSSHAIR;
    }

    void claim(MapView requestedOwner) {
        Objects.requireNonNull(requestedOwner, "requestedOwner");
        if (owner != null && owner != requestedOwner) {
            throw new IllegalStateException("MeasurementTool is already installed in another view");
        }
        owner = requestedOwner;
    }

    void release(MapView releasingOwner) {
        if (owner == releasingOwner) {
            owner = null;
        }
    }

    private MapToolResult handleMove(MapToolEvent event, MapToolContext context) {
        if (phase != MeasurementPhase.MEASURING || vertexCount == 0) {
            return MapToolResult.CONSUME;
        }
        Optional<Coordinate> requested =
                Objects.requireNonNull(event.mapCoordinate(), "coordinate");
        if (requested.isEmpty()) {
            clearPreview(context);
            return MapToolResult.CONSUME;
        }
        Coordinate next = requested.orElseThrow();
        DistanceResult nextDistance = strategy.distance(vertex(vertexCount - 1), next);
        new DistanceResult(cumulativeMetres[vertexCount - 1]).plus(nextDistance);
        OptionalDouble displayReference = displayReference(event);
        if (!preview.equals(requested)
                || !previewDistance.equals(Optional.of(nextDistance))
                || !same(previewDisplayReferenceX, displayReference)) {
            preview = requested;
            previewDistance = Optional.of(nextDistance);
            previewDisplayReferenceX = displayReference;
            publish();
            context.requestRepaint();
        }
        return MapToolResult.CONSUME;
    }

    private MapToolResult handleClick(MapToolEvent event, MapToolContext context) {
        boolean qualifying =
                event.button().equals(MapPointerButton.PRIMARY)
                        && event.modifiers().isEmpty()
                        && !event.popupTrigger();
        if (!qualifying) {
            return MapToolResult.PASS;
        }
        if (event.clickCount() > 1) {
            if (phase == MeasurementPhase.MEASURING && vertexCount >= 2) {
                phase = MeasurementPhase.COMPLETE;
                preview = Optional.empty();
                previewDistance = Optional.empty();
                previewDisplayReferenceX = OptionalDouble.empty();
                publish();
                context.requestRepaint();
            }
            return MapToolResult.CONSUME;
        }
        if (event.mapCoordinate().isPresent()) {
            Coordinate coordinate = event.mapCoordinate().orElseThrow();
            strategy.distance(coordinate, coordinate);
            if (phase == MeasurementPhase.COMPLETE) {
                clearState();
            }
            append(coordinate, displayReference(event));
            if (vertexCount == vertexLimit) {
                phase = MeasurementPhase.COMPLETE;
            }
            publish();
            context.requestRepaint();
        }
        return MapToolResult.CONSUME;
    }

    private MapToolResult handleCancel(MapToolCancelReason reason, MapToolContext context) {
        if (reason == MapToolCancelReason.USER_CANCEL) {
            if (clearState()) {
                context.requestRepaint();
                return MapToolResult.CONSUME;
            }
            return MapToolResult.PASS;
        }
        clearPreview(context);
        return MapToolResult.PASS;
    }

    private void append(Coordinate coordinate, OptionalDouble displayReference) {
        DistanceResult segment = DistanceResult.ZERO;
        DistanceResult cumulative = DistanceResult.ZERO;
        if (vertexCount > 0) {
            segment = strategy.distance(vertex(vertexCount - 1), coordinate);
            cumulative = new DistanceResult(cumulativeMetres[vertexCount - 1]).plus(segment);
        }
        ensureCapacity(vertexCount + 1);
        vertices[vertexCount * 2] = coordinate.x();
        vertices[vertexCount * 2 + 1] = coordinate.y();
        segmentMetres[vertexCount] = segment.metres();
        displayReferenceXs[vertexCount] = displayReference.orElse(Double.NaN);
        cumulativeMetres[vertexCount] = cumulative.metres();
        vertexCount++;
        preview = Optional.empty();
        previewDistance = Optional.empty();
        previewDisplayReferenceX = OptionalDouble.empty();
        phase = MeasurementPhase.MEASURING;
    }

    private void ensureCapacity(int required) {
        if (required > vertexLimit) {
            throw new IllegalStateException("measurement vertex limit reached");
        }
        if (required <= cumulativeMetres.length) {
            return;
        }
        int next = Math.min(vertexLimit, Math.max(required, cumulativeMetres.length * 2));
        int previous = displayReferenceXs.length;
        vertices = Arrays.copyOf(vertices, next * 2);
        cumulativeMetres = Arrays.copyOf(cumulativeMetres, next);
        segmentMetres = Arrays.copyOf(segmentMetres, next);
        displayReferenceXs = Arrays.copyOf(displayReferenceXs, next);
        Arrays.fill(displayReferenceXs, previous, next, Double.NaN);
    }

    private Coordinate vertex(int index) {
        return new Coordinate(vertices[index * 2], vertices[index * 2 + 1]);
    }

    private void clearPreview(MapToolContext context) {
        if (preview.isPresent()) {
            preview = Optional.empty();
            previewDistance = Optional.empty();
            previewDisplayReferenceX = OptionalDouble.empty();
            publish();
            context.requestRepaint();
        }
    }

    private boolean clearState() {
        if (phase == MeasurementPhase.EMPTY) {
            return false;
        }
        vertexCount = 0;
        phase = MeasurementPhase.EMPTY;
        preview = Optional.empty();
        previewDistance = Optional.empty();
        previewDisplayReferenceX = OptionalDouble.empty();
        state = MeasurementState.empty();
        return true;
    }

    private void publish() {
        if (phase == MeasurementPhase.EMPTY) {
            state = MeasurementState.empty();
            return;
        }
        state =
                new MeasurementState(
                        phase,
                        Arrays.copyOf(vertices, vertexCount * 2),
                        preview,
                        new DistanceResult(cumulativeMetres[vertexCount - 1]),
                        vertexCount >= 2
                                ? Optional.of(new DistanceResult(segmentMetres[vertexCount - 1]))
                                : Optional.empty(),
                        previewDistance);
    }

    OverlayState overlayState() {
        return new OverlayState(
                state, Arrays.copyOf(displayReferenceXs, vertexCount), previewDisplayReferenceX);
    }

    private OptionalDouble displayReference(MapToolEvent event) {
        if (owner == null || owner.horizontalWrap().isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(
                owner.viewport().screenToWorld(event.screenX(), event.screenY()).x());
    }

    private static boolean same(OptionalDouble first, OptionalDouble second) {
        return first.isPresent() == second.isPresent()
                && (first.isEmpty()
                        || Double.compare(first.orElseThrow(), second.orElseThrow()) == 0);
    }

    static final class OverlayState {
        private final MeasurementState state;
        private final double[] vertexDisplayReferenceXs;
        private final OptionalDouble previewDisplayReferenceX;

        OverlayState(
                MeasurementState state,
                double[] vertexDisplayReferenceXs,
                OptionalDouble previewDisplayReferenceX) {
            this.state = Objects.requireNonNull(state, "state");
            this.vertexDisplayReferenceXs =
                    Objects.requireNonNull(vertexDisplayReferenceXs, "vertexDisplayReferenceXs")
                            .clone();
            this.previewDisplayReferenceX =
                    Objects.requireNonNull(previewDisplayReferenceX, "previewDisplayReferenceX");
            if (vertexDisplayReferenceXs.length != state.vertexCount()) {
                throw new IllegalArgumentException(
                        "measurement display references must match vertices");
            }
        }

        MeasurementState state() {
            return state;
        }

        double[] vertexDisplayReferenceXs() {
            return vertexDisplayReferenceXs.clone();
        }

        OptionalDouble previewDisplayReferenceX() {
            return previewDisplayReferenceX;
        }
    }
}
