package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.ResolvedFeaturePortrayal;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.RuleSymbolSelector;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolSelector;
import io.github.mundanej.map.api.TextAttribute;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Compiled immutable resolution of one closed feature portrayal. */
public final class FeaturePortrayalResolver {
    private final FeaturePortrayal portrayal;
    private final Map<SymbolRole, SymbolSelector> selectors;
    private final Map<SymbolRole, Map<ThematicValue, Symbol>> categorical;
    private final Map<SymbolRole, GraduatedTable> graduated;
    private final List<String> requiredSymbolAttributes;
    private final List<Symbol> reachableSymbols;
    private final Optional<PointLabelProfile> pointLabel;
    private final Optional<RulePortrayalEvaluator> ruleEvaluator;

    private FeaturePortrayalResolver(FeaturePortrayal portrayal) {
        this.portrayal = Objects.requireNonNull(portrayal, "portrayal");
        EnumMap<SymbolRole, SymbolSelector> byRole = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, Map<ThematicValue, Symbol>> compiled = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, GraduatedTable> graduatedCompiled = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, List<Symbol>> symbolsByRole = new EnumMap<>(SymbolRole.class);
        Set<String> attributes = new LinkedHashSet<>();
        RulePortrayalPlan sharedRulePlan = null;
        for (SymbolSelector selector : portrayal.selectors()) {
            byRole.put(selector.role(), selector);
            if (selector instanceof FixedSymbolSelector fixed) {
                symbolsByRole
                        .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                        .add(fixed.symbol());
                continue;
            }
            if (selector instanceof CategoricalSymbolSelector categories) {
                attributes.add(categories.attribute());
                Map<ThematicValue, Symbol> lookup = new LinkedHashMap<>();
                for (CategoricalSymbolRule rule : categories.rules()) {
                    lookup.put(rule.value(), rule.symbol());
                    symbolsByRole
                            .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                            .add(rule.symbol());
                }
                categories
                        .fallback()
                        .ifPresent(
                                symbol ->
                                        symbolsByRole
                                                .computeIfAbsent(
                                                        selector.role(),
                                                        ignored -> new ArrayList<>())
                                                .add(symbol));
                compiled.put(categories.role(), Collections.unmodifiableMap(lookup));
                continue;
            }
            if (selector instanceof RuleSymbolSelector rules) {
                if (sharedRulePlan != null && !sharedRulePlan.equals(rules.plan())) {
                    throw new IllegalArgumentException(
                            "rule selectors in one portrayal must share one plan");
                }
                sharedRulePlan = rules.plan();
                continue;
            }
            GraduatedSymbolSelector ranges = (GraduatedSymbolSelector) selector;
            attributes.add(ranges.attribute());
            BigDecimal[] thresholds = new BigDecimal[ranges.steps().size()];
            Symbol[] selected = new Symbol[ranges.steps().size()];
            for (int index = 0; index < ranges.steps().size(); index++) {
                GraduatedSymbolStep step = ranges.steps().get(index);
                thresholds[index] = step.lowerInclusive();
                selected[index] = step.symbol();
                symbolsByRole
                        .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                        .add(step.symbol());
            }
            ranges.fallback()
                    .ifPresent(
                            symbol ->
                                    symbolsByRole
                                            .computeIfAbsent(
                                                    selector.role(), ignored -> new ArrayList<>())
                                            .add(symbol));
            graduatedCompiled.put(ranges.role(), new GraduatedTable(thresholds, selected));
        }
        RulePortrayalEvaluator compiledRules =
                sharedRulePlan == null ? null : new RulePortrayalEvaluator(sharedRulePlan);
        if (compiledRules != null) {
            attributes.addAll(compiledRules.requiredAttributes());
            for (SymbolSelector selector : portrayal.selectors()) {
                if (selector instanceof RuleSymbolSelector) {
                    symbolsByRole
                            .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                            .addAll(compiledRules.reachableSymbols(selector.role()));
                }
            }
        }
        List<Symbol> symbols = new ArrayList<>();
        symbols.addAll(symbolsByRole.getOrDefault(SymbolRole.MARKER, List.of()));
        symbols.addAll(symbolsByRole.getOrDefault(SymbolRole.LINE, List.of()));
        symbols.addAll(symbolsByRole.getOrDefault(SymbolRole.FILL, List.of()));
        this.selectors = Collections.unmodifiableMap(byRole);
        this.categorical = Collections.unmodifiableMap(compiled);
        this.graduated = Collections.unmodifiableMap(graduatedCompiled);
        this.requiredSymbolAttributes = List.copyOf(attributes);
        this.reachableSymbols = List.copyOf(symbols);
        this.pointLabel = portrayal.pointLabel();
        this.ruleEvaluator = Optional.ofNullable(compiledRules);
    }

    /**
     * Compiles one immutable portrayal into deterministic lookup structures.
     *
     * @param portrayal immutable closed portrayal
     * @return compiled immutable resolver
     */
    public static FeaturePortrayalResolver compile(FeaturePortrayal portrayal) {
        return new FeaturePortrayalResolver(portrayal);
    }

    /**
     * Returns the retained immutable portrayal.
     *
     * @return compiled portrayal
     */
    public FeaturePortrayal portrayal() {
        return portrayal;
    }

    /**
     * Returns exact required symbol attributes in marker, line, fill order with duplicates removed.
     *
     * @return immutable ordered unique attribute names
     */
    public List<String> requiredSymbolAttributes() {
        return requiredSymbolAttributes;
    }

    /**
     * Returns every fixed, rule, and fallback symbol in deterministic declaration order.
     *
     * @return immutable reachable symbol list
     */
    public List<Symbol> reachableSymbols() {
        return reachableSymbols;
    }

    /**
     * Returns the optional singular-point label profile.
     *
     * @return optional immutable profile
     */
    public Optional<PointLabelProfile> pointLabel() {
        return pointLabel;
    }

    /**
     * Returns whether this portrayal requires an explicit scale denominator.
     *
     * @return true for a scale-constrained rule plan
     */
    public boolean requiresScaleContext() {
        return ruleEvaluator.map(RulePortrayalEvaluator::requiresScaleContext).orElse(false);
    }

    /**
     * Returns ordered unique attributes required to validate this complete portrayal.
     *
     * @return immutable marker/line/fill/label attribute order
     */
    public List<String> requiredConfigurationAttributes() {
        return requiredPaintAttributes(true);
    }

    /**
     * Returns ordered unique attributes required for paint at one resolution.
     *
     * @param unitsPerPixel finite positive map units per logical pixel
     * @return immutable symbol attributes plus a visible text attribute when present
     */
    public List<String> requiredPaintAttributes(double unitsPerPixel) {
        return requiredPaintAttributes(
                pointLabel.isPresent()
                        && pointLabel.orElseThrow().visibleResolution().includes(unitsPerPixel));
    }

    /**
     * Resolves ordinary eligible point-label text without formatting or diagnostics.
     *
     * @param featureName immutable feature display name
     * @param attributes canonical attributes
     * @param unitsPerPixel finite positive map units per logical pixel
     * @return exact non-blank name/text attribute, or empty for an ordinary omission
     */
    public Optional<String> resolveLabelText(
            String featureName, Map<String, Object> attributes, double unitsPerPixel) {
        Objects.requireNonNull(featureName, "featureName");
        Objects.requireNonNull(attributes, "attributes");
        if (pointLabel.isEmpty()
                || !pointLabel.orElseThrow().visibleResolution().includes(unitsPerPixel)) {
            return Optional.empty();
        }
        if (pointLabel.orElseThrow().textSource() instanceof FeatureName) {
            return featureName.isBlank() ? Optional.empty() : Optional.of(featureName);
        }
        String attribute = ((TextAttribute) pointLabel.orElseThrow().textSource()).attribute();
        Object value = attributes.get(attribute);
        return value instanceof String text && !text.isBlank()
                ? Optional.of(text)
                : Optional.empty();
    }

    /**
     * Resolves one geometry role from canonical feature attributes.
     *
     * @param role geometry symbol role
     * @param attributes immutable canonical feature attributes
     * @return selected symbol, or empty when the role or matching fallback is absent
     */
    public Optional<Symbol> resolve(SymbolRole role, Map<String, Object> attributes) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(attributes, "attributes");
        SymbolSelector selector = selectors.get(role);
        if (selector == null) {
            return Optional.empty();
        }
        if (selector instanceof RuleSymbolSelector) {
            return resolveAll(attributes, PortrayalEvaluationContext.UNSCALED).forRole(role);
        }
        return resolveOrdinary(selector, attributes);
    }

    private Optional<Symbol> resolveOrdinary(
            SymbolSelector selector, Map<String, Object> attributes) {
        SymbolRole role = selector.role();
        if (selector instanceof FixedSymbolSelector fixed) {
            return Optional.of(fixed.symbol());
        }
        if (selector instanceof CategoricalSymbolSelector categories) {
            if (!attributes.containsKey(categories.attribute())) {
                return categories.fallback();
            }
            Optional<ThematicValue> value =
                    ThematicValue.fromAttribute(attributes.get(categories.attribute()));
            if (value.isEmpty()) {
                return categories.fallback();
            }
            Symbol matched = categorical.get(role).get(value.orElseThrow());
            return matched == null ? categories.fallback() : Optional.of(matched);
        }
        GraduatedSymbolSelector ranges = (GraduatedSymbolSelector) selector;
        if (!attributes.containsKey(ranges.attribute())) {
            return ranges.fallback();
        }
        Optional<ThematicValue> value =
                ThematicValue.fromAttribute(attributes.get(ranges.attribute()));
        if (value.isEmpty() || value.orElseThrow().kind() != ThematicValue.Kind.NUMERIC) {
            return ranges.fallback();
        }
        Symbol matched =
                graduated.get(role).greatestLowerBound((BigDecimal) value.orElseThrow().value());
        return matched == null ? ranges.fallback() : Optional.of(matched);
    }

    /**
     * Resolves all roles once using explicit evaluation context.
     *
     * @param attributes immutable canonical feature attributes
     * @param context immutable scale context
     * @return immutable all-role result
     */
    public ResolvedFeaturePortrayal resolveAll(
            Map<String, Object> attributes, PortrayalEvaluationContext context) {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(context, "context");
        ResolvedFeaturePortrayal rules =
                ruleEvaluator
                        .map(evaluator -> evaluator.resolve(attributes, context))
                        .orElse(ResolvedFeaturePortrayal.EMPTY);
        return new ResolvedFeaturePortrayal(
                resolveRole(SymbolRole.MARKER, attributes, rules),
                resolveRole(SymbolRole.LINE, attributes, rules),
                resolveRole(SymbolRole.FILL, attributes, rules));
    }

    private Optional<Symbol> resolveRole(
            SymbolRole role, Map<String, Object> attributes, ResolvedFeaturePortrayal ruleResult) {
        SymbolSelector selector = selectors.get(role);
        if (selector == null) {
            return Optional.empty();
        }
        return selector instanceof RuleSymbolSelector
                ? ruleResult.forRole(role)
                : resolveOrdinary(selector, attributes);
    }

    private List<String> requiredPaintAttributes(boolean includeLabel) {
        if (!includeLabel
                || pointLabel.isEmpty()
                || !(pointLabel.orElseThrow().textSource() instanceof TextAttribute text)) {
            return requiredSymbolAttributes;
        }
        if (requiredSymbolAttributes.contains(text.attribute())) {
            return requiredSymbolAttributes;
        }
        ArrayList<String> attributes = new ArrayList<>(requiredSymbolAttributes);
        attributes.add(text.attribute());
        return List.copyOf(attributes);
    }

    private static final class GraduatedTable {
        private final BigDecimal[] thresholds;
        private final Symbol[] symbols;

        private GraduatedTable(BigDecimal[] thresholds, Symbol[] symbols) {
            this.thresholds = thresholds;
            this.symbols = symbols;
        }

        private Symbol greatestLowerBound(BigDecimal value) {
            int low = 0;
            int high = thresholds.length - 1;
            int selected = -1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (thresholds[middle].compareTo(value) <= 0) {
                    selected = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return selected < 0 ? null : symbols[selected];
        }
    }
}
