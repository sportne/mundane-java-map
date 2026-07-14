package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Stable DBF/CPG diagnostic construction without exposing input bytes. */
final class DbfDiagnostics {
    private DbfDiagnostics() {}

    static SourceException failure(
            String source,
            String code,
            String component,
            OptionalLong record,
            OptionalInt field,
            Optional<String> name,
            long offset,
            Map<String, String> context) {
        return ShapefileFailures.failureWithField(
                source,
                code,
                component,
                record,
                field,
                name,
                offset,
                "Shapefile attribute table is invalid",
                context);
    }

    static SourceDiagnostic warning(
            String source,
            String code,
            String component,
            OptionalLong record,
            OptionalInt field,
            Optional<String> name,
            long offset,
            Map<String, String> context) {
        return new SourceDiagnostic(
                code,
                DiagnosticSeverity.WARNING,
                source,
                Optional.of(
                        new DiagnosticLocation(
                                Optional.of(component),
                                record,
                                OptionalInt.empty(),
                                field,
                                name,
                                offset < 0 ? OptionalLong.empty() : OptionalLong.of(offset))),
                "Shapefile attribute diagnostic",
                context);
    }
}
