package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Deterministic winning target from a bounded snap query.
 *
 * @param coordinate exact vertex or reverse-transformed closest coordinate in the reference CRS
 * @param distancePixels finite non-negative logical-screen distance
 * @param targetType vertex or segment target
 * @param layerId exact winning layer identity
 * @param featureId exact winning feature identity
 * @param componentIndex zero-based geometry component index
 * @param partIndex zero-based ring/part index within the component
 * @param elementIndex zero-based vertex or segment-start index
 */
public record SnapResult(
        Coordinate coordinate,
        double distancePixels,
        SnapTargetType targetType,
        String layerId,
        String featureId,
        int componentIndex,
        int partIndex,
        int elementIndex) {
    /** Validates the complete winner. */
    public SnapResult {
        Objects.requireNonNull(coordinate, "coordinate");
        if (!Double.isFinite(distancePixels) || distancePixels < 0) {
            throw new IllegalArgumentException("snap distance must be finite and non-negative");
        }
        Objects.requireNonNull(targetType, "targetType");
        layerId = requireText(layerId, "layerId");
        featureId = requireText(featureId, "featureId");
        if (componentIndex < 0 || partIndex < 0 || elementIndex < 0) {
            throw new IllegalArgumentException("snap geometry indexes must be non-negative");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
