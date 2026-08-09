package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.streams.DownloadEvent;
import com.vaadin.flow.server.streams.DownloadHandler;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Per-viewer bounded, expiring SVG download owner. */
final class ViewerSvgDownloads implements AutoCloseable {
    static final long LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(5);
    static final int MAXIMUM_BYTES = 16 * 1024 * 1024;

    private final LongSupplier clock;
    private byte[] bytes;
    private long expiresAt;
    private boolean closed;

    ViewerSvgDownloads() {
        this(System::nanoTime);
    }

    ViewerSvgDownloads(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized void publish(byte[] svg) {
        requireOpen();
        Objects.requireNonNull(svg, "svg");
        if (svg.length == 0 || svg.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("SVG download bytes are outside the viewer limit");
        }
        bytes = Arrays.copyOf(svg, svg.length);
        expiresAt = saturatedAdd(clock.getAsLong(), LIFETIME_NANOS);
    }

    synchronized void invalidate() {
        requireOpen();
        bytes = null;
        expiresAt = 0;
    }

    DownloadHandler handler() {
        return new DownloadHandler() {
            @Override
            public void handleDownloadRequest(DownloadEvent event) throws IOException {
                Optional<byte[]> current = current();
                event.getResponse().setHeader("X-Content-Type-Options", "nosniff");
                event.getResponse().setHeader("Cache-Control", "private, no-store");
                event.getResponse().setHeader("Content-Security-Policy", "sandbox");
                if (current.isEmpty()) {
                    event.getResponse().setStatus(HttpStatusCode.GONE.getCode());
                    return;
                }
                byte[] document = current.orElseThrow();
                event.setFileName("mundane-map.svg");
                event.setContentType("image/svg+xml; charset=UTF-8");
                event.setContentLength(document.length);
                event.getOutputStream().write(document);
            }

            @Override
            public String getUrlPostfix() {
                return "mundane-map.svg";
            }
        };
    }

    synchronized Optional<byte[]> current() {
        if (closed || bytes == null || clock.getAsLong() >= expiresAt) {
            bytes = null;
            expiresAt = 0;
            return Optional.empty();
        }
        return Optional.of(Arrays.copyOf(bytes, bytes.length));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        bytes = null;
        expiresAt = 0;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("viewer SVG downloads are closed");
        }
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
