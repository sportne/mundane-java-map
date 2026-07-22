package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.MapViewport;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Coalescing one-image backing store that renders an overscanned static map away from the EDT. */
final class StaticMapBackgroundCache implements AutoCloseable {
    static final int OVERSCAN_FACTOR = 2;
    private static final int MAXIMUM_RENDER_AXIS = 4_096;

    private final Renderer renderer;
    private final Consumer<Snapshot> publisher;
    private final Consumer<Throwable> failureSink;
    private final ExecutorService executor;
    private Request pending;
    private boolean workerScheduled;
    private boolean closed;

    StaticMapBackgroundCache(
            Renderer renderer, Consumer<Snapshot> publisher, Consumer<Throwable> failureSink) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.failureSink = Objects.requireNonNull(failureSink, "failureSink");
        executor =
                Executors.newSingleThreadExecutor(
                        Thread.ofPlatform()
                                .daemon()
                                .name("live-track-static-map-renderer")
                                .factory());
    }

    void request(MapViewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        Request request = new Request(overscanned(viewport));
        synchronized (this) {
            requireOpen();
            pending = request;
            if (workerScheduled) {
                return;
            }
            workerScheduled = true;
        }
        executor.execute(this::drain);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = null;
        }
        executor.shutdownNow();
    }

    static boolean covers(MapViewport rendered, MapViewport requested) {
        Objects.requireNonNull(rendered, "rendered");
        Objects.requireNonNull(requested, "requested");
        if (Double.compare(rendered.worldUnitsPerPixel(), requested.worldUnitsPerPixel()) != 0) {
            return false;
        }
        Envelope outer = rendered.visibleWorldEnvelope();
        Envelope inner = requested.visibleWorldEnvelope();
        return inner.minX() >= outer.minX()
                && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY()
                && inner.maxY() <= outer.maxY();
    }

    private void drain() {
        while (true) {
            Request request;
            synchronized (this) {
                if (closed) {
                    workerScheduled = false;
                    return;
                }
                request = pending;
                pending = null;
                if (request == null) {
                    workerScheduled = false;
                    return;
                }
            }
            try {
                long started = System.nanoTime();
                BufferedImage image = renderer.render(request.renderViewport());
                Snapshot snapshot =
                        new Snapshot(request.renderViewport(), image, System.nanoTime() - started);
                synchronized (this) {
                    if (pending != null
                            && covers(snapshot.renderViewport(), pending.renderViewport())) {
                        pending = null;
                    }
                    if (closed) {
                        return;
                    }
                }
                EventQueue.invokeLater(() -> publish(snapshot));
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    if (closed) {
                        return;
                    }
                }
                EventQueue.invokeLater(() -> failureSink.accept(failure));
            }
        }
    }

    private void publish(Snapshot snapshot) {
        synchronized (this) {
            if (closed) {
                return;
            }
        }
        publisher.accept(snapshot);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("static map background cache is closed");
        }
    }

    private static MapViewport overscanned(MapViewport viewport) {
        int width = overscanAxis(viewport.width());
        int height = overscanAxis(viewport.height());
        return new MapViewport(
                width,
                height,
                viewport.centerX(),
                viewport.centerY(),
                viewport.worldUnitsPerPixel());
    }

    private static int overscanAxis(int value) {
        return value <= MAXIMUM_RENDER_AXIS / OVERSCAN_FACTOR ? value * OVERSCAN_FACTOR : value;
    }

    @FunctionalInterface
    interface Renderer {
        BufferedImage render(MapViewport viewport);
    }

    record Snapshot(MapViewport renderViewport, BufferedImage image, long renderNanos) {
        Snapshot {
            Objects.requireNonNull(renderViewport, "renderViewport");
            Objects.requireNonNull(image, "image");
            if (image.getWidth() != renderViewport.width()
                    || image.getHeight() != renderViewport.height()
                    || renderNanos < 0L) {
                throw new IllegalArgumentException("invalid static background snapshot");
            }
        }
    }

    private record Request(MapViewport renderViewport) {}
}
