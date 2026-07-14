package io.github.mundanej.map.api;

/** Immutable feature query ceilings. */
public record FeatureQueryLimits(
        long recordsExamined,
        long recordsReturned,
        long coordinatesReturned,
        long attributeValuesReturned,
        long decodedTextCharactersReturned,
        long ownedPayloadBytes,
        int retainedWarnings) {
    /** Level 1 defaults. */
    public static final FeatureQueryLimits LEVEL_1 =
            new FeatureQueryLimits(
                    1_000_000, 100_000, 10_000_000, 1_000_000, 16_777_216, 268_435_456, 256);

    /** Validates positive ceilings. */
    public FeatureQueryLimits {
        if (recordsExamined <= 0
                || recordsReturned <= 0
                || coordinatesReturned <= 0
                || attributeValuesReturned <= 0
                || decodedTextCharactersReturned <= 0
                || ownedPayloadBytes <= 0
                || retainedWarnings <= 0) {
            throw new IllegalArgumentException("Feature query limits must be positive");
        }
    }

    /** Returns whether every ceiling is no greater than the parent. */
    public boolean tightens(FeatureQueryLimits parent) {
        return recordsExamined <= parent.recordsExamined
                && recordsReturned <= parent.recordsReturned
                && coordinatesReturned <= parent.coordinatesReturned
                && attributeValuesReturned <= parent.attributeValuesReturned
                && decodedTextCharactersReturned <= parent.decodedTextCharactersReturned
                && ownedPayloadBytes <= parent.ownedPayloadBytes
                && retainedWarnings <= parent.retainedWarnings;
    }
}
