package io.github.mundanej.map.io.se;

/**
 * Immutable hard-bounded resource limits for one Symbology Encoding read.
 *
 * @param maximumInputBytes encoded input-byte ceiling
 * @param maximumElementDepth XML element-depth ceiling
 * @param maximumElements XML element-count ceiling
 * @param maximumAttributes XML ordinary-attribute ceiling
 * @param maximumAggregateTextCharacters aggregate decoded text ceiling
 * @param maximumValueCharacters per-value decoded text ceiling
 * @param maximumRules rule-count ceiling
 * @param maximumPredicates predicate-node ceiling
 * @param maximumPredicateDepth predicate-depth ceiling
 * @param maximumSymbolizers symbolizer-count ceiling
 * @param maximumCatalogReferences explicit catalog-reference ceiling
 * @param maximumOutputSymbols generated symbol/composite-child ceiling
 * @param maximumOwnedBytes conservative reader-owned memory ceiling
 */
public record SeReadLimits(
        int maximumInputBytes,
        int maximumElementDepth,
        int maximumElements,
        int maximumAttributes,
        int maximumAggregateTextCharacters,
        int maximumValueCharacters,
        int maximumRules,
        int maximumPredicates,
        int maximumPredicateDepth,
        int maximumSymbolizers,
        int maximumCatalogReferences,
        int maximumOutputSymbols,
        long maximumOwnedBytes) {
    private static final int HARD_INPUT_BYTES = 1_048_576;
    private static final int HARD_ELEMENT_DEPTH = 32;
    private static final int HARD_ELEMENTS = 4_096;
    private static final int HARD_ATTRIBUTES = 8_192;
    private static final int HARD_TEXT_CHARACTERS = 262_144;
    private static final int HARD_VALUE_CHARACTERS = 4_096;
    private static final int HARD_RULES = 256;
    private static final int HARD_PREDICATES = 1_024;
    private static final int HARD_PREDICATE_DEPTH = 32;
    private static final int HARD_SYMBOLIZERS = 1_024;
    private static final int HARD_CATALOG_REFERENCES = 1_024;
    private static final int HARD_OUTPUT_SYMBOLS = 2_048;
    private static final long HARD_OWNED_BYTES = 16_777_216L;

    /** Validates that every limit is positive and no greater than the approved hard ceiling. */
    public SeReadLimits {
        validate(
                maximumInputBytes,
                maximumElementDepth,
                maximumElements,
                maximumAttributes,
                maximumAggregateTextCharacters,
                maximumValueCharacters,
                maximumRules,
                maximumPredicates,
                maximumPredicateDepth,
                maximumSymbolizers,
                maximumCatalogReferences,
                maximumOutputSymbols,
                maximumOwnedBytes);
    }

    /**
     * Returns the approved bounded defaults.
     *
     * @return immutable default limits
     */
    public static SeReadLimits defaults() {
        return new SeReadLimits(
                HARD_INPUT_BYTES,
                HARD_ELEMENT_DEPTH,
                HARD_ELEMENTS,
                HARD_ATTRIBUTES,
                HARD_TEXT_CHARACTERS,
                HARD_VALUE_CHARACTERS,
                HARD_RULES,
                HARD_PREDICATES,
                HARD_PREDICATE_DEPTH,
                HARD_SYMBOLIZERS,
                HARD_CATALOG_REFERENCES,
                HARD_OUTPUT_SYMBOLS,
                HARD_OWNED_BYTES);
    }

    private static void validate(
            int input,
            int depth,
            int elements,
            int attributes,
            int text,
            int value,
            int rules,
            int predicates,
            int predicateDepth,
            int symbolizers,
            int catalogReferences,
            int outputSymbols,
            long ownedBytes) {
        bounded(input, HARD_INPUT_BYTES, "maximumInputBytes");
        bounded(depth, HARD_ELEMENT_DEPTH, "maximumElementDepth");
        bounded(elements, HARD_ELEMENTS, "maximumElements");
        bounded(attributes, HARD_ATTRIBUTES, "maximumAttributes");
        bounded(text, HARD_TEXT_CHARACTERS, "maximumAggregateTextCharacters");
        bounded(value, HARD_VALUE_CHARACTERS, "maximumValueCharacters");
        bounded(rules, HARD_RULES, "maximumRules");
        bounded(predicates, HARD_PREDICATES, "maximumPredicates");
        bounded(predicateDepth, HARD_PREDICATE_DEPTH, "maximumPredicateDepth");
        bounded(symbolizers, HARD_SYMBOLIZERS, "maximumSymbolizers");
        bounded(catalogReferences, HARD_CATALOG_REFERENCES, "maximumCatalogReferences");
        bounded(outputSymbols, HARD_OUTPUT_SYMBOLS, "maximumOutputSymbols");
        bounded(ownedBytes, HARD_OWNED_BYTES, "maximumOwnedBytes");
        if (value > text) {
            throw new IllegalArgumentException(
                    "maximumValueCharacters must not exceed aggregate text");
        }
        if (predicateDepth > depth) {
            throw new IllegalArgumentException(
                    "maximumPredicateDepth must not exceed element depth");
        }
    }

    private static void bounded(long value, long maximum, String name) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
    }
}
