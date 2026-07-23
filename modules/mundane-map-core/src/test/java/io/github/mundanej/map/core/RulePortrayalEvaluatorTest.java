package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.PortrayalComparison;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalLogicalOperator;
import io.github.mundanej.map.api.PortrayalOperand;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.ResolvedFeaturePortrayal;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.RuleSymbolSelector;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RulePortrayalEvaluatorTest {
    private static final Symbol RED =
            BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(255, 0, 0), 8, 1);
    private static final Symbol BLUE =
            BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(0, 0, 255), 8, 1);

    @Test
    void orderedMatchesComposeAndElseOnlyAppliesWithoutOrdinaryMatch() {
        PortrayalPredicate category =
                comparison(
                        PortrayalComparison.EQUAL,
                        new PortrayalOperand.Property("kind"),
                        new PortrayalOperand.Literal("primary"));
        PortrayalPredicate score =
                comparison(
                        PortrayalComparison.GREATER_THAN_OR_EQUAL,
                        new PortrayalOperand.Property("score"),
                        new PortrayalOperand.Literal("10"));
        RulePortrayalPlan plan =
                new RulePortrayalPlan(
                        List.of(
                                rule(category, RED),
                                rule(score, BLUE),
                                elseRule(BLUE, ScaleInterval.ALL)));
        FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(plan.portrayal());

        CompositeSymbol composite =
                assertInstanceOf(
                        CompositeSymbol.class,
                        resolver.resolveAll(
                                        Map.of("kind", "primary", "score", 10L),
                                        PortrayalEvaluationContext.UNSCALED)
                                .marker()
                                .orElseThrow());
        assertEquals(List.of(RED, BLUE), composite.children());
        assertEquals(
                BLUE,
                resolver.resolveAll(Map.of("kind", "other"), PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .orElseThrow());
        assertEquals(List.of("kind", "score"), resolver.requiredSymbolAttributes());
        assertEquals(List.of(RED, BLUE, BLUE), resolver.reachableSymbols());
    }

    @Test
    void predicateKindsMissingNullConversionAndBooleanCompositionAreExact() {
        PortrayalPredicate predicate =
                new PortrayalPredicate.Logical(
                        PortrayalLogicalOperator.AND,
                        List.of(
                                new PortrayalPredicate.IsNull(
                                        new PortrayalOperand.Property("nullable")),
                                new PortrayalPredicate.Between(
                                        new PortrayalOperand.Property("number"),
                                        new PortrayalOperand.Literal("1.5"),
                                        new PortrayalOperand.Property("upper")),
                                new PortrayalPredicate.Logical(
                                        PortrayalLogicalOperator.OR,
                                        List.of(
                                                comparison(
                                                        PortrayalComparison.EQUAL,
                                                        new PortrayalOperand.Property("date"),
                                                        new PortrayalOperand.Literal("2026-07-23")),
                                                comparison(
                                                        PortrayalComparison.LESS_THAN,
                                                        new PortrayalOperand.Property("text"),
                                                        new PortrayalOperand.Literal("z"))))));
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        new RulePortrayalPlan(List.of(rule(predicate, RED))).portrayal());
        Map<String, Object> matching =
                Map.of(
                        "nullable",
                        AttributeNull.INSTANCE,
                        "number",
                        2L,
                        "upper",
                        3.0,
                        "date",
                        LocalDate.of(2026, 7, 23),
                        "text",
                        "a");

        assertEquals(
                RED,
                resolver.resolveAll(matching, PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .orElseThrow());
        assertTrue(
                resolver.resolveAll(
                                Map.of("number", 2L, "upper", 3L, "date", "2026-07-23"),
                                PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .isEmpty());
        assertTrue(
                resolver.resolveAll(
                                Map.of(
                                        "nullable",
                                        AttributeNull.INSTANCE,
                                        "number",
                                        "2",
                                        "upper",
                                        3L,
                                        "date",
                                        LocalDate.of(2026, 7, 23)),
                                PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comparisonCases")
    void comparisonTruthAndTypeTableIsExact(ComparisonCase testCase) {
        assertEquals(
                testCase.expected(),
                matches(testCase.predicate(), testCase.attributes()),
                testCase.name());
    }

    private static List<ComparisonCase> comparisonCases() {
        PortrayalOperand.Property left = new PortrayalOperand.Property("left");
        PortrayalOperand.Property right = new PortrayalOperand.Property("right");
        return List.of(
                comparisonCase("text equal", PortrayalComparison.EQUAL, "same", "same", true),
                comparisonCase("logical equal", PortrayalComparison.EQUAL, true, true, true),
                comparisonCase(
                        "normalized numeric properties equal",
                        PortrayalComparison.EQUAL,
                        2L,
                        new BigDecimal("2.00"),
                        true),
                comparisonCase(
                        "date ordered",
                        PortrayalComparison.LESS_THAN,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 2),
                        true),
                comparisonCase(
                        "less-than-or-equal includes boundary",
                        PortrayalComparison.LESS_THAN_OR_EQUAL,
                        2L,
                        2.0,
                        true),
                comparisonCase(
                        "less-than-or-equal rejects greater value",
                        PortrayalComparison.LESS_THAN_OR_EQUAL,
                        3L,
                        2.0,
                        false),
                comparisonCase(
                        "greater-than-or-equal includes boundary",
                        PortrayalComparison.GREATER_THAN_OR_EQUAL,
                        2L,
                        new BigDecimal("2.00"),
                        true),
                comparisonCase(
                        "greater-than-or-equal rejects lesser value",
                        PortrayalComparison.GREATER_THAN_OR_EQUAL,
                        1L,
                        2.0,
                        false),
                comparisonCase(
                        "strict greater-than succeeds",
                        PortrayalComparison.GREATER_THAN,
                        "z",
                        "a",
                        true),
                comparisonCase(
                        "unlike kinds are not equal", PortrayalComparison.EQUAL, 2L, "2", false),
                comparisonCase(
                        "unlike kinds are unequal", PortrayalComparison.NOT_EQUAL, 2L, "2", true),
                comparisonCase(
                        "logical values are unordered",
                        PortrayalComparison.LESS_THAN,
                        false,
                        true,
                        false),
                comparisonCase(
                        "null values are unordered",
                        PortrayalComparison.GREATER_THAN,
                        AttributeNull.INSTANCE,
                        AttributeNull.INSTANCE,
                        false),
                comparisonCase(
                        "explicit null values are equal",
                        PortrayalComparison.EQUAL,
                        AttributeNull.INSTANCE,
                        AttributeNull.INSTANCE,
                        true),
                comparisonCase(
                        "unsupported binary is false",
                        PortrayalComparison.NOT_EQUAL,
                        new byte[] {1},
                        "bytes",
                        false),
                new ComparisonCase(
                        "missing is false",
                        new PortrayalPredicate.Comparison(
                                PortrayalComparison.NOT_EQUAL,
                                left,
                                new PortrayalOperand.Literal("anything")),
                        Map.of(),
                        false),
                new ComparisonCase(
                        "failed conversion is false for not-equal",
                        new PortrayalPredicate.Comparison(
                                PortrayalComparison.NOT_EQUAL,
                                left,
                                new PortrayalOperand.Literal("not-a-number")),
                        Map.of("left", 2L),
                        false),
                literalCase(
                        "strict true literal converts",
                        PortrayalComparison.EQUAL,
                        true,
                        "true",
                        true),
                literalCase(
                        "strict false literal converts",
                        PortrayalComparison.EQUAL,
                        false,
                        "false",
                        true),
                literalCase(
                        "invalid boolean literal conversion is false",
                        PortrayalComparison.NOT_EQUAL,
                        true,
                        "TRUE",
                        false),
                literalCase(
                        "valid date literal converts",
                        PortrayalComparison.EQUAL,
                        LocalDate.of(2026, 7, 23),
                        "2026-07-23",
                        true),
                literalCase(
                        "invalid date literal conversion is false",
                        PortrayalComparison.NOT_EQUAL,
                        LocalDate.of(2026, 7, 23),
                        "July 23",
                        false),
                new ComparisonCase(
                        "not negates false comparison",
                        new PortrayalPredicate.Logical(
                                PortrayalLogicalOperator.NOT,
                                List.of(
                                        new PortrayalPredicate.Comparison(
                                                PortrayalComparison.EQUAL,
                                                left,
                                                new PortrayalOperand.Literal("other")))),
                        Map.of("left", "value"),
                        true),
                new ComparisonCase(
                        "property comparison",
                        new PortrayalPredicate.Comparison(PortrayalComparison.EQUAL, left, right),
                        Map.of("left", 4L, "right", new BigDecimal("4.000")),
                        true));
    }

    private static ComparisonCase comparisonCase(
            String name,
            PortrayalComparison operation,
            Object left,
            Object right,
            boolean expected) {
        return new ComparisonCase(
                name,
                new PortrayalPredicate.Comparison(
                        operation,
                        new PortrayalOperand.Property("left"),
                        new PortrayalOperand.Property("right")),
                Map.of("left", left, "right", right),
                expected);
    }

    private static ComparisonCase literalCase(
            String name,
            PortrayalComparison operation,
            Object property,
            String literal,
            boolean expected) {
        return new ComparisonCase(
                name,
                new PortrayalPredicate.Comparison(
                        operation,
                        new PortrayalOperand.Property("left"),
                        new PortrayalOperand.Literal(literal)),
                Map.of("left", property),
                expected);
    }

    private static boolean matches(PortrayalPredicate predicate, Map<String, Object> attributes) {
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        new RulePortrayalPlan(List.of(rule(predicate, RED))).portrayal());
        return resolver.resolveAll(attributes, PortrayalEvaluationContext.UNSCALED)
                .marker()
                .isPresent();
    }

    @Test
    void mixedSelectorsResolveOnlyTheirConfiguredRoles() {
        SolidLineSymbol fixedLine =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(0, 0, 0), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        RulePortrayalPlan plan =
                new RulePortrayalPlan(
                        List.of(
                                new PortrayalRule(
                                        Optional.empty(),
                                        ScaleInterval.ALL,
                                        Optional.empty(),
                                        false,
                                        List.of(RED),
                                        List.of(),
                                        List.of())));
        FeaturePortrayal portrayal =
                new FeaturePortrayal(
                        Optional.of(new RuleSymbolSelector(plan, SymbolRole.MARKER)),
                        Optional.of(new FixedSymbolSelector(fixedLine)),
                        Optional.empty());
        FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(portrayal);

        ResolvedFeaturePortrayal resolved =
                resolver.resolveAll(Map.of(), PortrayalEvaluationContext.UNSCALED);
        assertEquals(RED, resolved.marker().orElseThrow());
        assertEquals(fixedLine, resolved.line().orElseThrow());
        assertTrue(resolved.fill().isEmpty());
        assertEquals(List.of(RED, fixedLine), resolver.reachableSymbols());
    }

    @Test
    void scaleBoundariesAndContextRequirementAreExplicit() {
        ScaleInterval interval = new ScaleInterval(OptionalDouble.of(100), OptionalDouble.of(200));
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(
                        new RulePortrayalPlan(
                                        List.of(
                                                new PortrayalRule(
                                                        Optional.empty(),
                                                        interval,
                                                        Optional.empty(),
                                                        false,
                                                        List.of(RED),
                                                        List.of(),
                                                        List.of())))
                                .portrayal());

        SymbolException missing =
                assertThrows(
                        SymbolException.class, () -> resolver.resolve(SymbolRole.MARKER, Map.of()));
        assertEquals(SymbolException.PORTRAYAL_SCALE_CONTEXT_REQUIRED, missing.code());
        assertEquals(
                RED,
                resolver.resolveAll(Map.of(), PortrayalEvaluationContext.atScale(100))
                        .marker()
                        .orElseThrow());
        assertTrue(
                resolver.resolveAll(Map.of(), PortrayalEvaluationContext.atScale(200))
                        .marker()
                        .isEmpty());
        assertTrue(resolver.requiresScaleContext());
    }

    @Test
    void plansDefensivelyCopyRulesAndRejectOverlappingElseIntervals() {
        ArrayList<PortrayalRule> mutable = new ArrayList<>();
        mutable.add(rule(new PortrayalPredicate.IsNull(new PortrayalOperand.Property("x")), RED));
        RulePortrayalPlan copied = new RulePortrayalPlan(mutable);
        mutable.clear();
        assertEquals(1, copied.rules().size());

        PortrayalRule firstElse =
                elseRule(RED, new ScaleInterval(OptionalDouble.of(0), OptionalDouble.of(10)));
        PortrayalRule secondElse =
                elseRule(BLUE, new ScaleInterval(OptionalDouble.of(9), OptionalDouble.of(20)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RulePortrayalPlan(List.of(firstElse, secondElse)));
    }

    private static PortrayalPredicate comparison(
            PortrayalComparison operation, PortrayalOperand left, PortrayalOperand right) {
        return new PortrayalPredicate.Comparison(operation, left, right);
    }

    private static PortrayalRule rule(PortrayalPredicate predicate, Symbol symbol) {
        return new PortrayalRule(
                Optional.empty(),
                ScaleInterval.ALL,
                Optional.of(predicate),
                false,
                List.of(symbol),
                List.of(),
                List.of());
    }

    private static PortrayalRule elseRule(Symbol symbol, ScaleInterval interval) {
        return new PortrayalRule(
                Optional.empty(),
                interval,
                Optional.empty(),
                true,
                List.of(symbol),
                List.of(),
                List.of());
    }

    private record ComparisonCase(
            String name,
            PortrayalPredicate predicate,
            Map<String, Object> attributes,
            boolean expected) {}
}
