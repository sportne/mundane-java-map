package io.github.mundanej.map.io.shapefile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ShxFixtures {
    private ShxFixtures() {}

    static byte[] file(
            int type, double minX, double minY, double maxX, double maxY, byte[]... contents) {
        ByteBuffer out =
                ByteBuffer.allocate(Math.addExact(100, Math.multiplyExact(contents.length, 8)));
        out.order(ByteOrder.BIG_ENDIAN).putInt(9994);
        for (int index = 0; index < 5; index++) {
            out.putInt(0);
        }
        out.putInt(out.capacity() / 2);
        out.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1000)
                .putInt(type)
                .putDouble(minX)
                .putDouble(minY)
                .putDouble(maxX)
                .putDouble(maxY);
        for (int index = 0; index < 4; index++) {
            out.putLong(0x7ff8000000000000L);
        }
        int offsetWords = 50;
        out.order(ByteOrder.BIG_ENDIAN);
        for (byte[] content : contents) {
            out.putInt(offsetWords).putInt(content.length / 2);
            offsetWords = Math.addExact(offsetWords, 4 + content.length / 2);
        }
        return out.array();
    }
}
