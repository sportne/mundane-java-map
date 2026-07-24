package io.github.mundanej.map.io.maplibre.style;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached immutable result of one bounded MapLibre Style v8 read.
 *
 * @param name optional retained name
 * @param metadata retained root scalar metadata
 * @param camera retained camera metadata that is never applied automatically
 * @param sources declaration-ordered detached sources
 * @param layers declaration-ordered layers in bottom-to-top paint order
 */
public record MapLibreStyle(
        Optional<String> name,
        Map<String, Object> metadata,
        MapLibreCamera camera,
        List<MapLibreSourceDescriptor> sources,
        List<MapLibreLayer> layers) {
    /** Validates and defensively copies style state. */
    public MapLibreStyle {
        Objects.requireNonNull(name, "name");
        name = name.map(value -> MapLibreValues.retained(value, "name"));
        metadata = MapLibreValues.metadata(metadata, "metadata");
        Objects.requireNonNull(camera, "camera");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        sources.forEach(source -> Objects.requireNonNull(source, "source"));
        layers.forEach(layer -> Objects.requireNonNull(layer, "layer"));
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("layers must not be empty");
        }
        requireUniqueSources(sources);
        requireUniqueLayers(layers);
    }

    /**
     * Returns a defensive immutable root metadata copy.
     *
     * @return scalar metadata
     */
    @Override
    public Map<String, Object> metadata() {
        return Map.copyOf(metadata);
    }

    private static void requireUniqueSources(List<MapLibreSourceDescriptor> sources) {
        LinkedHashMap<String, Boolean> ids = new LinkedHashMap<>();
        for (MapLibreSourceDescriptor source : sources) {
            if (ids.put(source.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("source ids must be unique");
            }
        }
    }

    private static void requireUniqueLayers(List<MapLibreLayer> layers) {
        LinkedHashMap<String, Boolean> ids = new LinkedHashMap<>();
        for (MapLibreLayer layer : layers) {
            if (ids.put(layer.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("layer ids must be unique");
            }
        }
    }
}
