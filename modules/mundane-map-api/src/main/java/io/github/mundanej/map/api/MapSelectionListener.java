package io.github.mundanej.map.api;

/** Receives synchronous map-selection identity transitions. */
@FunctionalInterface
public interface MapSelectionListener {
    /** Handles one committed selection transition. */
    void onMapSelectionChanged(MapSelectionEvent event);
}
