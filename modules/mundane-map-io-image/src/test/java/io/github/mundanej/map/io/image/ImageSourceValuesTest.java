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
        assertEquals(4_096, defaults.maximumWorldFileBytes());
        assertEquals(256, defaults.maximumWorldFileLineBytes());
        assertEquals(5, defaults.withMaximumLogicalChannels(5).maximumLogicalChannels());
        assertEquals(defaults.maximumWidth(), defaults.withMaximumHeight(7).maximumWidth());
        assertEquals(
                defaults.maximumEncodedBytes(),
                defaults.withMaximumWorldFileBytes(7).maximumEncodedBytes());
        assertEquals(9, defaults.withMaximumWorldFileLineBytes(9).maximumWorldFileLineBytes());
        assertEquals(
                defaults,
                new ImageSourceLimits(
                        defaults.maximumEncodedBytes(),
                        defaults.maximumHeaderBytes(),
                        defaults.maximumWidth(),
                        defaults.maximumHeight(),
                        defaults.maximumPixels(),
                        defaults.maximumLogicalChannels()));
        assertThrows(IllegalArgumentException.class, () -> new ImageSourceLimits(0, 1, 1, 1, 1, 1));
    }

    @Test
    void fullConstructorAndEveryWitherPreserveAllEightLimitFields() {
        ImageSourceLimits limits = new ImageSourceLimits(11, 12, 13, 14, 15, 16, 17, 18);
        assertEquals(
                new ImageSourceLimits(21, 12, 13, 14, 15, 16, 17, 18),
                limits.withMaximumEncodedBytes(21));
        assertEquals(
                new ImageSourceLimits(11, 22, 13, 14, 15, 16, 17, 18),
                limits.withMaximumHeaderBytes(22));
        assertEquals(
                new ImageSourceLimits(11, 12, 23, 14, 15, 16, 17, 18), limits.withMaximumWidth(23));
        assertEquals(
                new ImageSourceLimits(11, 12, 13, 24, 15, 16, 17, 18),
                limits.withMaximumHeight(24));
        assertEquals(
                new ImageSourceLimits(11, 12, 13, 14, 25, 16, 17, 18),
                limits.withMaximumPixels(25));
        assertEquals(
                new ImageSourceLimits(11, 12, 13, 14, 15, 26, 17, 18),
                limits.withMaximumLogicalChannels(26));
        assertEquals(
                new ImageSourceLimits(11, 12, 13, 14, 15, 16, 27, 18),
                limits.withMaximumWorldFileBytes(27));
        assertEquals(
                new ImageSourceLimits(11, 12, 13, 14, 15, 16, 17, 28),
                limits.withMaximumWorldFileLineBytes(28));
        assertEquals(limits, new ImageSourceLimits(11, 12, 13, 14, 15, 16, 17, 18));
        assertEquals(
                limits.hashCode(),
                new ImageSourceLimits(11, 12, 13, 14, 15, 16, 17, 18).hashCode());
        assertTrue(limits.toString().contains("maximumWorldFileBytes=17"));
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
        assertEquals(
                ImagePlacement.Kind.WORLD_FILE,
                ImagePlacement.worldFile(java.util.Optional.of(webMercator())).kind());
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
