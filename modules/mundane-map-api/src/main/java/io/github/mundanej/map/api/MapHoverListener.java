package io.github.mundanej.map.api;

/** Receives synchronous map-hover identity transitions. */
@FunctionalInterface
public interface MapHoverListener {
    /** Handles one committed hover transition. */
    void onMapHoverChanged(MapHoverEvent event);
}
