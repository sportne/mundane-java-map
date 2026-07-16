package consumer;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.RasterImages;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Public-artifact-only Level 1 consumer smoke. */
public final class MundaneMapConsumerSmoke {
    private MundaneMapConsumerSmoke() {}

    /** Runs the isolated downstream assertions. */
    public static void main(String[] arguments) throws Exception {
        require(Runtime.version().feature() == 21, "consumer must run on Java 21");
        CrsRegistry registry = CrsRegistry.level1();
        SymbolRendererRegistry renderers = SymbolRendererRegistry.builderWithBuiltIns().build();
        var decoders = AwtRasterDecoders.level1();
        renderVector(registry, renderers);
        Path directory = Files.createTempDirectory("mundane-map-consumer-");
        try {
            testShapefile(directory);
            testMalformedShapefile(directory);
            testImages(directory, decoders);
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(MundaneMapConsumerSmoke::delete);
            }
        }
        require(!Files.exists(directory), "consumer temporary directory leaked");
        System.out.println("mundane-map consumer smoke: OK");
    }

    private static void renderVector(CrsRegistry registry, SymbolRendererRegistry renderers)
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    registry,
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857,
                                    renderers);
                    try {
                        Feature feature =
                                new Feature(
                                        "consumer-point",
                                        "Consumer point",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of(),
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.DIAMOND,
                                                Rgba.rgb(20, 120, 220),
                                                18,
                                                1));
                        view.setLayers(List.of(new InMemoryLayer("consumer", "Consumer", List.of(feature))));
                        view.setSize(96, 96);
                        view.fitToData(16);
                        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(java.awt.Color.WHITE);
                            graphics.fillRect(0, 0, 96, 96);
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        int colored = 0;
                        for (int y = 32; y < 64; y++) {
                            for (int x = 32; x < 64; x++) {
                                int rgb = image.getRGB(x, y);
                                int red = (rgb >>> 16) & 0xff;
                                int green = (rgb >>> 8) & 0xff;
                                int blue = rgb & 0xff;
                                if (blue > red + 40 && blue > green + 20) colored++;
                            }
                        }
                        require(colored > 20, "vector symbol did not render in the expected region");
                    } finally {
                        view.close();
                    }
                });
    }

    private static void testShapefile(Path directory) throws Exception {
        Path shape = directory.resolve("point.shp");
        Files.write(shape, pointShp(0.25, 0.75));
        Files.write(directory.resolve("point.shx"), pointShx(0.25, 0.75));
        FeatureRecord retained;
        FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity("consumer-shape", "Consumer shape"),
                        shape,
                        ShapefileOpenOptions.defaults());
        try {
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            try {
                require(cursor.advance(), "point shapefile was empty");
                retained = cursor.current();
                require(!cursor.advance(), "point shapefile had an extra record");
            } finally {
                cursor.close();
                cursor.close();
                require(cursor.isClosed(), "shapefile cursor did not close");
            }
        } finally {
            source.close();
            source.close();
            require(source.isClosed(), "shapefile source did not close");
        }
        PointGeometry point = (PointGeometry) retained.geometry();
        require(retained.id().equals("record:1"), "unexpected feature id");
        require(point.coordinate().equals(new Coordinate(0.25, 0.75)), "unexpected point");
    }

    private static void testMalformedShapefile(Path directory) throws IOException {
        Path malformed = directory.resolve("truncated.shp");
        Files.write(malformed, new byte[99]);
        try {
            Shapefiles.open(
                    new SourceIdentity("malformed", "Malformed"),
                    malformed,
                    ShapefileOpenOptions.defaults());
            throw new IllegalStateException("malformed shapefile was accepted");
        } catch (SourceException expected) {
            var diagnostic = expected.terminal();
            require(diagnostic.code().equals("SHAPEFILE_HEADER_INVALID"), "unexpected SHP code");
            require(
                    diagnostic.location().orElseThrow().component().orElseThrow().equals("shp"),
                    "unexpected SHP component");
            require(
                    diagnostic.location().orElseThrow().byteOffset().orElseThrow() == 0,
                    "unexpected SHP offset");
            require(
                    diagnostic.context()
                            .equals(
                                    Map.of(
                                            "field", "headerSize",
                                            "expectedBytes", "100",
                                            "actualBytes", "99")),
                    "unexpected SHP context");
        }
    }

    private static void testImages(Path directory, io.github.mundanej.map.api.EncodedRasterDecoderRegistry decoders)
            throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff204080);
        image.setRGB(1, 0, 0xffc03020);
        image.setRGB(0, 1, 0xff20c040);
        image.setRGB(1, 1, 0xffe0d030);
        Path png = directory.resolve("sample.png");
        Path jpeg = directory.resolve("sample.jpg");
        require(ImageIO.write(image, "png", png.toFile()), "PNG writer unavailable");
        BufferedImage jpegImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) jpegImage.setRGB(x, y, 0xff406080);
        }
        require(ImageIO.write(jpegImage, "jpeg", jpeg.toFile()), "JPEG writer unavailable");
        readImage(png, decoders, true);
        readImage(jpeg, decoders, false);
    }

    private static void readImage(
            Path path,
            io.github.mundanej.map.api.EncodedRasterDecoderRegistry decoders,
            boolean exact)
            throws Exception {
        var source =
                RasterImages.open(
                        path,
                        new SourceIdentity("image-" + path.getFileName(), "Consumer image"),
                        ImageOpenOptions.defaults(),
                        decoders);
        var read =
                source.read(
                        new RasterRequest(new RasterWindow(0, 0, 2, 2), 2, 2, Optional.empty()),
                        CancellationToken.none());
        int sample = read.pixels().rgbaAt(0, 0);
        source.close();
        source.close();
        require(source.isClosed(), "image source did not close");
        require(read.pixels().rgbaAt(0, 0) == sample, "retained raster value changed");
        int red = (sample >>> 24) & 0xff;
        int green = (sample >>> 16) & 0xff;
        int blue = (sample >>> 8) & 0xff;
        if (exact) {
            require(red == 0x20 && green == 0x40 && blue == 0x80, "PNG sample changed");
        } else {
            require(Math.abs(red - 0x40) <= 16
                            && Math.abs(green - 0x60) <= 16
                            && Math.abs(blue - 0x80) <= 16,
                    "JPEG sample outside tolerance");
        }
    }

    private static byte[] pointShp(double x, double y) {
        ByteBuffer bytes = ByteBuffer.allocate(128);
        header(bytes, 64, x, y);
        bytes.order(ByteOrder.BIG_ENDIAN).position(100).putInt(1).putInt(10);
        bytes.order(ByteOrder.LITTLE_ENDIAN).putInt(1).putDouble(x).putDouble(y);
        return bytes.array();
    }

    private static byte[] pointShx(double x, double y) {
        ByteBuffer bytes = ByteBuffer.allocate(108);
        header(bytes, 54, x, y);
        bytes.order(ByteOrder.BIG_ENDIAN).position(100).putInt(50).putInt(10);
        return bytes.array();
    }

    private static void header(ByteBuffer bytes, int words, double x, double y) {
        bytes.order(ByteOrder.BIG_ENDIAN).putInt(0, 9994).putInt(24, words);
        bytes.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(28, 1000)
                .putInt(32, 1)
                .putDouble(36, x)
                .putDouble(44, y)
                .putDouble(52, x)
                .putDouble(60, y);
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException failure) {
            throw new IllegalStateException("consumer cleanup failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
