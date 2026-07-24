package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit all-or-nothing binder for detached styles and caller-owned feature sources. */
public final class MapLibreStyleBinder {
    private MapLibreStyleBinder() {}

    /**
     * Resolves every layer source before publishing an immutable borrowed binding.
     *
     * @param style detached style
     * @param registry exact explicit source registry
     * @return declaration-ordered binding
     * @throws MapLibreBindException when a source is missing or closed
     */
    public static MapLibreStyleBinding bind(MapLibreStyle style, MapLibreSourceRegistry registry) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(registry, "registry");
        List<IndexedLayer> candidates =
                java.util.stream.IntStream.range(0, style.layers().size())
                        .mapToObj(index -> new IndexedLayer(index, style.layers().get(index)))
                        .filter(indexed -> indexed.layer().portrayal().isPresent())
                        .toList();
        ArrayList<FeatureSource> resolved = new ArrayList<>(candidates.size());
        for (IndexedLayer indexed : candidates) {
            MapLibreLayer layer = indexed.layer();
            int index = indexed.index();
            String location = "/layers/" + index + "/source";
            FeatureSource source =
                    registry.find(layer.source())
                            .orElseThrow(
                                    () ->
                                            failure(
                                                    "MAPLIBRE_SOURCE_UNRESOLVED",
                                                    location,
                                                    "missing"));
            if (source.isClosed()) {
                throw failure("MAPLIBRE_SOURCE_UNRESOLVED", location, "closed");
            }
            resolved.add(source);
        }
        ArrayList<MapLibreBoundLayer> bound = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            MapLibreLayer layer = candidates.get(index).layer();
            Optional<FeaturePortrayal> portrayal = layer.portrayal();
            AttributeSelection attributes =
                    portrayal
                            .map(FeaturePortrayalResolver::compile)
                            .map(FeaturePortrayalResolver::requiredConfigurationAttributes)
                            .filter(names -> !names.isEmpty())
                            .map(AttributeSelection::only)
                            .orElse(AttributeSelection.NONE);
            bound.add(
                    new MapLibreBoundLayer(
                            layer.id(),
                            resolved.get(index),
                            portrayal,
                            attributes,
                            layer.minimumZoom(),
                            layer.maximumZoom()));
        }
        return new MapLibreStyleBinding(bound);
    }

    private static MapLibreBindException failure(String code, String location, String reason) {
        return new MapLibreBindException(
                new MapLibreProblem(code, "bind", location, Map.of("reason", reason)));
    }

    private record IndexedLayer(int index, MapLibreLayer layer) {}
}
