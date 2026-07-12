package io.github.mundanej.map.api;

import java.util.Map;
import java.util.Objects;

/** A named geometry with stable identity, attributes, and display style. */
public record Feature(
        String id,
        String name,
        Geometry geometry,
        Map<String, Object> attributes,
        FeatureStyle style) {
    /** Creates a feature and defensively copies its attributes. */
    public Feature {
        id = requireText(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(geometry, "geometry");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        Objects.requireNonNull(style, "style");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
