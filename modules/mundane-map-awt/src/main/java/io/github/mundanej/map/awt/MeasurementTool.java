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

/** Swing measurement tool with packed vertices and an immutable public state snapshot. */
public final class MeasurementTool implements MapTool {
    /** Default maximum committed vertex count. */
    public static final int DEFAULT_VERTEX_LIMIT = 10_000;

    private final DistanceStrategy strategy;
    private final int vertexLimit;
    private double[] vertices;
    private double[] cumulativeMetres;
    private double[] segmentMetres;
    private int vertexCount;
    private Optional<Coordinate> preview = Optional.empty();
    private Optional<DistanceResult> previewDistance = Optional.empty();
    private MeasurementPhase phase = MeasurementPhase.EMPTY;
    private MeasurementState state = MeasurementState.empty();
    private MapView owner;

    /** Creates a tool with the default bounded vertex limit. */
    public MeasurementTool(DistanceStrategy strategy) {
        this(strategy, DEFAULT_VERTEX_LIMIT);
    }

    /** Creates a tool with an explicit vertex limit of at least two. */
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
    }

    /** Returns the CRS-bound distance strategy. */
    public DistanceStrategy distanceStrategy() {
        return strategy;
    }

    /** Returns the immutable current state snapshot. */
    public MeasurementState state() {
        return state;
    }

    /** Returns the configured maximum vertex count. */
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
            case MOVE -> handleMove(event.mapCoordinate(), context);
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

    private MapToolResult handleMove(Optional<Coordinate> coordinate, MapToolContext context) {
        if (phase != MeasurementPhase.MEASURING || vertexCount == 0) {
            return MapToolResult.CONSUME;
        }
        Optional<Coordinate> requested = Objects.requireNonNull(coordinate, "coordinate");
        if (requested.isEmpty()) {
            clearPreview(context);
            return MapToolResult.CONSUME;
        }
        Coordinate next = requested.orElseThrow();
        DistanceResult nextDistance = strategy.distance(vertex(vertexCount - 1), next);
        new DistanceResult(cumulativeMetres[vertexCount - 1]).plus(nextDistance);
        if (!preview.equals(requested) || !previewDistance.equals(Optional.of(nextDistance))) {
            preview = requested;
            previewDistance = Optional.of(nextDistance);
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
            append(coordinate);
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

    private void append(Coordinate coordinate) {
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
        cumulativeMetres[vertexCount] = cumulative.metres();
        vertexCount++;
        preview = Optional.empty();
        previewDistance = Optional.empty();
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
        vertices = Arrays.copyOf(vertices, next * 2);
        cumulativeMetres = Arrays.copyOf(cumulativeMetres, next);
        segmentMetres = Arrays.copyOf(segmentMetres, next);
    }

    private Coordinate vertex(int index) {
        return new Coordinate(vertices[index * 2], vertices[index * 2 + 1]);
    }

    private void clearPreview(MapToolContext context) {
        if (preview.isPresent()) {
            preview = Optional.empty();
            previewDistance = Optional.empty();
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
}
