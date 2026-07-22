package io.github.mundanej.map.core;

/**
 * A non-empty half-open canonical horizontal interval.
 *
 * @param minimumX finite inclusive canonical minimum
 * @param maximumX finite exclusive canonical maximum
 */
public record HorizontalInterval(double minimumX, double maximumX) {
    /** Requires finite strictly ordered endpoints. */
    public HorizontalInterval {
        if (!Double.isFinite(minimumX) || !Double.isFinite(maximumX) || minimumX >= maximumX) {
            throw new IllegalArgumentException("Horizontal interval must be finite and non-empty");
        }
    }
}
