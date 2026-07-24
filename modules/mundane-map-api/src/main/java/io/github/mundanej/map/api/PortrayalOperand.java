package io.github.mundanej.map.api;

import java.util.Objects;

/** Closed immutable operand used by bounded portrayal predicates. */
public sealed interface PortrayalOperand
        permits PortrayalOperand.Property, PortrayalOperand.Literal, PortrayalOperand.TypedLiteral {
    /**
     * Exact canonical feature-attribute lookup.
     *
     * @param name non-blank bounded attribute name
     */
    record Property(String name) implements PortrayalOperand {
        /** Validates the attribute name. */
        public Property {
            Objects.requireNonNull(name, "name");
            if (name.isBlank() || !name.equals(name.strip()) || name.length() > 256) {
                throw new IllegalArgumentException("name must be stripped, non-blank, and bounded");
            }
        }
    }

    /**
     * Retained literal text converted only against a compared property kind.
     *
     * @param text bounded exact literal text
     */
    record Literal(String text) implements PortrayalOperand {
        /** Validates the literal. */
        public Literal {
            Objects.requireNonNull(text, "text");
            if (text.length() > 4_096) {
                throw new IllegalArgumentException("text must contain at most 4096 characters");
            }
        }
    }

    /**
     * Exact typed null, boolean, finite-decimal, or string literal.
     *
     * @param value exact immutable value
     */
    record TypedLiteral(ThematicValue value) implements PortrayalOperand {
        /** Validates the closed literal profile. */
        public TypedLiteral {
            Objects.requireNonNull(value, "value");
            if (value.kind() == ThematicValue.Kind.DATE) {
                throw new IllegalArgumentException("date is not a supported typed literal");
            }
            if (value.kind() == ThematicValue.Kind.TEXT
                    && ((String) value.value()).length() > 4_096) {
                throw new IllegalArgumentException("text must contain at most 4096 characters");
            }
        }
    }
}
