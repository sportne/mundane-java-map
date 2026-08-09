package io.github.mundanej.map.vaadin;

import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** One staged set of immutable session resources for a single accepted scene. */
final class IconResourceBatch implements AutoCloseable, SceneProtocol.IconResources {
    static final int MAX_RESOURCES = 4_096;
    static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int HEADER_BYTES = 12;

    private final Map<RasterIconSymbol, String> uris;
    private final List<Runnable> unregister;
    private final long encodedBytes;
    private boolean closed;

    private IconResourceBatch(
            Map<RasterIconSymbol, String> uris, List<Runnable> unregister, long encodedBytes) {
        this.uris = uris;
        this.unregister = unregister;
        this.encodedBytes = encodedBytes;
    }

    static IconResourceBatch empty() {
        return new IconResourceBatch(Map.of(), List.of(), 0);
    }

    static IconResourceBatch prepare(
            List<? extends Layer> layers,
            Predicate<RasterIconSymbol> authorized,
            Registrar registrar) {
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(authorized, "authorized");
        Objects.requireNonNull(registrar, "registrar");
        Set<RasterIconSymbol> icons = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Layer layer : layers) {
            for (Feature feature : layer.features()) {
                collect(feature.symbol(), icons, 0);
            }
        }
        if (icons.size() > MAX_RESOURCES) {
            throw limit("iconResources", icons.size(), MAX_RESOURCES);
        }
        long bytes = 0;
        for (RasterIconSymbol icon : icons) {
            if (!authorized.test(icon)) {
                throw SceneProtocol.unsupportedBindingValue("unauthorized raster icon");
            }
            bytes = Math.addExact(bytes, encodedLength(icon));
            if (bytes > MAX_BYTES) {
                throw limit("iconResourceBytes", bytes, MAX_BYTES);
            }
        }
        IdentityHashMap<RasterIconSymbol, String> staged = new IdentityHashMap<>();
        List<Runnable> removals = new ArrayList<>();
        try {
            for (RasterIconSymbol icon : icons) {
                RegisteredResource resource = registrar.register(encode(icon));
                removals.add(resource.unregister());
                String uri = requireRelativeUri(resource.uri());
                staged.put(icon, uri);
            }
            return new IconResourceBatch(
                    Collections.unmodifiableMap(staged), List.copyOf(removals), bytes);
        } catch (RuntimeException | Error failure) {
            closeAll(removals, failure);
            throw failure;
        }
    }

    static Registrar vaadin(VaadinSession session) {
        Objects.requireNonNull(session, "session");
        return bytes -> {
            StreamRegistration registration =
                    session.getResourceRegistry()
                            .registerResource(
                                    DownloadHandler.fromInputStream(
                                            event ->
                                                    new DownloadResponse(
                                                            new ByteArrayInputStream(bytes.clone()),
                                                            "icon.mmri",
                                                            "application/vnd.mundane-map.rgba",
                                                            bytes.length),
                                            "icon.mmri"));
            return new RegisteredResource(
                    registration.getResourceUri().toASCIIString(), registration::unregister);
        };
    }

    @Override
    public String uri(RasterIconSymbol icon) {
        String uri = uris.get(Objects.requireNonNull(icon, "icon"));
        if (uri == null || closed) {
            throw new MundaneMapException(
                    MundaneMapException.RESOURCE_UNAVAILABLE,
                    "Raster icon resource is unavailable",
                    Map.of("resourceKind", "catalog-icon"));
        }
        return uri;
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

    private static void collect(Symbol symbol, Set<RasterIconSymbol> icons, int depth) {
        SceneProtocol.requireSymbolDepth(depth);
        if (symbol instanceof RasterIconSymbol icon) {
            icons.add(icon);
        } else if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                collect(child, icons, depth + 1);
            }
        } else if (symbol instanceof SolidLineSymbol line) {
            line.startMarker().ifPresent(marker -> collect(marker, icons, depth + 1));
            line.endMarker().ifPresent(marker -> collect(marker, icons, depth + 1));
        } else if (symbol instanceof SolidFillSymbol fill) {
            fill.outline().ifPresent(line -> collect(line, icons, depth + 1));
        } else if (symbol instanceof HatchFillSymbol hatch) {
            hatch.outline().ifPresent(line -> collect(line, icons, depth + 1));
        }
    }

    private static long encodedLength(RasterIconSymbol icon) {
        return HEADER_BYTES + Math.multiplyExact((long) icon.width() * icon.height(), 4L);
    }

    private static byte[] encode(RasterIconSymbol icon) {
        byte[] bytes = new byte[Math.toIntExact(encodedLength(icon))];
        bytes[0] = 'M';
        bytes[1] = 'M';
        bytes[2] = 'R';
        bytes[3] = 'I';
        bytes[4] = 1;
        bytes[6] = (byte) (icon.width() >>> 8);
        bytes[7] = (byte) icon.width();
        bytes[8] = (byte) (icon.height() >>> 8);
        bytes[9] = (byte) icon.height();
        int target = HEADER_BYTES;
        for (int pixel : icon.toRgbaArray()) {
            bytes[target++] = (byte) (pixel >>> 24);
            bytes[target++] = (byte) (pixel >>> 16);
            bytes[target++] = (byte) (pixel >>> 8);
            bytes[target++] = (byte) pixel;
        }
        return bytes;
    }

    private static String requireRelativeUri(String value) {
        Objects.requireNonNull(value, "resource uri");
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new MundaneMapException(
                    MundaneMapException.RESOURCE_UNAVAILABLE,
                    "Raster icon resource URI is invalid",
                    Map.of("resourceKind", "catalog-icon"));
        }
        if (uri.isAbsolute() || uri.getHost() != null || uri.getRawFragment() != null) {
            throw new MundaneMapException(
                    MundaneMapException.RESOURCE_UNAVAILABLE,
                    "Raster icon resource URI is not same-origin relative",
                    Map.of("resourceKind", "catalog-icon"));
        }
        String text = uri.toASCIIString();
        if (text.isBlank() || text.startsWith("//") || text.indexOf('\\') >= 0) {
            throw new MundaneMapException(
                    MundaneMapException.RESOURCE_UNAVAILABLE,
                    "Raster icon resource URI is not same-origin relative",
                    Map.of("resourceKind", "catalog-icon"));
        }
        return text.startsWith("/") || text.startsWith("./") ? text : "./" + text;
    }

    private static MundaneMapException limit(String name, long actual, long maximum) {
        return new MundaneMapException(
                MundaneMapException.LIMIT_EXCEEDED,
                "Browser icon resource limit exceeded",
                Map.of(
                        "limit", name,
                        "actual", Long.toString(actual),
                        "maximum", Long.toString(maximum)));
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
        RegisteredResource register(byte[] bytes);
    }

    record RegisteredResource(String uri, Runnable unregister) {
        RegisteredResource {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(unregister, "unregister");
        }
    }
}
