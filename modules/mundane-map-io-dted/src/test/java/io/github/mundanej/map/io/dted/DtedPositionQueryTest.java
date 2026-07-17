package io.github.mundanej.map.io.dted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.ElevationValue;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.ElevationQueries;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DtedPositionQueryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void everyGeneratedLevelUsesTheFormatNeutralOrientationAndInterpolationPolicy()
            throws Exception {
        for (int level = 0; level <= 2; level++) {
            Path path = temporaryDirectory.resolve("query-level-" + level + ".dt" + level);
            DtedFixtures.Fixture fixture = DtedFixtures.write(path, level);
            try (ElevationSource source =
                    DtedFiles.open(
                            new SourceIdentity("query-level-" + level, "Query level " + level),
                            path,
                            DtedOpenOptions.defaults())) {
                CrsDefinition crs = source.metadata().crs().definition().orElseThrow();

                assertValue(
                        source,
                        crs,
                        source.metadata().sampleCoordinate(0, 0),
                        ElevationQueryMode.NEAREST,
                        -456.0);
                assertValue(
                        source,
                        crs,
                        source.metadata().sampleCoordinate(fixture.columns() - 1, 1),
                        ElevationQueryMode.NEAREST,
                        DtedFixtures.value(
                                fixture.columns() - 1, fixture.rows() - 2, fixture.rows()));
                assertValue(
                        source,
                        crs,
                        source.metadata().sampleCoordinate(1, fixture.rows() - 1),
                        ElevationQueryMode.NEAREST,
                        DtedFixtures.value(1, 0, fixture.rows()));

                Coordinate northWest = source.metadata().sampleCoordinate(2, fixture.rows() - 12);
                Coordinate southEast = source.metadata().sampleCoordinate(3, fixture.rows() - 11);
                Coordinate interior =
                        new Coordinate(
                                Math.fma(0.25, southEast.x() - northWest.x(), northWest.x()),
                                Math.fma(0.25, southEast.y() - northWest.y(), northWest.y()));
                assertValue(
                        source,
                        crs,
                        interior,
                        ElevationQueryMode.NEAREST,
                        DtedFixtures.value(2, 11, fixture.rows()));
                double columnWeight =
                        (interior.x() - northWest.x()) / (southEast.x() - northWest.x());
                double rowWeight = (northWest.y() - interior.y()) / (northWest.y() - southEast.y());
                double north =
                        Math.fma(
                                columnWeight,
                                DtedFixtures.value(3, 11, fixture.rows())
                                        - DtedFixtures.value(2, 11, fixture.rows()),
                                DtedFixtures.value(2, 11, fixture.rows()));
                double south =
                        Math.fma(
                                columnWeight,
                                DtedFixtures.value(3, 10, fixture.rows())
                                        - DtedFixtures.value(2, 10, fixture.rows()),
                                DtedFixtures.value(2, 10, fixture.rows()));
                double expectedBilinear = Math.fma(rowWeight, south - north, north);
                assertValue(source, crs, interior, ElevationQueryMode.BILINEAR, expectedBilinear);

                if (level == 2) {
                    int centerColumn = fixture.columns() / 2;
                    int centerRow = fixture.rows() / 2;
                    Coordinate voidPost =
                            source.metadata().sampleCoordinate(centerColumn, centerRow);
                    assertTrue(
                            ElevationQueries.query(
                                            source, crs, voidPost, ElevationQueryMode.NEAREST)
                                    .isEmpty());

                    Coordinate nextPost =
                            source.metadata().sampleCoordinate(centerColumn + 1, centerRow + 1);
                    Coordinate voidInterior =
                            new Coordinate(
                                    (voidPost.x() + nextPost.x()) / 2.0,
                                    (voidPost.y() + nextPost.y()) / 2.0);
                    assertTrue(
                            ElevationQueries.query(
                                            source, crs, voidInterior, ElevationQueryMode.BILINEAR)
                                    .isEmpty());
                }
            }
        }
    }

    private static void assertValue(
            ElevationSource source,
            CrsDefinition crs,
            Coordinate position,
            ElevationQueryMode mode,
            double expected) {
        ElevationValue value = ElevationQueries.query(source, crs, position, mode).orElseThrow();
        assertEquals(expected, value.value());
        assertEquals(ElevationUnit.METRE, value.unit());
    }
}
