package io.github.mundanej.map.io.svg;

import java.util.Objects;

/** Failure to encode or atomically write a canonical SVG map picture. */
@SuppressWarnings("serial")
public final class SvgExportException extends RuntimeException {
    /** Stable immutable failure detail. */
    private final SvgExportProblem problem;

    /**
     * Creates an export failure.
     *
     * @param message human-readable non-contractual message
     * @param problem stable structured failure
     * @param cause optional Java cause
     */
    public SvgExportException(String message, SvgExportProblem problem, Throwable cause) {
        super(message, cause);
        this.problem = Objects.requireNonNull(problem, "problem");
    }

    /**
     * Creates an export failure without a Java cause.
     *
     * @param message human-readable non-contractual message
     * @param problem stable structured failure
     */
    public SvgExportException(String message, SvgExportProblem problem) {
        this(message, problem, null);
    }

    /**
     * Returns the stable structured failure.
     *
     * @return immutable problem
     */
    public SvgExportProblem problem() {
        return problem;
    }
}
