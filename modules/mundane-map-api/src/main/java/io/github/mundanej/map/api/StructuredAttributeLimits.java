package io.github.mundanej.map.api;

/**
 * Safety limits for structured attribute trees.
 *
 * @param maxDepth maximum array/object nesting depth
 * @param maxValues maximum scalar, array, and object nodes
 * @param maxObjectMembers maximum members in any one object
 * @param maxArrayElements maximum elements in any one array
 */
public record StructuredAttributeLimits(
        int maxDepth, int maxValues, int maxObjectMembers, int maxArrayElements) {
    /** Conservative defaults for untrusted adapter input. */
    public static final StructuredAttributeLimits DEFAULT =
            new StructuredAttributeLimits(32, 10_000, 1_000, 10_000);

    /** Creates positive limits with a nonnegative depth. */
    public StructuredAttributeLimits {
        if (maxDepth < 0 || maxValues <= 0 || maxObjectMembers <= 0 || maxArrayElements <= 0) {
            throw new IllegalArgumentException(
                    "Structured attribute limits must be positive and depth nonnegative");
        }
    }
}
