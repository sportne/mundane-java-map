package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Captured feature-source ceilings.
 *
 * @param queryLimits immutable ceilings applied to every source query
 */
public record FeatureSourceLimits(FeatureQueryLimits queryLimits) {
    /** Level 1 defaults. */
    public static final FeatureSourceLimits LEVEL_1 =
            new FeatureSourceLimits(FeatureQueryLimits.LEVEL_1);

    /** Validates limits. */
    public FeatureSourceLimits {
        Objects.requireNonNull(queryLimits, "queryLimits");
    }
}
