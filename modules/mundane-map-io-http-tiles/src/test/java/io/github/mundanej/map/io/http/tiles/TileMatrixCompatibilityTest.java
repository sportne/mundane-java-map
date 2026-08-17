package io.github.mundanej.map.io.http.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.core.CommonTileMatrixSets;
import io.github.mundanej.map.core.TileCoverage;
import io.github.mundanej.map.core.TileCoverageLimits;
import io.github.mundanej.map.core.TileCoverageStatus;
import io.github.mundanej.map.core.TileMatrixAlgorithms;
import io.github.mundanej.map.core.TileMatrixIndex;
import io.github.mundanej.map.core.TileMatrixSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TileMatrixCompatibilityTest {
    @Test
    void legacyXyzRegionMatchesNeutralWebMercatorQuadCoverage() {
        XyzTileRegion region = new XyzTileRegion(2, 1, 1, 2, 2);
        TileMatrixSet set = CommonTileMatrixSets.legacyXyz();
        TileCoverage coverage =
                TileMatrixAlgorithms.coverage(set, "2", region.bounds(), new TileCoverageLimits(4));

        assertEquals(TileCoverageStatus.COMPLETE, coverage.status());
        assertEquals(List.of(region.bounds()), coverage.intersections());
        assertEquals(
                List.of(
                        new TileMatrixIndex("2", 1, 1),
                        new TileMatrixIndex("2", 1, 2),
                        new TileMatrixIndex("2", 2, 1),
                        new TileMatrixIndex("2", 2, 2)),
                coverage.tiles());
        assertEquals(
                region.bounds(),
                CommonTileMatrixSets.xyzEnvelope(2, 1, 1)
                        .union(CommonTileMatrixSets.xyzEnvelope(2, 2, 2)));
    }
}
