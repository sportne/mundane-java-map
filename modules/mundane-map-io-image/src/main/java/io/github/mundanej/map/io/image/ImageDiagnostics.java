package io.github.mundanej.map.io.image;

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

final class ImageDiagnostics {
    private ImageDiagnostics() {}

    static SourceException failure(
            String sourceId,
            String code,
            String component,
            String message,
            Map<String, String> context) {
        return failure(sourceId, code, component, OptionalLong.empty(), message, context, null);
    }

    static SourceException failure(
            String sourceId,
            String code,
            String component,
            OptionalLong byteOffset,
            String message,
            Map<String, String> context,
            Throwable cause) {
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of(component),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        byteOffset);
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

    static SourceException limit(String sourceId, String limit, long requested, long maximum) {
        return failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "image",
                "Image opening limit exceeded",
                Map.of(
                        "scope",
                        "imageOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }
}
