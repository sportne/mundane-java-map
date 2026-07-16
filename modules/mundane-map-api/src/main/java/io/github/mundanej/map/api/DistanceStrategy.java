package io.github.mundanej.map.api;

/** CRS-bound distance calculation with metre results. */
public interface DistanceStrategy {
    /**
     * Returns the exact coordinate CRS accepted by this strategy.
     *
     * @return accepted coordinate CRS
     */
    CrsDefinition coordinateCrs();

    /**
     * Calculates a checked distance between two coordinates.
     *
     * @param start first coordinate in {@link #coordinateCrs()}
     * @param end second coordinate in {@link #coordinateCrs()}
     * @return finite non-negative distance in metres
     * @throws CrsException when either coordinate lies outside {@link #coordinateCrs()} or the
     *     calculation cannot produce a finite result
     */
    DistanceResult distance(Coordinate start, Coordinate end);
}
