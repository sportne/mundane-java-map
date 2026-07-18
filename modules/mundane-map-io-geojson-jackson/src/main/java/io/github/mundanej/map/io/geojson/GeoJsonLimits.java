package io.github.mundanej.map.io.geojson;

/**
 * Immutable open-time GeoJSON ceilings.
 *
 * @param maximumInputBytes encoded input-byte ceiling
 * @param maximumNestingDepth JSON nesting ceiling, with the root at one
 * @param maximumTokens JSON token ceiling
 * @param maximumObjectMembers object-member ceiling
 * @param maximumPhysicalFeatures physical Feature ceiling
 * @param maximumTotalPositions total coordinate-position ceiling
 * @param maximumPositionsPerGeometry per-geometry position ceiling
 * @param maximumParts total multipart component ceiling
 * @param maximumPropertiesPerFeature per-Feature property ceiling
 * @param maximumTotalProperties total property-value ceiling
 * @param maximumMemberNameCharacters member-name character ceiling
 * @param maximumScalarCharacters scalar-string character ceiling
 * @param maximumAggregateCharacters aggregate decoded-string character ceiling
 * @param maximumNumberCharacters number-token character ceiling
 * @param maximumOwnedBytes conservative operation-owned byte ceiling
 * @param retainedWarnings retained opening-warning ceiling
 */
public record GeoJsonLimits(
        int maximumInputBytes,
        int maximumNestingDepth,
        long maximumTokens,
        int maximumObjectMembers,
        int maximumPhysicalFeatures,
        int maximumTotalPositions,
        int maximumPositionsPerGeometry,
        int maximumParts,
        int maximumPropertiesPerFeature,
        int maximumTotalProperties,
        int maximumMemberNameCharacters,
        int maximumScalarCharacters,
        int maximumAggregateCharacters,
        int maximumNumberCharacters,
        long maximumOwnedBytes,
        int retainedWarnings) {
    private static final GeoJsonLimits DEFAULTS =
            new GeoJsonLimits(
                    16_777_216,
                    64,
                    16_000_000,
                    2_000_000,
                    100_000,
                    2_000_000,
                    1_000_000,
                    250_000,
                    256,
                    1_000_000,
                    256,
                    65_536,
                    16_777_216,
                    128,
                    268_435_456,
                    256);

    /** Validates every ceiling and the cross-field invariants. */
    public GeoJsonLimits {
        requireRange(maximumInputBytes, 1, 268_435_456, "maximumInputBytes");
        requireRange(maximumNestingDepth, 1, 128, "maximumNestingDepth");
        requireRange(maximumTokens, 1, 134_217_728L, "maximumTokens");
        requireRange(maximumObjectMembers, 1, 16_000_000, "maximumObjectMembers");
        requireRange(maximumPhysicalFeatures, 1, 1_000_000, "maximumPhysicalFeatures");
        requireRange(maximumTotalPositions, 1, 16_000_000, "maximumTotalPositions");
        requireRange(maximumPositionsPerGeometry, 1, 16_000_000, "maximumPositionsPerGeometry");
        requireRange(maximumParts, 1, 2_000_000, "maximumParts");
        requireRange(maximumPropertiesPerFeature, 1, 4_096, "maximumPropertiesPerFeature");
        requireRange(maximumTotalProperties, 1, 8_000_000, "maximumTotalProperties");
        requireRange(maximumMemberNameCharacters, 1, 256, "maximumMemberNameCharacters");
        requireRange(maximumScalarCharacters, 1, 1_048_576, "maximumScalarCharacters");
        requireRange(maximumAggregateCharacters, 1, 134_217_728, "maximumAggregateCharacters");
        requireRange(maximumNumberCharacters, 1, 256, "maximumNumberCharacters");
        requireRange(maximumOwnedBytes, 1, 1_073_741_824L, "maximumOwnedBytes");
        requireRange(retainedWarnings, 1, 4_096, "retainedWarnings");
        if (maximumPositionsPerGeometry > maximumTotalPositions
                || maximumPropertiesPerFeature > maximumTotalProperties
                || maximumScalarCharacters > maximumAggregateCharacters) {
            throw new IllegalArgumentException("GeoJSON per-value limits must fit total limits");
        }
        long minimumOwned =
                Math.addExact(
                        maximumInputBytes,
                        Math.addExact(
                                Math.multiplyExact(16L, maximumTotalPositions),
                                Math.multiplyExact(2L, maximumAggregateCharacters)));
        if (maximumOwnedBytes < minimumOwned) {
            throw new IllegalArgumentException("maximumOwnedBytes cannot cover configured input");
        }
    }

    /**
     * Returns the supported default ceilings.
     *
     * @return immutable defaults
     */
    public static GeoJsonLimits defaults() {
        return DEFAULTS;
    }

    private static void requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its supported range");
        }
    }
}
