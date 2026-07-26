package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSourceLimits;
import org.junit.jupiter.api.Test;

class GeoTiffRasterOptionsTest {
    @Test
    void defaultsAndWithersRetainIndependentValidatedLimits() {
        GeoTiffRasterOptions defaults = GeoTiffRasterOptions.defaults();
        assertSameDefaults(defaults);
        RasterSourceLimits requestLimits =
                new RasterSourceLimits(new RasterRequestLimits(1, 1, 1, 1, 1, 1));
        GeoTiffRasterOptions requests = defaults.withRequestLimits(requestLimits);
        assertEquals(requestLimits, requests.requestLimits());
        assertEquals(defaults.formatLimits(), requests.formatLimits());
        GeoTiffLimits formatLimits = GeoTiffLimits.defaults().withMaximumIfdEntries(1);
        GeoTiffRasterOptions format = defaults.withFormatLimits(formatLimits);
        assertEquals(formatLimits, format.formatLimits());
        assertEquals(defaults.requestLimits(), format.requestLimits());
        assertNotSame(defaults, format);
        assertThrows(
                NullPointerException.class, () -> assertNotNull(defaults.withFormatLimits(null)));
        assertThrows(
                NullPointerException.class, () -> assertNotNull(defaults.withRequestLimits(null)));
        assertThrows(
                NullPointerException.class, () -> new GeoTiffRasterOptions(null, requestLimits));
    }

    private static void assertSameDefaults(GeoTiffRasterOptions defaults) {
        assertEquals(GeoTiffLimits.defaults(), defaults.formatLimits());
        assertEquals(RasterSourceLimits.LEVEL_1, defaults.requestLimits());
        assertEquals(defaults, GeoTiffRasterOptions.defaults());
    }
}
