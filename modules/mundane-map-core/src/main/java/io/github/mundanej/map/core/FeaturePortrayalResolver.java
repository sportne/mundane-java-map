package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolSelector;
import io.github.mundanej.map.api.ThematicValue;
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
    private final List<String> requiredSymbolAttributes;
    private final List<Symbol> reachableSymbols;

    private FeaturePortrayalResolver(FeaturePortrayal portrayal) {
        this.portrayal = Objects.requireNonNull(portrayal, "portrayal");
        EnumMap<SymbolRole, SymbolSelector> byRole = new EnumMap<>(SymbolRole.class);
        Map<CategoricalSymbolSelector, Map<ThematicValue, Symbol>> compiled = new LinkedHashMap<>();
        Set<String> attributes = new LinkedHashSet<>();
        List<Symbol> symbols = new ArrayList<>();
        for (SymbolSelector selector : portrayal.selectors()) {
            byRole.put(selector.role(), selector);
            if (selector instanceof FixedSymbolSelector fixed) {
                symbols.add(fixed.symbol());
                continue;
            }
            CategoricalSymbolSelector categories = (CategoricalSymbolSelector) selector;
            attributes.add(categories.attribute());
            Map<ThematicValue, Symbol> lookup = new LinkedHashMap<>();
            for (CategoricalSymbolRule rule : categories.rules()) {
                lookup.put(rule.value(), rule.symbol());
                symbols.add(rule.symbol());
            }
            categories.fallback().ifPresent(symbols::add);
            compiled.put(categories, Collections.unmodifiableMap(lookup));
        }
        this.selectors = Collections.unmodifiableMap(byRole);
        this.categorical = Collections.unmodifiableMap(compiled);
        this.requiredSymbolAttributes = List.copyOf(attributes);
        this.reachableSymbols = List.copyOf(symbols);
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
        CategoricalSymbolSelector categories = (CategoricalSymbolSelector) selector;
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
}
