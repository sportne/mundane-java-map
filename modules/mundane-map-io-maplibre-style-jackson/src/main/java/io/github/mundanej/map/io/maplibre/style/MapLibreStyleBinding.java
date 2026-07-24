package io.github.mundanej.map.io.maplibre.style;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transactionally published style binding that borrows every registered source.
 *
 * <p>Closing this binding invalidates its views but never closes caller-owned feature sources.
 */
public final class MapLibreStyleBinding implements AutoCloseable {
    private final List<MapLibreBoundLayer> layers;
    private final AtomicBoolean closed = new AtomicBoolean();

    MapLibreStyleBinding(List<MapLibreBoundLayer> layers) {
        this.layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        this.layers.forEach(layer -> Objects.requireNonNull(layer, "layer"));
    }

    /**
     * Returns every bound layer in style declaration order.
     *
     * @return immutable layers
     */
    public List<MapLibreBoundLayer> layers() {
        requireOpen();
        return layers;
    }

    /**
     * Returns visible and non-degenerate layers active at one zoom, in declaration order.
     *
     * @param zoom finite zoom level
     * @return immutable active layer list
     */
    public List<MapLibreBoundLayer> activeLayers(double zoom) {
        requireOpen();
        return layers.stream()
                .filter(layer -> layer.portrayal().isPresent() && layer.activeAt(zoom))
                .toList();
    }

    /**
     * Returns whether this binding has been explicitly closed.
     *
     * @return close state
     */
    public boolean isClosed() {
        return closed.get();
    }

    /** Invalidates this binding without closing borrowed sources. */
    @Override
    public void close() {
        closed.set(true);
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("binding is closed");
        }
    }
}
