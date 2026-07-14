package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import org.junit.jupiter.api.Test;

class MarkerTransformTest {
    @Test
    void inverseNormalizesLargeAndSmallFiniteCoefficients() {
        double large = Math.scalb(1.0, 700);
        MarkerTransform largeTransform =
                new MarkerTransform(
                        large,
                        0.0,
                        0.0,
                        large,
                        large,
                        -large,
                        new Envelope(-large, -large, large, large));
        Coordinate largeLocal =
                largeTransform.screenToLocal(new Coordinate(large * 1.5, -large * 0.5));
        assertEquals(0.5, largeLocal.x(), 1.0e-15);
        assertEquals(0.5, largeLocal.y(), 1.0e-15);

        double small = Math.scalb(1.0, -700);
        MarkerTransform smallTransform =
                new MarkerTransform(
                        small,
                        0.0,
                        0.0,
                        small,
                        small,
                        -small,
                        new Envelope(-small, -small, small, small));
        Coordinate smallLocal =
                smallTransform.screenToLocal(new Coordinate(small * 1.5, -small * 0.5));
        assertEquals(0.5, smallLocal.x(), 1.0e-15);
        assertEquals(0.5, smallLocal.y(), 1.0e-15);
    }
}
