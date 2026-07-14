package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolCommand;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapToolRouterTest {
    private final MapToolContext context = new StubContext();

    @Test
    void commandRoutingSharesSequenceRejectsCaptureAndRefreshesCursor() {
        MapToolRouter router = new MapToolRouter();
        List<MapToolCommand> commands = new ArrayList<>();
        MapTool tool =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        return MapToolResult.PASS;
                    }

                    @Override
                    public MapToolResult onMapToolCommand(
                            MapToolCommandEvent event, MapToolContext ignored) {
                        commands.add(event.command());
                        return MapToolResult.CONSUME;
                    }

                    @Override
                    public MapCursorIntent cursorIntent() {
                        return MapCursorIntent.CROSSHAIR;
                    }
                };
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        RouteOutcome outcome =
                router.routeCommand(
                        new MapToolCommandEvent(2, MapToolCommand.DELETE_BACKWARD), context);

        assertTrue(outcome.suppressDefault());
        assertFalse(outcome.captured());
        assertEquals(MapCursorIntent.CROSSHAIR, outcome.cursorIntent());
        assertEquals(List.of(MapToolCommand.DELETE_BACKWARD), commands);
        assertThrows(IllegalArgumentException.class, () -> router.route(move(2), context));
    }

    @Test
    void commandCannotCapture() {
        MapToolRouter router = new MapToolRouter();
        MapTool tool =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        return MapToolResult.PASS;
                    }

                    @Override
                    public MapToolResult onMapToolCommand(
                            MapToolCommandEvent event, MapToolContext ignored) {
                        return MapToolResult.CAPTURE;
                    }
                };
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        assertThrows(
                IllegalStateException.class,
                () ->
                        router.routeCommand(
                                new MapToolCommandEvent(2, MapToolCommand.DELETE_BACKWARD),
                                context));
    }

    @Test
    void userCancelSuppressesWhenItEndsCaptureEvenWhenToolPasses() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.CAPTURE);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        router.route(press(2, MapPointerButton.PRIMARY), context);
        tool.result = MapToolResult.PASS;

        RouteOutcome outcome =
                router.cancelInteraction(
                        cancel(
                                3,
                                MapToolCancelReason.USER_CANCEL,
                                Set.of(MapPointerButton.PRIMARY)),
                        context);

        assertTrue(outcome.suppressDefault());
        assertFalse(outcome.captured());
    }

    @Test
    void commandAppliesQueuedReplacementBeforeReturning() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool replacement = new RecordingTool(MapToolResult.PASS);
        MapTool first =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        return MapToolResult.PASS;
                    }

                    @Override
                    public MapToolResult onMapToolCommand(
                            MapToolCommandEvent event, MapToolContext ignored) {
                        router.setActiveTool(
                                replacement, cancel(3, MapToolCancelReason.TOOL_REPLACED), context);
                        return MapToolResult.PASS;
                    }
                };
        router.setActiveTool(first, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        RouteOutcome outcome =
                router.routeCommand(
                        new MapToolCommandEvent(2, MapToolCommand.DELETE_BACKWARD), context);

        assertTrue(outcome.suppressDefault());
        assertSame(replacement, router.activeTool().orElseThrow());
        assertEquals(List.of("activate"), replacement.calls);
    }

    @Test
    void commandFailureResetsCursorAndRejectsRecursiveDispatch() {
        MapToolRouter router = new MapToolRouter();
        RuntimeException failure = new RuntimeException("command");
        MapTool tool =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        return MapToolResult.PASS;
                    }

                    @Override
                    public MapToolResult onMapToolCommand(
                            MapToolCommandEvent event, MapToolContext ignored) {
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        router.routeCommand(
                                                new MapToolCommandEvent(
                                                        3, MapToolCommand.DELETE_BACKWARD),
                                                context));
                        throw failure;
                    }

                    @Override
                    public MapCursorIntent cursorIntent() {
                        return MapCursorIntent.HAND;
                    }
                };
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        assertSame(
                failure,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                router.routeCommand(
                                        new MapToolCommandEvent(2, MapToolCommand.DELETE_BACKWARD),
                                        context)));
        assertEquals(MapCursorIntent.DEFAULT, router.currentCursorIntent());
        assertSame(tool, router.activeTool().orElseThrow());
    }

    @Test
    void activatesCapturesRoutesAndReleasesInOrder() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.CAPTURE);

        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        RouteOutcome press = router.route(press(2, MapPointerButton.PRIMARY), context);
        tool.result = MapToolResult.PASS;
        RouteOutcome drag = router.route(drag(3, MapPointerButton.PRIMARY), context);
        RouteOutcome release = router.route(release(4, MapPointerButton.PRIMARY), context);

        assertTrue(press.captured());
        assertTrue(press.suppressDefault());
        assertTrue(drag.suppressDefault());
        assertFalse(release.captured());
        assertTrue(release.suppressDefault());
        assertEquals(List.of("activate", "PRESS", "DRAG", "RELEASE"), tool.calls);
    }

    @Test
    void replacementUsesIdentityAndCancellationBeforeDeactivationAndActivation() {
        MapToolRouter router = new MapToolRouter();
        List<String> order = new ArrayList<>();
        RecordingTool first = new RecordingTool(MapToolResult.PASS, "first", order);
        RecordingTool second = new RecordingTool(MapToolResult.PASS, "second", order);

        router.setActiveTool(first, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        router.setActiveTool(first, cancel(2, MapToolCancelReason.TOOL_REPLACED), context);
        router.setActiveTool(second, cancel(3, MapToolCancelReason.TOOL_REPLACED), context);

        assertSame(second, router.activeTool().orElseThrow());
        assertEquals(
                List.of("first:activate", "first:CANCEL", "first:deactivate", "second:activate"),
                order);
    }

    @Test
    void rejectsIllegalCaptureAndStaleSequencesWithoutChangingSession() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.CAPTURE);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        assertThrows(IllegalStateException.class, () -> router.route(move(2), context));
        tool.result = MapToolResult.PASS;
        assertThrows(IllegalArgumentException.class, () -> router.route(move(2), context));
        assertSame(tool, router.activeTool().orElseThrow());
        assertFalse(router.captured());
    }

    @Test
    void externalCancellationCoalescesUntilResumeAndQuarantinesStaleReleaseClick() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.PASS);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        router.route(press(2, MapPointerButton.PRIMARY), context);

        router.cancelInteraction(
                cancel(3, MapToolCancelReason.FOCUS_LOST, Set.of(MapPointerButton.PRIMARY)),
                context);
        router.cancelInteraction(
                cancel(4, MapToolCancelReason.VIEW_REMOVED, Set.of(MapPointerButton.PRIMARY)),
                context);
        assertEquals(1, tool.calls.stream().filter("CANCEL"::equals).count());
        router.resume();

        assertTrue(router.route(release(5, MapPointerButton.PRIMARY), context).suppressDefault());
        assertTrue(router.route(click(6, MapPointerButton.PRIMARY), context).suppressDefault());
        assertFalse(router.route(move(7), context).suppressDefault());
    }

    @Test
    void activationFailureAttemptsCleanupAndLeavesNoTool() {
        MapToolRouter router = new MapToolRouter();
        RuntimeException activation = new RuntimeException("activate");
        RuntimeException cleanup = new RuntimeException("cleanup");
        MapTool failing =
                new MapTool() {
                    @Override
                    public void onActivate(MapToolContext ignored) {
                        throw activation;
                    }

                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        return MapToolResult.PASS;
                    }

                    @Override
                    public void onDeactivate(MapToolContext ignored) {
                        throw cleanup;
                    }
                };

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                router.setActiveTool(
                                        failing,
                                        cancel(1, MapToolCancelReason.TOOL_REPLACED),
                                        context));

        assertSame(activation, thrown);
        assertEquals(List.of(cleanup), List.of(thrown.getSuppressed()));
        assertTrue(router.activeTool().isEmpty());
    }

    @Test
    void capturedWheelPassesIndependentlyWhileCapturedButtonRemainsDown() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.CAPTURE);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        router.route(press(2, MapPointerButton.PRIMARY), context);
        tool.result = MapToolResult.PASS;

        RouteOutcome wheel = router.route(wheel(3, MapPointerButton.PRIMARY), context);

        assertFalse(wheel.suppressDefault());
        assertTrue(wheel.captured());
    }

    @Test
    void callbackFailureStillAppliesQueuedHostUnavailabilityWithoutRecursiveCallback() {
        MapToolRouter router = new MapToolRouter();
        List<MapToolEvent.Type> calls = new ArrayList<>();
        RuntimeException failure = new RuntimeException("callback");
        MapTool tool =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        calls.add(event.type());
                        router.cancelInteraction(
                                cancel(3, MapToolCancelReason.VIEW_DISABLED), context);
                        throw failure;
                    }

                    @Override
                    public MapCursorIntent cursorIntent() {
                        return MapCursorIntent.HAND;
                    }
                };
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        assertSame(
                failure,
                assertThrows(RuntimeException.class, () -> router.route(move(2), context)));
        RouteOutcome repeated =
                router.cancelInteraction(cancel(4, MapToolCancelReason.FOCUS_LOST), context);

        assertEquals(List.of(MapToolEvent.Type.MOVE), calls);
        assertEquals(MapCursorIntent.DEFAULT, repeated.cursorIntent());
        assertSame(tool, router.activeTool().orElseThrow());
    }

    @Test
    void promotedClickTokenExpiresOnTheFirstUnrelatedClick() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.PASS);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        router.route(press(2, MapPointerButton.PRIMARY), context);
        router.cancelInteraction(
                cancel(3, MapToolCancelReason.FOCUS_LOST, Set.of(MapPointerButton.PRIMARY)),
                context);
        router.resume();
        assertTrue(router.route(release(4, MapPointerButton.PRIMARY), context).suppressDefault());

        assertFalse(router.route(click(5, MapPointerButton.SECONDARY), context).suppressDefault());
        assertFalse(router.route(click(6, MapPointerButton.PRIMARY), context).suppressDefault());
    }

    @Test
    void consumeSuppressesDefaultsAndNonmatchingReleaseKeepsCapture() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.CONSUME);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        assertTrue(router.route(move(2), context).suppressDefault());
        tool.result = MapToolResult.CAPTURE;
        router.route(press(3, MapPointerButton.PRIMARY), context);
        tool.result = MapToolResult.PASS;

        RouteOutcome unrelatedRelease =
                router.route(
                        event(
                                4,
                                MapToolEvent.Type.RELEASE,
                                MapPointerButton.SECONDARY,
                                Set.of(MapPointerButton.PRIMARY),
                                Optional.empty()),
                        context);

        assertTrue(unrelatedRelease.suppressDefault());
        assertTrue(unrelatedRelease.captured());
    }

    @Test
    void chordedPressCannotCapture() {
        MapToolRouter router = new MapToolRouter();
        RecordingTool tool = new RecordingTool(MapToolResult.CAPTURE);
        router.setActiveTool(tool, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        MapToolEvent chorded =
                event(
                        2,
                        MapToolEvent.Type.PRESS,
                        MapPointerButton.SECONDARY,
                        Set.of(MapPointerButton.PRIMARY, MapPointerButton.SECONDARY),
                        Optional.empty());

        assertThrows(IllegalStateException.class, () -> router.route(chorded, context));
        assertFalse(router.captured());
    }

    @Test
    void callbackQueuedReplacementAndClearRunAfterTheCallbackUnwinds() {
        MapToolRouter router = new MapToolRouter();
        List<String> order = new ArrayList<>();
        RecordingTool replacement = new RecordingTool(MapToolResult.PASS, "replacement", order);
        MapTool replacing =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        order.add("original:" + event.type());
                        if (event.type() == MapToolEvent.Type.MOVE) {
                            router.setActiveTool(
                                    replacement,
                                    cancel(3, MapToolCancelReason.TOOL_REPLACED),
                                    context);
                        }
                        return MapToolResult.PASS;
                    }

                    @Override
                    public void onDeactivate(MapToolContext ignored) {
                        order.add("original:deactivate");
                    }
                };
        router.setActiveTool(replacing, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);

        assertTrue(router.route(move(2), context).suppressDefault());
        assertEquals(
                List.of(
                        "original:MOVE",
                        "original:CANCEL",
                        "original:deactivate",
                        "replacement:activate"),
                order);

        MapTool clearing =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        if (event.type() == MapToolEvent.Type.MOVE) {
                            router.clearActiveTool(
                                    cancel(6, MapToolCancelReason.TOOL_CLEARED), context);
                        }
                        return MapToolResult.PASS;
                    }
                };
        router.setActiveTool(clearing, cancel(4, MapToolCancelReason.TOOL_REPLACED), context);
        assertTrue(router.route(move(5), context).suppressDefault());
        assertTrue(router.activeTool().isEmpty());
    }

    @Test
    void replacementFailureKeepsPrimaryCancellationAndSuppressesDeactivationFailure() {
        MapToolRouter router = new MapToolRouter();
        RuntimeException cancellation = new RuntimeException("cancel");
        RuntimeException deactivation = new RuntimeException("deactivate");
        MapTool failing =
                new MapTool() {
                    @Override
                    public MapToolResult onMapToolEvent(
                            MapToolEvent event, MapToolContext ignored) {
                        throw cancellation;
                    }

                    @Override
                    public void onDeactivate(MapToolContext ignored) {
                        throw deactivation;
                    }
                };
        router.setActiveTool(failing, cancel(1, MapToolCancelReason.TOOL_REPLACED), context);
        RecordingTool replacement = new RecordingTool(MapToolResult.PASS);

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                router.setActiveTool(
                                        replacement,
                                        cancel(2, MapToolCancelReason.TOOL_REPLACED),
                                        context));

        assertSame(cancellation, thrown);
        assertEquals(List.of(deactivation), List.of(thrown.getSuppressed()));
        assertTrue(router.activeTool().isEmpty());
        assertTrue(replacement.calls.isEmpty());
    }

    @Test
    void firstActivationBetweenNoToolReleaseAndClickSuppressesTheStaleClick() {
        MapToolRouter router = new MapToolRouter();
        assertFalse(router.route(press(1, MapPointerButton.PRIMARY), context).suppressDefault());
        assertFalse(router.route(release(2, MapPointerButton.PRIMARY), context).suppressDefault());
        RecordingTool tool = new RecordingTool(MapToolResult.PASS);

        router.setActiveTool(tool, cancel(3, MapToolCancelReason.TOOL_REPLACED), context);
        RouteOutcome click = router.route(click(4, MapPointerButton.PRIMARY), context);

        assertTrue(click.suppressDefault());
        assertEquals(List.of("activate"), tool.calls);
    }

    private static MapToolEvent press(long sequence, MapPointerButton button) {
        return event(sequence, MapToolEvent.Type.PRESS, button, Set.of(button), Optional.empty());
    }

    private static MapToolEvent drag(long sequence, MapPointerButton button) {
        return event(
                sequence,
                MapToolEvent.Type.DRAG,
                MapPointerButton.NONE,
                Set.of(button),
                Optional.empty());
    }

    private static MapToolEvent release(long sequence, MapPointerButton button) {
        return event(sequence, MapToolEvent.Type.RELEASE, button, Set.of(), Optional.empty());
    }

    private static MapToolEvent click(long sequence, MapPointerButton button) {
        return event(sequence, MapToolEvent.Type.CLICK, button, Set.of(), Optional.empty());
    }

    private static MapToolEvent move(long sequence) {
        return event(
                sequence,
                MapToolEvent.Type.MOVE,
                MapPointerButton.NONE,
                Set.of(),
                Optional.empty());
    }

    private static MapToolEvent wheel(long sequence, MapPointerButton button) {
        return new MapToolEvent(
                sequence,
                MapToolEvent.Type.WHEEL,
                10.0,
                20.0,
                Optional.empty(),
                MapPointerButton.NONE,
                Set.of(button),
                Set.of(),
                0,
                0.5,
                false,
                Optional.empty());
    }

    private static MapToolEvent cancel(long sequence, MapToolCancelReason reason) {
        return cancel(sequence, reason, Set.of());
    }

    private static MapToolEvent cancel(
            long sequence, MapToolCancelReason reason, Set<MapPointerButton> buttons) {
        return event(
                sequence,
                MapToolEvent.Type.CANCEL,
                MapPointerButton.NONE,
                buttons,
                Optional.of(reason));
    }

    private static MapToolEvent event(
            long sequence,
            MapToolEvent.Type type,
            MapPointerButton button,
            Set<MapPointerButton> buttons,
            Optional<MapToolCancelReason> reason) {
        return new MapToolEvent(
                sequence,
                type,
                10.0,
                20.0,
                Optional.empty(),
                button,
                buttons,
                Set.of(),
                type == MapToolEvent.Type.CLICK ? 1 : 0,
                0.0,
                false,
                reason);
    }

    private static final class RecordingTool implements MapTool {
        private final List<String> calls;
        private final String prefix;
        private MapToolResult result;

        private RecordingTool(MapToolResult result) {
            this(result, "", new ArrayList<>());
        }

        private RecordingTool(MapToolResult result, String prefix, List<String> calls) {
            this.result = result;
            this.prefix = prefix;
            this.calls = calls;
        }

        @Override
        public void onActivate(MapToolContext ignored) {
            calls.add(label("activate"));
        }

        @Override
        public MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext ignored) {
            calls.add(label(event.type().name()));
            return result;
        }

        @Override
        public void onDeactivate(MapToolContext ignored) {
            calls.add(label("deactivate"));
        }

        @Override
        public MapCursorIntent cursorIntent() {
            return MapCursorIntent.CROSSHAIR;
        }

        private String label(String value) {
            return prefix.isEmpty() ? value : prefix + ":" + value;
        }
    }

    private static final class StubContext implements MapToolContext {
        @Override
        public CrsDefinition mapCrs() {
            return CrsDefinitions.EPSG_4326;
        }

        @Override
        public CrsDefinition displayCrs() {
            return CrsDefinitions.EPSG_4326;
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
        public void requestRepaint() {}
    }
}
