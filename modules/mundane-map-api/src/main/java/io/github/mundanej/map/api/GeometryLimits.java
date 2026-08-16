package io.github.mundanej.map.api;

/**
 * Explicit construction and traversal limits for untrusted geometry values.
 *
 * @param maxCoordinates maximum positions across one value tree
 * @param maxParts maximum primitive parts and rings across one value tree
 * @param maxCollectionElements maximum collection members across one value tree
 * @param maxDepth maximum collection nesting depth, where a primitive has depth zero
 */
public record GeometryLimits(
        long maxCoordinates, long maxParts, long maxCollectionElements, int maxDepth) {
    /** Conservative defaults suitable for ordinary adapter input. */
    public static final GeometryLimits DEFAULT =
            new GeometryLimits(10_000_000L, 1_000_000L, 100_000L, 64);

    /** Creates validated positive limits. */
    public GeometryLimits {
        if (maxCoordinates <= 0 || maxParts <= 0 || maxCollectionElements <= 0 || maxDepth < 0) {
            throw new IllegalArgumentException(
                    "Geometry limits must be positive and depth must be non-negative");
        }
    }
}
