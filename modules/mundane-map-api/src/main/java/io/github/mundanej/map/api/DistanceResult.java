package io.github.mundanej.map.api;

import java.util.Objects;

/** Immutable non-negative distance measured in metres. */
public record DistanceResult(double metres) {
    /** Shared zero-distance value. */
    public static final DistanceResult ZERO = new DistanceResult(0.0);

    /** Validates and canonicalizes the metre value. */
    public DistanceResult {
        if (!Double.isFinite(metres) || metres < 0.0) {
            throw new IllegalArgumentException("metres must be finite and non-negative");
        }
        if (metres == 0.0) {
            metres = 0.0;
        }
    }

    /** Returns the checked sum in encounter order. */
    public DistanceResult plus(DistanceResult other) {
        Objects.requireNonNull(other, "other");
        double sum = metres + other.metres;
        if (!Double.isFinite(sum)) {
            throw new ArithmeticException("distance sum is not finite");
        }
        return new DistanceResult(sum);
    }
}
