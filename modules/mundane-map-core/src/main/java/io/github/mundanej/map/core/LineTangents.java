package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.SymbolException;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.OptionalDouble;

/** Toolkit-neutral outward endpoint tangent calculations for screen-coordinate line parts. */
public final class LineTangents {
    private LineTangents() {}

    /** Finds the first and last non-zero segment and returns outward clockwise screen bearings. */
    public static LineEndpointBearings outwardScreenBearings(
            CoordinateSequence screenCoordinates, String featureId, int partIndex) {
        Objects.requireNonNull(screenCoordinates, "screenCoordinates");
        Objects.requireNonNull(featureId, "featureId");
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
        if (partIndex < 0) {
            throw new IllegalArgumentException("partIndex must not be negative");
        }

        OptionalDouble start = OptionalDouble.empty();
        double firstX = screenCoordinates.x(0);
        double firstY = screenCoordinates.y(0);
        for (int index = 1; index < screenCoordinates.size(); index++) {
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
        int lastIndex = screenCoordinates.size() - 1;
        double lastX = screenCoordinates.x(lastIndex);
        double lastY = screenCoordinates.y(lastIndex);
        for (int index = lastIndex - 1; index >= 0; index--) {
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
