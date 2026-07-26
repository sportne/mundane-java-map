package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MeasurementValuesTest {
    @Test
    void distanceIsFiniteNonNegativeCanonicalAndChecked() {
        assertEquals(0L, Double.doubleToLongBits(new DistanceResult(-0.0).metres()));
        assertEquals(new DistanceResult(7.5), new DistanceResult(2.5).plus(new DistanceResult(5)));
        assertThrows(IllegalArgumentException.class, () -> new DistanceResult(-1));
        assertThrows(IllegalArgumentException.class, () -> new DistanceResult(Double.NaN));
        assertThrows(
                ArithmeticException.class,
                () ->
                        new DistanceResult(Double.MAX_VALUE)
                                .plus(new DistanceResult(Double.MAX_VALUE)));
    }

    @Test
    void measurementStateDefensivelyOwnsPackedCoordinatesAndTotals() {
        double[] packed = {1, 2, 4, 6};
        MeasurementState state =
                new MeasurementState(
                        MeasurementPhase.MEASURING,
                        packed,
                        Optional.of(new Coordinate(7, 10)),
                        new DistanceResult(5),
                        Optional.of(new DistanceResult(5)),
                        Optional.of(new DistanceResult(5)));
        packed[0] = 99;
        double[] returned = state.packedVertices();
        returned[0] = 88;

        assertArrayEquals(new double[] {1, 2, 4, 6}, state.packedVertices());
        assertEquals(new Coordinate(4, 6), state.vertex(1));
        assertEquals(MeasurementPhase.MEASURING, state.phase());
        assertEquals(2, state.vertexCount());
        assertEquals(Optional.of(new Coordinate(7, 10)), state.preview());
        assertEquals(new DistanceResult(5), state.committedDistance());
        assertEquals(Optional.of(new DistanceResult(5)), state.lastCommittedSegmentDistance());
        assertEquals(Optional.of(new DistanceResult(5)), state.previewSegmentDistance());
        assertEquals(new DistanceResult(10), state.displayedDistance());
        assertEquals(
                state,
                new MeasurementState(
                        MeasurementPhase.MEASURING,
                        new double[] {1, 2, 4, 6},
                        Optional.of(new Coordinate(7, 10)),
                        new DistanceResult(5),
                        Optional.of(new DistanceResult(5)),
                        Optional.of(new DistanceResult(5))));
        assertNotEquals(state, MeasurementState.empty());
        assertEquals(
                state.hashCode(),
                new MeasurementState(
                                MeasurementPhase.MEASURING,
                                new double[] {1, 2, 4, 6},
                                Optional.of(new Coordinate(7, 10)),
                                new DistanceResult(5),
                                Optional.of(new DistanceResult(5)),
                                Optional.of(new DistanceResult(5)))
                        .hashCode());
        assertEquals(
                "MeasurementState[phase=MEASURING, vertexCount=2, "
                        + "preview=Optional[Coordinate[x=7.0, y=10.0]], "
                        + "displayedDistance=DistanceResult[metres=10.0]]",
                state.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> state.vertex(2));
    }

    @Test
    void measurementStateRejectsInconsistentPhasesAndSegments() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.EMPTY,
                                new double[] {0, 0},
                                Optional.empty(),
                                DistanceResult.ZERO,
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.MEASURING,
                                new double[] {0},
                                Optional.empty(),
                                DistanceResult.ZERO,
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.MEASURING,
                                new double[] {Double.NaN, 0},
                                Optional.empty(),
                                DistanceResult.ZERO,
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.MEASURING,
                                new double[] {0, 0, 1, 1},
                                Optional.empty(),
                                new DistanceResult(1),
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.COMPLETE,
                                new double[] {0, 0, 1, 1},
                                Optional.of(new Coordinate(2, 2)),
                                new DistanceResult(1),
                                Optional.of(new DistanceResult(1)),
                                Optional.of(new DistanceResult(1))));
        assertThrows(
                NullPointerException.class,
                () ->
                        new MeasurementState(
                                null,
                                new double[0],
                                Optional.empty(),
                                DistanceResult.ZERO,
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.COMPLETE,
                                new double[] {0, 0},
                                Optional.empty(),
                                DistanceResult.ZERO,
                                Optional.empty(),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementState(
                                MeasurementPhase.MEASURING,
                                new double[] {0, 0},
                                Optional.of(new Coordinate(1, 1)),
                                DistanceResult.ZERO,
                                Optional.empty(),
                                Optional.empty()));
    }

    @Test
    void commandEventRequiresPositiveSequence() {
        assertEquals(
                MapToolCommand.DELETE_BACKWARD,
                new MapToolCommandEvent(1, MapToolCommand.DELETE_BACKWARD).command());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapToolCommandEvent(0, MapToolCommand.DELETE_BACKWARD));
    }
}
