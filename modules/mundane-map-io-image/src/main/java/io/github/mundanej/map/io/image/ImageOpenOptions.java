package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.RasterSourceLimits;
import java.util.Objects;

/**
 * Immutable image-source opening options.
 *
 * @param imageLimits encoded input ceilings
 * @param requestLimits raster request ceilings
 * @param placement explicit map placement
 */
public record ImageOpenOptions(
        ImageSourceLimits imageLimits, RasterSourceLimits requestLimits, ImagePlacement placement) {
    /** Validates all options. */
    public ImageOpenOptions {
        Objects.requireNonNull(imageLimits, "imageLimits");
        Objects.requireNonNull(requestLimits, "requestLimits");
        Objects.requireNonNull(placement, "placement");
    }

    /**
     * Returns Level 1 defaults with no map placement.
     *
     * @return Level 1 defaults with no map placement
     */
    public static ImageOpenOptions defaults() {
        return new ImageOpenOptions(
                ImageSourceLimits.defaults(),
                RasterSourceLimits.LEVEL_1,
                ImagePlacement.unplaced());
    }

    /**
     * Replaces the image-open limits.
     *
     * @param limits replacement image-open limits
     * @return the updated immutable options
     */
    public ImageOpenOptions withImageLimits(ImageSourceLimits limits) {
        return new ImageOpenOptions(limits, requestLimits, placement);
    }

    /**
     * Replaces the raster-request limits.
     *
     * @param limits replacement raster-request limits
     * @return the updated immutable options
     */
    public ImageOpenOptions withRequestLimits(RasterSourceLimits limits) {
        return new ImageOpenOptions(imageLimits, limits, placement);
    }

    /**
     * Replaces the placement.
     *
     * @param value replacement placement
     * @return the updated immutable options
     */
    public ImageOpenOptions withPlacement(ImagePlacement value) {
        return new ImageOpenOptions(imageLimits, requestLimits, value);
    }
}
