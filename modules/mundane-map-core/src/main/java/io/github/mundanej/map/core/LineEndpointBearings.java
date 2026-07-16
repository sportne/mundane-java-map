package io.github.mundanej.map.core;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable outward screen bearings for the two ends of one line part.
 *
 * @param startBearingDegrees outward start bearing in clockwise screen degrees, if defined
 * @param endBearingDegrees outward end bearing in clockwise screen degrees, if defined
 */
public record LineEndpointBearings(
        OptionalDouble startBearingDegrees, OptionalDouble endBearingDegrees) {
    /** Creates an endpoint-bearing pair. */
    public LineEndpointBearings {
        Objects.requireNonNull(startBearingDegrees, "startBearingDegrees");
        Objects.requireNonNull(endBearingDegrees, "endBearingDegrees");
    }
}
