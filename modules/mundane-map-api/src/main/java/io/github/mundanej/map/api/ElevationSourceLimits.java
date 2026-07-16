package io.github.mundanej.map.api;

/**
 * Immutable ceilings captured by an elevation source.
 *
 * @param maximumColumns maximum accepted grid-column count
 * @param maximumRows maximum accepted grid-row count
 * @param maximumSamples maximum accepted sample count
 * @param maximumRetainedSampleBytes maximum logical bytes retained for samples and their no-data
 *     mask
 * @param maximumRetainedWarnings maximum warning entries retained in the opening report
 */
public record ElevationSourceLimits(
        int maximumColumns,
        int maximumRows,
        long maximumSamples,
        long maximumRetainedSampleBytes,
        int maximumRetainedWarnings) {
    /** Default eager-grid ceilings. */
    public static final ElevationSourceLimits DEFAULTS =
            new ElevationSourceLimits(4_096, 4_096, 16_777_216, 136_314_880, 256);

    /** Validates that every ceiling is positive. */
    public ElevationSourceLimits {
        if (maximumColumns <= 0
                || maximumRows <= 0
                || maximumSamples <= 0
                || maximumRetainedSampleBytes <= 0
                || maximumRetainedWarnings <= 0) {
            throw new IllegalArgumentException("Elevation source limits must be positive");
        }
    }
}
