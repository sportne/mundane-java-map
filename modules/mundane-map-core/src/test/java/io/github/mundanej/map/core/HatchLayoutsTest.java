package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.SymbolException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HatchLayoutsTest {
    private static final Envelope BOUNDS = new Envelope(0.0, 0.0, 10.0, 10.0);
    private static final Coordinate ORIGIN = new Coordinate(0.0, 0.0);

    @Test
    void eachPatternIsPackedClippedOrderedAndDefensivelyCopied() {
        HatchSegments forward =
                HatchLayouts.cover(
                        HatchPattern.FORWARD_DIAGONAL, BOUNDS, ORIGIN, 0.0, 5.0, 10, "area");
        HatchSegments backward =
                HatchLayouts.cover(
                        HatchPattern.BACKWARD_DIAGONAL, BOUNDS, ORIGIN, 0.0, 5.0, 10, "area");
        HatchSegments cross =
                HatchLayouts.cover(
                        HatchPattern.CROSS_DIAGONAL, BOUNDS, ORIGIN, 0.0, 5.0, 10, "area");

        assertEquals(2, forward.segmentCount());
        assertEquals(3, backward.segmentCount());
        assertEquals(forward.segmentCount() + backward.segmentCount(), cross.segmentCount());
        assertEquals(5, cross.segmentCount());
        for (int index = 0; index < forward.segmentCount(); index++) {
            assertEquals(
                    -1.0,
                    (forward.y2(index) - forward.y1(index))
                            / (forward.x2(index) - forward.x1(index)),
                    1.0e-12);
        }
        for (int index = 0; index < backward.segmentCount(); index++) {
            assertEquals(
                    1.0,
                    (backward.y2(index) - backward.y1(index))
                            / (backward.x2(index) - backward.x1(index)),
                    1.0e-12);
        }
        for (int index = 0; index < cross.segmentCount(); index++) {
            assertTrue(BOUNDS.contains(new Coordinate(cross.x1(index), cross.y1(index))));
            assertTrue(BOUNDS.contains(new Coordinate(cross.x2(index), cross.y2(index))));
        }
        assertArrayEquals(
                forward.toArray(),
                java.util.Arrays.copyOf(cross.toArray(), forward.segmentCount() * 4));
        double[] copy = forward.toArray();
        copy[0] = 999.0;
        assertTrue(forward.x1(0) != 999.0);
        assertThrows(IndexOutOfBoundsException.class, () -> assertEquals(0.0, forward.x1(-1)));
    }

    @Test
    void originBearingAndSpacingChangeTheFiniteLattice() {
        HatchSegments baseline =
                HatchLayouts.cover(
                        HatchPattern.FORWARD_DIAGONAL, BOUNDS, ORIGIN, 0.0, 4.0, 20, "area");
        HatchSegments shifted =
                HatchLayouts.cover(
                        HatchPattern.FORWARD_DIAGONAL,
                        BOUNDS,
                        new Coordinate(2.0, 1.0),
                        30.0,
                        3.0,
                        20,
                        "area");
        assertTrue(baseline.segmentCount() > 0);
        assertTrue(shifted.segmentCount() > 0);
        assertTrue(!java.util.Arrays.equals(baseline.toArray(), shifted.toArray()));
    }

    @Test
    void candidateBudgetIsPreflightedForSingleAndCrossPatterns() {
        assertEquals(
                3,
                HatchLayouts.candidateSegmentCount(
                        HatchPattern.FORWARD_DIAGONAL, BOUNDS, ORIGIN, 0.0, 5.0, "area"));
        assertEquals(
                6,
                HatchLayouts.candidateSegmentCount(
                        HatchPattern.CROSS_DIAGONAL, BOUNDS, ORIGIN, 0.0, 5.0, "area"));
        HatchSegments exactlyAtLimit =
                HatchLayouts.cover(
                        HatchPattern.FORWARD_DIAGONAL, BOUNDS, ORIGIN, 0.0, 5.0, 3, "area");
        assertTrue(exactlyAtLimit.segmentCount() <= 3);

        SymbolException single =
                assertThrows(
                        SymbolException.class,
                        () ->
                                HatchLayouts.cover(
                                        HatchPattern.FORWARD_DIAGONAL,
                                        BOUNDS,
                                        ORIGIN,
                                        0.0,
                                        5.0,
                                        2,
                                        "area"));
        assertEquals(SymbolException.HATCH_SEGMENT_LIMIT_EXCEEDED, single.code());
        assertEquals(
                List.of("featureId", "pattern", "requiredSegments", "maxSegments", "countKind"),
                List.copyOf(single.context().keySet()));
        assertEquals("3", single.context().get("requiredSegments"));
        assertEquals("candidate", single.context().get("countKind"));

        SymbolException cross =
                assertThrows(
                        SymbolException.class,
                        () ->
                                HatchLayouts.cover(
                                        HatchPattern.CROSS_DIAGONAL,
                                        BOUNDS,
                                        ORIGIN,
                                        0.0,
                                        5.0,
                                        5,
                                        "area"));
        assertEquals("6", cross.context().get("requiredSegments"));
    }

    @Test
    void unrepresentableLatticeCountUsesStableOverflowSentinel() {
        assertEquals(
                Long.MAX_VALUE,
                HatchLayouts.candidateSegmentCount(
                        HatchPattern.FORWARD_DIAGONAL,
                        BOUNDS,
                        ORIGIN,
                        0.0,
                        Double.MIN_VALUE,
                        "area"));
        SymbolException failure =
                assertThrows(
                        SymbolException.class,
                        () ->
                                HatchLayouts.cover(
                                        HatchPattern.FORWARD_DIAGONAL,
                                        BOUNDS,
                                        ORIGIN,
                                        0.0,
                                        Double.MIN_VALUE,
                                        Integer.MAX_VALUE,
                                        "area"));
        assertEquals(SymbolException.HATCH_SEGMENT_LIMIT_EXCEEDED, failure.code());
        assertEquals("overflow", failure.context().get("requiredSegments"));
    }
}
