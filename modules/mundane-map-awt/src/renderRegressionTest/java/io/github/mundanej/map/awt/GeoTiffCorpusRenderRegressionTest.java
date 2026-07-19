package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GeoTiffCorpusRenderRegressionTest {
    @Test
    void independentRasterAndElevationFixturesRenderWithTolerantColorEvidence() throws Exception {
        RasterSource raster =
                GeoTiffFiles.openRaster(
                        new SourceIdentity("corpus-raster", "Corpus raster"),
                        resource("gdal-rgb-strip-none-4326.tif"),
                        GeoTiffRasterOptions.defaults());
        BufferedImage rasterImage =
                paint(
                        MapLayerBinding.ownedRaster("corpus-raster", "Corpus raster", raster),
                        CrsDefinitions.EPSG_4326);
        assertColorEvidence(rasterImage, 12_000, 4_000);
        assertTrue(raster.isClosed());

        ElevationSource elevation =
                GeoTiffFiles.openElevation(
                        new SourceIdentity("corpus-elevation", "Corpus elevation"),
                        resource("gdal-int16-strip-packbits-4326.tif"),
                        GeoTiffElevationOptions.of(ElevationUnit.METRE));
        ElevationRasterStyle style =
                ElevationRasterStyle.of(
                        new ElevationColorRamp(
                                ElevationUnit.METRE,
                                List.of(
                                        new ElevationColorStop(-32_768, Rgba.rgb(16, 48, 120)),
                                        new ElevationColorStop(-24_000, Rgba.rgb(40, 150, 70)),
                                        new ElevationColorStop(-15_000, Rgba.rgb(240, 230, 160)))));
        BufferedImage elevationImage =
                paint(
                        MapLayerBinding.ownedElevation(
                                "corpus-elevation", "Corpus elevation", elevation, style),
                        CrsDefinitions.EPSG_4326);
        assertColorEvidence(elevationImage, 12_000, 3_000);
        assertTrue(elevation.isClosed());
    }

    private static BufferedImage paint(MapLayerBinding binding, CrsDefinition crs)
            throws Exception {
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(CrsRegistry.level1(), crs, crs);
                    try {
                        view.setLayerBindings(List.of(binding));
                        view.setSize(320, 240);
                        view.fitToData(24);
                        BufferedImage image =
                                new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(Color.WHITE);
                            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        result.set(image);
                    } finally {
                        view.close();
                    }
                });
        return result.get();
    }

    private static void assertColorEvidence(
            BufferedImage image, int minimumColored, int minimumVaried) {
        int colored = 0;
        int varied = 0;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                Color color = new Color(image.getRGB(column, row), true);
                if (color.getRed() < 245 || color.getGreen() < 245 || color.getBlue() < 245) {
                    colored++;
                    if (Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()))
                                    - Math.min(
                                            color.getRed(),
                                            Math.min(color.getGreen(), color.getBlue()))
                            > 20) {
                        varied++;
                    }
                }
            }
        }
        assertTrue(colored > minimumColored, "too little corpus color evidence " + colored);
        assertTrue(varied > minimumVaried, "too little corpus variation evidence " + varied);
    }

    private static byte[] resource(String name) throws IOException {
        try (var input =
                GeoTiffCorpusRenderRegressionTest.class.getResourceAsStream(
                        "/geotiff-corpus/data/" + name)) {
            if (input == null) {
                throw new AssertionError("Missing corpus fixture " + name);
            }
            return input.readAllBytes();
        }
    }
}
