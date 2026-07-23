package io.github.mundanej.map.api;

import java.util.OptionalDouble;

/**
 * Immutable caller-supplied portrayal evaluation context.
 *
 * @param scaleDenominator optional finite nonnegative scale denominator
 */
public record PortrayalEvaluationContext(OptionalDouble scaleDenominator) {
    /** Context without scale information. */
    public static final PortrayalEvaluationContext UNSCALED =
            new PortrayalEvaluationContext(OptionalDouble.empty());

    /** Validates the context. */
    public PortrayalEvaluationContext {
        if (scaleDenominator == null) {
            throw new NullPointerException("scaleDenominator");
        }
        scaleDenominator.ifPresent(
                value -> {
                    if (!Double.isFinite(value) || value < 0) {
                        throw new IllegalArgumentException(
                                "scale denominator must be finite and nonnegative");
                    }
                });
    }

    /**
     * Creates a context containing one scale denominator.
     *
     * @param denominator finite nonnegative denominator
     * @return immutable context
     */
    public static PortrayalEvaluationContext atScale(double denominator) {
        return new PortrayalEvaluationContext(OptionalDouble.of(denominator));
    }
}
