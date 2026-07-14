package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DistanceStrategy;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapInputModifier;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolCommand;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.DistanceStrategies;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MeasurementToolTest {
    @Test
    void addsPreviewsCompletesUndoesAndCancelsWithoutCapture() {
        StubContext context = new StubContext(CrsDefinitions.EPSG_3857);
        MeasurementTool tool =
                new MeasurementTool(DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        tool.onActivate(context);

        assertEquals(MapToolResult.CONSUME, tool.onMapToolEvent(click(1, 0, 0, 1), context));
        assertEquals(MapToolResult.CONSUME, tool.onMapToolEvent(move(2, 3, 4), context));
        assertEquals(MeasurementPhase.MEASURING, tool.state().phase());
        assertEquals(5, tool.state().displayedDistance().metres());
        assertEquals(MapToolResult.PASS, tool.onMapToolEvent(press(3), context));
        assertTrue(tool.state().preview().isEmpty());
        assertEquals(MapToolResult.CONSUME, tool.onMapToolEvent(click(4, 3, 4, 1), context));
        assertEquals(5, tool.state().committedDistance().metres());
        assertEquals(MapToolResult.CONSUME, tool.onMapToolEvent(click(5, 3, 4, 2), context));
        assertEquals(MeasurementPhase.COMPLETE, tool.state().phase());

        assertEquals(
                MapToolResult.CONSUME,
                tool.onMapToolCommand(
                        new MapToolCommandEvent(6, MapToolCommand.DELETE_BACKWARD), context));
        assertEquals(1, tool.state().vertexCount());
        assertEquals(MeasurementPhase.MEASURING, tool.state().phase());
        assertEquals(
                MapToolResult.CONSUME,
                tool.onMapToolEvent(cancel(7, MapToolCancelReason.USER_CANCEL), context));
        assertEquals(MeasurementPhase.EMPTY, tool.state().phase());
        assertEquals(
                MapToolResult.PASS,
                tool.onMapToolEvent(cancel(8, MapToolCancelReason.USER_CANCEL), context));
        assertTrue(context.repaintCount > 0);
    }

    @Test
    void missingCoordinatesZeroSegmentsExternalCancelAndVertexLimitAreDeterministic() {
        StubContext context = new StubContext(CrsDefinitions.EPSG_3857);
        MeasurementTool tool =
                new MeasurementTool(DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857), 2);
        tool.onActivate(context);

        assertEquals(
                MapToolResult.CONSUME, tool.onMapToolEvent(click(1, Optional.empty(), 1), context));
        tool.onMapToolEvent(click(2, 1, 1, 1), context);
        tool.onMapToolEvent(click(3, 1, 1, 1), context);

        assertEquals(0, tool.state().committedDistance().metres());
        assertEquals(MeasurementPhase.COMPLETE, tool.state().phase());
        tool.onMapToolEvent(cancel(4, MapToolCancelReason.FOCUS_LOST), context);
        assertEquals(2, tool.state().vertexCount());
        assertEquals(MapToolResult.PASS, tool.onMapToolEvent(release(5), context));
    }

    @Test
    void invalidNewPathCoordinateDoesNotClearCompletedState() {
        StubContext context = new StubContext(CrsDefinitions.EPSG_3857);
        MeasurementTool tool =
                new MeasurementTool(DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        tool.onMapToolEvent(click(1, 0, 0, 1), context);
        tool.onMapToolEvent(click(2, 3, 4, 1), context);
        tool.onMapToolEvent(click(3, 3, 4, 2), context);
        io.github.mundanej.map.api.MeasurementState before = tool.state();

        assertThrows(
                io.github.mundanej.map.api.CrsException.class,
                () -> tool.onMapToolEvent(click(4, Double.MAX_VALUE, 0, 1), context));

        assertSame(before, tool.state());
    }

    @Test
    void exactCrsAndSingleViewOwnershipAreEnforcedAndReleased() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MeasurementTool mismatch =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    MapView geographic = geographicView();
                    assertThrows(
                            io.github.mundanej.map.api.CrsException.class,
                            () -> geographic.setActiveTool(mismatch));
                    assertTrue(geographic.activeTool().isEmpty());

                    MeasurementTool shared =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    MapView first = TestMapViews.identity();
                    MapView second = TestMapViews.identity();
                    first.setActiveTool(shared);
                    assertThrows(IllegalStateException.class, () -> second.setActiveTool(shared));
                    assertSame(shared, first.activeTool().orElseThrow());
                    first.clearActiveTool();
                    second.setActiveTool(shared);
                    assertSame(shared, second.activeTool().orElseThrow());
                });
    }

    @Test
    void overlayPaintsAfterToolStateAndFormatsStableUnits() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    StubContext context = new StubContext(CrsDefinitions.EPSG_3857);
                    MeasurementTool tool =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    tool.onMapToolEvent(click(1, -20, 0, 1), context);
                    tool.onMapToolEvent(click(2, 20, 0, 1), context);
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    Feature source =
                            new Feature(
                                    "line",
                                    "",
                                    new LineStringGeometry(CoordinateSequence.of(-20, 0, 20, 0)),
                                    Map.of(),
                                    SolidLineSymbol.of(
                                            new SymbolStroke(
                                                    Rgba.rgb(30, 130, 70),
                                                    new SymbolLength(4, SymbolUnit.SCREEN_PIXEL)),
                                            1));
                    view.setLayers(List.of(new InMemoryLayer("layer", "layer", List.of(source))));
                    view.setSelection(new FeatureSelection("layer", "line"));
                    BufferedImage selected = paint(view);
                    long before = hash(selected);
                    view.setActiveTool(tool);
                    BufferedImage measured = paint(view);
                    long after = hash(measured);

                    assertNotEquals(before, after);
                    Color selectedCenter = new Color(selected.getRGB(50, 50), true);
                    Color measuredCenter = new Color(measured.getRGB(50, 50), true);
                    assertTrue(selectedCenter.getBlue() > selectedCenter.getRed());
                    assertTrue(measuredCenter.getRed() > measuredCenter.getBlue());
                    assertEquals(
                            "999.9 m",
                            MeasurementOverlayRenderer.format(
                                    new io.github.mundanej.map.api.DistanceResult(999.94)));
                    assertEquals(
                            "1.00 km",
                            MeasurementOverlayRenderer.format(
                                    new io.github.mundanej.map.api.DistanceResult(1000)));
                    assertEquals(
                            "line", view.hitTest(50, 50, 4).topmost().orElseThrow().featureId());
                });
    }

    @Test
    void clipRejectsOverflowingFiniteEndpointsBeforeJava2d() {
        assertTrue(
                MeasurementOverlayRenderer.clip(
                                new Coordinate(-Double.MAX_VALUE, 0),
                                new Coordinate(Double.MAX_VALUE, 0),
                                100,
                                100)
                        .isEmpty());
    }

    @Test
    void previewAndCommittedSegmentsPaintDifferentlyAndMissingEndpointIsSkipped() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    StubContext planarContext = new StubContext(CrsDefinitions.EPSG_3857);
                    MeasurementTool planar =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    planar.onMapToolEvent(click(1, -20, 0, 1), planarContext);
                    planar.onMapToolEvent(move(2, 20, 0), planarContext);
                    MapView planarView = TestMapViews.identity();
                    planarView.setSize(100, 100);
                    planarView.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    planarView.setActiveTool(planar);
                    long previewHash = hash(paint(planarView));
                    assertEquals(40, planar.state().displayedDistance().metres());
                    planar.onMapToolEvent(click(3, 20, 0, 1), planarContext);
                    long committedHash = hash(paint(planarView));
                    assertNotEquals(previewHash, committedHash);

                    StubContext geographicContext = new StubContext(CrsDefinitions.EPSG_4326);
                    MeasurementTool geographic =
                            new MeasurementTool(
                                    DistanceStrategies.epsg4326GreatCircle(
                                            CrsDefinitions.EPSG_4326));
                    geographic.onMapToolEvent(click(1, -10, 0, 1), geographicContext);
                    geographic.onMapToolEvent(click(2, 0, 90, 1), geographicContext);
                    geographic.onMapToolEvent(click(3, 10, 0, 1), geographicContext);
                    MapView projectedView =
                            new MapView(
                                    io.github.mundanej.map.core.CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_3857);
                    projectedView.setSize(100, 100);
                    projectedView.setViewport(new MapViewport(100, 100, 0, 0, 100_000));
                    projectedView.setActiveTool(geographic);
                    BufferedImage noBridge = paint(projectedView);
                    assertEquals(3, geographic.state().vertexCount());
                    assertEquals(0xffffffff, noBridge.getRGB(50, 50));
                });
    }

    @Test
    void orderedAccumulationBackspaceAndCheckedFailuresPreserveExactState() {
        StubContext context = new StubContext(CrsDefinitions.EPSG_3857);
        MeasurementTool ordered =
                new MeasurementTool(DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        ordered.onMapToolEvent(click(1, 0, 0, 1), context);
        ordered.onMapToolEvent(click(2, 3, 0, 1), context);
        ordered.onMapToolEvent(click(3, 3, 4, 1), context);
        ordered.onMapToolEvent(click(4, 0, 0, 1), context);
        assertEquals(12, ordered.state().committedDistance().metres());
        ordered.onMapToolCommand(
                new MapToolCommandEvent(5, MapToolCommand.DELETE_BACKWARD), context);
        assertEquals(7, ordered.state().committedDistance().metres());
        ordered.onMapToolCommand(
                new MapToolCommandEvent(6, MapToolCommand.DELETE_BACKWARD), context);
        assertEquals(3, ordered.state().committedDistance().metres());

        DistanceStrategy overflowing =
                new DistanceStrategy() {
                    @Override
                    public CrsDefinition coordinateCrs() {
                        return CrsDefinitions.EPSG_3857;
                    }

                    @Override
                    public io.github.mundanej.map.api.DistanceResult distance(
                            Coordinate start, Coordinate end) {
                        return start.equals(end)
                                ? io.github.mundanej.map.api.DistanceResult.ZERO
                                : new io.github.mundanej.map.api.DistanceResult(Double.MAX_VALUE);
                    }
                };
        MeasurementTool checked = new MeasurementTool(overflowing);
        checked.onMapToolEvent(click(1, 0, 0, 1), context);
        checked.onMapToolEvent(click(2, 1, 0, 1), context);
        io.github.mundanej.map.api.MeasurementState before = checked.state();

        assertThrows(
                ArithmeticException.class, () -> checked.onMapToolEvent(move(3, 2, 0), context));
        assertSame(before, checked.state());
        assertThrows(
                ArithmeticException.class,
                () -> checked.onMapToolEvent(click(4, 2, 0, 1), context));
        assertSame(before, checked.state());
        assertEquals(2, checked.state().vertexCount());
        assertTrue(checked.state().preview().isEmpty());
        assertEquals(MeasurementPhase.MEASURING, checked.state().phase());
    }

    @Test
    void realMapViewRoutingAddsPreviewsCompletesAndPreservesNavigation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    MeasurementTool tool =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    view.setActiveTool(tool);

                    mouse(view, MouseEvent.MOUSE_CLICKED, 40, 50, MouseEvent.BUTTON1, 0, 1);
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertEquals(10, tool.state().displayedDistance().metres());
                    mouse(view, MouseEvent.MOUSE_CLICKED, 60, 50, MouseEvent.BUTTON1, 0, 1);
                    mouse(view, MouseEvent.MOUSE_CLICKED, 60, 50, MouseEvent.BUTTON1, 0, 2);
                    assertEquals(MeasurementPhase.COMPLETE, tool.state().phase());
                    assertTrue(view.routeFocusedKey(KeyEvent.VK_BACK_SPACE));
                    assertEquals(MeasurementPhase.MEASURING, tool.state().phase());
                    assertTrue(view.routeFocusedKey(KeyEvent.VK_BACK_SPACE));
                    assertEquals(MeasurementPhase.EMPTY, tool.state().phase());
                    assertFalse(view.routeFocusedKey(KeyEvent.VK_BACK_SPACE));

                    double centerX = view.viewport().centerX();
                    mouse(view, MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON1, 0, 1);
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            60,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    mouse(view, MouseEvent.MOUSE_RELEASED, 60, 50, MouseEvent.BUTTON1, 0, 1);
                    assertNotEquals(centerX, view.viewport().centerX());

                    double scale = view.viewport().worldUnitsPerPixel();
                    view.dispatchEvent(
                            new MouseWheelEvent(
                                    view,
                                    MouseEvent.MOUSE_WHEEL,
                                    System.currentTimeMillis(),
                                    0,
                                    50,
                                    50,
                                    0,
                                    false,
                                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                    1,
                                    -1));
                    assertNotEquals(scale, view.viewport().worldUnitsPerPixel());
                });
    }

    @Test
    void queuedPointerAndCommandReplacementRetainExclusiveOwnership() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView pointerView = TestMapViews.identity();
                    pointerView.setSize(100, 100);
                    MeasurementTool pointerReplacement =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    pointerView.setActiveTool(
                            (event, context) -> {
                                if (event.type() == MapToolEvent.Type.MOVE) {
                                    pointerView.setActiveTool(pointerReplacement);
                                }
                                return MapToolResult.PASS;
                            });
                    mouse(pointerView, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertSame(pointerReplacement, pointerView.activeTool().orElseThrow());
                    MapView other = TestMapViews.identity();
                    assertThrows(
                            IllegalStateException.class,
                            () -> other.setActiveTool(pointerReplacement));
                    pointerView.clearActiveTool();
                    other.setActiveTool(pointerReplacement);

                    MapView commandView = TestMapViews.identity();
                    MeasurementTool commandReplacement =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    commandView.setActiveTool(
                            new io.github.mundanej.map.api.MapTool() {
                                @Override
                                public MapToolResult onMapToolEvent(
                                        MapToolEvent event, MapToolContext context) {
                                    return MapToolResult.PASS;
                                }

                                @Override
                                public MapToolResult onMapToolCommand(
                                        MapToolCommandEvent event, MapToolContext context) {
                                    commandView.setActiveTool(commandReplacement);
                                    return MapToolResult.PASS;
                                }
                            });
                    assertTrue(commandView.routeFocusedKey(KeyEvent.VK_BACK_SPACE));
                    assertSame(commandReplacement, commandView.activeTool().orElseThrow());
                    assertThrows(
                            IllegalStateException.class,
                            () -> other.setActiveTool(commandReplacement));
                });
    }

    @Test
    void queuedClearFromMeasurementCallbackReleasesOwnership() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    DistanceStrategy delegate =
                            DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857);
                    boolean[] clearOnNextDistance = {false};
                    DistanceStrategy clearing =
                            new DistanceStrategy() {
                                @Override
                                public CrsDefinition coordinateCrs() {
                                    return delegate.coordinateCrs();
                                }

                                @Override
                                public io.github.mundanej.map.api.DistanceResult distance(
                                        Coordinate start, Coordinate end) {
                                    if (clearOnNextDistance[0]) {
                                        clearOnNextDistance[0] = false;
                                        view.clearActiveTool();
                                    }
                                    return delegate.distance(start, end);
                                }
                            };
                    MeasurementTool tool = new MeasurementTool(clearing);
                    view.setActiveTool(tool);
                    mouse(view, MouseEvent.MOUSE_CLICKED, 40, 50, MouseEvent.BUTTON1, 0, 1);
                    clearOnNextDistance[0] = true;
                    mouse(view, MouseEvent.MOUSE_CLICKED, 60, 50, MouseEvent.BUTTON1, 0, 1);

                    assertTrue(view.activeTool().isEmpty());
                    assertEquals(MeasurementPhase.EMPTY, tool.state().phase());
                    MapView other = TestMapViews.identity();
                    other.setActiveTool(tool);
                    assertSame(tool, other.activeTool().orElseThrow());
                });
    }

    @Test
    void preRouterSnapshotFailureReleasesClaim() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature point =
                            new Feature(
                                    "point",
                                    "",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    BuiltInMarkers.filledScreen(
                                            io.github.mundanej.map.api.BuiltInMarker.SQUARE,
                                            Rgba.rgb(30, 120, 80),
                                            20,
                                            1));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setLayers(List.of(new InMemoryLayer("layer", "layer", List.of(point))));
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    RuntimeException failure = new RuntimeException("hover-clear");
                    view.addMapHoverListener(
                            event -> {
                                if (event.current().isEmpty()) {
                                    throw failure;
                                }
                            });
                    view.setSize(120, 120);
                    MeasurementTool tool =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));

                    assertSame(
                            failure,
                            assertThrows(RuntimeException.class, () -> view.setActiveTool(tool)));
                    assertTrue(view.activeTool().isEmpty());
                    MapView other = TestMapViews.identity();
                    other.setActiveTool(tool);
                    assertSame(tool, other.activeTool().orElseThrow());
                });
    }

    @Test
    void escapeConsumesNavigationAndCommandFailureRefreshesCursor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    mouse(view, MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON1, 0, 1);
                    assertTrue(view.routeFocusedKey(KeyEvent.VK_ESCAPE));
                    double center = view.viewport().centerX();
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            60,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    assertEquals(center, view.viewport().centerX());

                    RuntimeException failure = new RuntimeException("command");
                    RuntimeException clearFailure = new RuntimeException("hover-clear");
                    Feature point =
                            new Feature(
                                    "point",
                                    "",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    BuiltInMarkers.filledScreen(
                                            io.github.mundanej.map.api.BuiltInMarker.SQUARE,
                                            Rgba.rgb(30, 120, 80),
                                            20,
                                            1));
                    view.setLayers(List.of(new InMemoryLayer("layer", "layer", List.of(point))));
                    view.setActiveTool(
                            new io.github.mundanej.map.api.MapTool() {
                                @Override
                                public MapToolResult onMapToolEvent(
                                        MapToolEvent event, MapToolContext context) {
                                    return MapToolResult.PASS;
                                }

                                @Override
                                public MapToolResult onMapToolCommand(
                                        MapToolCommandEvent event, MapToolContext context) {
                                    throw failure;
                                }

                                @Override
                                public io.github.mundanej.map.api.MapCursorIntent cursorIntent() {
                                    return io.github.mundanej.map.api.MapCursorIntent.HAND;
                                }
                            });
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(view.hover().isPresent());
                    view.addMapHoverListener(
                            event -> {
                                if (event.current().isEmpty()) {
                                    throw clearFailure;
                                }
                            });
                    assertSame(
                            failure,
                            assertThrows(
                                    RuntimeException.class,
                                    () -> view.routeFocusedKey(KeyEvent.VK_BACK_SPACE)));
                    assertEquals(List.of(clearFailure), List.of(failure.getSuppressed()));
                    assertTrue(view.hover().isEmpty());
                    assertEquals(java.awt.Cursor.DEFAULT_CURSOR, view.getCursor().getType());
                });
    }

    @Test
    void consumedMeasurementInputClearsHoverPreservesSelectionAndRepaintsOnceForClick()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature point =
                            new Feature(
                                    "point",
                                    "",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    BuiltInMarkers.filledScreen(
                                            io.github.mundanej.map.api.BuiltInMarker.SQUARE,
                                            Rgba.rgb(30, 120, 80),
                                            20,
                                            1));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setLayers(List.of(new InMemoryLayer("layer", "layer", List.of(point))));
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(view.hover().isPresent());
                    FeatureSelection selection = new FeatureSelection("layer", "point");
                    view.setSelection(selection);
                    MeasurementTool tool =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    view.setActiveTool(tool);
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(view.hover().isEmpty());
                    assertEquals(Optional.of(selection), view.selection());

                    RepaintManager previous = RepaintManager.currentManager(view);
                    CountingRepaintManager recording = new CountingRepaintManager();
                    RepaintManager.setCurrentManager(recording);
                    try {
                        mouse(view, MouseEvent.MOUSE_CLICKED, 40, 40, MouseEvent.BUTTON1, 0, 1);
                        assertEquals(1, recording.fullRepaints);
                        assertEquals(Optional.of(selection), view.selection());
                    } finally {
                        RepaintManager.setCurrentManager(previous);
                    }
                });
    }

    @Test
    void processKeyBindingFallsBackWhenViewIsNotEligible() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    MeasurementTool tool =
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
                    view.setActiveTool(tool);
                    KeyEvent event =
                            new KeyEvent(
                                    view,
                                    KeyEvent.KEY_PRESSED,
                                    System.currentTimeMillis(),
                                    0,
                                    KeyEvent.VK_BACK_SPACE,
                                    '\b');

                    assertFalse(
                            view.processKeyBinding(
                                    KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
                                    event,
                                    JComponent.WHEN_FOCUSED,
                                    true));
                    assertEquals(MeasurementPhase.EMPTY, tool.state().phase());
                });
    }

    @Test
    void escapeFailureClearsHoverGestureAndRefreshesCursor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature point =
                            new Feature(
                                    "point",
                                    "",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    BuiltInMarkers.filledScreen(
                                            io.github.mundanej.map.api.BuiltInMarker.SQUARE,
                                            Rgba.rgb(30, 120, 80),
                                            20,
                                            1));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setLayers(List.of(new InMemoryLayer("layer", "layer", List.of(point))));
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    RuntimeException failure = new RuntimeException("escape");
                    view.setActiveTool(
                            new io.github.mundanej.map.api.MapTool() {
                                @Override
                                public MapToolResult onMapToolEvent(
                                        MapToolEvent event, MapToolContext context) {
                                    if (event.type() == MapToolEvent.Type.CANCEL) {
                                        throw failure;
                                    }
                                    return MapToolResult.PASS;
                                }

                                @Override
                                public io.github.mundanej.map.api.MapCursorIntent cursorIntent() {
                                    return io.github.mundanej.map.api.MapCursorIntent.HAND;
                                }
                            });
                    mouse(view, MouseEvent.MOUSE_PRESSED, 50, 50, MouseEvent.BUTTON1, 0, 1);
                    mouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0, 0);
                    assertTrue(view.hover().isPresent());

                    assertSame(
                            failure,
                            assertThrows(
                                    RuntimeException.class,
                                    () -> view.routeFocusedKey(KeyEvent.VK_ESCAPE)));
                    assertTrue(view.hover().isEmpty());
                    assertEquals(java.awt.Cursor.DEFAULT_CURSOR, view.getCursor().getType());
                    double center = view.viewport().centerX();
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            60,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    assertEquals(center, view.viewport().centerX());
                });
    }

    private static MapView geographicView() {
        return new MapView(
                io.github.mundanej.map.core.CrsRegistry.level1(),
                CrsDefinitions.EPSG_4326,
                CrsDefinitions.EPSG_4326);
    }

    private static MapToolEvent click(long sequence, double x, double y, int count) {
        return click(sequence, Optional.of(new Coordinate(x, y)), count);
    }

    private static MapToolEvent click(long sequence, Optional<Coordinate> coordinate, int count) {
        return event(
                sequence,
                MapToolEvent.Type.CLICK,
                coordinate,
                MapPointerButton.PRIMARY,
                Set.of(),
                count,
                Optional.empty());
    }

    private static MapToolEvent move(long sequence, double x, double y) {
        return event(
                sequence,
                MapToolEvent.Type.MOVE,
                Optional.of(new Coordinate(x, y)),
                MapPointerButton.NONE,
                Set.of(),
                0,
                Optional.empty());
    }

    private static MapToolEvent press(long sequence) {
        return event(
                sequence,
                MapToolEvent.Type.PRESS,
                Optional.empty(),
                MapPointerButton.PRIMARY,
                Set.of(MapPointerButton.PRIMARY),
                1,
                Optional.empty());
    }

    private static MapToolEvent release(long sequence) {
        return event(
                sequence,
                MapToolEvent.Type.RELEASE,
                Optional.empty(),
                MapPointerButton.PRIMARY,
                Set.of(),
                1,
                Optional.empty());
    }

    private static MapToolEvent cancel(long sequence, MapToolCancelReason reason) {
        return event(
                sequence,
                MapToolEvent.Type.CANCEL,
                Optional.empty(),
                MapPointerButton.NONE,
                Set.of(),
                0,
                Optional.of(reason));
    }

    private static MapToolEvent event(
            long sequence,
            MapToolEvent.Type type,
            Optional<Coordinate> coordinate,
            MapPointerButton button,
            Set<MapPointerButton> buttons,
            int clickCount,
            Optional<MapToolCancelReason> reason) {
        return new MapToolEvent(
                sequence,
                type,
                50,
                50,
                coordinate,
                button,
                buttons,
                Set.<MapInputModifier>of(),
                clickCount,
                0,
                false,
                reason);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
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

    private static long hash(BufferedImage image) {
        long result = 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                result = 31 * result + image.getRGB(x, y);
            }
        }
        return result;
    }

    private static final class StubContext implements MapToolContext {
        private final CrsDefinition crs;
        private int repaintCount;

        private StubContext(CrsDefinition crs) {
            this.crs = crs;
        }

        @Override
        public CrsDefinition mapCrs() {
            return crs;
        }

        @Override
        public CrsDefinition displayCrs() {
            return crs;
        }

        @Override
        public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
            return Optional.of(coordinate);
        }

        @Override
        public Optional<Coordinate> screenToMap(double screenX, double screenY) {
            return Optional.of(new Coordinate(screenX, screenY));
        }

        @Override
        public void requestRepaint() {
            repaintCount++;
        }
    }

    private static final class CountingRepaintManager extends RepaintManager {
        private int fullRepaints;

        @Override
        public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
            if (x == 0
                    && y == 0
                    && width == component.getWidth()
                    && height == component.getHeight()) {
                fullRepaints++;
            }
        }
    }
}
