package io.github.mundanej.map.api;

import java.util.Objects;

/** Structured unchecked terminal source failure. */
@SuppressWarnings("serial")
public final class SourceException extends RuntimeException {
    /** Complete diagnostic report retained for serialization. */
    private final DiagnosticReport report;

    /** Terminal error entry retained for serialization. */
    private final SourceDiagnostic terminal;

    /**
     * Creates a structured failure.
     *
     * @param report complete report ending in {@code terminal}
     * @param terminal final error diagnostic
     */
    public SourceException(DiagnosticReport report, SourceDiagnostic terminal) {
        this(report, terminal, null);
    }

    /**
     * Creates a structured failure with a debugging cause.
     *
     * @param report complete report ending in {@code terminal}
     * @param terminal final error diagnostic
     * @param cause optional non-contractual debugging cause
     */
    public SourceException(DiagnosticReport report, SourceDiagnostic terminal, Throwable cause) {
        super(Objects.requireNonNull(terminal, "terminal").message(), cause);
        this.report = Objects.requireNonNull(report, "report");
        this.terminal = terminal;
        if (terminal.severity() != DiagnosticSeverity.ERROR
                || report.entries().isEmpty()
                || !report.entries().getLast().equals(terminal)) {
            throw new IllegalArgumentException("Terminal error must be the final report entry");
        }
    }

    /**
     * Returns the complete report.
     *
     * @return immutable diagnostic report
     */
    public DiagnosticReport report() {
        return report;
    }

    /**
     * Returns the terminal diagnostic.
     *
     * @return final error entry
     */
    public SourceDiagnostic terminal() {
        return terminal;
    }
}
