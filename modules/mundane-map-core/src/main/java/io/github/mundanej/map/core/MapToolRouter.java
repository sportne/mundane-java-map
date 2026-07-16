package io.github.mundanej.map.core;

import io.github.mundanej.map.api.MapCursorIntent;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MapToolResult;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Call-thread-confined state machine for one active map tool.
 *
 * <p>The router owns capture, lifecycle ordering, stale-gesture isolation, and cursor intent. A
 * host remains responsible for converting toolkit events and applying defaults only when the
 * returned outcome permits them.
 */
public final class MapToolRouter {
    private final Set<MapPointerButton> quarantinedButtons = new HashSet<>();
    private MapTool activeTool;
    private MapPointerButton capturedButton;
    private MapPointerButton pendingClickButton;
    private ReleaseCandidate releaseCandidate;
    private MapCursorIntent cursorIntent = MapCursorIntent.DEFAULT;
    private long lastSequence;
    private boolean dispatching;
    private boolean lifecycleCallbacks;
    private boolean unavailableCancellationArmed;
    private PendingOperation pendingOperation;

    /** Creates a router with no active tool, capture, or custom cursor intent. */
    public MapToolRouter() {}

    /**
     * Returns the active tool, if any.
     *
     * @return current tool instance without transferring ownership
     */
    public Optional<MapTool> activeTool() {
        return Optional.ofNullable(activeTool);
    }

    /**
     * Returns whether the active tool currently owns pointer capture.
     *
     * @return {@code true} while one pointer button is captured
     */
    public boolean captured() {
        return capturedButton != null;
    }

    /**
     * Returns the effective toolkit-neutral cursor intent.
     *
     * @return current cursor intent for the host toolkit
     */
    public MapCursorIntent currentCursorIntent() {
        return cursorIntent;
    }

    /**
     * Installs a tool, replacing a distinct active instance in deterministic lifecycle order.
     *
     * @param tool tool instance to install
     * @param replacementCancel replacement cancellation event for the previous session
     * @param context current immutable tool context
     * @return routing outcome for host default handling, capture, and cursor state
     */
    public RouteOutcome setActiveTool(
            MapTool tool, MapToolEvent replacementCancel, MapToolContext context) {
        Objects.requireNonNull(tool, "tool");
        requireCancel(replacementCancel, MapToolCancelReason.TOOL_REPLACED);
        Objects.requireNonNull(context, "context");
        if (tool == activeTool && pendingOperation == null) {
            return outcome(false);
        }
        if (dispatching) {
            rejectLifecycleReentry();
            queue(PendingOperation.replace(tool, replacementCancel, context));
            return outcome(true);
        }
        acceptSequence(replacementCancel);
        return replaceNow(tool, replacementCancel, context);
    }

    /**
     * Clears the active tool after cancellation and deactivation callbacks.
     *
     * @param clearCancel clear cancellation event for the current session
     * @param context current immutable tool context
     * @return routing outcome for host default handling, capture, and cursor state
     */
    public RouteOutcome clearActiveTool(MapToolEvent clearCancel, MapToolContext context) {
        requireCancel(clearCancel, MapToolCancelReason.TOOL_CLEARED);
        Objects.requireNonNull(context, "context");
        if (activeTool == null && pendingOperation == null) {
            return outcome(false);
        }
        if (dispatching) {
            rejectLifecycleReentry();
            queue(PendingOperation.clear(clearCancel, context));
            return outcome(true);
        }
        acceptSequence(clearCancel);
        return clearNow(clearCancel, context);
    }

    /**
     * Routes one ordinary pointer, wheel, or user-cancel event.
     *
     * @param event strictly increasing toolkit-neutral event
     * @param context current immutable tool context
     * @return routing outcome for host default handling, capture, and cursor state
     */
    public RouteOutcome route(MapToolEvent event, MapToolContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        if (dispatching) {
            throw new IllegalStateException("Recursive map-tool dispatch is not supported");
        }
        acceptSequence(event);
        if (event.type() == MapToolEvent.Type.CANCEL) {
            return cancelInteraction(event, context, true);
        }
        dispatching = true;
        try {
            unavailableCancellationArmed = false;
            reconcileIsolation(event);
            if (shouldSuppressIsolated(event)) {
                observeRelease(event);
                return outcome(true);
            }
            if (capturedButton != null
                    && event.type() != MapToolEvent.Type.RELEASE
                    && !event.buttonsDown().contains(capturedButton)) {
                return stateLost(event, context);
            }
            if (capturedButton != null
                    && event.type() == MapToolEvent.Type.RELEASE
                    && !event.button().equals(capturedButton)
                    && !event.buttonsDown().contains(capturedButton)) {
                return stateLost(event, context);
            }

            boolean capturedBefore = capturedButton != null;
            MapTool session = activeTool;
            MapToolResult result = MapToolResult.PASS;
            if (session != null) {
                result = requireResult(session.onMapToolEvent(event, context), event);
            }

            boolean matchingCapturedRelease =
                    capturedButton != null
                            && event.type() == MapToolEvent.Type.RELEASE
                            && event.button().equals(capturedButton);
            if (result == MapToolResult.CAPTURE) {
                requireCaptureAllowed(event);
                capturedButton = event.button();
            }
            if (matchingCapturedRelease) {
                capturedButton = null;
            }
            observeRelease(event);

            if (pendingOperation != null) {
                PendingOperation pending = takePending();
                applyPending(pending);
                return outcome(true);
            }
            if (session != null && session == activeTool) {
                cursorIntent = Objects.requireNonNull(session.cursorIntent(), "cursorIntent");
            }
            boolean buttonEvent =
                    event.type() == MapToolEvent.Type.PRESS
                            || event.type() == MapToolEvent.Type.DRAG
                            || event.type() == MapToolEvent.Type.RELEASE
                            || event.type() == MapToolEvent.Type.CLICK;
            boolean suppress = result != MapToolResult.PASS || (capturedBefore && buttonEvent);
            return outcome(suppress);
        } catch (RuntimeException | Error failure) {
            PendingOperation pending = takePending();
            failInteraction(event);
            if (pending != null && pending.kind() == PendingKind.CANCEL) {
                acceptSequence(pending.event());
                applyUnavailableCleanup(pending.event());
            }
            throw failure;
        } finally {
            dispatching = false;
        }
    }

    /**
     * Routes one bounded semantic command through the active tool session.
     *
     * @param event strictly increasing semantic command event
     * @param context current immutable tool context
     * @return routing outcome for host default handling and cursor state
     */
    public RouteOutcome routeCommand(MapToolCommandEvent event, MapToolContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        if (dispatching) {
            throw new IllegalStateException("Recursive map-tool dispatch is not supported");
        }
        acceptSequence(event);
        dispatching = true;
        try {
            MapTool session = activeTool;
            MapToolResult result = MapToolResult.PASS;
            if (session != null) {
                result =
                        Objects.requireNonNull(
                                session.onMapToolCommand(event, context), "tool result");
            }
            if (result == MapToolResult.CAPTURE) {
                throw new IllegalStateException("A map-tool command cannot capture");
            }
            if (pendingOperation != null) {
                PendingOperation pending = takePending();
                applyPending(pending);
                return outcome(true);
            }
            if (session != null && session == activeTool) {
                cursorIntent = Objects.requireNonNull(session.cursorIntent(), "cursorIntent");
            }
            return outcome(result != MapToolResult.PASS);
        } catch (RuntimeException | Error failure) {
            PendingOperation pending = takePending();
            cursorIntent = MapCursorIntent.DEFAULT;
            if (pending != null && pending.kind() == PendingKind.CANCEL) {
                acceptSequence(pending.event());
                applyUnavailableCleanup(pending.event());
            }
            throw failure;
        } finally {
            dispatching = false;
        }
    }

    /**
     * Cancels the current gesture while leaving the active tool installed.
     *
     * @param externalCancel cancellation event and reason
     * @param context current immutable tool context
     * @return routing outcome after capture and gesture cleanup
     */
    public RouteOutcome cancelInteraction(MapToolEvent externalCancel, MapToolContext context) {
        return cancelInteraction(externalCancel, context, false);
    }

    /**
     * Refreshes an installed tool after the host becomes available again.
     *
     * @return routing outcome containing the refreshed cursor state
     */
    public RouteOutcome resume() {
        if (dispatching) {
            throw new IllegalStateException("Cannot resume during map-tool dispatch");
        }
        unavailableCancellationArmed = false;
        if (activeTool == null) {
            cursorIntent = MapCursorIntent.DEFAULT;
            return outcome(false);
        }
        try {
            cursorIntent = Objects.requireNonNull(activeTool.cursorIntent(), "cursorIntent");
            return outcome(false);
        } catch (RuntimeException | Error failure) {
            cursorIntent = MapCursorIntent.DEFAULT;
            unavailableCancellationArmed = true;
            throw failure;
        }
    }

    private RouteOutcome cancelInteraction(
            MapToolEvent cancel, MapToolContext context, boolean sequenceAccepted) {
        requireCancellationKind(cancel);
        Objects.requireNonNull(context, "context");
        if (dispatching && !sequenceAccepted) {
            rejectLifecycleReentry();
            queue(PendingOperation.cancel(cancel, context));
            return outcome(true);
        }
        if (!sequenceAccepted) {
            acceptSequence(cancel);
        }
        MapToolCancelReason reason = cancel.cancelReason().orElseThrow();
        boolean external = reason != MapToolCancelReason.USER_CANCEL;
        if (external && unavailableCancellationArmed) {
            return outcome(true);
        }
        boolean ownedDispatch = !dispatching;
        if (ownedDispatch) {
            dispatching = true;
            lifecycleCallbacks = external;
        }
        try {
            promoteReleaseCandidate();
            boolean hadCapture = capturedButton != null;
            capturedButton = null;
            quarantinedButtons.addAll(cancel.buttonsDown());
            cursorIntent = MapCursorIntent.DEFAULT;
            unavailableCancellationArmed = external;
            if (activeTool != null) {
                MapToolResult result =
                        requireResult(activeTool.onMapToolEvent(cancel, context), cancel);
                if (result == MapToolResult.CAPTURE) {
                    throw new IllegalStateException("A cancellation event cannot capture");
                }
                if (pendingOperation != null) {
                    PendingOperation pending = takePending();
                    applyPending(pending);
                    return outcome(true);
                }
                if (!external) {
                    cursorIntent =
                            Objects.requireNonNull(activeTool.cursorIntent(), "cursorIntent");
                }
                return outcome(external || hadCapture || result != MapToolResult.PASS);
            }
            return outcome(external || hadCapture);
        } catch (RuntimeException | Error failure) {
            pendingOperation = null;
            throw failure;
        } finally {
            if (ownedDispatch) {
                lifecycleCallbacks = false;
                dispatching = false;
            }
        }
    }

    private RouteOutcome stateLost(MapToolEvent event, MapToolContext context) {
        MapToolEvent cancel =
                new MapToolEvent(
                        event.sequence(),
                        MapToolEvent.Type.CANCEL,
                        event.screenX(),
                        event.screenY(),
                        event.mapCoordinate(),
                        MapPointerButton.NONE,
                        event.buttonsDown(),
                        event.modifiers(),
                        0,
                        0.0,
                        false,
                        Optional.of(MapToolCancelReason.POINTER_STATE_LOST));
        return cancelInteraction(cancel, context, true);
    }

    private RouteOutcome replaceNow(
            MapTool replacement, MapToolEvent cancel, MapToolContext context) {
        MapTool old = activeTool;
        if (old == null) {
            promoteReleaseCandidate();
            isolateBoundary(cancel.buttonsDown());
            return activate(replacement, context);
        }
        Throwable failure = terminate(old, cancel, context);
        if (failure != null) {
            throwUnchecked(failure);
        }
        return activate(replacement, context);
    }

    private RouteOutcome clearNow(MapToolEvent cancel, MapToolContext context) {
        MapTool old = activeTool;
        if (old == null) {
            return outcome(false);
        }
        Throwable failure = terminate(old, cancel, context);
        if (failure != null) {
            throwUnchecked(failure);
        }
        return outcome(true);
    }

    private Throwable terminate(MapTool old, MapToolEvent cancel, MapToolContext context) {
        promoteReleaseCandidate();
        isolateBoundary(cancel.buttonsDown());
        activeTool = null;
        cursorIntent = MapCursorIntent.DEFAULT;
        Throwable failure = null;
        dispatching = true;
        lifecycleCallbacks = true;
        try {
            try {
                MapToolResult result =
                        Objects.requireNonNull(old.onMapToolEvent(cancel, context), "tool result");
                if (result == MapToolResult.CAPTURE) {
                    throw new IllegalStateException("A lifecycle cancellation cannot capture");
                }
            } catch (RuntimeException | Error exception) {
                failure = exception;
            }
            try {
                old.onDeactivate(context);
            } catch (RuntimeException | Error exception) {
                failure = appendFailure(failure, exception);
            }
        } finally {
            dispatching = false;
            lifecycleCallbacks = false;
            pendingOperation = null;
        }
        return failure;
    }

    private RouteOutcome activate(MapTool replacement, MapToolContext context) {
        activeTool = replacement;
        dispatching = true;
        lifecycleCallbacks = true;
        try {
            replacement.onActivate(context);
            cursorIntent = Objects.requireNonNull(replacement.cursorIntent(), "cursorIntent");
            return outcome(true);
        } catch (RuntimeException | Error failure) {
            activeTool = null;
            cursorIntent = MapCursorIntent.DEFAULT;
            try {
                replacement.onDeactivate(context);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            lifecycleCallbacks = false;
            dispatching = false;
            pendingOperation = null;
        }
    }

    private void applyPending(PendingOperation pending) {
        dispatching = false;
        acceptSequence(pending.event());
        try {
            switch (pending.kind()) {
                case REPLACE -> replaceNow(pending.tool(), pending.event(), pending.context());
                case CLEAR -> clearNow(pending.event(), pending.context());
                case CANCEL -> cancelInteraction(pending.event(), pending.context(), true);
            }
        } finally {
            dispatching = true;
        }
    }

    private void queue(PendingOperation operation) {
        if (pendingOperation != null) {
            if (pendingOperation.kind() == PendingKind.CANCEL
                    && operation.kind() == PendingKind.CANCEL) {
                return;
            }
            throw new IllegalStateException("Only one lifecycle operation may be queued");
        }
        pendingOperation = operation;
    }

    private void rejectLifecycleReentry() {
        if (lifecycleCallbacks) {
            throw new IllegalStateException(
                    "Lifecycle mutation from a lifecycle callback is invalid");
        }
    }

    private PendingOperation takePending() {
        PendingOperation pending = pendingOperation;
        pendingOperation = null;
        return pending;
    }

    private void applyUnavailableCleanup(MapToolEvent cancel) {
        MapToolCancelReason reason = cancel.cancelReason().orElseThrow();
        if (reason != MapToolCancelReason.USER_CANCEL) {
            promoteReleaseCandidate();
            isolateBoundary(cancel.buttonsDown());
            cursorIntent = MapCursorIntent.DEFAULT;
            unavailableCancellationArmed = true;
        }
    }

    private void requireCaptureAllowed(MapToolEvent event) {
        if (event.type() != MapToolEvent.Type.PRESS
                || capturedButton != null
                || event.buttonsDown().size() != 1
                || !event.buttonsDown().contains(event.button())) {
            throw new IllegalStateException("Capture requires an uncaptured sole-button press");
        }
    }

    private void reconcileIsolation(MapToolEvent event) {
        if (event.type() == MapToolEvent.Type.PRESS) {
            quarantinedButtons.remove(event.button());
            if (event.button().equals(pendingClickButton)) {
                pendingClickButton = null;
            }
        }
        if (event.type() == MapToolEvent.Type.MOVE
                || event.type() == MapToolEvent.Type.WHEEL
                || event.type() == MapToolEvent.Type.PRESS) {
            quarantinedButtons.retainAll(event.buttonsDown());
            if (pendingClickButton != null && !event.buttonsDown().contains(pendingClickButton)) {
                pendingClickButton = null;
            }
        }
        if (releaseCandidate != null
                && (event.type() != MapToolEvent.Type.CLICK
                        || !event.button().equals(releaseCandidate.button()))) {
            releaseCandidate = null;
        }
        if (pendingClickButton != null
                && (event.type() != MapToolEvent.Type.CLICK
                        || !event.button().equals(pendingClickButton))) {
            pendingClickButton = null;
        }
    }

    private boolean shouldSuppressIsolated(MapToolEvent event) {
        if (event.type() == MapToolEvent.Type.CLICK
                && (quarantinedButtons.contains(event.button())
                        || event.button().equals(pendingClickButton))) {
            quarantinedButtons.remove(event.button());
            pendingClickButton = null;
            releaseCandidate = null;
            return true;
        }
        if (event.type() == MapToolEvent.Type.RELEASE
                && quarantinedButtons.remove(event.button())) {
            pendingClickButton = event.button();
            return true;
        }
        if (event.type() == MapToolEvent.Type.DRAG) {
            for (MapPointerButton button : event.buttonsDown()) {
                if (quarantinedButtons.contains(button)) {
                    return true;
                }
            }
        }
        if (releaseCandidate != null
                && event.type() == MapToolEvent.Type.CLICK
                && event.button().equals(releaseCandidate.button())) {
            releaseCandidate = null;
        }
        return false;
    }

    private void observeRelease(MapToolEvent event) {
        if (event.type() == MapToolEvent.Type.RELEASE) {
            releaseCandidate = new ReleaseCandidate(event.button(), event.sequence());
        }
    }

    private void promoteReleaseCandidate() {
        if (releaseCandidate != null) {
            pendingClickButton = releaseCandidate.button();
            releaseCandidate = null;
        }
    }

    private void failInteraction(MapToolEvent event) {
        promoteReleaseCandidate();
        if (event.type() == MapToolEvent.Type.RELEASE) {
            pendingClickButton = event.button();
        }
        isolateBoundary(event.buttonsDown());
        cursorIntent = MapCursorIntent.DEFAULT;
    }

    private void isolateBoundary(Set<MapPointerButton> buttonsDown) {
        capturedButton = null;
        quarantinedButtons.addAll(buttonsDown);
    }

    private void acceptSequence(MapToolEvent event) {
        if (event.sequence() <= lastSequence) {
            throw new IllegalArgumentException("Map-tool event sequence must increase");
        }
        lastSequence = event.sequence();
    }

    private void acceptSequence(MapToolCommandEvent event) {
        if (event.sequence() <= lastSequence) {
            throw new IllegalArgumentException("Map-tool event sequence must increase");
        }
        lastSequence = event.sequence();
    }

    private static MapToolResult requireResult(MapToolResult result, MapToolEvent event) {
        MapToolResult required = Objects.requireNonNull(result, "tool result");
        if (required == MapToolResult.CAPTURE && event.type() != MapToolEvent.Type.PRESS) {
            throw new IllegalStateException("CAPTURE is valid only for PRESS");
        }
        return required;
    }

    private static void requireCancel(MapToolEvent event, MapToolCancelReason expected) {
        Objects.requireNonNull(event, "event");
        if (event.type() != MapToolEvent.Type.CANCEL
                || event.cancelReason().orElse(null) != expected) {
            throw new IllegalArgumentException("Expected cancellation reason " + expected);
        }
    }

    private static void requireCancellationKind(MapToolEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.type() != MapToolEvent.Type.CANCEL) {
            throw new IllegalArgumentException("Expected a cancellation event");
        }
        MapToolCancelReason reason = event.cancelReason().orElseThrow();
        if (reason == MapToolCancelReason.TOOL_REPLACED
                || reason == MapToolCancelReason.TOOL_CLEARED) {
            throw new IllegalArgumentException("Lifecycle cancellation requires lifecycle method");
        }
    }

    private RouteOutcome outcome(boolean suppressDefault) {
        return new RouteOutcome(suppressDefault, captured(), cursorIntent);
    }

    private static Throwable appendFailure(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }

    private enum PendingKind {
        REPLACE,
        CLEAR,
        CANCEL
    }

    private record PendingOperation(
            PendingKind kind, MapTool tool, MapToolEvent event, MapToolContext context) {
        static PendingOperation replace(MapTool tool, MapToolEvent event, MapToolContext context) {
            return new PendingOperation(PendingKind.REPLACE, tool, event, context);
        }

        static PendingOperation clear(MapToolEvent event, MapToolContext context) {
            return new PendingOperation(PendingKind.CLEAR, null, event, context);
        }

        static PendingOperation cancel(MapToolEvent event, MapToolContext context) {
            return new PendingOperation(PendingKind.CANCEL, null, event, context);
        }
    }

    private record ReleaseCandidate(MapPointerButton button, long sequence) {}
}
