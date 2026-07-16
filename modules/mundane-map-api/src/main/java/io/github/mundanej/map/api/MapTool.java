package io.github.mundanej.map.api;

/** Toolkit-neutral interactive map tool. */
public interface MapTool {
    /**
     * Activates this tool for the supplied view context.
     *
     * @param context callback-scoped view services
     */
    default void onActivate(MapToolContext context) {}

    /**
     * Handles one routed event and returns the desired default-routing result.
     *
     * @param event immutable routed event
     * @param context callback-scoped view services
     * @return whether default navigation should also handle the event
     */
    MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context);

    /**
     * Handles one bounded semantic command.
     *
     * @param event immutable command event
     * @param context callback-scoped view services
     * @return whether default command routing should continue
     */
    default MapToolResult onMapToolCommand(MapToolCommandEvent event, MapToolContext context) {
        return MapToolResult.PASS;
    }

    /**
     * Deactivates this tool and releases tool-owned state.
     *
     * @param context callback-scoped view services
     */
    default void onDeactivate(MapToolContext context) {}

    /**
     * Returns the tool's current toolkit-neutral cursor intent.
     *
     * @return current cursor intent
     */
    default MapCursorIntent cursorIntent() {
        return MapCursorIntent.DEFAULT;
    }
}
