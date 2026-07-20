package io.github.mundanej.map.api;

/**
 * Immutable ceilings for one linear snap query.
 *
 * @param maximumLayers maximum declared reference layers
 * @param maximumFeatures maximum non-excluded reference features
 * @param maximumCoordinates maximum stored coordinates traversed
 * @param maximumSegments maximum non-zero candidate segments traversed
 */
public record SnapLimits(
        int maximumLayers, int maximumFeatures, long maximumCoordinates, long maximumSegments) {
    /** Default bounded same-CRS snapping profile. */
    public static final SnapLimits DEFAULT = new SnapLimits(256, 100_000, 1_000_000, 1_000_000);

    /** Validates positive ceilings. */
    public SnapLimits {
        if (maximumLayers <= 0
                || maximumFeatures <= 0
                || maximumCoordinates <= 0
                || maximumSegments <= 0) {
            throw new IllegalArgumentException("snap limits must be positive");
        }
    }

    /**
     * Returns a copy with a new layer ceiling.
     *
     * @param maximum positive layer ceiling
     * @return immutable updated limits
     */
    public SnapLimits withMaximumLayers(int maximum) {
        return new SnapLimits(maximum, maximumFeatures, maximumCoordinates, maximumSegments);
    }

    /**
     * Returns a copy with a new feature ceiling.
     *
     * @param maximum positive feature ceiling
     * @return immutable updated limits
     */
    public SnapLimits withMaximumFeatures(int maximum) {
        return new SnapLimits(maximumLayers, maximum, maximumCoordinates, maximumSegments);
    }

    /**
     * Returns a copy with a new coordinate ceiling.
     *
     * @param maximum positive coordinate ceiling
     * @return immutable updated limits
     */
    public SnapLimits withMaximumCoordinates(long maximum) {
        return new SnapLimits(maximumLayers, maximumFeatures, maximum, maximumSegments);
    }

    /**
     * Returns a copy with a new segment ceiling.
     *
     * @param maximum positive segment ceiling
     * @return immutable updated limits
     */
    public SnapLimits withMaximumSegments(long maximum) {
        return new SnapLimits(maximumLayers, maximumFeatures, maximumCoordinates, maximum);
    }
}
