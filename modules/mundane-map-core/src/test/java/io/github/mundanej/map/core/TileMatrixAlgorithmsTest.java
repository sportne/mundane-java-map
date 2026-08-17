package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TileMatrixAlgorithmsTest {
    @Test
    void publishedWebMercatorQuadValuesAndLegacyXyzRemainExact() {
        TileMatrixSet set = CommonTileMatrixSets.webMercatorQuad(3);
        TileMatrix zero = set.matrix("0");
        TileMatrix three = set.matrix("3");

        assertEquals("EPSG:3857", set.crs().canonicalIdentifier());
        assertEquals(559_082_264.028717, zero.scaleDenominator(), 1e-6);
        assertEquals(156_543.033928041, zero.cellSize(), 1e-9);
        assertEquals(1, zero.matrixWidth());
        assertEquals(8, three.matrixWidth());
        assertEquals(8, three.matrixHeight());
        assertEquals(
                set.boundingBox(),
                TileMatrixAlgorithms.tileEnvelope(set, new TileMatrixIndex("0", 0, 0)));

        Envelope expected =
                new Envelope(
                        -WebMercatorProjection.WORLD_LIMIT / 2,
                        0,
                        0,
                        WebMercatorProjection.WORLD_LIMIT / 2);
        assertEnvelope(expected, CommonTileMatrixSets.xyzEnvelope(2, 1, 1), 1e-8);
        assertEquals(
                new TileMatrixIndex("2", 1, 1),
                TileMatrixAlgorithms.tileAt(set, "2", expected.center()));
        assertEquals(
                new TileMatrixIndex("2", 3, 3),
                TileMatrixAlgorithms.tileAt(
                        set,
                        "2",
                        new Coordinate(
                                WebMercatorProjection.WORLD_LIMIT,
                                -WebMercatorProjection.WORLD_LIMIT)));
        assertThrows(
                IllegalArgumentException.class, () -> CommonTileMatrixSets.xyzEnvelope(23, 0, 0));
    }

    @Test
    void publishedWorldCrs84QuadUsesTwoByOneLevelZeroAndExplicitSeamCoverage() {
        TileMatrixSet set = CommonTileMatrixSets.worldCrs84Quad(2);
        TileMatrix zero = set.matrix("0");
        assertEquals("OGC:CRS84", set.crs().canonicalIdentifier());
        assertEquals(279_541_132.014358, zero.scaleDenominator());
        assertEquals(0.703125, zero.cellSize());
        assertEquals(2, zero.matrixWidth());
        assertEquals(1, zero.matrixHeight());
        assertEquals(
                new Envelope(-180, -90, 0, 90),
                TileMatrixAlgorithms.tileEnvelope(set, new TileMatrixIndex("0", 0, 0)));
        assertEquals(
                new Envelope(0, -90, 180, 90),
                TileMatrixAlgorithms.tileEnvelope(set, new TileMatrixIndex("0", 0, 1)));

        TileCoverage seam =
                TileMatrixAlgorithms.coverageAcrossHorizontalSeam(
                        set, "1", 170, -10, -170, 10, new TileCoverageLimits(8));
        assertEquals(TileCoverageStatus.COMPLETE, seam.status());
        assertEquals(2, seam.intersections().size());
        assertEquals(
                List.of(
                        new TileMatrixIndex("1", 0, 3),
                        new TileMatrixIndex("1", 1, 3),
                        new TileMatrixIndex("1", 0, 0),
                        new TileMatrixIndex("1", 1, 0)),
                seam.tiles());
        assertCode(
                "TILE_MATRIX_SEAM_RANGE_INVALID",
                () ->
                        TileMatrixAlgorithms.coverageAcrossHorizontalSeam(
                                set, "1", -10, -10, 10, 10, TileCoverageLimits.defaults()));
    }

    @Test
    void axisOrderBottomOriginNonSquareTilesAndVariableWidthsAreIndependent() {
        TileMatrix matrix =
                new TileMatrix(
                        "variable",
                        10_000,
                        1,
                        new Coordinate(10, 100),
                        TileMatrixCorner.BOTTOM_LEFT,
                        10,
                        20,
                        8,
                        4,
                        List.of(new VariableMatrixWidth(2, 1, 2)));
        TileMatrixSet set =
                new TileMatrixSet(
                        "synthetic-variable",
                        CrsDefinitions.EPSG_3857,
                        TileMatrixAxisOrder.YX,
                        new Envelope(100, 10, 180, 90),
                        List.of(matrix));

        assertEquals(1, matrix.coalesce(0));
        assertEquals(2, matrix.coalesce(1));
        assertEquals(4, matrix.columnCount(2));
        assertEquals(8, matrix.columnCount(3));
        assertEquals(
                new Envelope(160, 30, 180, 50),
                TileMatrixAlgorithms.tileEnvelope(set, new TileMatrixIndex("variable", 1, 3)));
        assertEquals(
                new TileMatrixIndex("variable", 1, 3),
                TileMatrixAlgorithms.tileAt(set, "variable", new Coordinate(175, 40)));
        assertEquals(
                new TileMatrixIndex("variable", 0, 7),
                TileMatrixAlgorithms.tileAt(set, "variable", new Coordinate(175, 20)));

        TileCoverage coverage =
                TileMatrixAlgorithms.coverage(
                        set, "variable", set.boundingBox(), new TileCoverageLimits(24));
        assertEquals(TileCoverageStatus.COMPLETE, coverage.status());
        assertEquals(24, coverage.tiles().size());
        assertCode(
                "TILE_MATRIX_COVERAGE_LIMIT",
                () ->
                        TileMatrixAlgorithms.coverage(
                                set, "variable", set.boundingBox(), new TileCoverageLimits(23)));
    }

    @Test
    void coverageClipsOutsideQueriesAndPreservesRowMajorOrder() {
        TileMatrixSet set = CommonTileMatrixSets.webMercatorQuad(2);
        double limit = WebMercatorProjection.WORLD_LIMIT;
        TileCoverage complete =
                TileMatrixAlgorithms.coverage(
                        set, "2", set.boundingBox(), new TileCoverageLimits(16));
        assertEquals(TileCoverageStatus.COMPLETE, complete.status());
        assertEquals(16, complete.tiles().size());
        assertEquals(new TileMatrixIndex("2", 0, 0), complete.tiles().get(0));
        assertEquals(new TileMatrixIndex("2", 3, 3), complete.tiles().get(15));

        TileCoverage clipped =
                TileMatrixAlgorithms.coverage(
                        set,
                        "2",
                        new Envelope(-limit * 2, -limit * 2, 0, 0),
                        new TileCoverageLimits(16));
        assertEquals(TileCoverageStatus.CLIPPED, clipped.status());
        assertEquals(new Envelope(-limit, -limit, 0, 0), clipped.intersections().get(0));
        assertEquals(4, clipped.tiles().size());

        TileCoverage outside =
                TileMatrixAlgorithms.coverage(
                        set,
                        "2",
                        new Envelope(limit + 1, 0, limit + 2, 1),
                        TileCoverageLimits.defaults());
        assertEquals(new TileCoverage(TileCoverageStatus.OUTSIDE, List.of(), List.of()), outside);
        assertCode(
                "TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN",
                () -> TileMatrixAlgorithms.tileAt(set, "2", new Coordinate(limit + 1, 0)));
    }

    @Test
    void scaleSelectionIsExplicitAndFailsBeyondOneSidedProfiles() {
        TileMatrixSet set = CommonTileMatrixSets.webMercatorQuad(3);
        double levelOne = set.matrix("1").scaleDenominator();
        double levelTwo = set.matrix("2").scaleDenominator();
        assertSame(set.matrix("2"), set.select(levelTwo * 1.1, TileMatrixSelectionPolicy.NEAREST));
        assertSame(
                set.matrix("1"),
                set.select(levelTwo * 1.1, TileMatrixSelectionPolicy.COARSER_OR_EQUAL));
        assertSame(
                set.matrix("2"),
                set.select(levelOne * 0.9, TileMatrixSelectionPolicy.FINER_OR_EQUAL));
        assertCode(
                "TILE_MATRIX_SCALE_UNAVAILABLE",
                () ->
                        set.select(
                                set.matrix("0").scaleDenominator() * 2,
                                TileMatrixSelectionPolicy.COARSER_OR_EQUAL));
        assertCode(
                "TILE_MATRIX_SCALE_INVALID",
                () -> set.select(0, TileMatrixSelectionPolicy.NEAREST));
        assertCode("TILE_MATRIX_UNKNOWN", () -> set.matrix("missing"));
    }

    @Test
    void modelLimitsAndStableProblemValuesRejectInvalidState() {
        assertEquals(100_000, TileCoverageLimits.defaults().maximumTiles());
        assertThrows(IllegalArgumentException.class, () -> new TileCoverageLimits(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TileCoverageLimits(TileCoverageLimits.HARD_MAXIMUM_TILES + 1));
        assertThrows(IllegalArgumentException.class, () -> new VariableMatrixWidth(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new VariableMatrixWidth(2, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> matrix(" ", 4, 4, List.of()));
        assertThrows(IllegalArgumentException.class, () -> matrix("x", 0, 4, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> matrix("x", TileMatrix.MAXIMUM_MATRIX_DIMENSION + 1, 4, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> matrix("x", 4, 4, List.of(new VariableMatrixWidth(3, 0, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        matrix(
                                "x",
                                4,
                                4,
                                List.of(
                                        new VariableMatrixWidth(2, 0, 2),
                                        new VariableMatrixWidth(2, 2, 3))));
        assertThrows(
                IllegalArgumentException.class, () -> CommonTileMatrixSets.webMercatorQuad(25));
        assertThrows(IllegalArgumentException.class, () -> new TileMatrixIndex("", 0, 0));

        TileMatrixSet set = CommonTileMatrixSets.webMercatorQuad(0);
        assertCode(
                "TILE_MATRIX_INDEX_OUT_OF_DOMAIN",
                () -> TileMatrixAlgorithms.tileEnvelope(set, new TileMatrixIndex("0", 0, 1)));
        assertCode(
                "TILE_MATRIX_COORDINATE_OUT_OF_DOMAIN",
                () ->
                        TileMatrixAlgorithms.coverageAcrossHorizontalSeam(
                                CommonTileMatrixSets.worldCrs84Quad(0),
                                "0",
                                181,
                                -10,
                                -170,
                                10,
                                TileCoverageLimits.defaults()));

        TileMatrixProblem problem = new TileMatrixProblem("TILE_TEST", Map.of("b", "2", "a", "1"));
        TileMatrixException failure = new TileMatrixException(problem);
        assertSame(problem, failure.problem());
        assertEquals("TILE_TEST", failure.getMessage());
        assertEquals(List.of("a", "b"), problem.context().keySet().stream().toList());
        assertThrows(IllegalArgumentException.class, () -> new TileMatrixProblem("bad", Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TileCoverage(TileCoverageStatus.COMPLETE, List.of(), List.of()));
    }

    private static TileMatrix matrix(
            String identifier,
            long matrixWidth,
            long matrixHeight,
            List<VariableMatrixWidth> widths) {
        return new TileMatrix(
                identifier,
                1,
                1,
                new Coordinate(0, 4),
                TileMatrixCorner.TOP_LEFT,
                1,
                1,
                matrixWidth,
                matrixHeight,
                widths);
    }

    private static void assertEnvelope(Envelope expected, Envelope actual, double tolerance) {
        assertEquals(expected.minX(), actual.minX(), tolerance);
        assertEquals(expected.minY(), actual.minY(), tolerance);
        assertEquals(expected.maxX(), actual.maxX(), tolerance);
        assertEquals(expected.maxY(), actual.maxY(), tolerance);
    }

    private static void assertCode(String code, Runnable action) {
        TileMatrixException failure = assertThrows(TileMatrixException.class, action::run);
        assertEquals(code, failure.problem().code());
        assertTrue(failure.problem().context().size() <= 2);
    }
}
