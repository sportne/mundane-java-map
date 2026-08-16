package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * An explicitly typed empty primitive or homogeneous multi-geometry.
 *
 * @param kind non-collection empty family
 * @param dimension dimensional model retained for round trips
 */
public record EmptyGeometry(GeometryKind kind, GeometryDimension dimension) implements Geometry {
    /** Creates an empty value with an explicit kind and dimensional model. */
    public EmptyGeometry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(dimension, "dimension");
        if (kind == GeometryKind.GEOMETRY_COLLECTION) {
            throw new IllegalArgumentException(
                    "Use GeometryCollection.empty for an empty geometry collection");
        }
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public java.util.Optional<Envelope> bounds() {
        return java.util.Optional.empty();
    }

    @Override
    public Envelope envelope() {
        throw GeometryException.emptyEnvelope(kind);
    }
}
