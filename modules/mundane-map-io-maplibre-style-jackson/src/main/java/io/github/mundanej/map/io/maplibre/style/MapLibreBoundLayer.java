package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureSource;
import java.util.Objects;
import java.util.Optional;

/**
 * One declaration-ordered layer bound to a borrowed caller source.
 *
 * @param id exact style layer identifier
 * @param source borrowed caller-owned feature source
 * @param portrayal optional filtered portrayal
 * @param queryAttributes exact required attribute projection
 * @param minimumZoom inclusive minimum zoom
 * @param maximumZoom exclusive maximum zoom
 */
public record MapLibreBoundLayer(
        String id,
        FeatureSource source,
        Optional<FeaturePortrayal> portrayal,
        AttributeSelection queryAttributes,
        double minimumZoom,
        double maximumZoom) {
    /** Validates immutable binding state. */
    public MapLibreBoundLayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(portrayal, "portrayal");
        portrayal = portrayal.map(Objects::requireNonNull);
        Objects.requireNonNull(queryAttributes, "queryAttributes");
        if (id.isBlank()
                || !id.equals(id.strip())
                || id.length() > 1_048_576
                || source.isClosed()
                || !Double.isFinite(minimumZoom)
                || !Double.isFinite(maximumZoom)
                || minimumZoom < 0.0
                || maximumZoom > 24.0
                || minimumZoom >= maximumZoom) {
            throw new IllegalArgumentException("invalid bound layer");
        }
    }

    /**
     * Returns whether this layer is active at one finite zoom.
     *
     * @param zoom finite zoom level
     * @return true at the lower-inclusive, upper-exclusive interval
     */
    public boolean activeAt(double zoom) {
        if (!Double.isFinite(zoom)) {
            throw new IllegalArgumentException("zoom must be finite");
        }
        return zoom >= minimumZoom && zoom < maximumZoom;
    }
}
