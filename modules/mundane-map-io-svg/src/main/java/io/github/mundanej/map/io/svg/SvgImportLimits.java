package io.github.mundanej.map.io.svg;

/**
 * Immutable resource limits for one secure SVG marker import.
 *
 * @param maximumInputBytes encoded input-byte ceiling
 * @param maximumElements element-count ceiling
 * @param maximumElementDepth element nesting-depth ceiling
 * @param maximumAttributes aggregate ordinary-attribute ceiling
 * @param maximumAttributeCharacters per-attribute UTF-16 character ceiling
 * @param maximumAggregateAttributeCharacters aggregate attribute-character ceiling
 * @param maximumNumberTokenCharacters per-number token character ceiling
 * @param maximumExpandedCommands generated vector-command ceiling
 * @param maximumDrawingSegments generated drawing-segment ceiling
 * @param maximumTransformFunctions transform-function ceiling
 * @param maximumTransformAncestorDepth transform-bearing ancestor ceiling
 * @param maximumPaintedOutputPaths painted leaf ceiling
 * @param maximumOwnedBytes conservative importer-owned memory ceiling
 */
public record SvgImportLimits(
        int maximumInputBytes,
        int maximumElements,
        int maximumElementDepth,
        int maximumAttributes,
        int maximumAttributeCharacters,
        int maximumAggregateAttributeCharacters,
        int maximumNumberTokenCharacters,
        int maximumExpandedCommands,
        int maximumDrawingSegments,
        int maximumTransformFunctions,
        int maximumTransformAncestorDepth,
        int maximumPaintedOutputPaths,
        long maximumOwnedBytes) {
    private static final int HARD_INPUT_BYTES = 16_777_216;
    private static final int HARD_ELEMENTS = 1_048_576;
    private static final int HARD_ELEMENT_DEPTH = 128;
    private static final int HARD_ATTRIBUTES = 1_048_576;
    private static final int HARD_ATTRIBUTE_CHARACTERS = 1_048_576;
    private static final int HARD_AGGREGATE_ATTRIBUTE_CHARACTERS = 16_777_216;
    private static final int HARD_NUMBER_TOKEN_CHARACTERS = 256;
    private static final int HARD_EXPANDED_COMMANDS = 1_048_576;
    private static final int HARD_DRAWING_SEGMENTS = 1_048_575;
    private static final int HARD_TRANSFORM_FUNCTIONS = 65_536;
    private static final int HARD_TRANSFORM_ANCESTOR_DEPTH = 128;
    private static final int HARD_PAINTED_OUTPUT_PATHS = 65_536;
    private static final long HARD_OWNED_BYTES = 268_435_456L;

    /** Validates a complete limit set. */
    public SvgImportLimits {
        bounded(maximumInputBytes, HARD_INPUT_BYTES, "maximumInputBytes");
        bounded(maximumElements, HARD_ELEMENTS, "maximumElements");
        bounded(maximumElementDepth, HARD_ELEMENT_DEPTH, "maximumElementDepth");
        bounded(maximumAttributes, HARD_ATTRIBUTES, "maximumAttributes");
        bounded(
                maximumAttributeCharacters,
                HARD_ATTRIBUTE_CHARACTERS,
                "maximumAttributeCharacters");
        bounded(
                maximumAggregateAttributeCharacters,
                HARD_AGGREGATE_ATTRIBUTE_CHARACTERS,
                "maximumAggregateAttributeCharacters");
        bounded(
                maximumNumberTokenCharacters,
                HARD_NUMBER_TOKEN_CHARACTERS,
                "maximumNumberTokenCharacters");
        bounded(maximumExpandedCommands, HARD_EXPANDED_COMMANDS, "maximumExpandedCommands");
        bounded(maximumDrawingSegments, HARD_DRAWING_SEGMENTS, "maximumDrawingSegments");
        bounded(maximumTransformFunctions, HARD_TRANSFORM_FUNCTIONS, "maximumTransformFunctions");
        bounded(
                maximumTransformAncestorDepth,
                HARD_TRANSFORM_ANCESTOR_DEPTH,
                "maximumTransformAncestorDepth");
        bounded(maximumPaintedOutputPaths, HARD_PAINTED_OUTPUT_PATHS, "maximumPaintedOutputPaths");
        bounded(maximumOwnedBytes, HARD_OWNED_BYTES, "maximumOwnedBytes");
        if (maximumAttributeCharacters > maximumAggregateAttributeCharacters) {
            throw new IllegalArgumentException(
                    "maximumAttributeCharacters must not exceed aggregate characters");
        }
        if (maximumTransformAncestorDepth > maximumElementDepth) {
            throw new IllegalArgumentException(
                    "maximumTransformAncestorDepth must not exceed element depth");
        }
        if (maximumPaintedOutputPaths > maximumElements) {
            throw new IllegalArgumentException(
                    "maximumPaintedOutputPaths must not exceed maximumElements");
        }
        if (maximumDrawingSegments >= maximumExpandedCommands) {
            throw new IllegalArgumentException(
                    "maximumDrawingSegments must be less than maximumExpandedCommands");
        }
    }

    /**
     * Returns the bounded default profile.
     *
     * @return default import limits
     */
    public static SvgImportLimits defaults() {
        return new SvgImportLimits(
                1_048_576,
                2_048,
                32,
                16_384,
                65_536,
                1_048_576,
                128,
                131_072,
                65_536,
                4_096,
                16,
                2_048,
                16_777_216L);
    }

    /**
     * Returns a copy with the encoded-input ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumInputBytes(int value) {
        return copy(
                value,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the element ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumElements(int value) {
        return copy(
                maximumInputBytes,
                value,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the element-depth ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumElementDepth(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                value,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the ordinary-attribute ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumAttributes(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                value,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the per-attribute character ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumAttributeCharacters(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                value,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the aggregate-attribute character ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumAggregateAttributeCharacters(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                value,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the number-token character ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumNumberTokenCharacters(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                value,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the expanded-command ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumExpandedCommands(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                value,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the drawing-segment ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumDrawingSegments(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                value,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the transform-function ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumTransformFunctions(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                value,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the transform-ancestor ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumTransformAncestorDepth(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                value,
                maximumPaintedOutputPaths,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the painted-path ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumPaintedOutputPaths(int value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                value,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with the conservative owned-byte ceiling replaced.
     *
     * @param value replacement ceiling
     * @return validated copy
     */
    public SvgImportLimits withMaximumOwnedBytes(long value) {
        return copy(
                maximumInputBytes,
                maximumElements,
                maximumElementDepth,
                maximumAttributes,
                maximumAttributeCharacters,
                maximumAggregateAttributeCharacters,
                maximumNumberTokenCharacters,
                maximumExpandedCommands,
                maximumDrawingSegments,
                maximumTransformFunctions,
                maximumTransformAncestorDepth,
                maximumPaintedOutputPaths,
                value);
    }

    private SvgImportLimits copy(
            int inputBytes,
            int elements,
            int elementDepth,
            int attributes,
            int attributeCharacters,
            int aggregateAttributeCharacters,
            int numberTokenCharacters,
            int expandedCommands,
            int drawingSegments,
            int transformFunctions,
            int transformAncestorDepth,
            int paintedOutputPaths,
            long ownedBytes) {
        return new SvgImportLimits(
                inputBytes,
                elements,
                elementDepth,
                attributes,
                attributeCharacters,
                aggregateAttributeCharacters,
                numberTokenCharacters,
                expandedCommands,
                drawingSegments,
                transformFunctions,
                transformAncestorDepth,
                paintedOutputPaths,
                ownedBytes);
    }

    private static void bounded(long value, long maximum, String name) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 1 and " + maximum);
        }
    }
}
