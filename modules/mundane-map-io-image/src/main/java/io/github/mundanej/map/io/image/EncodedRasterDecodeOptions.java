package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterRequestLimits;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable limits and optional signature expectations for one detached image decode.
 *
 * @param expectedFormat optional required PNG or JPEG signature
 * @param expectedWidth optional exact native width, paired with {@code expectedHeight}
 * @param expectedHeight optional exact native height, paired with {@code expectedWidth}
 * @param imageLimits encoded-image header and container limits
 * @param decodeLimits complete decode and output ownership limits
 */
public record EncodedRasterDecodeOptions(
        Optional<EncodedRasterFormat> expectedFormat,
        OptionalInt expectedWidth,
        OptionalInt expectedHeight,
        ImageSourceLimits imageLimits,
        RasterRequestLimits decodeLimits) {
    private static final EncodedRasterDecodeOptions DEFAULTS =
            new EncodedRasterDecodeOptions(
                    Optional.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    ImageSourceLimits.defaults(),
                    RasterRequestLimits.LEVEL_1);

    /** Validates immutable expectations and ceilings. */
    public EncodedRasterDecodeOptions {
        Objects.requireNonNull(expectedFormat, "expectedFormat");
        Objects.requireNonNull(expectedWidth, "expectedWidth");
        Objects.requireNonNull(expectedHeight, "expectedHeight");
        Objects.requireNonNull(imageLimits, "imageLimits");
        Objects.requireNonNull(decodeLimits, "decodeLimits");
        if (expectedWidth.isPresent() != expectedHeight.isPresent()) {
            throw new IllegalArgumentException(
                    "Expected raster width and height must both be present or absent");
        }
        if (expectedWidth.isPresent()) {
            validateExpectedDimensions(
                    expectedWidth.orElseThrow(),
                    expectedHeight.orElseThrow(),
                    imageLimits,
                    decodeLimits);
        }
    }

    /**
     * Returns the shared conservative defaults with no signature or dimension expectation.
     *
     * @return immutable default options
     */
    public static EncodedRasterDecodeOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a copy requiring the supplied encoded signature.
     *
     * @param format required PNG or JPEG signature
     * @return updated options
     */
    public EncodedRasterDecodeOptions expecting(EncodedRasterFormat format) {
        return new EncodedRasterDecodeOptions(
                Optional.of(Objects.requireNonNull(format, "format")),
                expectedWidth,
                expectedHeight,
                imageLimits,
                decodeLimits);
    }

    /**
     * Returns a copy requiring the exact native dimensions.
     *
     * @param width expected positive width
     * @param height expected positive height
     * @return updated options
     */
    public EncodedRasterDecodeOptions expectingDimensions(int width, int height) {
        return new EncodedRasterDecodeOptions(
                expectedFormat,
                OptionalInt.of(width),
                OptionalInt.of(height),
                imageLimits,
                decodeLimits);
    }

    /**
     * Returns a copy with replacement encoded-image limits.
     *
     * @param limits replacement limits
     * @return updated options
     */
    public EncodedRasterDecodeOptions withImageLimits(ImageSourceLimits limits) {
        return new EncodedRasterDecodeOptions(
                expectedFormat,
                expectedWidth,
                expectedHeight,
                Objects.requireNonNull(limits, "limits"),
                decodeLimits);
    }

    /**
     * Returns a copy with replacement decode-operation limits.
     *
     * @param limits replacement limits
     * @return updated options
     */
    public EncodedRasterDecodeOptions withDecodeLimits(RasterRequestLimits limits) {
        return new EncodedRasterDecodeOptions(
                expectedFormat,
                expectedWidth,
                expectedHeight,
                imageLimits,
                Objects.requireNonNull(limits, "limits"));
    }

    private static void validateExpectedDimensions(
            int width,
            int height,
            ImageSourceLimits imageLimits,
            RasterRequestLimits decodeLimits) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Expected raster dimensions must be positive");
        }
        long pixels = Math.multiplyExact((long) width, height);
        if (width > imageLimits.maximumWidth()
                || height > imageLimits.maximumHeight()
                || pixels > imageLimits.maximumPixels()
                || width > decodeLimits.outputDimension()
                || height > decodeLimits.outputDimension()
                || pixels > decodeLimits.sourceWindowPixels()
                || pixels > decodeLimits.outputPixels()
                || pixels > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected raster dimensions exceed decode limits");
        }
    }
}
