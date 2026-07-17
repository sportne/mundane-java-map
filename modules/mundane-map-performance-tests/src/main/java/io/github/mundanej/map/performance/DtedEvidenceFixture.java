package io.github.mundanej.map.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Independent support-only author of complete zone-I DTED evidence fixtures. */
final class DtedEvidenceFixture {
    static final int HEADER_BYTES = 3_428;
    static final Fixture SMOKE =
            new Fixture(
                    "dted-zone-i-l0-smoke-v1",
                    0,
                    121,
                    34_162L,
                    "99bd897d6d4af55ffe1092be7a3ee8051d1fbfff1613d6f008fbfb447c46fad5");
    static final Fixture MAXIMUM =
            new Fixture(
                    "dted-zone-i-l2-v1",
                    2,
                    3_601,
                    25_981_042L,
                    "2e1e3adcb1f65d41d93ad5d31c63211522ca830bd8f2716415070e3ae8b72330");

    private DtedEvidenceFixture() {}

    static Fixture write(Path path, Fixture requested) throws IOException {
        Path parent = java.util.Objects.requireNonNull(path, "path").getParent();
        Files.createDirectories(java.util.Objects.requireNonNull(parent, "path parent"));
        Files.deleteIfExists(path);
        int count = requested.posts();
        int interval = requested.level() == 0 ? 300 : 10;
        try (FileChannel output =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writeFully(output, headers(requested.level(), count, interval));
            ByteBuffer record = ByteBuffer.allocate(12 + 2 * count).order(ByteOrder.BIG_ENDIAN);
            for (int column = 0; column < count; column++) {
                record.clear();
                record.put((byte) 0xaa);
                record.put((byte) ((column >>> 16) & 0xff));
                record.put((byte) ((column >>> 8) & 0xff));
                record.put((byte) (column & 0xff));
                record.putShort((short) column);
                record.putShort((short) 0);
                for (int fileSample = 0; fileSample < count; fileSample++) {
                    int row = count - 1 - fileSample;
                    record.putShort((short) value(column, row, count));
                }
                long checksum = 0;
                for (int index = 0; index < record.position(); index++) {
                    checksum += record.get(index) & 0xffL;
                }
                record.putInt((int) checksum).flip();
                while (record.hasRemaining()) {
                    output.write(record);
                }
            }
        }
        long length = Files.size(path);
        if (length != requested.bytes()) {
            throw new IllegalStateException("Generated DTED fixture length changed");
        }
        String digest = sha256(path);
        if (!digest.equals(requested.sha256())) {
            throw new IllegalStateException("Generated DTED fixture hash changed");
        }
        return new Fixture(requested.id(), requested.level(), requested.posts(), length, digest);
    }

    static int value(int column, int row, int posts) {
        return 1_200
                + Math.floorDiv(1_200 * column, posts - 1)
                - Math.floorDiv(800 * row, posts - 1);
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65_536];
                for (int count; (count = input.read(buffer)) >= 0; ) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] headers(int level, int posts, int interval) {
        byte[] uhl = spaces(80);
        put(uhl, 0, "UHL1");
        put(uhl, 4, "0000000E");
        put(uhl, 12, "0000000N");
        put(uhl, 20, four(interval));
        put(uhl, 24, four(interval));
        put(uhl, 28, "NA  ");
        put(uhl, 32, "U  ");
        put(uhl, 35, "MUNDANE-PERF");
        put(uhl, 47, four(posts));
        put(uhl, 51, four(posts));
        uhl[55] = '0';

        byte[] dsi = spaces(648);
        put(dsi, 0, "DSI");
        dsi[3] = 'U';
        put(dsi, 59, "DTED" + level);
        put(dsi, 64, "MUNDANE-PERF   ");
        put(dsi, 87, "01");
        dsi[89] = 'A';
        put(dsi, 90, "2607");
        put(dsi, 94, "2607");
        put(dsi, 98, "0000");
        put(dsi, 102, "MUN     ");
        put(dsi, 126, "PRF89020B");
        put(dsi, 135, "00");
        put(dsi, 137, "8902");
        put(dsi, 141, "MSL");
        put(dsi, 144, "WGS84");
        put(dsi, 149, "PERFGEN   ");
        put(dsi, 159, "2607");
        put(dsi, 185, "000000.0N");
        put(dsi, 194, "0000000.0E");
        put(dsi, 204, "000000N");
        put(dsi, 211, "0000000E");
        put(dsi, 219, "010000N");
        put(dsi, 226, "0000000E");
        put(dsi, 234, "010000N");
        put(dsi, 241, "0010000E");
        put(dsi, 249, "000000N");
        put(dsi, 256, "0010000E");
        put(dsi, 264, "0000000.0");
        put(dsi, 273, four(interval));
        put(dsi, 277, four(interval));
        put(dsi, 281, four(posts));
        put(dsi, 285, four(posts));
        put(dsi, 289, "00");

        byte[] acc = spaces(2_700);
        put(acc, 0, "ACC");
        put(acc, 3, "NA  NA  NA  NA  ");
        put(acc, 55, "00");
        return ByteBuffer.allocate(HEADER_BYTES).put(uhl).put(dsi).put(acc).array();
    }

    private static byte[] spaces(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
        return bytes;
    }

    private static void put(byte[] target, int offset, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private static String four(int value) {
        return String.format(Locale.ROOT, "%04d", value);
    }

    private static void writeFully(FileChannel output, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            output.write(buffer);
        }
    }

    record Fixture(String id, int level, int posts, long bytes, String sha256) {
        long samples() {
            return Math.multiplyExact((long) posts, posts);
        }
    }
}
