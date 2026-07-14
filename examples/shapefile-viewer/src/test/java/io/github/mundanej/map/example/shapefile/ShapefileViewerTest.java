package io.github.mundanej.map.example.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
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
        assertEquals("SHAPEFILE_SHX_MISSING", openingCode(fixture));
        RenderedView sequential = render(fixture);

        assertEquals(1, sequential.map().layerBindings().size());
        assertTrue(countNonWhite(sequential.image()) > 20);
        SwingUtilities.invokeAndWait(sequential.map()::close);

        Path index = temporaryDirectory.resolve("multipoint.shx");
        Files.write(index, multipointIndexFixture());
        assertEquals("", openingCode(fixture));
        RenderedView indexed = render(fixture);

        assertEquals(1, indexed.map().layerBindings().size());
        assertTrue(countNonWhite(indexed.image()) > 20);
        assertEquals(sequential.viewport(), indexed.viewport());
        assertEquals(signature(sequential.image()), signature(indexed.image()));
        SwingUtilities.invokeAndWait(indexed.map()::close);
        Files.delete(index);
        Files.delete(fixture);
        assertFalse(Files.exists(index));
        assertFalse(Files.exists(fixture));
    }

    @Test
    void rendersSingleAndMultipartLinesWithoutBridgingParts() throws Exception {
        Path fixture = temporaryDirectory.resolve("lines.shp");
        Files.write(fixture, polylineFixture());

        RenderedView rendered = render(fixture);
        var projection =
                CrsRegistry.level1().operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        Coordinate firstPart =
                rendered.viewport().worldToScreen(projection.transform(new Coordinate(0, 0)));
        Coordinate secondPart =
                rendered.viewport().worldToScreen(projection.transform(new Coordinate(2, 0)));
        Coordinate singlePart =
                rendered.viewport().worldToScreen(projection.transform(new Coordinate(-1.5, -0.5)));
        Coordinate interPartGap =
                rendered.viewport().worldToScreen(projection.transform(new Coordinate(1, 1)));

        assertTrue(hasColoredPixel(rendered.image(), singlePart, 3));
        assertTrue(hasColoredPixel(rendered.image(), firstPart, 3));
        assertTrue(hasColoredPixel(rendered.image(), secondPart, 3));
        assertFalse(hasColoredPixel(rendered.image(), interPartGap, 3));
        SwingUtilities.invokeAndWait(rendered.map()::close);
        Files.delete(fixture);
        assertFalse(Files.exists(fixture));
    }

    @Test
    void rendersPolygonHolesDisjointShellsAndNestedIslands() throws Exception {
        Path fixture = temporaryDirectory.resolve("polygons.shp");
        Files.write(fixture, polygonFixture());

        RenderedView rendered = render(fixture);
        CrsOperation projection =
                CrsRegistry.level1().operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);

        assertTrue(hasColoredPixel(rendered, projection, new Coordinate(-3.5, 0), 2));
        assertFalse(hasColoredPixel(rendered, projection, new Coordinate(2, 0), 2));
        assertTrue(hasColoredPixel(rendered, projection, new Coordinate(0, 0), 2));
        assertTrue(hasColoredPixel(rendered, projection, new Coordinate(7, 0), 2));
        assertFalse(hasColoredPixel(rendered, projection, new Coordinate(5, 0), 2));

        SwingUtilities.invokeAndWait(rendered.map()::close);
        Files.delete(fixture);
        assertFalse(Files.exists(fixture));
    }

    private static RenderedView render(Path fixture) throws Exception {
        AtomicReference<MapView> mapReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        AtomicReference<MapViewport> viewportReference = new AtomicReference<>();

        SwingUtilities.invokeAndWait(
                () -> {
                    MapView map = ShapefileViewer.createMapView(fixture, CrsDefinitions.EPSG_4326);
                    map.setSize(240, 180);
                    map.fitToData(20.0);
                    mapReference.set(map);
                    viewportReference.set(map.viewport());
                    imageReference.set(paint(map, 240, 180));
                });
        return new RenderedView(mapReference.get(), imageReference.get(), viewportReference.get());
    }

    private static String openingCode(Path fixture) {
        try (FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity("viewer-test", "Viewer test"),
                        fixture,
                        ShapefileOpenOptions.defaults()
                                .withCrsOverride(CrsDefinitions.EPSG_4326))) {
            return source.openingDiagnostics().entries().stream()
                    .map(diagnostic -> diagnostic.code())
                    .findFirst()
                    .orElse("");
        }
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

    private static boolean hasColoredPixel(BufferedImage image, Coordinate coordinate, int radius) {
        int centerX = (int) Math.round(coordinate.x());
        int centerY = (int) Math.round(coordinate.y());
        for (int row = Math.max(0, centerY - radius);
                row <= Math.min(image.getHeight() - 1, centerY + radius);
                row++) {
            for (int column = Math.max(0, centerX - radius);
                    column <= Math.min(image.getWidth() - 1, centerX + radius);
                    column++) {
                if (image.getRGB(column, row) != 0xffff_ffff) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasColoredPixel(
            RenderedView rendered, CrsOperation projection, Coordinate source, int radius) {
        Coordinate screen = rendered.viewport().worldToScreen(projection.transform(source));
        return hasColoredPixel(rendered.image(), screen, radius);
    }

    private static RenderSignature signature(BufferedImage image) {
        long count = 0;
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                if (image.getRGB(column, row) != 0xffff_ffff) {
                    count++;
                    minX = Math.min(minX, column);
                    minY = Math.min(minY, row);
                    maxX = Math.max(maxX, column);
                    maxY = Math.max(maxY, row);
                }
            }
        }
        return new RenderSignature(count, minX, minY, maxX, maxY);
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

    private static byte[] multipointIndexFixture() {
        ByteBuffer bytes = ByteBuffer.allocate(116);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(9994);
        for (int index = 0; index < 5; index++) {
            bytes.putInt(0);
        }
        bytes.putInt(58);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1000);
        bytes.putInt(8);
        putBounds(bytes, -1.0, -1.0, 1.0, 1.0);
        bytes.putDouble(0.0).putDouble(0.0).putDouble(0.0).putDouble(0.0);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(50).putInt(2);
        bytes.putInt(56).putInt(36);
        assertEquals(116, bytes.position());
        return bytes.array();
    }

    private static byte[] polylineFixture() {
        byte[] nullShape = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array();
        byte[] single = polyline(new int[] {0}, -2, -0.5, -1, -0.5);
        byte[] multipart = polyline(new int[] {0, 2}, 0, -1, 0, 1, 2, 1, 2, -1);
        int size = 100 + 8 + nullShape.length + 8 + single.length + 8 + multipart.length;
        ByteBuffer bytes = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(9994);
        for (int index = 0; index < 5; index++) {
            bytes.putInt(0);
        }
        bytes.putInt(size / 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(3);
        putBounds(bytes, -2, -1, 2, 1);
        bytes.putDouble(0).putDouble(0).putDouble(0).putDouble(0);
        putRecord(bytes, 1, nullShape);
        putRecord(bytes, 2, single);
        putRecord(bytes, 3, multipart);
        assertEquals(size, bytes.position());
        return bytes.array();
    }

    private static byte[] polygonFixture() {
        byte[] polygon =
                multipartShape(
                        5,
                        new int[] {0, 5, 10, 15},
                        -4,
                        -4,
                        -4,
                        4,
                        4,
                        4,
                        4,
                        -4,
                        -4,
                        -4,
                        -3,
                        -3,
                        3,
                        -3,
                        3,
                        3,
                        -3,
                        3,
                        -3,
                        -3,
                        -1,
                        -1,
                        -1,
                        1,
                        1,
                        1,
                        1,
                        -1,
                        -1,
                        -1,
                        6,
                        -2,
                        6,
                        2,
                        9,
                        2,
                        9,
                        -2,
                        6,
                        -2);
        int size = 100 + 8 + polygon.length;
        ByteBuffer bytes = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(9994);
        for (int index = 0; index < 5; index++) {
            bytes.putInt(0);
        }
        bytes.putInt(size / 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(5);
        putBounds(bytes, -4, -4, 9, 4);
        bytes.putDouble(0).putDouble(0).putDouble(0).putDouble(0);
        putRecord(bytes, 1, polygon);
        assertEquals(size, bytes.position());
        return bytes.array();
    }

    private static byte[] polyline(int[] starts, double... coordinates) {
        return multipartShape(3, starts, coordinates);
    }

    private static byte[] multipartShape(int type, int[] starts, double... coordinates) {
        int points = coordinates.length / 2;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < coordinates.length; index += 2) {
            minX = Math.min(minX, coordinates[index]);
            minY = Math.min(minY, coordinates[index + 1]);
            maxX = Math.max(maxX, coordinates[index]);
            maxY = Math.max(maxY, coordinates[index + 1]);
        }
        ByteBuffer bytes =
                ByteBuffer.allocate(44 + starts.length * 4 + coordinates.length * 8)
                        .order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(type);
        putBounds(bytes, minX, minY, maxX, maxY);
        bytes.putInt(starts.length).putInt(points);
        for (int start : starts) {
            bytes.putInt(start);
        }
        for (double coordinate : coordinates) {
            bytes.putDouble(coordinate);
        }
        return bytes.array();
    }

    private static void putRecord(ByteBuffer bytes, int number, byte[] content) {
        bytes.order(ByteOrder.BIG_ENDIAN).putInt(number).putInt(content.length / 2);
        bytes.put(content);
    }

    private static void putBounds(
            ByteBuffer bytes, double minimumX, double minimumY, double maximumX, double maximumY) {
        bytes.putDouble(minimumX);
        bytes.putDouble(minimumY);
        bytes.putDouble(maximumX);
        bytes.putDouble(maximumY);
    }

    private record RenderedView(MapView map, BufferedImage image, MapViewport viewport) {}

    private record RenderSignature(long count, int minX, int minY, int maxX, int maxY) {}
}
