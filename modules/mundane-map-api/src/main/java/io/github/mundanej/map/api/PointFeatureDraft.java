package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable point-feature content awaiting a coordinate from an editing interaction.
 *
 * @param id stable non-blank feature identifier
 * @param name feature display name
 * @param attributes immutable canonical feature attributes
 */
public record PointFeatureDraft(String id, String name, Map<String, Object> attributes) {
    /** Validates identity and defensively canonicalizes attributes like {@link FeatureRecord}. */
    public PointFeatureDraft {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        attributes = AttributeValues.canonicalize(attributes);
    }

    /**
     * Returns immutable insertion-ordered canonical attributes.
     *
     * @return immutable canonical attributes
     */
    @Override
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Creates the immutable feature at one coordinate.
     *
     * @param coordinate point coordinate
     * @return complete immutable feature record
     */
    public FeatureRecord at(Coordinate coordinate) {
        return new FeatureRecord(id, name, new PointGeometry(coordinate), attributes);
    }
}
