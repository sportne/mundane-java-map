package io.github.mundanej.map.io.dted;

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

/** Stable bounded DTED failure construction. */
final class DtedFailures {
    private DtedFailures() {}

    static SourceException failure(
            String sourceId,
            String code,
            String component,
            long byteOffset,
            String message,
            Map<String, String> context,
            Throwable cause) {
        return failure(sourceId, code, component, 0, byteOffset, message, context, cause);
    }

    static SourceException failure(
            String sourceId,
            String code,
            String component,
            long recordNumber,
            long byteOffset,
            String message,
            Map<String, String> context,
            Throwable cause) {
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of(component),
                        recordNumber > 0 ? OptionalLong.of(recordNumber) : OptionalLong.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        byteOffset >= 0 ? OptionalLong.of(byteOffset) : OptionalLong.empty());
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(location),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal, cause);
    }

    static SourceException field(
            String sourceId,
            String code,
            String component,
            long offset,
            String field,
            String reason) {
        return failure(
                sourceId,
                code,
                component,
                offset,
                "DTED field is invalid",
                Map.of("field", field, "reason", reason),
                null);
    }

    static SourceException recordField(
            String sourceId, long recordNumber, long offset, String field, String reason) {
        return failure(
                sourceId,
                "DTED_DATA_RECORD_INVALID",
                "data",
                recordNumber,
                offset,
                "DTED data record is invalid",
                Map.of("field", field, "reason", reason),
                null);
    }

    static SourceException recordCountMismatch(
            String sourceId,
            long recordNumber,
            long offset,
            String field,
            int actual,
            int expected) {
        return failure(
                sourceId,
                "DTED_DATA_RECORD_INVALID",
                "data",
                recordNumber,
                offset,
                "DTED data record is invalid",
                Map.of(
                        "field",
                        field,
                        "reason",
                        "mismatch",
                        "actual",
                        Integer.toString(actual),
                        "expected",
                        Integer.toString(expected)),
                null);
    }

    static SourceException cancelled(String sourceId) {
        return failure(
                sourceId,
                "SOURCE_CANCELLED",
                "dted",
                -1,
                "DTED open was cancelled",
                Map.of("operation", "dted-open"),
                null);
    }

    static SourceException io(String sourceId, String operation, Throwable cause) {
        return failure(
                sourceId,
                "DTED_IO_FAILED",
                "dted",
                -1,
                "DTED file operation failed",
                Map.of("operation", operation),
                cause);
    }

    static SourceException limit(String sourceId, String limit, long requested, long maximum) {
        return failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "dted",
                -1,
                "Elevation source limit exceeded",
                Map.of(
                        "scope",
                        "elevationOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                null);
    }
}
