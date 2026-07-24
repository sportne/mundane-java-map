package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FilteredSymbolSelector;
import io.github.mundanej.map.api.PortrayalComparison;
import io.github.mundanej.map.api.PortrayalGeometryType;
import io.github.mundanej.map.api.PortrayalLogicalOperator;
import io.github.mundanej.map.api.PortrayalOperand;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class MapLibreFilters {
    private MapLibreFilters() {}

    static CompiledFilter compile(
            Object expression,
            MapLibreReadLimits limits,
            int precedingNodes,
            CancellationToken cancellation,
            String location) {
        Compiler compiler = new Compiler(limits, precedingNodes, cancellation, true);
        PortrayalPredicate predicate = compiler.predicate(expression, location, 1);
        return new CompiledFilter(predicate, compiler.nodes);
    }

    static CompiledFilter compileLayerFilter(
            Object expression,
            MapLibreReadLimits limits,
            int precedingNodes,
            CancellationToken cancellation,
            String location) {
        Compiler compiler = new Compiler(limits, precedingNodes, cancellation, false);
        PortrayalPredicate predicate = compiler.predicate(expression, location, 1);
        return new CompiledFilter(predicate, compiler.nodes);
    }

    static Optional<FeaturePortrayal> apply(
            Optional<FeaturePortrayal> portrayal, PortrayalPredicate predicate) {
        if (portrayal.isEmpty()) {
            return Optional.empty();
        }
        FeaturePortrayal source = portrayal.orElseThrow();
        return Optional.of(
                new FeaturePortrayal(
                        source.marker()
                                .map(selector -> new FilteredSymbolSelector(predicate, selector)),
                        source.line()
                                .map(selector -> new FilteredSymbolSelector(predicate, selector)),
                        source.fill()
                                .map(selector -> new FilteredSymbolSelector(predicate, selector)),
                        source.pointLabel()));
    }

    private static final class Compiler {
        private final MapLibreReadLimits limits;
        private final int precedingNodes;
        private final CancellationToken cancellation;
        private final boolean branchingAllowed;
        private int nodes;

        private Compiler(
                MapLibreReadLimits limits,
                int precedingNodes,
                CancellationToken cancellation,
                boolean branchingAllowed) {
            this.limits = limits;
            this.precedingNodes = precedingNodes;
            this.cancellation = cancellation;
            this.branchingAllowed = branchingAllowed;
        }

        private PortrayalPredicate predicate(Object value, String location, int depth) {
            node(location, depth);
            if (value instanceof Boolean constant) {
                return new PortrayalPredicate.Constant(constant);
            }
            List<Object> expression = expression(value, location);
            String operation = operation(expression, location);
            return switch (operation) {
                case "!" -> negate(expression, location, depth);
                case "all" -> logical(expression, PortrayalLogicalOperator.AND, location, depth);
                case "any" -> logical(expression, PortrayalLogicalOperator.OR, location, depth);
                case "has" -> exists(expression, location);
                case "match" -> {
                    requireBranching(location);
                    yield match(expression, location, depth);
                }
                case "case" -> {
                    requireBranching(location);
                    yield conditional(expression, location, depth);
                }
                case "step" -> {
                    requireBranching(location);
                    yield step(expression, location, depth);
                }
                case "==", "!=", "<", "<=", ">", ">=" ->
                        comparison(expression, operation, location, depth);
                default -> throw unsupported(location);
            };
        }

        private void requireBranching(String location) {
            if (!branchingAllowed) {
                throw unsupported(location);
            }
        }

        private PortrayalPredicate match(List<Object> expression, String location, int depth) {
            if (expression.size() < 5 || (expression.size() & 1) == 0) {
                throw type(location, "arity");
            }
            Operand input = operand(expression.get(1), location + "/1", depth + 1);
            ArrayList<PortrayalPredicate> matched = new ArrayList<>();
            ArrayList<PortrayalPredicate> accepted = new ArrayList<>();
            for (int index = 2; index < expression.size() - 1; index += 2) {
                PortrayalPredicate category =
                        equality(
                                input,
                                operand(expression.get(index), location + '/' + index, depth + 1),
                                location + '/' + index);
                matched.add(category);
                PortrayalPredicate result =
                        predicateResult(
                                expression.get(index + 1), location + '/' + (index + 1), depth + 1);
                accepted.add(and(List.of(category, result)));
            }
            PortrayalPredicate fallback =
                    predicateResult(
                            expression.getLast(),
                            location + '/' + (expression.size() - 1),
                            depth + 1);
            accepted.add(and(List.of(not(or(matched)), fallback)));
            return or(accepted);
        }

        private PortrayalPredicate conditional(
                List<Object> expression, String location, int depth) {
            if (expression.size() < 4 || (expression.size() & 1) != 0) {
                throw type(location, "arity");
            }
            ArrayList<PortrayalPredicate> preceding = new ArrayList<>();
            ArrayList<PortrayalPredicate> accepted = new ArrayList<>();
            for (int index = 1; index < expression.size() - 1; index += 2) {
                PortrayalPredicate condition =
                        predicate(expression.get(index), location + '/' + index, depth + 1);
                PortrayalPredicate result =
                        predicateResult(
                                expression.get(index + 1), location + '/' + (index + 1), depth + 1);
                ArrayList<PortrayalPredicate> branch = new ArrayList<>();
                branch.add(not(or(preceding)));
                branch.add(condition);
                branch.add(result);
                accepted.add(and(branch));
                preceding.add(condition);
            }
            accepted.add(
                    and(
                            List.of(
                                    not(or(preceding)),
                                    predicateResult(
                                            expression.getLast(),
                                            location + '/' + (expression.size() - 1),
                                            depth + 1))));
            return or(accepted);
        }

        private PortrayalPredicate step(List<Object> expression, String location, int depth) {
            if (expression.size() < 5 || (expression.size() & 1) == 0) {
                throw type(location, "arity");
            }
            Operand input = operand(expression.get(1), location + "/1", depth + 1);
            if (!(input.operand() instanceof PortrayalOperand.Property)) {
                throw type(location + "/1", "stepInput");
            }
            ArrayList<PortrayalPredicate> branches = new ArrayList<>();
            PortrayalOperand.TypedLiteral previous = null;
            for (int index = 3; index < expression.size(); index += 2) {
                Operand threshold =
                        operand(expression.get(index), location + '/' + index, depth + 1);
                if (!(threshold.operand() instanceof PortrayalOperand.TypedLiteral literal)
                        || literal.value().kind() != ThematicValue.Kind.NUMERIC) {
                    throw type(location + '/' + index, "stepStop");
                }
                if (previous != null
                        && ((BigDecimal) previous.value().value())
                                        .compareTo((BigDecimal) literal.value().value())
                                >= 0) {
                    throw type(location + '/' + index, "stopOrder");
                }
                PortrayalPredicate lower =
                        new PortrayalPredicate.Comparison(
                                PortrayalComparison.GREATER_THAN_OR_EQUAL,
                                input.operand(),
                                literal);
                PortrayalPredicate upper = null;
                if (index + 2 < expression.size()) {
                    Operand next =
                            operand(
                                    expression.get(index + 2),
                                    location + '/' + (index + 2),
                                    depth + 1);
                    if (!(next.operand() instanceof PortrayalOperand.TypedLiteral nextLiteral)
                            || nextLiteral.value().kind() != ThematicValue.Kind.NUMERIC) {
                        throw type(location + '/' + (index + 2), "stepStop");
                    }
                    upper =
                            new PortrayalPredicate.Comparison(
                                    PortrayalComparison.LESS_THAN, input.operand(), nextLiteral);
                }
                PortrayalPredicate result =
                        predicateResult(
                                expression.get(index + 1), location + '/' + (index + 1), depth + 1);
                branches.add(
                        upper == null
                                ? and(List.of(lower, result))
                                : and(List.of(lower, upper, result)));
                previous = literal;
            }
            PortrayalPredicate firstStop =
                    new PortrayalPredicate.Comparison(
                            PortrayalComparison.LESS_THAN,
                            input.operand(),
                            operand(expression.get(3), location + "/3", depth + 1).operand());
            branches.add(
                    and(
                            List.of(
                                    firstStop,
                                    predicateResult(
                                            expression.get(2), location + "/2", depth + 1))));
            return or(branches);
        }

        private PortrayalPredicate predicateResult(Object value, String location, int depth) {
            return value instanceof Boolean constant
                    ? new PortrayalPredicate.Constant(constant)
                    : predicate(value, location, depth);
        }

        private PortrayalPredicate equality(Operand left, Operand right, String location) {
            if (left.geometry() || right.geometry()) {
                return geometryComparison(left, right, "==", location);
            }
            try {
                return new PortrayalPredicate.Comparison(
                        PortrayalComparison.EQUAL, left.operand(), right.operand());
            } catch (IllegalArgumentException failure) {
                throw type(location, "comparison");
            }
        }

        private static PortrayalPredicate not(PortrayalPredicate predicate) {
            return new PortrayalPredicate.Logical(PortrayalLogicalOperator.NOT, List.of(predicate));
        }

        private static PortrayalPredicate and(List<PortrayalPredicate> predicates) {
            if (predicates.isEmpty()) {
                return new PortrayalPredicate.Constant(true);
            }
            return predicates.size() == 1
                    ? predicates.getFirst()
                    : new PortrayalPredicate.Logical(PortrayalLogicalOperator.AND, predicates);
        }

        private static PortrayalPredicate or(List<PortrayalPredicate> predicates) {
            if (predicates.isEmpty()) {
                return new PortrayalPredicate.Constant(false);
            }
            return predicates.size() == 1
                    ? predicates.getFirst()
                    : new PortrayalPredicate.Logical(PortrayalLogicalOperator.OR, predicates);
        }

        private PortrayalPredicate negate(List<Object> expression, String location, int depth) {
            requireSize(expression, 2, location);
            return new PortrayalPredicate.Logical(
                    PortrayalLogicalOperator.NOT,
                    List.of(predicate(expression.get(1), location + "/1", depth + 1)));
        }

        private PortrayalPredicate logical(
                List<Object> expression,
                PortrayalLogicalOperator operator,
                String location,
                int depth) {
            if (expression.size() < 2) {
                throw type(location, "arity");
            }
            if (expression.size() - 1 > 1_024) {
                throw limit(location, "logicalChildren", expression.size() - 1L, 1_024);
            }
            ArrayList<PortrayalPredicate> children = new ArrayList<>(expression.size() - 1);
            for (int index = 1; index < expression.size(); index++) {
                children.add(predicate(expression.get(index), location + '/' + index, depth + 1));
            }
            if (children.size() == 1) {
                return children.getFirst();
            }
            return new PortrayalPredicate.Logical(operator, children);
        }

        private PortrayalPredicate exists(List<Object> expression, String location) {
            requireSize(expression, 2, location);
            return new PortrayalPredicate.Exists(
                    property(expression.get(1), location + "/1", false));
        }

        private PortrayalPredicate comparison(
                List<Object> expression, String operation, String location, int depth) {
            requireSize(expression, 3, location);
            Operand left = operand(expression.get(1), location + "/1", depth + 1);
            Operand right = operand(expression.get(2), location + "/2", depth + 1);
            if (expression.get(1) instanceof String && expression.get(2) instanceof String) {
                throw type(location, "legacyFilter");
            }
            if (left.geometry() || right.geometry()) {
                return geometryComparison(left, right, operation, location);
            }
            if (left.operand() instanceof PortrayalOperand.TypedLiteral leftLiteral
                    && right.operand() instanceof PortrayalOperand.TypedLiteral rightLiteral) {
                return new PortrayalPredicate.Constant(
                        compareConstants(leftLiteral.value(), rightLiteral.value(), operation));
            }
            PortrayalOperand.Property property =
                    left.operand() instanceof PortrayalOperand.Property candidate
                            ? candidate
                            : right.operand() instanceof PortrayalOperand.Property candidate
                                    ? candidate
                                    : null;
            PortrayalOperand.TypedLiteral literal =
                    left.operand() instanceof PortrayalOperand.TypedLiteral candidate
                            ? candidate
                            : right.operand() instanceof PortrayalOperand.TypedLiteral candidate
                                    ? candidate
                                    : null;
            if (property != null
                    && literal != null
                    && literal.value().kind() == ThematicValue.Kind.NULL) {
                return nullComparison(property, operation, location);
            }
            try {
                return new PortrayalPredicate.Comparison(
                        MapLibreFilters.comparison(operation), left.operand(), right.operand());
            } catch (IllegalArgumentException failure) {
                throw type(location, "comparison");
            }
        }

        private PortrayalPredicate geometryComparison(
                Operand left, Operand right, String operation, String location) {
            Operand literal = left.geometry() ? right : left;
            if (left.geometry() == right.geometry()
                    || !(literal.operand() instanceof PortrayalOperand.TypedLiteral typed)
                    || typed.value().kind() != ThematicValue.Kind.TEXT) {
                throw type(location, "geometryComparison");
            }
            PortrayalGeometryType type =
                    switch ((String) typed.value().value()) {
                        case "Point" -> PortrayalGeometryType.POINT;
                        case "LineString" -> PortrayalGeometryType.LINE_STRING;
                        case "Polygon" -> PortrayalGeometryType.POLYGON;
                        default -> throw type(location, "geometryType");
                    };
            PortrayalPredicate match = new PortrayalPredicate.GeometryTypeIs(Set.of(type));
            return switch (operation) {
                case "==" -> match;
                case "!=" ->
                        new PortrayalPredicate.Logical(
                                PortrayalLogicalOperator.NOT, List.of(match));
                default -> throw type(location, "geometryOrdering");
            };
        }

        private PortrayalPredicate nullComparison(
                PortrayalOperand.Property property, String operation, String location) {
            PortrayalPredicate exists = new PortrayalPredicate.Exists(property);
            PortrayalPredicate isNull = new PortrayalPredicate.IsNull(property);
            return switch (operation) {
                case "==" ->
                        new PortrayalPredicate.Logical(
                                PortrayalLogicalOperator.OR,
                                List.of(
                                        new PortrayalPredicate.Logical(
                                                PortrayalLogicalOperator.NOT, List.of(exists)),
                                        isNull));
                case "!=" ->
                        new PortrayalPredicate.Logical(
                                PortrayalLogicalOperator.AND,
                                List.of(
                                        exists,
                                        new PortrayalPredicate.Logical(
                                                PortrayalLogicalOperator.NOT, List.of(isNull))));
                default -> throw type(location, "nullOrdering");
            };
        }

        private Operand operand(Object value, String location, int depth) {
            node(location, depth);
            if (value instanceof List<?>) {
                List<Object> expression = expression(value, location);
                String operation = operation(expression, location);
                return switch (operation) {
                    case "get" ->
                            new Operand(
                                    property(
                                            requireOperand(expression, location),
                                            location + "/1",
                                            true),
                                    false);
                    case "geometry-type" -> {
                        requireSize(expression, 1, location);
                        yield Operand.GEOMETRY;
                    }
                    case "literal" -> {
                        requireSize(expression, 2, location);
                        yield new Operand(literal(expression.get(1), location + "/1"), false);
                    }
                    default -> throw unsupported(location);
                };
            }
            return new Operand(literal(value, location), false);
        }

        private PortrayalOperand.Property property(
                Object value, String location, boolean expressionName) {
            if (!(value instanceof String name)) {
                throw type(location, expressionName ? "propertyName" : "hasName");
            }
            try {
                return new PortrayalOperand.Property(name);
            } catch (IllegalArgumentException failure) {
                throw type(location, "propertyName");
            }
        }

        private PortrayalOperand.TypedLiteral literal(Object value, String location) {
            ThematicValue typed;
            if (value == AttributeNull.INSTANCE) {
                typed = ThematicValue.nullValue();
            } else if (value instanceof Boolean logical) {
                typed = ThematicValue.logical(logical);
            } else if (value instanceof BigDecimal number) {
                typed = ThematicValue.numeric(number);
            } else if (value instanceof String text) {
                typed = ThematicValue.text(text);
            } else {
                throw type(location, "literal");
            }
            try {
                return new PortrayalOperand.TypedLiteral(typed);
            } catch (IllegalArgumentException failure) {
                throw type(location, "literal");
            }
        }

        private void node(String location, int depth) {
            nodes++;
            if ((nodes & 255) == 0 && cancellation.isCancellationRequested()) {
                throw MapLibreStyles.failure("MAPLIBRE_CANCELLED", location, Map.of());
            }
            long aggregateNodes = (long) precedingNodes + nodes;
            if (aggregateNodes > limits.maximumExpressionNodes()) {
                throw limit(
                        location,
                        "expressionNodes",
                        aggregateNodes,
                        limits.maximumExpressionNodes());
            }
            if (depth > limits.maximumExpressionDepth()) {
                throw limit(location, "expressionDepth", depth, limits.maximumExpressionDepth());
            }
        }
    }

    private static boolean compareConstants(
            ThematicValue left, ThematicValue right, String operation) {
        if ("==".equals(operation)) {
            return left.equals(right);
        }
        if ("!=".equals(operation)) {
            if ((left.kind() == ThematicValue.Kind.NULL)
                    != (right.kind() == ThematicValue.Kind.NULL)) {
                return true;
            }
            return !left.equals(right);
        }
        if ((left.kind() == ThematicValue.Kind.NULL) != (right.kind() == ThematicValue.Kind.NULL)) {
            return false;
        }
        if (left.kind() != right.kind()) {
            return false;
        }
        int ordering =
                switch (left.kind()) {
                    case NUMERIC ->
                            ((BigDecimal) left.value()).compareTo((BigDecimal) right.value());
                    case TEXT -> compareCodePoints((String) left.value(), (String) right.value());
                    case LOGICAL, NULL, DATE -> Integer.MIN_VALUE;
                };
        if (ordering == Integer.MIN_VALUE) {
            return false;
        }
        return switch (operation) {
            case "<" -> ordering < 0;
            case "<=" -> ordering <= 0;
            case ">" -> ordering > 0;
            case ">=" -> ordering >= 0;
            default -> throw new AssertionError(operation);
        };
    }

    private static PortrayalComparison comparison(String operation) {
        return switch (operation) {
            case "==" -> PortrayalComparison.EQUAL;
            case "!=" -> PortrayalComparison.NOT_EQUAL;
            case "<" -> PortrayalComparison.LESS_THAN;
            case "<=" -> PortrayalComparison.LESS_THAN_OR_EQUAL;
            case ">" -> PortrayalComparison.GREATER_THAN;
            case ">=" -> PortrayalComparison.GREATER_THAN_OR_EQUAL;
            default -> throw new AssertionError(operation);
        };
    }

    private static int compareCodePoints(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftPoint = left.codePointAt(leftIndex);
            int rightPoint = right.codePointAt(rightIndex);
            int compared = Integer.compare(leftPoint, rightPoint);
            if (compared != 0) {
                return compared;
            }
            leftIndex += Character.charCount(leftPoint);
            rightIndex += Character.charCount(rightPoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> expression(Object value, String location) {
        if (!(value instanceof List<?> expression) || expression.isEmpty()) {
            throw type(location, "expression");
        }
        return (List<Object>) expression;
    }

    private static String operation(List<Object> expression, String location) {
        if (!(expression.getFirst() instanceof String operation)) {
            throw type(location + "/0", "operator");
        }
        return operation;
    }

    private static Object requireOperand(List<Object> expression, String location) {
        requireSize(expression, 2, location);
        return expression.get(1);
    }

    private static void requireSize(List<Object> expression, int expected, String location) {
        if (expression.size() != expected) {
            throw type(location, "arity");
        }
    }

    private static MapLibreReadException unsupported(String location) {
        return MapLibreStyles.failure(
                "MAPLIBRE_EXPRESSION_UNSUPPORTED", location, Map.of("reason", "operator"));
    }

    private static MapLibreReadException type(String location, String reason) {
        return MapLibreStyles.failure(
                "MAPLIBRE_EXPRESSION_TYPE", location, Map.of("reason", reason));
    }

    private static MapLibreReadException limit(
            String location, String name, long actual, long maximum) {
        return MapLibreStyles.failure(
                "MAPLIBRE_LIMIT_EXCEEDED",
                location,
                Map.of(
                        "limit", name,
                        "actual", Long.toString(actual),
                        "maximum", Long.toString(maximum)));
    }

    private record Operand(PortrayalOperand operand, boolean geometry) {
        private static final Operand GEOMETRY =
                new Operand(new PortrayalOperand.TypedLiteral(ThematicValue.text("")), true);
    }

    record CompiledFilter(PortrayalPredicate predicate, int nodes) {}
}
