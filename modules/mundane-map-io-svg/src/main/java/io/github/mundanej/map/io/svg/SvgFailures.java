package io.github.mundanej.map.io.svg;

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

final class SvgFailures {
    private SvgFailures() {}

    static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
        return failure(sourceId, code, message, context, null);
    }

    static SourceException failure(
            String sourceId,
            String code,
            String message,
            Map<String, String> context,
            Throwable cause) {
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of("svg"),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        OptionalLong.empty());
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

    static SourceException value(String sourceId, String field, String reason) {
        return failure(
                sourceId,
                "SVG_VALUE_INVALID",
                "SVG value is invalid",
                Map.of("field", field, "reason", reason));
    }

    static SourceException unsupported(String sourceId, String construct) {
        return failure(
                sourceId,
                "SVG_PROFILE_UNSUPPORTED",
                "SVG construct is outside the supported profile",
                Map.of("construct", construct));
    }

    static SourceException xml(String sourceId, String reason) {
        return failure(sourceId, "SVG_XML_INVALID", "SVG XML is invalid", Map.of("reason", reason));
    }

    static SourceException encoding(String sourceId, String reason) {
        return failure(
                sourceId,
                "SVG_ENCODING_INVALID",
                "SVG encoding is invalid",
                Map.of("reason", reason));
    }

    static SourceException limit(String sourceId, String limit, long requested, long maximum) {
        return failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "SVG import limit exceeded",
                Map.of(
                        "scope",
                        "svgImport",
                        "limit",
                        limit,
                        "maximum",
                        Long.toString(maximum),
                        "requested",
                        Long.toString(requested)));
    }

    static SourceException cancelled(String sourceId) {
        return failure(sourceId, "SOURCE_CANCELLED", "SVG import was cancelled", Map.of());
    }

    static SourceException io(String sourceId, String operation, String reason) {
        return failure(
                sourceId,
                "SVG_IO_FAILED",
                "SVG file operation failed",
                Map.of("operation", operation, "reason", reason));
    }
}
