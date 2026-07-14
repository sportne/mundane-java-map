package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable transition between two distinct map-selection identities. */
public record MapSelectionEvent(
        Optional<FeatureSelection> previous, Optional<FeatureSelection> current) {
    /** Validates a real old/new selection transition. */
    public MapSelectionEvent {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (previous.equals(current)) {
            throw new IllegalArgumentException("previous and current must differ");
        }
    }
}
