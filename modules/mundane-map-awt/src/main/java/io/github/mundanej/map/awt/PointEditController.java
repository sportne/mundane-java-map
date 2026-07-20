package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DeleteFeature;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditNotificationException;
import io.github.mundanej.map.api.FeatureEditProblem;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.ReplaceFeature;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapQueryResult;
import io.github.mundanej.map.api.SnapQueryStatus;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapReferenceSet;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.FeatureSnapper;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.SnapQuery;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * View-bound Swing controller for creating, moving, and deleting point features.
 *
 * <p>The controller borrows one installed editable binding. It is confined to the Swing event
 * dispatch thread and never owns the binding or its {@link FeatureEditSession}.
 */
public final class PointEditController implements MapTool {
    /** Default snapping tolerance in logical screen pixels. */
    public static final double DEFAULT_SNAP_TOLERANCE_PIXELS = 8.0;

    /** Closed point-edit interaction mode. */
    public enum Mode {
        /** No coordinate-producing interaction is selected. */
        NONE,
        /** A primary click creates the configured point draft. */
        CREATE,
        /** A primary drag moves the currently selected editable point. */
        MOVE_SELECTED
    }

    private final MapView view;
    private final MapLayerBinding target;
    private final FeatureEditSession session;
    private final SnapReferenceSet externalReferences;
    private final SnapLimits snapLimits;
    private final double tolerancePixels;
    private final FeatureSnapper snapper = new FeatureSnapper();
    private final List<Consumer<FeatureEditResult>> resultListeners = new ArrayList<>();
    private Mode mode = Mode.NONE;
    private PointFeatureDraft draft;
    private Gesture gesture;
    private Preview preview;
    private Optional<FeatureEditResult> lastResult = Optional.empty();
    private boolean deliveringResults;
    private MapView activeOwner;

    /**
     * Creates a controller without external snap references.
     *
     * @param view exact host view
     * @param target installed editable binding owned by {@code view}
     */
    public PointEditController(MapView view, MapLayerBinding target) {
        this(
                view,
                target,
                new SnapReferenceSet(Objects.requireNonNull(view, "view").mapCrs(), List.of()),
                SnapLimits.DEFAULT,
                DEFAULT_SNAP_TOLERANCE_PIXELS);
    }

    /**
     * Creates a controller with an explicit immutable same-CRS snap profile.
     *
     * @param view exact host view
     * @param target installed editable binding owned by {@code view}
     * @param externalReferences ordered external snap references, excluding the target layer
     * @param snapLimits bounded resolver limits
     * @param tolerancePixels snap tolerance in {@code (0, 256]}
     */
    public PointEditController(
            MapView view,
            MapLayerBinding target,
            SnapReferenceSet externalReferences,
            SnapLimits snapLimits,
            double tolerancePixels) {
        requireEdt();
        this.view = Objects.requireNonNull(view, "view");
        this.target = Objects.requireNonNull(target, "target");
        this.externalReferences = Objects.requireNonNull(externalReferences, "externalReferences");
        this.snapLimits = Objects.requireNonNull(snapLimits, "snapLimits");
        if (!Double.isFinite(tolerancePixels)
                || tolerancePixels <= 0.0
                || tolerancePixels > 256.0) {
            throw new IllegalArgumentException("tolerancePixels must be in (0, 256]");
        }
        this.tolerancePixels = tolerancePixels;
        if (!view.hasBinding(target) || !target.isEditable()) {
            throw new IllegalStateException("target must be an installed editable binding");
        }
        this.session = target.editSession();
        requireExactCrs(view.mapCrs(), session.snapshot().crs());
        requireExactCrs(view.mapCrs(), externalReferences.crs());
        for (SnapReferenceLayer layer : externalReferences.layers()) {
            if (layer.layerId().equals(target.id())) {
                throw new IllegalArgumentException(
                        "external snap layers must exclude target layer");
            }
        }
    }

    /**
     * Returns the selected interaction mode.
     *
     * @return selected interaction mode
     */
    public Mode mode() {
        requireEdt();
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
        view.repaint();
    }

    /** Selects move mode for the current editable point selection. */
    public void moveSelected() {
        requireMutable();
        draft = null;
        mode = Mode.MOVE_SELECTED;
        clearTransient();
        view.repaint();
    }

    /** Clears the selected edit mode and any transient preview. */
    public void clearMode() {
        requireMutable();
        mode = Mode.NONE;
        draft = null;
        clearTransient();
        view.repaint();
    }

    /**
     * Deletes the current editable point selection as one atomic transaction.
     *
     * @return applied or rejected edit result
     */
    public FeatureEditResult deleteSelected() {
        requireMutable();
        FeatureEditSnapshot snapshot = session.snapshot();
        SelectionResolution selected = resolveSelection(snapshot, true);
        if (selected.problem().isPresent()) {
            return publish(FeatureEditResult.rejected(snapshot, selected.problem().orElseThrow()));
        }
        FeatureRecord record = selected.record().orElseThrow();
        return invokeSession(
                () ->
                        session.apply(
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
        return invokeSession(() -> session.undo(session.snapshot().revision()), Optional.empty());
    }

    /**
     * Redoes the newest retained undone edit.
     *
     * @return applied or rejected edit result
     */
    public FeatureEditResult redo() {
        requireMutable();
        return invokeSession(() -> session.redo(session.snapshot().revision()), Optional.empty());
    }

    /**
     * Returns the most recently published ordinary edit result.
     *
     * @return latest result, or empty before the first ordinary result
     */
    public Optional<FeatureEditResult> lastResult() {
        requireEdt();
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

    @Override
    public void onActivate(MapToolContext context) {
        requireEdt();
        Objects.requireNonNull(context, "context");
        requireAttached();
        if (activeOwner != view) {
            throw new IllegalStateException("controller is not claimed by its host view");
        }
        requireExactCrs(view.mapCrs(), context.mapCrs());
        requireExactCrs(view.displayCrs(), context.displayCrs());
    }

    @Override
    public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
        requireEdt();
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
    public void onDeactivate(MapToolContext context) {
        if (clearTransient()) {
            context.requestRepaint();
        }
    }

    @Override
    public MapCursorIntent cursorIntent() {
        return mode == Mode.NONE ? MapCursorIntent.DEFAULT : MapCursorIntent.CROSSHAIR;
    }

    void claim(MapView requestedOwner) {
        requireEdt();
        if (requestedOwner != view) {
            throw new IllegalStateException("controller belongs to another view");
        }
        if (activeOwner != null && activeOwner != requestedOwner) {
            throw new IllegalStateException("controller is active in another view");
        }
        activeOwner = requestedOwner;
    }

    void release(MapView releasingOwner) {
        if (activeOwner == releasingOwner) {
            activeOwner = null;
        }
    }

    MapLayerBinding targetBinding() {
        return target;
    }

    boolean isClaimed() {
        return activeOwner != null;
    }

    Optional<Preview> visiblePreview(MapViewport viewport) {
        Preview current = preview;
        return current != null && current.viewport().equals(viewport)
                ? Optional.of(current)
                : Optional.empty();
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
        Optional<Coordinate> requested = event.mapCoordinate();
        if (requested.isEmpty()) {
            clearPreview(context);
            return MapToolResult.PASS;
        }
        FeatureEditSnapshot snapshot = session.snapshot();
        CoordinateResolution resolution =
                resolveCoordinate(event, Set.of(), captureReferences(snapshot), view.viewport());
        if (resolution.problem().isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, resolution.problem().orElseThrow()));
            return MapToolResult.CONSUME;
        }
        PointFeatureDraft currentDraft = Objects.requireNonNull(draft, "create draft");
        try {
            invokeSession(
                    () ->
                            session.apply(
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
        if (event.mapCoordinate().isEmpty()) {
            clearPreview(context);
            return;
        }
        FeatureEditSnapshot snapshot = session.snapshot();
        CoordinateResolution resolution =
                resolveCoordinate(event, Set.of(), captureReferences(snapshot), view.viewport());
        if (resolution.problem().isPresent()) {
            clearPreview(context);
            publish(FeatureEditResult.rejected(snapshot, resolution.problem().orElseThrow()));
            return;
        }
        Preview next =
                new Preview(
                        view.viewport(),
                        Optional.empty(),
                        resolution.coordinate().orElseThrow(),
                        resolution.snapped());
        if (!next.equals(preview)) {
            preview = next;
            context.requestRepaint();
        }
    }

    private MapToolResult handleMovePress(MapToolEvent event, MapToolContext context) {
        if (!qualifyingMovePress(event)) {
            return MapToolResult.PASS;
        }
        requireAttached();
        if (event.mapCoordinate().isEmpty()) {
            return MapToolResult.PASS;
        }
        FeatureEditSnapshot snapshot = session.snapshot();
        SelectionResolution selected = resolveSelection(snapshot, true);
        if (selected.problem().isPresent()) {
            publish(FeatureEditResult.rejected(snapshot, selected.problem().orElseThrow()));
            return MapToolResult.PASS;
        }
        FeatureSelection key = selected.selection().orElseThrow();
        Optional<MapHit> topmost =
                view.hitTestForEditing(
                                target,
                                snapshot,
                                event.screenX(),
                                event.screenY(),
                                MapView.DEFAULT_SELECTION_TOLERANCE_PIXELS)
                        .topmost();
        FeatureEditSnapshot afterHit = session.snapshot();
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
                new Gesture(snapshot, captureReferences(snapshot), view.viewport(), record, false);
        Coordinate original = ((PointGeometry) record.geometry()).coordinate();
        preview = new Preview(gesture.viewport(), Optional.of(original), original, false);
        context.requestRepaint();
        return MapToolResult.CAPTURE;
    }

    private MapToolResult handleGesture(MapToolEvent event, MapToolContext context) {
        Gesture current = gesture;
        if (current.viewRejected()) {
            if (event.type() == MapToolEvent.Type.RELEASE) {
                gesture = null;
            }
            return MapToolResult.CONSUME;
        }
        if (!view.viewport().equals(current.viewport())) {
            if (!current.viewRejected()) {
                gesture = current.withViewRejected();
                preview = null;
                publish(
                        FeatureEditResult.rejected(
                                session.snapshot(),
                                problem(
                                        "EDIT_GESTURE_VIEW_CHANGED",
                                        "Viewport changed during point-edit gesture",
                                        Map.of())));
                context.requestRepaint();
            }
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
            if (event.mapCoordinate().isEmpty()) {
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
                                session.snapshot(), resolution.problem().orElseThrow()));
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
                            session.apply(
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
        if (event.mapCoordinate().isEmpty()) {
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
            gesture = current.withViewRejected();
            preview = null;
            publish(
                    FeatureEditResult.rejected(
                            session.snapshot(), resolution.problem().orElseThrow()));
            context.requestRepaint();
            return;
        }
        Coordinate original = ((PointGeometry) current.record().geometry()).coordinate();
        Preview next =
                new Preview(
                        current.viewport(),
                        Optional.of(original),
                        resolution.coordinate().orElseThrow(),
                        resolution.snapped());
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
        SnapQueryResult result =
                snapper.find(
                        new SnapQuery(
                                event.screenX(),
                                event.screenY(),
                                tolerancePixels,
                                view.mapToDisplayOperation(),
                                view.displayToMapOperation(),
                                viewport,
                                references,
                                exclusions,
                                snapLimits,
                                CancellationToken.none()));
        if (result.status() == SnapQueryStatus.REJECTED) {
            return CoordinateResolution.rejected(result.problem().orElseThrow());
        }
        if (result.status() == SnapQueryStatus.SNAPPED) {
            return CoordinateResolution.at(result.result().orElseThrow().coordinate(), true);
        }
        return event.mapCoordinate()
                .map(coordinate -> CoordinateResolution.at(coordinate, false))
                .orElseGet(CoordinateResolution::empty);
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

    private SelectionResolution resolveSelection(
            FeatureEditSnapshot snapshot, boolean reconcileMissing) {
        Optional<FeatureSelection> current = view.selectionForEditing();
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
                view.clearSelectionForEditing(selected);
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
                selectCommitted(result.snapshot(), selectFeatureId.orElseThrow());
            }
            if (result != null) {
                publish(result);
            }
        } catch (RuntimeException | Error failure) {
            primary = suppress(primary, failure);
        } finally {
            try {
                view.reconcileEditingInteraction();
            } catch (RuntimeException | Error failure) {
                primary = suppress(primary, failure);
            }
        }
        if (primary != null) {
            throwUnchecked(primary);
        }
        return Objects.requireNonNull(result, "session result");
    }

    private void selectCommitted(FeatureEditSnapshot snapshot, String featureId) {
        requireAttached();
        boolean point =
                snapshot.records().stream()
                        .filter(record -> record.id().equals(featureId))
                        .anyMatch(record -> record.geometry() instanceof PointGeometry);
        if (!point || session.snapshot().revision() != snapshot.revision()) {
            throw new IllegalStateException("committed point snapshot is no longer authoritative");
        }
        view.selectForEditing(target, snapshot, featureId);
    }

    private FeatureEditResult publish(FeatureEditResult result) {
        Objects.requireNonNull(result, "result");
        lastResult = Optional.of(result);
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
        requireEdt();
        if (deliveringResults) {
            throw new IllegalStateException(
                    "Point-edit controller mutation during result delivery");
        }
        requireAttached();
    }

    private void requireAttached() {
        if (!view.hasBinding(target)) {
            throw new IllegalStateException("point-edit target is no longer installed");
        }
    }

    private static void requireEdt() {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Point editing is confined to the Swing event dispatch thread");
        }
    }

    private static void requireExactCrs(CrsDefinition expected, CrsDefinition actual) {
        if (!expected.equals(actual)) {
            throw new FeatureEditConfigurationException(
                    problem(
                            "EDIT_CRS_MISMATCH",
                            "Point-edit CRS does not match the map view",
                            Map.of(
                                    "expectedCrs",
                                    expected.canonicalIdentifier(),
                                    "actualCrs",
                                    actual.canonicalIdentifier())));
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

    record Preview(
            MapViewport viewport,
            Optional<Coordinate> original,
            Coordinate candidate,
            boolean snapped) {
        Preview {
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    private record Gesture(
            FeatureEditSnapshot snapshot,
            SnapReferenceSet references,
            MapViewport viewport,
            FeatureRecord record,
            boolean viewRejected) {
        private Gesture withViewRejected() {
            return new Gesture(snapshot, references, viewport, record, true);
        }
    }

    private record CoordinateResolution(
            Optional<Coordinate> coordinate,
            boolean snapped,
            Optional<FeatureEditProblem> problem) {
        private static CoordinateResolution at(Coordinate coordinate, boolean snapped) {
            return new CoordinateResolution(Optional.of(coordinate), snapped, Optional.empty());
        }

        private static CoordinateResolution empty() {
            return new CoordinateResolution(Optional.empty(), false, Optional.empty());
        }

        private static CoordinateResolution rejected(FeatureEditProblem problem) {
            return new CoordinateResolution(Optional.empty(), false, Optional.of(problem));
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
