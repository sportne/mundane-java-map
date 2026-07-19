package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.ThematicValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeaturePortrayalResolverTest {
    private static final Symbol RED = new TestSymbol(SymbolRole.MARKER, "red");
    private static final Symbol BLUE = new TestSymbol(SymbolRole.MARKER, "blue");
    private static final Symbol FALLBACK = new TestSymbol(SymbolRole.MARKER, "fallback");
    private static final Symbol LINE = new TestSymbol(SymbolRole.LINE, "line");
    private static final Symbol NULL_MARKER = new TestSymbol(SymbolRole.MARKER, "null-marker");

    @Test
    void resolvesExactSupportedCategoriesAndExplicitFallbacks() {
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        new FeaturePortrayal(
                                Optional.of(
                                        new CategoricalSymbolSelector(
                                                "kind",
                                                List.of(
                                                        rule(ThematicValue.text("red"), RED),
                                                        rule(ThematicValue.numeric(1), BLUE),
                                                        rule(
                                                                ThematicValue.nullValue(),
                                                                NULL_MARKER)),
                                                Optional.of(FALLBACK))),
                                Optional.empty(),
                                Optional.empty()));

        assertEquals(Optional.of(RED), resolver.resolve(SymbolRole.MARKER, Map.of("kind", "red")));
        assertEquals(Optional.of(BLUE), resolver.resolve(SymbolRole.MARKER, Map.of("kind", 1.0)));
        assertEquals(
                "test.null-marker",
                resolver.resolve(SymbolRole.MARKER, Map.of("kind", AttributeNull.INSTANCE))
                        .orElseThrow()
                        .rendererKey()
                        .value());
        assertEquals(Optional.of(FALLBACK), resolver.resolve(SymbolRole.MARKER, Map.of()));
        assertEquals(
                Optional.of(FALLBACK),
                resolver.resolve(
                        SymbolRole.MARKER, Map.of("kind", new AttributeBytes(new byte[] {1}))));
        assertEquals(
                Optional.of(FALLBACK), resolver.resolve(SymbolRole.MARKER, Map.of("kind", "x")));
        assertEquals(Optional.empty(), resolver.resolve(SymbolRole.LINE, Map.of("kind", "red")));
    }

    @Test
    void compilesRequiredAttributesAndReachableSymbolsInRoleOrder() {
        CategoricalSymbolSelector marker =
                new CategoricalSymbolSelector(
                        "shared",
                        List.of(rule(ThematicValue.text("red"), RED)),
                        Optional.of(FALLBACK));
        CategoricalSymbolSelector line =
                new CategoricalSymbolSelector(
                        "shared",
                        List.of(rule(ThematicValue.text("line"), LINE)),
                        Optional.empty());
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        new FeaturePortrayal(
                                Optional.of(marker), Optional.of(line), Optional.empty()));

        assertEquals(List.of("shared"), resolver.requiredSymbolAttributes());
        assertEquals(List.of(RED, FALLBACK, LINE), resolver.reachableSymbols());
    }

    @Test
    void omissionRemainsDistinctFromFallback() {
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        FeaturePortrayal.markers(
                                new CategoricalSymbolSelector(
                                        "kind",
                                        List.of(rule(ThematicValue.text("red"), RED)),
                                        Optional.empty())));

        assertEquals(Optional.empty(), resolver.resolve(SymbolRole.MARKER, Map.of()));
        assertEquals(Optional.empty(), resolver.resolve(SymbolRole.MARKER, Map.of("kind", "blue")));
    }

    private static CategoricalSymbolRule rule(ThematicValue value, Symbol symbol) {
        return new CategoricalSymbolRule(value, symbol);
    }

    private record TestSymbol(SymbolRole role, SymbolRendererKey rendererKey) implements Symbol {
        private TestSymbol(SymbolRole role, String key) {
            this(role, new SymbolRendererKey("test." + key));
        }

        @Override
        public double opacity() {
            return 1;
        }
    }
}
