package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministically chunked UTF-8 sink used by one SVG encode operation. */
final class SvgByteSink {
    private static final int CHUNK_BYTES = 8_192;

    private final int maximumOutputBytes;
    private final long maximumOwnedBytes;
    private final CancellationToken cancellation;
    private final List<byte[]> chunks = new ArrayList<>();
    private long ownedBytes;
    private int size;
    private int allocatedCapacity;

    SvgByteSink(
            int maximumOutputBytes,
            long maximumOwnedBytes,
            long initialOwnedBytes,
            CancellationToken cancellation) {
        this.maximumOutputBytes = maximumOutputBytes;
        this.maximumOwnedBytes = maximumOwnedBytes;
        this.cancellation = cancellation;
        ownedBytes = initialOwnedBytes;
    }

    void begin() {
        ownedLimit(ownedBytes);
    }

    void append(CharSequence text) {
        append(text, 0, text.length());
    }

    void append(CharSequence text, int startInclusive, int endExclusive) {
        for (int offset = startInclusive; offset < endExclusive; ) {
            int codePoint = Character.codePointAt(text, offset);
            int length = utf8Length(codePoint);
            outputLimit(add(size, length));
            ensureCapacity(length);
            writeCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
    }

    void appendAscii(char value) {
        if (value > 0x7f) {
            throw new IllegalArgumentException("ASCII output character is out of range");
        }
        outputLimit(add(size, 1));
        ensureCapacity(1);
        write(value);
    }

    void charge(long bytes) {
        long requested = add(ownedBytes, bytes);
        ownedLimit(requested);
        ownedBytes = requested;
    }

    byte[] finish() {
        checkCancelled();
        long requested = add(ownedBytes, size);
        ownedLimit(requested);
        checkCancelled();
        byte[] result = new byte[size];
        int copied = 0;
        for (byte[] chunk : chunks) {
            int length = Math.min(chunk.length, size - copied);
            if (length == 0) {
                break;
            }
            System.arraycopy(chunk, 0, result, copied, length);
            copied += length;
        }
        ownedBytes = requested;
        checkCancelled();
        return result;
    }

    private void ensureCapacity(int required) {
        if (size + required <= allocatedCapacity) {
            return;
        }
        checkCancelled();
        int capacity = Math.min(CHUNK_BYTES, maximumOutputBytes - allocatedCapacity);
        if (capacity <= 0) {
            outputLimit(Long.MAX_VALUE);
        }
        long requested = add(ownedBytes, capacity);
        ownedLimit(requested);
        checkCancelled();
        chunks.add(new byte[capacity]);
        allocatedCapacity += capacity;
        ownedBytes = requested;
    }

    private void writeCodePoint(int codePoint) {
        if (codePoint <= 0x7f) {
            write(codePoint);
        } else if (codePoint <= 0x7ff) {
            write(0xc0 | (codePoint >>> 6));
            write(0x80 | (codePoint & 0x3f));
        } else if (codePoint <= 0xffff) {
            write(0xe0 | (codePoint >>> 12));
            write(0x80 | ((codePoint >>> 6) & 0x3f));
            write(0x80 | (codePoint & 0x3f));
        } else {
            write(0xf0 | (codePoint >>> 18));
            write(0x80 | ((codePoint >>> 12) & 0x3f));
            write(0x80 | ((codePoint >>> 6) & 0x3f));
            write(0x80 | (codePoint & 0x3f));
        }
    }

    private void write(int value) {
        int chunkIndex = size / CHUNK_BYTES;
        int offset = size % CHUNK_BYTES;
        chunks.get(chunkIndex)[offset] = (byte) value;
        size++;
    }

    private void outputLimit(long requested) {
        if (requested > maximumOutputBytes) {
            throw limit("outputBytes", maximumOutputBytes, requested);
        }
    }

    private void ownedLimit(long requested) {
        if (requested > maximumOwnedBytes) {
            throw limit("ownedBytes", maximumOwnedBytes, requested);
        }
    }

    private static SvgExportException limit(String name, long maximum, long requested) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("scope", "writer");
        context.put("limit", name);
        context.put("maximum", Long.toString(maximum));
        context.put("requested", Long.toString(requested));
        return new SvgExportException(
                "An SVG export limit was exceeded",
                new SvgExportProblem("SVG_EXPORT_LIMIT_EXCEEDED", context));
    }

    private void checkCancelled() {
        if (cancellation.isCancellationRequested()) {
            throw new SvgExportException(
                    "SVG export was cancelled",
                    new SvgExportProblem("SVG_EXPORT_CANCELLED", Map.of()));
        }
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint <= 0xffff) {
            return 3;
        }
        return 4;
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
