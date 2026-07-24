package io.github.mundanej.map.io.maplibre.style;

/**
 * Immutable ceilings for one MapLibre style read.
 *
 * @param maximumInputBytes encoded byte ceiling
 * @param maximumNestingDepth JSON nesting ceiling
 * @param maximumTokens JSON token ceiling
 * @param maximumStringCharacters one decoded string ceiling
 * @param maximumAggregateCharacters aggregate decoded string ceiling
 * @param maximumObjectMembers aggregate member ceiling
 * @param maximumSources source ceiling
 * @param maximumLayers layer ceiling
 * @param maximumMetadataEntries metadata-entry ceiling
 * @param maximumExpressionNodes future expression-node ceiling
 * @param maximumExpressionDepth future expression-depth ceiling
 * @param maximumStops future interpolation-stop ceiling
 * @param maximumCategories future category ceiling
 * @param maximumCatalogReferences future catalog-reference ceiling
 * @param maximumProducedRules future produced-rule ceiling
 * @param maximumOwnedBytes conservative reader-owned allocation ceiling
 */
public record MapLibreReadLimits(
        int maximumInputBytes,
        int maximumNestingDepth,
        long maximumTokens,
        int maximumStringCharacters,
        int maximumAggregateCharacters,
        int maximumObjectMembers,
        int maximumSources,
        int maximumLayers,
        int maximumMetadataEntries,
        int maximumExpressionNodes,
        int maximumExpressionDepth,
        int maximumStops,
        int maximumCategories,
        int maximumCatalogReferences,
        int maximumProducedRules,
        long maximumOwnedBytes) {
    private static final MapLibreReadLimits DEFAULTS =
            new MapLibreReadLimits(
                    4_194_304,
                    64,
                    500_000,
                    65_536,
                    2_097_152,
                    100_000,
                    256,
                    1_024,
                    256,
                    8_192,
                    32,
                    64,
                    256,
                    1_024,
                    4_096,
                    33_554_432);

    /** Validates the supported range and aggregate invariants. */
    public MapLibreReadLimits {
        range(maximumInputBytes, 1, 67_108_864, "maximumInputBytes");
        range(maximumNestingDepth, 1, 256, "maximumNestingDepth");
        range(maximumTokens, 1, 5_000_000, "maximumTokens");
        range(maximumStringCharacters, 1, 1_048_576, "maximumStringCharacters");
        range(maximumAggregateCharacters, 1, 33_554_432, "maximumAggregateCharacters");
        range(maximumObjectMembers, 1, 1_000_000, "maximumObjectMembers");
        range(maximumSources, 0, 4_096, "maximumSources");
        range(maximumLayers, 1, 16_384, "maximumLayers");
        range(maximumMetadataEntries, 0, 4_096, "maximumMetadataEntries");
        range(maximumExpressionNodes, 1, 131_072, "maximumExpressionNodes");
        range(maximumExpressionDepth, 1, 64, "maximumExpressionDepth");
        range(maximumStops, 1, 2_048, "maximumStops");
        range(maximumCategories, 1, 4_096, "maximumCategories");
        range(maximumCatalogReferences, 0, 16_384, "maximumCatalogReferences");
        range(maximumProducedRules, 1, 4_096, "maximumProducedRules");
        range(maximumOwnedBytes, 1, 536_870_912, "maximumOwnedBytes");
        if (maximumStringCharacters > maximumAggregateCharacters
                || maximumSources + maximumLayers > maximumObjectMembers
                || maximumOwnedBytes < maximumInputBytes) {
            throw new IllegalArgumentException("per-value limits must fit aggregate limits");
        }
    }

    /**
     * Returns the supported default ceilings.
     *
     * @return immutable defaults
     */
    public static MapLibreReadLimits defaults() {
        return DEFAULTS;
    }

    private static void range(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its supported range");
        }
    }
}
