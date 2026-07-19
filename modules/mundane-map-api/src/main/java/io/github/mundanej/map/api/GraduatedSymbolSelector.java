package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Greatest-lower-bound numeric selector with bounded ordered steps and explicit fallback. */
public final class GraduatedSymbolSelector implements SymbolSelector {
    /** Maximum number of lower-inclusive threshold steps. */
    public static final int MAXIMUM_STEPS = 64;

    private final String attribute;
    private final List<GraduatedSymbolStep> steps;
    private final Optional<Symbol> fallback;
    private final SymbolRole role;

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
        this.attribute = AttributeValues.requireName(attribute);
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        Objects.requireNonNull(fallback, "fallback");
        this.fallback = fallback.map(Objects::requireNonNull);
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
        this.role = inferred;
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

    @Override
    public SymbolRole role() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GraduatedSymbolSelector selector
                && attribute.equals(selector.attribute)
                && steps.equals(selector.steps)
                && fallback.equals(selector.fallback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, steps, fallback);
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
