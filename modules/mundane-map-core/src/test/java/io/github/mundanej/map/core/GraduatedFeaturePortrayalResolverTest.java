package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraduatedFeaturePortrayalResolverTest {
    private static final Symbol LOW = new TestSymbol(SymbolRole.MARKER, "low");
    private static final Symbol HIGH = new TestSymbol(SymbolRole.MARKER, "high");
    private static final Symbol FALLBACK = new TestSymbol(SymbolRole.MARKER, "fallback");
    private static final Symbol LINE = new TestSymbol(SymbolRole.LINE, "line");
    private static final Symbol FILL_LOW = new TestSymbol(SymbolRole.FILL, "fill-low");
    private static final Symbol FILL_HIGH = new TestSymbol(SymbolRole.FILL, "fill-high");

    @Test
    void choosesGreatestLowerInclusiveThresholdAcrossCanonicalNumericForms() {
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        FeaturePortrayal.markers(
                                graduated(SymbolRole.MARKER, Optional.of(FALLBACK))));

        assertEquals(Optional.of(FALLBACK), resolver.resolve(SymbolRole.MARKER, Map.of()));
        assertEquals(
                Optional.of(FALLBACK), resolver.resolve(SymbolRole.MARKER, Map.of("value", -1L)));
        assertEquals(Optional.of(LOW), resolver.resolve(SymbolRole.MARKER, Map.of("value", 0L)));
        assertEquals(Optional.of(LOW), resolver.resolve(SymbolRole.MARKER, Map.of("value", 9.999)));
        assertEquals(
                Optional.of(HIGH),
                resolver.resolve(SymbolRole.MARKER, Map.of("value", new BigDecimal("10.00"))));
        assertEquals(Optional.of(HIGH), resolver.resolve(SymbolRole.MARKER, Map.of("value", 100L)));
        assertEquals(
                Optional.of(FALLBACK), resolver.resolve(SymbolRole.MARKER, Map.of("value", "10")));
        assertEquals(
                Optional.of(FALLBACK),
                resolver.resolve(SymbolRole.MARKER, Map.of("value", AttributeNull.INSTANCE)));
    }

    @Test
    void belowRangeAndWrongTypeCanOmitWithoutCoercion() {
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        FeaturePortrayal.markers(graduated(SymbolRole.MARKER, Optional.empty())));

        assertEquals(Optional.empty(), resolver.resolve(SymbolRole.MARKER, Map.of("value", -1L)));
        assertEquals(Optional.empty(), resolver.resolve(SymbolRole.MARKER, Map.of("value", "100")));
    }

    @Test
    void requiredAttributesDeduplicateInMarkerLineFillOrder() {
        GraduatedSymbolSelector marker = graduated(SymbolRole.MARKER, Optional.empty());
        CategoricalSymbolSelector line =
                new CategoricalSymbolSelector(
                        "value",
                        List.of(new CategoricalSymbolRule(ThematicValue.text("line"), LINE)),
                        Optional.empty());
        GraduatedSymbolSelector fill =
                new GraduatedSymbolSelector(
                        "fillValue",
                        List.of(
                                new GraduatedSymbolStep(BigDecimal.ZERO, FILL_LOW),
                                new GraduatedSymbolStep(BigDecimal.TEN, FILL_HIGH)),
                        Optional.empty());
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        new FeaturePortrayal(
                                Optional.of(marker), Optional.of(line), Optional.of(fill)));

        assertEquals(List.of("value", "fillValue"), resolver.requiredSymbolAttributes());
        assertEquals(List.of(LOW, HIGH, LINE, FILL_LOW, FILL_HIGH), resolver.reachableSymbols());
    }

    private static GraduatedSymbolSelector graduated(
            SymbolRole role, Optional<? extends Symbol> fallback) {
        Symbol low = role == SymbolRole.MARKER ? LOW : FILL_LOW;
        Symbol high = role == SymbolRole.MARKER ? HIGH : FILL_HIGH;
        return new GraduatedSymbolSelector(
                "value",
                List.of(
                        new GraduatedSymbolStep(BigDecimal.ZERO, low),
                        new GraduatedSymbolStep(BigDecimal.TEN, high)),
                fallback);
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
