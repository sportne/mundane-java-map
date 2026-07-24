package io.github.mundanej.map.io.kml;

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

final class KmlDiagnostics {
    private final String sourceId;
    private final int retainedLimit;
    private final List<SourceDiagnostic> retained = new ArrayList<>();
    private long omitted;

    KmlDiagnostics(String sourceId, int retainedLimit) {
        this.sourceId = sourceId;
        this.retainedLimit = retainedLimit;
    }

    boolean canRetainWarning() {
        return retained.size() < retainedLimit;
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
                new DiagnosticLocation(
                        Optional.of("kml"),
                        recordNumber > 0 ? OptionalLong.of(recordNumber) : OptionalLong.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        OptionalLong.empty());
        return new SourceDiagnostic(
                code, severity, sourceId, Optional.of(location), message, context);
    }

    private static String warningMessage(String code) {
        return switch (code) {
            case "KML_UTF8_BOM_IGNORED" -> "UTF-8 byte-order mark was ignored";
            case "KML_PRESENTATION_IGNORED" -> "KML presentation content was ignored";
            case "KML_ALTITUDE_IGNORED" -> "KML altitude was ignored";
            case "KML_PLACEMARK_SKIPPED" -> "KML placemark without geometry was skipped";
            default -> "KML input produced a warning";
        };
    }
}
