package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.RasterRequestAccounting;
import java.util.Objects;

final class ImageDecodeContext implements EncodedRasterDecodeContext {
    private final SourceIdentity identity;
    private final ImageHeader header;
    private final RasterWindow window;
    private final int outputWidth;
    private final int outputHeight;
    private final RasterRequestAccounting accounting;
    private final long reservation;
    private long claimed;

    ImageDecodeContext(
            SourceIdentity identity,
            ImageHeader header,
            RasterWindow window,
            int outputWidth,
            int outputHeight,
            RasterRequestAccounting accounting,
            long reservation) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.header = Objects.requireNonNull(header, "header");
        this.window = Objects.requireNonNull(window, "window");
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.accounting = Objects.requireNonNull(accounting, "accounting");
        this.reservation = reservation;
    }

    @Override
    public SourceIdentity sourceIdentity() {
        return identity;
    }

    @Override
    public EncodedRasterFormat format() {
        return header.format();
    }

    @Override
    public long encodedByteLength() {
        return header.encodedLength();
    }

    @Override
    public int width() {
        return header.width();
    }

    @Override
    public int height() {
        return header.height();
    }

    @Override
    public int channelCount() {
        return header.channels();
    }

    @Override
    public int bitsPerSample() {
        return header.bitsPerSample();
    }

    @Override
    public RasterWindow sourceWindow() {
        return window;
    }

    @Override
    public int outputWidth() {
        return outputWidth;
    }

    @Override
    public int outputHeight() {
        return outputHeight;
    }

    @Override
    public void checkpoint() {
        accounting.checkpoint();
    }

    @Override
    public void claimReservedIntermediateBytes(long bytes) {
        if (bytes <= 0) {
            throw new IllegalStateException("Decoder claims must be positive");
        }
        long next;
        try {
            next = Math.addExact(claimed, bytes);
        } catch (ArithmeticException failure) {
            throw new IllegalStateException("Decoder over-claimed its reservation", failure);
        }
        if (next > reservation) {
            throw new IllegalStateException("Decoder over-claimed its reservation");
        }
        claimed = next;
    }

    void requireFullyClaimed() {
        if (claimed != reservation) {
            throw new IllegalStateException("Decoder did not claim its complete reservation");
        }
    }
}
