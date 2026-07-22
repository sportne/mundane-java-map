package io.github.mundanej.map.core;

import java.util.Objects;

/** Indicates a bounded horizontal-wrap planning or translation failure. */
@SuppressWarnings("serial")
public final class HorizontalWrapException extends RuntimeException {
    /** Stable immutable failure detail. */
    private final HorizontalWrapProblem problem;

    /**
     * Creates an exception for a stable problem.
     *
     * @param problem immutable machine-readable failure
     */
    public HorizontalWrapException(HorizontalWrapProblem problem) {
        super(Objects.requireNonNull(problem, "problem").code());
        this.problem = problem;
    }

    /**
     * Returns the stable machine-readable problem.
     *
     * @return immutable bounded problem
     */
    public HorizontalWrapProblem problem() {
        return problem;
    }
}
