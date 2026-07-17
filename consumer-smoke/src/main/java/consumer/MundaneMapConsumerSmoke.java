package consumer;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.ElevationSource;
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
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
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

/** Public-artifact-only consumer smoke for the published map modules. */
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
            testDted(directory);
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

    private static void testDted(Path directory) throws IOException {
        Path path = directory.resolve("consumer.dt0");
        byte[] fixture = levelZeroDted();
        verifyLevelZeroDtedHeader(fixture);
        Files.write(path, fixture);
        ElevationSource source =
                DtedFiles.open(
                        new SourceIdentity("consumer-dted", "Consumer DTED"),
                        path,
                        DtedOpenOptions.defaults());
        var metadata = source.metadata();
        require(metadata.columnCount() == 21, "unexpected DTED columns");
        require(metadata.rowCount() == 121, "unexpected DTED rows");
        require(metadata.sampleBounds().equals(new io.github.mundanej.map.api.Envelope(0, 80, 1, 81)),
                "unexpected DTED bounds");
        require(source.sample(0, 0).orElseThrow() == 120, "unexpected DTED north sample");
        require(source.sample(0, 120).orElseThrow() == 0, "unexpected DTED south sample");
        require(source.openingDiagnostics().entries().isEmpty(), "unexpected DTED diagnostics");
        source.close();
        source.close();
        require(source.isClosed(), "DTED source did not close");
        require(source.metadata().equals(metadata), "DTED metadata changed after close");
    }

    private static byte[] levelZeroDted() {
        byte[] bytes = new byte[8_762];
        java.util.Arrays.fill(bytes, (byte) ' ');
        putAscii(bytes, 0, "UHL1");
        putAscii(bytes, 4, "0000000E0800000N18000300NA  U  MUNDANE-CONS002101210");
        putAscii(bytes, 80, "DSI");
        bytes[83] = 'U';
        putAscii(bytes, 139, "DTED0");
        putAscii(bytes, 144, "MUNDANE-CONS   ");
        putAscii(bytes, 167, "01");
        bytes[169] = 'A';
        putAscii(bytes, 170, "0000");
        putAscii(bytes, 174, "0000");
        putAscii(bytes, 178, "0000");
        putAscii(bytes, 182, "MUN     ");
        putAscii(bytes, 206, "PRF89020B");
        putAscii(bytes, 215, "00");
        putAscii(bytes, 217, "0000");
        putAscii(bytes, 221, "MSLWGS84");
        putAscii(bytes, 229, "CONSUMER  ");
        putAscii(bytes, 239, "0000");
        putAscii(bytes, 265, "800000.0N");
        putAscii(bytes, 274, "0000000.0E");
        putAscii(bytes, 284, "800000N");
        putAscii(bytes, 291, "0000000E");
        putAscii(bytes, 299, "810000N");
        putAscii(bytes, 306, "0000000E");
        putAscii(bytes, 314, "810000N");
        putAscii(bytes, 321, "0010000E");
        putAscii(bytes, 329, "800000N");
        putAscii(bytes, 336, "0010000E");
        putAscii(bytes, 344, "0000000.0");
        putAscii(bytes, 353, "0300");
        putAscii(bytes, 357, "1800");
        putAscii(bytes, 361, "0121");
        putAscii(bytes, 365, "0021");
        putAscii(bytes, 369, "00");
        putAscii(bytes, 728, "ACC");
        putAscii(bytes, 731, "NA  NA  NA  NA  ");
        putAscii(bytes, 783, "00");
        int position = 3_428;
        for (int profile = 0; profile < 21; profile++) {
            ByteBuffer record = ByteBuffer.wrap(bytes, position, 254).slice().order(ByteOrder.BIG_ENDIAN);
            record.put((byte) 0xaa);
            record.put((byte) 0).put((byte) 0).put((byte) profile);
            record.putShort((short) profile).putShort((short) 0);
            for (int sample = 0; sample < 121; sample++) {
                record.putShort((short) (profile * 10 + sample));
            }
            long checksum = 0;
            for (int index = 0; index < 250; index++) checksum += bytes[position + index] & 0xffL;
            record.putInt((int) checksum);
            position += 254;
        }
        return bytes;
    }

    private static void verifyLevelZeroDtedHeader(byte[] bytes) {
        require(bytes.length == 8_762, "independent DTED fixture length changed");
        requireAscii(bytes, 0, "UHL1", "UHL sentinel");
        requireAscii(bytes, 80, "DSIU", "DSI sentinel/classification");
        requireAscii(bytes, 139, "DTED0", "DTED series");
        requireAscii(bytes, 167, "01A000000000000", "DSI edition/date profile");
        requireAscii(bytes, 182, "MUN     ", "DSI producer");
        requireAscii(
                bytes,
                206,
                "PRF89020B000000MSLWGS84CONSUMER  0000",
                "DSI product profile");
        requireAscii(
                bytes,
                265,
                "800000.0N0000000.0E800000N0000000E"
                        + "810000N0000000E810000N0010000E"
                        + "800000N0010000E0000000.0030018000121002100",
                "DSI grid profile");
        requireAscii(bytes, 728, "ACCNA  NA  NA  NA  ", "ACC profile");
        requireAscii(bytes, 783, "00", "ACC subregion count");
    }

    private static void requireAscii(byte[] bytes, int offset, String expected, String field) {
        String actual =
                new String(
                        bytes,
                        offset,
                        expected.length(),
                        java.nio.charset.StandardCharsets.US_ASCII);
        require(actual.equals(expected), "independent DTED " + field + " changed");
    }

    private static void putAscii(byte[] bytes, int offset, String value) {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, bytes, offset, encoded.length);
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
