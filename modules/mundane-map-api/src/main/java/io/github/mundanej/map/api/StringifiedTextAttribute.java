package io.github.mundanej.map.api;

/**
 * Exact attribute converted through the closed null/boolean/number/string text profile.
 *
 * @param attribute exact canonical attribute name
 */
public record StringifiedTextAttribute(String attribute) implements LabelTextSource {
    /** Validates the exact attribute name. */
    public StringifiedTextAttribute {
        attribute = AttributeValues.requireName(attribute);
    }
}
