package io.github.mundanej.map.core;

/**
 * Prospective limits for one tile-coverage enumeration.
 *
 * @param maximumTiles maximum materialized tile addresses
 */
public record TileCoverageLimits(int maximumTiles) {
    /** Hard maximum materialized addresses in one result. */
    public static final int HARD_MAXIMUM_TILES = 1_000_000;

    /** Validates the immutable ceiling. */
    public TileCoverageLimits {
        if (maximumTiles <= 0 || maximumTiles > HARD_MAXIMUM_TILES) {
            throw new IllegalArgumentException("Tile coverage limit is outside its profile");
        }
    }

    /**
     * Returns the standard interactive coverage limit.
     *
     * @return limit of 100,000 materialized tile addresses
     */
    public static TileCoverageLimits defaults() {
        return new TileCoverageLimits(100_000);
    }
}
