package io.github.mundanej.map.core;

/**
 * Immutable value ceilings for one operation-local screen-geometry optimization.
 *
 * @param maximumOutputCoordinates maximum coordinates owned by a rendering result
 * @param maximumBuildBytes maximum cumulative logical primitive build bytes
 * @param maximumTopologyComparisons maximum polygon segment/topology comparisons
 */
public record ScreenGeometryOptimizationLimits(
        int maximumOutputCoordinates, long maximumBuildBytes, long maximumTopologyComparisons) {
    /** Fixed Level 1 defaults. */
    public static final ScreenGeometryOptimizationLimits LEVEL_1 =
            new ScreenGeometryOptimizationLimits(2_000_000, 134_217_728, 1_000_000);

    /**
     * Validates positive ceilings.
     *
     * @throws IllegalArgumentException when any ceiling is zero or negative
     */
    public ScreenGeometryOptimizationLimits {
        if (maximumOutputCoordinates <= 0
                || maximumBuildBytes <= 0
                || maximumTopologyComparisons <= 0) {
            throw new IllegalArgumentException("Screen geometry limits must be positive");
        }
    }

    /** Returns the fixed Level 1 defaults. */
    public static ScreenGeometryOptimizationLimits defaults() {
        return LEVEL_1;
    }

    /** Returns a value with the coordinate ceiling replaced. */
    public ScreenGeometryOptimizationLimits withMaximumOutputCoordinates(int value) {
        return new ScreenGeometryOptimizationLimits(
                value, maximumBuildBytes, maximumTopologyComparisons);
    }

    /** Returns a value with the build-byte ceiling replaced. */
    public ScreenGeometryOptimizationLimits withMaximumBuildBytes(long value) {
        return new ScreenGeometryOptimizationLimits(
                maximumOutputCoordinates, value, maximumTopologyComparisons);
    }

    /** Returns a value with the topology-comparison ceiling replaced. */
    public ScreenGeometryOptimizationLimits withMaximumTopologyComparisons(long value) {
        return new ScreenGeometryOptimizationLimits(
                maximumOutputCoordinates, maximumBuildBytes, value);
    }
}
