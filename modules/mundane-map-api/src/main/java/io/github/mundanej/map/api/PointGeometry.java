package io.github.mundanej.map.api;

import java.util.Objects;

/** A point geometry. */
public record PointGeometry(Coordinate coordinate) implements Geometry {
    /** Creates a point geometry. */
    public PointGeometry {
        Objects.requireNonNull(coordinate, "coordinate");
    }

    @Override
    public Envelope envelope() {
        return Envelope.at(coordinate);
    }
}

