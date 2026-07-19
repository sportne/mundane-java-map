package io.github.mundanej.map.api;

/**
 * Uses one exact canonical text attribute as point-label text.
 *
 * @param attribute exact canonical text attribute name
 */
public record TextAttribute(String attribute) implements LabelTextSource {
    /** Validates the canonical attribute name. */
    public TextAttribute {
        attribute = AttributeValues.requireName(attribute);
    }
}
