package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class ShapefileFailures {
    private ShapefileFailures() {}

    static SourceException failure(
            String source,
            String code,
            String component,
            OptionalLong record,
            long offset,
            String message,
            Map<String, String> context) {
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of(component),
                        record,
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        offset < 0 ? OptionalLong.empty() : OptionalLong.of(offset));
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        source,
                        Optional.of(location),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    static SourceException io(
            String source, String component, String operation, long offset, Exception cause) {
        String kind =
                cause instanceof NoSuchFileException
                        ? "notFound"
                        : cause instanceof AccessDeniedException
                                ? "accessDenied"
                                : cause instanceof ClosedChannelException ? "closed" : "other";
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of(component),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        offset < 0 ? OptionalLong.empty() : OptionalLong.of(offset));
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SHAPEFILE_IO_FAILED",
                        DiagnosticSeverity.ERROR,
                        source,
                        Optional.of(location),
                        "Shapefile I/O operation failed",
                        Map.of("causeKind", kind, "operation", operation));
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal, cause);
    }

    static SourceException cancelled(String source) {
        return failure(
                source,
                "SOURCE_CANCELLED",
                "shp",
                OptionalLong.empty(),
                -1,
                "Shapefile operation was cancelled",
                Map.of("operation", "shapefile"));
    }

    static SourceException limit(
            String source,
            String scope,
            String limit,
            long requested,
            long maximum,
            OptionalLong record,
            long offset) {
        return failure(
                source,
                "SOURCE_LIMIT_EXCEEDED",
                "shp",
                record,
                offset,
                "Shapefile limit exceeded",
                Map.of(
                        "scope",
                        scope,
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }
}
