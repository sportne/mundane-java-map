package io.github.mundanej.map.core;

/**
 * Prospective work and output limits for bounded geometry topology operations.
 *
 * @param maxCoordinates maximum input positions visited
 * @param maxSegmentComparisons maximum pairwise segment or position comparisons
 * @param maxOutputCoordinates maximum positions retained in a result
 */
public record GeometryTopologyLimits(
        int maxCoordinates, long maxSegmentComparisons, int maxOutputCoordinates) {
    /** Conservative defaults for interactive rendering, querying, and editing. */
    public static final GeometryTopologyLimits DEFAULT =
            new GeometryTopologyLimits(1_000_000, 4_000_000L, 2_000_000);

    /** Validates positive limits. */
    public GeometryTopologyLimits {
        if (maxCoordinates < 1 || maxSegmentComparisons < 1 || maxOutputCoordinates < 1) {
            throw new IllegalArgumentException("Geometry topology limits must be positive");
        }
    }
}
