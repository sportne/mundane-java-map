package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable bounded profile for one annotation-only singular-point label.
 *
 * <p>A label is normally omitted, without a diagnostic, when its resolution is excluded, text is
 * missing or blank, its marker role is omitted, every candidate is clipped, or every candidate
 * collides. Labels are painted in the global annotation pass and do not participate in geometry or
 * symbol interaction.
 *
 * @param textSource closed exact label-text source
 * @param style immutable visible text style
 * @param positions ordered unique compass-position preferences
 * @param gapPixels separation from anchor bounds from zero through 32768 pixels
 * @param offsetXPixels horizontal anchor translation from -32768 through 32768 pixels
 * @param offsetYPixels vertical anchor translation from -32768 through 32768 pixels
 * @param collisionPaddingPixels collision-box expansion on every side
 * @param priority explicit collision-admission priority
 * @param visibleResolution inclusive map-resolution range
 * @param anchorBasis marker bounds or projected feature point
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
        ResolutionRange visibleResolution,
        PointLabelAnchorBasis anchorBasis) {
    /** Validates bounds and defensively copies ordered unique positions. */
    public PointLabelProfile {
        Objects.requireNonNull(textSource, "textSource");
        Objects.requireNonNull(style, "style");
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        Objects.requireNonNull(visibleResolution, "visibleResolution");
        Objects.requireNonNull(anchorBasis, "anchorBasis");
        if (positions.isEmpty() || positions.size() > PointLabelPosition.values().length) {
            throw new IllegalArgumentException("positions must contain between 1 and 9 entries");
        }
        if (positions.stream().anyMatch(Objects::isNull)
                || new HashSet<>(positions).size() != positions.size()) {
            throw new IllegalArgumentException("positions must be non-null and unique");
        }
        requireRange(gapPixels, 0.0, 32_768.0, "gapPixels");
        requireRange(offsetXPixels, -32_768.0, 32_768.0, "offsetXPixels");
        requireRange(offsetYPixels, -32_768.0, 32_768.0, "offsetYPixels");
        requireRange(collisionPaddingPixels, 0.0, 64.0, "collisionPaddingPixels");
    }

    /**
     * Creates a marker-bounds profile compatible with the original G11 contract.
     *
     * @param textSource exact label text source
     * @param style immutable text style
     * @param positions ordered unique candidate positions
     * @param gapPixels non-negative marker gap
     * @param offsetXPixels horizontal screen offset
     * @param offsetYPixels vertical screen offset
     * @param collisionPaddingPixels non-negative collision padding
     * @param priority collision admission priority
     * @param visibleResolution visible resolution interval
     */
    public PointLabelProfile(
            LabelTextSource textSource,
            LabelTextStyle style,
            List<PointLabelPosition> positions,
            double gapPixels,
            double offsetXPixels,
            double offsetYPixels,
            double collisionPaddingPixels,
            int priority,
            ResolutionRange visibleResolution) {
        this(
                textSource,
                style,
                positions,
                gapPixels,
                offsetXPixels,
                offsetYPixels,
                collisionPaddingPixels,
                priority,
                visibleResolution,
                PointLabelAnchorBasis.MARKER_BOUNDS);
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
                ResolutionRange.ALL,
                PointLabelAnchorBasis.MARKER_BOUNDS);
    }

    private static void requireRange(double value, double minimum, double maximum, String field) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
