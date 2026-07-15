package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.SourceException;
import java.util.Map;
import java.util.OptionalLong;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Complete AWT-free physical validator for the bounded Level 1 image profile. */
final class ImageContainerValidator {
    private static final int[] ADAM7_X_START = {0, 4, 0, 2, 0, 1, 0};
    private static final int[] ADAM7_Y_START = {0, 0, 4, 0, 2, 0, 1};
    private static final int[] ADAM7_X_STEP = {8, 8, 4, 4, 2, 2, 1};
    private static final int[] ADAM7_Y_STEP = {8, 8, 8, 4, 4, 2, 2};

    private ImageContainerValidator() {}

    static void validate(
            byte[] bytes,
            ImageHeader header,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            String sourceId) {
        if (header.format() == EncodedRasterFormat.PNG) {
            png(bytes, header, limits, cancellation, sourceId);
        } else {
            jpeg(bytes, limits, cancellation, sourceId);
        }
    }

    private static void png(
            byte[] bytes,
            ImageHeader header,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            String sourceId) {
        PngInflationValidator inflation =
                new PngInflationValidator(header, limits, cancellation, sourceId);
        try {
            pngChunks(bytes, limits, cancellation, sourceId, inflation);
        } finally {
            inflation.close();
        }
    }

    private static void pngChunks(
            byte[] bytes,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            String sourceId,
            PngInflationValidator inflation) {
        int position = 8;
        long elements = 0;
        boolean palette = false;
        boolean transparency = false;
        boolean data = false;
        boolean dataEnded = false;
        boolean end = false;
        int paletteEntries = 0;
        while (position < bytes.length) {
            checkpoint(cancellation, sourceId);
            if (bytes.length - position < 12) {
                throw invalid(sourceId, "PNG", "chunkLength", position);
            }
            long length = u32(bytes, position);
            if (length > Integer.MAX_VALUE || length + 12 > bytes.length - position) {
                throw invalid(sourceId, "PNG", "chunkLength", position);
            }
            int size = (int) length;
            int typeOffset = position + 4;
            String type = type(bytes, typeOffset, sourceId);
            elements = increment(elements, limits.maximumContainerElements(), sourceId);
            int payload = position + 8;
            int crcOffset = payload + size;
            CRC32 crc = new CRC32();
            int crcLength = size + 4;
            for (int checked = 0; checked < crcLength; checked += 4096) {
                checkpoint(cancellation, sourceId);
                crc.update(bytes, typeOffset + checked, Math.min(4096, crcLength - checked));
            }
            if (crc.getValue() != u32(bytes, crcOffset)) {
                throw invalid(sourceId, "PNG", "chunkCrc", crcOffset);
            }
            if (end) {
                throw invalid(sourceId, "PNG", "trailingData", position);
            }
            if (type.equals("acTL") || type.equals("fcTL") || type.equals("fdAT")) {
                throw unsupported(sourceId, "PNG", "animation", type);
            }
            switch (type) {
                case "IHDR" -> {
                    if (position != 8 || size != 13) {
                        throw invalid(sourceId, "PNG", "chunkOrder", position);
                    }
                }
                case "PLTE" -> {
                    if (palette
                            || transparency
                            || data
                            || size == 0
                            || size % 3 != 0
                            || size > 768) {
                        throw invalid(sourceId, "PNG", "palette", position);
                    }
                    int color = bytes[25] & 0xff;
                    if (color == 0 || color == 4) {
                        throw invalid(sourceId, "PNG", "palette", position);
                    }
                    paletteEntries = size / 3;
                    if (color == 3 && paletteEntries > 1 << (bytes[24] & 0xff)) {
                        throw invalid(sourceId, "PNG", "palette", position);
                    }
                    palette = true;
                }
                case "tRNS" -> {
                    int color = bytes[25] & 0xff;
                    boolean valid = !transparency && !data;
                    valid &=
                            switch (color) {
                                case 0 ->
                                        size == 2
                                                && sample(bytes, payload, 2)
                                                        < (1 << (bytes[24] & 0xff));
                                case 2 -> size == 6 && samplesFit(bytes, payload, bytes[24] & 0xff);
                                case 3 -> palette && size >= 1 && size <= paletteEntries;
                                default -> false;
                            };
                    if (!valid) {
                        throw invalid(sourceId, "PNG", "palette", position);
                    }
                    transparency = true;
                }
                case "IDAT" -> {
                    if (dataEnded || ((bytes[25] & 0xff) == 3 && !palette)) {
                        throw invalid(sourceId, "PNG", "chunkOrder", position);
                    }
                    inflation.accept(bytes, payload, size);
                    data = true;
                }
                case "IEND" -> {
                    if (!data) {
                        throw invalid(sourceId, "PNG", "missingData", position);
                    }
                    if (size != 0) {
                        throw invalid(sourceId, "PNG", "chunkLength", position);
                    }
                    end = true;
                }
                default -> {
                    if (data) {
                        dataEnded = true;
                    }
                    if (Character.isUpperCase(type.charAt(0))) {
                        throw unsupported(sourceId, "PNG", "criticalChunk", type);
                    }
                }
            }
            if (!type.equals("IDAT") && data) {
                dataEnded = true;
            }
            position = crcOffset + 4;
            if (end && position != bytes.length) {
                throw invalid(sourceId, "PNG", "trailingData", position);
            }
        }
        if (!data) {
            throw invalid(sourceId, "PNG", "missingData", bytes.length);
        }
        if (!end) {
            throw invalid(sourceId, "PNG", "missingEnd", bytes.length);
        }
        inflation.finish();
    }

    private static final class PngInflationValidator implements AutoCloseable {
        private final Inflater inflater;
        private final byte[] scratch = new byte[4096];
        private final long expected;
        private final CancellationToken cancellation;
        private final String sourceId;
        private final int width;
        private final int height;
        private final int depth;
        private final int channels;
        private final boolean adam7;
        private long count;
        private int rowRemaining;
        private int rowsRemaining;
        private int pass;

        private PngInflationValidator(
                ImageHeader header,
                ImageSourceLimits limits,
                CancellationToken cancellation,
                String sourceId) {
            width = header.width();
            height = header.height();
            depth = header.bitsPerSample();
            byte[] snapshot = header.snapshot();
            channels = physicalChannels(snapshot[25] & 0xff);
            adam7 = snapshot[28] == 1;
            expected = filteredBytes(header);
            this.cancellation = cancellation;
            this.sourceId = sourceId;
            if (expected > limits.maximumInflatedRasterBytes()) {
                throw ImageDiagnostics.limit(
                        sourceId,
                        "inflatedRasterBytes",
                        expected,
                        limits.maximumInflatedRasterBytes());
            }
            inflater = new Inflater();
        }

        private void accept(byte[] bytes, int offset, int length) {
            int consumed = 0;
            while (consumed < length) {
                checkpoint(cancellation, sourceId);
                int supplied = Math.min(4096, length - consumed);
                if (inflater.finished()) {
                    throw invalid(sourceId, "PNG", "compressedData", offset + consumed);
                }
                inflater.setInput(bytes, offset + consumed, supplied);
                try {
                    while (!inflater.needsInput() && !inflater.finished()) {
                        checkpoint(cancellation, sourceId);
                        int produced = inflater.inflate(scratch);
                        if (produced == 0) {
                            if (inflater.needsDictionary()) {
                                throw invalid(sourceId, "PNG", "dictionary", offset + consumed);
                            }
                            if (!inflater.needsInput()) {
                                throw invalid(sourceId, "PNG", "compressedData", offset + consumed);
                            }
                        }
                        inspect(produced);
                    }
                    if (inflater.finished() && inflater.getRemaining() != 0) {
                        throw invalid(sourceId, "PNG", "compressedData", offset + consumed);
                    }
                } catch (DataFormatException failure) {
                    throw invalid(sourceId, "PNG", "compressedData", offset + consumed);
                }
                consumed += supplied;
            }
        }

        private void inspect(int produced) {
            for (int index = 0; index < produced; index++) {
                if (rowRemaining == 0) {
                    int next = nextRowBytes();
                    if (next < 0 || (scratch[index] & 0xff) > 4) {
                        throw invalid(sourceId, "PNG", next < 0 ? "decodedLength" : "filter", 0);
                    }
                    rowRemaining = next;
                } else {
                    rowRemaining--;
                }
            }
            count += produced;
            if (count > expected) {
                throw invalid(sourceId, "PNG", "decodedLength", 0);
            }
        }

        private void finish() {
            if (!inflater.finished()) {
                if (inflater.needsDictionary()) {
                    throw invalid(sourceId, "PNG", "dictionary", 0);
                }
                throw invalid(sourceId, "PNG", "compressedData", 0);
            }
            if (count != expected || rowRemaining != 0 || nextRowBytes() >= 0) {
                throw invalid(sourceId, "PNG", "decodedLength", 0);
            }
        }

        private int nextRowBytes() {
            if (!adam7) {
                if (pass >= height) {
                    return -1;
                }
                pass++;
                return rowBytes(width, channels, depth);
            }
            while (rowsRemaining == 0 && pass < 7) {
                int passWidth = span(width, ADAM7_X_START[pass], ADAM7_X_STEP[pass]);
                rowsRemaining = span(height, ADAM7_Y_START[pass], ADAM7_Y_STEP[pass]);
                pass++;
                if (passWidth > 0 && rowsRemaining > 0) {
                    rowRemaining = rowBytes(passWidth, channels, depth);
                    rowsRemaining--;
                    return rowRemaining;
                }
                rowsRemaining = 0;
            }
            if (rowsRemaining > 0) {
                int passWidth = span(width, ADAM7_X_START[pass - 1], ADAM7_X_STEP[pass - 1]);
                rowsRemaining--;
                return rowBytes(passWidth, channels, depth);
            }
            return -1;
        }

        @Override
        public void close() {
            inflater.end();
        }
    }

    private static void jpeg(
            byte[] bytes,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            String sourceId) {
        int position = 2;
        long elements = 1;
        boolean sof = false;
        boolean scan = false;
        boolean entropy = false;
        boolean entropyData = false;
        int components = 0;
        int sofMarker = 0;
        boolean[] componentIds = new boolean[256];
        int restartInterval = 0;
        int restart = 0;
        while (position < bytes.length) {
            checkpoint(cancellation, sourceId);
            if (entropy) {
                int start = position;
                while (position < bytes.length && (bytes[position] & 0xff) != 0xff) {
                    if (((position - start) & 4095) == 0) {
                        checkpoint(cancellation, sourceId);
                    }
                    position++;
                }
                entropyData |= position > start;
                if (position == start && position >= bytes.length) {
                    throw invalid(sourceId, "JPEG", "missingEnd", position);
                }
                if (position >= bytes.length) {
                    throw invalid(sourceId, "JPEG", "missingEnd", position);
                }
            }
            if ((bytes[position++] & 0xff) != 0xff) {
                throw invalid(sourceId, "JPEG", entropy ? "entropy" : "markerOrder", position - 1);
            }
            int fillStart = position;
            while (position < bytes.length && (bytes[position] & 0xff) == 0xff) {
                if (((position - fillStart) & 4095) == 0) {
                    checkpoint(cancellation, sourceId);
                }
                position++;
            }
            if (position >= bytes.length) {
                throw invalid(sourceId, "JPEG", scan ? "missingEnd" : "missingData", position);
            }
            int marker = bytes[position++] & 0xff;
            if (entropy && marker == 0) {
                entropyData = true;
                continue;
            }
            if (entropy && marker >= 0xd0 && marker <= 0xd7) {
                elements = increment(elements, limits.maximumContainerElements(), sourceId);
                if (restartInterval == 0 || marker != 0xd0 + restart) {
                    throw invalid(sourceId, "JPEG", "entropy", position - 1);
                }
                restart = (restart + 1) & 7;
                continue;
            }
            if (entropy && !entropyData) {
                throw invalid(sourceId, "JPEG", "entropy", position - 1);
            }
            entropy = false;
            elements = increment(elements, limits.maximumContainerElements(), sourceId);
            if (marker == 0xd9) {
                if (!scan) {
                    throw invalid(sourceId, "JPEG", "missingData", position - 1);
                }
                if (position != bytes.length) {
                    throw invalid(sourceId, "JPEG", "trailingData", position);
                }
                return;
            }
            if (marker == 0xd8 || (marker >= 0xd0 && marker <= 0xd7)) {
                throw invalid(sourceId, "JPEG", "markerOrder", position - 1);
            }
            if (!supportedMarker(marker)) {
                throw unsupported(sourceId, "JPEG", "marker", markerName(marker));
            }
            if (position + 2 > bytes.length) {
                throw invalid(sourceId, "JPEG", "segmentLength", position);
            }
            int length = sample(bytes, position, 2);
            if (length < 2 || position + length > bytes.length) {
                throw invalid(sourceId, "JPEG", "segmentLength", position);
            }
            int payload = position + 2;
            int payloadLength = length - 2;
            if (marker == 0xc0 || marker == 0xc2) {
                if (sof || scan || payloadLength < 6) {
                    throw invalid(sourceId, "JPEG", "markerOrder", position);
                }
                components = bytes[payload + 5] & 0xff;
                sofMarker = marker;
                for (int index = 0; index < components; index++) {
                    componentIds[bytes[payload + 6 + 3 * index] & 0xff] = true;
                }
                sof = true;
            } else if (marker == 0xdd) {
                if (payloadLength != 2) {
                    throw invalid(sourceId, "JPEG", "table", position);
                }
                restartInterval = sample(bytes, payload, 2);
            } else if (marker == 0xdb) {
                validateDqt(bytes, payload, payloadLength, cancellation, sourceId);
            } else if (marker == 0xc4) {
                validateDht(bytes, payload, payloadLength, cancellation, sourceId);
            } else if (marker == 0xda) {
                if (!sof || payloadLength < 4) {
                    throw invalid(sourceId, "JPEG", "markerOrder", position);
                }
                int count = bytes[payload] & 0xff;
                if (count < 1 || count > components || payloadLength != 4 + 2 * count) {
                    throw invalid(sourceId, "JPEG", "scan", position);
                }
                boolean[] ids = new boolean[256];
                for (int index = 0; index < count; index++) {
                    int id = bytes[payload + 1 + 2 * index] & 0xff;
                    int selector = bytes[payload + 2 + 2 * index] & 0xff;
                    if (ids[id]
                            || !componentIds[id]
                            || (selector >>> 4) > 3
                            || (selector & 15) > 3) {
                        throw invalid(sourceId, "JPEG", "scan", position);
                    }
                    ids[id] = true;
                }
                int spectral = payload + 1 + 2 * count;
                int startSpectral = bytes[spectral] & 0xff;
                int endSpectral = bytes[spectral + 1] & 0xff;
                int approximation = bytes[spectral + 2] & 0xff;
                int high = approximation >>> 4;
                int low = approximation & 15;
                boolean validScan;
                if (sofMarker == 0xc0) {
                    validScan = startSpectral == 0 && endSpectral == 63 && high == 0 && low == 0;
                } else {
                    boolean spectralShape =
                            startSpectral == 0
                                    ? endSpectral == 0
                                    : startSpectral <= endSpectral
                                            && endSpectral <= 63
                                            && count == 1;
                    validScan =
                            spectralShape
                                    && high <= 13
                                    && low <= 13
                                    && (high == 0 || high == low + 1);
                }
                if (!validScan) {
                    throw invalid(sourceId, "JPEG", "scan", position);
                }
                scan = true;
                entropy = true;
                entropyData = false;
                restart = 0;
            } else if (scan
                    && marker != 0xe0
                    && marker != 0xfe
                    && !(marker >= 0xe1 && marker <= 0xef)) {
                throw invalid(sourceId, "JPEG", "markerOrder", position);
            }
            checkpointRange(payloadLength, cancellation, sourceId);
            position += length;
        }
        throw invalid(sourceId, "JPEG", scan ? "missingEnd" : "missingData", bytes.length);
    }

    private static boolean supportedMarker(int marker) {
        return marker == 0xc0
                || marker == 0xc2
                || marker == 0xc4
                || marker == 0xdb
                || marker == 0xdd
                || marker == 0xda
                || marker == 0xfe
                || (marker >= 0xe0 && marker <= 0xef);
    }

    static String markerName(int marker) {
        String mnemonic =
                switch (marker) {
                    case 0xc1 -> "SOF1";
                    case 0xc3 -> "SOF3";
                    case 0xc5 -> "SOF5";
                    case 0xc6 -> "SOF6";
                    case 0xc7 -> "SOF7";
                    case 0xc8 -> "JPG";
                    case 0xc9 -> "SOF9";
                    case 0xca -> "SOF10";
                    case 0xcb -> "SOF11";
                    case 0xcc -> "DAC";
                    case 0xcd -> "SOF13";
                    case 0xce -> "SOF14";
                    case 0xcf -> "SOF15";
                    case 0xdc -> "DNL";
                    case 0xde -> "DHP";
                    case 0xdf -> "EXP";
                    default -> marker == 0x01 ? "TEM" : "RESERVED";
                };
        return String.format(java.util.Locale.ROOT, "%s/0x%02X", mnemonic, marker);
    }

    private static void checkpointRange(
            int length, CancellationToken cancellation, String sourceId) {
        for (int checked = 0; checked < length; checked += 4096) {
            checkpoint(cancellation, sourceId);
        }
    }

    private static void validateDqt(
            byte[] bytes, int offset, int length, CancellationToken cancellation, String sourceId) {
        if (length == 0) {
            throw invalid(sourceId, "JPEG", "table", offset);
        }
        int end = offset + length;
        while (offset < end) {
            checkpoint(cancellation, sourceId);
            int spec = bytes[offset++] & 0xff;
            int precision = spec >>> 4;
            if (precision > 1 || (spec & 15) > 3 || offset + 64 * (precision + 1) > end) {
                throw invalid(sourceId, "JPEG", "table", offset - 1);
            }
            offset += 64 * (precision + 1);
        }
        if (offset != end) {
            throw invalid(sourceId, "JPEG", "table", offset);
        }
    }

    private static void validateDht(
            byte[] bytes, int offset, int length, CancellationToken cancellation, String sourceId) {
        if (length == 0) {
            throw invalid(sourceId, "JPEG", "table", offset);
        }
        int end = offset + length;
        while (offset < end) {
            checkpoint(cancellation, sourceId);
            int spec = bytes[offset++] & 0xff;
            if ((spec >>> 4) > 1 || (spec & 15) > 3 || offset + 16 > end) {
                throw invalid(sourceId, "JPEG", "table", offset - 1);
            }
            int symbols = 0;
            for (int index = 0; index < 16; index++) {
                if ((index & 15) == 0) {
                    checkpoint(cancellation, sourceId);
                }
                symbols += bytes[offset++] & 0xff;
            }
            if (symbols > 256 || offset + symbols > end) {
                throw invalid(sourceId, "JPEG", "table", offset);
            }
            offset += symbols;
        }
    }

    private static long filteredBytes(ImageHeader header) {
        try {
            int depth = header.bitsPerSample();
            byte[] snapshot = header.snapshot();
            int channels = physicalChannels(snapshot[25] & 0xff);
            boolean adam7 = snapshot[28] == 1;
            if (!adam7) {
                return Math.multiplyExact(
                        Math.addExact(rowBytesLong(header.width(), channels, depth), 1),
                        header.height());
            }
            long total = 0;
            for (int pass = 0; pass < 7; pass++) {
                int width = span(header.width(), ADAM7_X_START[pass], ADAM7_X_STEP[pass]);
                int height = span(header.height(), ADAM7_Y_START[pass], ADAM7_Y_STEP[pass]);
                if (width > 0) {
                    long passBytes = Math.addExact(rowBytesLong(width, channels, depth), 1);
                    total = Math.addExact(total, Math.multiplyExact(passBytes, height));
                }
            }
            return total;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static int physicalChannels(int color) {
        return switch (color) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw new AssertionError();
        };
    }

    private static int rowBytes(int width, int channels, int depth) {
        return Math.toIntExact(rowBytesLong(width, channels, depth));
    }

    private static long rowBytesLong(int width, int channels, int depth) {
        return ((long) width * channels * depth + 7) / 8;
    }

    private static int span(int extent, int start, int step) {
        return extent <= start ? 0 : (extent - start + step - 1) / step;
    }

    private static String type(byte[] bytes, int offset, String sourceId) {
        for (int index = 0; index < 4; index++) {
            int value = bytes[offset + index] & 0xff;
            if (!((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z'))
                    || (index == 2 && value >= 'a' && value <= 'z')) {
                throw invalid(sourceId, "PNG", "chunkType", offset + index);
            }
        }
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static boolean samplesFit(byte[] bytes, int offset, int depth) {
        int maximum = (1 << depth) - 1;
        return sample(bytes, offset, 2) <= maximum
                && sample(bytes, offset + 2, 2) <= maximum
                && sample(bytes, offset + 4, 2) <= maximum;
    }

    private static int sample(byte[] bytes, int offset, int length) {
        int value = 0;
        for (int index = 0; index < length; index++) {
            value = value << 8 | (bytes[offset + index] & 0xff);
        }
        return value;
    }

    private static long u32(byte[] bytes, int offset) {
        return sample(bytes, offset, 4) & 0xffff_ffffL;
    }

    private static long increment(long count, long maximum, String sourceId) {
        long next = count + 1;
        if (next > maximum) {
            throw ImageDiagnostics.limit(sourceId, "containerElements", next, maximum);
        }
        return next;
    }

    private static void checkpoint(CancellationToken token, String sourceId) {
        ImageHeaderProbe.checkpoint(token, sourceId, "image-container-validation");
    }

    private static SourceException invalid(
            String sourceId, String format, String reason, long offset) {
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_CONTAINER_INVALID",
                "image",
                OptionalLong.of(offset),
                "Encoded image container is invalid",
                Map.of("format", format, "reason", reason),
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
}
