package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Deletes one feature from an edit snapshot.
 *
 * @param featureId exact identity to delete
 */
public record DeleteFeature(String featureId) implements FeatureEditCommand {
    /** Validates the identity. */
    public DeleteFeature {
        Objects.requireNonNull(featureId, "featureId");
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
    }
}
