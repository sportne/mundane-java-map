package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.SourceException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.OptionalLong;
import java.util.zip.CRC32;

final class ImageHeaderProbe {
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private ImageHeaderProbe() {}

    static ImageHeader probe(
            ImageChannel channel,
            String extension,
            String sourceId,
            ImageSourceLimits limits,
            CancellationToken cancellation)
            throws IOException {
        long length = channel.size();
        if (length > limits.maximumEncodedBytes()) {
            throw ImageDiagnostics.limit(
                    sourceId, "encodedBytes", length, limits.maximumEncodedBytes());
        }
        if (length < 2) {
            throw invalid(sourceId, "unknown", "signature", "truncated", 0);
        }
        byte[] first = readExact(channel, 0, (int) Math.min(8, length), cancellation, sourceId);
        EncodedRasterFormat signature = signature(first);
        EncodedRasterFormat expected =
                extension.equals("png") ? EncodedRasterFormat.PNG : EncodedRasterFormat.JPEG;
        if (signature != expected) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "IMAGE_FORMAT_MISMATCH",
                    "image",
                    "Filename extension and image signature disagree",
                    Map.of(
                            "extension",
                            extension,
                            "signature",
                            signature == null ? "unknown" : signature.name()));
        }
        return signature == EncodedRasterFormat.PNG
                ? png(channel, sourceId, limits, cancellation, length)
                : jpeg(channel, sourceId, limits, cancellation, length);
    }

    static byte[] readExact(
            ImageChannel channel,
            long start,
            int length,
            CancellationToken cancellation,
            String sourceId)
            throws IOException {
        byte[] result = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        long position = start;
        while (buffer.hasRemaining()) {
            checkpoint(cancellation, sourceId, "image-open");
            int oldLimit = buffer.limit();
            buffer.limit(Math.min(oldLimit, buffer.position() + 4096));
            int count = channel.read(buffer, position);
            buffer.limit(oldLimit);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                continue;
            }
            position += count;
        }
        checkpoint(cancellation, sourceId, "image-open");
        if (buffer.hasRemaining()) {
            return Arrays.copyOf(result, buffer.position());
        }
        return result;
    }

    static void checkpoint(CancellationToken cancellation, String sourceId, String operation) {
        if (cancellation.isCancellationRequested()) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "image",
                    "Image operation was cancelled",
                    Map.of("operation", operation));
        }
    }

    private static ImageHeader png(
            ImageChannel channel,
            String sourceId,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            long length)
            throws IOException {
        if (length < 33) {
            throw invalid(sourceId, "PNG", "IHDR", "truncated", length);
        }
        byte[] header = readExact(channel, 0, 33, cancellation, sourceId);
        if (int32(header, 8) != 13
                || !Arrays.equals(
                        Arrays.copyOfRange(header, 12, 16), new byte[] {'I', 'H', 'D', 'R'})) {
            throw invalid(sourceId, "PNG", "IHDR", "firstChunk", 8);
        }
        CRC32 crc = new CRC32();
        crc.update(header, 12, 17);
        if ((crc.getValue() & 0xffff_ffffL) != (int32(header, 29) & 0xffff_ffffL)) {
            throw invalid(sourceId, "PNG", "IHDR", "crc", 29);
        }
        int width = positiveDimension(sourceId, "PNG", "width", int32(header, 16), 16);
        int height = positiveDimension(sourceId, "PNG", "height", int32(header, 20), 20);
        int depth = header[24] & 0xff;
        int color = header[25] & 0xff;
        int channels = pngChannels(sourceId, depth, color);
        if ((header[26] & 0xff) != 0 || (header[27] & 0xff) != 0 || (header[28] & 0xff) > 1) {
            throw unsupported(sourceId, "PNG", "method", "compression/filter/interlace");
        }
        validateLimits(sourceId, limits, width, height, channels);
        return new ImageHeader(
                EncodedRasterFormat.PNG, width, height, channels, depth, header, length);
    }

    private static ImageHeader jpeg(
            ImageChannel channel,
            String sourceId,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            long length)
            throws IOException {
        JpegReader bytes = new JpegReader(channel, sourceId, limits, cancellation, length);
        bytes.requireRange(0, 2, "signature");
        long position = 2;
        while (position < length) {
            long markerStart = position;
            int prefix = bytes.unsigned(position++, "marker");
            if (prefix != 0xff) {
                throw invalid(sourceId, "JPEG", "marker", "prefix", markerStart);
            }
            if (position >= length) {
                throw invalid(sourceId, "JPEG", "marker", "truncated", position);
            }
            int marker;
            do {
                marker = bytes.unsigned(position++, "marker");
            } while (marker == 0xff && position < length);
            if (marker == 0x00) {
                throw invalid(sourceId, "JPEG", "marker", "stuffedBeforeSof", position - 1);
            }
            if (marker == 0x01) {
                throw unsupported(
                        sourceId, "JPEG", "marker", ImageContainerValidator.markerName(marker));
            }
            if (marker == 0xd8 || (marker >= 0xd0 && marker <= 0xd7)) {
                throw invalid(sourceId, "JPEG", "marker", "standaloneBeforeSof", position - 1);
            }
            if (marker == 0xd9 || marker == 0xda) {
                throw invalid(sourceId, "JPEG", "SOF", "missing", position - 1);
            }
            bytes.requireRange(position, 2, "segmentLength");
            int segmentLength =
                    (bytes.unsigned(position, "segmentLength") << 8)
                            | bytes.unsigned(position + 1, "segmentLength");
            if (segmentLength < 2) {
                throw invalid(sourceId, "JPEG", "segmentLength", "invalid", position);
            }
            long end = bytes.requireRange(position, segmentLength, "segment");
            if (marker == 0xc0 || marker == 0xc2) {
                if (segmentLength < 8) {
                    throw invalid(sourceId, "JPEG", "SOF", "short", position);
                }
                int precision = bytes.unsigned(position + 2, "SOF");
                int height =
                        (bytes.unsigned(position + 3, "SOF") << 8)
                                | bytes.unsigned(position + 4, "SOF");
                int width =
                        (bytes.unsigned(position + 5, "SOF") << 8)
                                | bytes.unsigned(position + 6, "SOF");
                int components = bytes.unsigned(position + 7, "SOF");
                if (precision != 8 || (components != 1 && components != 3)) {
                    throw unsupported(sourceId, "JPEG", "SOF", precision + "/" + components);
                }
                if (segmentLength != 8 + 3 * components) {
                    throw invalid(sourceId, "JPEG", "SOF", "length", position);
                }
                boolean[] ids = new boolean[256];
                for (int index = 0; index < components; index++) {
                    long base = position + 8L + 3L * index;
                    int id = bytes.unsigned(base, "component");
                    int sampling = bytes.unsigned(base + 1, "component");
                    int table = bytes.unsigned(base + 2, "component");
                    if (ids[id] || (sampling & 0x0f) == 0 || (sampling & 0xf0) == 0 || table > 3) {
                        throw invalid(sourceId, "JPEG", "component", "invalid", base);
                    }
                    ids[id] = true;
                }
                positiveDimension(sourceId, "JPEG", "width", width, position + 5);
                positiveDimension(sourceId, "JPEG", "height", height, position + 3);
                validateLimits(sourceId, limits, width, height, components);
                if (end > limits.maximumHeaderBytes() || end > Integer.MAX_VALUE) {
                    throw ImageDiagnostics.limit(
                            sourceId,
                            "headerBytes",
                            end > Integer.MAX_VALUE ? Long.MAX_VALUE : end,
                            Math.min(limits.maximumHeaderBytes(), Integer.MAX_VALUE));
                }
                byte[] snapshot = readExact(channel, 0, (int) end, cancellation, sourceId);
                return new ImageHeader(
                        EncodedRasterFormat.JPEG,
                        width,
                        height,
                        components,
                        precision,
                        snapshot,
                        length);
            }
            if ((marker >= 0xc0 && marker <= 0xcf)
                    && marker != 0xc4
                    && marker != 0xc8
                    && marker != 0xcc) {
                throw unsupported(
                        sourceId, "JPEG", "marker", ImageContainerValidator.markerName(marker));
            }
            position = end;
        }
        throw invalid(sourceId, "JPEG", "SOF", "truncated", length);
    }

    static EncodedRasterFormat signature(byte[] first) {
        if (first.length >= 8 && Arrays.equals(Arrays.copyOf(first, 8), PNG_SIGNATURE)) {
            return EncodedRasterFormat.PNG;
        }
        if (first.length >= 2 && (first[0] & 0xff) == 0xff && (first[1] & 0xff) == 0xd8) {
            return EncodedRasterFormat.JPEG;
        }
        return null;
    }

    private static int pngChannels(String sourceId, int depth, int color) {
        boolean accepted =
                switch (color) {
                    case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
                    case 2, 4, 6 -> depth == 8;
                    case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
                    default -> false;
                };
        if (!accepted) {
            throw unsupported(sourceId, "PNG", "bitDepth/colorType", depth + "/" + color);
        }
        return switch (color) {
            case 0 -> 1;
            case 2 -> 3;
            case 3, 6 -> 4;
            case 4 -> 2;
            default -> throw new AssertionError(color);
        };
    }

    private static void validateLimits(
            String sourceId, ImageSourceLimits limits, int width, int height, int channels) {
        if (width > limits.maximumWidth()) {
            throw ImageDiagnostics.limit(sourceId, "width", width, limits.maximumWidth());
        }
        if (height > limits.maximumHeight()) {
            throw ImageDiagnostics.limit(sourceId, "height", height, limits.maximumHeight());
        }
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException ignored) {
            pixels = Long.MAX_VALUE;
        }
        if (pixels > limits.maximumPixels()) {
            throw ImageDiagnostics.limit(sourceId, "pixels", pixels, limits.maximumPixels());
        }
        if (channels > limits.maximumLogicalChannels()) {
            throw ImageDiagnostics.limit(
                    sourceId, "logicalChannels", channels, limits.maximumLogicalChannels());
        }
    }

    private static int positiveDimension(
            String sourceId, String format, String field, int value, long offset) {
        if (value <= 0) {
            throw invalid(sourceId, format, field, "nonPositive", offset);
        }
        return value;
    }

    private static int int32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
    }

    private static SourceException invalid(
            String sourceId, String format, String field, String reason, long offset) {
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_HEADER_INVALID",
                "image",
                OptionalLong.of(offset),
                "Encoded image header is invalid",
                Map.of("format", format, "field", field, "reason", reason),
                null);
    }

    private static SourceException unsupported(
            String sourceId, String format, String field, String actual) {
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_PROFILE_UNSUPPORTED",
                "image",
                "Encoded image profile is unsupported",
                Map.of("format", format, "field", field, "actual", actual));
    }

    private static final class JpegReader {
        private final ImageChannel channel;
        private final String sourceId;
        private final ImageSourceLimits limits;
        private final CancellationToken cancellation;
        private final long encodedLength;

        private JpegReader(
                ImageChannel channel,
                String sourceId,
                ImageSourceLimits limits,
                CancellationToken cancellation,
                long encodedLength) {
            this.channel = channel;
            this.sourceId = sourceId;
            this.limits = limits;
            this.cancellation = cancellation;
            this.encodedLength = encodedLength;
        }

        private int unsigned(long position, String field) throws IOException {
            requireRange(position, 1, field);
            byte[] value = readExact(channel, position, 1, cancellation, sourceId);
            if (value.length != 1) {
                throw invalid(sourceId, "JPEG", field, "truncated", position);
            }
            return value[0] & 0xff;
        }

        private long requireRange(long start, long count, String field) {
            long end;
            try {
                end = Math.addExact(start, count);
            } catch (ArithmeticException ignored) {
                throw ImageDiagnostics.limit(
                        sourceId, "headerBytes", Long.MAX_VALUE, limits.maximumHeaderBytes());
            }
            if (end > limits.maximumHeaderBytes()) {
                throw ImageDiagnostics.limit(
                        sourceId, "headerBytes", end, limits.maximumHeaderBytes());
            }
            if (end > encodedLength) {
                throw invalid(sourceId, "JPEG", field, "truncated", Math.min(start, encodedLength));
            }
            return end;
        }
    }
}
