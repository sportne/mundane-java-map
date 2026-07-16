package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable change in one installed source layer's report.
 *
 * @param layerId non-blank installed source-layer identity
 * @param previous prior report, or empty before the first report
 * @param current new report, or empty when the report is removed
 */
public record MapSourceReportEvent(
        String layerId, Optional<DiagnosticReport> previous, Optional<DiagnosticReport> current) {
    /** Validates a real report transition. */
    public MapSourceReportEvent {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (layerId.isBlank() || previous.equals(current)) {
            throw new IllegalArgumentException(
                    "A source report event requires a layer ID and real transition");
        }
    }
}
