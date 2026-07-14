package io.github.mundanej.map.core;

import io.github.mundanej.map.api.MapCursorIntent;
import java.util.Objects;

/** Immutable result of one toolkit-neutral tool-router operation. */
public record RouteOutcome(
        boolean suppressDefault, boolean captured, MapCursorIntent cursorIntent) {
    /** Validates the cursor intent. */
    public RouteOutcome {
        Objects.requireNonNull(cursorIntent, "cursorIntent");
    }
}
