package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable toolkit-neutral output of one point-label placement decision.
 *
 * @param layerId stable layer identifier
 * @param featureId stable feature identifier
 * @param text exact retained label text
 * @param style immutable toolkit-neutral text style
 * @param baselineX finite logical-screen baseline x ordinate
 * @param baselineY finite logical-screen baseline y ordinate
 * @param advance finite non-negative measured text advance
 * @param visualBounds measured translated visual bounds
 * @param collisionBounds padded collision bounds
 * @param ordinaryPaintOrdinal ordinary feature paint order
 */
public record PlacedPointLabel(
        String layerId,
        String featureId,
        String text,
        LabelTextStyle style,
        double baselineX,
        double baselineY,
        double advance,
        ScreenBox visualBounds,
        ScreenBox collisionBounds,
        int ordinaryPaintOrdinal) {
    /** Validates bounded identity, text, and finite metric values. */
    public PlacedPointLabel {
        layerId = requireText(layerId, "layerId");
        featureId = requireText(featureId, "featureId");
        PointLabelTexts.requireSupported(text);
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(visualBounds, "visualBounds");
        Objects.requireNonNull(collisionBounds, "collisionBounds");
        if (!Double.isFinite(baselineX)
                || !Double.isFinite(baselineY)
                || !Double.isFinite(advance)
                || advance < 0.0) {
            throw new IllegalArgumentException(
                    "label metrics must be finite and advance non-negative");
        }
        if (ordinaryPaintOrdinal < 0) {
            throw new IllegalArgumentException("ordinaryPaintOrdinal must be non-negative");
        }
        if (collisionBounds.minX() > visualBounds.minX()
                || collisionBounds.minY() > visualBounds.minY()
                || collisionBounds.maxX() < visualBounds.maxX()
                || collisionBounds.maxY() < visualBounds.maxY()) {
            throw new IllegalArgumentException("collisionBounds must contain visualBounds");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
