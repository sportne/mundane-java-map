package io.github.mundanej.map.api;

import java.util.Objects;

/** Unchecked stable failure at a coordinate-reference boundary. */
@SuppressWarnings("serial")
public final class CrsException extends RuntimeException {
    private final CrsProblem problem;

    /** Creates an exception carrying one bounded problem. */
    public CrsException(CrsProblem problem) {
        super(Objects.requireNonNull(problem, "problem").message());
        this.problem = problem;
    }

    /** Returns the stable problem. */
    public CrsProblem problem() {
        return problem;
    }
}
