package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Stable identity of the single selected feature.
 *
 * @param layerId non-blank logical layer identity
 * @param featureId non-blank stable feature identity within the layer
 */
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
