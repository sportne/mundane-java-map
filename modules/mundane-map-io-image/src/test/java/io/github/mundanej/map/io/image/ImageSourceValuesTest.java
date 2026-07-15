package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.core.CrsDefinitions;
import org.junit.jupiter.api.Test;

class ImageSourceValuesTest {
    @Test
    void defaultsAndWithersAreImmutableAndBounded() {
        ImageSourceLimits defaults = ImageSourceLimits.defaults();
        assertEquals(33_554_432, defaults.maximumEncodedBytes());
        assertEquals(1_048_576, defaults.maximumHeaderBytes());
        assertEquals(16_384, defaults.maximumWidth());
        assertEquals(16_777_216, defaults.maximumPixels());
        assertEquals(5, defaults.withMaximumLogicalChannels(5).maximumLogicalChannels());
        assertEquals(defaults.maximumWidth(), defaults.withMaximumHeight(7).maximumWidth());
        assertThrows(IllegalArgumentException.class, () -> new ImageSourceLimits(0, 1, 1, 1, 1, 1));
    }

    @Test
    void placementAndOptionsRetainOnlyExplicitValues() {
        ImagePlacement unplaced = ImagePlacement.unplaced();
        assertTrue(unplaced.mapBounds().isEmpty());
        ImagePlacement placed =
                ImagePlacement.axisAligned(new Envelope(0, 0, 10, 5), webMercator());
        ImageOpenOptions options = ImageOpenOptions.defaults().withPlacement(placed);
        assertEquals(placed, options.placement());
        assertEquals(RasterSourceLimits.LEVEL_1, options.requestLimits());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ImagePlacement(
                                java.util.Optional.empty(), java.util.Optional.of(webMercator())));
    }

    private static CrsMetadata webMercator() {
        return CrsMetadata.recognized(
                CrsDefinitions.EPSG_3857,
                java.util.Optional.of("EPSG:3857"),
                java.util.Optional.empty());
    }
}
