package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

final class ImageSnapshots {
    private ImageSnapshots() {}

    static byte[] exact(
            ImageChannel channel,
            long expectedLength,
            String sourceId,
            String stage,
            CancellationToken cancellation)
            throws IOException {
        if (expectedLength > Integer.MAX_VALUE) {
            throw ImageDiagnostics.limit(
                    sourceId, "encodedBytes", expectedLength, Integer.MAX_VALUE);
        }
        requireSize(channel, expectedLength, sourceId, stage);
        byte[] bytes = new byte[(int) expectedLength];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long position = 0;
        while (buffer.hasRemaining()) {
            ImageHeaderProbe.checkpoint(cancellation, sourceId, "image-" + stage);
            int previousLimit = buffer.limit();
            buffer.limit(Math.min(previousLimit, buffer.position() + 4096));
            int count = channel.read(buffer, position);
            buffer.limit(previousLimit);
            if (count < 0) {
                lengthMismatch(sourceId, expectedLength, buffer.position(), stage);
            }
            if (count > 0) {
                position += count;
            }
        }
        requireSize(channel, expectedLength, sourceId, stage);
        return bytes;
    }

    static ImageContentVersion fingerprint(
            ImageChannel channel,
            long expectedLength,
            ImageHeader expectedHeader,
            String sourceId,
            String stage,
            CancellationToken cancellation)
            throws IOException {
        requireSize(channel, expectedLength, sourceId, stage);
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        long position = 0;
        while (position < expectedLength) {
            ImageHeaderProbe.checkpoint(cancellation, sourceId, "image-" + stage);
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), expectedLength - position));
            int count = channel.read(buffer, position);
            if (count < 0) {
                lengthMismatch(sourceId, expectedLength, position, stage);
            }
            if (count > 0) {
                if (!expectedHeader.matchesSnapshotRange(buffer.array(), 0, position, count)) {
                    headerMismatch(sourceId, stage);
                }
                digest.update(buffer.array(), 0, count);
                position += count;
            }
        }
        requireSize(channel, expectedLength, sourceId, stage);
        return new ImageContentVersion(expectedLength, digest.digest());
    }

    static ImageContentVersion version(byte[] bytes) {
        return new ImageContentVersion(bytes.length, sha256().digest(bytes));
    }

    static void requireVersion(
            ImageContentVersion expected,
            ImageContentVersion actual,
            String sourceId,
            String reason) {
        if (!expected.equals(actual)) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "IMAGE_CONTENT_CHANGED",
                    "image",
                    "Encoded image content changed",
                    Map.of("reason", reason));
        }
    }

    static void requireHeader(byte[] bytes, ImageHeader header, String sourceId, String stage) {
        if (!header.matchesSnapshotPrefix(bytes)) {
            headerMismatch(sourceId, stage);
        }
    }

    private static void headerMismatch(String sourceId, String stage) {
        throw ImageDiagnostics.failure(
                sourceId,
                "IMAGE_DECODE_MISMATCH",
                "decoder",
                "Encoded image header changed",
                Map.of(
                        "field", "headerSnapshot",
                        "expected", "captured",
                        "actual", "changed",
                        "reason", stage));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("Java runtime must provide SHA-256", impossible);
        }
    }

    private static void requireSize(
            ImageChannel channel, long expected, String sourceId, String stage) throws IOException {
        long actual = channel.size();
        if (actual != expected) {
            lengthMismatch(sourceId, expected, actual, stage);
        }
    }

    private static void lengthMismatch(String sourceId, long expected, long actual, String stage) {
        throw ImageDiagnostics.failure(
                sourceId,
                "IMAGE_FILE_LENGTH_MISMATCH",
                "image",
                "Encoded image length changed",
                Map.of(
                        "capturedBytes", Long.toString(expected),
                        "actualBytes", Long.toString(actual),
                        "reason", stage));
    }
}
