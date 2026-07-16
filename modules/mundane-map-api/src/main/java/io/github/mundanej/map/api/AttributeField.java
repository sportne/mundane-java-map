package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * One ordered attribute-schema field.
 *
 * @param name non-blank canonical field name
 * @param type canonical value type accepted by the field
 * @param nullable whether {@link AttributeNull} is accepted
 */
public record AttributeField(String name, AttributeType type, boolean nullable) {
    /** Validates the field. */
    public AttributeField {
        name = AttributeValues.requireName(name);
        Objects.requireNonNull(type, "type");
    }

    /**
     * Returns whether a canonical value conforms to this field.
     *
     * @param value canonical attribute value to test
     * @return whether the value conforms
     */
    public boolean accepts(Object value) {
        if (value == AttributeNull.INSTANCE) {
            return nullable;
        }
        return switch (type) {
            case TEXT -> value instanceof String;
            case LOGICAL -> value instanceof Boolean;
            case INTEGER -> value instanceof Long;
            case FLOATING -> value instanceof Double;
            case DECIMAL -> value instanceof java.math.BigDecimal;
            case DATE -> value instanceof java.time.LocalDate;
            case BINARY -> value instanceof AttributeBytes;
        };
    }
}
