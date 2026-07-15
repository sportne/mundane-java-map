package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.EncodedRasterFormat;
import java.util.Objects;

final class ImageHeader {
    private final EncodedRasterFormat format;
    private final int width;
    private final int height;
    private final int channels;
    private final int bitsPerSample;
    private final byte[] snapshot;
    private final long encodedLength;

    ImageHeader(
            EncodedRasterFormat format,
            int width,
            int height,
            int channels,
            int bitsPerSample,
            byte[] snapshot,
            long encodedLength) {
        this.format = Objects.requireNonNull(format, "format");
        this.width = width;
        this.height = height;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot").clone();
        this.encodedLength = encodedLength;
    }

    EncodedRasterFormat format() {
        return format;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int channels() {
        return channels;
    }

    int bitsPerSample() {
        return bitsPerSample;
    }

    byte[] snapshot() {
        return snapshot.clone();
    }

    long encodedLength() {
        return encodedLength;
    }
}
