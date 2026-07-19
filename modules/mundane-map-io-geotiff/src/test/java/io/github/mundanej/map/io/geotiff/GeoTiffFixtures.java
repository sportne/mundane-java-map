package io.github.mundanej.map.io.geotiff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Deflater;

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

    static byte[] bigEndianGray() {
        return raster(ByteOrder.BIG_ENDIAN, 4, 3, 1, 1, false, false);
    }

    static byte[] whiteGray() {
        return raster(ByteOrder.LITTLE_ENDIAN, 4, 3, 0, 1, false, false);
    }

    static byte[] whiteGrayAlpha() {
        return raster(ByteOrder.LITTLE_ENDIAN, 4, 3, 0, 2, false, false);
    }

    static byte[] blackGrayAlpha() {
        return raster(ByteOrder.LITTLE_ENDIAN, 4, 3, 1, 2, false, false);
    }

    static byte[] rgb() {
        return raster(ByteOrder.LITTLE_ENDIAN, 4, 3, 2, 3, false, false);
    }

    static byte[] rgba() {
        return raster(ByteOrder.LITTLE_ENDIAN, 4, 3, 2, 4, false, false);
    }

    static byte[] tiledRgb() {
        return raster(ByteOrder.LITTLE_ENDIAN, 17, 17, 2, 3, true, false);
    }

    static byte[] projectedGray() {
        return raster(ByteOrder.LITTLE_ENDIAN, 4, 3, 1, 1, false, true);
    }

    static byte[] packBitsTiledRgb() {
        return compressedRaster(17, 17, 2, 3, true, 32773);
    }

    static byte[] packBitsTiledRgbWithFirstSegment(byte[] firstSegment) {
        List<byte[]> encoded = new ArrayList<>();
        for (byte[] segment : segments(17, 17, 2, 3, true)) {
            encoded.add(packBits(segment));
        }
        encoded.set(0, firstSegment.clone());
        return raster(ByteOrder.LITTLE_ENDIAN, 17, 17, 2, 3, true, false, 32773, encoded);
    }

    static byte[] deflateRgba() {
        return compressedRaster(4, 3, 2, 4, false, 8);
    }

    static byte[] compressedGray(int compression, byte[] encoded) {
        return raster(
                ByteOrder.LITTLE_ENDIAN,
                4,
                2,
                1,
                1,
                false,
                false,
                compression,
                List.of(encoded.clone()));
    }

    static byte[] deflate(byte[] decoded) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(decoded);
            deflater.finish();
            byte[] buffer = new byte[decoded.length * 2 + 32];
            int length = deflater.deflate(buffer);
            return java.util.Arrays.copyOf(buffer, length);
        } finally {
            deflater.end();
        }
    }

    static byte[] deflateWithDictionary(byte[] decoded) {
        Deflater deflater = new Deflater();
        try {
            deflater.setDictionary(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
            deflater.setInput(decoded);
            deflater.finish();
            byte[] buffer = new byte[decoded.length * 2 + 32];
            int length = deflater.deflate(buffer);
            return java.util.Arrays.copyOf(buffer, length);
        } finally {
            deflater.end();
        }
    }

    private static byte[] raster(
            ByteOrder order,
            int width,
            int height,
            int photometric,
            int samples,
            boolean tiled,
            boolean projected) {
        return raster(
                order,
                width,
                height,
                photometric,
                samples,
                tiled,
                projected,
                1,
                segments(width, height, photometric, samples, tiled));
    }

    private static byte[] compressedRaster(
            int width, int height, int photometric, int samples, boolean tiled, int compression) {
        List<byte[]> encoded = new ArrayList<>();
        for (byte[] segment : segments(width, height, photometric, samples, tiled)) {
            encoded.add(compression == 8 ? deflate(segment) : packBits(segment));
        }
        return raster(
                ByteOrder.LITTLE_ENDIAN,
                width,
                height,
                photometric,
                samples,
                tiled,
                false,
                compression,
                encoded);
    }

    private static byte[] raster(
            ByteOrder order,
            int width,
            int height,
            int photometric,
            int samples,
            boolean tiled,
            boolean projected,
            int compression,
            List<byte[]> segments) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.shorts(256, width));
        tags.add(Tag.shorts(257, height));
        tags.add(Tag.repeatedShorts(258, samples, 8));
        tags.add(Tag.shorts(259, compression));
        tags.add(Tag.shorts(262, photometric));
        int segmentCount = segments.size();
        if (tiled) {
            tags.add(Tag.shorts(322, 16));
            tags.add(Tag.shorts(323, 16));
            tags.add(Tag.longs(324, new long[segmentCount]));
            tags.add(Tag.longs(325, segments.stream().mapToLong(value -> value.length).toArray()));
        } else {
            tags.add(Tag.longs(273, new long[segmentCount]));
            tags.add(Tag.longs(278, 2));
            tags.add(Tag.longs(279, segments.stream().mapToLong(value -> value.length).toArray()));
        }
        if (samples != 1) {
            tags.add(Tag.shorts(277, samples));
        }
        tags.add(Tag.shorts(284, 1));
        if (samples == 2 || samples == 4) {
            tags.add(Tag.shorts(338, 2));
        }
        tags.add(Tag.repeatedShorts(339, samples, 1));
        tags.add(Tag.doubles(33550, 1, 1, 0));
        tags.add(Tag.doubles(33922, 0, 0, 0, projected ? 1_000 : 10, projected ? 2_000 : 20, 0));
        tags.add(
                Tag.shorts(
                        34735,
                        projected
                                ? new int[] {
                                    1, 1, 0, 4,
                                    1024, 0, 1, 1,
                                    1025, 0, 1, 1,
                                    3072, 0, 1, 3857,
                                    3076, 0, 1, 9001
                                }
                                : new int[] {
                                    1, 1, 0, 3,
                                    1024, 0, 1, 2,
                                    1025, 0, 1, 1,
                                    2048, 0, 1, 4326
                                }));
        tags.sort(Comparator.comparingInt(tag -> tag.id));
        int position = 8 + 2 + tags.size() * 12 + 4;
        for (Tag tag : tags) {
            if (tag.payloadBytes() > 4) {
                position = even(position);
                tag.offset = position;
                position += tag.payloadBytes();
            }
        }
        int offsetTagId = tiled ? 324 : 273;
        Tag offsetTag =
                tags.stream().filter(tag -> tag.id == offsetTagId).findFirst().orElseThrow();
        for (int index = 0; index < segments.size(); index++) {
            position = even(position);
            offsetTag.integers[index] = position;
            position += segments.get(index).length;
        }
        ByteBuffer bytes = ByteBuffer.allocate(position).order(order);
        bytes.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        bytes.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        bytes.putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) tags.size());
        for (Tag tag : tags) {
            bytes.putShort((short) tag.id).putShort((short) tag.type).putInt(tag.count());
            if (tag.payloadBytes() > 4) {
                bytes.putInt(tag.offset);
            } else {
                int start = bytes.position();
                tag.writeValues(bytes);
                while (bytes.position() < start + 4) {
                    bytes.put((byte) 0);
                }
            }
        }
        bytes.putInt(0);
        for (Tag tag : tags) {
            if (tag.payloadBytes() > 4) {
                bytes.position(tag.offset);
                tag.writeValues(bytes);
            }
        }
        for (int index = 0; index < segments.size(); index++) {
            bytes.position(Math.toIntExact(offsetTag.integers[index]));
            bytes.put(segments.get(index));
        }
        return bytes.array();
    }

    private static byte[] packBits(byte[] decoded) {
        java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
        encoded.write(0x80);
        int index = 0;
        while (index < decoded.length) {
            int run = 1;
            while (index + run < decoded.length
                    && decoded[index + run] == decoded[index]
                    && run < 128) {
                run++;
            }
            if (run >= 3) {
                encoded.write(1 - run);
                encoded.write(decoded[index]);
                index += run;
                continue;
            }
            int literalStart = index;
            index += run;
            while (index < decoded.length && index - literalStart < 128) {
                int nextRun = 1;
                while (index + nextRun < decoded.length
                        && decoded[index + nextRun] == decoded[index]
                        && nextRun < 128) {
                    nextRun++;
                }
                if (nextRun >= 3) {
                    break;
                }
                index += nextRun;
            }
            int literal = index - literalStart;
            encoded.write(literal - 1);
            encoded.write(decoded, literalStart, literal);
        }
        return encoded.toByteArray();
    }

    private static List<byte[]> segments(
            int width, int height, int photometric, int samples, boolean tiled) {
        List<byte[]> result = new ArrayList<>();
        int blockWidth = tiled ? 16 : width;
        int blockHeight = tiled ? 16 : 2;
        int across = tiled ? (width + 15) / 16 : 1;
        int down = (height + blockHeight - 1) / blockHeight;
        for (int blockRow = 0; blockRow < down; blockRow++) {
            for (int blockColumn = 0; blockColumn < across; blockColumn++) {
                int rows =
                        tiled
                                ? blockHeight
                                : Math.min(blockHeight, height - blockRow * blockHeight);
                byte[] segment = new byte[blockWidth * rows * samples];
                int target = 0;
                for (int localRow = 0; localRow < rows; localRow++) {
                    for (int localColumn = 0; localColumn < blockWidth; localColumn++) {
                        int column = blockColumn * blockWidth + localColumn;
                        int row = blockRow * blockHeight + localRow;
                        int[] values = sample(column, row, photometric, samples);
                        for (int value : values) {
                            segment[target++] = (byte) value;
                        }
                    }
                }
                result.add(segment);
            }
        }
        return result;
    }

    static int expectedRgba(int column, int row, int photometric, int samples) {
        int[] values = sample(column, row, photometric, samples);
        int red;
        int green;
        int blue;
        int alpha = (samples == 2 || samples == 4) ? values[samples - 1] : 255;
        if (photometric == 2) {
            red = values[0];
            green = values[1];
            blue = values[2];
        } else {
            int gray = photometric == 0 ? 255 - values[0] : values[0];
            red = gray;
            green = gray;
            blue = gray;
        }
        return (red << 24) | (green << 16) | (blue << 8) | alpha;
    }

    private static int[] sample(int column, int row, int photometric, int samples) {
        int alpha = (64 + column * 13 + row * 19) & 0xff;
        if (photometric == 2) {
            return samples == 4
                    ? new int[] {
                        (10 + column * 7) & 0xff,
                        (20 + row * 9) & 0xff,
                        (30 + column + row) & 0xff,
                        alpha
                    }
                    : new int[] {
                        (10 + column * 7) & 0xff, (20 + row * 9) & 0xff, (30 + column + row) & 0xff
                    };
        }
        int gray = (column * 11 + row * 17) & 0xff;
        return samples == 2 ? new int[] {gray, alpha} : new int[] {gray};
    }

    private static int even(int value) {
        return (value + 1) & ~1;
    }

    private static final class Tag {
        private final int id;
        private final int type;
        private final long[] integers;
        private final double[] floating;
        private int offset;

        private Tag(int id, int type, long[] integers, double[] floating) {
            this.id = id;
            this.type = type;
            this.integers = integers;
            this.floating = floating;
        }

        private static Tag shorts(int id, int... values) {
            long[] converted = new long[values.length];
            for (int index = 0; index < values.length; index++) {
                converted[index] = values[index];
            }
            return new Tag(id, 3, converted, null);
        }

        private static Tag repeatedShorts(int id, int count, int value) {
            int[] values = new int[count];
            java.util.Arrays.fill(values, value);
            return shorts(id, values);
        }

        private static Tag longs(int id, long... values) {
            return new Tag(id, 4, values, null);
        }

        private static Tag doubles(int id, double... values) {
            return new Tag(id, 12, null, values);
        }

        private int count() {
            return floating == null ? integers.length : floating.length;
        }

        private int payloadBytes() {
            return count() * (type == 3 ? 2 : type == 4 ? 4 : 8);
        }

        private void writeValues(ByteBuffer target) {
            if (floating != null) {
                for (double value : floating) {
                    target.putDouble(value);
                }
            } else if (type == 3) {
                for (long value : integers) {
                    target.putShort((short) value);
                }
            } else {
                for (long value : integers) {
                    target.putInt(Math.toIntExact(value));
                }
            }
        }
    }

    private static void entry(ByteBuffer bytes, int tag, int type, int count, int value) {
        bytes.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static void key(ByteBuffer bytes, int key, int value) {
        bytes.putShort((short) key).putShort((short) 0).putShort((short) 1).putShort((short) value);
    }
}
