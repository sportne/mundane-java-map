package io.github.mundanej.map.api;

import java.util.Objects;

/** Stable identity of the single selected feature. */
public record FeatureSelection(String layerId, String featureId) {
    /** Validates both exact non-blank identifiers. */
    public FeatureSelection {
        layerId = requireText(layerId, "layerId");
        featureId = requireText(featureId, "featureId");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
