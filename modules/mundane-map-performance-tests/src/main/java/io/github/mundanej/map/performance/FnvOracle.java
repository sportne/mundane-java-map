package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.Rgba;
import java.nio.charset.StandardCharsets;

final class FnvOracle {
    private static final long OFFSET = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;
    private long value = OFFSET;

    FnvOracle(long seed) {
        addTag(0x03);
        addRawLong(seed);
    }

    FnvOracle add(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        addTag(0x01);
        addRawInt(bytes.length);
        addBytes(bytes);
        return this;
    }

    FnvOracle add(int number) {
        addTag(0x02);
        addRawInt(number);
        return this;
    }

    FnvOracle add(long number) {
        addTag(0x03);
        addRawLong(number);
        return this;
    }

    FnvOracle add(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Oracle doubles must be finite");
        }
        addTag(0x04);
        addRawLong(Double.doubleToLongBits(number == 0.0 ? 0.0 : number));
        return this;
    }

    FnvOracle add(boolean flag) {
        addTag(0x05);
        addByte(flag ? 1 : 0);
        return this;
    }

    FnvOracle add(Enum<?> item) {
        byte[] bytes = item.name().getBytes(StandardCharsets.UTF_8);
        addTag(0x06);
        addRawInt(bytes.length);
        addBytes(bytes);
        return this;
    }

    FnvOracle add(Rgba color) {
        return addPackedRgba(
                (color.red() << 24) | (color.green() << 16) | (color.blue() << 8) | color.alpha());
    }

    FnvOracle addPackedRgba(int rgba) {
        addTag(0x07);
        addByte(rgba >>> 24);
        addByte(rgba >>> 16);
        addByte(rgba >>> 8);
        addByte(rgba);
        return this;
    }

    long value() {
        return value;
    }

    private void addTag(int tag) {
        addByte(tag);
    }

    private void addRawInt(int number) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            addByte(number >>> shift);
        }
    }

    private void addRawLong(long number) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            addByte((int) (number >>> shift));
        }
    }

    private void addBytes(byte[] bytes) {
        for (byte item : bytes) {
            addByte(item);
        }
    }

    private void addByte(int item) {
        value ^= item & 0xff;
        value *= PRIME;
    }
}
