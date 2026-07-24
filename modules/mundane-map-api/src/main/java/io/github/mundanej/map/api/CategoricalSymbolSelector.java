package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exact bounded attribute-category selector with explicit fallback or omission.
 *
 * <p>Selection compares the canonical immutable attribute value to each normalized rule value using
 * exact value equality. A missing, null, unsupported, or unmatched value selects {@link
 * #fallback()} when present and otherwise omits that geometry role; omission is presentation-only
 * and does not remove the feature from source queries or extents.
 */
public final class CategoricalSymbolSelector implements SymbolSelector {
    /** Maximum number of exact category rules. */
    public static final int MAXIMUM_RULES = 1_024;

    private final String attribute;
    private final List<CategoricalSymbolRule> rules;
    private final Optional<Symbol> fallback;
    private final SymbolRole role;
    private final AttributeValueConversion conversion;
    private final boolean missingAsNull;

    /**
     * Creates a bounded selector and rejects normalized duplicates and mixed symbol roles.
     *
     * @param attribute exact canonical attribute name
     * @param rules non-empty category rules in declaration order
     * @param fallback optional unmatched/missing/unsupported fallback
     */
    public CategoricalSymbolSelector(
            String attribute,
            List<CategoricalSymbolRule> rules,
            Optional<? extends Symbol> fallback) {
        this(attribute, rules, fallback, AttributeValueConversion.IDENTITY, false);
    }

    private CategoricalSymbolSelector(
            String attribute,
            List<CategoricalSymbolRule> rules,
            Optional<? extends Symbol> fallback,
            AttributeValueConversion conversion,
            boolean missingAsNull) {
        this.attribute = AttributeValues.requireName(attribute);
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        Objects.requireNonNull(fallback, "fallback");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
        this.missingAsNull = missingAsNull;
        this.fallback = fallback.map(Objects::requireNonNull);
        if (this.rules.isEmpty() || this.rules.size() > MAXIMUM_RULES) {
            throw new IllegalArgumentException("rules must contain between 1 and 1024 entries");
        }
        Set<ThematicValue> categories = new HashSet<>();
        SymbolRole inferred = null;
        for (CategoricalSymbolRule rule : this.rules) {
            Objects.requireNonNull(rule, "rule");
            if (!categories.add(rule.value())) {
                throw new IllegalArgumentException("rules contain a duplicate normalized category");
            }
            inferred = requireSameRole(inferred, rule.symbol().role());
        }
        if (this.fallback.isPresent()) {
            inferred = requireSameRole(inferred, this.fallback.orElseThrow().role());
        }
        this.role = inferred;
    }

    /**
     * Creates an expression-input selector with explicit conversion and missing-as-null behavior.
     *
     * @param attribute exact canonical attribute name
     * @param rules non-empty category rules
     * @param fallback optional unmatched or failed-conversion fallback
     * @param conversion closed input conversion
     * @return immutable selector
     */
    public static CategoricalSymbolSelector expressionInput(
            String attribute,
            List<CategoricalSymbolRule> rules,
            Optional<? extends Symbol> fallback,
            AttributeValueConversion conversion) {
        return new CategoricalSymbolSelector(attribute, rules, fallback, conversion, true);
    }

    /**
     * Returns the exact selected attribute name.
     *
     * @return canonical attribute name
     */
    public String attribute() {
        return attribute;
    }

    /**
     * Returns the immutable declaration-ordered rule list.
     *
     * @return non-empty bounded rule list
     */
    public List<CategoricalSymbolRule> rules() {
        return rules;
    }

    /**
     * Returns the explicit fallback, or empty for omission.
     *
     * @return optional same-role fallback symbol
     */
    public Optional<Symbol> fallback() {
        return fallback;
    }

    /**
     * Returns the closed input conversion.
     *
     * @return input conversion
     */
    public AttributeValueConversion conversion() {
        return conversion;
    }

    /**
     * Returns whether a missing attribute is observed as canonical null.
     *
     * @return missing-as-null policy
     */
    public boolean missingAsNull() {
        return missingAsNull;
    }

    @Override
    public SymbolRole role() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CategoricalSymbolSelector selector
                && attribute.equals(selector.attribute)
                && rules.equals(selector.rules)
                && fallback.equals(selector.fallback)
                && conversion.equals(selector.conversion)
                && missingAsNull == selector.missingAsNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, rules, fallback, conversion, missingAsNull);
    }

    @Override
    public String toString() {
        return "CategoricalSymbolSelector[attribute="
                + attribute
                + ", rules="
                + rules
                + ", fallback="
                + fallback
                + ']';
    }

    private static SymbolRole requireSameRole(SymbolRole expected, SymbolRole actual) {
        FixedSymbolSelector.requireVectorRole(actual);
        if (expected != null && expected != actual) {
            throw new IllegalArgumentException("rules and fallback must have one symbol role");
        }
        return actual;
    }
}
