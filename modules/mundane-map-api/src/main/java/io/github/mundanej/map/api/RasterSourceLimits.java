package io.github.mundanej.map.api;

import java.util.Objects;

/** Captured raster-source request ceilings. */
public record RasterSourceLimits(RasterRequestLimits requestLimits) {
    /** Level 1 defaults. */
    public static final RasterSourceLimits LEVEL_1 =
            new RasterSourceLimits(RasterRequestLimits.LEVEL_1);

    /** Validates limits. */
    public RasterSourceLimits {
        Objects.requireNonNull(requestLimits, "requestLimits");
    }
}
