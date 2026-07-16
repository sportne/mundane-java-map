package io.github.mundanej.map.core;

/**
 * Immutable value-semantics ceilings for one explicitly indexed in-memory feature source. Equality,
 * hashing, and text representation include all four components.
 *
 * @param maximumRecords maximum records admitted before snapshot construction
 * @param maximumRetainedBytes maximum bytes retained by the primitive index arrays
 * @param maximumBuildBytes maximum cumulative retained and temporary build-array bytes
 * @param maximumQueryBytes maximum bytes allocated by one cursor-owned query plan
 */
public record FeatureIndexLimits(
        int maximumRecords,
        long maximumRetainedBytes,
        long maximumBuildBytes,
        long maximumQueryBytes) {
    /** Level 1 defaults for explicitly selected packed indexing. */
    public static final FeatureIndexLimits LEVEL_1 =
            new FeatureIndexLimits(1_000_000, 16_777_216, 33_554_432, 1_048_576);

    /**
     * Validates that every ceiling is positive.
     *
     * @throws IllegalArgumentException when any component is zero or negative
     */
    public FeatureIndexLimits {
        if (maximumRecords <= 0
                || maximumRetainedBytes <= 0
                || maximumBuildBytes <= 0
                || maximumQueryBytes <= 0) {
            throw new IllegalArgumentException("Feature index limits must be positive");
        }
    }

    /**
     * Returns the Level 1 defaults.
     *
     * @return the shared immutable Level 1 value
     */
    public static FeatureIndexLimits defaults() {
        return LEVEL_1;
    }

    /**
     * Returns a copy with the record ceiling changed.
     *
     * @param value positive replacement record ceiling
     * @return the changed immutable value
     * @throws IllegalArgumentException when {@code value} is zero or negative
     */
    public FeatureIndexLimits withMaximumRecords(int value) {
        return new FeatureIndexLimits(
                value, maximumRetainedBytes, maximumBuildBytes, maximumQueryBytes);
    }

    /**
     * Returns a copy with the retained primitive-byte ceiling changed.
     *
     * @param value positive replacement retained-byte ceiling
     * @return the changed immutable value
     * @throws IllegalArgumentException when {@code value} is zero or negative
     */
    public FeatureIndexLimits withMaximumRetainedBytes(long value) {
        return new FeatureIndexLimits(maximumRecords, value, maximumBuildBytes, maximumQueryBytes);
    }

    /**
     * Returns a copy with the cumulative construction-byte ceiling changed.
     *
     * @param value positive replacement build-byte ceiling
     * @return the changed immutable value
     * @throws IllegalArgumentException when {@code value} is zero or negative
     */
    public FeatureIndexLimits withMaximumBuildBytes(long value) {
        return new FeatureIndexLimits(
                maximumRecords, maximumRetainedBytes, value, maximumQueryBytes);
    }

    /**
     * Returns a copy with the cursor-owned query-plan byte ceiling changed.
     *
     * @param value positive replacement query-plan-byte ceiling
     * @return the changed immutable value
     * @throws IllegalArgumentException when {@code value} is zero or negative
     */
    public FeatureIndexLimits withMaximumQueryBytes(long value) {
        return new FeatureIndexLimits(
                maximumRecords, maximumRetainedBytes, maximumBuildBytes, value);
    }
}
