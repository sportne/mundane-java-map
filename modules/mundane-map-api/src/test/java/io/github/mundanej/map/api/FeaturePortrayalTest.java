package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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
        assertEquals("kind", selector.attribute());
        assertEquals(Optional.of(OTHER_MARKER), selector.fallback());
        assertEquals(AttributeValueConversion.IDENTITY, selector.conversion());
        assertEquals(false, selector.missingAsNull());
        assertEquals(SymbolRole.MARKER, selector.role());
        assertEquals(
                selector,
                new CategoricalSymbolSelector(
                        "kind",
                        List.of(rule(ThematicValue.text("a"), MARKER)),
                        Optional.of(OTHER_MARKER)));
        assertEquals(
                selector.hashCode(),
                new CategoricalSymbolSelector(
                                "kind",
                                List.of(rule(ThematicValue.text("a"), MARKER)),
                                Optional.of(OTHER_MARKER))
                        .hashCode());
        assertEquals(
                "CategoricalSymbolSelector[attribute=kind, rules="
                        + selector.rules()
                        + ", fallback=Optional["
                        + OTHER_MARKER
                        + "]]",
                selector.toString());
        CategoricalSymbolSelector converted =
                CategoricalSymbolSelector.expressionInput(
                        "kind",
                        List.of(rule(ThematicValue.nullValue(), MARKER)),
                        Optional.empty(),
                        AttributeValueConversion.TO_STRING);
        assertEquals(true, converted.missingAsNull());
        assertNotEquals(selector, converted);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CategoricalSymbolSelector(
                                "kind",
                                List.of(
                                        rule(ThematicValue.numeric(1), MARKER),
                                        rule(ThematicValue.numeric(1.0), OTHER_MARKER)),
                                Optional.empty()));
        List<CategoricalSymbolRule> nullRule = new ArrayList<>();
        nullRule.add(null);
        assertThrows(
                NullPointerException.class,
                () -> new CategoricalSymbolSelector("kind", nullRule, Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> new CategoricalSymbolSelector("kind", mutable, null));
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

    @Test
    void rulePlansExposeRolesScaleAndStableValueSemantics() {
        PortrayalRule markerRule =
                new PortrayalRule(
                        Optional.of("primary"),
                        new ScaleInterval(OptionalDouble.of(10), OptionalDouble.of(20)),
                        Optional.of(new PortrayalPredicate.Constant(true)),
                        false,
                        List.of(MARKER),
                        List.of(),
                        List.of());
        PortrayalRule lineElse =
                new PortrayalRule(
                        Optional.empty(),
                        new ScaleInterval(OptionalDouble.of(20), OptionalDouble.empty()),
                        Optional.empty(),
                        true,
                        List.of(),
                        List.of(LINE),
                        List.of());
        RulePortrayalPlan plan = new RulePortrayalPlan(List.of(markerRule, lineElse));
        RulePortrayalPlan equal = new RulePortrayalPlan(List.of(markerRule, lineElse));

        assertEquals(List.of(markerRule, lineElse), plan.rules());
        assertEquals(
                List.of(SymbolRole.MARKER, SymbolRole.LINE),
                plan.portrayal().selectors().stream().map(SymbolSelector::role).toList());
        assertEquals(plan, equal);
        assertEquals(plan.hashCode(), equal.hashCode());
        assertEquals("RulePortrayalPlan[rules=" + plan.rules() + ']', plan.toString());
        assertEquals(true, plan.requiresScaleContext());

        PortrayalRule overlappingElse =
                new PortrayalRule(
                        Optional.empty(),
                        new ScaleInterval(OptionalDouble.of(19), OptionalDouble.of(30)),
                        Optional.empty(),
                        true,
                        List.of(),
                        List.of(),
                        List.of(FILL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RulePortrayalPlan(List.of(lineElse, overlappingElse)));
        assertThrows(IllegalArgumentException.class, () -> new RulePortrayalPlan(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PortrayalRule(
                                Optional.empty(),
                                ScaleInterval.ALL,
                                Optional.of(new PortrayalPredicate.Constant(true)),
                                true,
                                List.of(MARKER),
                                List.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PortrayalRule(
                                Optional.of(" invalid "),
                                ScaleInterval.ALL,
                                Optional.empty(),
                                false,
                                List.of(MARKER),
                                List.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PortrayalRule(
                                Optional.empty(),
                                ScaleInterval.ALL,
                                Optional.empty(),
                                false,
                                List.of(LINE),
                                List.of(),
                                List.of()));
    }

    @Test
    void interpolationSelectorsValidateAndDescribeTheirExactInputs() {
        InterpolatedSymbolStop low = new InterpolatedSymbolStop(BigDecimal.ZERO, LINE);
        InterpolatedSymbolStop high = new InterpolatedSymbolStop(BigDecimal.TEN, LINE);
        AttributeValueConversion conversion =
                AttributeValueConversion.toNumber(
                        List.of(new AttributeValueCandidate.Attribute("actual")));
        InterpolatedSymbolSelector attribute =
                InterpolatedSymbolSelector.expressionInput(
                        "nominal", List.of(low, high), LINE, conversion);
        InterpolatedSymbolSelector equal =
                InterpolatedSymbolSelector.expressionInput(
                        "nominal", List.of(low, high), LINE, conversion);
        InterpolatedSymbolSelector zoom = InterpolatedSymbolSelector.zoom(List.of(low, high), LINE);

        assertEquals(InterpolationInput.ATTRIBUTE, attribute.input());
        assertEquals(Optional.of("nominal"), attribute.attribute());
        assertEquals(List.of(low, high), attribute.stops());
        assertEquals(LINE, attribute.fallback());
        assertEquals(conversion, attribute.conversion());
        assertEquals(SymbolRole.LINE, attribute.role());
        assertEquals(attribute, equal);
        assertEquals(attribute.hashCode(), equal.hashCode());
        assertNotEquals(attribute, zoom);
        assertEquals(InterpolationInput.ZOOM, zoom.input());
        assertEquals(Optional.empty(), zoom.attribute());

        assertThrows(
                IllegalArgumentException.class,
                () -> InterpolatedSymbolSelector.attribute("x", List.of(low), LINE));
        assertThrows(
                IllegalArgumentException.class,
                () -> InterpolatedSymbolSelector.attribute("x", List.of(high, low), LINE));
        assertThrows(
                IllegalArgumentException.class,
                () -> InterpolatedSymbolSelector.attribute("x", List.of(low, high), MARKER));
        assertThrows(
                IllegalArgumentException.class,
                () -> InterpolatedSymbolSelector.zoom(List.of(low, high), FILL));
    }

    @Test
    void portrayalContextsRetainOnlyValidatedExplicitDimensions() {
        PortrayalEvaluationContext context =
                PortrayalEvaluationContext.atScaleAndZoom(1_000, 4.5)
                        .withGeometryType(PortrayalGeometryType.POLYGON);
        assertEquals(1_000, context.scaleDenominator().orElseThrow());
        assertEquals(4.5, context.zoomLevel().orElseThrow());
        assertEquals(PortrayalGeometryType.POLYGON, context.geometryType().orElseThrow());
        assertEquals(
                PortrayalEvaluationContext.atScale(25),
                new PortrayalEvaluationContext(OptionalDouble.of(25)));
        assertThrows(IllegalArgumentException.class, () -> PortrayalEvaluationContext.atScale(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortrayalEvaluationContext.atScaleAndZoom(1, Double.NaN));
        assertThrows(
                NullPointerException.class,
                () -> assertNotNull(PortrayalEvaluationContext.UNSCALED.withGeometryType(null)));
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
