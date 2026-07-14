package io.github.mundanej.map.io.shapefile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-only byte-authored datasets and bounded mutation primitives. */
final class ShapefileAdversarialFixtures {
    private static final DbfFixtures.Field[] TEXT_FIELDS = {DbfFixtures.field("name", 'C', 5, 0)};

    private ShapefileAdversarialFixtures() {}

    static Map<String, byte[]> pointDataset() {
        byte[] point = ShpFixtures.point(1, 2);
        return components(
                ShpFixtures.file(1, 1, 2, 1, 2, point),
                ShxFixtures.file(1, 1, 2, 1, 2, point),
                DbfFixtures.dbf(0x03, 0, TEXT_FIELDS, DbfFixtures.row(' ', TEXT_FIELDS, "alpha")),
                "UTF-8".getBytes(StandardCharsets.US_ASCII),
                PrjFixtures.utf8(PrjFixtures.EPSG_4326));
    }

    static Map<String, byte[]> shapeDataset(int variant) {
        byte[] content;
        int type;
        double minX = 0;
        double minY = 0;
        double maxX = 2;
        double maxY = 2;
        switch (Math.floorMod(variant, 5)) {
            case 0 -> {
                type = 0;
                minX = 0;
                minY = 0;
                maxX = 0;
                maxY = 0;
                content = ShpFixtures.nullShape();
            }
            case 1 -> {
                type = 1;
                content = ShpFixtures.point(1, 2);
                minX = 1;
                minY = 2;
                maxX = 1;
            }
            case 2 -> {
                type = 8;
                content = ShpFixtures.multipoint(0, 0, 2, 2);
            }
            case 3 -> {
                type = 3;
                content = ShpFixtures.polyline(new int[] {0}, 0, 0, 1, 1, 2, 2);
            }
            default -> {
                type = 5;
                content = ShpFixtures.polygon(new int[] {0}, 0, 0, 0, 2, 2, 2, 2, 0, 0, 0);
            }
        }
        byte[] shp = ShpFixtures.file(type, minX, minY, maxX, maxY, content);
        byte[] shx = ShxFixtures.file(type, minX, minY, maxX, maxY, content);
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("shp", shp);
        result.put("shx", shx);
        return result;
    }

    static Map<String, byte[]> unsupportedDbfDataset() {
        Map<String, byte[]> result = pointDataset();
        DbfFixtures.Field[] fields = {DbfFixtures.field("memo", 'M', 4, 0)};
        result.put("dbf", DbfFixtures.dbf(0x03, 0, fields, DbfFixtures.row(' ', fields, "0001")));
        return result;
    }

    static Map<String, byte[]> invalidDbfValueDataset() {
        Map<String, byte[]> result = pointDataset();
        DbfFixtures.Field[] fields = {DbfFixtures.field("value", 'N', 4, 0)};
        result.put("dbf", DbfFixtures.dbf(0x03, 0, fields, DbfFixtures.row(' ', fields, "12x")));
        return result;
    }

    static Map<String, byte[]> twoRowDbfDataset() {
        Map<String, byte[]> result = pointDataset();
        result.put(
                "dbf",
                DbfFixtures.dbf(
                        0x03,
                        0,
                        TEXT_FIELDS,
                        DbfFixtures.row(' ', TEXT_FIELDS, "alpha"),
                        DbfFixtures.row(' ', TEXT_FIELDS, "bravo")));
        return result;
    }

    static byte[] copy(byte[] input) {
        return Arrays.copyOf(input, input.length);
    }

    static byte[] truncate(byte[] input, int length) {
        return Arrays.copyOf(input, Math.max(0, Math.min(input.length, length)));
    }

    static byte[] append(byte[] input, int count, byte value) {
        int appended = Math.max(1, Math.min(16, count));
        byte[] result = Arrays.copyOf(input, input.length + appended);
        Arrays.fill(result, input.length, result.length, value);
        return result;
    }

    static void putInt(byte[] target, int offset, int value, ByteOrder order) {
        if (target.length < Integer.BYTES) {
            return;
        }
        int bounded = Math.min(Math.max(0, offset), target.length - Integer.BYTES);
        ByteBuffer.wrap(target).order(order).putInt(bounded, value);
    }

    static void putDouble(byte[] target, int offset, double value) {
        if (target.length < Double.BYTES) {
            return;
        }
        int bounded = Math.min(Math.max(0, offset), target.length - Double.BYTES);
        ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN).putDouble(bounded, value);
    }

    static void reverseInt(byte[] target, int offset) {
        if (target.length < Integer.BYTES) {
            return;
        }
        int bounded = Math.min(Math.max(0, offset), target.length - Integer.BYTES);
        byte first = target[bounded];
        byte second = target[bounded + 1];
        target[bounded] = target[bounded + 3];
        target[bounded + 1] = target[bounded + 2];
        target[bounded + 2] = second;
        target[bounded + 3] = first;
    }

    private static Map<String, byte[]> components(
            byte[] shp, byte[] shx, byte[] dbf, byte[] cpg, byte[] prj) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("shp", shp);
        result.put("shx", shx);
        result.put("dbf", dbf);
        result.put("cpg", cpg);
        result.put("prj", prj);
        return result;
    }
}
