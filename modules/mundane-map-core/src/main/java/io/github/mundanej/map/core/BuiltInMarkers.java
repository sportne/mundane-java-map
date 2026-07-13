package io.github.mundanej.map.core;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Normalized immutable paths and convenience factories for the built-in marker set. */
public final class BuiltInMarkers {
    private static final double HALF = 0.5;
    private static final double ZERO_TOLERANCE = 1.0e-15;
    private static final Envelope VIEW_BOX = new Envelope(-HALF, -HALF, HALF, HALF);
    private static final Map<BuiltInMarker, VectorPath> PATHS = createPaths();

    private BuiltInMarkers() {}

    /** Returns the common normalized marker view box. */
    public static Envelope viewBox() {
        return VIEW_BOX;
    }

    /** Returns the reusable immutable path for a built-in marker. */
    public static VectorPath path(BuiltInMarker marker) {
        return PATHS.get(Objects.requireNonNull(marker, "marker"));
    }

    /** Creates a filled centered screen marker from a built-in path. */
    public static VectorMarkerSymbol filledScreen(
            BuiltInMarker marker, Rgba fill, double sizePixels, double opacity) {
        return VectorMarkerSymbol.filledScreen(path(marker), VIEW_BOX, fill, sizePixels, opacity);
    }

    private static Map<BuiltInMarker, VectorPath> createPaths() {
        EnumMap<BuiltInMarker, VectorPath> paths = new EnumMap<>(BuiltInMarker.class);
        paths.put(BuiltInMarker.CIRCLE, circle());
        paths.put(
                BuiltInMarker.SQUARE, polygon(-HALF, -HALF, HALF, -HALF, HALF, HALF, -HALF, HALF));
        paths.put(BuiltInMarker.TRIANGLE, polygon(0.0, -HALF, HALF, HALF, -HALF, HALF));
        paths.put(BuiltInMarker.DIAMOND, polygon(0.0, -HALF, HALF, 0.0, 0.0, HALF, -HALF, 0.0));
        double arm = 1.0 / 6.0;
        double[] cross = {
            -arm, -HALF,
            arm, -HALF,
            arm, -arm,
            HALF, -arm,
            HALF, arm,
            arm, arm,
            arm, HALF,
            -arm, HALF,
            -arm, arm,
            -HALF, arm,
            -HALF, -arm,
            -arm, -arm
        };
        paths.put(BuiltInMarker.CROSS, polygon(cross));
        paths.put(BuiltInMarker.X, polygon(diagonalCross(cross)));
        paths.put(BuiltInMarker.STAR, star());
        paths.put(
                BuiltInMarker.ARROW,
                polygon(
                        0.1, -0.4, HALF, 0.0, 0.1, 0.4, 0.1, 0.15, -HALF, 0.15, -HALF, -0.15, 0.1,
                        -0.15));
        return Map.copyOf(paths);
    }

    private static VectorPath circle() {
        double control = HALF * 4.0 * (StrictMath.sqrt(2.0) - 1.0) / 3.0;
        return VectorPath.builder()
                .moveTo(0.0, -HALF)
                .cubicTo(control, -HALF, HALF, -control, HALF, 0.0)
                .cubicTo(HALF, control, control, HALF, 0.0, HALF)
                .cubicTo(-control, HALF, -HALF, control, -HALF, 0.0)
                .cubicTo(-HALF, -control, -control, -HALF, 0.0, -HALF)
                .close()
                .build();
    }

    private static VectorPath star() {
        double[] coordinates = new double[20];
        for (int index = 0; index < 10; index++) {
            double radius = index % 2 == 0 ? HALF : 0.2;
            double angle = StrictMath.toRadians(-90.0 + 36.0 * index);
            coordinates[index * 2] = canonicalZero(radius * StrictMath.cos(angle));
            coordinates[index * 2 + 1] = canonicalZero(radius * StrictMath.sin(angle));
        }
        return polygon(coordinates);
    }

    private static double[] diagonalCross(double[] cross) {
        double rootTwo = StrictMath.sqrt(2.0);
        double scale = 3.0 / (2.0 * rootTwo);
        double[] transformed = new double[cross.length];
        for (int index = 0; index < cross.length; index += 2) {
            double x = cross[index];
            double y = cross[index + 1];
            transformed[index] = canonicalZero(scale * (x - y) / rootTwo);
            transformed[index + 1] = canonicalZero(scale * (x + y) / rootTwo);
        }
        return rotateToNorthWest(transformed);
    }

    private static double[] rotateToNorthWest(double[] coordinates) {
        int first = 0;
        for (int index = 2; index < coordinates.length; index += 2) {
            if (coordinates[index + 1] < coordinates[first + 1]
                    || (Double.compare(coordinates[index + 1], coordinates[first + 1]) == 0
                            && coordinates[index] < coordinates[first])) {
                first = index;
            }
        }
        double[] rotated = new double[coordinates.length];
        for (int index = 0; index < coordinates.length; index += 2) {
            int source = (first + index) % coordinates.length;
            rotated[index] = coordinates[source];
            rotated[index + 1] = coordinates[source + 1];
        }
        return rotated;
    }

    private static VectorPath polygon(double... coordinates) {
        if (coordinates.length < 6 || coordinates.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "polygon coordinates must contain complete vertices");
        }
        VectorPath.Builder builder = VectorPath.builder().moveTo(coordinates[0], coordinates[1]);
        for (int index = 2; index < coordinates.length; index += 2) {
            builder.lineTo(coordinates[index], coordinates[index + 1]);
        }
        return builder.close().build();
    }

    private static double canonicalZero(double value) {
        return StrictMath.abs(value) <= ZERO_TOLERANCE ? 0.0 : value;
    }
}
