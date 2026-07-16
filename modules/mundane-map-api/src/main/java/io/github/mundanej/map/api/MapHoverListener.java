package io.github.mundanej.map.api;

/** Receives synchronous map-hover identity transitions. */
@FunctionalInterface
public interface MapHoverListener {
    /**
     * Handles one committed hover transition.
     *
     * @param event immutable transition event
     */
    void onMapHoverChanged(MapHoverEvent event);
}
