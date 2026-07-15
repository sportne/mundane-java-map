package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable unplaced or axis-aligned image placement.
 *
 * @param mapBounds optional explicit map bounds
 * @param crs optional declared coordinate-reference metadata
 */
public record ImagePlacement(Optional<Envelope> mapBounds, Optional<CrsMetadata> crs) {
    /** Validates consistent positive-area placement. */
    public ImagePlacement {
        Objects.requireNonNull(mapBounds, "mapBounds");
        Objects.requireNonNull(crs, "crs");
        if (mapBounds.isEmpty() && crs.isPresent()) {
            throw new IllegalArgumentException("An unplaced image cannot declare a CRS");
        }
        mapBounds.ifPresent(
                bounds -> {
                    if (bounds.width() <= 0 || bounds.height() <= 0) {
                        throw new IllegalArgumentException("Image bounds must have positive area");
                    }
                });
    }

    /**
     * Returns an image with no map placement.
     *
     * @return an image with no map placement
     */
    public static ImagePlacement unplaced() {
        return new ImagePlacement(Optional.empty(), Optional.empty());
    }

    /**
     * Creates an axis-aligned placement with optional CRS metadata.
     *
     * @param mapBounds positive-area map bounds
     * @param crs optional declared coordinate-reference metadata
     * @return the axis-aligned placement
     */
    public static ImagePlacement axisAligned(Envelope mapBounds, Optional<CrsMetadata> crs) {
        return new ImagePlacement(
                Optional.of(Objects.requireNonNull(mapBounds, "mapBounds")),
                Objects.requireNonNull(crs, "crs"));
    }

    /**
     * Creates an axis-aligned placement with declared CRS metadata.
     *
     * @param mapBounds positive-area map bounds
     * @param crs declared coordinate-reference metadata
     * @return the axis-aligned placement
     */
    public static ImagePlacement axisAligned(Envelope mapBounds, CrsMetadata crs) {
        return axisAligned(mapBounds, Optional.of(Objects.requireNonNull(crs, "crs")));
    }
}
