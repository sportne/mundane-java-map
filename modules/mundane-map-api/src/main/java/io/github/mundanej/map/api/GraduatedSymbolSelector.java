package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Greatest-lower-bound numeric selector with bounded ordered steps and explicit fallback.
 *
 * <p>Canonical numeric values select the last step whose normalized threshold is less than or equal
 * to the value. A below-range, missing, null, or non-numeric value selects {@link #fallback()} when
 * present and otherwise omits that geometry role; omission does not filter source content or
 * extents.
 */
public final class GraduatedSymbolSelector implements SymbolSelector {
    /** Maximum number of lower-inclusive threshold steps. */
    public static final int MAXIMUM_STEPS = 64;

    private final String attribute;
    private final List<GraduatedSymbolStep> steps;
    private final Optional<Symbol> fallback;
    private final Optional<Symbol> invalidFallback;
    private final SymbolRole role;
    private final InterpolationInput input;
    private final AttributeValueConversion conversion;

    /**
     * Creates a bounded selector whose normalized thresholds must be strictly increasing.
     *
     * @param attribute exact canonical attribute name
     * @param steps non-empty lower-inclusive steps in increasing order
     * @param fallback optional below-range/missing/non-numeric fallback
     */
    public GraduatedSymbolSelector(
            String attribute,
            List<GraduatedSymbolStep> steps,
            Optional<? extends Symbol> fallback) {
        this(
                Optional.of(AttributeValues.requireName(attribute)),
                steps,
                fallback,
                fallback,
                InterpolationInput.ATTRIBUTE,
                AttributeValueConversion.IDENTITY);
    }

    private GraduatedSymbolSelector(
            Optional<String> attribute,
            List<GraduatedSymbolStep> steps,
            Optional<? extends Symbol> fallback,
            Optional<? extends Symbol> invalidFallback,
            InterpolationInput input,
            AttributeValueConversion conversion) {
        Objects.requireNonNull(attribute, "attribute");
        this.attribute = attribute.orElse("");
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        Objects.requireNonNull(fallback, "fallback");
        this.fallback = fallback.map(Objects::requireNonNull);
        Objects.requireNonNull(invalidFallback, "invalidFallback");
        this.invalidFallback = invalidFallback.map(Objects::requireNonNull);
        this.input = Objects.requireNonNull(input, "input");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
        if ((input == InterpolationInput.ATTRIBUTE) != attribute.isPresent()) {
            throw new IllegalArgumentException(
                    "attribute must be present only for attribute input");
        }
        if (this.steps.isEmpty() || this.steps.size() > MAXIMUM_STEPS) {
            throw new IllegalArgumentException("steps must contain between 1 and 64 entries");
        }
        SymbolRole inferred = null;
        GraduatedSymbolStep previous = null;
        for (GraduatedSymbolStep step : this.steps) {
            Objects.requireNonNull(step, "step");
            if (previous != null
                    && previous.lowerInclusive().compareTo(step.lowerInclusive()) >= 0) {
                throw new IllegalArgumentException(
                        "steps must have strictly increasing normalized lowerInclusive values");
            }
            inferred = requireSameRole(inferred, step.symbol().role());
            previous = step;
        }
        if (this.fallback.isPresent()) {
            inferred = requireSameRole(inferred, this.fallback.orElseThrow().role());
        }
        if (this.invalidFallback.isPresent()) {
            inferred = requireSameRole(inferred, this.invalidFallback.orElseThrow().role());
        }
        this.role = inferred;
    }

    /**
     * Creates a converted attribute-driven step selector.
     *
     * @param attribute exact canonical attribute
     * @param steps increasing lower-inclusive steps
     * @param fallback below-range result
     * @param invalidFallback missing/null/type/conversion-failure result
     * @param conversion closed input conversion
     * @return immutable selector
     */
    public static GraduatedSymbolSelector expressionInput(
            String attribute,
            List<GraduatedSymbolStep> steps,
            Optional<? extends Symbol> fallback,
            Optional<? extends Symbol> invalidFallback,
            AttributeValueConversion conversion) {
        return new GraduatedSymbolSelector(
                Optional.of(AttributeValues.requireName(attribute)),
                steps,
                fallback,
                invalidFallback,
                InterpolationInput.ATTRIBUTE,
                conversion);
    }

    /**
     * Creates a zoom-driven step selector.
     *
     * @param steps increasing lower-inclusive steps
     * @param fallback below-range result
     * @param invalidFallback absent-zoom result
     * @return immutable selector
     */
    public static GraduatedSymbolSelector zoom(
            List<GraduatedSymbolStep> steps,
            Optional<? extends Symbol> fallback,
            Optional<? extends Symbol> invalidFallback) {
        return new GraduatedSymbolSelector(
                Optional.empty(),
                steps,
                fallback,
                invalidFallback,
                InterpolationInput.ZOOM,
                AttributeValueConversion.IDENTITY);
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
     * Returns the immutable increasing step list.
     *
     * @return non-empty bounded step list
     */
    public List<GraduatedSymbolStep> steps() {
        return steps;
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
     * Returns the invalid-input fallback.
     *
     * @return invalid-input fallback
     */
    public Optional<Symbol> invalidFallback() {
        return invalidFallback;
    }

    /**
     * Returns the input kind.
     *
     * @return input kind
     */
    public InterpolationInput input() {
        return input;
    }

    /**
     * Returns the input conversion.
     *
     * @return input conversion
     */
    public AttributeValueConversion conversion() {
        return conversion;
    }

    @Override
    public SymbolRole role() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GraduatedSymbolSelector selector
                && attribute.equals(selector.attribute)
                && steps.equals(selector.steps)
                && fallback.equals(selector.fallback)
                && invalidFallback.equals(selector.invalidFallback)
                && input == selector.input
                && conversion.equals(selector.conversion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, steps, fallback, invalidFallback, input, conversion);
    }

    @Override
    public String toString() {
        return "GraduatedSymbolSelector[attribute="
                + attribute
                + ", steps="
                + steps
                + ", fallback="
                + fallback
                + ']';
    }

    private static SymbolRole requireSameRole(SymbolRole expected, SymbolRole actual) {
        FixedSymbolSelector.requireVectorRole(actual);
        if (expected != null && expected != actual) {
            throw new IllegalArgumentException("steps and fallback must have one symbol role");
        }
        return actual;
    }
}
