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
 * @param maximumWorldFileBytes world-file byte ceiling
 * @param maximumWorldFileLineBytes world-file content-byte ceiling per line
 */
public record ImageSourceLimits(
        long maximumEncodedBytes,
        long maximumHeaderBytes,
        int maximumWidth,
        int maximumHeight,
        long maximumPixels,
        int maximumLogicalChannels,
        long maximumWorldFileBytes,
        int maximumWorldFileLineBytes) {
    private static final long DEFAULT_ENCODED_BYTES = 33_554_432;
    private static final long DEFAULT_HEADER_BYTES = 1_048_576;
    private static final int DEFAULT_DIMENSION = 16_384;
    private static final long DEFAULT_PIXELS = 16_777_216;
    private static final int DEFAULT_CHANNELS = 4;
    private static final long DEFAULT_WORLD_FILE_BYTES = 4_096;
    private static final int DEFAULT_WORLD_FILE_LINE_BYTES = 256;

    /**
     * Creates limits using the Level 1 world-file defaults.
     *
     * @param maximumEncodedBytes encoded file ceiling
     * @param maximumHeaderBytes JPEG pre-frame header ceiling
     * @param maximumWidth source width ceiling
     * @param maximumHeight source height ceiling
     * @param maximumPixels source pixel ceiling
     * @param maximumLogicalChannels encoded channel/component ceiling
     */
    public ImageSourceLimits(
            long maximumEncodedBytes,
            long maximumHeaderBytes,
            int maximumWidth,
            int maximumHeight,
            long maximumPixels,
            int maximumLogicalChannels) {
        this(
                maximumEncodedBytes,
                maximumHeaderBytes,
                maximumWidth,
                maximumHeight,
                maximumPixels,
                maximumLogicalChannels,
                DEFAULT_WORLD_FILE_BYTES,
                DEFAULT_WORLD_FILE_LINE_BYTES);
    }

    /** Validates positive ceilings. */
    public ImageSourceLimits {
        if (maximumEncodedBytes <= 0
                || maximumHeaderBytes <= 0
                || maximumWidth <= 0
                || maximumHeight <= 0
                || maximumPixels <= 0
                || maximumLogicalChannels <= 0
                || maximumWorldFileBytes <= 0
                || maximumWorldFileLineBytes <= 0) {
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
                DEFAULT_CHANNELS,
                DEFAULT_WORLD_FILE_BYTES,
                DEFAULT_WORLD_FILE_LINE_BYTES);
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
                maximumLogicalChannels,
                maximumWorldFileBytes,
                maximumWorldFileLineBytes);
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
                maximumLogicalChannels,
                maximumWorldFileBytes,
                maximumWorldFileLineBytes);
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
                maximumLogicalChannels,
                maximumWorldFileBytes,
                maximumWorldFileLineBytes);
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
                maximumLogicalChannels,
                maximumWorldFileBytes,
                maximumWorldFileLineBytes);
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
                maximumLogicalChannels,
                maximumWorldFileBytes,
                maximumWorldFileLineBytes);
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
                value,
                maximumWorldFileBytes,
                maximumWorldFileLineBytes);
    }

    /**
     * Replaces the world-file byte ceiling.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied world-file ceiling
     */
    public ImageSourceLimits withMaximumWorldFileBytes(long value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                maximumHeaderBytes,
                maximumWidth,
                maximumHeight,
                maximumPixels,
                maximumLogicalChannels,
                value,
                maximumWorldFileLineBytes);
    }

    /**
     * Replaces the world-file content-byte ceiling per line.
     *
     * @param value replacement positive ceiling
     * @return a copy with the supplied per-line ceiling
     */
    public ImageSourceLimits withMaximumWorldFileLineBytes(int value) {
        return new ImageSourceLimits(
                maximumEncodedBytes,
                maximumHeaderBytes,
                maximumWidth,
                maximumHeight,
                maximumPixels,
                maximumLogicalChannels,
                maximumWorldFileBytes,
                value);
    }
}
