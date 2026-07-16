package io.github.mundanej.map.api;

/** Source diagnostic severity. */
public enum DiagnosticSeverity {
    /** Processing completed but callers should surface a recoverable condition. */
    WARNING,
    /** Processing could not complete for the affected operation or record. */
    ERROR
}
