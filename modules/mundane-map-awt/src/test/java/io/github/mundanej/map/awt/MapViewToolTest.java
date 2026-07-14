package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapInputModifier;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Cursor;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewToolTest {
    private static final double TOLERANCE = 1.0e-9;

    @Test
    void capturedDragRoutesOnEdtAndSuppressesNavigationUntilRelease() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    RecordingTool tool = new RecordingTool();
                    tool.pressResult = MapToolResult.CAPTURE;
                    view.setActiveTool(tool);
                    assertSame(tool, view.activeTool().orElseThrow());

                    dispatch(view, MouseEvent.MOUSE_PRESSED, 100, 80, MouseEvent.BUTTON1, 0);
                    dispatch(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            140,
                            110,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK);
                    dispatch(view, MouseEvent.MOUSE_RELEASED, 140, 110, MouseEvent.BUTTON1, 0);

                    assertEquals(1000.0, view.viewport().centerX(), TOLERANCE);
                    assertEquals(2000.0, view.viewport().centerY(), TOLERANCE);
                    assertEquals(List.of("activate", "PRESS", "DRAG", "RELEASE"), tool.calls);
                    assertTrue(tool.callbacksOnEdt);
                    assertEquals(Cursor.CROSSHAIR_CURSOR, view.getCursor().getType());
                });
    }

    @Test
    void passedDragPansConsumedMoveClickAndWheelSuppressDefaultsAndObservers() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    RecordingTool tool = new RecordingTool();
                    view.setActiveTool(tool);

                    dispatch(view, MouseEvent.MOUSE_PRESSED, 100, 80, MouseEvent.BUTTON1, 0);
                    dispatch(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            120,
                            110,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK);
                    dispatch(view, MouseEvent.MOUSE_RELEASED, 120, 110, MouseEvent.BUTTON1, 0);
                    assertEquals(800.0, view.viewport().centerX(), TOLERANCE);
                    assertEquals(2300.0, view.viewport().centerY(), TOLERANCE);

                    List<MapPointerEvent> observed = new ArrayList<>();
                    view.addMapPointerListener(observed::add);
                    tool.otherResult = MapToolResult.CONSUME;
                    dispatch(view, MouseEvent.MOUSE_MOVED, 40, 50, MouseEvent.NOBUTTON, 0);
                    dispatch(view, MouseEvent.MOUSE_CLICKED, 40, 50, MouseEvent.BUTTON1, 0);
                    double scale = view.viewport().worldUnitsPerPixel();
                    wheel(view, 40, 50, -0.5);

                    assertTrue(observed.isEmpty());
                    assertEquals(scale, view.viewport().worldUnitsPerPixel(), TOLERANCE);
                });
    }

    @Test
    void replacementDisableAndClearDeliverLifecycleAndRestoreDefaultCursor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    RecordingTool first = new RecordingTool();
                    RecordingTool second = new RecordingTool();
                    view.setActiveTool(first);
                    view.setActiveTool(second);

                    assertEquals(List.of("activate", "CANCEL", "deactivate"), first.calls);
                    view.setEnabled(false);
                    assertEquals(List.of("activate", "CANCEL"), second.calls);
                    assertEquals(Cursor.DEFAULT_CURSOR, view.getCursor().getType());
                    view.clearActiveTool();

                    assertEquals(
                            List.of("activate", "CANCEL", "CANCEL", "deactivate"), second.calls);
                    assertTrue(view.activeTool().isEmpty());
                });
    }

    @Test
    void missingMapCoordinateStillRoutesScreenSpaceEvent() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    view.setViewport(new MapViewport(200, 160, Double.MAX_VALUE / 4.0, 0.0, 1.0));
                    RecordingTool tool = new RecordingTool();
                    view.setActiveTool(tool);

                    dispatch(view, MouseEvent.MOUSE_MOVED, 10, 10, MouseEvent.NOBUTTON, 0);

                    assertEquals("MOVE", tool.calls.get(1));
                    assertFalse(tool.events.getFirst().mapCoordinate().isPresent());
                });
    }

    @Test
    void callbackFailureRestoresDefaultCursorAndLeavesToolInstalled() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    RuntimeException failure = new RuntimeException("tool");
                    MapTool tool =
                            new MapTool() {
                                @Override
                                public MapToolResult onMapToolEvent(
                                        MapToolEvent event, MapToolContext context) {
                                    throw failure;
                                }

                                @Override
                                public MapCursorIntent cursorIntent() {
                                    return MapCursorIntent.HAND;
                                }
                            };
                    view.setActiveTool(tool);
                    assertEquals(Cursor.HAND_CURSOR, view.getCursor().getType());

                    assertSame(
                            failure,
                            assertThrows(
                                    RuntimeException.class,
                                    () ->
                                            dispatch(
                                                    view,
                                                    MouseEvent.MOUSE_MOVED,
                                                    10,
                                                    10,
                                                    MouseEvent.NOBUTTON,
                                                    0)));

                    assertEquals(Cursor.DEFAULT_CURSOR, view.getCursor().getType());
                    assertSame(tool, view.activeTool().orElseThrow());
                });
    }

    @Test
    void pointerExitClearsAnUncapturedNavigationAnchor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    dispatch(view, MouseEvent.MOUSE_PRESSED, 100, 80, MouseEvent.BUTTON1, 0);
                    dispatch(view, MouseEvent.MOUSE_EXITED, 110, 80, MouseEvent.NOBUTTON, 0);
                    dispatch(view, MouseEvent.MOUSE_ENTERED, 120, 80, MouseEvent.NOBUTTON, 0);
                    dispatch(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            140,
                            80,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK);

                    assertEquals(1000.0, view.viewport().centerX(), TOLERANCE);
                    assertEquals(2000.0, view.viewport().centerY(), TOLERANCE);
                });
    }

    @Test
    void focusAndDisplayLifecycleCancelAndResumeTheInstalledTool() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    view.addNotify();
                    RecordingTool tool = new RecordingTool();
                    view.setActiveTool(tool);

                    dispatchFocus(view, FocusEvent.FOCUS_LOST);
                    dispatchFocus(view, FocusEvent.FOCUS_GAINED);
                    view.removeNotify();

                    List<MapToolCancelReason> reasons =
                            tool.events.stream()
                                    .filter(event -> event.type() == MapToolEvent.Type.CANCEL)
                                    .map(event -> event.cancelReason().orElseThrow())
                                    .toList();
                    assertEquals(
                            List.of(
                                    MapToolCancelReason.FOCUS_LOST,
                                    MapToolCancelReason.VIEW_REMOVED),
                            reasons);
                    assertSame(tool, view.activeTool().orElseThrow());
                    assertEquals(Cursor.DEFAULT_CURSOR, view.getCursor().getType());
                });
    }

    @Test
    void missingCoordinatesRouteThroughReleaseCancellationAndPassedNavigation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(new WebMercatorProjection());
                    view.setSize(200, 100);
                    double scale = WebMercatorProjection.WORLD_LIMIT / 100.0;
                    view.setViewport(new MapViewport(200, 100, 0.0, 0.0, scale));
                    RecordingTool tool = new RecordingTool();
                    view.setActiveTool(tool);

                    dispatch(view, MouseEvent.MOUSE_PRESSED, -1, 50, MouseEvent.BUTTON1, 0);
                    dispatch(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            -2,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK);
                    dispatch(view, MouseEvent.MOUSE_RELEASED, -2, 50, MouseEvent.BUTTON1, 0);
                    dispatch(
                            view,
                            MouseEvent.MOUSE_EXITED,
                            -3,
                            50,
                            MouseEvent.NOBUTTON,
                            InputEvent.SHIFT_DOWN_MASK);

                    assertTrue(view.viewport().centerX() > 0.0);
                    assertEquals(
                            List.of(
                                    MapToolEvent.Type.PRESS,
                                    MapToolEvent.Type.DRAG,
                                    MapToolEvent.Type.RELEASE,
                                    MapToolEvent.Type.CANCEL),
                            tool.events.stream().map(MapToolEvent::type).toList());
                    assertTrue(
                            tool.events.stream()
                                    .allMatch(event -> event.mapCoordinate().isEmpty()));
                    MapToolEvent cancel = tool.events.getLast();
                    assertEquals(
                            MapToolCancelReason.POINTER_EXITED,
                            cancel.cancelReason().orElseThrow());
                    assertEquals(Set.of(MapInputModifier.SHIFT), cancel.modifiers());
                });
    }

    private static MapView view() {
        MapView view = TestMapViews.identity();
        view.setSize(200, 160);
        view.setViewport(new MapViewport(200, 160, 1000.0, 2000.0, 10.0));
        return view;
    }

    private static void dispatch(MapView view, int id, int x, int y, int button, int modifiers) {
        view.dispatchEvent(
                new MouseEvent(
                        view, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    private static void wheel(MapView view, int x, int y, double rotation) {
        view.dispatchEvent(
                new MouseWheelEvent(
                        view,
                        MouseEvent.MOUSE_WHEEL,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        x,
                        y,
                        0,
                        false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL,
                        1,
                        (int) rotation,
                        rotation));
    }

    private static void dispatchFocus(MapView view, int id) {
        FocusEvent event = new FocusEvent(view, id);
        for (java.awt.event.FocusListener listener : view.getFocusListeners()) {
            if (id == FocusEvent.FOCUS_LOST) {
                listener.focusLost(event);
            } else {
                listener.focusGained(event);
            }
        }
    }

    private static final class RecordingTool implements MapTool {
        private final List<String> calls = new ArrayList<>();
        private final List<MapToolEvent> events = new ArrayList<>();
        private MapToolResult pressResult = MapToolResult.PASS;
        private MapToolResult otherResult = MapToolResult.PASS;
        private boolean callbacksOnEdt = true;

        @Override
        public void onActivate(MapToolContext context) {
            callbacksOnEdt &= SwingUtilities.isEventDispatchThread();
            calls.add("activate");
        }

        @Override
        public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
            callbacksOnEdt &= SwingUtilities.isEventDispatchThread();
            calls.add(event.type().name());
            events.add(event);
            return event.type() == MapToolEvent.Type.PRESS ? pressResult : otherResult;
        }

        @Override
        public void onDeactivate(MapToolContext context) {
            callbacksOnEdt &= SwingUtilities.isEventDispatchThread();
            calls.add("deactivate");
        }

        @Override
        public MapCursorIntent cursorIntent() {
            return MapCursorIntent.CROSSHAIR;
        }
    }
}
