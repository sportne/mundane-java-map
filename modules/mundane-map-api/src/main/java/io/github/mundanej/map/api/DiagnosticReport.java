package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** Immutable ordered source diagnostic report. */
public record DiagnosticReport(List<SourceDiagnostic> entries, long omittedWarningCount) {
    /** Copies and validates the report. */
    public DiagnosticReport {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (omittedWarningCount < 0) {
            throw new IllegalArgumentException("omittedWarningCount must be non-negative");
        }
        String source = null;
        int errors = 0;
        for (int index = 0; index < entries.size(); index++) {
            SourceDiagnostic diagnostic = Objects.requireNonNull(entries.get(index), "diagnostic");
            if (source == null) {
                source = diagnostic.sourceId();
            } else if (!source.equals(diagnostic.sourceId())) {
                throw new IllegalArgumentException("A report must use one source ID");
            }
            if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
                errors++;
                if (index != entries.size() - 1) {
                    throw new IllegalArgumentException("The terminal error must be last");
                }
            }
        }
        if (errors > 1) {
            throw new IllegalArgumentException("A report may contain at most one terminal error");
        }
    }

    /** Returns an empty successful report. */
    public static DiagnosticReport empty() {
        return new DiagnosticReport(List.of(), 0);
    }
}
