package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.DeleteFeature;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureEditNotificationException;
import io.github.mundanej.map.api.FeatureEditProblem;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.ReplaceFeature;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapQueryResult;
import io.github.mundanej.map.api.SnapQueryStatus;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapReferenceSet;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeatureSnapper;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.SnapQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * View-bound browser controller for bounded point create, move, delete, undo, redo, and snapping.
 *
 * <p>The controller borrows one installed {@link FeatureEditBinding}. Gesture previews are
 * immutable overlays; only release/click operations submit revision-checked core transactions.
 */
public final class BrowserPointEditController implements MapTool, BrowserBoundTool {
    /** Default snapping tolerance in logical screen pixels. */
    public static final double DEFAULT_SNAP_TOLERANCE_PIXELS = 8.0;

    /** Browser-profile aggregate snap ceilings. */
    public static final SnapLimits BROWSER_SNAP_LIMITS = new SnapLimits(256, 4096, 4096, 4096);

    /** Closed point-edit interaction mode. */
    public enum Mode {
        /** No coordinate-producing interaction is selected. */
        NONE,
        /** A primary click creates the configured point draft. */
        CREATE,
        /** A primary drag moves the currently selected editable point. */
        MOVE_SELECTED
    }

    private static final io.github.mundanej.map.api.VectorMarkerSymbol SNAPPED_MARKER =
            BuiltInMarkers.filledScreen(BuiltInMarker.CROSS, Rgba.rgb(35, 155, 75), 14.0, 1.0);
    private static final io.github.mundanej.map.api.VectorMarkerSymbol UNSNAPPED_MARKER =
            BuiltInMarkers.filledScreen(BuiltInMarker.CROSS, Rgba.rgb(225, 125, 25), 14.0, 1.0);
    private static final SolidLineSymbol MOVE_LINE =
            SolidLineSymbol.of(
                    new SymbolStroke(
                            new Rgba(35, 95, 210, 190),
                            new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                    1.0);

    private final MundaneMap host;
    private final FeatureEditBinding target;
    private final SnapReferenceSet externalReferences;
    private final SnapLimits snapLimits;
    private final Optional<HorizontalWrap> horizontalWrap;
    private final Set<String> repeatingLayerIds;
    private final double tolerancePixels;
    private final FeatureSnapper snapper = new FeatureSnapper();
    private final List<Consumer<FeatureEditResult>> resultListeners = new ArrayList<>();
    private Mode mode = Mode.NONE;
    private PointFeatureDraft draft;
    private Gesture gesture;
    private Preview preview;
    private Optional<FeatureEditResult> lastResult = Optional.empty();
    private boolean deliveringResults;

    /**
     * Creates a controller without external snap references.
     *
     * @param host exact host component
     * @param target installed editable binding owned by the host
     */
    public BrowserPointEditController(MundaneMap host, FeatureEditBinding target) {
        this(
                host,
                target,
                new SnapReferenceSet(Objects.requireNonNull(host, "host").mapCrs(), List.of()),
                BROWSER_SNAP_LIMITS,
                DEFAULT_SNAP_TOLERANCE_PIXELS,
                host.horizontalWrapFor(target),
                Set.of());
    }

    /**
     * Creates a controller with an explicit immutable same-CRS snap profile.
     *
     * @param host exact host component
     * @param target installed editable binding owned by the host
     * @param externalReferences ordered approved references, excluding the target layer
     * @param snapLimits bounded resolver limits
     * @param tolerancePixels snap tolerance in {@code (0, 256]}
     * @throws IllegalArgumentException if CRS, references, limits, or tolerance are invalid
     * @throws IllegalStateException if the target is not installed
     */
    public BrowserPointEditController(
            MundaneMap host,
            FeatureEditBinding target,
            SnapReferenceSet externalReferences,
            SnapLimits snapLimits,
            double tolerancePixels) {
        this(
                host,
                target,
                externalReferences,
                snapLimits,
                tolerancePixels,
                host.horizontalWrapFor(target, externalReferences),
                host.repeatingSnapLayerIds(target, externalReferences));
    }

    /**
     * Creates a controller with explicit bounded horizontal repetition for approved references.
     *
     * @param host exact host component
     * @param target installed editable binding owned by the host
     * @param externalReferences ordered approved references, excluding the target layer
     * @param snapLimits limits no larger than {@link #BROWSER_SNAP_LIMITS}
     * @param tolerancePixels snap tolerance in {@code (0, 256]}
     * @param horizontalWrap optional display-world wrap profile
     * @param repeatingLayerIds reference layers repeated through the wrap profile
     * @throws IllegalArgumentException if any closed-profile constraint is violated
     * @throws IllegalStateException if the target is not installed
     */
    public BrowserPointEditController(
            MundaneMap host,
            FeatureEditBinding target,
            SnapReferenceSet externalReferences,
            SnapLimits snapLimits,
            double tolerancePixels,
            Optional<HorizontalWrap> horizontalWrap,
            Set<String> repeatingLayerIds) {
        this.host = Objects.requireNonNull(host, "host");
        this.target = Objects.requireNonNull(target, "target");
        this.externalReferences = Objects.requireNonNull(externalReferences, "externalReferences");
        this.snapLimits = Objects.requireNonNull(snapLimits, "snapLimits");
        this.horizontalWrap = Objects.requireNonNull(horizontalWrap, "horizontalWrap");
        this.repeatingLayerIds = Set.copyOf(repeatingLayerIds);
        if (snapLimits.maximumLayers() > BROWSER_SNAP_LIMITS.maximumLayers()
                || snapLimits.maximumFeatures() > BROWSER_SNAP_LIMITS.maximumFeatures()
                || snapLimits.maximumCoordinates() > BROWSER_SNAP_LIMITS.maximumCoordinates()
                || snapLimits.maximumSegments() > BROWSER_SNAP_LIMITS.maximumSegments()) {
            throw new IllegalArgumentException("snap limits exceed the browser profile");
        }
        if (horizontalWrap.isEmpty() && !this.repeatingLayerIds.isEmpty()) {
            throw new IllegalArgumentException("repeating snap layers require horizontal wrap");
        }
        if (!Double.isFinite(tolerancePixels)
                || tolerancePixels <= 0.0
                || tolerancePixels > 256.0) {
            throw new IllegalArgumentException("tolerancePixels must be in (0, 256]");
        }
        this.tolerancePixels = tolerancePixels;
        if (!host.hasFeatureEditBinding(target)) {
            throw new IllegalStateException("target must be an installed editable binding");
        }
        FeatureEditBinding.requireExactCrs(host.mapCrs(), target.snapshot().crs());
        FeatureEditBinding.requireExactCrs(host.mapCrs(), externalReferences.crs());
        for (SnapReferenceLayer layer : externalReferences.layers()) {
            if (layer.layerId().equals(target.id())) {
                throw new IllegalArgumentException(
                        "external snap layers must exclude target layer");
            }
            if (!host.isVisibleSnapLayer(layer.layerId())) {
                throw new IllegalArgumentException(
                        "external snap layers must identify visible host bindings");
            }
        }
        Set<String> declared =
                externalReferences.layers().stream()
                        .map(SnapReferenceLayer::layerId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!declared.containsAll(this.repeatingLayerIds)) {
            throw new IllegalArgumentException("repeating layer is not an external reference");
        }
        validateHorizontalWrapProfile(host.displayCrs(), host.viewport());
        validateReferenceProfile(captureReferences(target.snapshot()));
    }

    /**
     * Returns the selected interaction mode.
     *
     * @return current mode
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Selects create mode with immutable feature content.
     *
     * @param requested immutable content for the next created point
     */
    public void create(PointFeatureDraft requested) {
        requireMutable();
        draft = Objects.requireNonNull(requested, "draft");
        mode = Mode.CREATE;
        clearTransient();
        host.repaintToolOverlay();
    }

    /** Selects move mode for the current editable point selection. */
    public void moveSelected() {
        requireMutable();
        draft = null;
        mode = Mode.MOVE_SELECTED;
        clearTransient();
        host.repaintToolOverlay();
    }

    /** Clears the selected edit mode and transient preview. */
    public void clearMode() {
        requireMutable();
        mode = Mode.NONE;
        draft = null;
        clearTransient();
        host.repaintToolOverlay();
    }

    /**
     * Deletes the current editable point selection as one atomic transaction.
     *
     * @return applied or rejected edit result
     */
    public FeatureEditResult deleteSelected() {
        requireMutable();
        FeatureEditSnapshot snapshot = target.snapshot();
        Optional<FeatureEditProblem> stale = staleSceneProblem(snapshot);
        if (stale.isPresent()) {
            return publish(FeatureEditResult.rejected(snapshot, stale.orElseThrow()));
        }
        SelectionResolution selected = resolveSelection(snapshot, true);
        if (selected.problem().isPresent()) {
            return publish(FeatureEditResult.rejected(snapshot, selected.problem().orElseThrow()));
        }
        FeatureRecord record = selected.record().orElseThrow();
        return invokeSession(
                () ->
                        target.apply(
                                new FeatureEditTransaction(
                                        snapshot.revision(),
                                        "Delete point",
                                        List.of(new DeleteFeature(record.id())))),
                Optional.empty());
    }

    /**
     * Undoes the newest retained edit.
     *
     * @return applied or rejected edit result
     */
    public FeatureEditResult undo() {
        requireMutable();
        FeatureEditSnapshot snapshot = target.snapshot();
        return invokeSession(() -> target.undo(snapshot.revision()), Optional.empty());
    }

    /**
     * Redoes the newest retained undone edit.
     *
     * @return applied or rejected edit result
     */
    public FeatureEditResult redo() {
        requireMutable();
        FeatureEditSnapshot snapshot = target.snapshot();
        return invokeSession(() -> target.redo(snapshot.revision()), Optional.empty());
    }

    /**
     * Returns the most recently published edit result.
     *
     * @return latest result, or empty before the first result
     */
    public Optional<FeatureEditResult> lastResult() {
        return lastResult;
    }

    /**
     * Adds an ordered result listener; duplicate instances receive duplicate callbacks.
     *
     * @param listener result listener
     */
    public void addResultListener(Consumer<FeatureEditResult> listener) {
        requireMutable();
        resultListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes the first identical result-listener registration.
     *
     * @param listener listener instance to remove
     */
    public void removeResultListener(Consumer<FeatureEditResult> listener) {
        requireMutable();
        for (int index = 0; index < resultListeners.size(); index++) {
            if (resultListeners.get(index) == listener) {
                resultListeners.remove(index);
                return;
            }
        }
    }

    /**
     * Returns the current immutable preview, if any.
     *
     * @return current preview
     */
    public Optional<Preview> preview() {
        return Optional.ofNullable(preview);
    }

    @Override
    public void onActivate(MapToolContext context) {
        requireAttached();
        FeatureEditBinding.requireExactCrs(host.mapCrs(), context.mapCrs());
        FeatureEditBinding.requireExactCrs(host.displayCrs(), context.displayCrs());
    }

    @Override
    public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        if (event.type() == MapToolEvent.Type.CANCEL) {
            boolean changed = clearTransient();
            if (changed) {
                context.requestRepaint();
            }
            return changed ? MapToolResult.CONSUME : MapToolResult.PASS;
        }
        if (gesture != null) {
            return handleGesture(event, context);
        }
        return switch (mode) {
            case NONE -> MapToolResult.PASS;
            case CREATE -> handleCreate(event, context);
            case MOVE_SELECTED -> handleMovePress(event, context);
        };
    }

    @Override
    public MapToolResult onMapToolCommand(MapToolCommandEvent event, MapToolContext context) {
        switch (event.command()) {
            case DELETE_BACKWARD -> deleteSelected();
            case UNDO -> undo();
            case REDO -> redo();
        }
        context.requestRepaint();
        return MapToolResult.CONSUME;
    }

    @Override
    public void onDeactivate(MapToolContext context) {
        if (clearTransient()) {
            context.requestRepaint();
        }
    }

    @Override
    public MapCursorIntent cursorIntent() {
        return mode == Mode.NONE ? MapCursorIntent.DEFAULT : MapCursorIntent.CROSSHAIR;
    }

    @Override
    public boolean belongsTo(MundaneMap candidate) {
        return host == candidate;
    }

    boolean targets(FeatureEditBinding binding) {
        return target == binding;
    }

    @Override
    public List<Layer> overlayLayers() {
        Preview current = preview;
        if (current == null || !current.viewport().equals(host.viewport())) {
            return List.of();
        }
        ArrayList<Feature> features = new ArrayList<>(2);
        current.original()
                .ifPresent(
                        original ->
                                features.add(
                                        new Feature(
                                                "move",
                                                "Point move preview",
                                                display(
                                                        new LineStringGeometry(
                                                                CoordinateSequence.of(
                                                                        original.x(),
                                                                        original.y(),
                                                                        current.candidate().x(),
                                                                        current.candidate().y())),
                                                        current.referenceDisplayX()),
                                                Map.of(),
                                                MOVE_LINE)));
        features.add(
                new Feature(
                        "candidate",
                        "Point edit candidate",
                        display(
                                new PointGeometry(current.candidate()),
                                current.referenceDisplayX()),
                        Map.of(),
                        current.snapped() ? SNAPPED_MARKER : UNSNAPPED_MARKER));
        return List.of(new InMemoryLayer("__point_edit", "Point edit preview", features));
    }

    private io.github.mundanej.map.api.Geometry display(
            io.github.mundanej.map.api.Geometry geometry, double referenceDisplayX) {
        io.github.mundanej.map.api.Geometry transformed =
                FeatureSourceQueryEngine.transformGeometry(
                        geometry,
                        host.crsOperation(host.mapCrs(), host.mapCrs()),
                        host.crsOperation(host.mapCrs(), host.displayCrs()),
                        CancellationToken.none());
        if (horizontalWrap.isEmpty()) {
            return transformed;
        }
        HorizontalWrap wrap = horizontalWrap.orElseThrow();
        if (transformed instanceof PointGeometry point) {
            Coordinate coordinate = point.coordinate();
            return new PointGeometry(
                    new Coordinate(
                            wrap.nearestEquivalent(coordinate.x(), referenceDisplayX),
                            coordinate.y()));
        }
        LineStringGeometry line = (LineStringGeometry) transformed;
        double[] placed = line.coordinates().toArray();
        for (int index = 0; index < placed.length; index += 2) {
            placed[index] = wrap.nearestEquivalent(placed[index], referenceDisplayX);
        }
        return new LineStringGeometry(CoordinateSequence.of(placed));
    }

    private MapToolResult handleCreate(MapToolEvent event, MapToolContext context) {
        if (event.type() == MapToolEvent.Type.MOVE) {
            updateCreatePreview(event, context);
            return MapToolResult.CONSUME;
        }
        if (!qualifyingCreateClick(event)) {
            return MapToolResult.PASS;
        }
        requireAttached();
        if (event.mapCoordinate().isEmpty() && horizontalWrap.isEmpty()) {
            clearPreview(context);
            return MapToolResult.PASS;
        }
        FeatureEditSnapshot snapshot = target.snapshot();
        Optional<FeatureEditProblem> stale = staleSceneProblem(snapshot);
        if (stale.isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, stale.orElseThrow()));
            return MapToolResult.CONSUME;
        }
        CoordinateResolution resolution =
                resolveCoordinate(event, Set.of(), captureReferences(snapshot), host.viewport());
        if (resolution.problem().isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, resolution.problem().orElseThrow()));
            return MapToolResult.CONSUME;
        }
        PointFeatureDraft currentDraft = Objects.requireNonNull(draft, "create draft");
        Optional<FeatureEditProblem> capacity = createCapacityProblem(snapshot);
        if (capacity.isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, capacity.orElseThrow()));
            return MapToolResult.CONSUME;
        }
        try {
            invokeSession(
                    () ->
                            target.apply(
                                    new FeatureEditTransaction(
                                            snapshot.revision(),
                                            "Create point",
                                            List.of(
                                                    new CreateFeature(
                                                            currentDraft.at(
                                                                    resolution
                                                                            .coordinate()
                                                                            .orElseThrow()))))),
                    Optional.of(currentDraft.id()));
        } finally {
            clearPreview(context);
        }
        return MapToolResult.CONSUME;
    }

    private void updateCreatePreview(MapToolEvent event, MapToolContext context) {
        requireAttached();
        if (event.mapCoordinate().isEmpty() && horizontalWrap.isEmpty()) {
            clearPreview(context);
            return;
        }
        FeatureEditSnapshot snapshot = target.snapshot();
        Optional<FeatureEditProblem> stale = staleSceneProblem(snapshot);
        if (stale.isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, stale.orElseThrow()));
            return;
        }
        CoordinateResolution resolution =
                resolveCoordinate(event, Set.of(), captureReferences(snapshot), host.viewport());
        if (resolution.problem().isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, resolution.problem().orElseThrow()));
            return;
        }
        Preview next =
                new Preview(
                        host.viewport(),
                        Optional.empty(),
                        resolution.coordinate().orElseThrow(),
                        resolution.snapped(),
                        resolution.referenceDisplayX());
        if (!next.equals(preview)) {
            preview = next;
            context.requestRepaint();
        }
    }

    private MapToolResult handleMovePress(MapToolEvent event, MapToolContext context) {
        if (!qualifyingMovePress(event)
                || (event.mapCoordinate().isEmpty() && horizontalWrap.isEmpty())) {
            return MapToolResult.PASS;
        }
        requireAttached();
        FeatureEditSnapshot snapshot = target.snapshot();
        Optional<FeatureEditProblem> stale = staleSceneProblem(snapshot);
        if (stale.isPresent()) {
            publish(FeatureEditResult.rejected(snapshot, stale.orElseThrow()));
            return MapToolResult.PASS;
        }
        Optional<FeatureEditProblem> unavailable = unavailableReferenceProblem();
        if (unavailable.isPresent()) {
            publish(FeatureEditResult.rejected(snapshot, unavailable.orElseThrow()));
            return MapToolResult.PASS;
        }
        SelectionResolution selected = resolveSelection(snapshot, true);
        if (selected.problem().isPresent()) {
            publish(FeatureEditResult.rejected(snapshot, selected.problem().orElseThrow()));
            return MapToolResult.PASS;
        }
        FeatureSelection key = selected.selection().orElseThrow();
        Optional<MapHit> topmost =
                host.hitTest(
                                event.screenX(),
                                event.screenY(),
                                MundaneMap.DEFAULT_SELECTION_TOLERANCE_PIXELS)
                        .topmost();
        FeatureEditSnapshot afterHit = target.snapshot();
        if (afterHit.revision() != snapshot.revision()) {
            publish(
                    FeatureEditResult.rejected(
                            afterHit,
                            problem(
                                    "EDIT_REVISION_CONFLICT",
                                    "Feature-edit revision changed during hit testing",
                                    Map.of(
                                            "expectedRevision",
                                            Long.toString(snapshot.revision()),
                                            "actualRevision",
                                            Long.toString(afterHit.revision())))));
            return MapToolResult.PASS;
        }
        if (topmost.isEmpty()
                || !topmost.orElseThrow().layerId().equals(key.layerId())
                || !topmost.orElseThrow().featureId().equals(key.featureId())) {
            return MapToolResult.PASS;
        }
        FeatureRecord record = selected.record().orElseThrow();
        gesture =
                new Gesture(
                        snapshot,
                        captureReferences(snapshot),
                        host.viewport(),
                        host.sceneGenerationForTest(),
                        record,
                        false);
        Coordinate original = ((PointGeometry) record.geometry()).coordinate();
        preview =
                new Preview(
                        gesture.viewport(),
                        Optional.of(original),
                        original,
                        false,
                        referenceDisplayX(event, gesture.viewport()));
        context.requestRepaint();
        return MapToolResult.CAPTURE;
    }

    private MapToolResult handleGesture(MapToolEvent event, MapToolContext context) {
        Gesture current = gesture;
        if (!target.isPublishedRevision(target.snapshot().revision())
                || !host.viewport().equals(current.viewport())
                || host.sceneGenerationForTest() != current.sceneGeneration()) {
            if (!current.rejected()) {
                gesture = current.rejectedNow();
                preview = null;
                publish(
                        FeatureEditResult.rejected(
                                target.snapshot(),
                                problem(
                                        "EDIT_GESTURE_SCENE_CHANGED",
                                        "Scene or viewport changed during point-edit gesture",
                                        Map.of())));
                context.requestRepaint();
            }
            if (event.type() == MapToolEvent.Type.RELEASE) {
                gesture = null;
            }
            return MapToolResult.CONSUME;
        }
        if (current.rejected()) {
            if (event.type() == MapToolEvent.Type.RELEASE) {
                gesture = null;
            }
            return MapToolResult.CONSUME;
        }
        if (event.type() == MapToolEvent.Type.WHEEL) {
            return MapToolResult.CONSUME;
        }
        if (event.type() == MapToolEvent.Type.DRAG) {
            updateMovePreview(event, current, context);
            return MapToolResult.CONSUME;
        }
        if (event.type() != MapToolEvent.Type.RELEASE) {
            return MapToolResult.CONSUME;
        }
        try {
            if (event.mapCoordinate().isEmpty() && horizontalWrap.isEmpty()) {
                return MapToolResult.CONSUME;
            }
            CoordinateResolution resolution =
                    resolveCoordinate(
                            event,
                            Set.of(new FeatureSelection(target.id(), current.record().id())),
                            current.references(),
                            current.viewport());
            if (resolution.problem().isPresent()) {
                publish(
                        FeatureEditResult.rejected(
                                target.snapshot(), resolution.problem().orElseThrow()));
                return MapToolResult.CONSUME;
            }
            FeatureRecord replacement =
                    new FeatureRecord(
                            current.record().id(),
                            current.record().name(),
                            new PointGeometry(resolution.coordinate().orElseThrow()),
                            current.record().attributes());
            invokeSession(
                    () ->
                            target.apply(
                                    new FeatureEditTransaction(
                                            current.snapshot().revision(),
                                            "Move point",
                                            List.of(
                                                    new ReplaceFeature(
                                                            current.record().id(), replacement)))),
                    Optional.of(current.record().id()));
            return MapToolResult.CONSUME;
        } finally {
            gesture = null;
            preview = null;
            context.requestRepaint();
        }
    }

    private void updateMovePreview(MapToolEvent event, Gesture current, MapToolContext context) {
        if (event.mapCoordinate().isEmpty() && horizontalWrap.isEmpty()) {
            clearPreview(context);
            return;
        }
        CoordinateResolution resolution =
                resolveCoordinate(
                        event,
                        Set.of(new FeatureSelection(target.id(), current.record().id())),
                        current.references(),
                        current.viewport());
        if (resolution.problem().isPresent()) {
            gesture = current.rejectedNow();
            preview = null;
            publish(
                    FeatureEditResult.rejected(
                            target.snapshot(), resolution.problem().orElseThrow()));
            context.requestRepaint();
            return;
        }
        Coordinate original = ((PointGeometry) current.record().geometry()).coordinate();
        Preview next =
                new Preview(
                        current.viewport(),
                        Optional.of(original),
                        resolution.coordinate().orElseThrow(),
                        resolution.snapped(),
                        resolution.referenceDisplayX());
        if (!next.equals(preview)) {
            preview = next;
            context.requestRepaint();
        }
    }

    private CoordinateResolution resolveCoordinate(
            MapToolEvent event,
            Set<FeatureSelection> exclusions,
            SnapReferenceSet references,
            MapViewport viewport) {
        try {
            validateHorizontalWrapProfile(host.displayCrs(), viewport);
            double referenceDisplayX = referenceDisplayX(event, viewport);
            Optional<FeatureEditProblem> unavailable = unavailableReferenceProblem();
            if (unavailable.isPresent()) {
                return CoordinateResolution.rejected(unavailable.orElseThrow());
            }
            SnapQueryResult result =
                    snapper.find(
                            new SnapQuery(
                                    event.screenX(),
                                    event.screenY(),
                                    tolerancePixels,
                                    host.crsOperation(host.mapCrs(), host.displayCrs()),
                                    host.crsOperation(host.displayCrs(), host.mapCrs()),
                                    viewport,
                                    horizontalWrap,
                                    repeatingReferences(references),
                                    references,
                                    exclusions,
                                    snapLimits,
                                    CancellationToken.none()));
            if (result.status() == SnapQueryStatus.REJECTED) {
                return CoordinateResolution.rejected(result.problem().orElseThrow());
            }
            if (result.status() == SnapQueryStatus.SNAPPED) {
                return CoordinateResolution.at(
                        result.result().orElseThrow().coordinate(), true, referenceDisplayX);
            }
            return unsnappedCoordinate(event, viewport)
                    .map(
                            coordinate ->
                                    CoordinateResolution.at(coordinate, false, referenceDisplayX))
                    .orElseGet(CoordinateResolution::empty);
        } catch (IllegalArgumentException
                | io.github.mundanej.map.core.HorizontalWrapException failure) {
            return CoordinateResolution.rejected(
                    problem(
                            "EDIT_WRAP_UNAVAILABLE",
                            "Horizontal repetition is unavailable for this edit sample",
                            Map.of("reason", "profile")));
        }
    }

    private SnapReferenceSet captureReferences(FeatureEditSnapshot snapshot) {
        List<SnapReferenceLayer> layers = new ArrayList<>(externalReferences.layers());
        List<SnapFeature> editable =
                snapshot.records().stream()
                        .map(record -> new SnapFeature(record.id(), record.geometry()))
                        .toList();
        layers.add(new SnapReferenceLayer(target.id(), editable));
        return new SnapReferenceSet(snapshot.crs(), layers);
    }

    private Set<String> repeatingReferences(SnapReferenceSet references) {
        if (horizontalWrap.isEmpty()) {
            return Set.of();
        }
        java.util.HashSet<String> result = new java.util.HashSet<>(repeatingLayerIds);
        if (target.horizontalWrapMode() == BrowserHorizontalWrapMode.REPEAT_X
                && references.layers().stream()
                        .anyMatch(layer -> layer.layerId().equals(target.id()))) {
            result.add(target.id());
        }
        return Set.copyOf(result);
    }

    private void validateReferenceProfile(SnapReferenceSet references) {
        SnapQueryResult validation = validateReferenceProfile(references, snapLimits);
        if (validation.status() == SnapQueryStatus.REJECTED) {
            throw new IllegalArgumentException("snap references exceed the browser profile");
        }
    }

    private Optional<FeatureEditProblem> createCapacityProblem(FeatureEditSnapshot snapshot) {
        long features = snapshot.records().size();
        for (SnapReferenceLayer layer : externalReferences.layers()) {
            features = Math.addExact(features, layer.features().size());
        }
        long nextFeatures = Math.addExact(features, 1L);
        if (nextFeatures > snapLimits.maximumFeatures()) {
            return Optional.of(snapLimitProblem(snapLimits.maximumFeatures(), nextFeatures));
        }
        if (features > 0 && snapLimits.maximumCoordinates() == 1) {
            return Optional.of(snapLimitProblem(1, 2));
        }
        if (snapLimits.maximumCoordinates() > 1) {
            SnapLimits remaining =
                    new SnapLimits(
                            snapLimits.maximumLayers(),
                            snapLimits.maximumFeatures(),
                            snapLimits.maximumCoordinates() - 1,
                            snapLimits.maximumSegments());
            SnapQueryResult validation =
                    validateReferenceProfile(captureReferences(snapshot), remaining);
            if (validation.status() == SnapQueryStatus.REJECTED) {
                return Optional.of(
                        snapLimitProblem(
                                snapLimits.maximumCoordinates(),
                                Math.addExact(snapLimits.maximumCoordinates(), 1)));
            }
        }
        return Optional.empty();
    }

    private SnapQueryResult validateReferenceProfile(
            SnapReferenceSet references, SnapLimits limits) {
        MapViewport viewport = host.viewport();
        return snapper.find(
                new SnapQuery(
                        viewport.width() / 2.0,
                        viewport.height() / 2.0,
                        tolerancePixels,
                        host.crsOperation(host.mapCrs(), host.displayCrs()),
                        host.crsOperation(host.displayCrs(), host.mapCrs()),
                        viewport,
                        horizontalWrap,
                        repeatingReferences(references),
                        references,
                        Set.of(),
                        limits,
                        CancellationToken.none()));
    }

    private static FeatureEditProblem snapLimitProblem(long maximum, long actual) {
        return problem(
                "EDIT_SNAP_LIMIT_EXCEEDED",
                "Snap query limit exceeded",
                Map.of("maximum", Long.toString(maximum), "actual", Long.toString(actual)));
    }

    void requireCoordinateReferenceSystems(
            io.github.mundanej.map.api.CrsDefinition mapCrs,
            io.github.mundanej.map.api.CrsDefinition displayCrs) {
        FeatureEditBinding.requireExactCrs(mapCrs, target.snapshot().crs());
        FeatureEditBinding.requireExactCrs(mapCrs, externalReferences.crs());
        validateHorizontalWrapProfile(displayCrs, host.viewport());
    }

    private void validateHorizontalWrapProfile(
            io.github.mundanej.map.api.CrsDefinition displayCrs, MapViewport viewport) {
        if (horizontalWrap.isEmpty()) {
            return;
        }
        HorizontalWrap profile = horizontalWrap.orElseThrow();
        io.github.mundanej.map.api.Envelope domain = displayCrs.coordinateDomain();
        if (Double.compare(profile.canonicalMinimumX(), domain.minX()) != 0
                || Double.compare(profile.canonicalMaximumX(), domain.maxX()) != 0
                || (profile.equals(HorizontalWrap.webMercator())
                        && !displayCrs.equals(CrsDefinitions.EPSG_3857))) {
            throw new IllegalArgumentException(
                    "horizontal wrap does not match the display CRS domain");
        }
        io.github.mundanej.map.api.Envelope visible = viewport.visibleWorldEnvelope();
        profile.plan(visible.minX(), visible.maxX(), viewport.worldUnitsPerPixel());
    }

    private static double referenceDisplayX(MapToolEvent event, MapViewport viewport) {
        return viewport.screenToWorld(event.screenX(), event.screenY()).x();
    }

    private Optional<FeatureEditProblem> unavailableReferenceProblem() {
        for (int index = 0; index < externalReferences.layers().size(); index++) {
            if (!host.isVisibleSnapLayer(externalReferences.layers().get(index).layerId())) {
                return Optional.of(
                        problem(
                                "EDIT_SNAP_REFERENCE_UNAVAILABLE",
                                "Point-edit snap reference is not visible",
                                Map.of("layerIndex", Integer.toString(index))));
            }
        }
        return Optional.empty();
    }

    private Optional<Coordinate> unsnappedCoordinate(MapToolEvent event, MapViewport viewport) {
        if (horizontalWrap.isEmpty()) {
            return event.mapCoordinate();
        }
        Coordinate display = viewport.screenToWorld(event.screenX(), event.screenY());
        double canonicalX = horizontalWrap.orElseThrow().canonicalize(display.x()).canonicalX();
        return Optional.of(
                host.crsOperation(host.displayCrs(), host.mapCrs())
                        .transform(new Coordinate(canonicalX, display.y())));
    }

    private Optional<FeatureEditProblem> staleSceneProblem(FeatureEditSnapshot snapshot) {
        if (target.isPublishedRevision(snapshot.revision())) {
            return Optional.empty();
        }
        return Optional.of(
                problem(
                        "EDIT_SCENE_REVISION_STALE",
                        "Editable scene does not contain the authoritative revision",
                        Map.of("revision", Long.toString(snapshot.revision()))));
    }

    private SelectionResolution resolveSelection(
            FeatureEditSnapshot snapshot, boolean reconcileMissing) {
        Optional<FeatureSelection> current = host.selectionForEditing();
        if (current.isEmpty()) {
            return SelectionResolution.rejected(selectionProblem("empty"));
        }
        FeatureSelection selected = current.orElseThrow();
        if (!selected.layerId().equals(target.id())) {
            return SelectionResolution.rejected(selectionProblem("wrongLayer"));
        }
        Optional<FeatureRecord> record =
                snapshot.records().stream()
                        .filter(candidate -> candidate.id().equals(selected.featureId()))
                        .findFirst();
        if (record.isEmpty()) {
            if (reconcileMissing) {
                host.clearSelectionForEditing(selected);
            }
            return SelectionResolution.rejected(selectionProblem("missing"));
        }
        if (!(record.orElseThrow().geometry() instanceof PointGeometry)) {
            return SelectionResolution.rejected(selectionProblem("notPoint"));
        }
        return SelectionResolution.selected(selected, record.orElseThrow());
    }

    private FeatureEditResult invokeSession(
            SessionOperation operation, Optional<String> selectFeatureId) {
        Throwable primary = null;
        FeatureEditResult result = null;
        try {
            result = operation.invoke();
        } catch (FeatureEditNotificationException failure) {
            primary = failure;
            result = failure.committedResult();
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        try {
            if (result != null
                    && result.status() == FeatureEditStatus.APPLIED
                    && selectFeatureId.isPresent()) {
                host.selectForEditing(target, result.snapshot(), selectFeatureId.orElseThrow());
            }
            if (result != null) {
                publish(result);
            }
        } catch (RuntimeException | Error failure) {
            primary = suppress(primary, failure);
        }
        if (primary != null) {
            throwUnchecked(primary);
        }
        return Objects.requireNonNull(result, "session result");
    }

    private FeatureEditResult publish(FeatureEditResult result) {
        lastResult = Optional.of(Objects.requireNonNull(result, "result"));
        if (deliveringResults) {
            throw new IllegalStateException("Point-edit result delivery is not reentrant");
        }
        RuntimeException primary = null;
        deliveringResults = true;
        try {
            for (Consumer<FeatureEditResult> listener : List.copyOf(resultListeners)) {
                try {
                    listener.accept(result);
                } catch (RuntimeException failure) {
                    if (primary == null) {
                        primary = failure;
                    } else if (primary != failure) {
                        primary.addSuppressed(failure);
                    }
                }
            }
        } finally {
            deliveringResults = false;
        }
        if (primary != null) {
            throw primary;
        }
        return result;
    }

    private void requireMutable() {
        if (deliveringResults) {
            throw new IllegalStateException(
                    "Point-edit controller mutation during result delivery");
        }
        requireAttached();
    }

    private void requireAttached() {
        if (!host.hasFeatureEditBinding(target)) {
            throw new IllegalStateException("point-edit target is no longer installed");
        }
    }

    private boolean clearTransient() {
        boolean changed = gesture != null || preview != null;
        gesture = null;
        preview = null;
        return changed;
    }

    private void clearPreview(MapToolContext context) {
        if (preview != null) {
            preview = null;
            context.requestRepaint();
        }
    }

    private static boolean qualifyingCreateClick(MapToolEvent event) {
        return event.type() == MapToolEvent.Type.CLICK
                && event.button().equals(MapPointerButton.PRIMARY)
                && event.buttonsDown().isEmpty()
                && event.clickCount() == 1
                && event.modifiers().isEmpty()
                && !event.popupTrigger();
    }

    private static boolean qualifyingMovePress(MapToolEvent event) {
        return event.type() == MapToolEvent.Type.PRESS
                && event.button().equals(MapPointerButton.PRIMARY)
                && event.buttonsDown().equals(Set.of(MapPointerButton.PRIMARY))
                && event.clickCount() <= 1
                && event.modifiers().isEmpty()
                && !event.popupTrigger();
    }

    private static FeatureEditProblem selectionProblem(String reason) {
        return problem(
                "EDIT_SELECTION_NOT_EDITABLE",
                "Current selection is not an editable point",
                Map.of("reason", reason));
    }

    private static FeatureEditProblem problem(
            String code, String message, Map<String, String> context) {
        return new FeatureEditProblem(code, message, context);
    }

    private static Throwable suppress(Throwable primary, Throwable later) {
        if (primary == null) {
            return later;
        }
        if (primary != later) {
            primary.addSuppressed(later);
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

    /**
     * Immutable non-authoritative point-edit preview.
     *
     * @param viewport exact viewport captured for the preview
     * @param original optional original point for a move gesture
     * @param candidate candidate canonical map coordinate
     * @param snapped whether snapping selected the candidate
     * @param referenceDisplayX continuous display-copy reference for wrapped preview placement
     */
    public record Preview(
            MapViewport viewport,
            Optional<Coordinate> original,
            Coordinate candidate,
            boolean snapped,
            double referenceDisplayX) {
        /** Validates the immutable preview. */
        public Preview {
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(candidate, "candidate");
            if (!Double.isFinite(referenceDisplayX)) {
                throw new IllegalArgumentException("preview display reference must be finite");
            }
        }
    }

    private record Gesture(
            FeatureEditSnapshot snapshot,
            SnapReferenceSet references,
            MapViewport viewport,
            long sceneGeneration,
            FeatureRecord record,
            boolean rejected) {
        private Gesture rejectedNow() {
            return new Gesture(snapshot, references, viewport, sceneGeneration, record, true);
        }
    }

    private record CoordinateResolution(
            Optional<Coordinate> coordinate,
            boolean snapped,
            double referenceDisplayX,
            Optional<FeatureEditProblem> problem) {
        private static CoordinateResolution at(
                Coordinate coordinate, boolean snapped, double referenceDisplayX) {
            return new CoordinateResolution(
                    Optional.of(coordinate), snapped, referenceDisplayX, Optional.empty());
        }

        private static CoordinateResolution empty() {
            return new CoordinateResolution(Optional.empty(), false, 0, Optional.empty());
        }

        private static CoordinateResolution rejected(FeatureEditProblem problem) {
            return new CoordinateResolution(Optional.empty(), false, 0, Optional.of(problem));
        }
    }

    private record SelectionResolution(
            Optional<FeatureSelection> selection,
            Optional<FeatureRecord> record,
            Optional<FeatureEditProblem> problem) {
        private static SelectionResolution selected(
                FeatureSelection selection, FeatureRecord record) {
            return new SelectionResolution(
                    Optional.of(selection), Optional.of(record), Optional.empty());
        }

        private static SelectionResolution rejected(FeatureEditProblem problem) {
            return new SelectionResolution(
                    Optional.empty(), Optional.empty(), Optional.of(problem));
        }
    }

    @FunctionalInterface
    private interface SessionOperation {
        FeatureEditResult invoke();
    }
}
