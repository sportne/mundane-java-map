package io.github.mundanej.map.io.se;

import java.util.Objects;

/** Unchecked terminal failure from the bounded Symbology Encoding reader. */
@SuppressWarnings("serial")
public final class SeReadException extends RuntimeException {
    /** Stable immutable failure detail. */
    private final SeReadProblem problem;

    /**
     * Creates a failure with no exposed parser exception.
     *
     * @param problem stable immutable problem
     */
    public SeReadException(SeReadProblem problem) {
        this(problem, null);
    }

    /**
     * Creates a failure with a non-contractual debugging cause.
     *
     * @param problem stable immutable problem
     * @param cause optional debugging cause
     */
    public SeReadException(SeReadProblem problem, Throwable cause) {
        super(message(Objects.requireNonNull(problem, "problem")), cause);
        this.problem = problem;
    }

    /**
     * Returns the stable problem.
     *
     * @return immutable problem
     */
    public SeReadProblem problem() {
        return problem;
    }

    private static String message(SeReadProblem problem) {
        return "OGC SE read failed: " + problem.code();
    }
}
