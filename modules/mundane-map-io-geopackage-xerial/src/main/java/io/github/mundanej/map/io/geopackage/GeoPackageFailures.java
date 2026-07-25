package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class GeoPackageFailures {
    private GeoPackageFailures() {}

    static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    static SourceException failure(
            String sourceId,
            String code,
            String message,
            Map<String, String> context,
            Throwable cause) {
        SourceException failure = failure(sourceId, code, message, context);
        return new SourceException(failure.report(), failure.terminal(), cause);
    }

    static void checkpoint(String sourceId, CancellationTokenLike cancellation, String operation) {
        if (cancellation.cancelled()) {
            throw failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "GeoPackage operation was cancelled",
                    Map.of("operation", operation));
        }
    }

    static SourceException atRecord(SourceException failure, long recordNumber) {
        SourceDiagnostic original = failure.terminal();
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of("geopackage"),
                        OptionalLong.of(recordNumber),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        OptionalLong.empty());
        SourceDiagnostic located =
                new SourceDiagnostic(
                        original.code(),
                        original.severity(),
                        original.sourceId(),
                        Optional.of(location),
                        original.message(),
                        original.context());
        return new SourceException(
                new DiagnosticReport(List.of(located), failure.report().omittedWarningCount()),
                located,
                failure.getCause());
    }

    static DiagnosticLocation recordLocation(long recordNumber) {
        return new DiagnosticLocation(
                Optional.of("geopackage"),
                OptionalLong.of(recordNumber),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    @FunctionalInterface
    interface CancellationTokenLike {
        boolean cancelled();
    }
}
