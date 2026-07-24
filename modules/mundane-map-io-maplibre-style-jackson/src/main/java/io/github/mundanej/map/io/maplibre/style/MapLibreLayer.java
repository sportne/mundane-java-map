package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.FeaturePortrayal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable declaration-ordered vector or symbol layer.
 *
 * @param id exact unique layer identifier
 * @param source exact detached source identifier
 * @param type supported layer type
 * @param visible whether layout visibility is visible
 * @param minimumZoom inclusive minimum zoom
 * @param maximumZoom exclusive maximum zoom
 * @param metadata retained bounded scalar metadata
 * @param portrayal detached portrayal, empty for an invisible or degenerate layer
 */
public record MapLibreLayer(
        String id,
        String source,
        MapLibreLayerType type,
        boolean visible,
        double minimumZoom,
        double maximumZoom,
        Map<String, Object> metadata,
        Optional<FeaturePortrayal> portrayal) {
    /** Validates and defensively copies a layer. */
    public MapLibreLayer {
        id = requireText(id, "id");
        source = requireText(source, "source");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(minimumZoom)
                || !Double.isFinite(maximumZoom)
                || minimumZoom < 0.0
                || maximumZoom > 24.0
                || minimumZoom >= maximumZoom) {
            throw new IllegalArgumentException("zoom range is invalid");
        }
        metadata = MapLibreValues.metadata(metadata, "metadata");
        Objects.requireNonNull(portrayal, "portrayal");
        portrayal = portrayal.map(Objects::requireNonNull);
        if (!visible && portrayal.isPresent()) {
            throw new IllegalArgumentException("invisible layer cannot retain a portrayal");
        }
    }

    /**
     * Returns a defensive immutable layer metadata copy.
     *
     * @return scalar metadata
     */
    @Override
    public Map<String, Object> metadata() {
        return Map.copyOf(metadata);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > 1_048_576) {
            throw new IllegalArgumentException(name + " is outside the bounded profile");
        }
        return value;
    }
}
