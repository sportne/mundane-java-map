package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HorizontalWrapTest {
    private static final HorizontalWrap WRAP = new HorizontalWrap(-180.0, 180.0, 8, 16);

    @Test
    void canonicalizesBothSeamsIntoOneHalfOpenIdentity() {
        assertEquals(new WrappedX(-180.0, 0L), WRAP.canonicalize(-180.0));
        assertEquals(new WrappedX(-180.0, 1L), WRAP.canonicalize(180.0));
        assertEquals(new WrappedX(170.0, -1L), WRAP.canonicalize(-190.0));
        assertEquals(new WrappedX(-170.0, 1L), WRAP.canonicalize(190.0));
        assertEquals(550.0, WRAP.translate(-170.0, 2L));
    }

    @Test
    void choosesNearestVisualEquivalentWithWestwardHalfPeriodTies() {
        assertEquals(190.0, WRAP.nearestEquivalent(-170.0, 185.0));
        assertEquals(-170.0, WRAP.nearestEquivalent(-170.0, 10.0));
        assertEquals(0.0, WRAP.nearestEquivalent(0.0, 180.0));
        assertEquals(5_590.0, WRAP.nearestEquivalent(-170.0, 5_600.0));
        assertEquals(-5_590.0, WRAP.nearestEquivalent(170.0, -5_600.0));
    }

    @Test
    void plansOneSplitAndFullWorldIntervalsWithoutDuplicateSeams() {
        HorizontalWrapPlan one = WRAP.plan(-100.0, 100.0, 1.0);
        assertEquals(List.of(new HorizontalInterval(-100.0, 100.0)), one.canonicalIntervals());
        assertEquals(1, one.visibleCopyCount());
        assertFalse(one.fullWorld());

        HorizontalWrapPlan toSeam = WRAP.plan(0.0, 180.0, 1.0);
        assertEquals(List.of(new HorizontalInterval(0.0, 180.0)), toSeam.canonicalIntervals());

        HorizontalWrapPlan split = WRAP.plan(170.0, 190.0, 1.0);
        assertEquals(
                List.of(
                        new HorizontalInterval(170.0, 180.0),
                        new HorizontalInterval(-180.0, -170.0)),
                split.canonicalIntervals());
        assertEquals(0L, split.minimumVisibleCopyIndex());
        assertEquals(1L, split.maximumVisibleCopyIndex());

        HorizontalWrapPlan full = WRAP.plan(-360.0, 360.0, 1.0);
        assertEquals(List.of(new HorizontalInterval(-180.0, 180.0)), full.canonicalIntervals());
        assertEquals(3, full.visibleCopyCount());
        assertTrue(full.fullWorld());
    }

    @Test
    void limitsCopiesAndPrecisionBeforeReturningAPlan() {
        HorizontalWrap twoCopies = new HorizontalWrap(-180.0, 180.0, 2, 16);
        HorizontalWrapException copies =
                assertThrows(
                        HorizontalWrapException.class, () -> twoCopies.plan(-540.0, 540.0, 1.0));
        assertEquals("WORLD_WRAP_COPY_LIMIT_EXCEEDED", copies.problem().code());
        assertEquals("3", copies.problem().context().get("requested"));

        HorizontalWrapException precision =
                assertThrows(
                        HorizontalWrapException.class,
                        () -> WRAP.plan(3_599.0, 3_601.0, Math.ulp(3_600.0)));
        assertEquals("WORLD_WRAP_PRECISION_EXCEEDED", precision.problem().code());

        HorizontalWrapException index =
                assertThrows(HorizontalWrapException.class, () -> WRAP.canonicalize(6_300.0));
        assertEquals("WORLD_WRAP_PRECISION_EXCEEDED", index.problem().code());

        HorizontalWrapException precedence =
                assertThrows(
                        HorizontalWrapException.class,
                        () -> twoCopies.plan(-540.0, 540.0, Math.ulp(540.0)));
        assertEquals("WORLD_WRAP_PRECISION_EXCEEDED", precedence.problem().code());
    }

    @Test
    void permitsExclusiveEdgesAtBothSupportedCopyLimits() {
        HorizontalWrapPlan positive = WRAP.plan(5_580.0, 5_940.0, 1.0);
        assertEquals(16L, positive.minimumVisibleCopyIndex());
        assertEquals(16L, positive.maximumVisibleCopyIndex());
        assertEquals(List.of(new HorizontalInterval(-180.0, 180.0)), positive.canonicalIntervals());

        HorizontalWrapPlan negative = WRAP.plan(-5_940.0, -5_580.0, 1.0);
        assertEquals(-16L, negative.minimumVisibleCopyIndex());
        assertEquals(-16L, negative.maximumVisibleCopyIndex());
        assertEquals(List.of(new HorizontalInterval(-180.0, 180.0)), negative.canonicalIntervals());
    }

    @Test
    void canonicalTileColumnsUseFloorModulo() {
        assertEquals(3L, WRAP.canonicalTileColumn(-1L, 4L));
        assertEquals(0L, WRAP.canonicalTileColumn(4L, 4L));
        assertEquals(0L, WRAP.canonicalTileColumn(Long.MIN_VALUE, 1L));
        assertEquals(0L, WRAP.canonicalTileColumn(Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE - 1, WRAP.canonicalTileColumn(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(0L, WRAP.canonicalTileColumn(Long.MAX_VALUE, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> WRAP.canonicalTileColumn(0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> WRAP.canonicalTileColumn(0L, -1L));
    }

    @Test
    void rejectsInvalidProfilesAndArguments() {
        assertThrows(IllegalArgumentException.class, () -> new HorizontalWrap(0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new HorizontalWrap(0, 1, 65, 1));
        assertThrows(IllegalArgumentException.class, () -> new HorizontalWrap(0, 1, 1, 1_048_577));
        assertThrows(IllegalArgumentException.class, () -> WRAP.canonicalize(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> WRAP.translate(180.0, 0L));
        assertThrows(IllegalArgumentException.class, () -> WRAP.plan(0.0, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new HorizontalWrapPlan(
                                List.of(new HorizontalInterval(-180.0, 180.0)),
                                Long.MIN_VALUE,
                                Long.MAX_VALUE,
                                true));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new HorizontalWrapPlan(
                                List.of(new HorizontalInterval(-180.0, 180.0)), 0L, 64L, true));
    }
}
