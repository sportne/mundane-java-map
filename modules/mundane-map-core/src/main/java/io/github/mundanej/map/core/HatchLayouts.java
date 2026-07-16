package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.SymbolException;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Allocation-bounded clipped diagonal hatch layout. */
public final class HatchLayouts {
    private HatchLayouts() {}

    /**
     * Covers a finite screen rectangle with clipped candidate hatch lines.
     *
     * @param pattern supported diagonal hatch pattern
     * @param bounds finite logical-screen bounds to cover
     * @param latticeOrigin logical-screen origin that fixes hatch phase
     * @param orientationBaseBearing clockwise logical-screen bearing in degrees
     * @param spacingPixels positive spacing in logical screen pixels
     * @param maxSegments positive allocation and work ceiling
     * @param featureId stable feature identifier used in structured failures
     * @return immutable packed non-zero segments clipped to {@code bounds}
     * @throws SymbolException when required work exceeds the segment limit or arithmetic is unsafe
     */
    public static HatchSegments cover(
            HatchPattern pattern,
            Envelope bounds,
            Coordinate latticeOrigin,
            double orientationBaseBearing,
            double spacingPixels,
            int maxSegments,
            String featureId) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(latticeOrigin, "latticeOrigin");
        Objects.requireNonNull(featureId, "featureId");
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
        if (!Double.isFinite(orientationBaseBearing)) {
            throw new IllegalArgumentException("orientationBaseBearing must be finite");
        }
        if (!Double.isFinite(spacingPixels) || spacingPixels <= 0.0) {
            throw new IllegalArgumentException("spacingPixels must be finite and positive");
        }
        if (maxSegments <= 0) {
            throw new IllegalArgumentException("maxSegments must be positive");
        }

        Orientation[] orientations =
                switch (pattern) {
                    case FORWARD_DIAGONAL ->
                            new Orientation[] {
                                orientation(
                                        bounds,
                                        latticeOrigin,
                                        orientationBaseBearing + 315.0,
                                        spacingPixels)
                            };
                    case BACKWARD_DIAGONAL ->
                            new Orientation[] {
                                orientation(
                                        bounds,
                                        latticeOrigin,
                                        orientationBaseBearing + 45.0,
                                        spacingPixels)
                            };
                    case CROSS_DIAGONAL ->
                            new Orientation[] {
                                orientation(
                                        bounds,
                                        latticeOrigin,
                                        orientationBaseBearing + 315.0,
                                        spacingPixels),
                                orientation(
                                        bounds,
                                        latticeOrigin,
                                        orientationBaseBearing + 45.0,
                                        spacingPixels)
                            };
                };
        long required = 0L;
        for (Orientation orientation : orientations) {
            if (orientation.countOverflow()) {
                throw limit(pattern, "overflow", maxSegments, featureId);
            }
            try {
                required = Math.addExact(required, orientation.candidateCount());
            } catch (ArithmeticException overflow) {
                throw limit(pattern, "overflow", maxSegments, featureId);
            }
        }
        if (required > maxSegments || required > Integer.MAX_VALUE / 4) {
            throw limit(pattern, Long.toString(required), maxSegments, featureId);
        }

        double[] packed = new double[(int) required * 4];
        int segmentCount = 0;
        for (Orientation orientation : orientations) {
            for (long index = orientation.firstIndex(); index <= orientation.lastIndex(); index++) {
                double offset = finite(index * spacingPixels, "hatch-lattice-offset");
                double lineX =
                        finite(latticeOrigin.x() + orientation.normalX() * offset, "hatch-line-x");
                double lineY =
                        finite(latticeOrigin.y() + orientation.normalY() * offset, "hatch-line-y");
                segmentCount =
                        clipInto(
                                bounds,
                                lineX,
                                lineY,
                                orientation.directionX(),
                                orientation.directionY(),
                                packed,
                                segmentCount);
                if (index == Long.MAX_VALUE) {
                    break;
                }
            }
        }
        return new HatchSegments(packed, segmentCount);
    }

    private static Orientation orientation(
            Envelope bounds, Coordinate origin, double bearing, double spacing) {
        double radians = StrictMath.toRadians(bearing % 360.0);
        double directionX = finite(StrictMath.cos(radians), "hatch-direction-x");
        double directionY = finite(StrictMath.sin(radians), "hatch-direction-y");
        double normalX = -directionY;
        double normalY = directionX;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double[] xs = {bounds.minX(), bounds.maxX()};
        double[] ys = {bounds.minY(), bounds.maxY()};
        for (double x : xs) {
            for (double y : ys) {
                double projection =
                        finite(
                                finite((x - origin.x()) * normalX, "hatch-projection-x")
                                        + finite((y - origin.y()) * normalY, "hatch-projection-y"),
                                "hatch-projection");
                minimum = StrictMath.min(minimum, projection);
                maximum = StrictMath.max(maximum, projection);
            }
        }
        double firstDouble = StrictMath.ceil(minimum / spacing);
        double lastDouble = StrictMath.floor(maximum / spacing);
        if (!Double.isFinite(firstDouble)
                || !Double.isFinite(lastDouble)
                || firstDouble < -0x1.0p63
                || firstDouble >= 0x1.0p63
                || lastDouble < -0x1.0p63
                || lastDouble >= 0x1.0p63) {
            return new Orientation(directionX, directionY, normalX, normalY, 0L, -1L, true);
        }
        long first = (long) firstDouble;
        long last = (long) lastDouble;
        if (last < first) {
            return new Orientation(directionX, directionY, normalX, normalY, 0L, -1L, false);
        }
        try {
            Math.addExact(Math.subtractExact(last, first), 1L);
        } catch (ArithmeticException overflow) {
            return new Orientation(directionX, directionY, normalX, normalY, first, last, true);
        }
        return new Orientation(directionX, directionY, normalX, normalY, first, last, false);
    }

    private static int clipInto(
            Envelope bounds,
            double lineX,
            double lineY,
            double directionX,
            double directionY,
            double[] packed,
            int segmentCount) {
        double minimumT = Double.NEGATIVE_INFINITY;
        double maximumT = Double.POSITIVE_INFINITY;
        if (directionX == 0.0) {
            if (lineX < bounds.minX() || lineX > bounds.maxX()) {
                return segmentCount;
            }
        } else {
            double first = (bounds.minX() - lineX) / directionX;
            double second = (bounds.maxX() - lineX) / directionX;
            minimumT = StrictMath.max(minimumT, StrictMath.min(first, second));
            maximumT = StrictMath.min(maximumT, StrictMath.max(first, second));
        }
        if (directionY == 0.0) {
            if (lineY < bounds.minY() || lineY > bounds.maxY()) {
                return segmentCount;
            }
        } else {
            double first = (bounds.minY() - lineY) / directionY;
            double second = (bounds.maxY() - lineY) / directionY;
            minimumT = StrictMath.max(minimumT, StrictMath.min(first, second));
            maximumT = StrictMath.min(maximumT, StrictMath.max(first, second));
        }
        if (!(minimumT < maximumT)) {
            return segmentCount;
        }
        int target = segmentCount * 4;
        packed[target] =
                clamp(
                        finite(lineX + directionX * minimumT, "hatch-segment-x1"),
                        bounds.minX(),
                        bounds.maxX());
        packed[target + 1] =
                clamp(
                        finite(lineY + directionY * minimumT, "hatch-segment-y1"),
                        bounds.minY(),
                        bounds.maxY());
        packed[target + 2] =
                clamp(
                        finite(lineX + directionX * maximumT, "hatch-segment-x2"),
                        bounds.minX(),
                        bounds.maxX());
        packed[target + 3] =
                clamp(
                        finite(lineY + directionY * maximumT, "hatch-segment-y2"),
                        bounds.minY(),
                        bounds.maxY());
        return segmentCount + 1;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return StrictMath.max(minimum, StrictMath.min(maximum, value));
    }

    private static SymbolException limit(
            HatchPattern pattern, String required, int maximum, String featureId) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("featureId", featureId);
        context.put("pattern", pattern.name());
        context.put("requiredSegments", required);
        context.put("maxSegments", Integer.toString(maximum));
        context.put("countKind", "candidate");
        return new SymbolException(
                SymbolException.HATCH_SEGMENT_LIMIT_EXCEEDED,
                "Hatch candidate segment limit exceeded",
                context);
    }

    private static double finite(double value, String quantity) {
        if (!Double.isFinite(value)) {
            throw new SymbolException(
                    SymbolException.TRANSFORM_NON_FINITE,
                    "Hatch layout produced a non-finite value",
                    java.util.Map.of("quantity", quantity));
        }
        return value == 0.0 ? 0.0 : value;
    }

    private record Orientation(
            double directionX,
            double directionY,
            double normalX,
            double normalY,
            long firstIndex,
            long lastIndex,
            boolean countOverflow) {
        long candidateCount() {
            if (lastIndex < firstIndex) {
                return 0L;
            }
            return lastIndex - firstIndex + 1L;
        }
    }
}
