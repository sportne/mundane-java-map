package io.github.mundanej.map.api;

import java.util.Objects;

/** Immutable ordered packed multipoint geometry. */
public record MultiPointGeometry(CoordinateSequence coordinates) implements Geometry {
    /** Creates a non-empty multipoint. */
    public MultiPointGeometry {
        Objects.requireNonNull(coordinates, "coordinates");
    }

    @Override
    public Envelope envelope() {
        return coordinates.envelope();
    }
}
