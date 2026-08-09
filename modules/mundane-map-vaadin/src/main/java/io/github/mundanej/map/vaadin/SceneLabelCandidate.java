package io.github.mundanej.map.vaadin;

import java.util.Objects;

/** One scene-ordered candidate with stable traversal and label paint ordinals. */
record SceneLabelCandidate(
        BrowserLabelCandidate candidate, int layerIndex, int ordinaryPaintOrdinal) {
    SceneLabelCandidate {
        Objects.requireNonNull(candidate, "candidate");
        if (layerIndex < 0 || ordinaryPaintOrdinal < 0) {
            throw new IllegalArgumentException("label ordinals must be non-negative");
        }
    }
}
