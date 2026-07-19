package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Appends one immutable feature to an edit snapshot.
 *
 * @param feature complete immutable feature to append
 */
public record CreateFeature(FeatureRecord feature) implements FeatureEditCommand {
    /** Validates the complete feature. */
    public CreateFeature {
        Objects.requireNonNull(feature, "feature");
    }

    @Override
    public String featureId() {
        return feature.id();
    }
}
