package io.github.mundanej.map.api;

/**
 * Immutable ceilings for undo and redo history retained by one feature-edit session.
 *
 * @param maximumEntries maximum combined undo and redo entries
 * @param maximumBytes maximum deterministic logical history payload
 */
public record FeatureEditHistoryLimits(int maximumEntries, long maximumBytes) {
    /** Default bounded editing-history profile. */
    public static final FeatureEditHistoryLimits DEFAULT =
            new FeatureEditHistoryLimits(256, 67_108_864);

    /** Validates positive ceilings. */
    public FeatureEditHistoryLimits {
        if (maximumEntries <= 0 || maximumBytes <= 0) {
            throw new IllegalArgumentException("feature-edit history limits must be positive");
        }
    }

    /**
     * Returns a copy with the combined entry ceiling replaced.
     *
     * @param maximum positive entry ceiling
     * @return immutable updated limits
     */
    public FeatureEditHistoryLimits withMaximumEntries(int maximum) {
        return new FeatureEditHistoryLimits(maximum, maximumBytes);
    }

    /**
     * Returns a copy with the logical byte ceiling replaced.
     *
     * @param maximum positive logical byte ceiling
     * @return immutable updated limits
     */
    public FeatureEditHistoryLimits withMaximumBytes(long maximum) {
        return new FeatureEditHistoryLimits(maximumEntries, maximum);
    }
}
