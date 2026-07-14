package io.github.mundanej.map.api;

/** Result of handling one map-tool event. */
public enum MapToolResult {
    /** Permit the view's remaining default behavior. */
    PASS,
    /** Suppress the view's remaining default behavior. */
    CONSUME,
    /** Capture the sole changed button from a press and suppress defaults. */
    CAPTURE
}
