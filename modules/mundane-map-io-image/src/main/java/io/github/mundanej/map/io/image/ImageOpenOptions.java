package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.RasterSourceLimits;
import java.util.Objects;

/**
 * Immutable image-source opening options.
 *
 * <p>The cache policy is captured by each opened source. It retains only source-owned successful
 * decoded/resampled RGBA values; callers always receive independent buffers. Changing options after
 * construction is impossible, and reopening is required to accept changed encoded content.
 *
 * @param imageLimits encoded input ceilings
 * @param requestLimits raster request ceilings
 * @param placement explicit map placement
 * @param cachePolicy source-owned decoded-result cache policy
 */
public record ImageOpenOptions(
        ImageSourceLimits imageLimits,
        RasterSourceLimits requestLimits,
        ImagePlacement placement,
        ImageCachePolicy cachePolicy) {
    /**
     * Creates options with the Level 1 default cache policy.
     *
     * @param imageLimits encoded input ceilings
     * @param requestLimits raster request ceilings
     * @param placement explicit map placement
     */
    public ImageOpenOptions(
            ImageSourceLimits imageLimits,
            RasterSourceLimits requestLimits,
            ImagePlacement placement) {
        this(imageLimits, requestLimits, placement, ImageCachePolicy.defaults());
    }

    /** Validates all options. */
    public ImageOpenOptions {
        Objects.requireNonNull(imageLimits, "imageLimits");
        Objects.requireNonNull(requestLimits, "requestLimits");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(cachePolicy, "cachePolicy");
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
                ImagePlacement.unplaced(),
                ImageCachePolicy.defaults());
    }

    /**
     * Replaces the image-open limits.
     *
     * @param limits replacement image-open limits
     * @return the updated immutable options
     */
    public ImageOpenOptions withImageLimits(ImageSourceLimits limits) {
        return new ImageOpenOptions(limits, requestLimits, placement, cachePolicy);
    }

    /**
     * Replaces the raster-request limits.
     *
     * @param limits replacement raster-request limits
     * @return the updated immutable options
     */
    public ImageOpenOptions withRequestLimits(RasterSourceLimits limits) {
        return new ImageOpenOptions(imageLimits, limits, placement, cachePolicy);
    }

    /**
     * Replaces the placement.
     *
     * @param value replacement placement
     * @return the updated immutable options
     */
    public ImageOpenOptions withPlacement(ImagePlacement value) {
        return new ImageOpenOptions(imageLimits, requestLimits, value, cachePolicy);
    }

    /**
     * Replaces the source-owned decoded-result cache policy.
     *
     * @param value replacement cache policy
     * @return the updated immutable options
     */
    public ImageOpenOptions withCachePolicy(ImageCachePolicy value) {
        return new ImageOpenOptions(imageLimits, requestLimits, placement, value);
    }
}
