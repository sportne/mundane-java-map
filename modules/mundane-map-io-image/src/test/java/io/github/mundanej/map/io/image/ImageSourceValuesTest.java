package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertEquals(65_536, defaults.maximumContainerElements());
        assertEquals(67_141_632, defaults.maximumInflatedRasterBytes());
        assertEquals(5, defaults.withMaximumLogicalChannels(5).maximumLogicalChannels());
        assertEquals(defaults.maximumWidth(), defaults.withMaximumHeight(7).maximumWidth());
        assertEquals(
                defaults.maximumEncodedBytes(),
                defaults.withMaximumWorldFileBytes(7).maximumEncodedBytes());
        assertEquals(9, defaults.withMaximumWorldFileLineBytes(9).maximumWorldFileLineBytes());
        assertEquals(10, defaults.withMaximumContainerElements(10).maximumContainerElements());
        assertEquals(11, defaults.withMaximumInflatedRasterBytes(11).maximumInflatedRasterBytes());
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
    void tenFieldLimitsAndCachePolicyHaveCompatibleValueSemantics() {
        ImageSourceLimits limits = new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 19, 10),
                limits.withMaximumContainerElements(19));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 9, 20),
                limits.withMaximumInflatedRasterBytes(20));
        assertEquals(9, limits.withMaximumEncodedBytes(21).maximumContainerElements());
        assertEquals(10, limits.withMaximumHeaderBytes(22).maximumInflatedRasterBytes());
        assertEquals(9, limits.withMaximumWidth(23).maximumContainerElements());
        assertEquals(10, limits.withMaximumHeight(24).maximumInflatedRasterBytes());
        assertEquals(9, limits.withMaximumPixels(25).maximumContainerElements());
        assertEquals(10, limits.withMaximumLogicalChannels(26).maximumInflatedRasterBytes());
        assertEquals(9, limits.withMaximumWorldFileBytes(27).maximumContainerElements());
        assertEquals(10, limits.withMaximumWorldFileLineBytes(28).maximumInflatedRasterBytes());
        assertEquals(
                limits.hashCode(), new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).hashCode());
        assertTrue(limits.toString().contains("maximumContainerElements=9"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 0, 10));

        long[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        for (int zero = 0; zero < values.length; zero++) {
            long[] candidate = values.clone();
            candidate[zero] = 0;
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ImageSourceLimits(
                                    candidate[0],
                                    candidate[1],
                                    Math.toIntExact(candidate[2]),
                                    Math.toIntExact(candidate[3]),
                                    candidate[4],
                                    Math.toIntExact(candidate[5]),
                                    candidate[6],
                                    Math.toIntExact(candidate[7]),
                                    candidate[8],
                                    candidate[9]));
        }

        assertEquals(
                new ImageSourceLimits(21, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                limits.withMaximumEncodedBytes(21));
        assertEquals(
                new ImageSourceLimits(1, 22, 3, 4, 5, 6, 7, 8, 9, 10),
                limits.withMaximumHeaderBytes(22));
        assertEquals(
                new ImageSourceLimits(1, 2, 23, 4, 5, 6, 7, 8, 9, 10), limits.withMaximumWidth(23));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 24, 5, 6, 7, 8, 9, 10),
                limits.withMaximumHeight(24));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 25, 6, 7, 8, 9, 10),
                limits.withMaximumPixels(25));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 26, 7, 8, 9, 10),
                limits.withMaximumLogicalChannels(26));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 6, 27, 8, 9, 10),
                limits.withMaximumWorldFileBytes(27));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 28, 9, 10),
                limits.withMaximumWorldFileLineBytes(28));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 29, 10),
                limits.withMaximumContainerElements(29));
        assertEquals(
                new ImageSourceLimits(1, 2, 3, 4, 5, 6, 7, 8, 9, 30),
                limits.withMaximumInflatedRasterBytes(30));

        ImageCachePolicy disabled = ImageCachePolicy.disabled();
        assertFalse(disabled.enabled());
        assertTrue(disabled.maximumEntries().isEmpty());
        assertTrue(disabled.maximumPixelBytes().isEmpty());
        assertEquals("ImageCachePolicy[disabled]", disabled.toString());
        assertEquals(ImageCachePolicy.bounded(8, 33_554_432), ImageCachePolicy.defaults());
        ImageCachePolicy bounded = ImageCachePolicy.bounded(2, 16);
        assertTrue(bounded.enabled());
        assertEquals(2, bounded.maximumEntries().orElseThrow());
        assertEquals(16, bounded.maximumPixelBytes().orElseThrow());
        assertEquals(bounded, ImageCachePolicy.bounded(2, 16));
        assertEquals(bounded.hashCode(), ImageCachePolicy.bounded(2, 16).hashCode());
        assertNotEquals(bounded, ImageCachePolicy.bounded(3, 16));
        assertNotEquals(bounded, ImageCachePolicy.bounded(2, 17));
        assertNotEquals(bounded, null);
        assertNotEquals(bounded, "cache");
        assertEquals(
                "ImageCachePolicy[maximumEntries=2, maximumPixelBytes=16]", bounded.toString());
        assertThrows(IllegalArgumentException.class, () -> ImageCachePolicy.bounded(0, 1));
        assertThrows(IllegalArgumentException.class, () -> ImageCachePolicy.bounded(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> ImageCachePolicy.bounded(1, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageCachePolicy.bounded(1, -1));

        ImageOpenOptions old =
                new ImageOpenOptions(
                        ImageSourceLimits.defaults(),
                        RasterSourceLimits.LEVEL_1,
                        ImagePlacement.unplaced());
        assertEquals(ImageCachePolicy.defaults(), old.cachePolicy());
        assertEquals(
                ImageCachePolicy.disabled(),
                old.withCachePolicy(ImageCachePolicy.disabled()).cachePolicy());
        assertThrows(
                NullPointerException.class,
                () ->
                        new ImageOpenOptions(
                                old.imageLimits(), old.requestLimits(), old.placement(), null));
        assertThrows(
                NullPointerException.class,
                () -> new ImageOpenOptions(null, old.requestLimits(), old.placement()));
        assertThrows(
                NullPointerException.class,
                () -> new ImageOpenOptions(old.imageLimits(), null, old.placement()));
        assertThrows(
                NullPointerException.class,
                () -> new ImageOpenOptions(old.imageLimits(), old.requestLimits(), null));
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
