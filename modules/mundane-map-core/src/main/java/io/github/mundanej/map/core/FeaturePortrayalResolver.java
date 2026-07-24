package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeValueCandidate;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FilteredSymbolSelector;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.InterpolatedSymbolSelector;
import io.github.mundanej.map.api.InterpolatedSymbolStop;
import io.github.mundanej.map.api.InterpolationInput;
import io.github.mundanej.map.api.LiteralLabelText;
import io.github.mundanej.map.api.OmittedSymbol;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.ResolvedFeaturePortrayal;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.RuleSymbolSelector;
import io.github.mundanej.map.api.StringifiedTextAttribute;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolSelector;
import io.github.mundanej.map.api.TextAttribute;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.math.MathContext;
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
    private final Map<SymbolRole, PortrayalPredicate> filters;
    private final Map<SymbolRole, Map<ThematicValue, Symbol>> categorical;
    private final Map<SymbolRole, GraduatedTable> graduated;
    private final Map<SymbolRole, InterpolationTable> interpolated;
    private final List<String> requiredSymbolAttributes;
    private final List<Symbol> reachableSymbols;
    private final Optional<PointLabelProfile> pointLabel;
    private final Optional<RulePortrayalEvaluator> ruleEvaluator;

    private FeaturePortrayalResolver(FeaturePortrayal portrayal) {
        this.portrayal = Objects.requireNonNull(portrayal, "portrayal");
        EnumMap<SymbolRole, SymbolSelector> byRole = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, PortrayalPredicate> guards = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, Map<ThematicValue, Symbol>> compiled = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, GraduatedTable> graduatedCompiled = new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, InterpolationTable> interpolationCompiled =
                new EnumMap<>(SymbolRole.class);
        EnumMap<SymbolRole, List<Symbol>> symbolsByRole = new EnumMap<>(SymbolRole.class);
        Set<String> attributes = new LinkedHashSet<>();
        RulePortrayalPlan sharedRulePlan = null;
        for (SymbolSelector declared : portrayal.selectors()) {
            SymbolSelector selector = declared;
            if (selector instanceof FilteredSymbolSelector filtered) {
                guards.put(selector.role(), filtered.predicate());
                RulePortrayalEvaluator.collect(filtered.predicate(), attributes);
                selector = filtered.delegate();
            }
            byRole.put(selector.role(), selector);
            if (selector instanceof FixedSymbolSelector fixed) {
                symbolsByRole
                        .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                        .add(fixed.symbol());
                continue;
            }
            if (selector instanceof CategoricalSymbolSelector categories) {
                collectInputAttributes(categories.attribute(), categories.conversion(), attributes);
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
                                                        categories.role(),
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
            if (selector instanceof InterpolatedSymbolSelector interpolation) {
                interpolation
                        .attribute()
                        .ifPresent(
                                attribute ->
                                        collectInputAttributes(
                                                attribute, interpolation.conversion(), attributes));
                BigDecimal[] inputs = new BigDecimal[interpolation.stops().size()];
                Symbol[] endpoints = new Symbol[interpolation.stops().size()];
                for (int index = 0; index < interpolation.stops().size(); index++) {
                    InterpolatedSymbolStop stop = interpolation.stops().get(index);
                    inputs[index] = stop.input();
                    endpoints[index] = stop.symbol();
                    symbolsByRole
                            .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                            .add(stop.symbol());
                    if (index > 0) {
                        SymbolInterpolation.interpolate(
                                endpoints[index - 1], endpoints[index], 0.5);
                    }
                }
                symbolsByRole
                        .computeIfAbsent(selector.role(), ignored -> new ArrayList<>())
                        .add(interpolation.fallback());
                interpolationCompiled.put(
                        interpolation.role(), new InterpolationTable(inputs, endpoints));
                continue;
            }
            GraduatedSymbolSelector ranges = (GraduatedSymbolSelector) selector;
            if (ranges.input() == InterpolationInput.ATTRIBUTE) {
                collectInputAttributes(ranges.attribute(), ranges.conversion(), attributes);
            }
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
                                                    ranges.role(), ignored -> new ArrayList<>())
                                            .add(symbol));
            graduatedCompiled.put(ranges.role(), new GraduatedTable(thresholds, selected));
        }
        RulePortrayalEvaluator compiledRules =
                sharedRulePlan == null ? null : new RulePortrayalEvaluator(sharedRulePlan);
        if (compiledRules != null) {
            attributes.addAll(compiledRules.requiredAttributes());
            for (SymbolSelector selector : byRole.values()) {
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
        this.filters = Collections.unmodifiableMap(guards);
        this.categorical = Collections.unmodifiableMap(compiled);
        this.graduated = Collections.unmodifiableMap(graduatedCompiled);
        this.interpolated = Collections.unmodifiableMap(interpolationCompiled);
        this.requiredSymbolAttributes = List.copyOf(attributes);
        this.reachableSymbols =
                symbols.stream().filter(symbol -> !(symbol instanceof OmittedSymbol)).toList();
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
        return List.copyOf(reachableSymbols);
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
     * Returns whether this portrayal selects a symbol from explicit zoom.
     *
     * @return true for a zoom-driven interpolation selector
     */
    public boolean requiresZoomContext() {
        return selectors.values().stream()
                .anyMatch(
                        selector ->
                                (selector instanceof InterpolatedSymbolSelector interpolation
                                                && interpolation.input() == InterpolationInput.ZOOM)
                                        || (selector instanceof GraduatedSymbolSelector graduated
                                                && graduated.input() == InterpolationInput.ZOOM));
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
        if (pointLabel.orElseThrow().textSource() instanceof LiteralLabelText literal) {
            return Optional.of(literal.text());
        }
        String attribute =
                pointLabel.orElseThrow().textSource() instanceof TextAttribute text
                        ? text.attribute()
                        : ((StringifiedTextAttribute) pointLabel.orElseThrow().textSource())
                                .attribute();
        Object value = attributes.get(attribute);
        String text =
                pointLabel.orElseThrow().textSource() instanceof StringifiedTextAttribute
                        ? LabelTextValues.stringify(
                                attributes.getOrDefault(
                                        attribute,
                                        io.github.mundanej.map.api.AttributeNull.INSTANCE))
                        : value instanceof String exact ? exact : "";
        return text.isBlank() ? Optional.empty() : Optional.of(text);
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
        if (selector instanceof RuleSymbolSelector || filters.containsKey(role)) {
            return resolveAll(attributes, PortrayalEvaluationContext.UNSCALED).forRole(role);
        }
        return resolveOrdinary(selector, attributes, PortrayalEvaluationContext.UNSCALED);
    }

    private Optional<Symbol> resolveOrdinary(
            SymbolSelector selector,
            Map<String, Object> attributes,
            PortrayalEvaluationContext context) {
        SymbolRole role = selector.role();
        if (selector instanceof FixedSymbolSelector fixed) {
            return resolved(fixed.symbol());
        }
        if (selector instanceof CategoricalSymbolSelector categories) {
            if (!attributes.containsKey(categories.attribute())) {
                if (!categories.missingAsNull()) {
                    return categories.fallback().flatMap(FeaturePortrayalResolver::resolved);
                }
            }
            Object input =
                    attributes.containsKey(categories.attribute())
                            ? attributes.get(categories.attribute())
                            : io.github.mundanej.map.api.AttributeNull.INSTANCE;
            Optional<ThematicValue> value =
                    AttributeValueConversions.convert(input, categories.conversion(), attributes);
            if (value.isEmpty()) {
                return categories.fallback().flatMap(FeaturePortrayalResolver::resolved);
            }
            Symbol matched = categorical.get(role).get(value.orElseThrow());
            return matched == null
                    ? categories.fallback().flatMap(FeaturePortrayalResolver::resolved)
                    : resolved(matched);
        }
        if (selector instanceof InterpolatedSymbolSelector interpolation) {
            BigDecimal input =
                    interpolationInput(
                            interpolation.input(),
                            interpolation.attribute().orElse(""),
                            interpolation.conversion(),
                            attributes,
                            context);
            return input == null
                    ? resolved(interpolation.fallback())
                    : resolved(interpolated.get(role).select(input));
        }
        GraduatedSymbolSelector ranges = (GraduatedSymbolSelector) selector;
        BigDecimal value =
                interpolationInput(
                        ranges.input(),
                        ranges.attribute(),
                        ranges.conversion(),
                        attributes,
                        context);
        if (value == null) {
            return ranges.invalidFallback().flatMap(FeaturePortrayalResolver::resolved);
        }
        Symbol matched = graduated.get(role).greatestLowerBound(value);
        return matched == null
                ? ranges.fallback().flatMap(FeaturePortrayalResolver::resolved)
                : resolved(matched);
    }

    private static Optional<Symbol> resolved(Symbol symbol) {
        return symbol instanceof OmittedSymbol ? Optional.empty() : Optional.of(symbol);
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
        boolean rulesNeeded =
                selectors.entrySet().stream()
                        .filter(entry -> entry.getValue() instanceof RuleSymbolSelector)
                        .anyMatch(
                                entry -> {
                                    PortrayalPredicate filter = filters.get(entry.getKey());
                                    return filter == null
                                            || RulePortrayalEvaluator.test(
                                                    filter, attributes, context);
                                });
        ResolvedFeaturePortrayal rules =
                rulesNeeded
                        ? ruleEvaluator
                                .map(evaluator -> evaluator.resolve(attributes, context))
                                .orElse(ResolvedFeaturePortrayal.EMPTY)
                        : ResolvedFeaturePortrayal.EMPTY;
        return new ResolvedFeaturePortrayal(
                resolveRole(SymbolRole.MARKER, attributes, context, rules),
                resolveRole(SymbolRole.LINE, attributes, context, rules),
                resolveRole(SymbolRole.FILL, attributes, context, rules));
    }

    private Optional<Symbol> resolveRole(
            SymbolRole role,
            Map<String, Object> attributes,
            PortrayalEvaluationContext context,
            ResolvedFeaturePortrayal ruleResult) {
        SymbolSelector selector = selectors.get(role);
        if (selector == null) {
            return Optional.empty();
        }
        PortrayalPredicate filter = filters.get(role);
        if (filter != null && !RulePortrayalEvaluator.test(filter, attributes, context)) {
            return Optional.empty();
        }
        return selector instanceof RuleSymbolSelector
                ? ruleResult.forRole(role)
                : resolveOrdinary(selector, attributes, context);
    }

    private static BigDecimal interpolationInput(
            InterpolationInput input,
            String attribute,
            io.github.mundanej.map.api.AttributeValueConversion conversion,
            Map<String, Object> attributes,
            PortrayalEvaluationContext context) {
        if (input == InterpolationInput.ZOOM) {
            return context.zoomLevel().isPresent()
                    ? BigDecimal.valueOf(context.zoomLevel().orElseThrow())
                    : null;
        }
        if (!attributes.containsKey(attribute)) {
            if (conversion.operation()
                    == io.github.mundanej.map.api.AttributeValueConversion.Operation.IDENTITY) {
                return null;
            }
        }
        Object primary =
                attributes.getOrDefault(
                        attribute, io.github.mundanej.map.api.AttributeNull.INSTANCE);
        Optional<ThematicValue> value =
                AttributeValueConversions.convert(primary, conversion, attributes);
        return value.isPresent() && value.orElseThrow().kind() == ThematicValue.Kind.NUMERIC
                ? (BigDecimal) value.orElseThrow().value()
                : null;
    }

    private static void collectInputAttributes(
            String primary,
            io.github.mundanej.map.api.AttributeValueConversion conversion,
            Set<String> attributes) {
        if (conversion.candidates().isEmpty()) {
            attributes.add(primary);
            return;
        }
        for (AttributeValueCandidate candidate : conversion.candidates()) {
            if (candidate instanceof AttributeValueCandidate.Attribute attribute) {
                attributes.add(attribute.name());
            }
        }
    }

    private List<String> requiredPaintAttributes(boolean includeLabel) {
        if (!includeLabel || pointLabel.isEmpty()) {
            return requiredSymbolAttributes;
        }
        String attribute =
                pointLabel.orElseThrow().textSource() instanceof TextAttribute text
                        ? text.attribute()
                        : pointLabel.orElseThrow().textSource()
                                        instanceof StringifiedTextAttribute converted
                                ? converted.attribute()
                                : null;
        if (attribute == null || requiredSymbolAttributes.contains(attribute)) {
            return requiredSymbolAttributes;
        }
        ArrayList<String> attributes = new ArrayList<>(requiredSymbolAttributes);
        attributes.add(attribute);
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

    private static final class InterpolationTable {
        private final BigDecimal[] inputs;
        private final Symbol[] symbols;

        private InterpolationTable(BigDecimal[] inputs, Symbol[] symbols) {
            this.inputs = inputs;
            this.symbols = symbols;
        }

        private Symbol select(BigDecimal value) {
            if (value.compareTo(inputs[0]) <= 0) {
                return symbols[0];
            }
            int last = inputs.length - 1;
            if (value.compareTo(inputs[last]) >= 0) {
                return symbols[last];
            }
            int low = 0;
            int high = last;
            while (low + 1 < high) {
                int middle = (low + high) >>> 1;
                if (inputs[middle].compareTo(value) <= 0) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            double fraction =
                    value.subtract(inputs[low])
                            .divide(inputs[high].subtract(inputs[low]), MathContext.DECIMAL64)
                            .doubleValue();
            return SymbolInterpolation.interpolate(symbols[low], symbols[high], fraction);
        }
    }
}
