package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Closed immutable predicate algebra without callbacks or executable extensions. */
public sealed interface PortrayalPredicate
        permits PortrayalPredicate.IsNull,
                PortrayalPredicate.Exists,
                PortrayalPredicate.GeometryTypeIs,
                PortrayalPredicate.Constant,
                PortrayalPredicate.Comparison,
                PortrayalPredicate.Between,
                PortrayalPredicate.Logical {
    /**
     * Tests explicit canonical null, distinct from a missing attribute.
     *
     * @param property exact property operand
     */
    record IsNull(PortrayalOperand.Property property) implements PortrayalPredicate {
        /** Validates the property. */
        public IsNull {
            Objects.requireNonNull(property, "property");
        }
    }

    /**
     * Tests whether an attribute is present, including explicit null.
     *
     * @param property exact property operand
     */
    record Exists(PortrayalOperand.Property property) implements PortrayalPredicate {
        /** Validates the property. */
        public Exists {
            Objects.requireNonNull(property, "property");
        }
    }

    /**
     * Tests the normalized current geometry category.
     *
     * @param types non-empty immutable accepted categories
     */
    record GeometryTypeIs(Set<PortrayalGeometryType> types) implements PortrayalPredicate {
        /** Validates and defensively copies the categories. */
        public GeometryTypeIs {
            types = Set.copyOf(Objects.requireNonNull(types, "types"));
            if (types.isEmpty()) {
                throw new IllegalArgumentException("types must not be empty");
            }
            types.forEach(type -> Objects.requireNonNull(type, "type"));
        }
    }

    /**
     * Constant-folded predicate.
     *
     * @param value result
     */
    record Constant(boolean value) implements PortrayalPredicate {}

    /**
     * Compares two operands, at least one of which is a property.
     *
     * @param operation closed comparison operation
     * @param left left operand
     * @param right right operand
     */
    record Comparison(PortrayalComparison operation, PortrayalOperand left, PortrayalOperand right)
            implements PortrayalPredicate {
        /** Validates the comparison. */
        public Comparison {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            if (!(left instanceof PortrayalOperand.Property)
                    && !(right instanceof PortrayalOperand.Property)) {
                throw new IllegalArgumentException("a comparison requires a property operand");
            }
        }
    }

    /**
     * Tests an inclusive lower and upper boundary.
     *
     * @param property exact property operand
     * @param lower inclusive lower operand
     * @param upper inclusive upper operand
     */
    record Between(
            PortrayalOperand.Property property, PortrayalOperand lower, PortrayalOperand upper)
            implements PortrayalPredicate {
        /** Validates the operands. */
        public Between {
            Objects.requireNonNull(property, "property");
            Objects.requireNonNull(lower, "lower");
            Objects.requireNonNull(upper, "upper");
        }
    }

    /**
     * Bounded boolean composition.
     *
     * @param operator closed boolean operation
     * @param children immutable child predicates
     */
    record Logical(PortrayalLogicalOperator operator, List<PortrayalPredicate> children)
            implements PortrayalPredicate {
        /** Validates and defensively copies the children. */
        public Logical {
            Objects.requireNonNull(operator, "operator");
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            children.forEach(child -> Objects.requireNonNull(child, "child"));
            int required = operator == PortrayalLogicalOperator.NOT ? 1 : 2;
            if ((operator == PortrayalLogicalOperator.NOT && children.size() != 1)
                    || (operator != PortrayalLogicalOperator.NOT && children.size() < required)
                    || children.size() > 1_024) {
                throw new IllegalArgumentException("invalid logical child count");
            }
        }
    }
}
