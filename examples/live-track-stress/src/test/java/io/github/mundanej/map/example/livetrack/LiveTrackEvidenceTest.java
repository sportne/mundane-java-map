package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidence.Profile;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Cleanup;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Configuration;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Diagnostic;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Environment;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Limitation;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Phase;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Phases;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Status;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Storage;
import io.github.mundanej.map.example.livetrack.LiveTrackEvidenceReport.Telemetry;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameEngine;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackViewport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveTrackEvidenceTest {
    private static final long ONE_GIBIBYTE = 1_073_741_824L;

    @Test
    void acceptsOnlyNamedEvidenceProfiles() {
        assertEquals(10_000, Profile.parse("10k").population());
        assertEquals(100_000, Profile.parse("100k").population());
        assertEquals(1_000_000, Profile.parse("1m").population());
        assertThrows(IllegalArgumentException.class, () -> Profile.parse("10000"));
        assertThrows(IllegalArgumentException.class, () -> Profile.parse("all"));
    }

    @Test
    void reportRoundTripsThroughAtomicJsonAndMarkdownFiles(@TempDir Path directory)
            throws IOException {
        LiveTrackEvidenceReport report = report(Status.SUCCESS, List.of());
        report.writeAtomically(directory);

        Path jsonPath = directory.resolve("live-track-10k.json");
        Path markdownPath = directory.resolve("live-track-10k.md");
        String json = Files.readString(jsonPath);
        assertEquals(report.toJson(), json);
        new JsonSyntax(json).parseDocument();
        assertTrue(json.contains("\"schema\": \"mundane-map-live-track-evidence/v1\""));
        assertTrue(json.contains("\"cleanup\""));
        assertTrue(Files.readString(markdownPath).contains("# Live-track evidence — 10k"));
        assertFalse(Files.exists(directory.resolve("live-track-10k.json.part")));
        assertFalse(Files.exists(directory.resolve("live-track-10k.md.part")));
    }

    @Test
    void failureReportRetainsStableDiagnosticAndRequiredObjects() {
        LiveTrackEvidenceReport report =
                report(
                        Status.FAILED,
                        List.of(
                                new Diagnostic(
                                        "LIVE_TRACK_TEST_FAILURE", "ERROR", "quoted \"detail\"")));
        String json = report.toJson();
        new JsonSyntax(json).parseDocument();
        assertTrue(json.contains("LIVE_TRACK_TEST_FAILURE"));
        assertTrue(json.contains("quoted \\\"detail\\\""));
        assertTrue(json.contains("\"configuration\""));
        assertTrue(json.contains("\"environment\""));
        assertTrue(json.contains("\"telemetry\""));
    }

    @Test
    void lowHeapPreflightProducesTerminalFailureAndRemovesWorkspace(@TempDir Path directory)
            throws IOException {
        Path workspace = directory.resolve("work");
        LiveTrackEvidenceReport report =
                LiveTrackEvidence.execute(Profile.TEN_THOUSAND, "low-heap", workspace, 0, 1, 1L);
        assertEquals(Status.FAILED, report.status());
        assertEquals(List.of(Limitation.INDETERMINATE), report.limitations());
        assertTrue(report.cleanup().workspaceRemoved());
        assertFalse(Files.exists(workspace.resolve("low-heap")));
        new JsonSyntax(report.toJson()).parseDocument();
    }

    @Test
    void interruptionProducesTerminalCancellationAndCleansResources(@TempDir Path directory)
            throws IOException {
        Semaphore producerGate = new Semaphore(0);
        LiveTrackEvidence.EvidenceHooks hooks =
                new LiveTrackEvidence.EvidenceHooks(
                        (configuration, nowNanos, maximumHeap) ->
                                new LiveTrackFrameEngine(
                                        configuration,
                                        nowNanos,
                                        maximumHeap,
                                        () -> {
                                            try {
                                                producerGate.acquire();
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                            }
                                        }),
                        ignored -> {},
                        Thread.currentThread()::interrupt);
        try {
            LiveTrackEvidenceReport report =
                    LiveTrackEvidence.execute(
                            Profile.TEN_THOUSAND,
                            "cancelled",
                            directory.resolve("work"),
                            1,
                            1,
                            ONE_GIBIBYTE,
                            hooks);
            assertEquals(Status.CANCELLED, report.status());
            assertTrue(report.cleanup().workersTerminated());
            assertTrue(report.cleanup().resourcesClosed());
            assertTrue(report.cleanup().workspaceRemoved());
            new JsonSyntax(report.toJson()).parseDocument();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void workerFailureProducesTerminalFailureInsteadOfReusingAnEarlierFrame(@TempDir Path directory)
            throws IOException {
        LiveTrackEvidence.EvidenceHooks hooks =
                new LiveTrackEvidence.EvidenceHooks(
                        LiveTrackFrameEngine::new,
                        engine -> {
                            engine.failWorkerForTest(0);
                            LiveTrackViewport viewport =
                                    new LiveTrackViewport(
                                            1L,
                                            MapViewport.fit(
                                                    64,
                                                    32,
                                                    new Envelope(
                                                            -TrackShard.WORLD_X,
                                                            -TrackShard.MAX_Y,
                                                            TrackShard.WORLD_X,
                                                            TrackShard.MAX_Y),
                                                    2.0));
                            assertTrue(engine.requestVirtual(viewport, 1));
                            assertTrue(engine.awaitIdle(10_000L));
                        },
                        () -> {});
        LiveTrackEvidenceReport report =
                LiveTrackEvidence.execute(
                        Profile.TEN_THOUSAND,
                        "worker-failure",
                        directory.resolve("work"),
                        0,
                        1,
                        ONE_GIBIBYTE,
                        hooks);
        assertEquals(Status.FAILED, report.status());
        assertTrue(
                report.diagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.category().equals("LIVE_TRACK_WORKER_FAILURE")));
        assertTrue(report.cleanup().workersTerminated());
        assertTrue(report.cleanup().resourcesClosed());
        assertTrue(report.cleanup().workspaceRemoved());
    }

    @Test
    void millionTrackTierPublishesOneBoundedHeadlessFrame() {
        TrackSimulationConfig config = TrackSimulationConfig.reference(1_000_000, 8);
        LiveTrackFrameEngine engine = new LiveTrackFrameEngine(config, 0L, ONE_GIBIBYTE);
        LiveTrackViewport viewport =
                new LiveTrackViewport(
                        1L,
                        MapViewport.fit(
                                320,
                                180,
                                new Envelope(
                                        -TrackShard.WORLD_X,
                                        -TrackShard.MAX_Y,
                                        TrackShard.WORLD_X,
                                        TrackShard.MAX_Y),
                                8.0));
        try {
            assertTrue(engine.requestVirtual(viewport, 1));
            assertTrue(engine.awaitIdle(120_000L));
            assertTrue(engine.telemetry(System.nanoTime()).processedReports() > 0L);
            assertEquals(1_000_000L, engine.telemetry(System.nanoTime()).pendingReports());
            assertTrue(engine.handoff().frameForPaint(1L, 320, 180, System.nanoTime()) != null);
            assertTrue(engine.largestPositionAllocationBytes() <= 8_000_000L);
        } finally {
            engine.close();
        }
        assertFalse(engine.producerAlive());
        assertTrue(engine.workersTerminated());
        assertTrue(engine.handoff().isClosed());
    }

    private static LiveTrackEvidenceReport report(Status status, List<Diagnostic> diagnostics) {
        Phase phase = new Phase(1_000_000_000L, 10L, 9L, 10L, 9L, 9L);
        return new LiveTrackEvidenceReport(
                "test-run",
                "10k",
                status,
                List.of(
                        status == Status.SUCCESS
                                ? Limitation.FRAME_CAP_LIMITED
                                : Limitation.INDETERMINATE),
                new Configuration(
                        10_000,
                        TrackSimulationConfig.REFERENCE_SEED,
                        8,
                        10,
                        0.05,
                        20.0,
                        5_000.0,
                        10,
                        60,
                        900,
                        500),
                new Environment("Linux", "x86_64", "test cpu", 8, "21", "test", ONE_GIBIBYTE),
                new Phases(phase, phase, phase),
                new Storage(1L, 2L, 3L, 4L, ONE_GIBIBYTE, 5L),
                new Telemetry(
                        70, 11L, 10L, 0L, 0L, 10_000L, 10.0, 10L, 9L, 9L, 1L, 0L, 0L, 9.0, 1L, 2L,
                        3L, 4L, 0, 1.01, 1.02, 42.0, 10L, 1.9, 4.5),
                new Cleanup(true, true, true),
                diagnostics);
    }

    private static final class JsonSyntax {
        private final String text;
        private int index;

        private JsonSyntax(String text) {
            this.text = text;
        }

        private void parseDocument() {
            value();
            whitespace();
            if (index != text.length()) {
                throw new AssertionError("trailing JSON content at " + index);
            }
        }

        private void value() {
            whitespace();
            if (index >= text.length()) {
                throw new AssertionError("missing JSON value");
            }
            switch (text.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true");
                case 'f' -> literal("false");
                case 'n' -> literal("null");
                default -> number();
            }
        }

        private void object() {
            expect('{');
            whitespace();
            if (take('}')) {
                return;
            }
            while (true) {
                string();
                whitespace();
                expect(':');
                value();
                whitespace();
                if (take('}')) {
                    return;
                }
                expect(',');
                whitespace();
            }
        }

        private void array() {
            expect('[');
            whitespace();
            if (take(']')) {
                return;
            }
            while (true) {
                value();
                whitespace();
                if (take(']')) {
                    return;
                }
                expect(',');
            }
        }

        private void string() {
            expect('"');
            while (index < text.length()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return;
                }
                if (character == '\\') {
                    if (index >= text.length()) {
                        throw new AssertionError("incomplete JSON escape");
                    }
                    char escaped = text.charAt(index++);
                    if (escaped == 'u') {
                        for (int count = 0; count < 4; count++) {
                            if (index >= text.length()
                                    || Character.digit(text.charAt(index++), 16) < 0) {
                                throw new AssertionError("invalid Unicode escape");
                            }
                        }
                    } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
                        throw new AssertionError("invalid JSON escape");
                    }
                } else if (character < 0x20) {
                    throw new AssertionError("unescaped JSON control character");
                }
            }
            throw new AssertionError("unterminated JSON string");
        }

        private void number() {
            int start = index;
            if (take('-')) {
                // Optional sign consumed.
            }
            digits();
            if (take('.')) {
                digits();
            }
            if (take('e') || take('E')) {
                if (take('+') || take('-')) {
                    // Optional exponent sign consumed.
                }
                digits();
            }
            if (start == index) {
                throw new AssertionError("invalid JSON number at " + index);
            }
        }

        private void digits() {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new AssertionError("missing JSON digits at " + index);
            }
        }

        private void literal(String expected) {
            if (!text.startsWith(expected, index)) {
                throw new AssertionError("invalid JSON literal at " + index);
            }
            index += expected.length();
        }

        private void whitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean take(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw new AssertionError("expected " + expected + " at " + index);
            }
        }
    }
}
