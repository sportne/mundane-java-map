package io.github.mundanej.map.core;

import java.util.Objects;

/** Indicates a stable bounded tile-matrix algorithm failure. */
@SuppressWarnings("serial")
public final class TileMatrixException extends RuntimeException {
    /** Stable immutable failure detail. */
    private final TileMatrixProblem problem;

    /**
     * Creates an exception for one immutable problem.
     *
     * @param problem stable failure detail
     */
    public TileMatrixException(TileMatrixProblem problem) {
        super(Objects.requireNonNull(problem, "problem").code());
        this.problem = problem;
    }

    /**
     * Returns the stable failure detail.
     *
     * @return immutable bounded problem
     */
    public TileMatrixProblem problem() {
        return problem;
    }
}
