package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** Immutable format-neutral expression tree with explicitly bounded shape. */
public final class PortrayalExpression {
    /** Neutral operations supported by the common expression contract. */
    public enum Operator {
        /** A canonical literal value. */
        LITERAL,
        /** A feature attribute selected by exact name. */
        ATTRIBUTE,
        /** The evaluation scale denominator. */
        SCALE_DENOMINATOR,
        /** The evaluation zoom level. */
        ZOOM_LEVEL,
        /** The normalized feature geometry type name. */
        GEOMETRY_TYPE,
        /** Numeric addition. */
        ADD,
        /** Numeric multiplication. */
        MULTIPLY,
        /** Deterministic string concatenation. */
        CONCAT,
        /** Canonical value equality. */
        EQUAL,
        /** First successfully evaluated argument. */
        COALESCE
    }

    private final Operator operator;
    private final Object literal;
    private final String attributeName;
    private final List<PortrayalExpression> arguments;
    private final int nodeCount;
    private final int depth;

    private PortrayalExpression(
            Operator operator,
            Object literal,
            String attributeName,
            List<PortrayalExpression> arguments,
            PortrayalExpressionLimits limits) {
        this.operator = Objects.requireNonNull(operator, "operator");
        this.literal = literal;
        this.attributeName = attributeName;
        this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        validateShape();
        int nodes = 1;
        int childDepth = -1;
        for (PortrayalExpression argument : this.arguments) {
            Objects.requireNonNull(argument, "argument");
            nodes = Math.addExact(nodes, argument.nodeCount);
            childDepth = Math.max(childDepth, argument.depth);
        }
        this.nodeCount = nodes;
        this.depth = childDepth + 1;
        if (arguments.size() > limits.maxArguments()) {
            throw new IllegalArgumentException("PORTRAYAL_EXPRESSION_MAX_ARGUMENTS_EXCEEDED");
        }
        if (nodeCount > limits.maxNodes()) {
            throw new IllegalArgumentException("PORTRAYAL_EXPRESSION_MAX_NODES_EXCEEDED");
        }
        if (depth > limits.maxDepth()) {
            throw new IllegalArgumentException("PORTRAYAL_EXPRESSION_MAX_DEPTH_EXCEEDED");
        }
    }

    /**
     * Creates a canonical scalar or structured literal.
     *
     * @param value supported attribute value
     * @return literal expression
     */
    public static PortrayalExpression literal(Object value) {
        return new PortrayalExpression(
                Operator.LITERAL,
                AttributeValues.canonicalizeValue(value),
                null,
                List.of(),
                PortrayalExpressionLimits.DEFAULT);
    }

    /**
     * Creates an exact feature-attribute input.
     *
     * @param name canonical attribute name
     * @return attribute expression
     */
    public static PortrayalExpression attribute(String name) {
        return new PortrayalExpression(
                Operator.ATTRIBUTE,
                null,
                AttributeValues.requireName(name),
                List.of(),
                PortrayalExpressionLimits.DEFAULT);
    }

    /**
     * Creates a context-input expression.
     *
     * @param input scale, zoom, or geometry-type operator
     * @return input expression
     */
    public static PortrayalExpression input(Operator input) {
        return new PortrayalExpression(
                input, null, null, List.of(), PortrayalExpressionLimits.DEFAULT);
    }

    /**
     * Creates an operation under default limits.
     *
     * @param operator non-leaf operator
     * @param arguments ordered arguments
     * @return immutable expression
     */
    public static PortrayalExpression call(Operator operator, List<PortrayalExpression> arguments) {
        return call(operator, arguments, PortrayalExpressionLimits.DEFAULT);
    }

    /**
     * Creates an operation under explicit tree limits.
     *
     * @param operator non-leaf operator
     * @param arguments ordered arguments
     * @param limits safety limits
     * @return immutable expression
     */
    public static PortrayalExpression call(
            Operator operator,
            List<PortrayalExpression> arguments,
            PortrayalExpressionLimits limits) {
        return new PortrayalExpression(operator, null, null, arguments, limits);
    }

    /**
     * Returns the neutral operator.
     *
     * @return operator
     */
    public Operator operator() {
        return operator;
    }

    /**
     * Returns the literal payload, or {@code null} for other operators.
     *
     * @return canonical literal or null
     */
    public Object literal() {
        return literal;
    }

    /**
     * Returns the attribute name, or {@code null} for other operators.
     *
     * @return exact name or null
     */
    public String attributeName() {
        return attributeName;
    }

    /**
     * Returns immutable ordered operation arguments.
     *
     * @return arguments
     */
    public List<PortrayalExpression> arguments() {
        return arguments;
    }

    /**
     * Returns total tree nodes.
     *
     * @return node count
     */
    public int nodeCount() {
        return nodeCount;
    }

    /**
     * Returns maximum tree edge depth.
     *
     * @return expression depth
     */
    public int depth() {
        return depth;
    }

    private void validateShape() {
        switch (operator) {
            case LITERAL -> {
                Objects.requireNonNull(literal, "literal");
                requireEmptyArguments();
            }
            case ATTRIBUTE -> {
                Objects.requireNonNull(attributeName, "attributeName");
                requireEmptyArguments();
            }
            case SCALE_DENOMINATOR, ZOOM_LEVEL, GEOMETRY_TYPE -> requireEmptyArguments();
            case EQUAL -> {
                if (arguments.size() != 2) {
                    throw new IllegalArgumentException("EQUAL requires exactly two arguments");
                }
            }
            case ADD, MULTIPLY, CONCAT, COALESCE -> {
                if (arguments.isEmpty()) {
                    throw new IllegalArgumentException(
                            operator + " requires at least one argument");
                }
            }
        }
    }

    private void requireEmptyArguments() {
        if (!arguments.isEmpty()) {
            throw new IllegalArgumentException(operator + " does not accept arguments");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PortrayalExpression expression
                && operator == expression.operator
                && Objects.equals(literal, expression.literal)
                && Objects.equals(attributeName, expression.attributeName)
                && arguments.equals(expression.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, literal, attributeName, arguments);
    }

    @Override
    public String toString() {
        return "PortrayalExpression[operator="
                + operator
                + ", literal="
                + literal
                + ", attributeName="
                + attributeName
                + ", arguments="
                + arguments
                + "]";
    }
}
