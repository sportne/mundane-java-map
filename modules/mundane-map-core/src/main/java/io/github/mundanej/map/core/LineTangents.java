package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.SymbolException;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.OptionalDouble;

/** Toolkit-neutral outward endpoint tangent calculations for screen-coordinate line parts. */
public final class LineTangents {
    private LineTangents() {}

    /**
     * Finds the first and last non-zero segment and returns outward clockwise screen bearings.
     *
     * @param screenCoordinates immutable coordinates in logical screen space
     * @param featureId non-blank feature identifier used in stable failure context
     * @param partIndex non-negative declared part index used in stable failure context
     * @return optional outward endpoint bearings; both are empty for a coincident part
     */
    public static LineEndpointBearings outwardScreenBearings(
            CoordinateSequence screenCoordinates, String featureId, int partIndex) {
        Objects.requireNonNull(screenCoordinates, "screenCoordinates");
        return outwardScreenBearings(
                screenCoordinates, 0, screenCoordinates.size(), featureId, partIndex);
    }

    /**
     * Finds outward endpoint bearings in one non-empty coordinate range without copying the range.
     *
     * @param screenCoordinates packed screen coordinates containing the line part
     * @param startInclusive first coordinate index in the part
     * @param endExclusive coordinate fencepost immediately after the part
     * @param featureId non-blank feature identifier used in stable failure context
     * @param partIndex non-negative declared part index used in stable failure context
     * @return optional start and end bearings; both are empty when the range is coincident
     * @throws IndexOutOfBoundsException when the range is outside the coordinate sequence
     * @throws IllegalArgumentException when the range is empty, the feature ID is blank, or the
     *     part index is negative
     */
    public static LineEndpointBearings outwardScreenBearings(
            CoordinateSequence screenCoordinates,
            int startInclusive,
            int endExclusive,
            String featureId,
            int partIndex) {
        Objects.requireNonNull(screenCoordinates, "screenCoordinates");
        Objects.requireNonNull(featureId, "featureId");
        if (startInclusive < 0 || endExclusive > screenCoordinates.size()) {
            throw new IndexOutOfBoundsException(
                    "Coordinate range ["
                            + startInclusive
                            + ", "
                            + endExclusive
                            + ") is outside sequence size "
                            + screenCoordinates.size());
        }
        if (startInclusive >= endExclusive) {
            throw new IllegalArgumentException("Coordinate range must not be empty or reversed");
        }
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
        if (partIndex < 0) {
            throw new IllegalArgumentException("partIndex must not be negative");
        }

        OptionalDouble start = OptionalDouble.empty();
        double firstX = screenCoordinates.x(startInclusive);
        double firstY = screenCoordinates.y(startInclusive);
        for (int index = startInclusive + 1; index < endExclusive; index++) {
            double x = screenCoordinates.x(index);
            double y = screenCoordinates.y(index);
            if (x != firstX || y != firstY) {
                start =
                        OptionalDouble.of(
                                bearing(firstX - x, firstY - y, featureId, partIndex, "start"));
                break;
            }
        }

        OptionalDouble end = OptionalDouble.empty();
        int lastIndex = endExclusive - 1;
        double lastX = screenCoordinates.x(lastIndex);
        double lastY = screenCoordinates.y(lastIndex);
        for (int index = lastIndex - 1; index >= startInclusive; index--) {
            double x = screenCoordinates.x(index);
            double y = screenCoordinates.y(index);
            if (x != lastX || y != lastY) {
                end = OptionalDouble.of(bearing(lastX - x, lastY - y, featureId, partIndex, "end"));
                break;
            }
        }
        return new LineEndpointBearings(start, end);
    }

    private static double bearing(
            double deltaX, double deltaY, String featureId, int partIndex, String endpoint) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("featureId", featureId);
            context.put("partIndex", Integer.toString(partIndex));
            context.put("endpoint", endpoint);
            context.put("quantity", "line-tangent-delta");
            throw new SymbolException(
                    SymbolException.TRANSFORM_NON_FINITE,
                    "Line tangent produced a non-finite delta",
                    context);
        }
        double degrees = StrictMath.toDegrees(StrictMath.atan2(deltaY, deltaX));
        double normalized = degrees % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        return normalized == 0.0 ? 0.0 : normalized;
    }
}
