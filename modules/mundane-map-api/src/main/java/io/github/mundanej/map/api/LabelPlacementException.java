package io.github.mundanej.map.api;

import java.util.Objects;

/** Unchecked stable failure produced by bounded label collection, metrics, or placement. */
@SuppressWarnings("serial")
public final class LabelPlacementException extends RuntimeException {
    /** Stable immutable failure detail. */
    private final LabelPlacementProblem problem;

    /**
     * Creates a problem-bearing label failure.
     *
     * @param problem immutable bounded problem
     */
    public LabelPlacementException(LabelPlacementProblem problem) {
        super(Objects.requireNonNull(problem, "problem").message());
        this.problem = problem;
    }

    /**
     * Returns the stable failure detail.
     *
     * @return immutable problem
     */
    public LabelPlacementProblem problem() {
        return problem;
    }
}
