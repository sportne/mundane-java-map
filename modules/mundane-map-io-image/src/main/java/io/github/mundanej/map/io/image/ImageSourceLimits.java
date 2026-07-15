package io.github.mundanej.map.io.image;

/**
 * Immutable encoded-image opening ceilings.
 *
 * @param maximumEncodedBytes encoded file ceiling
 * @param maximumHeaderBytes JPEG pre-frame header ceiling
 * @param maximumWidth source width ceiling
 * @param maximumHeight source height ceiling
 * @param maximumPixels source pixel ceiling
 * @param maximumLogicalChannels encoded channel/component ceiling
 */
public record ImageSourceLimits(
        long maximumEncodedBytes,
        long maximumHeaderBytes,
        int maximumWidth,
        int maximumHeight,
        long maximumPixels,
        int maximumLogicalChannels) {
    private static final long DEFAULT_ENCODED_BYTES = 33_554_432;
    private static final long DEFAULT_HEADER_BYTES = 1_048_576;
    private static final int DEFAULT_DIMENSION = 16_384;
    private static final long DEFAULT_PIXELS = 16_777_216;
    private static final int DEFAULT_CHANNELS = 4;

    /** Validates positive ceilings. */
    public ImageSourceLimits {
        if (maximumEncodedBytes <= 0
                || maximumHeaderBytes <= 0
                || maximumWidth <= 0
                || maximumHeight <= 0
                || maximumPixels <= 0
                || maximumLogicalChannels <= 0) {
            throw new IllegalArgumentException("Image source limits must be positive");
        }
    }

    /**
     * Returns Level 1 defaults.
     *
     * @return Level 1 defaults
     */
    public static ImageSourceLimits defaults() {
        return new ImageSourceLimits(
                DEFAULT_ENCODED_BYTES,
                DEFAULT_HEADER_BYTES,
                DEFAULT_DIMENSION,
                DEFAULT_DIMENSION,
                DEFAULT_PIXELS,
                DEFAULT_CHANNELS);
    }

    /**
     * Replaces the encoded-byte ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied encoded-byte ceiling
     */
    public ImageSourceLimits withMaximumEncodedBytes(long value) {
        return new ImageSourceLimits(
                value,
                maximumHeaderBytes,
                maximumWidth,
                maximumHeight,
                maximumPixels,
                maximumLogicalChannels);
    }

    /**
     * Replaces the header-byte ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied header-byte ceiling
     */
    public ImageSourceLimits withMaximumHeaderBytes(long value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                value,
                maximumWidth,
                maximumHeight,
                maximumPixels,
                maximumLogicalChannels);
    }

    /**
     * Replaces the width ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied width ceiling
     */
    public ImageSourceLimits withMaximumWidth(int value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                maximumHeaderBytes,
                value,
                maximumHeight,
                maximumPixels,
                maximumLogicalChannels);
    }

    /**
     * Replaces the height ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied height ceiling
     */
    public ImageSourceLimits withMaximumHeight(int value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                maximumHeaderBytes,
                maximumWidth,
                value,
                maximumPixels,
                maximumLogicalChannels);
    }

    /**
     * Replaces the source-pixel ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied source-pixel ceiling
     */
    public ImageSourceLimits withMaximumPixels(long value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                maximumHeaderBytes,
                maximumWidth,
                maximumHeight,
                value,
                maximumLogicalChannels);
    }

    /**
     * Replaces the logical-channel ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied logical-channel ceiling
     */
    public ImageSourceLimits withMaximumLogicalChannels(int value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                maximumHeaderBytes,
                maximumWidth,
                maximumHeight,
                maximumPixels,
                value);
    }
}
