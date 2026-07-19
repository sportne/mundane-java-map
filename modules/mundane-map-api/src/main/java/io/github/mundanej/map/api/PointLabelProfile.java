package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable bounded profile for one annotation-only singular-point label.
 *
 * @param textSource exact feature-name or text-attribute source
 * @param style immutable visible text style
 * @param positions ordered unique compass-position preferences
 * @param gapPixels separation from marker bounds on each positioned axis
 * @param offsetXPixels horizontal marker-anchor translation
 * @param offsetYPixels vertical marker-anchor translation
 * @param collisionPaddingPixels collision-box expansion on every side
 * @param priority explicit collision-admission priority
 * @param visibleResolution inclusive map-resolution range
 */
public record PointLabelProfile(
        LabelTextSource textSource,
        LabelTextStyle style,
        List<PointLabelPosition> positions,
        double gapPixels,
        double offsetXPixels,
        double offsetYPixels,
        double collisionPaddingPixels,
        int priority,
        ResolutionRange visibleResolution) {
    /** Validates bounds and defensively copies ordered unique positions. */
    public PointLabelProfile {
        Objects.requireNonNull(textSource, "textSource");
        Objects.requireNonNull(style, "style");
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        Objects.requireNonNull(visibleResolution, "visibleResolution");
        if (positions.isEmpty() || positions.size() > PointLabelPosition.values().length) {
            throw new IllegalArgumentException("positions must contain between 1 and 8 entries");
        }
        if (positions.stream().anyMatch(Objects::isNull)
                || new HashSet<>(positions).size() != positions.size()) {
            throw new IllegalArgumentException("positions must be non-null and unique");
        }
        requireRange(gapPixels, 0.0, 64.0, "gapPixels");
        requireRange(offsetXPixels, -256.0, 256.0, "offsetXPixels");
        requireRange(offsetYPixels, -256.0, 256.0, "offsetYPixels");
        requireRange(collisionPaddingPixels, 0.0, 64.0, "collisionPaddingPixels");
    }

    /**
     * Returns the internal compatibility profile used by symbol-based binding factories.
     *
     * @return immutable name-based north-east profile
     */
    public static PointLabelProfile compatibility() {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                new LabelTextStyle(Rgba.rgb(32, 32, 32), LabelWeight.NORMAL, 12.0),
                List.of(PointLabelPosition.NE),
                4.0,
                0.0,
                0.0,
                1.0,
                0,
                ResolutionRange.ALL);
    }

    private static void requireRange(double value, double minimum, double maximum, String field) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
