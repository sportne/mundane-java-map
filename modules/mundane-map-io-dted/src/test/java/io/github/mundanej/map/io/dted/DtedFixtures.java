package io.github.mundanej.map.io.dted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Independent test-only writer for the supported one-degree profiles. */
final class DtedFixtures {
    static final int HEADER_BYTES = 3_428;

    private DtedFixtures() {}

    static Fixture write(Path path, int level) throws IOException {
        Grid grid = grid(level, 80);
        byte[] headers = headers(level, 80, level == 2, false, 0);
        int columns = grid.columns;
        int rows = grid.rows;
        try (FileChannel output =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            write(output, headers);
            int recordBytes = 12 + 2 * rows;
            ByteBuffer record = ByteBuffer.allocate(recordBytes).order(ByteOrder.BIG_ENDIAN);
            for (int profile = 0; profile < columns; profile++) {
                record.clear();
                record.put((byte) 0xaa);
                record.put((byte) ((profile >>> 16) & 0xff));
                record.put((byte) ((profile >>> 8) & 0xff));
                record.put((byte) (profile & 0xff));
                record.putShort((short) profile);
                record.putShort((short) 0);
                for (int sample = 0; sample < rows; sample++) {
                    record.putShort((short) word(level, profile, sample, rows, columns));
                }
                long checksum = 0;
                for (int index = 0; index < record.position(); index++) {
                    checksum += record.get(index) & 0xffL;
                }
                record.putInt((int) checksum);
                record.flip();
                while (record.hasRemaining()) {
                    output.write(record);
                }
            }
        }
        return new Fixture(
                level,
                columns,
                rows,
                HEADER_BYTES + (long) columns * (12L + 2L * rows),
                digest(path));
    }

    static byte[] headers(
            int level,
            int southLatitudeDegrees,
            boolean partial,
            boolean uhlMultipleAccuracy,
            int accSubregions) {
        Grid grid = grid(level, southLatitudeDegrees);
        byte[] uhl = spaces(80);
        put(uhl, 0, "UHL1");
        put(uhl, 4, "0000000E");
        put(uhl, 12, coordinate(southLatitudeDegrees, true, 3, false));
        put(uhl, 20, four(grid.longitudeInterval));
        put(uhl, 24, four(grid.latitudeInterval));
        put(uhl, 28, "NA  ");
        put(uhl, 32, "U  ");
        put(uhl, 35, "MUNDANE-TEST");
        put(uhl, 47, four(grid.columns));
        put(uhl, 51, four(grid.rows));
        uhl[55] = uhlMultipleAccuracy ? (byte) '1' : (byte) '0';

        byte[] dsi = spaces(648);
        put(dsi, 0, "DSI");
        dsi[3] = 'U';
        put(dsi, 59, "DTED" + level);
        put(dsi, 64, "MUNDANE-TEST   ");
        put(dsi, 87, "01");
        dsi[89] = 'A';
        put(dsi, 90, "0000");
        put(dsi, 94, "0000");
        put(dsi, 98, "0000");
        put(dsi, 102, "MUN     ");
        put(dsi, 126, "PRF89020B");
        put(dsi, 135, "00");
        put(dsi, 137, "0000");
        put(dsi, 141, "MSL");
        put(dsi, 144, "WGS84");
        put(dsi, 149, "TESTGEN   ");
        put(dsi, 159, "0000");
        put(dsi, 185, coordinate(southLatitudeDegrees, true, 2, true));
        put(dsi, 194, "0000000.0E");
        put(dsi, 204, coordinate(southLatitudeDegrees, true, 2, false));
        put(dsi, 211, "0000000E");
        put(dsi, 219, coordinate(southLatitudeDegrees + 1, true, 2, false));
        put(dsi, 226, "0000000E");
        put(dsi, 234, coordinate(southLatitudeDegrees + 1, true, 2, false));
        put(dsi, 241, "0010000E");
        put(dsi, 249, coordinate(southLatitudeDegrees, true, 2, false));
        put(dsi, 256, "0010000E");
        put(dsi, 264, "0000000.0");
        put(dsi, 273, four(grid.latitudeInterval));
        put(dsi, 277, four(grid.longitudeInterval));
        put(dsi, 281, four(grid.rows));
        put(dsi, 285, four(grid.columns));
        put(dsi, 289, partial ? "25" : "00");

        byte[] acc = spaces(2_700);
        put(acc, 0, "ACC");
        put(acc, 3, "NA  NA  NA  NA  ");
        put(acc, 55, String.format(java.util.Locale.ROOT, "%02d", accSubregions));

        ByteBuffer result = ByteBuffer.allocate(HEADER_BYTES);
        result.put(uhl).put(dsi).put(acc);
        return result.array();
    }

    static int value(int profile, int fileSample, int rows) {
        if (profile == 0 && fileSample == 0) {
            return 123;
        }
        if (profile == 0 && fileSample == rows - 1) {
            return -456;
        }
        if (profile == 1 && fileSample == 1) {
            return 0;
        }
        return (profile * 31 + fileSample * 7) % 2_001 - 1_000;
    }

    static boolean isVoid(int level, int profile, int fileSample, int rows, int columns) {
        return level == 2
                && ((profile == 0 && fileSample == 0)
                        || (profile == columns / 2 && fileSample == rows / 2)
                        || (profile == columns - 1 && fileSample == rows - 1));
    }

    private static int word(int level, int profile, int sample, int rows, int columns) {
        if (isVoid(level, profile, sample, rows, columns)) {
            return 0xffff;
        }
        if (profile == 1 && sample == 1) {
            return 0x8000;
        }
        int value = value(profile, sample, rows);
        return value < 0 ? 0x8000 | -value : value;
    }

    private static Grid grid(int level, int southLatitudeDegrees) {
        int[] rows = {121, 1_201, 3_601};
        int[] latitudeIntervals = {300, 30, 10};
        int[][] longitudeIntervals = {
            {300, 600, 900, 1_200, 1_800},
            {30, 60, 90, 120, 180},
            {10, 20, 30, 40, 60}
        };
        int nearest = Math.min(Math.abs(southLatitudeDegrees), Math.abs(southLatitudeDegrees + 1));
        int zone = nearest < 50 ? 0 : nearest < 70 ? 1 : nearest < 75 ? 2 : nearest < 80 ? 3 : 4;
        int longitudeInterval = longitudeIntervals[level][zone];
        return new Grid(
                36_000 / longitudeInterval + 1,
                rows[level],
                longitudeInterval,
                latitudeIntervals[level]);
    }

    private static String coordinate(
            int degrees, boolean latitude, int degreeDigits, boolean decimal) {
        char hemisphere = latitude ? (degrees < 0 ? 'S' : 'N') : (degrees < 0 ? 'W' : 'E');
        return String.format(
                java.util.Locale.ROOT,
                "%0" + degreeDigits + "d0000%s%c",
                Math.abs(degrees),
                decimal ? ".0" : "",
                hemisphere);
    }

    private static byte[] spaces(int length) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) ' ');
        return bytes;
    }

    private static void put(byte[] target, int offset, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, encoded.length);
    }

    private static String four(int value) {
        if (value < 0 || value > 9_999) {
            throw new IllegalArgumentException("four-digit value");
        }
        return String.format(java.util.Locale.ROOT, "%04d", value);
    }

    private static void write(FileChannel output, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            output.write(buffer);
        }
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = java.nio.file.Files.newInputStream(path)) {
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    record Fixture(int level, int columns, int rows, long bytes, String sha256) {}

    private record Grid(int columns, int rows, int longitudeInterval, int latitudeInterval) {}
}
