package io.github.mundanej.map.io.dted;

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
import java.util.Set;

/** Stable bounded DTED failure construction. */
final class DtedFailures {
    private static final Set<String> TERMINAL_CODES =
            Set.of(
                    "DTED_IO_FAILED",
                    "DTED_UHL_INVALID",
                    "DTED_DSI_INVALID",
                    "DTED_ACC_INVALID",
                    "DTED_PROFILE_UNSUPPORTED",
                    "DTED_HEADER_INCONSISTENT",
                    "DTED_FILE_LENGTH_MISMATCH",
                    "DTED_DATA_RECORD_INVALID",
                    "DTED_CHECKSUM_MISMATCH",
                    "DTED_ELEVATION_OUT_OF_RANGE");

    private DtedFailures() {}

    static Set<String> terminalCodes() {
        return TERMINAL_CODES;
    }

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

    private static SourceException failureWithoutLocation(
            String sourceId,
            String code,
            String message,
            Map<String, String> context,
            Throwable cause) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.empty(),
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

    static SourceException voidInComplete(
            String sourceId, long recordNumber, long offset, int sampleIndex) {
        return failure(
                sourceId,
                "DTED_DATA_RECORD_INVALID",
                "data",
                recordNumber,
                offset,
                "DTED data record is invalid",
                Map.of(
                        "field", "sample",
                        "reason", "voidInComplete",
                        "sampleIndex", Integer.toString(sampleIndex)),
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

    static SourceException checksum(
            String sourceId, long recordNumber, long offset, long actual, long expected) {
        return failure(
                sourceId,
                "DTED_CHECKSUM_MISMATCH",
                "data",
                recordNumber,
                offset,
                "DTED data record checksum does not match",
                Map.of(
                        "actual",
                        Long.toUnsignedString(actual),
                        "expected",
                        Long.toUnsignedString(expected)),
                null);
    }

    static SourceException elevation(
            String sourceId,
            long recordNumber,
            long offset,
            int sampleIndex,
            int magnitude,
            boolean negative) {
        return failure(
                sourceId,
                "DTED_ELEVATION_OUT_OF_RANGE",
                "data",
                recordNumber,
                offset,
                "DTED elevation is outside the supported metre range",
                Map.of(
                        "direction", negative ? "negative" : "positive",
                        "magnitude", Integer.toString(magnitude),
                        "sampleIndex", Integer.toString(sampleIndex)),
                null);
    }

    static SourceException unsupported(
            String sourceId, String component, long offset, String profile) {
        return failure(
                sourceId,
                "DTED_PROFILE_UNSUPPORTED",
                component,
                offset,
                "DTED profile is unsupported",
                Map.of("profile", profile),
                null);
    }

    static SourceException headerMismatch(
            String sourceId,
            String component,
            long offset,
            String field,
            String first,
            String second) {
        return failure(
                sourceId,
                "DTED_HEADER_INCONSISTENT",
                component,
                offset,
                "DTED header declarations disagree",
                Map.of("field", field, "first", first, "second", second),
                null);
    }

    static SourceException fileLength(String sourceId, long actual, long expected) {
        return failure(
                sourceId,
                "DTED_FILE_LENGTH_MISMATCH",
                "dted",
                -1,
                "DTED file length does not match",
                Map.of(
                        "actualBytes",
                        Long.toString(actual),
                        "expectedBytes",
                        Long.toString(expected)),
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
        return io(sourceId, operation, "dted", -1, cause);
    }

    static SourceException io(
            String sourceId, String operation, String component, long offset, Throwable cause) {
        return failure(
                sourceId,
                "DTED_IO_FAILED",
                component,
                offset,
                "DTED file operation failed",
                Map.of("causeKind", causeKind(cause), "operation", operation),
                cause);
    }

    static SourceException elevationLimit(
            String sourceId, String limit, long requested, long maximum) {
        return limit(sourceId, "elevationOpen", limit, requested, maximum);
    }

    static SourceException dtedLimit(String sourceId, String limit, long requested, long maximum) {
        return limit(sourceId, "dtedOpen", limit, requested, maximum);
    }

    private static SourceException limit(
            String sourceId, String scope, String limit, long requested, long maximum) {
        return failureWithoutLocation(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "Elevation source limit exceeded",
                Map.of(
                        "scope",
                        scope,
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                null);
    }

    private static String causeKind(Throwable cause) {
        if (cause instanceof NoSuchFileException) {
            return "notFound";
        }
        if (cause instanceof AccessDeniedException) {
            return "accessDenied";
        }
        if (cause instanceof ClosedChannelException) {
            return "closed";
        }
        return "other";
    }
}
