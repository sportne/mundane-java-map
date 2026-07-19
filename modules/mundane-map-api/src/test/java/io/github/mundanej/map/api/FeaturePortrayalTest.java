package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeaturePortrayalTest {
    private static final Symbol MARKER = new TestSymbol(SymbolRole.MARKER, "marker");
    private static final Symbol OTHER_MARKER = new TestSymbol(SymbolRole.MARKER, "other");
    private static final Symbol LINE = new TestSymbol(SymbolRole.LINE, "line");
    private static final Symbol FILL = new TestSymbol(SymbolRole.FILL, "fill");

    @Test
    void thematicValuesHaveExactClosedNormalizationSemantics() {
        assertEquals(ThematicValue.numeric(1), ThematicValue.numeric(1.0));
        assertEquals(ThematicValue.numeric(1), ThematicValue.numeric(new BigDecimal("1.00")));
        assertEquals(ThematicValue.numeric(0), ThematicValue.numeric(new BigDecimal("0.000")));
        assertEquals(ThematicValue.text("A"), ThematicValue.fromAttribute("A").orElseThrow());
        assertEquals(ThematicValue.logical(true), ThematicValue.fromAttribute(true).orElseThrow());
        assertEquals(
                ThematicValue.date(LocalDate.of(2025, 3, 4)),
                ThematicValue.fromAttribute(LocalDate.of(2025, 3, 4)).orElseThrow());
        assertEquals(
                ThematicValue.nullValue(),
                ThematicValue.fromAttribute(AttributeNull.INSTANCE).orElseThrow());
        assertNotEquals(ThematicValue.text("1"), ThematicValue.numeric(1));
        assertEquals(
                Optional.empty(), ThematicValue.fromAttribute(new AttributeBytes(new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> ThematicValue.numeric(Double.NaN));
    }

    @Test
    void categoricalSelectorCopiesRulesAndRejectsInvalidProfiles() {
        ArrayList<CategoricalSymbolRule> mutable =
                new ArrayList<>(List.of(rule(ThematicValue.text("a"), MARKER)));
        CategoricalSymbolSelector selector =
                new CategoricalSymbolSelector("kind", mutable, Optional.of(OTHER_MARKER));
        mutable.clear();

        assertEquals(1, selector.rules().size());
        assertEquals(SymbolRole.MARKER, selector.role());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CategoricalSymbolSelector(
                                "kind",
                                List.of(
                                        rule(ThematicValue.numeric(1), MARKER),
                                        rule(ThematicValue.numeric(1.0), OTHER_MARKER)),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CategoricalSymbolSelector("kind", List.of(), Optional.of(MARKER)));

        List<CategoricalSymbolRule> maximum =
                java.util.stream.IntStream.range(0, CategoricalSymbolSelector.MAXIMUM_RULES)
                        .mapToObj(index -> rule(ThematicValue.text("category-" + index), MARKER))
                        .toList();
        assertEquals(
                CategoricalSymbolSelector.MAXIMUM_RULES,
                new CategoricalSymbolSelector("x".repeat(256), maximum, Optional.empty())
                        .rules()
                        .size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CategoricalSymbolSelector("x".repeat(257), maximum, Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CategoricalSymbolSelector(
                                "kind",
                                List.of(rule(ThematicValue.text("a"), MARKER)),
                                Optional.of(LINE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CategoricalSymbolSelector(" ", mutable, Optional.of(MARKER)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CategoricalSymbolSelector(
                                "kind",
                                java.util.Collections.nCopies(
                                        CategoricalSymbolSelector.MAXIMUM_RULES + 1,
                                        rule(ThematicValue.text("a"), MARKER)),
                                Optional.empty()));
    }

    @Test
    void portrayalChecksPositionsAndRetainsClosedSelectorOrder() {
        FixedSymbolSelector marker = new FixedSymbolSelector(MARKER);
        FeaturePortrayal portrayal =
                new FeaturePortrayal(
                        Optional.of(marker),
                        Optional.of(new FixedSymbolSelector(LINE)),
                        Optional.of(new FixedSymbolSelector(FILL)));

        assertEquals(
                List.of(SymbolRole.MARKER, SymbolRole.LINE, SymbolRole.FILL),
                portrayal.selectors().stream().map(SymbolSelector::role).toList());
        assertEquals(portrayal, FeaturePortrayal.fixed(MARKER, LINE, FILL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FeaturePortrayal(
                                Optional.of(new FixedSymbolSelector(LINE)),
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeaturePortrayal(Optional.empty(), Optional.empty(), Optional.empty()));
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
