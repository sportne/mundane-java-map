package io.github.mundanej.map.core;

import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalEvaluationResult;
import io.github.mundanej.map.api.PortrayalExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic JDK-only evaluation of the neutral portrayal expression contract. */
public final class PortrayalExpressions {
    /** Stable code for a missing attribute or evaluation-context input. */
    public static final String INPUT_MISSING = "PORTRAYAL_EXPRESSION_INPUT_MISSING";

    /** Stable code for an operand of the wrong canonical type. */
    public static final String TYPE_MISMATCH = "PORTRAYAL_EXPRESSION_TYPE_MISMATCH";

    /** Stable code for non-finite numeric evaluation. */
    public static final String NON_FINITE = "PORTRAYAL_EXPRESSION_NON_FINITE";

    private PortrayalExpressions() {}

    /**
     * Evaluates one expression against an immutable feature and caller context.
     *
     * @param expression bounded neutral expression
     * @param feature feature inputs
     * @param context scale, zoom, and geometry inputs
     * @return stable success or failure result
     */
    public static PortrayalEvaluationResult evaluate(
            PortrayalExpression expression,
            FeatureRecord feature,
            PortrayalEvaluationContext context) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(context, "context");
        return evaluateNode(expression, feature, context);
    }

    private static PortrayalEvaluationResult evaluateNode(
            PortrayalExpression expression,
            FeatureRecord feature,
            PortrayalEvaluationContext context) {
        return switch (expression.operator()) {
            case LITERAL -> PortrayalEvaluationResult.success(expression.literal());
            case ATTRIBUTE -> {
                Object value = feature.attributes().get(expression.attributeName());
                yield value == null
                        ? missing("attribute " + expression.attributeName())
                        : PortrayalEvaluationResult.success(value);
            }
            case SCALE_DENOMINATOR ->
                    context.scaleDenominator().isPresent()
                            ? PortrayalEvaluationResult.success(
                                    context.scaleDenominator().orElseThrow())
                            : missing("scale denominator");
            case ZOOM_LEVEL ->
                    context.zoomLevel().isPresent()
                            ? PortrayalEvaluationResult.success(context.zoomLevel().orElseThrow())
                            : missing("zoom level");
            case GEOMETRY_TYPE ->
                    context.geometryType().isPresent()
                            ? PortrayalEvaluationResult.success(
                                    context.geometryType().orElseThrow().name())
                            : missing("geometry type");
            case ADD -> numeric(expression.arguments(), feature, context, false);
            case MULTIPLY -> numeric(expression.arguments(), feature, context, true);
            case CONCAT -> concatenate(expression.arguments(), feature, context);
            case EQUAL -> equal(expression.arguments(), feature, context);
            case COALESCE -> coalesce(expression.arguments(), feature, context);
        };
    }

    private static PortrayalEvaluationResult numeric(
            List<PortrayalExpression> arguments,
            FeatureRecord feature,
            PortrayalEvaluationContext context,
            boolean multiply) {
        double result = multiply ? 1.0 : 0.0;
        for (PortrayalExpression argument : arguments) {
            PortrayalEvaluationResult evaluated = evaluateNode(argument, feature, context);
            if (!evaluated.succeeded()) {
                return evaluated;
            }
            Object value = evaluated.value().orElseThrow();
            if (!(value instanceof Number number)) {
                return PortrayalEvaluationResult.failure(
                        TYPE_MISMATCH, "Numeric expression requires numeric operands");
            }
            result = multiply ? result * number.doubleValue() : result + number.doubleValue();
            if (!Double.isFinite(result)) {
                return PortrayalEvaluationResult.failure(
                        NON_FINITE, "Numeric expression produced a non-finite result");
            }
        }
        return PortrayalEvaluationResult.success(result);
    }

    private static PortrayalEvaluationResult concatenate(
            List<PortrayalExpression> arguments,
            FeatureRecord feature,
            PortrayalEvaluationContext context) {
        StringBuilder result = new StringBuilder();
        for (PortrayalExpression argument : arguments) {
            PortrayalEvaluationResult evaluated = evaluateNode(argument, feature, context);
            if (!evaluated.succeeded()) {
                return evaluated;
            }
            Object value = evaluated.value().orElseThrow();
            if (!(value instanceof String text)) {
                return PortrayalEvaluationResult.failure(
                        TYPE_MISMATCH, "Concatenation requires text operands");
            }
            result.append(text);
        }
        return PortrayalEvaluationResult.success(result.toString());
    }

    private static PortrayalEvaluationResult equal(
            List<PortrayalExpression> arguments,
            FeatureRecord feature,
            PortrayalEvaluationContext context) {
        List<Object> values = new ArrayList<>(2);
        for (PortrayalExpression argument : arguments) {
            PortrayalEvaluationResult evaluated = evaluateNode(argument, feature, context);
            if (!evaluated.succeeded()) {
                return evaluated;
            }
            values.add(evaluated.value().orElseThrow());
        }
        return PortrayalEvaluationResult.success(values.get(0).equals(values.get(1)));
    }

    private static PortrayalEvaluationResult coalesce(
            List<PortrayalExpression> arguments,
            FeatureRecord feature,
            PortrayalEvaluationContext context) {
        PortrayalEvaluationResult last = null;
        for (PortrayalExpression argument : arguments) {
            last = evaluateNode(argument, feature, context);
            if (last.succeeded()) {
                return last;
            }
        }
        return Objects.requireNonNull(last, "last");
    }

    private static PortrayalEvaluationResult missing(String input) {
        return PortrayalEvaluationResult.failure(INPUT_MISSING, "Missing " + input);
    }
}
