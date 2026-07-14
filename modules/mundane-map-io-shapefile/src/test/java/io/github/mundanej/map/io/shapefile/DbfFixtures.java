package io.github.mundanej.map.io.shapefile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class DbfFixtures {
    record Field(String name, char type, int width, int decimals) {}

    private DbfFixtures() {}

    static Field field(String name, char type, int width, int decimals) {
        return new Field(name, type, width, decimals);
    }

    static byte[] dbf(int version, int ldid, Field[] fields, byte[]... rows) {
        int headerLength = 32 + fields.length * 32 + 1;
        int recordLength = 1 + Arrays.stream(fields).mapToInt(Field::width).sum();
        ByteBuffer buffer =
                ByteBuffer.allocate(headerLength + rows.length * recordLength)
                        .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) version).position(4);
        buffer.putInt(rows.length).putShort((short) headerLength).putShort((short) recordLength);
        buffer.position(29).put((byte) ldid).position(32);
        for (Field field : fields) {
            byte[] name = field.name().getBytes(StandardCharsets.US_ASCII);
            if (name.length > 10) {
                throw new IllegalArgumentException("fixture field name is too long");
            }
            buffer.put(name).position(buffer.position() + 11 - name.length);
            buffer.put((byte) field.type()).position(buffer.position() + 4);
            buffer.put((byte) field.width()).put((byte) field.decimals());
            buffer.position(buffer.position() + 14);
        }
        buffer.put((byte) 0x0d);
        for (byte[] row : rows) {
            if (row.length != recordLength) {
                throw new IllegalArgumentException("fixture row has the wrong width");
            }
            buffer.put(row);
        }
        return buffer.array();
    }

    static byte[] row(int marker, Field[] fields, byte[]... values) {
        if (fields.length != values.length) {
            throw new IllegalArgumentException("fixture row field count differs");
        }
        int length = 1 + Arrays.stream(fields).mapToInt(Field::width).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length).put((byte) marker);
        for (int index = 0; index < fields.length; index++) {
            byte[] value = values[index];
            if (value.length > fields[index].width()) {
                throw new IllegalArgumentException("fixture value is too wide");
            }
            buffer.put(value);
            for (int padding = value.length; padding < fields[index].width(); padding++) {
                buffer.put((byte) ' ');
            }
        }
        return buffer.array();
    }

    static byte[] row(int marker, Field[] fields, String... values) {
        byte[][] encoded = new byte[values.length][];
        for (int index = 0; index < values.length; index++) {
            encoded[index] = values[index].getBytes(StandardCharsets.US_ASCII);
        }
        return row(marker, fields, encoded);
    }

    static byte[] withEof(byte[] dbf) {
        byte[] result = Arrays.copyOf(dbf, dbf.length + 1);
        result[result.length - 1] = 0x1a;
        return result;
    }

    static ByteBuffer little(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
