package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered immutable snap features in one explicit priority layer.
 *
 * @param layerId non-blank exact layer identity
 * @param features ordered features from lower to higher feature priority
 */
public record SnapReferenceLayer(String layerId, List<SnapFeature> features) {
    /** Defensively copies values and rejects ambiguous duplicate feature identities. */
    public SnapReferenceLayer {
        Objects.requireNonNull(layerId, "layerId");
        if (layerId.isBlank()) {
            throw new IllegalArgumentException("layerId must not be blank");
        }
        features = List.copyOf(Objects.requireNonNull(features, "features"));
        Set<String> identities = new HashSet<>();
        for (SnapFeature feature : features) {
            Objects.requireNonNull(feature, "feature");
            if (!identities.add(feature.featureId())) {
                throw new IllegalArgumentException("snap feature identities must be unique");
            }
        }
    }
}
