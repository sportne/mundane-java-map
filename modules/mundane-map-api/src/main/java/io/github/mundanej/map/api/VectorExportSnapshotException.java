package io.github.mundanej.map.api;

import java.util.Objects;

/** Failure to construct a bounded detached vector-export snapshot. */
@SuppressWarnings("serial")
public final class VectorExportSnapshotException extends RuntimeException {
    /** Stable immutable failure detail. */
    private final VectorExportSnapshotProblem problem;

    /**
     * Creates a snapshot failure.
     *
     * @param message human-readable non-contractual message
     * @param problem stable structured failure
     * @param cause optional Java cause
     */
    public VectorExportSnapshotException(
            String message, VectorExportSnapshotProblem problem, Throwable cause) {
        super(message, cause);
        this.problem = Objects.requireNonNull(problem, "problem");
    }

    /**
     * Creates a snapshot failure without a Java cause.
     *
     * @param message human-readable non-contractual message
     * @param problem stable structured failure
     */
    public VectorExportSnapshotException(String message, VectorExportSnapshotProblem problem) {
        this(message, problem, null);
    }

    /**
     * Returns the stable structured failure.
     *
     * @return immutable problem
     */
    public VectorExportSnapshotProblem problem() {
        return problem;
    }
}
