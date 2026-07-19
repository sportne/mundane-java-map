package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Replaces one immutable feature while preserving its snapshot position.
 *
 * @param featureId exact identity to replace
 * @param replacement complete replacement with the same identity
 */
public record ReplaceFeature(String featureId, FeatureRecord replacement)
        implements FeatureEditCommand {
    /** Validates identity consistency. */
    public ReplaceFeature {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(replacement, "replacement");
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
        if (!featureId.equals(replacement.id())) {
            throw new IllegalArgumentException("replacement id must equal featureId");
        }
    }
}
