package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable geometry and exact identity captured for one snap query.
 *
 * @param featureId non-blank feature identity within its reference layer
 * @param geometry immutable geometry in the reference-set CRS
 */
public record SnapFeature(String featureId, Geometry geometry) {
    /** Validates exact identity and geometry. */
    public SnapFeature {
        Objects.requireNonNull(featureId, "featureId");
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
        Objects.requireNonNull(geometry, "geometry");
    }
}
