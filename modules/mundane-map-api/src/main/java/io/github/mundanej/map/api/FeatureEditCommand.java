package io.github.mundanej.map.api;

/** One closed immutable operation in an atomic feature-edit transaction. */
public sealed interface FeatureEditCommand permits CreateFeature, DeleteFeature, ReplaceFeature {
    /**
     * Returns the exact feature identity named by this command.
     *
     * @return non-blank feature identifier
     */
    String featureId();
}
