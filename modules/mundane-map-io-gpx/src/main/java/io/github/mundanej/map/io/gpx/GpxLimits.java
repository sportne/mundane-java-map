package io.github.mundanej.map.io.gpx;

/**
 * Immutable GPX open-time ceilings.
 *
 * @param maximumInputBytes encoded input-byte ceiling
 * @param maximumXmlDepth XML nesting ceiling, with the root at one
 * @param maximumXmlEvents structural XML event ceiling
 * @param maximumElements element ceiling
 * @param maximumAttributes attribute ceiling
 * @param maximumNamespaceDeclarations namespace-declaration ceiling
 * @param maximumPhysicalFeatures waypoint/segment candidate ceiling
 * @param maximumTotalCoordinates total coordinate-position ceiling
 * @param maximumCoordinatesPerSegment per-segment coordinate-position ceiling
 * @param maximumParts segment/part ceiling
 * @param maximumScalarCharacters one logical scalar ceiling
 * @param maximumTextCharacters aggregate decoded-character ceiling
 * @param maximumNumberCharacters numeric-token character ceiling
 * @param maximumOwnedBytes conservative operation-owned byte ceiling
 * @param retainedWarnings retained opening-warning ceiling
 */
public record GpxLimits(
        int maximumInputBytes,
        int maximumXmlDepth,
        int maximumXmlEvents,
        int maximumElements,
        int maximumAttributes,
        int maximumNamespaceDeclarations,
        int maximumPhysicalFeatures,
        int maximumTotalCoordinates,
        int maximumCoordinatesPerSegment,
        int maximumParts,
        int maximumScalarCharacters,
        int maximumTextCharacters,
        int maximumNumberCharacters,
        long maximumOwnedBytes,
        int retainedWarnings) {
    private static final GpxLimits DEFAULTS =
            new GpxLimits(
                    16_777_216,
                    64,
                    4_000_000,
                    1_000_000,
                    1_000_000,
                    65_536,
                    100_000,
                    2_000_000,
                    1_000_000,
                    250_000,
                    65_536,
                    16_777_216,
                    128,
                    268_435_456,
                    256);

    /** Validates every ceiling and cross-field invariant. */
    public GpxLimits {
        requireRange(maximumInputBytes, 1, 268_435_456, "maximumInputBytes");
        requireRange(maximumXmlDepth, 1, 128, "maximumXmlDepth");
        requireRange(maximumXmlEvents, 1, 32_000_000, "maximumXmlEvents");
        requireRange(maximumElements, 1, 8_000_000, "maximumElements");
        requireRange(maximumAttributes, 1, 8_000_000, "maximumAttributes");
        requireRange(maximumNamespaceDeclarations, 1, 1_048_576, "maximumNamespaceDeclarations");
        requireRange(maximumPhysicalFeatures, 1, 1_000_000, "maximumPhysicalFeatures");
        requireRange(maximumTotalCoordinates, 1, 16_000_000, "maximumTotalCoordinates");
        requireRange(maximumCoordinatesPerSegment, 1, 16_000_000, "maximumCoordinatesPerSegment");
        requireRange(maximumParts, 1, 2_000_000, "maximumParts");
        requireRange(maximumScalarCharacters, 1, 1_048_576, "maximumScalarCharacters");
        requireRange(maximumTextCharacters, 1, 134_217_728, "maximumTextCharacters");
        requireRange(maximumNumberCharacters, 1, 256, "maximumNumberCharacters");
        requireRange(maximumOwnedBytes, 1, 1_073_741_824L, "maximumOwnedBytes");
        requireRange(retainedWarnings, 1, 4_096, "retainedWarnings");
        if (maximumCoordinatesPerSegment > maximumTotalCoordinates
                || maximumScalarCharacters > maximumTextCharacters
                || maximumPhysicalFeatures > maximumElements) {
            throw new IllegalArgumentException("GPX per-value limits must fit total limits");
        }
        long minimumEvents = Math.addExact(Math.multiplyExact(2L, maximumElements), 2L);
        if (maximumXmlEvents < minimumEvents) {
            throw new IllegalArgumentException("maximumXmlEvents cannot expose maximumElements");
        }
        long minimumOwned =
                Math.addExact(
                        maximumInputBytes,
                        Math.addExact(
                                Math.multiplyExact(16L, maximumTotalCoordinates),
                                Math.addExact(
                                        Math.multiplyExact(4L, maximumParts),
                                        Math.addExact(
                                                Math.multiplyExact(8L, maximumPhysicalFeatures),
                                                Math.multiplyExact(2L, maximumTextCharacters)))));
        if (maximumOwnedBytes < minimumOwned) {
            throw new IllegalArgumentException(
                    "maximumOwnedBytes cannot cover configured GPX input");
        }
    }

    /**
     * Returns the supported default ceilings.
     *
     * @return immutable defaults
     */
    public static GpxLimits defaults() {
        return DEFAULTS;
    }

    private static void requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its supported range");
        }
    }
}
