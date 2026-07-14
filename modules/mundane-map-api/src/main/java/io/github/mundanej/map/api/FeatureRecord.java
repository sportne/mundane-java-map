package io.github.mundanej.map.api;

import java.util.Map;
import java.util.Objects;

/** Immutable unstyled source feature. */
public record FeatureRecord(
        String id, String name, Geometry geometry, Map<String, Object> attributes) {
    /** Validates and defensively canonicalizes a record. */
    public FeatureRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(geometry, "geometry");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        attributes = AttributeValues.canonicalize(attributes);
    }

    /** Returns the immutable insertion-ordered owned attributes. */
    @Override
    public Map<String, Object> attributes() {
        return java.util.Collections.unmodifiableMap(attributes);
    }
}
