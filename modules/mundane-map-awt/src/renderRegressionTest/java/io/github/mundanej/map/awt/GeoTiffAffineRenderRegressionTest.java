package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GeoTiffAffineRenderRegressionTest {
    @Test
    void shearedRasterRetainsFootprintBoundsAndTolerantColorEvidence() throws Exception {
        RasterSource source =
                GeoTiffFiles.openRaster(
                        new SourceIdentity("render-geotiff-affine", "Render affine GeoTIFF"),
                        fixture(),
                        GeoTiffRasterOptions.defaults());
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedRaster(
                                            "affine", "Affine GeoTIFF", source)));
                    view.setSize(320, 240);
                    view.fitToData(28);
                    BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    viewReference.set(view);
                    imageReference.set(image);
                });

        MapView view = viewReference.get();
        BufferedImage image = imageReference.get();
        Coordinate center = view.mapToScreen(new Coordinate(1_004.75, 1_998.25)).orElseThrow();
        Color centerColor = sample(image, center);
        assertTrue(
                centerColor.getBlue() > centerColor.getRed() + 10,
                "expected retained blue-biased source color " + centerColor);

        Coordinate emptyEnvelopeCorner =
                view.mapToScreen(new Coordinate(1_000.2, 2_000.8)).orElseThrow();
        Color cornerColor = sample(image, emptyEnvelopeCorner);
        assertTrue(
                cornerColor.getRed() > 245
                        && cornerColor.getGreen() > 245
                        && cornerColor.getBlue() > 245,
                "affine envelope corner should remain background " + cornerColor);

        int colored = 0;
        int minX = image.getWidth();
        int maxX = -1;
        int minY = image.getHeight();
        int maxY = -1;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                Color color = new Color(image.getRGB(column, row), true);
                if (color.getBlue() > color.getRed() + 8 || color.getRed() > color.getGreen() + 8) {
                    colored++;
                    minX = Math.min(minX, column);
                    maxX = Math.max(maxX, column);
                    minY = Math.min(minY, row);
                    maxY = Math.max(maxY, row);
                }
            }
        }
        assertTrue(colored > 18_000, "too little affine color evidence " + colored);
        assertTrue(minX >= 20 && minX <= 40, "unexpected affine west bound " + minX);
        assertTrue(maxX >= 270 && maxX <= 290, "unexpected affine east bound " + maxX);
        assertTrue(minY >= 20 && minY <= 45, "unexpected affine north bound " + minY);
        assertTrue(maxY >= 195 && maxY <= 220, "unexpected affine south bound " + maxY);
        SwingUtilities.invokeAndWait(view::close);
        assertTrue(source.isClosed());
    }

    private static Color sample(BufferedImage image, Coordinate coordinate) {
        return new Color(
                image.getRGB((int) Math.round(coordinate.x()), (int) Math.round(coordinate.y())),
                true);
    }

    private static byte[] fixture() {
        ByteBuffer bytes = ByteBuffer.allocate(398).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) 14);
        entry(bytes, 256, 3, 1, 4);
        entry(bytes, 257, 3, 1, 3);
        entry(bytes, 258, 3, 3, 182);
        entry(bytes, 259, 3, 1, 1);
        entry(bytes, 262, 3, 1, 2);
        entry(bytes, 273, 4, 1, 362);
        entry(bytes, 274, 3, 1, 1);
        entry(bytes, 277, 3, 1, 3);
        entry(bytes, 278, 4, 1, 3);
        entry(bytes, 279, 4, 1, 36);
        entry(bytes, 284, 3, 1, 1);
        entry(bytes, 339, 3, 3, 188);
        entry(bytes, 34264, 12, 16, 194);
        entry(bytes, 34735, 3, 20, 322);
        bytes.putInt(0);
        bytes.position(182).putShort((short) 8).putShort((short) 8).putShort((short) 8);
        bytes.position(188).putShort((short) 1).putShort((short) 1).putShort((short) 1);
        bytes.position(194);
        for (double value :
                new double[] {2, 0.5, 0, 1_000, 0.25, -1.5, 0, 2_000, 0, 0, 1, 0, 0, 0, 0, 1}) {
            bytes.putDouble(value);
        }
        bytes.position(322)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 4);
        key(bytes, 1024, 1);
        key(bytes, 1025, 1);
        key(bytes, 3072, 3857);
        key(bytes, 3076, 9001);
        bytes.position(362);
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
