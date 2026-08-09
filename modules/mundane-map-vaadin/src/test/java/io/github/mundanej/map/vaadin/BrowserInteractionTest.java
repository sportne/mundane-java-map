package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.shared.Registration;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BrowserInteractionTest {
    @Test
    void derivesSelectionHoverAndCoordinatesWithoutClientFeatureIds() {
        long[] now = {0};
        List<Runnable> scheduled = new ArrayList<>();
        MundaneMap map = new MundaneMap(() -> now[0], scheduled::add);
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        map.setSnapshotLayers(List.of(layer()));
        List<MapPointerEvent> pointers = new ArrayList<>();
        List<FeatureSelection> selections = new ArrayList<>();
        map.addMapPointerListener(pointers::add);
        map.addMapSelectionListener(event -> event.current().ifPresent(selections::add));

        Map<String, Object> move = interaction(map, 0, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        assertEquals(false, move.get("suppressDefault"));
        assertEquals("feature", map.hover().orElseThrow().featureId());
        assertEquals(new Coordinate(0, 0), pointers.getFirst().mapCoordinate().orElseThrow());

        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        assertTrue(map.hover().isEmpty());

        interaction(map, 1, "CLICK", 50, 50, 1, 0, 0, 1, 0, "");
        assertEquals(new FeatureSelection("layer", "feature"), map.selection().orElseThrow());
        assertEquals(List.of(map.selection().orElseThrow()), selections);
        assertEquals(MapPointerEvent.Type.CLICKED, pointers.getLast().type());

        interaction(map, 2, "CLICK", 5, 5, 1, 0, 0, 1, 0, "");
        assertTrue(map.selection().isEmpty());
        assertTrue(map.hover().isEmpty());
        assertEquals(new Coordinate(0, 0), map.screenToMap(50, 50).orElseThrow());
        assertEquals(new Coordinate(50, 50), map.mapToScreen(new Coordinate(0, 0)).orElseThrow());
    }

    @Test
    void consumedToolMovesAndClicksStillPublishPointerCoordinates() {
        MundaneMap map = new MundaneMap();
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        map.setSnapshotLayers(List.of(layer()));
        map.setActiveTool(new RecordingTool());
        List<MapPointerEvent> pointers = new ArrayList<>();
        map.addMapPointerListener(pointers::add);

        interaction(map, 0, "MOVE", 53, 46, 0, 0, 0, 0, 0, "");
        interaction(map, 1, "CLICK", 53, 46, 1, 0, 0, 1, 0, "");

        assertEquals(
                List.of(MapPointerEvent.Type.MOVED, MapPointerEvent.Type.CLICKED),
                pointers.stream().map(MapPointerEvent::type).toList());
        assertEquals(new Coordinate(3, 4), pointers.getLast().mapCoordinate().orElseThrow());
    }

    @Test
    void rejectsMalformedValuesWithoutPoisoningLifecycleAndBoundsCoordinateConversion() {
        MundaneMap map = new MundaneMap();
        RecordingTool tool = new RecordingTool();
        map.setActiveTool(tool);

        Map<String, Object> malformed = interaction(map, 0, null, Double.NaN, 0, 0, 0, 0, 0, 0, "");
        assertEquals(false, malformed.get("accepted"));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, map.diagnostic().orElseThrow().code());
        assertEquals(
                false,
                map.acceptMapCommand(
                                1,
                                (double) map.componentGenerationForTest(),
                                (double) map.sceneGenerationForTest(),
                                (double) map.viewportGenerationForTest(),
                                1,
                                null)
                        .get("accepted"));
        map.setEnabled(false);
        assertFalse(map.isEnabled());

        MundaneMap conversions = new MundaneMap();
        assertTrue(conversions.screenToMap(Double.MAX_VALUE, 0).isEmpty());
        conversions.setViewport(new MapViewport(100, 100, -1.0e307, 0, 1));
        assertTrue(conversions.mapToScreen(new Coordinate(Double.MAX_VALUE, 0)).isEmpty());
    }

    @Test
    void retainsEmptySelectionIdentityWithoutPaintingAnOverlay() {
        MundaneMap map = new MundaneMap();
        Feature transparent =
                new Feature(
                        "transparent",
                        "Transparent",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        VectorMarkerSymbol.filledScreen(
                                markerPath(), new Envelope(-1, -1, 1, 1), Rgba.TRANSPARENT, 20, 1));
        map.setSnapshotLayers(List.of(new InMemoryLayer("layer", "Layer", List.of(transparent))));
        FeatureSelection identity = new FeatureSelection("layer", "transparent");
        map.setSelection(identity);
        assertEquals(identity, map.selection().orElseThrow());
        assertFalse(map.paintsFeatureForTest(identity));
    }

    @Test
    void coalescesHoverAndListenerMutationUsesSnapshots() {
        long[] now = {0};
        List<Runnable> scheduled = new ArrayList<>();
        MundaneMap map = new MundaneMap(() -> now[0], scheduled::add);
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        map.setSnapshotLayers(List.of(layer()));
        int[] pointerCount = {0};
        map.addMapPointerListener(event -> pointerCount[0]++);
        List<String> calls = new ArrayList<>();
        Registration[] first = new Registration[1];
        first[0] =
                map.addMapHoverListener(
                        event -> {
                            calls.add("first");
                            first[0].remove();
                            map.addMapHoverListener(ignored -> calls.add("late"));
                        });
        map.addMapHoverListener(event -> calls.add("second"));

        for (int sequence = 0; sequence < 21; sequence++) {
            interaction(map, sequence, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        }
        assertEquals(20, pointerCount[0]);
        assertEquals(List.of("first", "second"), calls);
        assertEquals(1, scheduled.size());
        now[0] = 100_000_000;
        scheduled.removeFirst().run();
        assertEquals(21, pointerCount[0]);

        long[] cancelNow = {0};
        List<Runnable> cancelScheduled = new ArrayList<>();
        MundaneMap cancelled = new MundaneMap(() -> cancelNow[0], cancelScheduled::add);
        cancelled.setViewport(new MapViewport(100, 100, 0, 0, 1));
        cancelled.setSnapshotLayers(List.of(layer()));
        int[] cancelledPointers = {0};
        cancelled.addMapPointerListener(event -> cancelledPointers[0]++);
        for (int sequence = 0; sequence < 21; sequence++) {
            interaction(cancelled, sequence, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        }
        interaction(cancelled, 21, "PRESS", 50, 50, 1, 1, 0, 1, 0, "");
        cancelNow[0] = 100_000_000;
        cancelScheduled.removeFirst().run();
        assertEquals(20, cancelledPointers[0]);
    }

    @Test
    void mapsBrowserButtonMasksAndQuarantinesToolPointerRateOverflow() {
        long[] now = {0};
        MundaneMap buttons = new MundaneMap(() -> now[0], Runnable::run);
        buttons.setViewport(new MapViewport(100, 100, 0, 0, 1));
        buttons.setSnapshotLayers(List.of(layer()));
        RecordingTool buttonTool = new RecordingTool();
        buttons.setActiveTool(buttonTool);

        interaction(buttons, 0, "PRESS", 50, 50, 3, 2, 0, 1, 0, "");
        assertEquals(MapPointerButton.SECONDARY, buttonTool.events.getLast().button());
        assertEquals(Set.of(MapPointerButton.SECONDARY), buttonTool.events.getLast().buttonsDown());
        interaction(buttons, 1, "RELEASE", 50, 50, 3, 0, 0, 1, 0, "");
        interaction(buttons, 2, "PRESS", 50, 50, 2, 4, 0, 1, 0, "");
        assertEquals(MapPointerButton.MIDDLE, buttonTool.events.getLast().button());
        assertEquals(Set.of(MapPointerButton.MIDDLE), buttonTool.events.getLast().buttonsDown());

        MundaneMap limited = new MundaneMap(() -> now[0], Runnable::run);
        limited.setViewport(new MapViewport(100, 100, 0, 0, 1));
        limited.setSnapshotLayers(List.of(layer()));
        RecordingTool limitedTool = new RecordingTool();
        limited.setActiveTool(limitedTool);
        interaction(limited, 0, "PRESS", 50, 50, 1, 1, 0, 1, 0, "");
        for (int sequence = 1; sequence < 120; sequence++) {
            interaction(limited, sequence, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        }
        Map<String, Object> rejected = interaction(limited, 120, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        assertEquals(true, rejected.get("rateExceeded"));
        assertEquals(
                MundaneMapException.EVENT_RATE_EXCEEDED, limited.diagnostic().orElseThrow().code());
        assertEquals(
                MapToolCancelReason.POINTER_STATE_LOST,
                limitedTool.events.getLast().cancelReason().orElseThrow());
        int afterCancellation = limitedTool.events.size();
        interaction(limited, 121, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        assertEquals(afterCancellation, limitedTool.events.size());
        interaction(limited, 122, "CANCEL", 50, 50, 0, 0, 0, 0, 0, "POINTER_STATE_LOST");
        now[0] = 1_000_000_000L;
        interaction(limited, 123, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        assertEquals(afterCancellation + 1, limitedTool.events.size());
    }

    @Test
    void rateLimitsSemanticCommandsAndReturnsRateOutcomeWhenCancellationFails() {
        long[] now = {0};
        MundaneMap commands = new MundaneMap(() -> now[0], Runnable::run);
        commands.setActiveTool(new RecordingTool());
        for (int sequence = 0; sequence < 120; sequence++) {
            assertEquals(
                    true,
                    commands.acceptMapCommand(
                                    1,
                                    (double) commands.componentGenerationForTest(),
                                    (double) commands.sceneGenerationForTest(),
                                    (double) commands.viewportGenerationForTest(),
                                    sequence,
                                    "DELETE_BACKWARD")
                            .get("accepted"));
        }
        assertEquals(
                true,
                commands.acceptMapCommand(
                                1,
                                (double) commands.componentGenerationForTest(),
                                (double) commands.sceneGenerationForTest(),
                                (double) commands.viewportGenerationForTest(),
                                120,
                                "DELETE_BACKWARD")
                        .get("rateExceeded"));

        MundaneMap failing = new MundaneMap(() -> now[0], Runnable::run);
        failing.setActiveTool(new FailingTool(true, false));
        interaction(failing, 0, "PRESS", 1, 1, 1, 1, 0, 1, 0, "");
        for (int sequence = 1; sequence < 120; sequence++) {
            interaction(failing, sequence, "MOVE", 1, 1, 0, 1, 0, 0, 0, "");
        }
        Map<String, Object> outcome = interaction(failing, 120, "MOVE", 1, 1, 0, 1, 0, 0, 0, "");
        assertEquals(true, outcome.get("rateExceeded"));
        assertEquals(1, failing.diagnostic().orElseThrow().getSuppressed().length);
    }

    @Test
    void lifecycleCleanupCompletesWhenToolCallbacksFail() {
        MundaneMap disabled = new MundaneMap();
        disabled.setActiveTool(new FailingTool(true, false));
        assertThrows(IllegalStateException.class, () -> disabled.setEnabled(false));
        assertFalse(disabled.isEnabled());

        MundaneMap detached = new MundaneMap();
        detached.setActiveTool(new FailingTool(true, false));
        long beforeDetach = detached.componentGenerationForTest();
        assertThrows(
                IllegalStateException.class,
                () -> detached.onDetach(new com.vaadin.flow.component.DetachEvent(detached)));
        assertEquals(beforeDetach + 1, detached.componentGenerationForTest());

        MundaneMap closed = new MundaneMap();
        closed.setActiveTool(new FailingTool(true, true));
        IllegalStateException failure = assertThrows(IllegalStateException.class, closed::close);
        assertEquals(1, failure.getSuppressed().length);
        closed.close();
        assertThrows(
                MundaneMapException.class,
                () -> closed.setViewport(new MapViewport(100, 100, 0, 0, 1)));
    }

    @Test
    void scenePublicationCancelsAnInProgressToolGesture() {
        MundaneMap map = new MundaneMap();
        map.setSnapshotLayers(List.of(layer()));
        RecordingTool tool = new RecordingTool();
        map.setActiveTool(tool);
        interaction(map, 0, "PRESS", 50, 50, 1, 1, 0, 1, 0, "");

        map.setSnapshotLayers(List.of(layer()));

        assertEquals(
                MapToolCancelReason.POINTER_STATE_LOST,
                tool.events.getLast().cancelReason().orElseThrow());
    }

    @Test
    void routesCaptureCommandsCancellationDisableAndStaleEvents() {
        MundaneMap map = new MundaneMap();
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        map.setSnapshotLayers(List.of(layer()));
        RecordingTool tool = new RecordingTool();
        map.setActiveTool(tool);
        assertEquals(1, tool.activations);

        Map<String, Object> press = interaction(map, 0, "PRESS", 50, 50, 1, 1, 0, 1, 0, "");
        assertEquals(true, press.get("captured"));
        assertEquals(true, press.get("suppressDefault"));
        Map<String, Object> drag = interaction(map, 1, "DRAG", 52, 50, 0, 1, 0, 0, 0, "");
        assertEquals(true, drag.get("suppressDefault"));
        interaction(map, 2, "RELEASE", 52, 50, 1, 0, 0, 1, 0, "");
        assertEquals(MapToolEvent.Type.RELEASE, tool.events.getLast().type());

        Map<String, Object> command =
                map.acceptMapCommand(
                        1,
                        (double) map.componentGenerationForTest(),
                        (double) map.sceneGenerationForTest(),
                        (double) map.viewportGenerationForTest(),
                        3,
                        "DELETE_BACKWARD");
        assertEquals(true, command.get("suppressDefault"));
        assertEquals(1, tool.commands);

        interaction(map, 4, "CANCEL", 52, 50, 0, 0, 0, 0, 0, "USER_CANCEL");
        assertEquals(
                MapToolCancelReason.USER_CANCEL,
                tool.events.getLast().cancelReason().orElseThrow());

        map.acceptMapCommand(
                1,
                (double) map.componentGenerationForTest(),
                (double) (map.sceneGenerationForTest() - 1),
                (double) map.viewportGenerationForTest(),
                5,
                "DELETE_BACKWARD");
        assertEquals(MundaneMapException.STALE_GENERATION, map.diagnostic().orElseThrow().code());

        map.setEnabled(false);
        assertEquals(
                MapToolCancelReason.VIEW_DISABLED,
                tool.events.getLast().cancelReason().orElseThrow());
        map.setEnabled(true);
        map.clearActiveTool();
        assertEquals(1, tool.deactivations);
        assertFalse(map.activeTool().isPresent());
    }

    @Test
    void transientPassNavigationUpdatesToolConversionWithoutPublishingSettledListeners() {
        MundaneMap map = new MundaneMap();
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        map.setSnapshotLayers(List.of(layer()));
        PassTool tool = new PassTool();
        map.setActiveTool(tool);
        List<MapViewport> settled = new ArrayList<>();
        map.addViewportChangeListener(settled::add);

        assertEquals(
                false,
                interaction(map, 0, "WHEEL", 50, 50, 0, 0, 0, 0, 1, "").get("suppressDefault"));
        map.acceptTransientViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                1,
                100,
                100,
                10,
                0,
                1);
        assertTrue(settled.isEmpty());
        interaction(map, 2, "MOVE", 50, 50, 0, 0, 0, 0, 0, "");
        assertEquals(new Coordinate(10, 0), tool.events.getLast().mapCoordinate().orElseThrow());

        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                3,
                100,
                100,
                10,
                0,
                1);
        assertEquals(List.of(new MapViewport(100, 100, 10, 0, 1)), settled);
    }

    private static Map<String, Object> interaction(
            MundaneMap map,
            int sequence,
            String type,
            double x,
            double y,
            int button,
            int buttons,
            int modifiers,
            int clicks,
            double wheel,
            String reason) {
        return map.acceptMapInteraction(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                sequence,
                type,
                x,
                y,
                button,
                buttons,
                modifiers,
                clicks,
                wheel,
                false,
                reason);
    }

    private static InMemoryLayer layer() {
        VectorPath path = markerPath();
        Feature feature =
                new Feature(
                        "feature",
                        "Feature",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        VectorMarkerSymbol.filledScreen(
                                path, new Envelope(-1, -1, 1, 1), Rgba.rgb(1, 2, 3), 20, 1));
        return new InMemoryLayer("layer", "Layer", List.of(feature));
    }

    private static VectorPath markerPath() {
        return VectorPath.builder()
                .moveTo(-1, -1)
                .lineTo(1, -1)
                .lineTo(1, 1)
                .lineTo(-1, 1)
                .close()
                .build();
    }

    private static final class RecordingTool implements MapTool {
        private final List<MapToolEvent> events = new ArrayList<>();
        private int activations;
        private int deactivations;
        private int commands;

        @Override
        public void onActivate(MapToolContext context) {
            activations++;
        }

        @Override
        public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
            events.add(event);
            return event.type() == MapToolEvent.Type.PRESS
                    ? MapToolResult.CAPTURE
                    : MapToolResult.CONSUME;
        }

        @Override
        public MapToolResult onMapToolCommand(
                io.github.mundanej.map.api.MapToolCommandEvent event, MapToolContext context) {
            commands++;
            return MapToolResult.CONSUME;
        }

        @Override
        public void onDeactivate(MapToolContext context) {
            deactivations++;
        }
    }

    private record FailingTool(boolean failCancel, boolean failDeactivate) implements MapTool {
        @Override
        public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
            if (failCancel && event.type() == MapToolEvent.Type.CANCEL) {
                throw new IllegalStateException("cancel");
            }
            return MapToolResult.CONSUME;
        }

        @Override
        public void onDeactivate(MapToolContext context) {
            if (failDeactivate) {
                throw new IllegalArgumentException("deactivate");
            }
        }
    }

    private static final class PassTool implements MapTool {
        private final List<MapToolEvent> events = new ArrayList<>();

        @Override
        public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context) {
            events.add(event);
            return MapToolResult.PASS;
        }
    }
}
