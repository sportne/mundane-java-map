package io.github.mundanej.map.api;

/** Toolkit-neutral interactive map tool. */
public interface MapTool {
    /** Activates this tool for the supplied view context. */
    default void onActivate(MapToolContext context) {}

    /** Handles one routed event and returns the desired default-routing result. */
    MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context);

    /** Deactivates this tool and releases tool-owned state. */
    default void onDeactivate(MapToolContext context) {}

    /** Returns the tool's current toolkit-neutral cursor intent. */
    default MapCursorIntent cursorIntent() {
        return MapCursorIntent.DEFAULT;
    }
}
