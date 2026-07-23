package io.github.mundanej.map.api;

import java.util.OptionalDouble;

/**
 * Immutable lower-inclusive and upper-exclusive scale-denominator interval.
 *
 * @param minimumInclusive optional finite nonnegative minimum
 * @param maximumExclusive optional finite nonnegative maximum
 */
public record ScaleInterval(OptionalDouble minimumInclusive, OptionalDouble maximumExclusive) {
    /** Unconstrained interval. */
    public static final ScaleInterval ALL =
            new ScaleInterval(OptionalDouble.empty(), OptionalDouble.empty());

    /** Validates the interval. */
    public ScaleInterval {
        minimumInclusive = copy(minimumInclusive, "minimumInclusive");
        maximumExclusive = copy(maximumExclusive, "maximumExclusive");
        if (minimumInclusive.isPresent()
                && maximumExclusive.isPresent()
                && minimumInclusive.getAsDouble() >= maximumExclusive.getAsDouble()) {
            throw new IllegalArgumentException("minimum must be less than maximum");
        }
    }

    /**
     * Returns whether the supplied denominator is within the interval.
     *
     * @param denominator finite nonnegative scale denominator
     * @return true when active
     */
    public boolean includes(double denominator) {
        requireScale(denominator);
        return (minimumInclusive.isEmpty() || denominator >= minimumInclusive.getAsDouble())
                && (maximumExclusive.isEmpty() || denominator < maximumExclusive.getAsDouble());
    }

    /**
     * Returns whether this interval has any scale constraint.
     *
     * @return true when bounded on either side
     */
    public boolean constrained() {
        return minimumInclusive.isPresent() || maximumExclusive.isPresent();
    }

    private static OptionalDouble copy(OptionalDouble value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        value.ifPresent(ScaleInterval::requireScale);
        return value;
    }

    private static void requireScale(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("scale denominator must be finite and nonnegative");
        }
    }
}
