package io.github.mundanej.map.example.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileViewerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validatesTheExplicitCliBeforeSwingScheduling() {
        Path path = temporaryDirectory.resolve("points.shp");
        ShapefileViewer.LaunchArguments launch =
                ShapefileViewer.parseArguments(new String[] {path.toString(), "EPSG:4326"});

        assertEquals(path, launch.path());
        assertEquals(CrsDefinitions.EPSG_4326, launch.crs());
        assertThrows(
                IllegalArgumentException.class,
                () -> ShapefileViewer.parseArguments(new String[] {path.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShapefileViewer.parseArguments(new String[] {path.toString(), "epsg:4326"}));
        assertEquals(
                CrsDefinitions.EPSG_3857,
                ShapefileViewer.parseArguments(new String[] {path.toString(), "EPSG:3857"}).crs());
        assertThrows(NullPointerException.class, () -> ShapefileViewer.parseArguments(null));
        assertThrows(
                NullPointerException.class,
                () -> ShapefileViewer.parseArguments(new String[] {null, "EPSG:4326"}));
        assertThrows(
                NullPointerException.class,
                () -> ShapefileViewer.parseArguments(new String[] {path.toString(), null}));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShapefileViewer.main(new String[] {path.toString()}));
    }

    @Test
    void validatesMapCreationInputsAndClosesOpeningFailures() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> ShapefileViewer.createMapView(null, CrsDefinitions.EPSG_4326));
        assertThrows(
                NullPointerException.class,
                () -> ShapefileViewer.createMapView(temporaryDirectory.resolve("a.shp"), null));
        assertThrows(
                RuntimeException.class,
                () ->
                        ShapefileViewer.createMapView(
                                temporaryDirectory.resolve("missing.shp"),
                                CrsDefinitions.EPSG_4326));

        Path malformed = temporaryDirectory.resolve("malformed.shp");
        Files.write(malformed, new byte[100]);
        assertThrows(
                RuntimeException.class,
                () -> ShapefileViewer.createMapView(malformed, CrsDefinitions.EPSG_4326));
        Files.delete(malformed);
    }

    @Test
    void opensFitsAndRendersARealNullAndMultipointFixtureThenReleasesIt() throws Exception {
        Path fixture = temporaryDirectory.resolve("multipoint.shp");
        Files.write(fixture, multipointFixture());
        AtomicReference<MapView> mapReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();

        SwingUtilities.invokeAndWait(
                () -> {
                    MapView map = ShapefileViewer.createMapView(fixture, CrsDefinitions.EPSG_4326);
                    map.setSize(240, 180);
                    map.fitToData(20.0);
                    mapReference.set(map);
                    imageReference.set(paint(map, 240, 180));
                });

        MapView map = mapReference.get();
        assertEquals(1, map.layerBindings().size());
        assertTrue(map.sourceReports().isEmpty());
        assertTrue(countNonWhite(imageReference.get()) > 20);
        SwingUtilities.invokeAndWait(map::close);
        Files.delete(fixture);
        assertFalse(Files.exists(fixture));
    }

    private static BufferedImage paint(MapView map, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            map.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static long countNonWhite(BufferedImage image) {
        long count = 0;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                if (image.getRGB(column, row) != 0xffff_ffff) {
                    count++;
                }
            }
        }
        return count;
    }

    private static byte[] multipointFixture() {
        ByteBuffer bytes = ByteBuffer.allocate(192);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(9994);
        for (int index = 0; index < 5; index++) {
            bytes.putInt(0);
        }
        bytes.putInt(96);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1000);
        bytes.putInt(8);
        putBounds(bytes, -1.0, -1.0, 1.0, 1.0);
        bytes.putDouble(0.0).putDouble(0.0).putDouble(0.0).putDouble(0.0);

        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(1).putInt(2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0);

        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(2).putInt(36);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(8);
        putBounds(bytes, -1.0, -1.0, 1.0, 1.0);
        bytes.putInt(2);
        bytes.putDouble(-1.0).putDouble(-1.0);
        bytes.putDouble(1.0).putDouble(1.0);
        assertEquals(192, bytes.position());
        return bytes.array();
    }

    private static void putBounds(
            ByteBuffer bytes, double minimumX, double minimumY, double maximumX, double maximumY) {
        bytes.putDouble(minimumX);
        bytes.putDouble(minimumY);
        bytes.putDouble(maximumX);
        bytes.putDouble(maximumY);
    }
}
