package io.github.mundanej.map.api;

/** CRS-bound distance calculation with metre results. */
public interface DistanceStrategy {
    /** Returns the exact coordinate CRS accepted by this strategy. */
    CrsDefinition coordinateCrs();

    /**
     * Calculates a checked distance between two coordinates.
     *
     * @throws CrsException when either coordinate lies outside {@link #coordinateCrs()} or the
     *     calculation cannot produce a finite result
     */
    DistanceResult distance(Coordinate start, Coordinate end);
}
