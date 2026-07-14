package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete toolkit-neutral input event routed to an active map tool. */
public record MapToolEvent(
        long sequence,
        Type type,
        double screenX,
        double screenY,
        Optional<Coordinate> mapCoordinate,
        MapPointerButton button,
        Set<MapPointerButton> buttonsDown,
        Set<MapInputModifier> modifiers,
        int clickCount,
        double wheelRotation,
        boolean popupTrigger,
        Optional<MapToolCancelReason> cancelReason) {
    /** Supported routed input kinds. */
    public enum Type {
        /** Button press. */
        PRESS,
        /** Movement with one or more buttons down. */
        DRAG,
        /** Button release. */
        RELEASE,
        /** Movement without buttons down. */
        MOVE,
        /** Platform click notification. */
        CLICK,
        /** Wheel rotation. */
        WHEEL,
        /** Lifecycle, state-loss, or user cancellation. */
        CANCEL
    }

    /** Validates and defensively copies all event values. */
    public MapToolEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mapCoordinate, "mapCoordinate");
        Objects.requireNonNull(button, "button");
        buttonsDown = Set.copyOf(Objects.requireNonNull(buttonsDown, "buttonsDown"));
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        Objects.requireNonNull(cancelReason, "cancelReason");
        if (sequence <= 0) {
            throw new IllegalArgumentException("Event sequence must be positive");
        }
        if (!Double.isFinite(screenX)
                || !Double.isFinite(screenY)
                || !Double.isFinite(wheelRotation)) {
            throw new IllegalArgumentException(
                    "Event coordinates and wheel rotation must be finite");
        }
        if (buttonsDown.contains(MapPointerButton.NONE)) {
            throw new IllegalArgumentException("buttonsDown must not contain NONE");
        }
        validateType(
                type, button, buttonsDown, clickCount, wheelRotation, popupTrigger, cancelReason);
    }

    private static void validateType(
            Type type,
            MapPointerButton button,
            Set<MapPointerButton> buttonsDown,
            int clickCount,
            double wheelRotation,
            boolean popupTrigger,
            Optional<MapToolCancelReason> cancelReason) {
        boolean noButton = button.equals(MapPointerButton.NONE);
        switch (type) {
            case PRESS ->
                    require(
                            !noButton
                                    && buttonsDown.contains(button)
                                    && clickCount >= 0
                                    && wheelRotation == 0.0
                                    && cancelReason.isEmpty(),
                            type);
            case DRAG ->
                    require(
                            noButton
                                    && !buttonsDown.isEmpty()
                                    && clickCount == 0
                                    && wheelRotation == 0.0
                                    && !popupTrigger
                                    && cancelReason.isEmpty(),
                            type);
            case RELEASE ->
                    require(
                            !noButton
                                    && !buttonsDown.contains(button)
                                    && clickCount >= 0
                                    && wheelRotation == 0.0
                                    && cancelReason.isEmpty(),
                            type);
            case MOVE ->
                    require(
                            noButton
                                    && buttonsDown.isEmpty()
                                    && clickCount == 0
                                    && wheelRotation == 0.0
                                    && !popupTrigger
                                    && cancelReason.isEmpty(),
                            type);
            case CLICK ->
                    require(
                            !noButton
                                    && !buttonsDown.contains(button)
                                    && clickCount > 0
                                    && wheelRotation == 0.0
                                    && cancelReason.isEmpty(),
                            type);
            case WHEEL ->
                    require(
                            noButton && clickCount == 0 && !popupTrigger && cancelReason.isEmpty(),
                            type);
            case CANCEL ->
                    require(
                            noButton
                                    && clickCount == 0
                                    && wheelRotation == 0.0
                                    && !popupTrigger
                                    && cancelReason.isPresent(),
                            type);
        }
    }

    private static void require(boolean valid, Type type) {
        if (!valid) {
            throw new IllegalArgumentException("Invalid values for " + type + " event");
        }
    }
}
