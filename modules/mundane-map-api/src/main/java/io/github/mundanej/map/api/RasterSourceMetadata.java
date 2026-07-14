package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable raster dimensions, placement, and coordinate-reference metadata. */
public record RasterSourceMetadata(
        SourceIdentity identity,
        int width,
        int height,
        Optional<Envelope> mapBounds,
        Optional<CrsMetadata> crs) {
    /** Validates positive dimensions and positive-area optional bounds. */
    public RasterSourceMetadata {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(mapBounds, "mapBounds");
        Objects.requireNonNull(crs, "crs");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Raster dimensions must be positive");
        }
        mapBounds.ifPresent(
                bounds -> {
                    if (bounds.width() <= 0 || bounds.height() <= 0) {
                        throw new IllegalArgumentException(
                                "Raster map bounds must have positive area");
                    }
                });
    }
}
