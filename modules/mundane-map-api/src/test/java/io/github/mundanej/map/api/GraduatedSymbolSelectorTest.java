package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GraduatedSymbolSelectorTest {
    private static final Symbol MARKER = new TestSymbol(SymbolRole.MARKER, "marker");
    private static final Symbol OTHER_MARKER = new TestSymbol(SymbolRole.MARKER, "other-marker");
    private static final Symbol LINE = new TestSymbol(SymbolRole.LINE, "line");

    @Test
    void stepNormalizesDecimalAndSelectorDefensivelyCopiesIncreasingOrder() {
        GraduatedSymbolStep normalized = new GraduatedSymbolStep(new BigDecimal("1.00"), MARKER);
        assertEquals(new BigDecimal("1"), normalized.lowerInclusive());
        assertEquals(
                BigDecimal.ZERO,
                new GraduatedSymbolStep(new BigDecimal("0.000"), MARKER).lowerInclusive());

        ArrayList<GraduatedSymbolStep> mutable =
                new ArrayList<>(List.of(step("0", MARKER), step("10", OTHER_MARKER)));
        GraduatedSymbolSelector selector =
                new GraduatedSymbolSelector("value", mutable, Optional.of(MARKER));
        mutable.clear();

        assertEquals(2, selector.steps().size());
        assertEquals(SymbolRole.MARKER, selector.role());
    }

    @Test
    void selectorRejectsEmptyUnorderedDuplicateMixedRoleAndExcessiveSteps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraduatedSymbolSelector("value", List.of(), Optional.of(MARKER)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraduatedSymbolSelector(
                                "value",
                                List.of(step("10", MARKER), step("0", OTHER_MARKER)),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraduatedSymbolSelector(
                                "value",
                                List.of(step("1", MARKER), step("1.00", OTHER_MARKER)),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraduatedSymbolSelector(
                                "value",
                                List.of(step("0", MARKER), step("10", LINE)),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraduatedSymbolSelector(
                                "value", List.of(step("0", MARKER)), Optional.of(LINE)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraduatedSymbolSelector(
                                "value",
                                IntStream.rangeClosed(0, GraduatedSymbolSelector.MAXIMUM_STEPS)
                                        .mapToObj(index -> step(Integer.toString(index), MARKER))
                                        .toList(),
                                Optional.empty()));
    }

    @Test
    void selectorAcceptsExactMaximumDistinctSteps() {
        List<GraduatedSymbolStep> maximum =
                IntStream.range(0, GraduatedSymbolSelector.MAXIMUM_STEPS)
                        .mapToObj(index -> step(Integer.toString(index), MARKER))
                        .toList();

        assertEquals(
                GraduatedSymbolSelector.MAXIMUM_STEPS,
                new GraduatedSymbolSelector("value", maximum, Optional.empty()).steps().size());
    }

    private static GraduatedSymbolStep step(String lower, Symbol symbol) {
        return new GraduatedSymbolStep(new BigDecimal(lower), symbol);
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
