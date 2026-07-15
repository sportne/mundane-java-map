package io.github.mundanej.map.api;

import java.io.InputStream;
import java.util.Objects;

/** Explicit toolkit adapter that decodes one bounded borrowed encoded stream. */
@FunctionalInterface
public interface EncodedRasterDecoder {
    /**
     * Returns whether this decoder implements the requested sampling mode.
     *
     * <p>Existing decoders remain nearest-only unless they explicitly declare otherwise.
     *
     * @param interpolation requested sampling mode
     * @return {@code true} when the mode is implemented
     */
    default boolean supportsInterpolation(RasterInterpolation interpolation) {
        return Objects.requireNonNull(interpolation, "interpolation")
                == RasterInterpolation.NEAREST;
    }

    /**
     * Decodes the requested window without retaining or closing either borrowed argument.
     *
     * @param borrowedInput bounded encoded input owned by the calling source
     * @param context immutable request facts and operation-local accounting
     * @return independently owned exact-size pixels
     */
    RgbaPixelBuffer decode(InputStream borrowedInput, EncodedRasterDecodeContext context);
}
