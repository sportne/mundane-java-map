package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoTiffRasterRenderingTest {
    @Test
    void rendersProjectedRgbGeoTiffWithoutImplicitReprojection() {
        RasterSource source =
                GeoTiffFiles.openRaster(
                        new SourceIdentity("awt-geotiff-rgb", "AWT projected RGB GeoTIFF"),
                        projectedRgbFixture(),
                        GeoTiffRasterOptions.defaults());
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        try {
            view.setLayerBindings(
                    List.of(MapLayerBinding.ownedRaster("geotiff-rgb", "GeoTIFF RGB", source)));
            view.setSize(120, 90);
            view.fitToData(8);
            view.setOpaque(false);
            BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.MAGENTA);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
            int colored = 0;
            int changed = 0;
            for (int row = 0; row < image.getHeight(); row++) {
                for (int column = 0; column < image.getWidth(); column++) {
                    int rgb = image.getRGB(column, row);
                    if (rgb != Color.MAGENTA.getRGB()) {
                        changed++;
                        int red = (rgb >>> 16) & 0xff;
                        int green = (rgb >>> 8) & 0xff;
                        int blue = rgb & 0xff;
                        if (red != green || green != blue) {
                            colored++;
                        }
                    }
                }
            }
            assertEquals(Color.MAGENTA.getRGB(), image.getRGB(2, 2));
            assertTrue(changed > 6_000, "too few projected raster pixels " + changed);
            assertTrue(colored > 4_000, "RGB channels were not retained " + colored);
        } finally {
            view.close();
        }
        assertTrue(source.isClosed());
    }

    @Test
    void rendersFirstGeoTiffAreaRasterThroughMapView() {
        RasterSource source =
                GeoTiffFiles.openRaster(
                        new SourceIdentity("awt-geotiff", "AWT GeoTIFF"),
                        fixture(),
                        GeoTiffRasterOptions.defaults());
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);
        try {
            view.setLayerBindings(
                    List.of(MapLayerBinding.ownedRaster("geotiff", "GeoTIFF", source)));
            view.setSize(120, 90);
            view.fitToData(8);
            view.setOpaque(false);
            BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.MAGENTA);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            int grayscale = 0;
            int darkest = 255;
            int lightest = 0;
            for (int row = 0; row < image.getHeight(); row++) {
                for (int column = 0; column < image.getWidth(); column++) {
                    int rgb = image.getRGB(column, row);
                    if (rgb != Color.MAGENTA.getRGB()) {
                        minX = Math.min(minX, column);
                        minY = Math.min(minY, row);
                        maxX = Math.max(maxX, column);
                        maxY = Math.max(maxY, row);
                        int red = (rgb >>> 16) & 0xff;
                        int green = (rgb >>> 8) & 0xff;
                        int blue = rgb & 0xff;
                        if (red == green && green == blue) {
                            grayscale++;
                            darkest = Math.min(darkest, red);
                            lightest = Math.max(lightest, red);
                        }
                    }
                }
            }
            assertEquals(Color.MAGENTA.getRGB(), image.getRGB(2, 2));
            assertTrue(minX >= 8 && minX <= 14, "unexpected west edge " + minX);
            assertTrue(maxX >= 105 && maxX <= 111, "unexpected east edge " + maxX);
            assertTrue(minY >= 6 && minY <= 14, "unexpected north edge " + minY);
            assertTrue(maxY >= 76 && maxY <= 84, "unexpected south edge " + maxY);
            assertTrue(Math.abs(minX + maxX - 119) <= 3, "raster was not horizontally centered");
            assertTrue(Math.abs(minY + maxY - 89) <= 3, "raster was not vertically centered");
            assertTrue(grayscale > 4_000, "too few grayscale pixels " + grayscale);
            assertTrue(darkest < 30, "dark region was lost: " + darkest);
            assertTrue(lightest > 190, "light region was lost: " + lightest);
        } finally {
            view.close();
        }
        assertTrue(source.isClosed());
    }

    private static byte[] fixture() {
        ByteBuffer bytes = ByteBuffer.allocate(286).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) 13);
        entry(bytes, 256, 3, 1, 4);
        entry(bytes, 257, 3, 1, 3);
        entry(bytes, 258, 3, 1, 8);
        entry(bytes, 259, 3, 1, 1);
        entry(bytes, 262, 3, 1, 1);
        entry(bytes, 273, 4, 1, 274);
        entry(bytes, 277, 3, 1, 1);
        entry(bytes, 278, 4, 1, 3);
        entry(bytes, 279, 4, 1, 12);
        entry(bytes, 284, 3, 1, 1);
        entry(bytes, 33550, 12, 3, 170);
        entry(bytes, 33922, 12, 6, 194);
        entry(bytes, 34735, 3, 16, 242);
        bytes.putInt(0);
        bytes.position(170).putDouble(1).putDouble(1).putDouble(0);
        bytes.position(194)
                .putDouble(0)
                .putDouble(0)
                .putDouble(0)
                .putDouble(10)
                .putDouble(20)
                .putDouble(0);
        bytes.position(242)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 3);
        key(bytes, 1024, 2);
        key(bytes, 1025, 1);
        key(bytes, 2048, 4326);
        bytes.position(274);
        for (int value = 0; value < 12; value++) {
            bytes.put((byte) (value * 20));
        }
        return bytes.array();
    }

    private static byte[] projectedRgbFixture() {
        ByteBuffer bytes = ByteBuffer.allocate(354).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) 15);
        entry(bytes, 256, 3, 1, 4);
        entry(bytes, 257, 3, 1, 3);
        entry(bytes, 258, 3, 3, 194);
        entry(bytes, 259, 3, 1, 1);
        entry(bytes, 262, 3, 1, 2);
        entry(bytes, 273, 4, 1, 318);
        entry(bytes, 274, 3, 1, 1);
        entry(bytes, 277, 3, 1, 3);
        entry(bytes, 278, 4, 1, 3);
        entry(bytes, 279, 4, 1, 36);
        entry(bytes, 284, 3, 1, 1);
        entry(bytes, 339, 3, 3, 200);
        entry(bytes, 33550, 12, 3, 206);
        entry(bytes, 33922, 12, 6, 230);
        entry(bytes, 34735, 3, 20, 278);
        bytes.putInt(0);
        bytes.position(194).putShort((short) 8).putShort((short) 8).putShort((short) 8);
        bytes.position(200).putShort((short) 1).putShort((short) 1).putShort((short) 1);
        bytes.position(206).putDouble(1).putDouble(1).putDouble(0);
        bytes.position(230)
                .putDouble(0)
                .putDouble(0)
                .putDouble(0)
                .putDouble(1_000)
                .putDouble(2_000)
                .putDouble(0);
        bytes.position(278)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 4);
        key(bytes, 1024, 1);
        key(bytes, 1025, 1);
        key(bytes, 3072, 3857);
        key(bytes, 3076, 9001);
        bytes.position(318);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                bytes.put((byte) (30 + column * 40));
                bytes.put((byte) (50 + row * 60));
                bytes.put((byte) (180 - column * 20));
            }
        }
        return bytes.array();
    }

    private static void entry(ByteBuffer bytes, int tag, int type, int count, int value) {
        bytes.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static void key(ByteBuffer bytes, int key, int value) {
        bytes.putShort((short) key).putShort((short) 0).putShort((short) 1).putShort((short) value);
    }
}
