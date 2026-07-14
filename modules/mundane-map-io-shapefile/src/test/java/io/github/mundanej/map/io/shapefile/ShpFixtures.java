package io.github.mundanej.map.io.shapefile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ShpFixtures {
    private ShpFixtures() {}

    static byte[] file(
            int type, double minX, double minY, double maxX, double maxY, byte[]... contents) {
        int total = 100;
        for (byte[] c : contents) {
            total += 8 + c.length;
        }
        ByteBuffer out = ByteBuffer.allocate(total);
        out.order(ByteOrder.BIG_ENDIAN).putInt(9994);
        for (int i = 0; i < 5; i++) {
            out.putInt(0);
        }
        out.putInt(total / 2);
        out.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1000)
                .putInt(type)
                .putDouble(minX)
                .putDouble(minY)
                .putDouble(maxX)
                .putDouble(maxY);
        for (int i = 0; i < 4; i++) {
            out.putLong(0x7ff8000000000000L);
        }
        int ordinal = 1;
        for (byte[] c : contents) {
            out.order(ByteOrder.BIG_ENDIAN).putInt(ordinal++).putInt(c.length / 2);
            out.put(c);
        }
        return out.array();
    }

    static byte[] nullShape() {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array();
    }

    static byte[] point(double x, double y) {
        return ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1)
                .putDouble(x)
                .putDouble(y)
                .array();
    }

    static byte[] multipoint(double... xy) {
        int n = xy.length / 2;
        double minX = Double.POSITIVE_INFINITY,
                minY = Double.POSITIVE_INFINITY,
                maxX = Double.NEGATIVE_INFINITY,
                maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < xy.length; i += 2) {
            minX = Math.min(minX, xy[i]);
            minY = Math.min(minY, xy[i + 1]);
            maxX = Math.max(maxX, xy[i]);
            maxY = Math.max(maxY, xy[i + 1]);
        }
        ByteBuffer b = ByteBuffer.allocate(40 + xy.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(8).putDouble(minX).putDouble(minY).putDouble(maxX).putDouble(maxY).putInt(n);
        for (double v : xy) {
            b.putDouble(v);
        }
        return b.array();
    }

    static byte[] typed(int type, int bytes) {
        ByteBuffer b = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(type);
        return b.array();
    }

    static ByteBuffer bigEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    }

    static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
