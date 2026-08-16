package io.github.mundanej.map.api;

/**
 * Safety limits for neutral portrayal expressions.
 *
 * @param maxDepth maximum expression edge depth
 * @param maxNodes maximum nodes in one expression tree
 * @param maxArguments maximum direct arguments to one operation
 */
public record PortrayalExpressionLimits(int maxDepth, int maxNodes, int maxArguments) {
    /** Conservative defaults for untrusted style input. */
    public static final PortrayalExpressionLimits DEFAULT =
            new PortrayalExpressionLimits(32, 1_000, 128);

    /** Creates positive limits with a nonnegative depth. */
    public PortrayalExpressionLimits {
        if (maxDepth < 0 || maxNodes <= 0 || maxArguments <= 0) {
            throw new IllegalArgumentException(
                    "Expression limits must be positive and depth nonnegative");
        }
    }
}
