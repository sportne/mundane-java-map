package io.github.mundanej.map.core;

import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelPlacementProblem;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PointLabelTexts;
import io.github.mundanej.map.api.ScreenBox;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Deterministic toolkit-neutral candidate geometry for singular-point labels. */
public final class PointLabelLayouts {
    private PointLabelLayouts() {}

    /**
     * Places one measured text visual box at one compass position around marker bounds.
     *
     * @param layerId stable layer identifier
     * @param featureId stable feature identifier
     * @param text exact label text
     * @param style immutable text style
     * @param markerBounds final nominal marker bounds
     * @param relativeVisualBounds measured bounds relative to baseline zero
     * @param advance measured non-negative advance
     * @param profile immutable point-label profile
     * @param position requested compass position
     * @param layerIndex zero-based captured layer index for stable failures
     * @param featureIndex zero-based captured feature index for stable failures
     * @param ordinaryPaintOrdinal non-negative ordinary paint order
     * @return immutable toolkit-neutral placed candidate
     */
    public static PlacedPointLabel place(
            String layerId,
            String featureId,
            String text,
            LabelTextStyle style,
            ScreenBox markerBounds,
            ScreenBox relativeVisualBounds,
            double advance,
            PointLabelProfile profile,
            PointLabelPosition position,
            int layerIndex,
            int featureIndex,
            int ordinaryPaintOrdinal) {
        Objects.requireNonNull(markerBounds, "markerBounds");
        Objects.requireNonNull(relativeVisualBounds, "relativeVisualBounds");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(position, "position");
        requireIdentifier(layerId, "layerId");
        requireIdentifier(featureId, "featureId");
        PointLabelTexts.requireSupported(text);
        Objects.requireNonNull(style, "style");
        if (!Double.isFinite(advance) || advance < 0.0) {
            throw new IllegalArgumentException("advance must be finite and non-negative");
        }
        if (layerIndex < 0 || featureIndex < 0) {
            throw new IllegalArgumentException("layerIndex and featureIndex must be non-negative");
        }
        if (ordinaryPaintOrdinal < 0) {
            throw new IllegalArgumentException("ordinaryPaintOrdinal must be non-negative");
        }

        double anchorMinX = markerBounds.minX() + profile.offsetXPixels();
        double anchorMinY = markerBounds.minY() + profile.offsetYPixels();
        double anchorMaxX = markerBounds.maxX() + profile.offsetXPixels();
        double anchorMaxY = markerBounds.maxY() + profile.offsetYPixels();
        requireFinite(
                layerIndex,
                featureIndex,
                position,
                "anchorBounds",
                anchorMinX,
                anchorMinY,
                anchorMaxX,
                anchorMaxY);

        double width = relativeVisualBounds.maxX() - relativeVisualBounds.minX();
        double height = relativeVisualBounds.maxY() - relativeVisualBounds.minY();
        requireFinite(layerIndex, featureIndex, position, "textBounds", width, height);

        double minX;
        double minY;
        switch (position) {
            case N -> {
                minX = center(anchorMinX, anchorMaxX, width);
                minY = anchorMinY - profile.gapPixels() - height;
            }
            case NE -> {
                minX = anchorMaxX + profile.gapPixels();
                minY = anchorMinY - profile.gapPixels() - height;
            }
            case E -> {
                minX = anchorMaxX + profile.gapPixels();
                minY = center(anchorMinY, anchorMaxY, height);
            }
            case SE -> {
                minX = anchorMaxX + profile.gapPixels();
                minY = anchorMaxY + profile.gapPixels();
            }
            case S -> {
                minX = center(anchorMinX, anchorMaxX, width);
                minY = anchorMaxY + profile.gapPixels();
            }
            case SW -> {
                minX = anchorMinX - profile.gapPixels() - width;
                minY = anchorMaxY + profile.gapPixels();
            }
            case W -> {
                minX = anchorMinX - profile.gapPixels() - width;
                minY = center(anchorMinY, anchorMaxY, height);
            }
            case NW -> {
                minX = anchorMinX - profile.gapPixels() - width;
                minY = anchorMinY - profile.gapPixels() - height;
            }
            default -> throw new AssertionError(position);
        }
        requireFinite(layerIndex, featureIndex, position, "candidateBounds", minX, minY);

        double baselineX = minX - relativeVisualBounds.minX();
        double baselineY = minY - relativeVisualBounds.minY();
        requireFinite(layerIndex, featureIndex, position, "baseline", baselineX, baselineY);

        double visualMinX = relativeVisualBounds.minX() + baselineX;
        double visualMinY = relativeVisualBounds.minY() + baselineY;
        double visualMaxX = relativeVisualBounds.maxX() + baselineX;
        double visualMaxY = relativeVisualBounds.maxY() + baselineY;
        requireFinite(
                layerIndex,
                featureIndex,
                position,
                "visualBounds",
                visualMinX,
                visualMinY,
                visualMaxX,
                visualMaxY);
        ScreenBox visual = new ScreenBox(visualMinX, visualMinY, visualMaxX, visualMaxY);

        double padding = profile.collisionPaddingPixels();
        double collisionMinX = visualMinX - padding;
        double collisionMinY = visualMinY - padding;
        double collisionMaxX = visualMaxX + padding;
        double collisionMaxY = visualMaxY + padding;
        requireFinite(
                layerIndex,
                featureIndex,
                position,
                "collisionBounds",
                collisionMinX,
                collisionMinY,
                collisionMaxX,
                collisionMaxY);
        ScreenBox collision =
                new ScreenBox(collisionMinX, collisionMinY, collisionMaxX, collisionMaxY);

        return new PlacedPointLabel(
                layerId,
                featureId,
                text,
                style,
                baselineX,
                baselineY,
                advance,
                visual,
                collision,
                ordinaryPaintOrdinal);
    }

    private static double center(double minimum, double maximum, double extent) {
        return minimum + ((maximum - minimum) - extent) / 2.0;
    }

    private static void requireFinite(
            int layerIndex,
            int featureIndex,
            PointLabelPosition position,
            String quantity,
            double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw layoutFailure(layerIndex, featureIndex, position, quantity);
            }
        }
    }

    private static void requireIdentifier(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
    }

    private static LabelPlacementException layoutFailure(
            int layerIndex, int featureIndex, PointLabelPosition position, String quantity) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("layerIndex", Integer.toString(layerIndex));
        context.put("featureIndex", Integer.toString(featureIndex));
        context.put("position", position.name());
        context.put("quantity", quantity);
        return new LabelPlacementException(
                new LabelPlacementProblem(
                        "LABEL_LAYOUT_NON_FINITE",
                        "Point-label candidate layout is non-finite",
                        context));
    }
}
