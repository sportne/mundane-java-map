package io.github.mundanej.map.vaadin;

import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.DownloadHandler;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One generation-scoped set of immutable browser raster window resources. */
final class RasterResourceBatch implements AutoCloseable {
    static final int HEADER_BYTES = 32;
    static final int MAX_EDGE = 16_384;
    static final long MAX_PIXELS = 16_777_216;
    static final long MAX_WINDOW_BYTES = 64L * 1024 * 1024;
    static final long MAX_SCENE_RESOURCE_BYTES = 128L * 1024 * 1024;
    static final int MAX_WINDOWS = 4_096;
    static final String CONTENT_TYPE = "application/vnd.mundane-map.rgba-window";
    static final Duration LIFETIME = Duration.ofMinutes(5);

    private final List<Map<String, Object>> encodedWindows;
    private final List<Runnable> unregister;
    private final long encodedBytes;
    private boolean closed;

    private RasterResourceBatch(
            List<Map<String, Object>> encodedWindows,
            List<Runnable> unregister,
            long encodedBytes) {
        this.encodedWindows = List.copyOf(encodedWindows);
        this.unregister = List.copyOf(unregister);
        this.encodedBytes = encodedBytes;
    }

    static RasterResourceBatch empty() {
        return new RasterResourceBatch(List.of(), List.of(), 0);
    }

    static RasterResourceBatch prepare(
            List<BrowserRasterWindow> windows,
            long componentGeneration,
            long sceneGeneration,
            long otherResourceBytes,
            BooleanSupplier authorized,
            Registrar registrar) {
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(authorized, "authorized");
        Objects.requireNonNull(registrar, "registrar");
        if (windows.size() > MAX_WINDOWS) {
            throw limit("rasterWindows", windows.size(), MAX_WINDOWS);
        }
        long bytes = otherResourceBytes;
        IdentityHashMap<RgbaPixelBuffer, Boolean> uniquePixels = new IdentityHashMap<>();
        for (BrowserRasterWindow window : windows) {
            validateWindow(window);
            if (uniquePixels.put(window.pixels(), Boolean.TRUE) == null) {
                bytes = Math.addExact(bytes, window.encodedBytes());
                if (bytes > MAX_SCENE_RESOURCE_BYTES) {
                    throw limit("sceneResourceBytes", bytes, MAX_SCENE_RESOURCE_BYTES);
                }
            }
        }
        List<Map<String, Object>> staged = new ArrayList<>(windows.size());
        List<Runnable> removals = new ArrayList<>(windows.size());
        Instant expiresAt = Instant.now().plus(LIFETIME);
        IdentityHashMap<RgbaPixelBuffer, String> resources = new IdentityHashMap<>();
        try {
            for (BrowserRasterWindow window : windows) {
                String resource = resources.get(window.pixels());
                if (resource == null) {
                    byte[] body = encode(window.pixels(), componentGeneration, sceneGeneration);
                    RegisteredResource registered =
                            registrar.register(new Payload(body, expiresAt, authorized));
                    removals.add(registered.unregister());
                    resource = requireRelativeUri(registered.uri());
                    resources.put(window.pixels(), resource);
                }
                staged.add(window.encode(resource));
            }
            return new RasterResourceBatch(staged, removals, bytes - otherResourceBytes);
        } catch (RuntimeException | Error failure) {
            closeAll(removals, failure);
            throw failure;
        }
    }

    static Registrar vaadin(VaadinSession session, Element owner) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(owner, "owner");
        return payload -> {
            DownloadHandler handler =
                    event ->
                            writeResponse(
                                    payload,
                                    Instant.now(),
                                    new ResourceResponse() {
                                        @Override
                                        public void header(String name, String value) {
                                            event.getResponse().setHeader(name, value);
                                        }

                                        @Override
                                        public void status(int value) {
                                            event.getResponse().setStatus(value);
                                        }

                                        @Override
                                        public void contentType(String value) {
                                            event.getResponse().setContentType(value);
                                        }

                                        @Override
                                        public void contentLength(long value) {
                                            event.getResponse().setContentLengthLong(value);
                                        }

                                        @Override
                                        public OutputStream outputStream() {
                                            return event.getOutputStream();
                                        }
                                    });
            StreamRegistration registration =
                    session.getResourceRegistry().registerResource(handler, owner);
            return new RegisteredResource(
                    registration.getResourceUri().toASCIIString(), registration::unregister);
        };
    }

    List<Map<String, Object>> encodedWindows() {
        if (closed) {
            throw unavailable();
        }
        return encodedWindows;
    }

    long encodedBytes() {
        return encodedBytes;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeAll(unregister, null);
    }

    static byte[] encode(RgbaPixelBuffer pixels, long componentGeneration, long sceneGeneration) {
        Objects.requireNonNull(pixels, "pixels");
        validateDimensions(pixels.width(), pixels.height());
        if (componentGeneration < 0 || sceneGeneration < 0) {
            throw new IllegalArgumentException("resource generations must be non-negative");
        }
        long payloadBytes =
                Math.multiplyExact(Math.multiplyExact((long) pixels.width(), pixels.height()), 4L);
        if (payloadBytes > MAX_WINDOW_BYTES) {
            throw limit("rasterWindowBytes", payloadBytes, MAX_WINDOW_BYTES);
        }
        byte[] encoded = new byte[Math.toIntExact(Math.addExact(HEADER_BYTES, payloadBytes))];
        ByteBuffer header = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        header.put((byte) 'M').put((byte) 'M').put((byte) 'R').put((byte) 'W');
        header.put((byte) 1).put((byte) 0).putShort((short) HEADER_BYTES);
        header.putInt(pixels.width()).putInt(pixels.height());
        header.putLong(componentGeneration).putLong(sceneGeneration);
        int target = HEADER_BYTES;
        for (int pixel : pixels.rgba()) {
            encoded[target++] = (byte) (pixel >>> 24);
            encoded[target++] = (byte) (pixel >>> 16);
            encoded[target++] = (byte) (pixel >>> 8);
            encoded[target++] = (byte) pixel;
        }
        return encoded;
    }

    static void writeResponse(Payload payload, Instant now, ResourceResponse response)
            throws IOException {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(response, "response");
        response.header("X-Content-Type-Options", "nosniff");
        response.header("Cache-Control", "private, no-store");
        response.header("Content-Security-Policy", "default-src 'none'");
        if (!payload.availableAt(now)) {
            response.status(HttpStatusCode.GONE.getCode());
            return;
        }
        byte[] body = payload.body();
        response.contentType(CONTENT_TYPE);
        response.contentLength(body.length);
        response.outputStream().write(body);
    }

    private static void validateWindow(BrowserRasterWindow window) {
        Objects.requireNonNull(window, "window");
        validateDimensions(window.pixels().width(), window.pixels().height());
        if (window.pixelBytes() > MAX_WINDOW_BYTES) {
            throw limit("rasterWindowBytes", window.pixelBytes(), MAX_WINDOW_BYTES);
        }
    }

    private static void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE) {
            throw limit("rasterWindowEdge", Math.max(width, height), MAX_EDGE);
        }
        long pixels = Math.multiplyExact((long) width, height);
        if (pixels > MAX_PIXELS) {
            throw limit("rasterWindowPixels", pixels, MAX_PIXELS);
        }
    }

    private static String requireRelativeUri(String value) {
        Objects.requireNonNull(value, "resource uri");
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw unavailable();
        }
        if (uri.isAbsolute()
                || uri.getHost() != null
                || uri.getRawFragment() != null
                || value.isBlank()
                || value.startsWith("//")
                || value.indexOf('\\') >= 0) {
            throw unavailable();
        }
        String text = uri.toASCIIString();
        return text.startsWith("/") || text.startsWith("./") ? text : "./" + text;
    }

    private static MundaneMapException limit(String name, long actual, long maximum) {
        return new MundaneMapException(
                MundaneMapException.LIMIT_EXCEEDED,
                "Browser raster resource limit exceeded",
                Map.of(
                        "limit", name,
                        "actual", Long.toString(actual),
                        "maximum", Long.toString(maximum)));
    }

    private static MundaneMapException unavailable() {
        return new MundaneMapException(
                MundaneMapException.RESOURCE_UNAVAILABLE,
                "Browser raster resource is unavailable",
                Map.of("resourceKind", "raster-window"));
    }

    private static void closeAll(List<Runnable> removals, Throwable primary) {
        Throwable failure = primary;
        for (int index = removals.size() - 1; index >= 0; index--) {
            try {
                removals.get(index).run();
            } catch (RuntimeException | Error closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (primary == null) {
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }

    @FunctionalInterface
    interface Registrar {
        RegisteredResource register(Payload payload);
    }

    interface ResourceResponse {
        void header(String name, String value);

        void status(int value);

        void contentType(String value);

        void contentLength(long value);

        OutputStream outputStream() throws IOException;
    }

    record RegisteredResource(String uri, Runnable unregister) {
        RegisteredResource {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(unregister, "unregister");
        }
    }

    static final class Payload {
        private final byte[] body;
        private final Instant expiresAt;
        private final BooleanSupplier authorized;

        Payload(byte[] body, Instant expiresAt, BooleanSupplier authorized) {
            this.body = Objects.requireNonNull(body, "body").clone();
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            this.authorized = Objects.requireNonNull(authorized, "authorized");
        }

        byte[] body() {
            return body.clone();
        }

        boolean availableAt(Instant now) {
            return !Objects.requireNonNull(now, "now").isAfter(expiresAt)
                    && authorized.getAsBoolean();
        }
    }
}
