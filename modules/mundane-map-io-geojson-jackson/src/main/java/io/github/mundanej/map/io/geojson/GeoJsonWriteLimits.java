package io.github.mundanej.map.io.geojson;

/**
 * Immutable ceilings for one complete GeoJSON write.
 *
 * @param maximumOutputBytes encoded UTF-8 output ceiling
 * @param maximumOwnedBytes conservative operation-owned byte ceiling
 * @param maximumNestingDepth generated JSON nesting ceiling, root at one
 * @param maximumFeatures feature ceiling
 * @param maximumTotalCoordinates aggregate coordinate-position ceiling
 * @param maximumCoordinatesPerGeometry per-geometry position ceiling
 * @param maximumParts aggregate part, ring, and polygon ceiling
 * @param maximumPropertiesPerFeature per-feature property ceiling
 * @param maximumTotalProperties aggregate property ceiling
 * @param maximumScalarCharacters per-scalar UTF-16 character ceiling
 * @param maximumAggregateCharacters aggregate scalar-character ceiling
 * @param maximumNumberCharacters emitted numeric-token character ceiling
 */
public record GeoJsonWriteLimits(
        long maximumOutputBytes,
        long maximumOwnedBytes,
        int maximumNestingDepth,
        int maximumFeatures,
        int maximumTotalCoordinates,
        int maximumCoordinatesPerGeometry,
        int maximumParts,
        int maximumPropertiesPerFeature,
        int maximumTotalProperties,
        int maximumScalarCharacters,
        int maximumAggregateCharacters,
        int maximumNumberCharacters) {
    private static final GeoJsonWriteLimits DEFAULTS =
            new GeoJsonWriteLimits(
                    16_777_216,
                    268_435_456,
                    8,
                    100_000,
                    2_000_000,
                    1_000_000,
                    250_000,
                    256,
                    1_000_000,
                    65_536,
                    16_777_216,
                    128);

    /** Validates every ceiling and cross-field invariant. */
    public GeoJsonWriteLimits {
        requireRange(maximumOutputBytes, 1, 268_435_456L, "maximumOutputBytes");
        requireRange(maximumOwnedBytes, 1, 1_073_741_824L, "maximumOwnedBytes");
        requireRange(maximumNestingDepth, 1, 16, "maximumNestingDepth");
        requireRange(maximumFeatures, 1, 1_000_000, "maximumFeatures");
        requireRange(maximumTotalCoordinates, 1, 16_000_000, "maximumTotalCoordinates");
        requireRange(maximumCoordinatesPerGeometry, 1, 16_000_000, "maximumCoordinatesPerGeometry");
        requireRange(maximumParts, 1, 2_000_000, "maximumParts");
        requireRange(maximumPropertiesPerFeature, 1, 4_096, "maximumPropertiesPerFeature");
        requireRange(maximumTotalProperties, 1, 8_000_000, "maximumTotalProperties");
        requireRange(maximumScalarCharacters, 1, 1_048_576, "maximumScalarCharacters");
        requireRange(maximumAggregateCharacters, 1, 134_217_728, "maximumAggregateCharacters");
        requireRange(maximumNumberCharacters, 1, 256, "maximumNumberCharacters");
        if (maximumCoordinatesPerGeometry > maximumTotalCoordinates
                || maximumPropertiesPerFeature > maximumTotalProperties
                || maximumScalarCharacters > maximumAggregateCharacters) {
            throw new IllegalArgumentException(
                    "GeoJSON per-value write limits must fit total limits");
        }
        long minimumOwned =
                Math.addExact(
                        maximumOutputBytes,
                        Math.addExact(
                                Math.multiplyExact(16L, maximumTotalCoordinates),
                                Math.addExact(
                                        Math.multiplyExact(
                                                8L,
                                                Math.addExact(
                                                        maximumFeatures,
                                                        Math.addExact(
                                                                maximumParts,
                                                                maximumTotalProperties))),
                                        Math.multiplyExact(2L, maximumAggregateCharacters))));
        if (maximumOwnedBytes < minimumOwned) {
            throw new IllegalArgumentException(
                    "maximumOwnedBytes cannot cover the configured write ceilings");
        }
    }

    /**
     * Returns the supported default ceilings.
     *
     * @return immutable defaults
     */
    public static GeoJsonWriteLimits defaults() {
        return DEFAULTS;
    }

    private static void requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its supported range");
        }
    }
}
