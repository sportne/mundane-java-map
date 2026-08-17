package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Envelope;
import java.util.List;
import java.util.Objects;

/**
 * Atomic bounded tile-coverage result.
 *
 * @param status request relationship to the supported domain
 * @param intersections one ordinary intersection or two seam-split intersections
 * @param tiles deterministic row-major unique tile addresses
 */
public record TileCoverage(
        TileCoverageStatus status, List<Envelope> intersections, List<TileMatrixIndex> tiles) {
    /** Validates and defensively copies the complete result. */
    public TileCoverage {
        Objects.requireNonNull(status, "status");
        intersections = List.copyOf(Objects.requireNonNull(intersections, "intersections"));
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
        if (intersections.size() > 2
                || tiles.size() > TileCoverageLimits.HARD_MAXIMUM_TILES
                || (status == TileCoverageStatus.OUTSIDE)
                        != (intersections.isEmpty() && tiles.isEmpty())
                || (status != TileCoverageStatus.OUTSIDE)
                        != (!intersections.isEmpty() && !tiles.isEmpty())) {
            throw new IllegalArgumentException("Tile coverage result is internally inconsistent");
        }
    }
}
