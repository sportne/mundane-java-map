package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit immutable same-CRS snap reference snapshot.
 *
 * @param crs exact CRS shared by every captured geometry
 * @param layers layers ordered from lower to higher snap priority
 */
public record SnapReferenceSet(CrsDefinition crs, List<SnapReferenceLayer> layers) {
    /** Defensively copies values and rejects ambiguous duplicate layer identities. */
    public SnapReferenceSet {
        Objects.requireNonNull(crs, "crs");
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        Set<String> identities = new HashSet<>();
        for (SnapReferenceLayer layer : layers) {
            Objects.requireNonNull(layer, "layer");
            if (!identities.add(layer.layerId())) {
                throw new IllegalArgumentException("snap layer identities must be unique");
            }
        }
    }
}
