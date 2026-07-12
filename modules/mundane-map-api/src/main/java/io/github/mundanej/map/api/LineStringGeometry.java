package io.github.mundanej.map.api;

import java.util.Objects;

/** A connected sequence of two or more points. */
public record LineStringGeometry(CoordinateSequence coordinates) implements Geometry {
    /** Creates a line string. */
    public LineStringGeometry {
        Objects.requireNonNull(coordinates, "coordinates");
        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("A line string requires at least two coordinates");
        }
    }

    @Override
    public Envelope envelope() {
        return coordinates.envelope();
    }
}

