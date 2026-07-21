package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackCoordinator.AccuracySummary;
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
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameMetrics;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackTelemetry;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackViewport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/** Opt-in canonical evidence runner for the 10k, 100k, and 1m live-track tiers. */
final class LiveTrackEvidence {
    private static final int WARMUP_SECONDS = 10;
    private static final int MEASUREMENT_SECONDS = 60;
    private static final int FPS_CAP = 10;
    private static final int WIDTH = 900;
    private static final int HEIGHT = 500;
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000L / FPS_CAP;
    private static final long POLL_NANOS = 5_000_000L;
    private static final DateTimeFormatter RUN_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    private LiveTrackEvidence() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: live-track-evidence <10k|100k|1m>");
        }
        Profile profile = Profile.parse(arguments[0]);
        String runId =
                RUN_TIME.format(Instant.now())
                        + '-'
                        + profile.id()
                        + '-'
                        + ProcessHandle.current().pid();
        Path workspaceRoot =
                Path.of(
                        System.getProperty(
                                "mundane.map.liveTrack.workspace",
                                "/tmp/mundane-java-map-live-track"));
        Path outputDirectory =
                Path.of(
                        System.getProperty(
                                "mundane.map.liveTrack.reports", "build/reports/live-track"));
        LiveTrackEvidenceReport report =
                execute(profile, runId, workspaceRoot, WARMUP_SECONDS, MEASUREMENT_SECONDS);
        report.writeAtomically(outputDirectory);
        System.out.printf(
                "Live-track evidence: profile=%s status=%s reports=%d fps=%.3f json=%s%n",
                profile.id(),
                report.status(),
                report.telemetry().processedReports(),
                report.telemetry().achievedFps(),
                outputDirectory.resolve("live-track-" + profile.id() + ".json"));
        if (report.status() != Status.SUCCESS) {
            throw new IllegalStateException(
                    "LIVE_TRACK_EVIDENCE_" + report.status() + ": " + report.diagnostics());
        }
    }

    static LiveTrackEvidenceReport execute(
            Profile profile,
            String runId,
            Path workspaceRoot,
            int warmupSeconds,
            int measurementSeconds)
            throws IOException {
        return execute(
                profile,
                runId,
                workspaceRoot,
                warmupSeconds,
                measurementSeconds,
                Runtime.getRuntime().maxMemory(),
                EvidenceHooks.standard());
    }

    static LiveTrackEvidenceReport execute(
            Profile profile,
            String runId,
            Path workspaceRoot,
            int warmupSeconds,
            int measurementSeconds,
            long maximumHeap)
            throws IOException {
        return execute(
                profile,
                runId,
                workspaceRoot,
                warmupSeconds,
                measurementSeconds,
                maximumHeap,
                EvidenceHooks.standard());
    }

    static LiveTrackEvidenceReport execute(
            Profile profile,
            String runId,
            Path workspaceRoot,
            int warmupSeconds,
            int measurementSeconds,
            long maximumHeap,
            EvidenceHooks hooks)
            throws IOException {
        Objects.requireNonNull(hooks, "hooks");
        if (warmupSeconds < 0 || measurementSeconds < 1) {
            throw new IllegalArgumentException("invalid evidence duration");
        }
        Path workspace = workspaceRoot.resolve(runId);
        Files.createDirectories(workspace);
        Files.writeString(
                workspace.resolve("run.txt"),
                "profile=" + profile.id() + "\nrunId=" + runId + "\n",
                StandardCharsets.UTF_8);

        int workers = TrackSimulationConfig.defaultWorkers(profile.population());
        TrackSimulationConfig simulation =
                TrackSimulationConfig.reference(profile.population(), workers);
        long started = System.nanoTime();
        long peakHeap = usedHeap();
        LiveTrackFrameEngine engine = null;
        LiveTrackTelemetry initial = emptyTelemetry(simulation);
        LiveTrackTelemetry measured = initial;
        AccuracySummary accuracy = new AccuracySummary(0.0, 0L, 0.0, 0.0);
        Phase initialization = new Phase(0L, 0L, 0L, 0L, 0L, 0L);
        Phase warmup = new Phase(0L, 0L, 0L, 0L, 0L, 0L);
        Phase measurement = new Phase(0L, 0L, 0L, 0L, 0L, 0L);
        Status status = Status.SUCCESS;
        List<Diagnostic> problems = new ArrayList<>();
        long logicalTrackBytes = 0L;
        long packedPositionBytes = 0L;
        long largestAllocation = 0L;
        boolean workersTerminated = false;
        boolean resourcesClosed = false;
        boolean workspaceRemoved = false;
        try {
            engine = hooks.engineFactory().create(simulation, started, maximumHeap);
            hooks.afterEngineCreated().accept(engine);
            long initialized = System.nanoTime();
            initial = engine.telemetry(initialized);
            logicalTrackBytes = initial.logicalTrackBytes();
            packedPositionBytes = initial.packedPositionBytes();
            largestAllocation =
                    Math.max(
                            engine.largestTrackAllocationBytes(),
                            engine.largestPositionAllocationBytes());
            initialization =
                    new Phase(
                            initialized - started,
                            initial.scheduledReports(),
                            initial.processedReports(),
                            initial.frames().requestedFrames(),
                            initial.frames().completedFrames(),
                            initial.frames().paintedFrames());
            peakHeap = Math.max(peakHeap, usedHeap());
            LiveTrackViewport viewport = worldViewport();
            PhaseOutcome warmOutcome =
                    runPhase(
                            engine,
                            viewport,
                            warmupSeconds,
                            initial,
                            initialized,
                            peakHeap,
                            hooks.beforeAwaitIdle());
            warmup = warmOutcome.phase();
            peakHeap = warmOutcome.peakHeap();

            engine.beginMeasurementWindow();
            LiveTrackTelemetry measurementBaseline = engine.telemetry(System.nanoTime());
            PhaseOutcome measuredOutcome =
                    runPhase(
                            engine,
                            viewport,
                            measurementSeconds,
                            measurementBaseline,
                            System.nanoTime(),
                            peakHeap,
                            hooks.beforeAwaitIdle());
            measured = measuredOutcome.telemetry();
            measurement = measuredOutcome.phase();
            peakHeap = measuredOutcome.peakHeap();
            accuracy = engine.accuracySummary();
            require(
                    measured.pendingReports() == profile.population(),
                    "report conservation failed");
            require(measured.rejectedReports() == 0L, "report rejection occurred");
            require(measured.frames().completedFrames() > 0L, "no measurement frame completed");
            require(measured.frames().paintedFrames() > 0L, "no measurement frame was consumed");
        } catch (EvidenceCancelledException exception) {
            status = Status.CANCELLED;
            problems.add(
                    new Diagnostic(
                            "LIVE_TRACK_EVIDENCE_CANCELLED", "WARNING", exception.getMessage()));
        } catch (RuntimeException exception) {
            status = Status.FAILED;
            problems.add(
                    new Diagnostic(
                            category(exception),
                            "ERROR",
                            exception.getMessage() == null
                                    ? exception.getClass().getSimpleName()
                                    : exception.getMessage()));
        } finally {
            if (engine != null) {
                try {
                    engine.close();
                } catch (RuntimeException exception) {
                    status = Status.FAILED;
                    problems.add(
                            new Diagnostic(
                                    "LIVE_TRACK_EVIDENCE_CLOSE_FAILED",
                                    "ERROR",
                                    exception.getMessage() == null
                                            ? exception.getClass().getSimpleName()
                                            : exception.getMessage()));
                }
                workersTerminated = engine.workersTerminated();
                resourcesClosed = engine.handoff().isClosed();
            }
            workspaceRemoved = removeWorkspace(workspace);
            if (!workspaceRemoved) {
                status = Status.FAILED;
                problems.add(
                        new Diagnostic(
                                "LIVE_TRACK_EVIDENCE_WORKSPACE_CLEANUP_FAILED",
                                "ERROR",
                                "temporary evidence workspace could not be removed"));
            }
        }

        LiveTrackFrameMetrics frames = measured.frames();
        long processedDuringMeasurement = measurement.processedReports();
        double reportsPerSecond =
                measurement.wallNanos() == 0L
                        ? 0.0
                        : processedDuringMeasurement * 1_000_000_000.0 / measurement.wallNanos();
        Limitation limitation =
                classify(status, measured.backlogSeconds(), frames.achievedFps(), FPS_CAP);
        IouKalmanConfig filter = simulation.filterConfig();
        LiveTrackEvidenceReport report =
                new LiveTrackEvidenceReport(
                        runId,
                        profile.id(),
                        status,
                        List.of(limitation),
                        new Configuration(
                                profile.population(),
                                simulation.seed(),
                                workers,
                                FPS_CAP,
                                filter.beta(),
                                filter.sigma(),
                                filter.measurementStandardDeviation(),
                                warmupSeconds,
                                measurementSeconds,
                                WIDTH,
                                HEIGHT),
                        environment(),
                        new Phases(initialization, warmup, measurement),
                        new Storage(
                                logicalTrackBytes,
                                packedPositionBytes,
                                frames.frameBufferBytes(),
                                largestAllocation,
                                maximumHeap,
                                peakHeap),
                        new Telemetry(
                                measured.simulationSecond(),
                                measured.scheduledReports(),
                                measured.processedReports(),
                                measured.rejectedReports(),
                                measured.lateReports(),
                                measured.pendingReports(),
                                reportsPerSecond,
                                frames.requestedFrames(),
                                frames.completedFrames(),
                                frames.paintedFrames(),
                                frames.skippedRequests(),
                                frames.staleDiscards(),
                                frames.replacedPendingFrames(),
                                frames.achievedFps(),
                                frames.buildP50Nanos(),
                                frames.buildP95Nanos(),
                                frames.buildP99Nanos(),
                                frames.buildMaximumNanos(),
                                measured.backlogSeconds(),
                                measured.shards().reportSkewRatio(),
                                measured.shards().workSkewRatio(),
                                accuracy.positionRmse(),
                                accuracy.innovationCount(),
                                accuracy.normalizedInnovationMean(),
                                accuracy.normalizedInnovationMaximum()),
                        new Cleanup(workersTerminated, resourcesClosed, workspaceRemoved),
                        problems);
        if (report.status() == Status.SUCCESS
                && (!workersTerminated || !resourcesClosed || !workspaceRemoved)) {
            return withCleanupFailure(report);
        }
        return report;
    }

    private static PhaseOutcome runPhase(
            LiveTrackFrameEngine engine,
            LiveTrackViewport viewport,
            int seconds,
            LiveTrackTelemetry baseline,
            long phaseStarted,
            long initialPeak,
            Runnable beforeAwaitIdle) {
        long durationNanos = Math.multiplyExact((long) seconds, 1_000_000_000L);
        long deadline = Math.addExact(phaseStarted, durationNanos);
        long nextFrame = phaseStarted;
        long peakHeap = initialPeak;
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                throw new EvidenceCancelledException("evidence thread was interrupted");
            }
            long now = System.nanoTime();
            if (now >= nextFrame) {
                engine.requestRealTime(viewport, now);
                do {
                    nextFrame += FRAME_INTERVAL_NANOS;
                } while (nextFrame <= now);
            }
            engine.handoff().frameForPaint(viewport.generation(), WIDTH, HEIGHT, now);
            requireHealthy(engine.telemetry(now));
            peakHeap = Math.max(peakHeap, usedHeap());
            long pause = Math.min(POLL_NANOS, Math.max(1L, Math.min(nextFrame, deadline) - now));
            LockSupport.parkNanos(pause);
        }
        beforeAwaitIdle.run();
        checkCancellation();
        try {
            if (!engine.awaitIdle(120_000L)) {
                throw new IllegalStateException("LIVE_TRACK_EVIDENCE_FRAME_TIMEOUT");
            }
        } catch (IllegalStateException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw new EvidenceCancelledException("evidence thread was interrupted");
            }
            throw exception;
        }
        checkCancellation();
        long completed = System.nanoTime();
        engine.handoff().frameForPaint(viewport.generation(), WIDTH, HEIGHT, completed);
        LiveTrackTelemetry terminal = engine.telemetry(completed);
        requireHealthy(terminal);
        LiveTrackFrameMetrics beforeFrames = baseline.frames();
        LiveTrackFrameMetrics afterFrames = terminal.frames();
        return new PhaseOutcome(
                terminal,
                new Phase(
                        completed - phaseStarted,
                        terminal.scheduledReports() - baseline.scheduledReports(),
                        terminal.processedReports() - baseline.processedReports(),
                        afterFrames.requestedFrames() - beforeFrames.requestedFrames(),
                        afterFrames.completedFrames() - beforeFrames.completedFrames(),
                        afterFrames.paintedFrames() - beforeFrames.paintedFrames()),
                Math.max(peakHeap, terminal.observedHeap()));
    }

    private static LiveTrackViewport worldViewport() {
        return new LiveTrackViewport(
                1L,
                MapViewport.fit(
                        WIDTH,
                        HEIGHT,
                        new Envelope(
                                -TrackShard.WORLD_X,
                                -TrackShard.MAX_Y,
                                TrackShard.WORLD_X,
                                TrackShard.MAX_Y),
                        24.0));
    }

    private static LiveTrackTelemetry emptyTelemetry(TrackSimulationConfig config) {
        return new LiveTrackTelemetry(
                LiveTrackCoordinator.State.NEW,
                config.population(),
                config.seed(),
                config.workers(),
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                new LiveTrackFrames.LiveTrackShardMetrics(config.workers(), 0L, 0L, 0L, 0L),
                0L,
                0L,
                Runtime.getRuntime().maxMemory(),
                usedHeap(),
                new LiveTrackFrameMetrics(0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0L, 0L, 0L, 0L, 0, 0L),
                "");
    }

    private static Environment environment() {
        return new Environment(
                System.getProperty("os.name") + ' ' + System.getProperty("os.version"),
                System.getProperty("os.arch"),
                cpuDescription(),
                Runtime.getRuntime().availableProcessors(),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                Runtime.getRuntime().maxMemory());
    }

    private static String cpuDescription() {
        Path cpuInfo = Path.of("/proc/cpuinfo");
        if (Files.isRegularFile(cpuInfo)) {
            try (InputStream input = Files.newInputStream(cpuInfo)) {
                String content = new String(input.readNBytes(65_536), StandardCharsets.UTF_8);
                for (String line : content.lines().toList()) {
                    if (line.startsWith("model name")) {
                        int separator = line.indexOf(':');
                        if (separator >= 0) {
                            return line.substring(separator + 1).trim();
                        }
                    }
                }
            } catch (IOException ignored) {
                // Fall back to the portable architecture below.
            }
        }
        return System.getProperty("os.arch");
    }

    private static Limitation classify(
            Status status, int backlogSeconds, double achievedFps, int fpsCap) {
        if (status != Status.SUCCESS) {
            return Limitation.INDETERMINATE;
        }
        if (backlogSeconds > 0) {
            return Limitation.BACKLOG_LIMITED;
        }
        if (achievedFps >= fpsCap * 0.95) {
            return Limitation.FRAME_CAP_LIMITED;
        }
        return Limitation.CPU_LIMITED;
    }

    private static LiveTrackEvidenceReport withCleanupFailure(LiveTrackEvidenceReport report) {
        return new LiveTrackEvidenceReport(
                report.runId(),
                report.profile(),
                Status.FAILED,
                List.of(Limitation.INDETERMINATE),
                report.configuration(),
                report.environment(),
                report.phases(),
                report.storage(),
                report.telemetry(),
                report.cleanup(),
                List.of(
                        new Diagnostic(
                                "LIVE_TRACK_EVIDENCE_CLEANUP_FAILED",
                                "ERROR",
                                "workers, resources, or workspace did not close")));
    }

    private static String category(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && message.matches("[A-Z0-9_]+")) {
            return message;
        }
        return "LIVE_TRACK_EVIDENCE_FAILED";
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static boolean removeWorkspace(Path workspace) {
        if (!Files.exists(workspace)) {
            return true;
        }
        try (var paths = Files.walk(workspace)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("LIVE_TRACK_EVIDENCE_INVALID: " + message);
        }
    }

    private static void requireHealthy(LiveTrackTelemetry telemetry) {
        if (telemetry.state() == LiveTrackCoordinator.State.FAILED
                || !telemetry.failureCategory().isBlank()) {
            String category =
                    telemetry.failureCategory().isBlank()
                            ? "LIVE_TRACK_EVIDENCE_ENGINE_FAILED"
                            : telemetry.failureCategory();
            throw new IllegalStateException(category);
        }
    }

    private static void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            throw new EvidenceCancelledException("evidence thread was interrupted");
        }
    }

    enum Profile {
        TEN_THOUSAND("10k", 10_000),
        HUNDRED_THOUSAND("100k", 100_000),
        ONE_MILLION("1m", 1_000_000);

        private final String id;
        private final int population;

        Profile(String id, int population) {
            this.id = id;
            this.population = population;
        }

        String id() {
            return id;
        }

        int population() {
            return population;
        }

        static Profile parse(String value) {
            for (Profile profile : values()) {
                if (profile.id.equals(value)) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("profile must be exactly 10k, 100k, or 1m");
        }
    }

    private record PhaseOutcome(LiveTrackTelemetry telemetry, Phase phase, long peakHeap) {}

    @FunctionalInterface
    interface EvidenceEngineFactory {
        LiveTrackFrameEngine create(
                TrackSimulationConfig configuration, long nowNanos, long maximumHeap);
    }

    record EvidenceHooks(
            EvidenceEngineFactory engineFactory,
            Consumer<LiveTrackFrameEngine> afterEngineCreated,
            Runnable beforeAwaitIdle) {
        EvidenceHooks {
            Objects.requireNonNull(engineFactory, "engineFactory");
            Objects.requireNonNull(afterEngineCreated, "afterEngineCreated");
            Objects.requireNonNull(beforeAwaitIdle, "beforeAwaitIdle");
        }

        static EvidenceHooks standard() {
            return new EvidenceHooks(LiveTrackFrameEngine::new, ignored -> {}, () -> {});
        }
    }

    private static final class EvidenceCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private EvidenceCancelledException(String message) {
            super(message);
        }
    }
}
