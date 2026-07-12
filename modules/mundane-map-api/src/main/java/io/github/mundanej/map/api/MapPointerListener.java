package io.github.mundanej.map.api;

/** Receives map pointer events. */
@FunctionalInterface
public interface MapPointerListener {
    /** Handles an event on the Swing event-dispatch thread. */
    void onMapPointerEvent(MapPointerEvent event);
}

