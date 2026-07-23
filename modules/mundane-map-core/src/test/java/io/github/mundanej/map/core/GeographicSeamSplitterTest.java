package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeographicSeamSplitterTest {
    @Test
    void eastboundLineSplitsIntoCanonicalAdjacentWorldFragments() {
        GeographicSeamSplitter.Result result =
                GeographicSeamSplitter.split(
                        line(170.0, 10.0, -170.0, 20.0), CancellationToken.none());

        assertEquals(1, result.insertedCrossings());
        assertEquals(2, result.fragments().size());
        assertLine(result.fragments().get(0), 0L, 170.0, 10.0, 180.0, 15.0);
        assertLine(result.fragments().get(1), 1L, -180.0, 15.0, -170.0, 20.0);
    }

    @Test
    void exactHalfPeriodTieUsesTheWestwardPath() {
        GeographicSeamSplitter.Result result =
                GeographicSeamSplitter.split(
                        line(-170.0, 0.0, 10.0, 18.0), CancellationToken.none());

        assertEquals(1, result.insertedCrossings());
        assertLine(result.fragments().get(0), 0L, -170.0, 0.0, -180.0, 1.0);
        assertLine(result.fragments().get(1), -1L, 180.0, 1.0, 10.0, 18.0);
    }

    @Test
    void multipartLinesKeepPartsPackedByContinuousWorld() {
        MultiLineStringGeometry source =
                MultiLineStringGeometry.ofParts(
                        List.of(
                                sequence(170.0, 0.0, -170.0, 0.0),
                                sequence(160.0, 10.0, -160.0, 10.0)));
        GeographicSeamSplitter.Result result =
                GeographicSeamSplitter.split(source, CancellationToken.none());

        assertEquals(4, result.fragments().size());
        assertEquals(
                List.of(0L, 1L, 0L, 1L),
                result.fragments().stream()
                        .map(GeographicSeamSplitter.Fragment::worldOffset)
                        .toList());
        assertEquals(
                List.of(true, false, true, false),
                result.fragments().stream()
                        .map(GeographicSeamSplitter.Fragment::retainsLogicalStart)
                        .toList());
        assertEquals(
                List.of(false, true, false, true),
                result.fragments().stream()
                        .map(GeographicSeamSplitter.Fragment::retainsLogicalEnd)
                        .toList());
    }

    @Test
    void polygonAndContainedHoleProduceClosedFragmentsInMatchingWorlds() {
        PolygonGeometry source =
                new PolygonGeometry(
                        sequence(
                                170.0, -20.0,
                                -170.0, -20.0,
                                -170.0, 20.0,
                                170.0, 20.0,
                                170.0, -20.0),
                        List.of(
                                sequence(
                                        175.0, -5.0,
                                        178.0, -5.0,
                                        178.0, 5.0,
                                        175.0, 5.0,
                                        175.0, -5.0)));

        GeographicSeamSplitter.Result result =
                GeographicSeamSplitter.split(source, CancellationToken.none());

        assertEquals(
                List.of(0L, 1L),
                result.fragments().stream()
                        .map(GeographicSeamSplitter.Fragment::worldOffset)
                        .toList());
        for (int index = 0; index < result.fragments().size(); index++) {
            PolygonGeometry polygon =
                    assertInstanceOf(
                            PolygonGeometry.class, result.fragments().get(index).geometry());
            assertEquals(index == 0 ? 1 : 0, polygon.holes().size());
            assertEquals(true, polygon.exterior().isClosed());
            polygon.holes().forEach(hole -> assertEquals(true, hole.isClosed()));
        }
    }

    @Test
    void exactSeamEndpointsAndTouchesRemainLogicalRatherThanArtificial() {
        GeographicSeamSplitter.Result ending =
                GeographicSeamSplitter.split(
                        line(170.0, 0.0, 180.0, 1.0), CancellationToken.none());
        assertEquals(0, ending.insertedCrossings());
        assertEquals(1, ending.fragments().size());
        assertEquals(true, ending.fragments().getFirst().retainsLogicalEnd());
        assertLine(ending.fragments().getFirst(), 0L, 170.0, 0.0, 180.0, 1.0);

        GeographicSeamSplitter.Result starting =
                GeographicSeamSplitter.split(
                        line(180.0, 1.0, 170.0, 0.0), CancellationToken.none());
        assertEquals(0, starting.insertedCrossings());
        assertEquals(true, starting.fragments().getFirst().retainsLogicalStart());
        assertLine(starting.fragments().getFirst(), 0L, 180.0, 1.0, 170.0, 0.0);

        GeographicSeamSplitter.Result touching =
                GeographicSeamSplitter.split(
                        line(170.0, 0.0, 180.0, 1.0, 170.0, 2.0), CancellationToken.none());
        assertEquals(0, touching.insertedCrossings());
        assertEquals(1, touching.fragments().size());
        assertEquals(3, lineCoordinates(touching.fragments().getFirst()).size());

        GeographicSeamSplitter.Result repeatedTouch =
                GeographicSeamSplitter.split(
                        line(170.0, 0.0, 180.0, 1.0, 180.0, 1.0, 170.0, 2.0),
                        CancellationToken.none());
        assertEquals(0, repeatedTouch.insertedCrossings());
        assertEquals(1, repeatedTouch.fragments().size());
        assertEquals(3, lineCoordinates(repeatedTouch.fragments().getFirst()).size());
    }

    @Test
    void ambiguousConcaveSplitAndCrossingHoleAreRejectedAtomically() {
        PolygonGeometry concave =
                new PolygonGeometry(
                        sequence(
                                170.0, -10.0,
                                -170.0, -10.0,
                                -170.0, -6.0,
                                174.0, -6.0,
                                174.0, 6.0,
                                -170.0, 6.0,
                                -170.0, 10.0,
                                170.0, 10.0,
                                170.0, -10.0));
        GeographicSeamSplitter.GeographicSeamException ringFailure =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () -> GeographicSeamSplitter.split(concave, CancellationToken.none()));
        assertEquals("ambiguousRing", ringFailure.context().get("reason"));

        PolygonGeometry crossingHole =
                new PolygonGeometry(
                        sequence(
                                170.0, -20.0,
                                -170.0, -20.0,
                                -170.0, 20.0,
                                170.0, 20.0,
                                170.0, -20.0),
                        List.of(
                                sequence(
                                        175.0, -5.0,
                                        -175.0, -5.0,
                                        -175.0, 5.0,
                                        175.0, 5.0,
                                        175.0, -5.0)));
        GeographicSeamSplitter.GeographicSeamException holeFailure =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () -> GeographicSeamSplitter.split(crossingHole, CancellationToken.none()));
        assertEquals("ambiguousHole", holeFailure.context().get("reason"));

        PolygonGeometry partialOutsideHole =
                new PolygonGeometry(
                        sequence(
                                170.0, -10.0,
                                179.0, -10.0,
                                179.0, 10.0,
                                170.0, 10.0,
                                170.0, -10.0),
                        List.of(
                                sequence(
                                        168.0, -5.0,
                                        176.0, -5.0,
                                        176.0, 5.0,
                                        168.0, 5.0,
                                        168.0, -5.0)));
        GeographicSeamSplitter.GeographicSeamException containmentFailure =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () ->
                                GeographicSeamSplitter.split(
                                        partialOutsideHole, CancellationToken.none()));
        assertEquals("ambiguousHole", containmentFailure.context().get("reason"));

        assertAmbiguousHole(
                polygonWithHoles(
                        sequence(
                                172.0, -4.0,
                                176.0, -4.0,
                                176.0, 4.0,
                                172.0, 4.0,
                                172.0, -4.0),
                        sequence(
                                174.0, -3.0,
                                178.0, -3.0,
                                178.0, 3.0,
                                174.0, 3.0,
                                174.0, -3.0)));
        assertAmbiguousHole(
                polygonWithHoles(
                        sequence(
                                172.0, -4.0,
                                175.0, -4.0,
                                175.0, 4.0,
                                172.0, 4.0,
                                172.0, -4.0),
                        sequence(
                                175.0, -3.0,
                                178.0, -3.0,
                                178.0, 3.0,
                                175.0, 3.0,
                                175.0, -3.0)));
        assertAmbiguousHole(
                polygonWithHoles(
                        sequence(
                                172.0, -6.0,
                                178.0, -6.0,
                                178.0, 6.0,
                                172.0, 6.0,
                                172.0, -6.0),
                        sequence(
                                174.0, -2.0,
                                176.0, -2.0,
                                176.0, 2.0,
                                174.0, 2.0,
                                174.0, -2.0)));
    }

    @Test
    void repeatedExactSeamCoordinatesDoNotCreateEmptyLineFragments() {
        GeographicSeamSplitter.Result result =
                GeographicSeamSplitter.split(
                        line(170.0, 0.0, 180.0, 5.0, 180.0, 5.0, -170.0, 10.0),
                        CancellationToken.none());

        assertEquals(2, result.fragments().size());
        assertEquals(1, result.insertedCrossings());
        result.fragments()
                .forEach(
                        fragment ->
                                assertEquals(
                                        2,
                                        assertInstanceOf(
                                                        LineStringGeometry.class,
                                                        fragment.geometry())
                                                .coordinates()
                                                .size()));
    }

    @Test
    void multipolygonKeepsLocalAndCrossingPolygonGroupsPacked() {
        PolygonGeometry crossing =
                new PolygonGeometry(
                        sequence(
                                170.0, -10.0,
                                -170.0, -10.0,
                                -170.0, 10.0,
                                170.0, 10.0,
                                170.0, -10.0));
        PolygonGeometry local =
                new PolygonGeometry(
                        sequence(
                                -40.0, -5.0,
                                -30.0, -5.0,
                                -30.0, 5.0,
                                -40.0, 5.0,
                                -40.0, -5.0));

        GeographicSeamSplitter.Result result =
                GeographicSeamSplitter.split(
                        MultiPolygonGeometry.ofPolygons(List.of(crossing, local)),
                        CancellationToken.none());

        assertEquals(
                List.of(0L, 1L),
                result.fragments().stream()
                        .map(GeographicSeamSplitter.Fragment::worldOffset)
                        .toList());
        assertEquals(
                2,
                assertInstanceOf(
                                MultiPolygonGeometry.class,
                                result.fragments().getFirst().geometry())
                        .polygonCount());
        assertInstanceOf(PolygonGeometry.class, result.fragments().getLast().geometry());
    }

    @Test
    void zeroAreaRingHasStableInvalidRingFailure() {
        PolygonGeometry invalid =
                new PolygonGeometry(sequence(170.0, 0.0, -170.0, 0.0, 175.0, 0.0, 170.0, 0.0));
        GeographicSeamSplitter.GeographicSeamException failure =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () -> GeographicSeamSplitter.split(invalid, CancellationToken.none()));

        assertEquals("WORLD_WRAP_GEOMETRY_UNSUPPORTED", failure.code());
        assertEquals("invalidRing", failure.context().get("reason"));
    }

    @Test
    void cancellationAndCrossingCeilingHaveStableProblems() {
        GeographicSeamSplitter.GeographicSeamException cancelled =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () -> GeographicSeamSplitter.split(line(0.0, 0.0, 1.0, 1.0), () -> true));
        assertEquals("SOURCE_CANCELLED", cancelled.code());

        AtomicInteger cancellationChecks = new AtomicInteger();
        GeographicSeamSplitter.GeographicSeamException finalCancellation =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () ->
                                GeographicSeamSplitter.split(
                                        line(0.0, 0.0, 1.0, 1.0),
                                        () -> cancellationChecks.getAndIncrement() > 0));
        assertEquals("SOURCE_CANCELLED", finalCancellation.code());

        double[] coordinates =
                new double[(GeographicSeamSplitter.MAXIMUM_INSERTED_CROSSINGS + 2) * 2];
        for (int index = 0; index < coordinates.length / 2; index++) {
            coordinates[index * 2] = (index & 1) == 0 ? 170.0 : -170.0;
            coordinates[index * 2 + 1] = index;
        }
        GeographicSeamSplitter.GeographicSeamException excessive =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () ->
                                GeographicSeamSplitter.split(
                                        new LineStringGeometry(CoordinateSequence.of(coordinates)),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", excessive.code());
        assertEquals("seamCrossings", excessive.context().get("limit"));

        PolygonGeometry containmentHostile =
                new PolygonGeometry(
                        regularRing(1_024, 175.0, 0.0, 4.0),
                        List.of(regularRing(1_024, 175.0, 0.0, 2.0)));
        GeographicSeamSplitter.GeographicSeamException containmentLimit =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () ->
                                GeographicSeamSplitter.split(
                                        containmentHostile, CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", containmentLimit.code());
        assertEquals("worldWrap", containmentLimit.context().get("scope"));
        assertEquals("containmentComparisons", containmentLimit.context().get("limit"));
        assertEquals("1048576", containmentLimit.context().get("maximum"));
    }

    private static LineStringGeometry line(double... ordinates) {
        return new LineStringGeometry(CoordinateSequence.of(ordinates));
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }

    private static PolygonGeometry polygonWithHoles(CoordinateSequence... holes) {
        return new PolygonGeometry(
                sequence(
                        170.0, -10.0,
                        179.0, -10.0,
                        179.0, 10.0,
                        170.0, 10.0,
                        170.0, -10.0),
                List.of(holes));
    }

    private static CoordinateSequence regularRing(
            int vertices, double centerX, double centerY, double radius) {
        double[] ordinates = new double[(vertices + 1) * 2];
        for (int index = 0; index < vertices; index++) {
            double angle = 2.0 * Math.PI * index / vertices;
            ordinates[index * 2] = centerX + radius * Math.cos(angle);
            ordinates[index * 2 + 1] = centerY + radius * Math.sin(angle);
        }
        ordinates[vertices * 2] = ordinates[0];
        ordinates[vertices * 2 + 1] = ordinates[1];
        return CoordinateSequence.of(ordinates);
    }

    private static void assertAmbiguousHole(PolygonGeometry polygon) {
        GeographicSeamSplitter.GeographicSeamException failure =
                assertThrows(
                        GeographicSeamSplitter.GeographicSeamException.class,
                        () -> GeographicSeamSplitter.split(polygon, CancellationToken.none()));
        assertEquals("ambiguousHole", failure.context().get("reason"));
    }

    private static void assertLine(
            GeographicSeamSplitter.Fragment fragment,
            long world,
            double firstX,
            double firstY,
            double lastX,
            double lastY) {
        assertEquals(world, fragment.worldOffset());
        CoordinateSequence coordinates = lineCoordinates(fragment);
        assertEquals(firstX, coordinates.x(0));
        assertEquals(firstY, coordinates.y(0));
        assertEquals(lastX, coordinates.x(coordinates.size() - 1));
        assertEquals(lastY, coordinates.y(coordinates.size() - 1));
    }

    private static CoordinateSequence lineCoordinates(GeographicSeamSplitter.Fragment fragment) {
        return assertInstanceOf(LineStringGeometry.class, fragment.geometry()).coordinates();
    }
}
