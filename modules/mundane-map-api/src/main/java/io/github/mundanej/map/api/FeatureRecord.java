package io.github.mundanej.map.api;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable unstyled source feature.
 *
 * @param id non-blank stable source-record identity
 * @param name non-null display name, which may be empty
 * @param geometry immutable source geometry
 * @param attributes attributes defensively canonicalized into insertion order
 */
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

    /**
     * Returns the immutable insertion-ordered owned attributes.
     *
     * @return immutable canonical attributes
     */
    @Override
    public Map<String, Object> attributes() {
        return java.util.Collections.unmodifiableMap(attributes);
    }
}
