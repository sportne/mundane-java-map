package io.github.mundanej.map.api;

/** Reason an in-progress map-tool interaction was cancelled. */
public enum MapToolCancelReason {
    /** Another tool replaced the active tool. */
    TOOL_REPLACED,
    /** The active tool was explicitly cleared. */
    TOOL_CLEARED,
    /** The view lost keyboard focus. */
    FOCUS_LOST,
    /** The view became disabled. */
    VIEW_DISABLED,
    /** The view was removed from its display hierarchy. */
    VIEW_REMOVED,
    /** The pointer left the view without an active capture. */
    POINTER_EXITED,
    /** Host button state became inconsistent with the routed gesture. */
    POINTER_STATE_LOST,
    /** The user explicitly cancelled the interaction. */
    USER_CANCEL
}
