package io.github.mundanej.map.core;

import io.github.mundanej.map.api.MapCursorIntent;
import java.util.Objects;

/**
 * Immutable result of one toolkit-neutral tool-router operation.
 *
 * @param suppressDefault whether the host must suppress its default navigation behavior
 * @param captured whether the active tool owns pointer capture after the operation
 * @param cursorIntent toolkit-neutral cursor requested after the operation
 */
public record RouteOutcome(
        boolean suppressDefault, boolean captured, MapCursorIntent cursorIntent) {
    /** Validates the cursor intent. */
    public RouteOutcome {
        Objects.requireNonNull(cursorIntent, "cursorIntent");
    }
}
