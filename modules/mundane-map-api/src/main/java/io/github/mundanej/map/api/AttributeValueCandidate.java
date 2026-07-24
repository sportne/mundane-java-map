package io.github.mundanej.map.api;

import java.util.Objects;

/** One closed attribute or literal candidate in an ordered value conversion. */
public sealed interface AttributeValueCandidate
        permits AttributeValueCandidate.Attribute, AttributeValueCandidate.Literal {
    /**
     * Exact canonical attribute candidate.
     *
     * @param name exact attribute name
     */
    record Attribute(String name) implements AttributeValueCandidate {
        /** Validates the exact attribute name. */
        public Attribute {
            name = AttributeValues.requireName(name);
        }
    }

    /**
     * Canonical literal candidate.
     *
     * @param value null, boolean, finite decimal, or string literal
     */
    record Literal(ThematicValue value) implements AttributeValueCandidate {
        /** Validates the immutable canonical literal. */
        public Literal {
            Objects.requireNonNull(value, "value");
            if (value.kind() == ThematicValue.Kind.DATE) {
                throw new IllegalArgumentException(
                        "conversion literal must be null, boolean, number, or string");
            }
        }
    }
}
