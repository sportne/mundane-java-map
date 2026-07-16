package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElevationRasterStyleTest {
    @Test
    void rampDefensivelyOwnsTwoThroughTwoHundredFiftySixOrderedStops() {
        List<ElevationColorStop> mutable =
                new ArrayList<>(
                        List.of(
                                new ElevationColorStop(-10, Rgba.rgb(0, 10, 20)),
                                new ElevationColorStop(10, Rgba.rgb(100, 110, 120))));
        ElevationColorRamp ramp = new ElevationColorRamp(ElevationUnit.METRE, mutable);
        mutable.clear();
        assertEquals(2, ramp.stops().size());
        assertThrows(UnsupportedOperationException.class, () -> ramp.stops().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationColorRamp(ElevationUnit.METRE, List.of()));
        List<ElevationColorStop> maximum = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            maximum.add(new ElevationColorStop(index, Rgba.TRANSPARENT));
        }
        assertEquals(256, new ElevationColorRamp(ElevationUnit.METRE, maximum).stops().size());
        maximum.add(new ElevationColorStop(256, Rgba.TRANSPARENT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationColorRamp(ElevationUnit.METRE, maximum));
    }

    @Test
    void rampRejectsInvalidStopsAndInterpolatesEveryChannelRoundHalfUp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationColorStop(Double.NaN, Rgba.TRANSPARENT));
        assertThrows(NullPointerException.class, () -> new ElevationColorStop(0, null));
        assertEquals(
                0L,
                Double.doubleToRawLongBits(
                        new ElevationColorStop(-0.0, Rgba.TRANSPARENT).elevation()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ElevationColorRamp(
                                ElevationUnit.METRE,
                                List.of(
                                        new ElevationColorStop(0, Rgba.TRANSPARENT),
                                        new ElevationColorStop(0, Rgba.TRANSPARENT))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ElevationColorRamp(
                                ElevationUnit.METRE,
                                List.of(
                                        new ElevationColorStop(-Double.MAX_VALUE, Rgba.TRANSPARENT),
                                        new ElevationColorStop(
                                                Double.MAX_VALUE, Rgba.TRANSPARENT))));

        ElevationColorRamp ramp =
                new ElevationColorRamp(
                        ElevationUnit.INTERNATIONAL_FOOT,
                        List.of(
                                new ElevationColorStop(0, new Rgba(0, 10, 20, 30)),
                                new ElevationColorStop(2, new Rgba(3, 13, 23, 33))));
        assertEquals(ElevationUnit.INTERNATIONAL_FOOT, ramp.unit());
        assertEquals(new Rgba(0, 10, 20, 30), ramp.colorAt(-1));
        assertEquals(new Rgba(3, 13, 23, 33), ramp.colorAt(3));
        assertEquals(new Rgba(2, 12, 22, 32), ramp.colorAt(1));
        assertEquals(ramp.stops().getFirst().color(), ramp.colorAt(0));
        assertThrows(IllegalArgumentException.class, () -> ramp.colorAt(Double.POSITIVE_INFINITY));
    }

    @Test
    void hillshadeBoundsAndStyleWithersAreImmutable() {
        assertEquals(new ElevationHillshade(315, 45, 1), ElevationHillshade.defaults());
        assertEquals(
                0L,
                Double.doubleToRawLongBits(new ElevationHillshade(-0.0, 45, 1).azimuthDegrees()));
        for (double invalid : new double[] {-1, 360, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(
                    IllegalArgumentException.class, () -> new ElevationHillshade(invalid, 45, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new ElevationHillshade(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ElevationHillshade(0, 91, 1));
        assertThrows(IllegalArgumentException.class, () -> new ElevationHillshade(0, 45, 0));
        assertThrows(IllegalArgumentException.class, () -> new ElevationHillshade(0, 45, 101));

        ElevationColorRamp ramp = ramp();
        ElevationRasterStyle base = ElevationRasterStyle.of(ramp);
        assertEquals(Rgba.TRANSPARENT, base.noDataColor());
        assertTrue(base.hillshade().isEmpty());
        ElevationRasterStyle colored = base.withNoDataColor(Rgba.rgb(1, 2, 3));
        ElevationRasterStyle shaded = colored.withHillshade(ElevationHillshade.defaults());
        assertNotSame(base, colored);
        assertEquals(Rgba.rgb(1, 2, 3), shaded.noDataColor());
        assertEquals(ElevationHillshade.defaults(), shaded.hillshade().orElseThrow());
        assertTrue(shaded.withoutHillshade().hillshade().isEmpty());
        assertThrows(
                NullPointerException.class,
                () -> {
                    ElevationRasterStyle ignored = base.withNoDataColor(null);
                    assertTrue(ignored.hillshade().isEmpty());
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    ElevationRasterStyle ignored = base.withHillshade(null);
                    assertTrue(ignored.hillshade().isEmpty());
                });
    }

    private static ElevationColorRamp ramp() {
        return new ElevationColorRamp(
                ElevationUnit.METRE,
                List.of(
                        new ElevationColorStop(0, Rgba.rgb(0, 0, 0)),
                        new ElevationColorStop(1, Rgba.rgb(255, 255, 255))));
    }
}
