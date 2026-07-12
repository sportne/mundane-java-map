package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** A polygon with one closed exterior ring and zero or more closed interior rings. */
public record PolygonGeometry(CoordinateSequence exterior, List<CoordinateSequence> holes)
        implements Geometry {
    /** Creates a polygon. */
    public PolygonGeometry {
        validateRing(Objects.requireNonNull(exterior, "exterior"), "exterior");
        holes = List.copyOf(Objects.requireNonNull(holes, "holes"));
        for (CoordinateSequence hole : holes) {
            validateRing(Objects.requireNonNull(hole, "hole"), "hole");
        }
    }

    /** Creates a polygon with no holes. */
    public PolygonGeometry(CoordinateSequence exterior) {
        this(exterior, List.of());
    }

    @Override
    public Envelope envelope() {
        return exterior.envelope();
    }

    private static void validateRing(CoordinateSequence ring, String role) {
        if (ring.size() < 4 || !ring.isClosed()) {
            throw new IllegalArgumentException(
                    "A polygon " + role + " ring requires at least four coordinates and closure");
        }
    }
}
