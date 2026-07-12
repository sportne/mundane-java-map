package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Projection;
import java.util.Objects;

/** Projection helpers for monotonic rectangular source envelopes. */
public final class ProjectionEnvelopes {
    private ProjectionEnvelopes() {}

    /** Projects all four corners and returns their enclosing world envelope. */
    public static Envelope project(Projection projection, Envelope source) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(source, "source");
        Coordinate first = projection.project(new Coordinate(source.minX(), source.minY()));
        Envelope result = Envelope.at(first);
        result =
                result.union(
                        Envelope.at(
                                projection.project(new Coordinate(source.minX(), source.maxY()))));
        result =
                result.union(
                        Envelope.at(
                                projection.project(new Coordinate(source.maxX(), source.minY()))));
        return result.union(
                Envelope.at(projection.project(new Coordinate(source.maxX(), source.maxY()))));
    }
}
