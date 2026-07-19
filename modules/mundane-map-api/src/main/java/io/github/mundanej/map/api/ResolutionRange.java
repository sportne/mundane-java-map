package io.github.mundanej.map.api;

/**
 * Inclusive positive map-units-per-logical-pixel range.
 *
 * @param minUnitsPerPixelInclusive finite positive inclusive minimum
 * @param maxUnitsPerPixelInclusive finite positive inclusive maximum
 */
public record ResolutionRange(double minUnitsPerPixelInclusive, double maxUnitsPerPixelInclusive) {
    /** All finite positive map resolutions. */
    public static final ResolutionRange ALL =
            new ResolutionRange(Double.MIN_VALUE, Double.MAX_VALUE);

    /** Validates finite positive ordered endpoints. */
    public ResolutionRange {
        if (!Double.isFinite(minUnitsPerPixelInclusive)
                || !Double.isFinite(maxUnitsPerPixelInclusive)
                || minUnitsPerPixelInclusive <= 0.0
                || maxUnitsPerPixelInclusive < minUnitsPerPixelInclusive) {
            throw new IllegalArgumentException(
                    "resolution endpoints must be finite, positive, and ordered");
        }
    }

    /**
     * Returns whether a finite positive resolution is inside this inclusive range.
     *
     * @param unitsPerPixel current map units per logical pixel
     * @return true at and between both endpoints
     */
    public boolean includes(double unitsPerPixel) {
        if (!Double.isFinite(unitsPerPixel) || unitsPerPixel <= 0.0) {
            throw new IllegalArgumentException("unitsPerPixel must be finite and positive");
        }
        return unitsPerPixel >= minUnitsPerPixelInclusive
                && unitsPerPixel <= maxUnitsPerPixelInclusive;
    }
}
