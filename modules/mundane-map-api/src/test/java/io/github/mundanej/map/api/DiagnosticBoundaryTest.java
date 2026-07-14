package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class DiagnosticBoundaryTest {
    @Test
    void textFieldsAcceptTheirMaximumAndRejectOneOver() {
        String code64 = "A" + "1".repeat(63);
        String source256 = "s".repeat(256);
        String message1024 = "m".repeat(1_024);
        SourceDiagnostic maximum =
                new SourceDiagnostic(
                        code64,
                        DiagnosticSeverity.WARNING,
                        source256,
                        Optional.empty(),
                        message1024,
                        Map.of());
        assertEquals(code64, maximum.code());
        assertThrows(
                IllegalArgumentException.class,
                () -> diagnostic("A" + "1".repeat(64), "source", "message", Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> diagnostic("SOURCE_WARNING", "s".repeat(257), "message", Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> diagnostic("SOURCE_WARNING", "source", "m".repeat(1_025), Map.of()));
    }

    @Test
    void componentFieldAndContextBoundsAcceptMaximumAndRejectOneOver() {
        DiagnosticLocation maximum =
                new DiagnosticLocation(
                        Optional.of("c".repeat(32)),
                        OptionalLong.of(1),
                        OptionalInt.of(0),
                        OptionalInt.of(0),
                        Optional.of("f".repeat(256)),
                        OptionalLong.of(0));
        assertEquals(32, maximum.component().orElseThrow().length());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        location(
                                Optional.of("c".repeat(33)),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        location(
                                Optional.empty(),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.of("f".repeat(257)),
                                OptionalLong.empty()));

        LinkedHashMap<String, String> sixteen = new LinkedHashMap<>();
        for (int index = 0; index < 16; index++) {
            sixteen.put("k" + index, "v");
        }
        sixteen.remove("k0");
        sixteen.put("k".repeat(64), "v".repeat(256));
        SourceDiagnostic bounded = diagnostic("SOURCE_WARNING", "source", "message", sixteen);
        assertEquals(16, bounded.context().size());
        LinkedHashMap<String, String> seventeen = new LinkedHashMap<>(sixteen);
        seventeen.put("extra", "value");
        assertThrows(
                IllegalArgumentException.class,
                () -> diagnostic("SOURCE_WARNING", "source", "message", seventeen));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        diagnostic(
                                "SOURCE_WARNING",
                                "source",
                                "message",
                                Map.of("k".repeat(65), "v")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        diagnostic(
                                "SOURCE_WARNING",
                                "source",
                                "message",
                                Map.of("k", "v".repeat(257))));
    }

    @Test
    void locationIndexRulesDistinguishPositiveRecordAndZeroBasedIndexes() {
        DiagnosticLocation valid =
                location(
                        Optional.empty(),
                        OptionalLong.of(1),
                        OptionalInt.of(0),
                        OptionalInt.of(0),
                        Optional.empty(),
                        OptionalLong.of(0));
        assertEquals(1, valid.recordNumber().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        location(
                                Optional.empty(),
                                OptionalLong.of(0),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        location(
                                Optional.empty(),
                                OptionalLong.empty(),
                                OptionalInt.of(-1),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        location(
                                Optional.empty(),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                OptionalInt.of(-1),
                                Optional.empty(),
                                OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        location(
                                Optional.empty(),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.of(-1)));
    }

    @Test
    void reportsPreserveOrderOneSourceAndTerminalErrorLast() {
        SourceDiagnostic first = warning("source", "SOURCE_FIRST");
        SourceDiagnostic second = warning("source", "SOURCE_SECOND");
        SourceDiagnostic terminal = error("source", "SOURCE_FAILED");
        DiagnosticReport report = new DiagnosticReport(List.of(first, second, terminal), 3);
        assertEquals(List.of(first, second, terminal), report.entries());
        assertEquals(terminal, new SourceException(report, terminal).terminal());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiagnosticReport(List.of(first, warning("other", "SOURCE_OTHER")), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiagnosticReport(List.of(terminal, first), 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DiagnosticReport(
                                List.of(
                                        error("source", "SOURCE_ONE"),
                                        error("source", "SOURCE_TWO")),
                                0));
        assertThrows(
                IllegalArgumentException.class, () -> new DiagnosticReport(List.of(first), -1));
    }

    private static SourceDiagnostic diagnostic(
            String code, String sourceId, String message, Map<String, String> context) {
        return new SourceDiagnostic(
                code, DiagnosticSeverity.WARNING, sourceId, Optional.empty(), message, context);
    }

    private static SourceDiagnostic warning(String sourceId, String code) {
        return diagnostic(code, sourceId, "warning", Map.of());
    }

    private static SourceDiagnostic error(String sourceId, String code) {
        return new SourceDiagnostic(
                code, DiagnosticSeverity.ERROR, sourceId, Optional.empty(), "error", Map.of());
    }

    private static DiagnosticLocation location(
            Optional<String> component,
            OptionalLong recordNumber,
            OptionalInt partIndex,
            OptionalInt fieldIndex,
            Optional<String> fieldName,
            OptionalLong byteOffset) {
        return new DiagnosticLocation(
                component, recordNumber, partIndex, fieldIndex, fieldName, byteOffset);
    }
}
