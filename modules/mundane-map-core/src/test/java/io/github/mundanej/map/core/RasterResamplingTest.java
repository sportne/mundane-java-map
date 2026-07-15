package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.RasterInterpolation;
import org.junit.jupiter.api.Test;

class RasterResamplingTest {
    @Test
    void nearestUsesExactCentersAndUpperTie() {
        assertEquals(1, RasterResampling.nearestIndex(0, 4, 2));
        assertEquals(3, RasterResampling.nearestIndex(1, 4, 2));
        assertEquals(0, RasterResampling.nearestIndex(0, 2, 4));
        assertEquals(1, RasterResampling.nearestIndex(2, 2, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> RasterResampling.nearestIndex(2, 2, 2));
    }

    @Test
    void bilinearAxisClampsEdgesAndRetainsExactQuarterWeights() {
        var first = RasterResampling.bilinearAxis(0, 2, 4);
        assertEquals(0, first.lowerIndex());
        assertEquals(0, first.upperIndex());
        var quarter = RasterResampling.bilinearAxis(1, 2, 4);
        assertEquals(0, quarter.lowerIndex());
        assertEquals(1, quarter.upperIndex());
        assertEquals(6, quarter.lowerWeight());
        assertEquals(2, quarter.upperWeight());
        assertEquals(8, quarter.denominator());
        var finalPixel = RasterResampling.bilinearAxis(3, 2, 4);
        assertEquals(1, finalPixel.lowerIndex());
        assertEquals(1, finalPixel.upperIndex());
        var singleton = RasterResampling.bilinearAxis(3, 1, 8);
        assertEquals(0, singleton.lowerIndex());
        assertEquals(0, singleton.upperIndex());
    }

    @Test
    void bilinearPremultipliesAlphaAndCanonicalizesTransparentBlack() {
        var equal = new RasterResampling.AxisWeights(0, 1, 1, 1, 2);
        int blended =
                RasterResampling.bilinearRgba(
                        0xff000000, 0x0000ffff, 0xff000000, 0x0000ffff, equal, equal);
        assertEquals(0x0000ff80, blended);
        assertEquals(
                0,
                RasterResampling.bilinearRgba(
                        0xff000000, 0x00ff0000, 0x0000ff00, 0xffffff00, equal, equal));
    }

    @Test
    void bilinearRejectsWeightProductsThatCannotBeExactInLong() {
        var huge = new RasterResampling.AxisWeights(0, 1, 4_294_967_294L, 0, 4_294_967_294L);
        assertThrows(
                ArithmeticException.class,
                () -> RasterResampling.bilinearRgba(0, 0, 0, 0, huge, huge));
    }

    @Test
    void identitySingletonAndOneDimensionalPlansRemainWindowLocal() {
        for (int index = 0; index < 4; index++) {
            var identity = RasterResampling.bilinearAxis(index, 4, 4);
            assertEquals(index, identity.lowerIndex());
            assertEquals(
                    index,
                    identity.upperWeight() == 0 ? identity.lowerIndex() : identity.upperIndex());
        }
        var singleton = RasterResampling.bilinearAxis(2, 1, 5);
        assertEquals(0, singleton.lowerIndex());
        assertEquals(0, singleton.upperIndex());
        var horizontal = RasterResampling.bilinearAxis(3, 7, 5);
        assertEquals(4, horizontal.lowerIndex());
        assertEquals(5, horizontal.upperIndex());
        var vertical = RasterResampling.bilinearAxis(1, 5, 3);
        assertEquals(2, vertical.lowerIndex());
        assertEquals(3, vertical.upperIndex());
    }

    @Test
    void alphaHalfUpRoundingDropsInvisibleColorAndRetainsVisibleColor() {
        var equal = new RasterResampling.AxisWeights(0, 1, 1, 1, 2);
        assertEquals(
                0,
                RasterResampling.bilinearRgba(
                        0xff000001, 0x00ff0000, 0x0000ff00, 0xffffff00, equal, equal));
        assertEquals(
                0xff000001,
                RasterResampling.bilinearRgba(
                        0xff000001, 0xff000001, 0x00ff0000, 0x0000ff00, equal, equal));
    }

    @Test
    void completePlanPreflightAcceptsMaximumAxisAndRejectsUnsafeWorkBeforeAllocation() {
        RasterResampling.validatePlan(1, 1, Integer.MAX_VALUE, 1, RasterInterpolation.BILINEAR);
        RasterResampling.validatePlan(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                RasterInterpolation.NEAREST);
        assertThrows(
                ArithmeticException.class,
                () ->
                        RasterResampling.validatePlan(
                                1, 1, 6_000_000, 6_000_000, RasterInterpolation.BILINEAR));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterResampling.validatePlan(0, 1, 1, 1, RasterInterpolation.NEAREST));
        assertThrows(
                NullPointerException.class, () -> RasterResampling.validatePlan(1, 1, 1, 1, null));
    }
}
