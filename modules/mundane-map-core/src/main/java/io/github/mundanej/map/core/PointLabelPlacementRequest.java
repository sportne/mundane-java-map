package io.github.mundanej.map.core;

import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PointLabelTexts;
import io.github.mundanej.map.api.ScreenBox;
import java.util.Objects;

/**
 * Immutable measured input for one bounded point-label placement request.
 *
 * <p>The request owns only immutable toolkit-neutral values. Callers retain no lifecycle
 * obligation, and the placement operation defensively copies the request list after enforcing its
 * batch-size limit.
 *
 * @param layerId stable layer identifier
 * @param featureId stable feature identifier
 * @param text exact bounded single-line text
 * @param style immutable toolkit-neutral text style
 * @param markerBounds final nominal marker bounds
 * @param relativeVisualBounds measured visual bounds relative to baseline zero
 * @param advance finite non-negative measured text advance
 * @param profile immutable point-label placement profile
 * @param layerIndex zero-based layer traversal index
 * @param featureIndex zero-based feature traversal index within the layer
 * @param ordinaryPaintOrdinal unique non-negative ordinary paint order
 */
public record PointLabelPlacementRequest(
        String layerId,
        String featureId,
        String text,
        LabelTextStyle style,
        ScreenBox markerBounds,
        ScreenBox relativeVisualBounds,
        double advance,
        PointLabelProfile profile,
        int layerIndex,
        int featureIndex,
        int ordinaryPaintOrdinal) {
    /** Validates identity, text, metric, and traversal invariants. */
    public PointLabelPlacementRequest {
        layerId = requireIdentifier(layerId, "layerId");
        featureId = requireIdentifier(featureId, "featureId");
        PointLabelTexts.requireSupported(text);
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(markerBounds, "markerBounds");
        Objects.requireNonNull(relativeVisualBounds, "relativeVisualBounds");
        Objects.requireNonNull(profile, "profile");
        if (!style.equals(profile.style())) {
            throw new IllegalArgumentException("style must equal profile.style");
        }
        if (!Double.isFinite(advance) || advance < 0.0) {
            throw new IllegalArgumentException("advance must be finite and non-negative");
        }
        if (layerIndex < 0 || featureIndex < 0 || ordinaryPaintOrdinal < 0) {
            throw new IllegalArgumentException(
                    "layerIndex, featureIndex, and ordinaryPaintOrdinal must be non-negative");
        }
    }

    private static String requireIdentifier(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
