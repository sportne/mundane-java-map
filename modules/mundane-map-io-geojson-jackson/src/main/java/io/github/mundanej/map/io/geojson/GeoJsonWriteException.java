package io.github.mundanej.map.io.geojson;

import io.github.mundanej.map.api.DiagnosticReport;
import java.util.Objects;
import java.util.Optional;

/** Structured unchecked terminal failure from a GeoJSON write. */
@SuppressWarnings("serial")
public final class GeoJsonWriteException extends RuntimeException {
    /** Stable public problem retained independently of the debugging cause. */
    private final GeoJsonWriteProblem problem;

    /** Complete source report retained only when a source failure was mapped. */
    private final Optional<DiagnosticReport> sourceReport;

    /**
     * Creates a write failure.
     *
     * @param problem stable failure value
     * @param sourceReport source report only for a mapped source failure
     * @param cause optional debugging cause
     */
    public GeoJsonWriteException(
            GeoJsonWriteProblem problem, Optional<DiagnosticReport> sourceReport, Throwable cause) {
        super(Objects.requireNonNull(problem, "problem").message(), cause);
        this.problem = problem;
        this.sourceReport = Objects.requireNonNull(sourceReport, "sourceReport");
        if (sourceReport.isPresent() && !problem.code().equals("GEOJSON_WRITE_SOURCE_FAILED")) {
            throw new IllegalArgumentException(
                    "A source report is valid only for GEOJSON_WRITE_SOURCE_FAILED");
        }
    }

    /**
     * Returns the stable problem.
     *
     * @return immutable problem
     */
    public GeoJsonWriteProblem problem() {
        return problem;
    }

    /**
     * Returns the mapped source report when the source failed.
     *
     * @return optional immutable source report
     */
    public Optional<DiagnosticReport> sourceReport() {
        return sourceReport;
    }
}
