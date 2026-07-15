package io.github.mundanej.map.api;

/** Operation-scoped facts and accounting exposed to an explicit encoded-raster decoder. */
public interface EncodedRasterDecodeContext {
    /**
     * Returns the logical source identity.
     *
     * @return the logical source identity
     */
    SourceIdentity sourceIdentity();

    /**
     * Returns the already-probed encoded format.
     *
     * @return the already-probed encoded format
     */
    EncodedRasterFormat format();

    /**
     * Returns the captured hard encoded-input fence.
     *
     * @return the captured hard encoded-input fence
     */
    long encodedByteLength();

    /**
     * Returns the encoded image width.
     *
     * @return the encoded image width
     */
    int width();

    /**
     * Returns the encoded image height.
     *
     * @return the encoded image height
     */
    int height();

    /**
     * Returns the encoded logical channel/component count.
     *
     * @return the encoded logical channel/component count
     */
    int channelCount();

    /**
     * Returns bits per encoded sample.
     *
     * @return bits per encoded sample
     */
    int bitsPerSample();

    /**
     * Returns the strict source window.
     *
     * @return the strict source window
     */
    RasterWindow sourceWindow();

    /**
     * Returns the requested output width.
     *
     * @return the requested output width
     */
    int outputWidth();

    /**
     * Returns the requested output height.
     *
     * @return the requested output height
     */
    int outputHeight();

    /**
     * Returns the requested sampling mode.
     *
     * <p>The default preserves source compatibility for decoders compiled against the original
     * nearest-only contract.
     *
     * @return the requested sampling mode
     */
    default RasterInterpolation interpolation() {
        return RasterInterpolation.NEAREST;
    }

    /** Fails with the source cancellation diagnostic if cancellation was requested. */
    void checkpoint();

    /**
     * Claims part of the source-owned intermediate reservation before allocation.
     *
     * @param bytes positive reserved bytes claimed by the decoder
     */
    void claimReservedIntermediateBytes(long bytes);
}
