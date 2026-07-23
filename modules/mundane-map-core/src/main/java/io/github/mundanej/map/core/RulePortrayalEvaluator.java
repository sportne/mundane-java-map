package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.PortrayalComparison;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalOperand;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.ResolvedFeaturePortrayal;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class RulePortrayalEvaluator {
    private final RulePortrayalPlan plan;
    private final List<String> requiredAttributes;

    RulePortrayalEvaluator(RulePortrayalPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        Set<String> attributes = new LinkedHashSet<>();
        for (PortrayalRule rule : plan.rules()) {
            rule.predicate().ifPresent(predicate -> collect(predicate, attributes));
        }
        requiredAttributes = List.copyOf(attributes);
    }

    List<String> requiredAttributes() {
        return requiredAttributes;
    }

    List<Symbol> reachableSymbols(SymbolRole role) {
        return plan.rules().stream()
                .map(rule -> symbols(rule, role))
                .flatMap(List::stream)
                .toList();
    }

    boolean requiresScaleContext() {
        return plan.requiresScaleContext();
    }

    ResolvedFeaturePortrayal resolve(
            Map<String, Object> attributes, PortrayalEvaluationContext context) {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(context, "context");
        if (plan.requiresScaleContext() && context.scaleDenominator().isEmpty()) {
            throw new SymbolException(
                    SymbolException.PORTRAYAL_SCALE_CONTEXT_REQUIRED,
                    "Scale-constrained portrayal requires an explicit denominator",
                    Map.of());
        }
        double scale = context.scaleDenominator().orElse(0.0);
        List<Symbol> markers = new ArrayList<>();
        List<Symbol> lines = new ArrayList<>();
        List<Symbol> fills = new ArrayList<>();
        PortrayalRule activeElse = null;
        boolean ordinaryMatched = false;
        for (PortrayalRule rule : plan.rules()) {
            if (rule.scale().constrained() && !rule.scale().includes(scale)) {
                continue;
            }
            if (rule.elseRule()) {
                activeElse = rule;
                continue;
            }
            if (rule.predicate().isPresent() && !test(rule.predicate().orElseThrow(), attributes)) {
                continue;
            }
            ordinaryMatched = true;
            append(rule, markers, lines, fills);
        }
        if (!ordinaryMatched && activeElse != null) {
            append(activeElse, markers, lines, fills);
        }
        return new ResolvedFeaturePortrayal(
                compose(markers, SymbolRole.MARKER),
                compose(lines, SymbolRole.LINE),
                compose(fills, SymbolRole.FILL));
    }

    private static void append(
            PortrayalRule rule, List<Symbol> markers, List<Symbol> lines, List<Symbol> fills) {
        markers.addAll(rule.markers());
        lines.addAll(rule.lines());
        fills.addAll(rule.fills());
    }

    private static List<Symbol> symbols(PortrayalRule rule, SymbolRole role) {
        return switch (role) {
            case MARKER -> rule.markers();
            case LINE -> rule.lines();
            case FILL -> rule.fills();
            case LEGACY_GEOMETRY ->
                    throw new IllegalArgumentException("legacy role is unsupported");
        };
    }

    private static Optional<Symbol> compose(List<Symbol> symbols, SymbolRole role) {
        if (symbols.isEmpty()) {
            return Optional.empty();
        }
        Symbol result = symbols.size() == 1 ? symbols.getFirst() : CompositeSymbol.of(symbols, 1.0);
        if (result.role() != role) {
            throw new IllegalStateException("resolved portrayal role mismatch");
        }
        return Optional.of(result);
    }

    private static boolean test(PortrayalPredicate predicate, Map<String, Object> attributes) {
        if (predicate instanceof PortrayalPredicate.IsNull isNull) {
            String name = isNull.property().name();
            return attributes.containsKey(name) && attributes.get(name) == AttributeNull.INSTANCE;
        }
        if (predicate instanceof PortrayalPredicate.Comparison comparison) {
            return compare(
                    comparison.operation(),
                    value(comparison.left(), attributes),
                    value(comparison.right(), attributes));
        }
        if (predicate instanceof PortrayalPredicate.Between between) {
            OperandValue value = value(between.property(), attributes);
            return compare(
                            PortrayalComparison.GREATER_THAN_OR_EQUAL,
                            value,
                            value(between.lower(), attributes))
                    && compare(
                            PortrayalComparison.LESS_THAN_OR_EQUAL,
                            value,
                            value(between.upper(), attributes));
        }
        PortrayalPredicate.Logical logical = (PortrayalPredicate.Logical) predicate;
        return switch (logical.operator()) {
            case NOT -> !test(logical.children().getFirst(), attributes);
            case AND -> logical.children().stream().allMatch(child -> test(child, attributes));
            case OR -> logical.children().stream().anyMatch(child -> test(child, attributes));
        };
    }

    private static boolean compare(
            PortrayalComparison operation, OperandValue left, OperandValue right) {
        if (left.missing() || right.missing()) {
            return false;
        }
        ThematicValue leftValue = left.value();
        ThematicValue rightValue = right.value();
        if (left.literal() && right.literal()) {
            return false;
        }
        if (left.literal()) {
            leftValue = convert((String) leftValue.value(), rightValue).orElse(null);
        } else if (right.literal()) {
            rightValue = convert((String) rightValue.value(), leftValue).orElse(null);
        }
        if (leftValue == null || rightValue == null) {
            return false;
        }
        if (operation == PortrayalComparison.EQUAL) {
            return leftValue.equals(rightValue);
        }
        if (operation == PortrayalComparison.NOT_EQUAL) {
            return !leftValue.equals(rightValue);
        }
        Integer ordering = ordering(leftValue, rightValue);
        if (ordering == null) {
            return false;
        }
        return switch (operation) {
            case LESS_THAN -> ordering < 0;
            case LESS_THAN_OR_EQUAL -> ordering <= 0;
            case GREATER_THAN -> ordering > 0;
            case GREATER_THAN_OR_EQUAL -> ordering >= 0;
            case EQUAL, NOT_EQUAL -> throw new AssertionError("handled above");
        };
    }

    private static Integer ordering(ThematicValue left, ThematicValue right) {
        if (left.kind() != right.kind()) {
            return null;
        }
        return switch (left.kind()) {
            case NUMERIC -> ((BigDecimal) left.value()).compareTo((BigDecimal) right.value());
            case TEXT -> ((String) left.value()).compareTo((String) right.value());
            case DATE -> ((LocalDate) left.value()).compareTo((LocalDate) right.value());
            case LOGICAL, NULL -> null;
        };
    }

    private static Optional<ThematicValue> convert(String literal, ThematicValue target) {
        try {
            return switch (target.kind()) {
                case TEXT -> Optional.of(ThematicValue.text(literal));
                case LOGICAL ->
                        "true".equals(literal)
                                ? Optional.of(ThematicValue.logical(true))
                                : "false".equals(literal)
                                        ? Optional.of(ThematicValue.logical(false))
                                        : Optional.empty();
                case NUMERIC -> Optional.of(ThematicValue.numeric(new BigDecimal(literal)));
                case DATE -> Optional.of(ThematicValue.date(LocalDate.parse(literal)));
                case NULL -> Optional.empty();
            };
        } catch (NumberFormatException | DateTimeParseException failure) {
            return Optional.empty();
        }
    }

    private static OperandValue value(PortrayalOperand operand, Map<String, Object> attributes) {
        if (operand instanceof PortrayalOperand.Literal literal) {
            return new OperandValue(false, true, ThematicValue.text(literal.text()));
        }
        String name = ((PortrayalOperand.Property) operand).name();
        if (!attributes.containsKey(name)) {
            return OperandValue.MISSING;
        }
        return ThematicValue.fromAttribute(attributes.get(name))
                .map(value -> new OperandValue(false, false, value))
                .orElse(OperandValue.MISSING);
    }

    private static void collect(PortrayalPredicate predicate, Set<String> attributes) {
        if (predicate instanceof PortrayalPredicate.IsNull isNull) {
            attributes.add(isNull.property().name());
        } else if (predicate instanceof PortrayalPredicate.Comparison comparison) {
            collect(comparison.left(), attributes);
            collect(comparison.right(), attributes);
        } else if (predicate instanceof PortrayalPredicate.Between between) {
            collect(between.property(), attributes);
            collect(between.lower(), attributes);
            collect(between.upper(), attributes);
        } else {
            ((PortrayalPredicate.Logical) predicate)
                    .children()
                    .forEach(child -> collect(child, attributes));
        }
    }

    private static void collect(PortrayalOperand operand, Set<String> attributes) {
        if (operand instanceof PortrayalOperand.Property property) {
            attributes.add(property.name());
        }
    }

    private record OperandValue(boolean missing, boolean literal, ThematicValue value) {
        private static final OperandValue MISSING = new OperandValue(true, false, null);
    }
}
