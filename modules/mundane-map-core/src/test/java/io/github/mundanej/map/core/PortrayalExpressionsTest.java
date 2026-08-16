package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalEvaluationResult;
import io.github.mundanej.map.api.PortrayalExpression;
import io.github.mundanej.map.api.PortrayalGeometryType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortrayalExpressionsTest {
    private static final FeatureRecord FEATURE =
            new FeatureRecord(
                    "one",
                    "One",
                    new PointGeometry(new Coordinate(1, 2)),
                    Map.of("number", 4, "text", "city", "same", "city"));
    private static final PortrayalEvaluationContext CONTEXT =
            PortrayalEvaluationContext.atScaleAndZoom(10_000, 8)
                    .withGeometryType(PortrayalGeometryType.POINT);

    @Test
    void evaluatesEveryLiteralAttributeAndContextInput() {
        assertValue(PortrayalExpression.literal(true), true);
        assertValue(PortrayalExpression.attribute("number"), 4L);
        assertValue(
                PortrayalExpression.input(PortrayalExpression.Operator.SCALE_DENOMINATOR),
                10_000.0);
        assertValue(PortrayalExpression.input(PortrayalExpression.Operator.ZOOM_LEVEL), 8.0);
        assertValue(PortrayalExpression.input(PortrayalExpression.Operator.GEOMETRY_TYPE), "POINT");
    }

    @Test
    void evaluatesNumericTextEqualityAndCoalesceOperations() {
        assertValue(
                call(
                        PortrayalExpression.Operator.ADD,
                        PortrayalExpression.attribute("number"),
                        PortrayalExpression.literal(2)),
                6.0);
        assertValue(
                call(
                        PortrayalExpression.Operator.MULTIPLY,
                        PortrayalExpression.attribute("number"),
                        PortrayalExpression.literal(3)),
                12.0);
        assertValue(
                call(
                        PortrayalExpression.Operator.CONCAT,
                        PortrayalExpression.attribute("text"),
                        PortrayalExpression.literal("!")),
                "city!");
        assertValue(
                call(
                        PortrayalExpression.Operator.EQUAL,
                        PortrayalExpression.attribute("text"),
                        PortrayalExpression.attribute("same")),
                true);
        assertValue(
                call(
                        PortrayalExpression.Operator.COALESCE,
                        PortrayalExpression.attribute("missing"),
                        PortrayalExpression.literal("fallback")),
                "fallback");
    }

    @Test
    void failuresUseStableCodesAndCoalesceRetainsLastFailure() {
        PortrayalEvaluationResult missing =
                PortrayalExpressions.evaluate(
                        PortrayalExpression.attribute("missing"), FEATURE, CONTEXT);
        assertFalse(missing.succeeded());
        assertEquals(PortrayalExpressions.INPUT_MISSING, missing.code());

        PortrayalEvaluationResult contextMissing =
                PortrayalExpressions.evaluate(
                        PortrayalExpression.input(PortrayalExpression.Operator.ZOOM_LEVEL),
                        FEATURE,
                        PortrayalEvaluationContext.UNSCALED);
        assertEquals(PortrayalExpressions.INPUT_MISSING, contextMissing.code());

        PortrayalEvaluationResult numericType =
                PortrayalExpressions.evaluate(
                        call(
                                PortrayalExpression.Operator.ADD,
                                PortrayalExpression.attribute("text")),
                        FEATURE,
                        CONTEXT);
        assertEquals(PortrayalExpressions.TYPE_MISMATCH, numericType.code());

        PortrayalEvaluationResult textType =
                PortrayalExpressions.evaluate(
                        call(
                                PortrayalExpression.Operator.CONCAT,
                                PortrayalExpression.attribute("number")),
                        FEATURE,
                        CONTEXT);
        assertEquals(PortrayalExpressions.TYPE_MISMATCH, textType.code());

        PortrayalEvaluationResult nonFinite =
                PortrayalExpressions.evaluate(
                        call(
                                PortrayalExpression.Operator.MULTIPLY,
                                PortrayalExpression.literal(Double.MAX_VALUE),
                                PortrayalExpression.literal(2.0)),
                        FEATURE,
                        CONTEXT);
        assertEquals(PortrayalExpressions.NON_FINITE, nonFinite.code());

        PortrayalEvaluationResult coalesceFailure =
                PortrayalExpressions.evaluate(
                        call(
                                PortrayalExpression.Operator.COALESCE,
                                PortrayalExpression.attribute("first-missing"),
                                PortrayalExpression.attribute("last-missing")),
                        FEATURE,
                        CONTEXT);
        assertTrue(coalesceFailure.message().contains("last-missing"));
    }

    private static PortrayalExpression call(
            PortrayalExpression.Operator operator, PortrayalExpression... arguments) {
        return PortrayalExpression.call(operator, List.of(arguments));
    }

    private static void assertValue(PortrayalExpression expression, Object expected) {
        PortrayalEvaluationResult result =
                PortrayalExpressions.evaluate(expression, FEATURE, CONTEXT);
        assertTrue(result.succeeded());
        assertEquals(expected, result.value().orElseThrow());
    }
}
