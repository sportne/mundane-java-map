package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable transition between two distinct map-hover identities.
 *
 * @param previous prior hit, or empty when hover begins
 * @param current new hit, or empty when hover ends
 */
public record MapHoverEvent(Optional<MapHit> previous, Optional<MapHit> current) {
    /** Validates a real old/new hover transition. */
    public MapHoverEvent {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (previous.equals(current)) {
            throw new IllegalArgumentException("previous and current must differ");
        }
    }
}
