package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.DistanceStrategy;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
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
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.DistanceStrategies;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * View-bound browser measurement controller using toolkit-neutral tool events and distance values.
 *
 * <p>Committed vertices, preview, segment distances, and cumulative distance are exposed through
 * immutable {@link MeasurementState} snapshots. The browser overlay is presentation-only and never
 * becomes ordinary scene content.
 */
public final class BrowserMeasurementTool implements MapTool, BrowserBoundTool {
    /** Default maximum committed vertex count ({@value}). */
    public static final int DEFAULT_VERTEX_LIMIT = 10_000;

    private static final SolidLineSymbol LINE =
            SolidLineSymbol.of(
                    new SymbolStroke(
                            Rgba.rgb(190, 35, 55), new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                    1.0);
    private static final SolidLineSymbol PREVIEW_LINE =
            SolidLineSymbol.of(
                    new SymbolStroke(
                            new Rgba(190, 35, 55, 180),
                            new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                    1.0);
    private static final io.github.mundanej.map.api.VectorMarkerSymbol VERTEX =
            BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(190, 35, 55), 8.0, 1.0);

    private final MundaneMap host;
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

    /**
     * Creates a controller with the default bounded vertex limit.
     *
     * @param host exact host component
     * @param strategy non-null CRS-bound distance strategy
     */
    public BrowserMeasurementTool(MundaneMap host, DistanceStrategy strategy) {
        this(host, strategy, DEFAULT_VERTEX_LIMIT);
    }

    /**
     * Creates a controller with an explicit vertex limit of at least two.
     *
     * @param host exact host component
     * @param strategy non-null CRS-bound distance strategy
     * @param vertexLimit maximum committed vertices, at least two
     * @throws IllegalArgumentException if the limit is less than two
     */
    public BrowserMeasurementTool(MundaneMap host, DistanceStrategy strategy, int vertexLimit) {
        this.host = Objects.requireNonNull(host, "host");
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

    /**
     * Returns the CRS-bound distance strategy.
     *
     * @return strategy supplied at construction
     */
    public DistanceStrategy distanceStrategy() {
        return strategy;
    }

    /**
     * Returns the immutable current measurement snapshot.
     *
     * @return current state
     */
    public MeasurementState state() {
        return state;
    }

    /**
     * Returns the configured maximum committed vertex count.
     *
     * @return positive maximum vertex count
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
            case MOVE -> move(event, context);
            case CLICK -> click(event, context);
            case PRESS -> {
                clearPreview(context);
                yield MapToolResult.PASS;
            }
            case CANCEL -> cancel(event.cancelReason().orElseThrow(), context);
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

    @Override
    public boolean belongsTo(MundaneMap candidate) {
        return host == candidate;
    }

    void requireMapCrs(io.github.mundanej.map.api.CrsDefinition candidate) {
        DistanceStrategies.requireCoordinateCrs(strategy, candidate);
    }

    @Override
    public List<Layer> overlayLayers() {
        if (vertexCount == 0) {
            return List.of();
        }
        java.util.ArrayList<Feature> features = new java.util.ArrayList<>(3);
        if (vertexCount >= 2) {
            features.add(
                    new Feature(
                            "path",
                            "Measurement path",
                            display(new LineStringGeometry(committedSequence())),
                            Map.of(),
                            LINE));
        }
        features.add(
                new Feature(
                        "vertices",
                        "Measurement vertices",
                        display(new MultiPointGeometry(committedSequence())),
                        Map.of(),
                        VERTEX));
        if (preview.isPresent()) {
            Coordinate last = vertex(vertexCount - 1);
            features.add(
                    new Feature(
                            "preview",
                            "Measurement preview",
                            display(
                                    new LineStringGeometry(
                                            CoordinateSequence.of(
                                                    last.x(),
                                                    last.y(),
                                                    preview.orElseThrow().x(),
                                                    preview.orElseThrow().y()))),
                            Map.of(),
                            PREVIEW_LINE));
        }
        return List.of(
                new io.github.mundanej.map.core.InMemoryLayer(
                        "__measurement", "Measurement", features));
    }

    private io.github.mundanej.map.api.Geometry display(
            io.github.mundanej.map.api.Geometry geometry) {
        return FeatureSourceQueryEngine.transformGeometry(
                geometry,
                host.crsOperation(host.mapCrs(), host.mapCrs()),
                host.crsOperation(host.mapCrs(), host.displayCrs()),
                CancellationToken.none());
    }

    private CoordinateSequence committedSequence() {
        return CoordinateSequence.of(Arrays.copyOf(vertices, vertexCount * 2));
    }

    private MapToolResult move(MapToolEvent event, MapToolContext context) {
        if (phase != MeasurementPhase.MEASURING || vertexCount == 0) {
            return MapToolResult.CONSUME;
        }
        Optional<Coordinate> requested = event.mapCoordinate();
        if (requested.isEmpty()) {
            clearPreview(context);
            return MapToolResult.CONSUME;
        }
        Coordinate next = requested.orElseThrow();
        DistanceResult distance = strategy.distance(vertex(vertexCount - 1), next);
        new DistanceResult(cumulativeMetres[vertexCount - 1]).plus(distance);
        if (!preview.equals(requested) || !previewDistance.equals(Optional.of(distance))) {
            preview = requested;
            previewDistance = Optional.of(distance);
            publish();
            context.requestRepaint();
        }
        return MapToolResult.CONSUME;
    }

    private MapToolResult click(MapToolEvent event, MapToolContext context) {
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

    private MapToolResult cancel(MapToolCancelReason reason, MapToolContext context) {
        if (reason == MapToolCancelReason.USER_CANCEL
                || reason == MapToolCancelReason.SOURCE_FAILURE) {
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
