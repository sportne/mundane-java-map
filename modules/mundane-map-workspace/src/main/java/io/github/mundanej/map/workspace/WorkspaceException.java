package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.DiagnosticReport;
import java.util.Objects;
import java.util.Optional;

/** Stable workspace operation failure without raw input or path disclosure. */
@SuppressWarnings("serial")
public final class WorkspaceException extends RuntimeException {
    /** Stable structured failure detail. */
    private final WorkspaceProblem problem;

    /** Retained concrete-source diagnostics when a later opener maps a source failure. */
    private final Optional<DiagnosticReport> sourceReport;

    /**
     * Creates a workspace failure without a source report.
     *
     * @param problem stable problem
     */
    public WorkspaceException(WorkspaceProblem problem) {
        this(problem, Optional.empty(), null);
    }

    WorkspaceException(
            WorkspaceProblem problem, Optional<DiagnosticReport> sourceReport, Throwable cause) {
        super(Objects.requireNonNull(problem, "problem").code(), cause);
        this.problem = problem;
        this.sourceReport = Objects.requireNonNull(sourceReport, "sourceReport");
    }

    /**
     * Returns the stable structured problem.
     *
     * @return immutable problem
     */
    public WorkspaceProblem problem() {
        return problem;
    }

    /**
     * Returns the retained source report only for a later mapped source failure.
     *
     * @return optional immutable source report
     */
    public Optional<DiagnosticReport> sourceReport() {
        return sourceReport;
    }
}
