package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditNotificationException;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapReferenceSet;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Graphics2D;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PointEditControllerTest {
    @Test
    void repeatedCopyEditsStoreCanonicalCoordinatesAndReplayLogicalCommands() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    HorizontalWrap wrap = HorizontalWrap.webMercator();
                    WebMercatorProjection projection = new WebMercatorProjection();
                    double startX = projection.project(new Coordinate(179, 0)).x();
                    MapViewport viewport = new MapViewport(200, 100, startX, 0, 10_000);
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_4326, List.of(record("point", 179, 0)));
                    FeatureEditSession referenceSession =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_4326,
                                    List.of(record("across-seam", -179, 0)));
                    MapLayerBinding binding = binding("editable", session);
                    MapLayerBinding referenceBinding = binding("reference", referenceSession);
                    binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
                    referenceBinding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_3857);
                    view.setSize(200, 100);
                    view.setViewport(viewport);
                    view.setHorizontalWrap(wrap);
                    view.setLayerBindings(List.of(binding, referenceBinding));
                    SnapReferenceSet references =
                            new SnapReferenceSet(
                                    CrsDefinitions.EPSG_4326,
                                    List.of(
                                            new SnapReferenceLayer(
                                                    "reference",
                                                    List.of(
                                                            new SnapFeature(
                                                                    "across-seam",
                                                                    new PointGeometry(
                                                                            new Coordinate(
                                                                                    -179, 0)))))));
                    PointEditController controller =
                            new PointEditController(
                                    view,
                                    binding,
                                    references,
                                    SnapLimits.DEFAULT,
                                    PointEditController.DEFAULT_SNAP_TOLERANCE_PIXELS);
                    view.setActiveTool(controller);
                    view.setSelection(new FeatureSelection("editable", "point"));
                    controller.moveSelected();

                    int targetX =
                            (int)
                                    StrictMath.round(
                                            viewport.worldToScreen(
                                                            new Coordinate(
                                                                    projection
                                                                                    .project(
                                                                                            new Coordinate(
                                                                                                    -179,
                                                                                                    0))
                                                                                    .x()
                                                                            + wrap.period(),
                                                                    0))
                                                    .x());
                    mouse(view, MouseEvent.MOUSE_PRESSED, 100, 50, MouseEvent.BUTTON1, 0, 1);
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            targetX,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    mouse(view, MouseEvent.MOUSE_RELEASED, targetX, 50, MouseEvent.BUTTON1, 0, 1);
                    assertEquals(
                            new Coordinate(-179, 0),
                            ((PointGeometry) record(session, "point").geometry()).coordinate());
                    controller.undo();
                    assertEquals(
                            new Coordinate(179, 0),
                            ((PointGeometry) record(session, "point").geometry()).coordinate());
                    controller.redo();
                    assertEquals(
                            new Coordinate(-179, 0),
                            ((PointGeometry) record(session, "point").geometry()).coordinate());

                    controller.create(new PointFeatureDraft("created", "Created", Map.of()));
                    mouse(view, MouseEvent.MOUSE_CLICKED, 110, 50, MouseEvent.BUTTON1, 0, 1);
                    Coordinate created =
                            ((PointGeometry) record(session, "created").geometry()).coordinate();
                    assertTrue(created.x() >= -180 && created.x() <= 180);
                    controller.deleteSelected();
                    assertFalse(
                            session.snapshot().records().stream()
                                    .anyMatch(record -> record.id().equals("created")));
                    controller.undo();
                    assertEquals(
                            created,
                            ((PointGeometry) record(session, "created").geometry()).coordinate());
                    view.close();
                });
    }

    @Test
    void createsMovesSnapsDeletesAndReplaysThroughRealView() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("origin", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("editable", session);
                    view.setLayerBindings(List.of(binding));
                    SnapFeature referencePoint =
                            new SnapFeature("vertex", new PointGeometry(new Coordinate(20, 0)));
                    SnapReferenceSet references =
                            new SnapReferenceSet(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(
                                            new SnapReferenceLayer(
                                                    "reference", List.of(referencePoint))));
                    PointEditController controller =
                            new PointEditController(
                                    view,
                                    binding,
                                    references,
                                    SnapLimits.DEFAULT,
                                    PointEditController.DEFAULT_SNAP_TOLERANCE_PIXELS);
                    List<FeatureEditResult> results = new ArrayList<>();
                    controller.addResultListener(results::add);
                    view.setActiveTool(controller);

                    controller.create(new PointFeatureDraft("created", "Created", Map.of("n", 1L)));
                    mouse(view, MouseEvent.MOUSE_MOVED, 80, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(controller.visiblePreview(view.viewport()).isPresent());
                    mouse(view, MouseEvent.MOUSE_CLICKED, 80, 50, MouseEvent.BUTTON1, 0, 1);
                    assertEquals(2, session.snapshot().records().size());
                    assertEquals(
                            new FeatureSelection("editable", "created"),
                            view.selection().orElseThrow());

                    controller.moveSelected();
                    mouse(view, MouseEvent.MOUSE_PRESSED, 80, 50, MouseEvent.BUTTON1, 0, 1);
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            69,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    wheel(view, 69, 50);
                    mouse(view, MouseEvent.MOUSE_RELEASED, 69, 50, MouseEvent.BUTTON1, 0, 1);
                    assertEquals(
                            new Coordinate(20, 0),
                            ((PointGeometry) record(session, "created").geometry()).coordinate());
                    assertEquals(
                            new FeatureSelection("editable", "created"),
                            view.selection().orElseThrow());

                    assertEquals(FeatureEditResult.class, controller.deleteSelected().getClass());
                    assertEquals(1, session.snapshot().records().size());
                    assertTrue(view.selection().isEmpty());
                    controller.undo();
                    assertEquals(2, session.snapshot().records().size());
                    controller.redo();
                    assertEquals(1, session.snapshot().records().size());
                    assertEquals(5, results.size());
                    view.close();
                });
    }

    @Test
    void rejectsInvalidSelectionStaleGestureAndViewportChangeWithoutPartialEdit() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("editable", session);
                    view.setLayerBindings(List.of(binding));
                    PointEditController controller = new PointEditController(view, binding);
                    view.setActiveTool(controller);

                    assertEquals(
                            "empty",
                            controller
                                    .deleteSelected()
                                    .problem()
                                    .orElseThrow()
                                    .context()
                                    .get("reason"));
                    view.setSelection(new FeatureSelection("editable", "point"));
                    controller.moveSelected();
                    mouse(view, MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON1, 0, 1);
                    view.setViewport(new MapViewport(100, 100, 1, 0, 1));
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            60,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    assertEquals(
                            "EDIT_GESTURE_VIEW_CHANGED",
                            controller.lastResult().orElseThrow().problem().orElseThrow().code());
                    mouse(view, MouseEvent.MOUSE_RELEASED, 60, 50, MouseEvent.BUTTON1, 0, 1);
                    assertEquals(0, session.snapshot().revision());

                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setSelection(new FeatureSelection("editable", "point"));
                    mouse(view, MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON1, 0, 1);
                    session.apply(
                            new io.github.mundanej.map.api.FeatureEditTransaction(
                                    0,
                                    "concurrent",
                                    List.of(
                                            new io.github.mundanej.map.api.ReplaceFeature(
                                                    "point", record("point", 1, 0)))));
                    mouse(view, MouseEvent.MOUSE_RELEASED, 65, 50, MouseEvent.BUTTON1, 0, 1);
                    assertEquals(
                            "EDIT_REVISION_CONFLICT",
                            controller.lastResult().orElseThrow().problem().orElseThrow().code());
                    assertEquals(
                            new Coordinate(1, 0),
                            ((PointGeometry) record(session, "point").geometry()).coordinate());
                    view.close();
                });
    }

    @Test
    void enforcesEdtCrsOwnershipTargetLifecycleAndListenerIsolation() throws Exception {
        FeatureEditSession offEdtSession =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0)));
        MapView offEdtView = configuredView();
        assertThrows(
                IllegalStateException.class,
                () -> new PointEditController(offEdtView, binding("detached", offEdtSession)));
        offEdtView.close();

        AtomicReference<PointEditController> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("editable", session);
                    view.setLayerBindings(List.of(binding));
                    assertThrows(
                            FeatureEditConfigurationException.class,
                            () ->
                                    new PointEditController(
                                            view,
                                            binding,
                                            new SnapReferenceSet(
                                                    CrsDefinitions.EPSG_4326, List.of()),
                                            SnapLimits.DEFAULT,
                                            8));
                    PointEditController controller = new PointEditController(view, binding);
                    reference.set(controller);
                    FeatureEditSession foreignSession =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("foreign", 10, 0)));
                    MapView foreignView = configuredView();
                    MapLayerBinding foreignBinding = binding("foreign", foreignSession);
                    foreignView.setLayerBindings(List.of(foreignBinding));
                    view.setActiveTool(controller);
                    assertThrows(
                            IllegalStateException.class,
                            () -> view.setLayerBindings(List.of(foreignBinding)));
                    assertEquals(controller, view.activeTool().orElseThrow());
                    assertEquals(List.of(binding), view.layerBindings());
                    foreignView.close();

                    controller.addResultListener(result -> controller.clearMode());
                    controller.create(new PointFeatureDraft("created", "Created", Map.of()));
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    mouse(
                                            view,
                                            MouseEvent.MOUSE_CLICKED,
                                            70,
                                            50,
                                            MouseEvent.BUTTON1,
                                            0,
                                            1));
                    assertEquals(2, session.snapshot().records().size());
                    view.setLayerBindings(List.of());
                    assertTrue(view.activeTool().isEmpty());
                    assertThrows(IllegalStateException.class, controller::undo);
                    view.close();
                });
        assertThrows(IllegalStateException.class, reference.get()::mode);
    }

    @Test
    void ignoresBlankChordedAndDoubleCreateInputAndUsesCapturedHitSnapshot() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("editable", session);
                    view.setLayerBindings(List.of(binding));
                    PointEditController controller = new PointEditController(view, binding);
                    view.setActiveTool(controller);
                    controller.create(new PointFeatureDraft("blocked", "Blocked", Map.of()));
                    MapToolResult blank =
                            controller.onMapToolEvent(
                                    new MapToolEvent(
                                            1,
                                            MapToolEvent.Type.CLICK,
                                            -1,
                                            50,
                                            Optional.empty(),
                                            MapPointerButton.PRIMARY,
                                            Set.of(),
                                            Set.of(),
                                            1,
                                            0,
                                            false,
                                            Optional.empty()),
                                    context(view));
                    assertEquals(MapToolResult.PASS, blank);
                    assertEquals(0, session.snapshot().revision());
                    mouse(
                            view,
                            MouseEvent.MOUSE_CLICKED,
                            70,
                            50,
                            MouseEvent.BUTTON1,
                            InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK,
                            1);
                    assertEquals(0, session.snapshot().revision());
                    mouse(view, MouseEvent.MOUSE_CLICKED, 70, 50, MouseEvent.BUTTON1, 0, 2);
                    assertEquals(0, session.snapshot().revision());

                    FeatureEditSnapshot captured = session.snapshot();
                    session.apply(
                            new io.github.mundanej.map.api.FeatureEditTransaction(
                                    0,
                                    "move",
                                    List.of(
                                            new io.github.mundanej.map.api.ReplaceFeature(
                                                    "point", record("point", 20, 0)))));
                    assertEquals(
                            "point",
                            view.hitTestForEditing(binding, captured, 50, 50, 0)
                                    .topmost()
                                    .orElseThrow()
                                    .featureId());
                    assertTrue(view.hitTest(50, 50, 0).topmost().isEmpty());
                    assertEquals(
                            "point", view.hitTest(70, 50, 0).topmost().orElseThrow().featureId());

                    view.setSelection(new FeatureSelection("editable", "point"));
                    controller.moveSelected();
                    mouse(
                            view,
                            MouseEvent.MOUSE_PRESSED,
                            70,
                            50,
                            MouseEvent.BUTTON1,
                            InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK,
                            1);
                    assertEquals(controller, view.activeTool().orElseThrow());
                    assertEquals(1, session.snapshot().revision());
                    view.close();
                });
    }

    @Test
    void clearsPreviewAndReconcilesAfterPostCommitRuntimeAndError() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession runtimeSession =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("seed", 0, 0)));
                    runtimeSession.addFeatureEditListener(
                            event -> {
                                throw new IllegalStateException("runtime-listener");
                            });
                    MapView runtimeView = configuredView();
                    MapLayerBinding runtimeBinding = binding("editable", runtimeSession);
                    runtimeView.setLayerBindings(List.of(runtimeBinding));
                    PointEditController runtimeController =
                            new PointEditController(runtimeView, runtimeBinding);
                    runtimeView.setActiveTool(runtimeController);
                    runtimeController.create(new PointFeatureDraft("runtime", "Runtime", Map.of()));
                    mouse(runtimeView, MouseEvent.MOUSE_MOVED, 70, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(
                            runtimeController.visiblePreview(runtimeView.viewport()).isPresent());
                    assertThrows(
                            FeatureEditNotificationException.class,
                            () ->
                                    mouse(
                                            runtimeView,
                                            MouseEvent.MOUSE_CLICKED,
                                            70,
                                            50,
                                            MouseEvent.BUTTON1,
                                            0,
                                            1));
                    assertEquals(2, runtimeSession.snapshot().records().size());
                    assertTrue(runtimeController.visiblePreview(runtimeView.viewport()).isEmpty());
                    assertEquals("runtime", runtimeView.selection().orElseThrow().featureId());
                    runtimeView.close();

                    FeatureEditSession errorSession =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("seed", 0, 0)));
                    errorSession.addFeatureEditListener(
                            event -> {
                                throw new AssertionError("error-listener");
                            });
                    MapView errorView = configuredView();
                    MapLayerBinding errorBinding = binding("editable", errorSession);
                    errorView.setLayerBindings(List.of(errorBinding));
                    PointEditController errorController =
                            new PointEditController(errorView, errorBinding);
                    errorView.setActiveTool(errorController);
                    errorController.create(new PointFeatureDraft("error", "Error", Map.of()));
                    mouse(errorView, MouseEvent.MOUSE_MOVED, 70, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertThrows(
                            AssertionError.class,
                            () ->
                                    mouse(
                                            errorView,
                                            MouseEvent.MOUSE_CLICKED,
                                            70,
                                            50,
                                            MouseEvent.BUTTON1,
                                            0,
                                            1));
                    assertEquals(2, errorSession.snapshot().records().size());
                    assertTrue(errorController.visiblePreview(errorView.viewport()).isEmpty());
                    errorView.close();
                });
    }

    @Test
    void queuedClearReleasesControllerClaimAfterResultDelivery() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("seed", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("editable", session);
                    view.setLayerBindings(List.of(binding));
                    PointEditController controller = new PointEditController(view, binding);
                    controller.addResultListener(result -> view.clearActiveTool());
                    view.setActiveTool(controller);
                    controller.create(new PointFeatureDraft("created", "Created", Map.of()));
                    mouse(view, MouseEvent.MOUSE_CLICKED, 70, 50, MouseEvent.BUTTON1, 0, 1);
                    assertTrue(view.activeTool().isEmpty());
                    assertFalse(controller.isClaimed());
                    view.close();
                });
    }

    @Test
    void reportsEverySelectionReasonHonorsTopmostAndRetainsReorderedTarget() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureRecord lineRecord =
                            new FeatureRecord(
                                    "line",
                                    "line",
                                    new LineStringGeometry(CoordinateSequence.of(-10, 0, 10, 0)),
                                    Map.of());
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(record("point", 0, 0), lineRecord));
                    session.addFeatureEditListener(
                            event -> {
                                throw new AssertionError("leave stale selection for missing test");
                            });
                    MapView view = configuredView();
                    MapLayerBinding target = binding("editable", session);
                    MapLayerBinding covering = snapshotBinding("covering", 0, 0);
                    view.setLayerBindings(List.of(target, covering));
                    PointEditController controller = new PointEditController(view, target);
                    view.setActiveTool(controller);

                    view.setSelection(new FeatureSelection("covering", "covering-point"));
                    assertEquals(
                            "wrongLayer",
                            controller
                                    .deleteSelected()
                                    .problem()
                                    .orElseThrow()
                                    .context()
                                    .get("reason"));

                    view.setSelection(new FeatureSelection("editable", "point"));
                    controller.moveSelected();
                    MapToolResult hidden =
                            controller.onMapToolEvent(
                                    pressEvent(2, 50, 50, Optional.of(new Coordinate(0, 0))),
                                    context(view));
                    assertEquals(MapToolResult.PASS, hidden);
                    assertTrue(controller.visiblePreview(view.viewport()).isEmpty());

                    view.setLayerBindings(List.of(covering, target));
                    assertSame(controller, view.activeTool().orElseThrow());
                    view.setSelection(new FeatureSelection("editable", "line"));
                    assertEquals(
                            "notPoint",
                            controller
                                    .deleteSelected()
                                    .problem()
                                    .orElseThrow()
                                    .context()
                                    .get("reason"));

                    view.setSelection(new FeatureSelection("editable", "point"));
                    assertThrows(
                            AssertionError.class,
                            () ->
                                    session.apply(
                                            new io.github.mundanej.map.api.FeatureEditTransaction(
                                                    0,
                                                    "remove",
                                                    List.of(
                                                            new io.github.mundanej.map.api
                                                                    .DeleteFeature("point")))));
                    assertEquals(
                            "missing",
                            controller
                                    .deleteSelected()
                                    .problem()
                                    .orElseThrow()
                                    .context()
                                    .get("reason"));
                    assertTrue(view.selectionForEditing().isEmpty());
                    view.close();
                });
    }

    @Test
    void cancelsCapturedGesturesForUserFocusPointerStateFitAndResize() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(record("point", -10, 0), record("extent", 10, 0)));
                    MapView view = configuredView();
                    MapLayerBinding target = binding("editable", session);
                    view.setLayerBindings(List.of(target));
                    PointEditController controller = new PointEditController(view, target);
                    view.setActiveTool(controller);

                    beginMove(view, controller, "point", 40, 50);
                    assertTrue(view.routeFocusedKey(KeyEvent.VK_ESCAPE));
                    assertTrue(controller.visiblePreview(view.viewport()).isEmpty());

                    beginMove(view, controller, "point", 40, 50);
                    dispatchFocusLost(view);
                    assertTrue(controller.visiblePreview(view.viewport()).isEmpty());

                    beginMove(view, controller, "point", 40, 50);
                    mouse(view, MouseEvent.MOUSE_DRAGGED, 45, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(controller.visiblePreview(view.viewport()).isEmpty());

                    beginMove(view, controller, "point", 40, 50);
                    view.fitToData(10);
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            45,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    assertEquals(
                            "EDIT_GESTURE_VIEW_CHANGED",
                            controller.lastResult().orElseThrow().problem().orElseThrow().code());
                    mouse(view, MouseEvent.MOUSE_RELEASED, 45, 50, MouseEvent.BUTTON1, 0, 1);

                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    beginMove(view, controller, "point", 40, 50);
                    view.setSize(120, 100);
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            45,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    assertEquals(
                            "EDIT_GESTURE_VIEW_CHANGED",
                            controller.lastResult().orElseThrow().problem().orElseThrow().code());
                    assertEquals(0, session.snapshot().revision());
                    view.close();
                });
    }

    @Test
    void aggregatesResultListenerFailuresAndRejectsNestedTargetRemoval() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("seed", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding target = binding("editable", session);
                    view.setLayerBindings(List.of(target));
                    PointEditController controller = new PointEditController(view, target);
                    view.setActiveTool(controller);
                    RuntimeException first = new RuntimeException("first");
                    RuntimeException second = new RuntimeException("second");
                    List<String> order = new ArrayList<>();
                    Consumer<FeatureEditResult> firstListener =
                            result -> {
                                order.add("first");
                                throw first;
                            };
                    controller.addResultListener(firstListener);
                    controller.addResultListener(
                            result -> {
                                order.add("second");
                                throw second;
                            });
                    controller.addResultListener(result -> order.add("third"));
                    assertSame(
                            first,
                            assertThrows(RuntimeException.class, controller::deleteSelected));
                    assertEquals(List.of("first", "second", "third"), order);
                    assertEquals(List.of(second), List.of(first.getSuppressed()));
                    controller.removeResultListener(firstListener);

                    PointEditController nested = new PointEditController(view, target);
                    nested.addResultListener(result -> view.setLayerBindings(List.of()));
                    view.setActiveTool(nested);
                    nested.create(new PointFeatureDraft("created", "Created", Map.of()));
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    mouse(
                                            view,
                                            MouseEvent.MOUSE_CLICKED,
                                            70,
                                            50,
                                            MouseEvent.BUTTON1,
                                            0,
                                            1));
                    assertEquals(2, session.snapshot().records().size());
                    assertEquals(List.of(target), view.layerBindings());
                    assertSame(nested, view.activeTool().orElseThrow());
                    view.close();
                });
    }

    @Test
    void rejectsWhenSourceReportCallbackMutatesSessionDuringRealHitTraversal() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding target = binding("editable", session);
                    view.setLayerBindings(List.of(target));
                    PointEditController controller = new PointEditController(view, target);
                    view.setActiveTool(controller);
                    view.setSelection(new FeatureSelection("editable", "point"));
                    controller.moveSelected();

                    WarningFeatureSource source = new WarningFeatureSource();
                    MapLayerBinding sourceBinding = sourceBinding(source);
                    view.setLayerBindings(List.of(target, sourceBinding));
                    view.addMapSourceReportListener(
                            event ->
                                    session.apply(
                                            new io.github.mundanej.map.api.FeatureEditTransaction(
                                                    0,
                                                    "report mutation",
                                                    List.of(
                                                            new io.github.mundanej.map.api
                                                                    .ReplaceFeature(
                                                                    "point",
                                                                    record("point", 1, 0))))));
                    mouse(view, MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON1, 0, 1);
                    assertEquals(1, session.snapshot().revision());
                    assertEquals(
                            "EDIT_REVISION_CONFLICT",
                            controller.lastResult().orElseThrow().problem().orElseThrow().code());
                    assertTrue(controller.visiblePreview(view.viewport()).isEmpty());
                    view.close();
                    source.close();
                });
    }

    @Test
    void previewPaintsBeforeSelectionWithoutMutatingSnapshot() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0)));
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("editable", session);
                    view.setLayerBindings(List.of(binding));
                    PointEditController controller = new PointEditController(view, binding);
                    view.setActiveTool(controller);
                    controller.create(new PointFeatureDraft("created", "Created", Map.of()));
                    mouse(view, MouseEvent.MOUSE_MOVED, 70, 50, MouseEvent.NOBUTTON, 0, 0);
                    BufferedImage image = paint(view);
                    assertEquals(0, session.snapshot().revision());
                    assertFalse(
                            new java.awt.Color(image.getRGB(70, 42), true)
                                    .equals(java.awt.Color.WHITE));
                    view.close();
                });
    }

    private static MapView configuredView() {
        MapView view = TestMapViews.identity();
        view.setSize(100, 100);
        view.setViewport(new MapViewport(100, 100, 0, 0, 1));
        return view;
    }

    private static MapLayerBinding binding(String id, FeatureEditSession session) {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(20, 90, 180), 10, 1);
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 90, 180),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        return MapLayerBinding.editableFeature(
                id, id, session, marker, line, SolidFillSymbol.of(Rgba.rgb(20, 90, 180), 1));
    }

    private static MapLayerBinding snapshotBinding(String id, double x, double y) {
        Feature feature =
                new Feature(
                        id + "-point",
                        id,
                        new PointGeometry(new Coordinate(x, y)),
                        Map.of(),
                        BuiltInMarkers.filledScreen(
                                BuiltInMarker.SQUARE, Rgba.rgb(180, 60, 40), 12, 1));
        return MapLayerBinding.snapshot(new InMemoryLayer(id, id, List.of(feature)));
    }

    private static MapLayerBinding sourceBinding(FeatureSource source) {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(100, 100, 100), 10, 1);
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(100, 100, 100),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        return MapLayerBinding.borrowedFeature(
                "source",
                "Source",
                source,
                marker,
                line,
                SolidFillSymbol.of(Rgba.rgb(100, 100, 100), 1));
    }

    private static void beginMove(
            MapView view, PointEditController controller, String featureId, int x, int y) {
        view.setSelection(new FeatureSelection("editable", featureId));
        controller.moveSelected();
        mouse(view, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1, 0, 1);
        assertTrue(controller.visiblePreview(view.viewport()).isPresent());
    }

    private static MapToolEvent pressEvent(
            long sequence, double x, double y, Optional<Coordinate> coordinate) {
        return new MapToolEvent(
                sequence,
                MapToolEvent.Type.PRESS,
                x,
                y,
                coordinate,
                MapPointerButton.PRIMARY,
                Set.of(MapPointerButton.PRIMARY),
                Set.of(),
                1,
                0,
                false,
                Optional.empty());
    }

    private static void dispatchFocusLost(MapView view) {
        FocusEvent event = new FocusEvent(view, FocusEvent.FOCUS_LOST);
        for (java.awt.event.FocusListener listener : view.getFocusListeners()) {
            listener.focusLost(event);
        }
    }

    private static FeatureRecord record(String id, double x, double y) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, y)), Map.of());
    }

    private static FeatureRecord record(FeatureEditSession session, String id) {
        return session.snapshot().records().stream()
                .filter(record -> record.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void mouse(
            MapView view, int type, int x, int y, int button, int modifiers, int clickCount) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        type,
                        System.currentTimeMillis(),
                        modifiers,
                        x,
                        y,
                        clickCount,
                        false,
                        button));
    }

    private static void wheel(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseWheelEvent(
                        view,
                        MouseEvent.MOUSE_WHEEL,
                        System.currentTimeMillis(),
                        InputEvent.BUTTON1_DOWN_MASK,
                        x,
                        y,
                        0,
                        false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL,
                        1,
                        1));
    }

    private static MapToolContext context(MapView view) {
        return new MapToolContext() {
            @Override
            public io.github.mundanej.map.api.CrsDefinition mapCrs() {
                return view.mapCrs();
            }

            @Override
            public io.github.mundanej.map.api.CrsDefinition displayCrs() {
                return view.displayCrs();
            }

            @Override
            public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
                return Optional.of(view.viewport().worldToScreen(coordinate));
            }

            @Override
            public Optional<Coordinate> screenToMap(double screenX, double screenY) {
                return Optional.of(view.viewport().screenToWorld(screenX, screenY));
            }

            @Override
            public void requestRepaint() {
                view.repaint();
            }
        };
    }

    private static final class WarningFeatureSource implements FeatureSource {
        private static final DiagnosticReport WARNING =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "TEST_WARNING",
                                        DiagnosticSeverity.WARNING,
                                        "warning-source",
                                        Optional.empty(),
                                        "Synthetic warning",
                                        Map.of())),
                        0);

        private boolean closed;

        @Override
        public FeatureSourceMetadata metadata() {
            return new FeatureSourceMetadata(
                    new SourceIdentity("warning-source", "Warning source"),
                    Optional.empty(),
                    OptionalLong.of(0),
                    Optional.empty(),
                    Optional.of(
                            CrsMetadata.recognized(
                                    CrsDefinitions.EPSG_3857,
                                    Optional.of("EPSG:3857"),
                                    Optional.empty())));
        }

        @Override
        public FeatureSourceLimits limits() {
            return FeatureSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (closed) {
                throw new IllegalStateException("source is closed");
            }
            return new FeatureCursor() {
                private boolean cursorClosed;

                @Override
                public boolean advance() {
                    return false;
                }

                @Override
                public FeatureRecord current() {
                    throw new IllegalStateException("cursor has no current record");
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return WARNING;
                }

                @Override
                public boolean isClosed() {
                    return cursorClosed;
                }

                @Override
                public void close() {
                    cursorClosed = true;
                }
            };
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
