package io.github.mundanej.map.io.http.tiles;

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

final class HttpTileDiagnostics {
    private HttpTileDiagnostics() {}

    static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(
                                new DiagnosticLocation(
                                        Optional.of("httpTile"),
                                        OptionalLong.of(1),
                                        OptionalInt.empty(),
                                        OptionalInt.empty(),
                                        Optional.empty(),
                                        OptionalLong.empty())),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }
}
