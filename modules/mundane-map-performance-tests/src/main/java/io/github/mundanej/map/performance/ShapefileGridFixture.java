package io.github.mundanej.map.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ShapefileGridFixture implements AutoCloseable {
    private static final String PRJ =
            "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\","
                    + "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\","
                    + "SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],"
                    + "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],"
                    + "PROJECTION[\"Mercator_Auxiliary_Sphere\"],PARAMETER[\"False_Easting\",0],"
                    + "PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",0],"
                    + "PARAMETER[\"Standard_Parallel_1\",0],"
                    + "PARAMETER[\"Auxiliary_Sphere_Type\",0],UNIT[\"Meter\",1]]";

    private final Path directory;
    private final Path shp;
    private final int records;
    private final String digest;

    private ShapefileGridFixture(Path directory, Path shp, int records, String digest) {
        this.directory = directory;
        this.shp = shp;
        this.records = records;
        this.digest = digest;
    }

    static ShapefileGridFixture create(Path parent, EvidenceConfiguration.Profile profile)
            throws IOException {
        int columns = profile == EvidenceConfiguration.Profile.BASELINE ? 500 : 50;
        int rows = profile == EvidenceConfiguration.Profile.BASELINE ? 100 : 10;
        int count = Math.multiplyExact(columns, rows);
        Path directory = Files.createDirectories(parent.resolve("shapefile-grid-v1"));
        Path shp = directory.resolve("grid.shp");
        Path shx = directory.resolve("grid.shx");
        Path dbf = directory.resolve("grid.dbf");
        writeShp(shp, columns, rows);
        writeShx(shx, columns, rows);
        writeDbf(dbf, columns, rows);
        Files.writeString(directory.resolve("grid.cpg"), "UTF-8\n", StandardCharsets.US_ASCII);
        Files.writeString(directory.resolve("grid.prj"), PRJ, StandardCharsets.US_ASCII);
        String digest = sha256(shp) + ':' + sha256(shx) + ':' + sha256(dbf);
        if (profile == EvidenceConfiguration.Profile.BASELINE) {
            require(
                    shp,
                    1_400_100,
                    "4118faa3e09bb3003139195d52c0f50f47235e700e17751b277e5a5ca619c687");
            require(
                    shx,
                    400_100,
                    "29e5ddc9ac75a1a588c165ed5b374fd2fc34b2cb0accb5df0ece2c6eb987239a");
            require(
                    dbf,
                    950_097,
                    "a745fcd1792fc26f1ba88a0bc4677f881e0e599f98970164122fbe7aec06cdad");
            require(
                    directory.resolve("grid.cpg"),
                    6,
                    "146d6789ffe033a5297c1ad046e6a62ee35319b86b021444f05b6ea2aa8a1f4a");
            require(
                    directory.resolve("grid.prj"),
                    413,
                    "4f01f36bf95963fb9174c50d396f1201b266ef7fa43304e718a7cb324ad71394");
        }
        return new ShapefileGridFixture(directory, shp, count, digest);
    }

    Path shp() {
        return shp;
    }

    int records() {
        return records;
    }

    String digest() {
        return digest;
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
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
                    String.format(java.util.Locale.ROOT, "group-%02d", (ordinal / columns) % 20),
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
            throw new IllegalArgumentException("Unexpected DBF field width");
        }
        target.put(encoded);
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void require(Path path, long length, String digest) throws IOException {
        if (Files.size(path) != length || !sha256(path).equals(digest)) {
            throw new IOException("Generated shapefile fixture changed: " + path.getFileName());
        }
    }
}
