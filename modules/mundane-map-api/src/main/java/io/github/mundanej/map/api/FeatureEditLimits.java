package io.github.mundanej.map.api;

/**
 * Immutable feature-edit session ceilings.
 *
 * @param maximumFeatures maximum records in a current snapshot
 * @param maximumCommandsPerTransaction maximum commands in one atomic transaction
 * @param maximumSnapshotBytes maximum deterministic logical current-snapshot payload
 */
public record FeatureEditLimits(
        int maximumFeatures, int maximumCommandsPerTransaction, long maximumSnapshotBytes) {
    /** Default bounded editing profile. */
    public static final FeatureEditLimits DEFAULT =
            new FeatureEditLimits(100_000, 10_000, 268_435_456);

    /** Validates positive ceilings. */
    public FeatureEditLimits {
        if (maximumFeatures <= 0
                || maximumCommandsPerTransaction <= 0
                || maximumSnapshotBytes <= 0) {
            throw new IllegalArgumentException("feature-edit limits must be positive");
        }
    }

    /**
     * Returns a copy with the feature ceiling replaced.
     *
     * @param maximum positive feature ceiling
     * @return immutable updated limits
     */
    public FeatureEditLimits withMaximumFeatures(int maximum) {
        return new FeatureEditLimits(maximum, maximumCommandsPerTransaction, maximumSnapshotBytes);
    }

    /**
     * Returns a copy with the command ceiling replaced.
     *
     * @param maximum positive per-transaction command ceiling
     * @return immutable updated limits
     */
    public FeatureEditLimits withMaximumCommandsPerTransaction(int maximum) {
        return new FeatureEditLimits(maximumFeatures, maximum, maximumSnapshotBytes);
    }

    /**
     * Returns a copy with the logical snapshot-byte ceiling replaced.
     *
     * @param maximum positive logical byte ceiling
     * @return immutable updated limits
     */
    public FeatureEditLimits withMaximumSnapshotBytes(long maximum) {
        return new FeatureEditLimits(maximumFeatures, maximumCommandsPerTransaction, maximum);
    }
}
