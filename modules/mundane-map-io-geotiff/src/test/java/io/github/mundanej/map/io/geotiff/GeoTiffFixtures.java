package io.github.mundanej.map.io.geotiff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class GeoTiffFixtures {
    private static final int ENTRY_COUNT = 13;
    private static final int SCALE_OFFSET = 170;
    private static final int TIE_OFFSET = 194;
    private static final int KEYS_OFFSET = 242;
    private static final int PIXELS_OFFSET = 274;

    private GeoTiffFixtures() {}

    static byte[] areaGray() {
        ByteBuffer bytes = ByteBuffer.allocate(286).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) ENTRY_COUNT);
        entry(bytes, 256, 3, 1, 4);
        entry(bytes, 257, 3, 1, 3);
        entry(bytes, 258, 3, 1, 8);
        entry(bytes, 259, 3, 1, 1);
        entry(bytes, 262, 3, 1, 1);
        entry(bytes, 273, 4, 1, PIXELS_OFFSET);
        entry(bytes, 277, 3, 1, 1);
        entry(bytes, 278, 4, 1, 3);
        entry(bytes, 279, 4, 1, 12);
        entry(bytes, 284, 3, 1, 1);
        entry(bytes, 33550, 12, 3, SCALE_OFFSET);
        entry(bytes, 33922, 12, 6, TIE_OFFSET);
        entry(bytes, 34735, 3, 16, KEYS_OFFSET);
        bytes.putInt(0);
        bytes.position(SCALE_OFFSET).putDouble(1).putDouble(1).putDouble(0);
        bytes.position(TIE_OFFSET)
                .putDouble(0)
                .putDouble(0)
                .putDouble(0)
                .putDouble(10)
                .putDouble(20)
                .putDouble(0);
        bytes.position(KEYS_OFFSET)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 3);
        key(bytes, 1024, 2);
        key(bytes, 1025, 1);
        key(bytes, 2048, 4326);
        bytes.position(PIXELS_OFFSET);
        for (int value = 0; value < 12; value++) {
            bytes.put((byte) (value * 20));
        }
        return bytes.array();
    }

    static byte[] threeStrips() {
        ByteBuffer bytes = ByteBuffer.allocate(310).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) ENTRY_COUNT);
        entry(bytes, 256, 3, 1, 4);
        entry(bytes, 257, 3, 1, 3);
        entry(bytes, 258, 3, 1, 8);
        entry(bytes, 259, 3, 1, 1);
        entry(bytes, 262, 3, 1, 1);
        entry(bytes, 273, 4, 3, 170);
        entry(bytes, 277, 3, 1, 1);
        entry(bytes, 278, 4, 1, 1);
        entry(bytes, 279, 4, 3, 182);
        entry(bytes, 284, 3, 1, 1);
        entry(bytes, 33550, 12, 3, 194);
        entry(bytes, 33922, 12, 6, 218);
        entry(bytes, 34735, 3, 16, 266);
        bytes.putInt(0);
        bytes.position(170).putInt(298).putInt(302).putInt(306);
        bytes.position(182).putInt(4).putInt(4).putInt(4);
        bytes.position(194).putDouble(1).putDouble(1).putDouble(0);
        bytes.position(218)
                .putDouble(0)
                .putDouble(0)
                .putDouble(0)
                .putDouble(10)
                .putDouble(20)
                .putDouble(0);
        bytes.position(266)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 3);
        key(bytes, 1024, 2);
        key(bytes, 1025, 1);
        key(bytes, 2048, 4326);
        bytes.position(298);
        for (int value = 0; value < 12; value++) {
            bytes.put((byte) (value * 20));
        }
        return bytes.array();
    }

    private static void entry(ByteBuffer bytes, int tag, int type, int count, int value) {
        bytes.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static void key(ByteBuffer bytes, int key, int value) {
        bytes.putShort((short) key).putShort((short) 0).putShort((short) 1).putShort((short) value);
    }
}
