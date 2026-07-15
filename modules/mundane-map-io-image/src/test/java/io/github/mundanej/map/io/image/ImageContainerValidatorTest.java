package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.SourceException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

class ImageContainerValidatorTest {
    @Test
    void pollsAcrossLargePngAncillaryAndIdatPhysicalBytes() {
        byte[] baseline = png(1, 1, new byte[0]);
        byte[] ancillary = png(1, 1, new byte[20_000]);
        assertMorePolling(ancillary, baseline, pngHeader(ancillary));

        byte[] expanded = pngWithLargeIdat();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () -> validate(expanded, pngHeader(expanded), cancelAt(8)));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
    }

    @Test
    void pollsAcrossLargeJpegEntropyFillAppAndCommentBytes() {
        byte[] baseline = jpeg(new byte[0], new byte[] {1, 2}, 0);
        for (byte[] candidate :
                new byte[][] {
                    jpeg(segment(0xe1, 20_000), new byte[] {1, 2}, 0),
                    jpeg(segment(0xfe, 20_000), new byte[] {1, 2}, 0),
                    jpeg(new byte[0], repeated((byte) 1, 20_000), 0),
                    jpeg(new byte[0], new byte[] {1, 2}, 20_000)
                }) {
            assertMorePolling(candidate, baseline, jpegHeader(candidate));
        }
    }

    @Test
    void rejectsEmptyAndMalformedJpegTablesAtEveryProfileBoundary() {
        for (int marker : new int[] {0xdb, 0xc4}) {
            assertReason(
                    jpeg(segment(marker, 0), new byte[] {1}, 0),
                    "IMAGE_CONTAINER_INVALID",
                    "table");
        }
        for (byte[] payload :
                new byte[][] {dqt(2, 0, 64), dqt(0, 4, 64), dqt(0, 0, 63), dqt(1, 3, 127)}) {
            assertReason(
                    jpeg(segment(0xdb, payload), new byte[] {1}, 0),
                    "IMAGE_CONTAINER_INVALID",
                    "table");
        }
        for (byte[] payload :
                new byte[][] {
                    dht(2, 0, 0, 0), dht(0, 4, 0, 0), dht(0, 0, 257, 0), dht(0, 0, 1, 0)
                }) {
            assertReason(
                    jpeg(segment(0xc4, payload), new byte[] {1}, 0),
                    "IMAGE_CONTAINER_INVALID",
                    "table");
        }
    }

    @Test
    void normalizesUnsupportedJpegMarkerFamilies() {
        int[] markers = {0xc1, 0xc3, 0xc8, 0xc9, 0xcc, 0xdc, 0xde, 0xdf, 0x01, 0xf0};
        for (int marker : markers) {
            byte[] unsupported =
                    insertAfterSoi(
                            jpeg(new byte[0], new byte[] {1}, 0),
                            marker == 0x01 ? new byte[] {(byte) 0xff, 0x01} : segment(marker, 0));
            SourceException failure = failure(unsupported);
            assertEquals("IMAGE_PROFILE_UNSUPPORTED", failure.terminal().code());
            assertEquals("marker", failure.terminal().context().get("field"));
            assertTrue(
                    failure.terminal().context().get("actual").matches("[A-Z0-9]+/0x[0-9A-F]{2}"));
        }
    }

    private static void assertMorePolling(byte[] candidate, byte[] baseline, ImageHeader header) {
        AtomicInteger baselinePolls = new AtomicInteger();
        validate(
                baseline,
                header.format() == EncodedRasterFormat.PNG
                        ? pngHeader(baseline)
                        : jpegHeader(baseline),
                counting(baselinePolls));
        AtomicInteger candidatePolls = new AtomicInteger();
        validate(candidate, header, counting(candidatePolls));
        assertTrue(
                candidatePolls.get() >= baselinePolls.get() + 4,
                baselinePolls + " -> " + candidatePolls);
    }

    private static CancellationToken counting(AtomicInteger polls) {
        return () -> {
            polls.incrementAndGet();
            return false;
        };
    }

    private static CancellationToken cancelAt(int threshold) {
        AtomicInteger polls = new AtomicInteger();
        return () -> polls.incrementAndGet() >= threshold;
    }

    private static void assertReason(byte[] bytes, String code, String reason) {
        SourceException failure = failure(bytes);
        assertEquals(code, failure.terminal().code());
        assertEquals(reason, failure.terminal().context().get("reason"));
    }

    private static SourceException failure(byte[] bytes) {
        return assertThrows(
                SourceException.class,
                () -> validate(bytes, jpegHeader(bytes), CancellationToken.none()));
    }

    private static void validate(byte[] bytes, ImageHeader header, CancellationToken token) {
        ImageContainerValidator.validate(
                bytes, header, ImageSourceLimits.defaults(), token, "test");
    }

    private static ImageHeader pngHeader(byte[] bytes) {
        return new ImageHeader(
                EncodedRasterFormat.PNG,
                ByteBuffer.wrap(bytes, 16, 4).getInt(),
                ByteBuffer.wrap(bytes, 20, 4).getInt(),
                4,
                8,
                java.util.Arrays.copyOf(bytes, 33),
                bytes.length);
    }

    private static ImageHeader jpegHeader(byte[] bytes) {
        return new ImageHeader(
                EncodedRasterFormat.JPEG,
                1,
                1,
                1,
                8,
                java.util.Arrays.copyOf(bytes, Math.min(bytes.length, 15)),
                bytes.length);
    }

    private static byte[] png(int width, int height, byte[] ancillary) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        ByteBuffer header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
        header.putInt(width)
                .putInt(height)
                .put((byte) 8)
                .put((byte) 6)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        chunk(output, "IHDR", header.array());
        if (ancillary.length > 0) {
            chunk(output, "aaAa", ancillary);
        }
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[] {0, 0, 0, 0, 0});
        deflater.finish();
        byte[] compressed = new byte[64];
        int count = deflater.deflate(compressed);
        deflater.end();
        chunk(output, "IDAT", java.util.Arrays.copyOf(compressed, count));
        chunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static byte[] pngWithLargeIdat() {
        int width = 1024;
        int height = 5;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        ByteBuffer header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
        header.putInt(width)
                .putInt(height)
                .put((byte) 8)
                .put((byte) 6)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        chunk(output, "IHDR", header.array());
        byte[] filtered = new byte[(width * 4 + 1) * height];
        Deflater deflater = new Deflater(Deflater.NO_COMPRESSION);
        deflater.setInput(filtered);
        deflater.finish();
        byte[] compressed = new byte[filtered.length + 128];
        int count = deflater.deflate(compressed);
        deflater.end();
        chunk(output, "IDAT", java.util.Arrays.copyOf(compressed, count));
        chunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static byte[] jpeg(byte[] prefixSegments, byte[] entropy, int fill) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0xff, (byte) 0xd8});
        output.writeBytes(prefixSegments);
        output.writeBytes(
                new byte[] {(byte) 0xff, (byte) 0xc0, 0, 11, 8, 0, 1, 0, 1, 1, 1, 0x11, 0});
        output.writeBytes(new byte[] {(byte) 0xff, (byte) 0xda, 0, 8, 1, 1, 0, 0, 63, 0});
        output.writeBytes(entropy);
        output.write((byte) 0xff);
        for (int index = 0; index < fill; index++) {
            output.write((byte) 0xff);
        }
        output.write((byte) 0xd9);
        return output.toByteArray();
    }

    private static byte[] segment(int marker, int payloadLength) {
        return segment(marker, new byte[payloadLength]);
    }

    private static byte[] segment(int marker, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write((byte) 0xff);
        output.write(marker);
        output.write((payload.length + 2) >>> 8);
        output.write(payload.length + 2);
        output.writeBytes(payload);
        return output.toByteArray();
    }

    private static byte[] dqt(int precision, int id, int values) {
        byte[] payload = new byte[1 + values];
        payload[0] = (byte) (precision << 4 | id);
        return payload;
    }

    private static byte[] dht(int tableClass, int id, int symbols, int supplied) {
        byte[] payload = new byte[17 + supplied];
        payload[0] = (byte) (tableClass << 4 | id);
        payload[1] = (byte) symbols;
        return payload;
    }

    private static byte[] repeated(byte value, int count) {
        byte[] result = new byte[count];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static byte[] insertAfterSoi(byte[] jpeg, byte[] segment) {
        byte[] result = new byte[jpeg.length + segment.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(segment, 0, result, 2, segment.length);
        System.arraycopy(jpeg, 2, result, 2 + segment.length, jpeg.length - 2);
        return result;
    }

    private static void chunk(ByteArrayOutputStream output, String type, byte[] payload) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + 12).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(payload.length).put(name).put(payload);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        buffer.putInt((int) crc.getValue());
        output.writeBytes(buffer.array());
    }
}
