package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** Closed immutable predicate algebra without callbacks or executable extensions. */
public sealed interface PortrayalPredicate
        permits PortrayalPredicate.IsNull,
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
