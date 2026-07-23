package io.github.mundanej.map.io.gpx;

import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class GpxDiagnostics {
    private final String sourceId;
    private final int retainedLimit;
    private final List<SourceDiagnostic> retained = new ArrayList<>();
    private long omitted;

    GpxDiagnostics(String sourceId, int retainedLimit) {
        this.sourceId = sourceId;
        this.retainedLimit = retainedLimit;
    }

    void warning(String code, Map<String, String> context, long recordNumber) {
        if (retained.size() < retainedLimit) {
            retained.add(
                    diagnostic(
                            code,
                            DiagnosticSeverity.WARNING,
                            context,
                            recordNumber,
                            warningMessage(code)));
        } else if (omitted != Long.MAX_VALUE) {
            omitted++;
        }
    }

    SourceException failure(
            String code,
            Map<String, String> context,
            long recordNumber,
            String message,
            Throwable cause) {
        SourceDiagnostic terminal =
                diagnostic(code, DiagnosticSeverity.ERROR, context, recordNumber, message);
        List<SourceDiagnostic> complete = new ArrayList<>(retained);
        complete.add(terminal);
        DiagnosticReport report = new DiagnosticReport(complete, omitted);
        return cause == null
                ? new SourceException(report, terminal)
                : new SourceException(report, terminal, cause);
    }

    DiagnosticReport report() {
        return new DiagnosticReport(retained, omitted);
    }

    private SourceDiagnostic diagnostic(
            String code,
            DiagnosticSeverity severity,
            Map<String, String> context,
            long recordNumber,
            String message) {
        DiagnosticLocation location =
                recordNumber > 0
                        ? new DiagnosticLocation(
                                Optional.of("gpx"),
                                OptionalLong.of(recordNumber),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty())
                        : new DiagnosticLocation(
                                Optional.of("gpx"),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty());
        return new SourceDiagnostic(
                code, severity, sourceId, Optional.of(location), message, context);
    }

    private static String warningMessage(String code) {
        return switch (code) {
            case "GPX_UTF8_BOM_IGNORED" -> "UTF-8 byte-order mark was ignored";
            case "GPX_EXTENSION_IGNORED" -> "GPX extension content was ignored";
            case "GPX_FIELD_IGNORED" -> "GPX field was ignored";
            case "GPX_TRACK_POINT_DATA_IGNORED" -> "GPX track-point data was ignored";
            case "GPX_TRACK_SEGMENT_SKIPPED" -> "GPX track segment was skipped";
            default -> "GPX input produced a warning";
        };
    }
}
