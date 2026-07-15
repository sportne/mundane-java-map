package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.io.image.ImageCachePolicy;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Test-owned on-disk fixtures reconstructed without the performance fixture implementations. */
final class ReferenceWorkspace implements AutoCloseable {
    private static final String RESOURCE_ROOT =
            "/io/github/mundanej/map/performance/fixture/raster-1024x768-v1/";
    private static final String PRJ =
            "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\","
                    + "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\","
                    + "SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],"
                    + "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],"
                    + "PROJECTION[\"Mercator_Auxiliary_Sphere\"],PARAMETER[\"False_Easting\",0],"
                    + "PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",0],"
                    + "PARAMETER[\"Standard_Parallel_1\",0],"
                    + "PARAMETER[\"Auxiliary_Sphere_Type\",0],UNIT[\"Meter\",1]]";

    private final Path root;
    private final Path shapefile;
    private final Path png;
    private final Path jpeg;
    private final int shapefileRecords;

    private ReferenceWorkspace(
            Path root, Path shapefile, Path png, Path jpeg, int shapefileRecords) {
        this.root = root;
        this.shapefile = shapefile;
        this.png = png;
        this.jpeg = jpeg;
        this.shapefileRecords = shapefileRecords;
    }

    static ReferenceWorkspace create(Path root, EvidenceConfiguration.Profile profile)
            throws IOException {
        int columns = profile == EvidenceConfiguration.Profile.BASELINE ? 500 : 50;
        int rows = profile == EvidenceConfiguration.Profile.BASELINE ? 100 : 10;
        Path shapeDirectory = Files.createDirectories(root.resolve("reference-shape"));
        Path shape = shapeDirectory.resolve("grid.shp");
        writeShp(shape, columns, rows);
        writeShx(shapeDirectory.resolve("grid.shx"), columns, rows);
        writeDbf(shapeDirectory.resolve("grid.dbf"), columns, rows);
        Files.writeString(shapeDirectory.resolve("grid.cpg"), "UTF-8\n", StandardCharsets.US_ASCII);
        Files.writeString(shapeDirectory.resolve("grid.prj"), PRJ, StandardCharsets.US_ASCII);

        Path rasterDirectory = Files.createDirectories(root.resolve("reference-raster"));
        Path png = rasterDirectory.resolve("evidence.png");
        Path jpeg = rasterDirectory.resolve("evidence.jpg");
        copyResource("evidence.png", png);
        copyResource("evidence.jpg", jpeg);
        copyResource("evidence.pgw", rasterDirectory.resolve("evidence.pgw"));
        copyResource("evidence.jgw", rasterDirectory.resolve("evidence.jgw"));
        return new ReferenceWorkspace(root, shape, png, jpeg, columns * rows);
    }

    Path shapefile() {
        return shapefile;
    }

    int shapefileRecords() {
        return shapefileRecords;
    }

    RasterSource openPng(ImageCachePolicy policy, boolean placed) {
        return openRaster(png, "reference-png", policy, placed);
    }

    RasterSource openJpeg(ImageCachePolicy policy, boolean placed) {
        return openRaster(jpeg, "reference-jpeg", policy, placed);
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static RasterSource openRaster(
            Path path, String id, ImageCachePolicy policy, boolean placed) {
        ImageOpenOptions options =
                ImageOpenOptions.defaults()
                        .withCachePolicy(policy)
                        .withPlacement(
                                placed
                                        ? ImagePlacement.worldFile(
                                                CrsMetadata.recognized(
                                                        CrsDefinitions.EPSG_3857,
                                                        Optional.of("EPSG:3857"),
                                                        Optional.empty()))
                                        : ImagePlacement.unplaced());
        return RasterImages.open(
                path, new SourceIdentity(id, id), options, AwtRasterDecoders.level1());
    }

    private static void copyResource(String name, Path target) throws IOException {
        try (InputStream input =
                ReferenceWorkspace.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (input == null) {
                throw new IOException("Missing reference raster resource: " + name);
            }
            Files.copy(input, target);
        }
    }

    private static void writeShp(Path path, int columns, int rows) throws IOException {
        int count = Math.multiplyExact(columns, rows);
        ByteBuffer buffer = ByteBuffer.allocate(Math.addExact(100, Math.multiplyExact(count, 28)));
        writeHeader(buffer, buffer.capacity(), columns, rows);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            buffer.order(ByteOrder.BIG_ENDIAN).putInt(ordinal + 1).putInt(10);
            buffer.order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(1)
                    .putDouble((ordinal % columns) * 1_000.0)
                    .putDouble(Math.floorDiv(ordinal, columns) * 1_000.0);
        }
        Files.write(path, buffer.array());
    }

    private static void writeShx(Path path, int columns, int rows) throws IOException {
        int count = Math.multiplyExact(columns, rows);
        ByteBuffer buffer = ByteBuffer.allocate(Math.addExact(100, Math.multiplyExact(count, 8)));
        writeHeader(buffer, buffer.capacity(), columns, rows);
        int offset = 50;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            buffer.order(ByteOrder.BIG_ENDIAN).putInt(offset).putInt(10);
            offset = Math.addExact(offset, 14);
        }
        Files.write(path, buffer.array());
    }

    private static void writeHeader(ByteBuffer buffer, int bytes, int columns, int rows) {
        buffer.order(ByteOrder.BIG_ENDIAN).putInt(9994);
        for (int index = 0; index < 5; index++) {
            buffer.putInt(0);
        }
        buffer.putInt(bytes / 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(1);
        buffer.putDouble(0.0).putDouble(0.0);
        buffer.putDouble((columns - 1) * 1_000.0).putDouble((rows - 1) * 1_000.0);
        buffer.putDouble(0.0).putDouble(0.0).putDouble(0.0).putDouble(0.0);
    }

    private static void writeDbf(Path path, int columns, int rows) throws IOException {
        int count = Math.multiplyExact(columns, rows);
        int headerLength = 97;
        int recordLength = 19;
        ByteBuffer buffer =
                ByteBuffer.allocate(
                                Math.addExact(
                                        headerLength, Math.multiplyExact(count, recordLength)))
                        .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x03).put((byte) 100).put((byte) 1).put((byte) 1);
        buffer.putInt(count).putShort((short) headerLength).putShort((short) recordLength);
        while (buffer.position() < 32) {
            buffer.put((byte) 0);
        }
        writeField(buffer, "ID", 'N', 10, 0);
        writeField(buffer, "GROUP", 'C', 8, 0);
        buffer.put((byte) 0x0d);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            buffer.put((byte) ' ');
            putAscii(buffer, String.format(java.util.Locale.ROOT, "%10d", ordinal + 1), 10);
            putAscii(
                    buffer,
                    String.format(
                            java.util.Locale.ROOT,
                            "group-%02d",
                            Math.floorDiv(ordinal, columns) % 20),
                    8);
        }
        Files.write(path, buffer.array());
    }

    private static void writeField(
            ByteBuffer target, String name, char type, int length, int decimals) {
        byte[] descriptor = new byte[32];
        byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, descriptor, 0, encoded.length);
        descriptor[11] = (byte) type;
        descriptor[16] = (byte) length;
        descriptor[17] = (byte) decimals;
        target.put(descriptor);
    }

    private static void putAscii(ByteBuffer target, String text, int length) {
        byte[] encoded = text.getBytes(StandardCharsets.US_ASCII);
        if (encoded.length != length) {
            throw new IllegalArgumentException("Unexpected reference DBF field width");
        }
        target.put(encoded);
    }
}
