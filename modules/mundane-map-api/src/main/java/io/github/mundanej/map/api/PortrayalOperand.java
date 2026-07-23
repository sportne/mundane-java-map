package io.github.mundanej.map.api;

import java.util.Objects;

/** Closed immutable operand used by bounded portrayal predicates. */
public sealed interface PortrayalOperand
        permits PortrayalOperand.Property, PortrayalOperand.Literal {
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
}
