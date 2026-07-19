package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.PointLabelProfile;
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
    private final Map<CategoricalSymbolSelector, Map<ThematicValue, Symbol>> categorical;
    private final Map<GraduatedSymbolSelector, GraduatedTable> graduated;
    private final List<String> requiredSymbolAttributes;
    private final List<Symbol> reachableSymbols;
    private final Optional<PointLabelProfile> pointLabel;

    private FeaturePortrayalResolver(FeaturePortrayal portrayal) {
        this.portrayal = Objects.requireNonNull(portrayal, "portrayal");
        EnumMap<SymbolRole, SymbolSelector> byRole = new EnumMap<>(SymbolRole.class);
        Map<CategoricalSymbolSelector, Map<ThematicValue, Symbol>> compiled = new LinkedHashMap<>();
        Map<GraduatedSymbolSelector, GraduatedTable> graduatedCompiled = new LinkedHashMap<>();
        Set<String> attributes = new LinkedHashSet<>();
        List<Symbol> symbols = new ArrayList<>();
        for (SymbolSelector selector : portrayal.selectors()) {
            byRole.put(selector.role(), selector);
            if (selector instanceof FixedSymbolSelector fixed) {
                symbols.add(fixed.symbol());
                continue;
            }
            if (selector instanceof CategoricalSymbolSelector categories) {
                attributes.add(categories.attribute());
                Map<ThematicValue, Symbol> lookup = new LinkedHashMap<>();
                for (CategoricalSymbolRule rule : categories.rules()) {
                    lookup.put(rule.value(), rule.symbol());
                    symbols.add(rule.symbol());
                }
                categories.fallback().ifPresent(symbols::add);
                compiled.put(categories, Collections.unmodifiableMap(lookup));
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
                symbols.add(step.symbol());
            }
            ranges.fallback().ifPresent(symbols::add);
            graduatedCompiled.put(ranges, new GraduatedTable(thresholds, selected));
        }
        this.selectors = Collections.unmodifiableMap(byRole);
        this.categorical = Collections.unmodifiableMap(compiled);
        this.graduated = Collections.unmodifiableMap(graduatedCompiled);
        this.requiredSymbolAttributes = List.copyOf(attributes);
        this.reachableSymbols = List.copyOf(symbols);
        this.pointLabel = portrayal.pointLabel();
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
            Symbol matched = categorical.get(categories).get(value.orElseThrow());
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
                graduated.get(ranges).greatestLowerBound((BigDecimal) value.orElseThrow().value());
        return matched == null ? ranges.fallback() : Optional.of(matched);
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
