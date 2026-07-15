package io.github.mundanej.map.api;

import java.io.InputStream;

/** Explicit toolkit adapter that decodes one bounded borrowed encoded stream. */
@FunctionalInterface
public interface EncodedRasterDecoder {
    /**
     * Decodes the requested window without retaining or closing either borrowed argument.
     *
     * @param borrowedInput bounded encoded input owned by the calling source
     * @param context immutable request facts and operation-local accounting
     * @return independently owned exact-size pixels
     */
    RgbaPixelBuffer decode(InputStream borrowedInput, EncodedRasterDecodeContext context);
}
