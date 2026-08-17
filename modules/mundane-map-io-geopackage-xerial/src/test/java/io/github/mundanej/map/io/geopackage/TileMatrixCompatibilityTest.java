package io.github.mundanej.map.io.geopackage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.TileCoverage;
import io.github.mundanej.map.core.TileCoverageLimits;
import io.github.mundanej.map.core.TileMatrix;
import io.github.mundanej.map.core.TileMatrixAlgorithms;
import io.github.mundanej.map.core.TileMatrixAxisOrder;
import io.github.mundanej.map.core.TileMatrixCorner;
import io.github.mundanej.map.core.TileMatrixIndex;
import io.github.mundanej.map.core.TileMatrixSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TileMatrixCompatibilityTest {
    @Test
    void geoPackageMatrixFixtureMapsToNeutralTileMatrixWithoutAxisGuessing() {
        Envelope bounds = new Envelope(0, 0, 1_024, 512);
        GeoPackageTileMatrix stored = new GeoPackageTileMatrix(1, 2, 1, 2, 2);
        TileMatrix matrix =
                new TileMatrix(
                        Integer.toString(stored.zoom()),
                        stored.pixelXSize() / 0.00028,
                        stored.pixelXSize(),
                        new Coordinate(bounds.minX(), bounds.maxY()),
                        TileMatrixCorner.TOP_LEFT,
                        256,
                        256,
                        stored.matrixWidth(),
                        stored.matrixHeight(),
                        List.of());
        TileMatrixSet set =
                new TileMatrixSet(
                        "geopackage-fixture",
                        CrsDefinitions.EPSG_3857,
                        TileMatrixAxisOrder.XY,
                        bounds,
                        List.of(matrix));

        TileCoverage coverage =
                TileMatrixAlgorithms.coverage(
                        set, "1", new Envelope(512, 0, 1_024, 512), new TileCoverageLimits(1));
        assertEquals(List.of(new TileMatrixIndex("1", 0, 1)), coverage.tiles());
        assertEquals(
                new Envelope(512, 0, 1_024, 512),
                TileMatrixAlgorithms.tileEnvelope(set, coverage.tiles().get(0)));
    }
}
