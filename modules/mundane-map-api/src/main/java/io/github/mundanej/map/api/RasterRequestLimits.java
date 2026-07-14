package io.github.mundanej.map.api;

/** Immutable raster-request ceilings. */
public record RasterRequestLimits(
        long sourceWindowPixels,
        int outputDimension,
        long outputPixels,
        long decodedIntermediateBytes,
        long ownedPayloadBytes,
        int retainedWarnings) {
    /** Level 1 defaults. */
    public static final RasterRequestLimits LEVEL_1 =
            new RasterRequestLimits(67_108_864, 8_192, 16_777_216, 268_435_456, 268_435_456, 256);

    /** Validates positive ceilings. */
    public RasterRequestLimits {
        if (sourceWindowPixels <= 0
                || outputDimension <= 0
                || outputPixels <= 0
                || decodedIntermediateBytes <= 0
                || ownedPayloadBytes <= 0
                || retainedWarnings <= 0) {
            throw new IllegalArgumentException("Raster request limits must be positive");
        }
    }

    /** Returns whether every ceiling is no greater than the parent. */
    public boolean tightens(RasterRequestLimits parent) {
        return sourceWindowPixels <= parent.sourceWindowPixels
                && outputDimension <= parent.outputDimension
                && outputPixels <= parent.outputPixels
                && decodedIntermediateBytes <= parent.decodedIntermediateBytes
                && ownedPayloadBytes <= parent.ownedPayloadBytes
                && retainedWarnings <= parent.retainedWarnings;
    }
}
