package io.github.mundanej.map.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable non-empty ordered portrayal-rule plan shared by role selectors. */
public final class RulePortrayalPlan {
    private final List<PortrayalRule> rules;
    private final boolean markerRole;
    private final boolean lineRole;
    private final boolean fillRole;

    /**
     * Creates and validates an ordered rule plan.
     *
     * @param rules non-empty declaration-ordered rules
     */
    public RulePortrayalPlan(List<PortrayalRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        if (this.rules.isEmpty() || this.rules.size() > 4_096) {
            throw new IllegalArgumentException("rules must be non-empty and bounded");
        }
        this.rules.forEach(rule -> Objects.requireNonNull(rule, "rule"));
        int predicateNodes = 0;
        for (PortrayalRule rule : this.rules) {
            if (rule.predicate().isPresent()) {
                predicateNodes += validatePredicate(rule.predicate().orElseThrow(), 1);
                if (predicateNodes > 1_024) {
                    throw new IllegalArgumentException("predicate plan exceeds 1024 nodes");
                }
            }
        }
        markerRole = this.rules.stream().anyMatch(rule -> !rule.markers().isEmpty());
        lineRole = this.rules.stream().anyMatch(rule -> !rule.lines().isEmpty());
        fillRole = this.rules.stream().anyMatch(rule -> !rule.fills().isEmpty());
        validateElseIntervals(this.rules);
    }

    /**
     * Returns immutable declaration-ordered rules.
     *
     * @return immutable rules
     */
    public List<PortrayalRule> rules() {
        return rules;
    }

    /**
     * Returns whether any rule has a scale constraint.
     *
     * @return true when evaluation requires an explicit scale
     */
    public boolean requiresScaleContext() {
        return rules.stream().anyMatch(rule -> rule.scale().constrained());
    }

    /**
     * Creates the ordinary three-role portrayal backed by this plan.
     *
     * @return immutable portrayal
     */
    public FeaturePortrayal portrayal() {
        return new FeaturePortrayal(
                selector(SymbolRole.MARKER, markerRole),
                selector(SymbolRole.LINE, lineRole),
                selector(SymbolRole.FILL, fillRole));
    }

    private Optional<RuleSymbolSelector> selector(SymbolRole role, boolean present) {
        return present ? Optional.of(new RuleSymbolSelector(this, role)) : Optional.empty();
    }

    private static void validateElseIntervals(List<PortrayalRule> rules) {
        List<ScaleInterval> intervals = new ArrayList<>();
        for (PortrayalRule rule : rules) {
            if (!rule.elseRule()) {
                continue;
            }
            for (ScaleInterval existing : intervals) {
                if (overlaps(existing, rule.scale())) {
                    throw new IllegalArgumentException("else-rule scale intervals overlap");
                }
            }
            intervals.add(rule.scale());
        }
    }

    private static boolean overlaps(ScaleInterval first, ScaleInterval second) {
        double firstMin = first.minimumInclusive().orElse(0.0);
        double secondMin = second.minimumInclusive().orElse(0.0);
        double firstMax = first.maximumExclusive().orElse(Double.POSITIVE_INFINITY);
        double secondMax = second.maximumExclusive().orElse(Double.POSITIVE_INFINITY);
        return Math.max(firstMin, secondMin) < Math.min(firstMax, secondMax);
    }

    private static int validatePredicate(PortrayalPredicate predicate, int depth) {
        if (depth > 32) {
            throw new IllegalArgumentException("predicate depth exceeds 32");
        }
        if (!(predicate instanceof PortrayalPredicate.Logical logical)) {
            return 1;
        }
        int count = 1;
        for (PortrayalPredicate child : logical.children()) {
            count += validatePredicate(child, depth + 1);
            if (count > 1_024) {
                return count;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RulePortrayalPlan plan && rules.equals(plan.rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return "RulePortrayalPlan[rules=" + rules + ']';
    }
}
