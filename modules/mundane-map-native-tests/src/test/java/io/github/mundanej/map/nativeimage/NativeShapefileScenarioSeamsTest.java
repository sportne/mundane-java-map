package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NativeShapefileScenarioSeamsTest {
    @Test
    void exactSemanticOracleRejectsARecordMutation() {
        List<FeatureRecord> records = records();
        assertDoesNotThrow(() -> NativeShapefileSmokeScenario.assertRecords(records));
        FeatureRecord first = records.getFirst();
        List<FeatureRecord> mutated =
                List.of(
                        new FeatureRecord(
                                "record:changed",
                                first.name(),
                                first.geometry(),
                                first.attributes()),
                        records.getLast());

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeShapefileSmokeScenario.assertRecords(mutated));
        assertTrue(failure.getMessage().endsWith("first record ID changed"));
    }

    @Test
    void exactWarningOracleRejectsACodeMutation() {
        DiagnosticReport report = new DiagnosticReport(List.of(warning("SHAPEFILE_DBF_OTHER")), 0);

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeShapefileSmokeScenario.assertCursorWarning(report));
        assertTrue(failure.getMessage().endsWith("warning code changed"));
    }

    @Test
    void exactMalformedOracleRejectsAContextMutation() {
        SourceDiagnostic terminal = malformed(Map.of("reason", "changed"));
        SourceException sourceFailure =
                new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                NativeShapefileSmokeScenario.assertMalformedDiagnostic(
                                        sourceFailure));
        assertTrue(failure.getMessage().endsWith("malformed diagnostic context changed"));
    }

    @Test
    void retainedReportRemainsReadableAfterItsOwnerCleanup() {
        AtomicBoolean closed = new AtomicBoolean();
        DiagnosticReport retained =
                NativeShapefileSmokeScenario.withCleanup(
                        () ->
                                new DiagnosticReport(
                                        List.of(warning("SHAPEFILE_DBF_VALUE_INVALID")), 0),
                        () -> closed.set(true));

        assertTrue(closed.get());
        assertDoesNotThrow(() -> NativeShapefileSmokeScenario.assertCursorWarning(retained));
        assertEquals("SHAPEFILE_DBF_VALUE_INVALID", retained.entries().getFirst().code());
    }

    @Test
    void scenarioRejectsSourceWorkOnTheEventDispatchThread() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        NativeShapefileSmokeScenario.run();
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });

        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(failure.get().getMessage().contains("source work must run off the EDT"));
    }

    @Test
    void scenarioMarshalsViewWorkToTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread());

        boolean ranOnEdt =
                NativeShapefileSmokeScenario.onEdt(SwingUtilities::isEventDispatchThread);

        assertTrue(ranOnEdt);
    }

    @Test
    void sourceCleanupIsSuppressedBehindThePrimaryFailure() {
        RuntimeException primary = new RuntimeException("semantic failure");
        RuntimeException cleanup = new RuntimeException("source close failure");

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                NativeShapefileSmokeScenario.withCleanup(
                                        () -> {
                                            throw primary;
                                        },
                                        () -> {
                                            throw cleanup;
                                        }));

        assertSame(primary, actual);
        assertArrayEquals(new Throwable[] {cleanup}, actual.getSuppressed());
    }

    @Test
    void installedBindingIsClosedOnlyByViewAndCannotReplacePaintFailure() {
        RuntimeException primary = new RuntimeException("paint failure");
        RuntimeException viewClose = new RuntimeException("view close failure");
        AtomicBoolean bindingClosed = new AtomicBoolean();

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                NativeShapefileSmokeScenario.withRenderOwnership(
                                        () -> {
                                            throw primary;
                                        },
                                        () -> true,
                                        () -> bindingClosed.set(true),
                                        () -> {
                                            throw viewClose;
                                        }));

        assertSame(primary, actual);
        assertFalse(bindingClosed.get());
        assertArrayEquals(new Throwable[] {viewClose}, actual.getSuppressed());
    }

    @Test
    void unattachedBindingAndViewCleanupFailuresAreOrderedBehindInstallFailure() {
        RuntimeException primary = new RuntimeException("install failure");
        RuntimeException bindingClose = new RuntimeException("binding close failure");
        RuntimeException viewClose = new RuntimeException("view close failure");

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                NativeShapefileSmokeScenario.withRenderOwnership(
                                        () -> {
                                            throw primary;
                                        },
                                        () -> false,
                                        () -> {
                                            throw bindingClose;
                                        },
                                        () -> {
                                            throw viewClose;
                                        }));

        assertSame(primary, actual);
        assertArrayEquals(new Throwable[] {bindingClose, viewClose}, actual.getSuppressed());
    }

    @Test
    void unattachedBindingFailureRemainsPrimaryWhenViewCleanupAlsoFails() {
        RuntimeException bindingClose = new RuntimeException("binding close failure");
        RuntimeException viewClose = new RuntimeException("view close failure");

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                NativeShapefileSmokeScenario.withRenderOwnership(
                                        () -> "rendered",
                                        () -> false,
                                        () -> {
                                            throw bindingClose;
                                        },
                                        () -> {
                                            throw viewClose;
                                        }));

        assertSame(bindingClose, actual);
        assertArrayEquals(new Throwable[] {viewClose}, actual.getSuppressed());
    }

    private static List<FeatureRecord> records() {
        PolygonGeometry firstGeometry =
                new PolygonGeometry(
                        sequence(0, 0, 0, 40, 40, 40, 40, 0, 0, 0),
                        List.of(sequence(10, 10, 20, 10, 20, 20, 10, 20, 10, 10)));
        MultiPolygonGeometry secondGeometry =
                MultiPolygonGeometry.of(
                        sequence(
                                50, 0, 50, 10, 60, 10, 60, 0, 50, 0, 70, 20, 70, 30, 80, 30, 80, 20,
                                70, 20),
                        new int[] {0, 5, 10},
                        new int[] {0, 1, 2});
        return List.of(
                new FeatureRecord(
                        "record:1", "", firstGeometry, Map.of("NAME", "Café", "NOTE", "valid")),
                new FeatureRecord(
                        "record:2",
                        "",
                        secondGeometry,
                        Map.of("NAME", "Second", "NOTE", AttributeNull.INSTANCE)));
    }

    private static SourceDiagnostic warning(String code) {
        return new SourceDiagnostic(
                code,
                DiagnosticSeverity.WARNING,
                NativeShapefileSmokeScenario.VALID_SOURCE_ID,
                Optional.of(
                        new DiagnosticLocation(
                                Optional.of("dbf"),
                                OptionalLong.of(2),
                                OptionalInt.empty(),
                                OptionalInt.of(1),
                                Optional.of("NOTE"),
                                OptionalLong.of(129))),
                "Invalid DBF value",
                Map.of("reason", "encoding"));
    }

    private static SourceDiagnostic malformed(Map<String, String> context) {
        return new SourceDiagnostic(
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                DiagnosticSeverity.ERROR,
                NativeShapefileSmokeScenario.MALFORMED_SOURCE_ID,
                Optional.of(
                        new DiagnosticLocation(
                                Optional.of("shp"),
                                OptionalLong.of(1),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.of(104))),
                "Invalid record length",
                context);
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }
}
