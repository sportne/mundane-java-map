package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import org.junit.jupiter.api.Test;

class AwtSourceCoverageTest {
    @Test
    void rasterOptionsCopyAndNormalizeAllSupportedValues() {
        RasterRenderOptions defaults = RasterRenderOptions.defaults();
        assertEquals(RasterInterpolation.NEAREST, defaults.interpolation());
        assertEquals(
                RasterInterpolation.BILINEAR,
                defaults.withInterpolation(RasterInterpolation.BILINEAR).interpolation());
        assertEquals(0.25, defaults.withOpacity(0.25).opacity());
        assertEquals(
                Double.doubleToLongBits(0.0),
                Double.doubleToLongBits(defaults.withOpacity(-0.0).opacity()));
        assertThrows(
                NullPointerException.class, () -> assertNotNull(defaults.withInterpolation(null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withOpacity(Double.NaN)));
    }

    @Test
    void overlayRendersMoveSnapWrapAndUnrepresentablePreviews() {
        MapViewport viewport = new MapViewport(64, 64, 0, 0, 1);
        CrsRegistry registry = CrsRegistry.level1();
        CrsDefinition webMercator = CrsDefinitions.EPSG_3857;
        CrsOperation identity = registry.operation(webMercator, webMercator);
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            PointEditOverlayRenderer.render(
                    graphics,
                    new PointEditController.Preview(
                            viewport,
                            Optional.of(new Coordinate(-5, 0)),
                            new Coordinate(5, 0),
                            true,
                            0),
                    identity,
                    viewport,
                    Optional.of(HorizontalWrap.webMercator()));
            PointEditOverlayRenderer.render(
                    graphics,
                    new PointEditController.Preview(
                            viewport, Optional.empty(), new Coordinate(0, 0), false, 0),
                    identity,
                    viewport,
                    Optional.empty());

            CrsDefinition geographic = CrsDefinitions.EPSG_4326;
            PointEditOverlayRenderer.render(
                    graphics,
                    new PointEditController.Preview(
                            viewport, Optional.empty(), new Coordinate(200, 0), false, 0),
                    registry.operation(geographic, geographic),
                    viewport,
                    Optional.empty());
        } finally {
            graphics.dispose();
        }
        assertTrue(nonTransparentPixels(image) > 0);
    }

    @Test
    void decoderFactoryReportsWhenItsFixedJdkProviderIsUnavailable() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        List<ImageReaderSpi> removed = new ArrayList<>();
        Iterator<ImageReaderSpi> providers =
                registry.getServiceProviders(ImageReaderSpi.class, true);
        while (providers.hasNext()) {
            ImageReaderSpi provider = providers.next();
            if ("java.desktop".equals(provider.getClass().getModule().getName())
                    && declaresPng(provider)) {
                removed.add(provider);
            }
        }
        try {
            removed.forEach(registry::deregisterServiceProvider);
            AwtRasterDecoders.DecoderConfigurationException failure =
                    assertThrows(
                            AwtRasterDecoders.DecoderConfigurationException.class,
                            AwtRasterDecoders::level1);
            assertEquals("RASTER_DECODER_JDK_READER_UNAVAILABLE", failure.code());
            assertEquals("PNG", failure.context().get("format"));
            assertEquals("0", failure.context().get("eligibleCount"));
        } finally {
            removed.forEach(registry::registerServiceProvider);
        }
        assertNotNull(AwtRasterDecoders.level1());
    }

    private static boolean declaresPng(ImageReaderSpi provider) {
        for (String name : provider.getFormatNames()) {
            if ("png".equals(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int nonTransparentPixels(BufferedImage image) {
        int count = 0;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                if ((image.getRGB(column, row) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
