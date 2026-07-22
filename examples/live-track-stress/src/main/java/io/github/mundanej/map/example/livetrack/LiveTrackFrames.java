package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapPlan;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;

/** Bounded packed-position rendering, frame ownership, pacing, and telemetry support. */
final class LiveTrackFrames {
    private LiveTrackFrames() {}

    record LiveTrackViewport(long generation, MapViewport viewport) {
        static final int MAX_PIXELS = 8_388_608;

        LiveTrackViewport {
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            Objects.requireNonNull(viewport, "viewport");
            long pixels = Math.multiplyExact((long) viewport.width(), viewport.height());
            if (pixels > MAX_PIXELS) {
                throw new IllegalArgumentException("LIVE_TRACK_FRAME_PIXEL_LIMIT");
            }
        }

        int width() {
            return viewport.width();
        }

        int height() {
            return viewport.height();
        }
    }

    record LiveTrackFrameMetrics(
            long requestedFrames,
            long completedFrames,
            long paintedFrames,
            long skippedRequests,
            long staleDiscards,
            long replacedPendingFrames,
            double achievedFps,
            long buildP50Nanos,
            long buildP95Nanos,
            long buildP99Nanos,
            long buildMaximumNanos,
            int allocatedBuffers,
            long frameBufferBytes) {}

    record LiveTrackPresentationMetrics(
            long presentedFrames,
            double achievedFps,
            long paintP50Nanos,
            long paintP95Nanos,
            long paintP99Nanos,
            long paintMaximumNanos,
            long backgroundRefreshes,
            long backgroundLastNanos,
            long backgroundMaximumNanos) {}

    record LiveTrackShardMetrics(
            int shardCount,
            long minimumProcessedReports,
            long maximumProcessedReports,
            long minimumWorkNanos,
            long maximumWorkNanos) {
        double reportSkewRatio() {
            return minimumProcessedReports == 0L
                    ? 0.0
                    : (double) maximumProcessedReports / minimumProcessedReports;
        }

        double workSkewRatio() {
            return minimumWorkNanos == 0L ? 0.0 : (double) maximumWorkNanos / minimumWorkNanos;
        }
    }

    record LiveTrackTelemetry(
            LiveTrackCoordinator.State state,
            int population,
            long seed,
            int workers,
            int simulationSecond,
            long scheduledReports,
            long processedReports,
            long rejectedReports,
            long lateReports,
            long pendingReports,
            int backlogSeconds,
            LiveTrackShardMetrics shards,
            long logicalTrackBytes,
            long packedPositionBytes,
            long maximumHeap,
            long observedHeap,
            LiveTrackFrameMetrics frames,
            String failureCategory) {}

    static final class LiveTrackFrameEngine implements AutoCloseable {
        private static final long HEADROOM = 256L * 1024L * 1024L;

        private enum Control {
            PAUSE,
            RESUME,
            RESET
        }

        private final Object monitor = new Object();
        private final Object telemetryMonitor = new Object();
        private final TrackSimulationConfig config;
        private final long maximumHeap;
        private final LiveTrackCoordinator coordinator;
        private final double[] positionsX;
        private final double[] positionsY;
        private final LiveTrackFrameHandoff handoff;
        private final long[] shardMetrics = new long[4];
        private final Runnable beforeWorkSelection;
        private final Thread producer;
        private FrameDemand pendingDemand;
        private Control pendingControl;
        private long controlNowNanos;
        private boolean building;
        private boolean closed;
        private boolean closeComplete;
        private volatile LiveTrackCoordinator.State observedState;
        private volatile int observedSimulationSecond;
        private volatile long observedScheduledReports;
        private volatile long observedProcessedReports;
        private volatile long observedRejectedReports;
        private volatile long observedLateReports;
        private volatile long observedPendingReports;
        private volatile int observedRequestedSimulationSecond;
        private volatile RuntimeException failure;

        LiveTrackFrameEngine(TrackSimulationConfig config, long nowNanos) {
            this(config, nowNanos, Runtime.getRuntime().maxMemory());
        }

        LiveTrackFrameEngine(TrackSimulationConfig config, long nowNanos, long maximumHeap) {
            this(config, nowNanos, maximumHeap, () -> {});
        }

        LiveTrackFrameEngine(
                TrackSimulationConfig config,
                long nowNanos,
                long maximumHeap,
                Runnable beforeWorkSelection) {
            this.config = Objects.requireNonNull(config, "config");
            this.maximumHeap = maximumHeap;
            this.beforeWorkSelection =
                    Objects.requireNonNull(beforeWorkSelection, "beforeWorkSelection");
            TrackStoragePlan storagePlan = TrackStoragePlan.preflight(config, maximumHeap);
            long onePositionArrayBytes =
                    Math.multiplyExact((long) config.population(), Double.BYTES);
            long positionBytes = Math.multiplyExact(onePositionArrayBytes, 2L);
            preflight(storagePlan.logicalBytes(), positionBytes, maximumHeap);
            coordinator = new LiveTrackCoordinator(config, storagePlan);
            positionsX = new double[config.population()];
            positionsY = new double[config.population()];
            handoff =
                    new LiveTrackFrameHandoff(
                            maximumHeap, Math.addExact(coordinator.logicalBytes(), positionBytes));
            coordinator.start(nowNanos);
            captureCoordinatorTelemetry();
            producer = new Thread(this::run, "live-track-frame-producer");
            producer.setDaemon(true);
            producer.start();
        }

        boolean requestRealTime(LiveTrackViewport viewport, long nowNanos) {
            return request(new FrameDemand(viewport, nowNanos, -1));
        }

        boolean requestVirtual(LiveTrackViewport viewport, int targetSecond) {
            if (targetSecond < 0) {
                throw new IllegalArgumentException("targetSecond must be non-negative");
            }
            return request(new FrameDemand(viewport, 0L, targetSecond));
        }

        boolean requestPause(long nowNanos) {
            return requestControl(Control.PAUSE, nowNanos);
        }

        boolean requestResume(long nowNanos) {
            return requestControl(Control.RESUME, nowNanos);
        }

        boolean requestReset(long nowNanos) {
            return requestControl(Control.RESET, nowNanos);
        }

        LiveTrackFrameHandoff handoff() {
            return handoff;
        }

        TrackSimulationConfig configuration() {
            return config;
        }

        long largestPositionAllocationBytes() {
            return Math.multiplyExact((long) positionsX.length, Double.BYTES);
        }

        LiveTrackCoordinator.AccuracySummary accuracySummary() {
            return coordinator.accuracySummary();
        }

        long largestTrackAllocationBytes() {
            return coordinator.largestAllocation();
        }

        void beginMeasurementWindow() {
            synchronized (telemetryMonitor) {
                coordinator.resetEvidenceMetrics();
                handoff.resetEvidenceMetrics();
                captureCoordinatorTelemetryLocked();
            }
        }

        LiveTrackTelemetry telemetry(long nowNanos) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            synchronized (telemetryMonitor) {
                return new LiveTrackTelemetry(
                        observedState,
                        config.population(),
                        config.seed(),
                        config.workers(),
                        observedSimulationSecond,
                        observedScheduledReports,
                        observedProcessedReports,
                        observedRejectedReports,
                        observedLateReports,
                        observedPendingReports,
                        Math.max(0, observedRequestedSimulationSecond - observedSimulationSecond),
                        new LiveTrackShardMetrics(
                                config.workers(),
                                shardMetrics[0],
                                shardMetrics[1],
                                shardMetrics[2],
                                shardMetrics[3]),
                        coordinator.logicalBytes(),
                        Math.multiplyExact(
                                Math.addExact((long) positionsX.length, positionsY.length),
                                Double.BYTES),
                        maximumHeap,
                        used,
                        handoff.metrics(nowNanos),
                        failureCategory(failure));
            }
        }

        boolean awaitIdle(long timeoutMillis) {
            if (timeoutMillis < 0L) {
                throw new IllegalArgumentException("timeoutMillis must be non-negative");
            }
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
            synchronized (monitor) {
                while (!closed
                        && (pendingDemand != null
                                || pendingControl != null
                                || building
                                || handoff.requestActive())) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        return false;
                    }
                    try {
                        long millis = Math.max(1L, remaining / 1_000_000L);
                        monitor.wait(millis);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "interrupted while awaiting frame work", exception);
                    }
                }
                return !building && pendingDemand == null && pendingControl == null;
            }
        }

        boolean producerAlive() {
            return producer.isAlive();
        }

        boolean workersTerminated() {
            return !producer.isAlive() && coordinator.liveWorkerCount() == 0;
        }

        void failWorkerForTest(int workerIndex) {
            coordinator.failWorkerForTest(workerIndex);
        }

        @Override
        public void close() {
            boolean interrupted = false;
            synchronized (monitor) {
                if (closed) {
                    while (!closeComplete) {
                        try {
                            monitor.wait();
                        } catch (InterruptedException exception) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                closed = true;
                pendingDemand = null;
                pendingControl = null;
                handoff.cancelRequest();
                monitor.notifyAll();
            }
            producer.interrupt();
            Throwable primary = null;
            try {
                coordinator.close();
            } catch (RuntimeException | Error closeFailure) {
                primary = closeFailure;
            }
            while (producer.isAlive()) {
                try {
                    producer.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            try {
                handoff.close();
            } catch (RuntimeException | Error closeFailure) {
                if (primary == null) {
                    primary = closeFailure;
                } else {
                    primary.addSuppressed(closeFailure);
                }
            }
            synchronized (telemetryMonitor) {
                observedState = LiveTrackCoordinator.State.CLOSED;
            }
            synchronized (monitor) {
                closeComplete = true;
                monitor.notifyAll();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (primary instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (primary instanceof Error error) {
                throw error;
            }
        }

        private boolean request(FrameDemand demand) {
            synchronized (monitor) {
                if (closed || failure != null) {
                    return false;
                }
                if (pendingDemand != null || building || !handoff.beginRequest()) {
                    handoff.recordSkippedRequest();
                    return false;
                }
                pendingDemand = demand;
                monitor.notifyAll();
                return true;
            }
        }

        private boolean requestControl(Control control, long nowNanos) {
            synchronized (monitor) {
                if (closed || failure != null || pendingControl != null) {
                    return false;
                }
                if (pendingDemand != null) {
                    pendingDemand = null;
                    handoff.cancelQueuedRequest();
                }
                pendingControl = control;
                controlNowNanos = nowNanos;
                monitor.notifyAll();
                return true;
            }
        }

        private void run() {
            while (true) {
                beforeWorkSelection.run();
                Work work;
                synchronized (monitor) {
                    while (!closed && pendingControl == null && pendingDemand == null) {
                        try {
                            monitor.wait();
                        } catch (InterruptedException exception) {
                            if (closed) {
                                return;
                            }
                        }
                    }
                    if (closed) {
                        return;
                    }
                    if (pendingControl != null) {
                        work = new Work(pendingControl, controlNowNanos, null);
                        pendingControl = null;
                    } else {
                        work = new Work(null, 0L, pendingDemand);
                        pendingDemand = null;
                    }
                    building = true;
                }
                try {
                    if (work.control() != null) {
                        processControl(work.control(), work.controlNowNanos());
                    } else {
                        build(Objects.requireNonNull(work.demand(), "demand"));
                    }
                } catch (RuntimeException exception) {
                    if (!closed) {
                        synchronized (telemetryMonitor) {
                            failure = exception;
                            observedState = LiveTrackCoordinator.State.FAILED;
                        }
                    }
                    handoff.cancelRequest();
                } finally {
                    synchronized (monitor) {
                        building = false;
                        monitor.notifyAll();
                    }
                }
            }
        }

        private void processControl(Control control, long nowNanos) {
            switch (control) {
                case PAUSE -> {
                    if (coordinator.state() == LiveTrackCoordinator.State.RUNNING) {
                        coordinator.advanceRealTime(nowNanos);
                        coordinator.pause(nowNanos);
                    }
                }
                case RESUME -> {
                    if (coordinator.state() == LiveTrackCoordinator.State.PAUSED) {
                        coordinator.resume(nowNanos);
                    }
                }
                case RESET -> {
                    if (coordinator.state() == LiveTrackCoordinator.State.RUNNING) {
                        coordinator.advanceRealTime(nowNanos);
                        coordinator.pause(nowNanos);
                    }
                    coordinator.reset();
                    coordinator.start(nowNanos);
                    handoff.invalidateFrames();
                    synchronized (telemetryMonitor) {
                        observedRequestedSimulationSecond = 0;
                    }
                }
            }
            captureCoordinatorTelemetry();
        }

        private void build(FrameDemand demand) {
            long started = System.nanoTime();
            double requestedTimestamp =
                    demand.virtualSecond() >= 0
                            ? demand.virtualSecond()
                            : coordinator.displayTimestampSeconds(demand.nowNanos());
            synchronized (telemetryMonitor) {
                observedRequestedSimulationSecond = (int) StrictMath.floor(requestedTimestamp);
            }
            if (coordinator.state() == LiveTrackCoordinator.State.RUNNING) {
                if (demand.virtualSecond() >= 0) {
                    coordinator.advanceTo(demand.virtualSecond());
                } else {
                    coordinator.advanceRealTime(demand.nowNanos());
                }
            }
            int displaySecond = coordinator.simulationSecond();
            double displayTimestamp =
                    demand.virtualSecond() >= 0
                            ? displaySecond
                            : coordinator.displayTimestampSeconds(demand.nowNanos());
            coordinator.copyDisplayPositions(displayTimestamp, positionsX, positionsY);
            FrameBuffer buffer = handoff.acquireProducerBuffer(demand.viewport());
            try {
                LiveTrackRasterizer.render(
                        positionsX, positionsY, demand.viewport(), buffer.pixels());
                long duration = System.nanoTime() - started;
                handoff.publish(
                        buffer, demand.viewport(), displayTimestamp, config.population(), duration);
                buffer = null;
                captureCoordinatorTelemetry();
            } finally {
                if (buffer != null) {
                    handoff.abandon(buffer);
                }
            }
        }

        private void captureCoordinatorTelemetry() {
            synchronized (telemetryMonitor) {
                captureCoordinatorTelemetryLocked();
            }
        }

        private void captureCoordinatorTelemetryLocked() {
            observedState = coordinator.state();
            observedSimulationSecond = coordinator.simulationSecond();
            observedScheduledReports = coordinator.scheduledReports();
            observedProcessedReports = coordinator.processedReports();
            observedRejectedReports = coordinator.rejectedReports();
            observedLateReports = coordinator.lateReports();
            coordinator.copyShardMetrics(shardMetrics);
            observedPendingReports = coordinator.pendingReports();
        }

        private static void preflight(long trackBytes, long positionBytes, long maximumHeap) {
            long logical = Math.addExact(trackBytes, positionBytes);
            long withHeadroom = Math.addExact(logical, HEADROOM);
            long sixtyPercent = maximumHeap / 5L * 3L;
            if (withHeadroom > maximumHeap || logical > sixtyPercent) {
                throw new IllegalArgumentException("LIVE_TRACK_DISPLAY_STORAGE_LIMIT");
            }
        }

        private static String failureCategory(RuntimeException currentFailure) {
            if (currentFailure == null) {
                return "";
            }
            String message = currentFailure.getMessage();
            return message == null ? currentFailure.getClass().getSimpleName() : message;
        }

        private record FrameDemand(LiveTrackViewport viewport, long nowNanos, int virtualSecond) {
            private FrameDemand {
                Objects.requireNonNull(viewport, "viewport");
            }
        }

        private record Work(Control control, long controlNowNanos, FrameDemand demand) {}
    }

    static final class LiveTrackFrameHandoff implements AutoCloseable {
        private static final int MAX_BUFFERS = 3;
        private static final int LATENCY_SAMPLES = 1_024;
        private static final int PAINT_SAMPLES = 512;
        private static final long HEADROOM = 256L * 1024L * 1024L;

        private final long maximumHeap;
        private final long baseLogicalBytes;
        private final ArrayDeque<FrameBuffer> available = new ArrayDeque<>();
        private final long[] latencyNanos = new long[LATENCY_SAMPLES];
        private final long[] paintNanos = new long[PAINT_SAMPLES];
        private int allocatedBuffers;
        private TrackFrame pending;
        private TrackFrame current;
        private boolean requestActive;
        private boolean closed;
        private long requestedFrames;
        private long completedFrames;
        private long paintedFrames;
        private long skippedRequests;
        private long staleDiscards;
        private long replacedPendingFrames;
        private long latencyCount;
        private long paintCount;
        private long frameBufferBytes;

        LiveTrackFrameHandoff(long maximumHeap, long baseLogicalBytes) {
            if (maximumHeap <= 0L || baseLogicalBytes < 0L) {
                throw new IllegalArgumentException("invalid frame handoff memory limits");
            }
            this.maximumHeap = maximumHeap;
            this.baseLogicalBytes = baseLogicalBytes;
        }

        synchronized boolean beginRequest() {
            if (closed || requestActive) {
                return false;
            }
            requestActive = true;
            requestedFrames++;
            return true;
        }

        synchronized boolean requestActive() {
            return requestActive;
        }

        synchronized long completedFrames() {
            return completedFrames;
        }

        synchronized void recordSkippedRequest() {
            skippedRequests++;
        }

        synchronized FrameBuffer acquireProducerBuffer(LiveTrackViewport viewport) {
            requireRequest();
            int width = viewport.width();
            int height = viewport.height();
            long bytes = frameBytes(width, height);
            preflightFrameBytes(bytes);
            FrameBuffer matching = null;
            int availableCount = available.size();
            for (int index = 0; index < availableCount; index++) {
                FrameBuffer candidate = available.removeFirst();
                if (matching == null && candidate.matches(width, height)) {
                    matching = candidate;
                } else if (candidate.matches(width, height)) {
                    available.addLast(candidate);
                } else {
                    allocatedBuffers--;
                    frameBufferBytes -= candidate.bytes();
                }
            }
            if (matching != null) {
                return matching;
            }
            if (allocatedBuffers >= MAX_BUFFERS) {
                throw new IllegalStateException("LIVE_TRACK_FRAME_BUFFER_EXHAUSTED");
            }
            FrameBuffer created = new FrameBuffer(width, height);
            allocatedBuffers++;
            frameBufferBytes = Math.addExact(frameBufferBytes, created.bytes());
            return created;
        }

        synchronized void publish(
                FrameBuffer buffer,
                LiveTrackViewport viewport,
                double displayTimestampSeconds,
                int population,
                long buildNanos) {
            Objects.requireNonNull(buffer, "buffer");
            requireRequest();
            if (!buffer.matches(viewport.width(), viewport.height())) {
                throw new IllegalArgumentException("frame dimensions do not match request");
            }
            requestActive = false;
            if (closed) {
                recycle(buffer);
                return;
            }
            if (pending != null) {
                recycle(pending.buffer());
                replacedPendingFrames++;
            }
            pending =
                    new TrackFrame(
                            buffer,
                            viewport.generation(),
                            viewport.width(),
                            viewport.height(),
                            displayTimestampSeconds,
                            population,
                            completedFrames + 1L);
            completedFrames++;
            latencyNanos[(int) (latencyCount % LATENCY_SAMPLES)] = buildNanos;
            latencyCount++;
        }

        synchronized BufferedImage frameForPaint(
                long generation, int width, int height, long nowNanos) {
            if (closed) {
                return null;
            }
            if (current != null && !current.matches(generation, width, height)) {
                recycle(current.buffer());
                current = null;
            }
            if (pending != null) {
                if (!pending.matches(generation, width, height)) {
                    recycle(pending.buffer());
                    pending = null;
                    staleDiscards++;
                } else {
                    if (current != null) {
                        recycle(current.buffer());
                    }
                    current = pending;
                    pending = null;
                    paintedFrames++;
                    paintNanos[(int) (paintCount % PAINT_SAMPLES)] = nowNanos;
                    paintCount++;
                }
            }
            return current == null ? null : current.buffer().image();
        }

        synchronized double currentDisplayTimestampSeconds() {
            return current == null ? Double.NaN : current.displayTimestampSeconds();
        }

        synchronized long currentSequence() {
            return current == null ? -1L : current.sequence();
        }

        synchronized void cancelRequest() {
            requestActive = false;
        }

        synchronized void cancelQueuedRequest() {
            requireRequest();
            requestActive = false;
            skippedRequests++;
        }

        synchronized void invalidateFrames() {
            if (pending != null) {
                recycle(pending.buffer());
                pending = null;
            }
            if (current != null) {
                recycle(current.buffer());
                current = null;
            }
        }

        synchronized void abandon(FrameBuffer buffer) {
            requestActive = false;
            recycle(buffer);
        }

        synchronized LiveTrackFrameMetrics metrics(long nowNanos) {
            int latencySize = (int) Math.min(latencyCount, LATENCY_SAMPLES);
            long[] ordered = Arrays.copyOf(latencyNanos, latencySize);
            Arrays.sort(ordered);
            return new LiveTrackFrameMetrics(
                    requestedFrames,
                    completedFrames,
                    paintedFrames,
                    skippedRequests,
                    staleDiscards,
                    replacedPendingFrames,
                    achievedFps(nowNanos),
                    quantile(ordered, 0.50),
                    quantile(ordered, 0.95),
                    quantile(ordered, 0.99),
                    ordered.length == 0 ? 0L : ordered[ordered.length - 1],
                    allocatedBuffers,
                    frameBufferBytes);
        }

        synchronized void resetEvidenceMetrics() {
            requestedFrames = 0L;
            completedFrames = 0L;
            paintedFrames = 0L;
            skippedRequests = 0L;
            staleDiscards = 0L;
            replacedPendingFrames = 0L;
            latencyCount = 0L;
            paintCount = 0L;
            Arrays.fill(latencyNanos, 0L);
            Arrays.fill(paintNanos, 0L);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            requestActive = false;
            if (pending != null) {
                recycle(pending.buffer());
                pending = null;
            }
            if (current != null) {
                recycle(current.buffer());
                current = null;
            }
            available.clear();
            allocatedBuffers = 0;
            frameBufferBytes = 0L;
        }

        synchronized boolean isClosed() {
            return closed;
        }

        private double achievedFps(long nowNanos) {
            return sampleRate(paintNanos, paintCount, nowNanos);
        }

        private void preflightFrameBytes(long oneBufferBytes) {
            long threeBuffers = Math.multiplyExact(oneBufferBytes, MAX_BUFFERS);
            long logical = Math.addExact(baseLogicalBytes, threeBuffers);
            long withHeadroom = Math.addExact(logical, HEADROOM);
            long sixtyPercent = maximumHeap / 5L * 3L;
            if (withHeadroom > maximumHeap || logical > sixtyPercent) {
                throw new IllegalArgumentException("LIVE_TRACK_FRAME_STORAGE_LIMIT");
            }
        }

        private void requireRequest() {
            if (!requestActive) {
                throw new IllegalStateException("no active frame request");
            }
        }

        private void recycle(FrameBuffer buffer) {
            available.addLast(buffer);
        }

        private static long frameBytes(int width, int height) {
            return Math.multiplyExact(Math.multiplyExact((long) width, height), Integer.BYTES);
        }

        private static long quantile(long[] ordered, double quantile) {
            if (ordered.length == 0) {
                return 0L;
            }
            int index = (int) StrictMath.ceil(quantile * ordered.length) - 1;
            return ordered[Math.max(0, index)];
        }

        private record TrackFrame(
                FrameBuffer buffer,
                long generation,
                int width,
                int height,
                double displayTimestampSeconds,
                int population,
                long sequence) {
            private TrackFrame {
                Objects.requireNonNull(buffer, "buffer");
                if (!Double.isFinite(displayTimestampSeconds) || displayTimestampSeconds < 0.0) {
                    throw new IllegalArgumentException("invalid display timestamp");
                }
            }

            boolean matches(long expectedGeneration, int expectedWidth, int expectedHeight) {
                return generation == expectedGeneration
                        && width == expectedWidth
                        && height == expectedHeight;
            }
        }
    }

    static final class FrameBuffer {
        private final int width;
        private final int height;
        private final BufferedImage image;
        private final int[] pixels;

        FrameBuffer(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("frame dimensions must be positive");
            }
            long count = Math.multiplyExact((long) width, height);
            if (count > LiveTrackViewport.MAX_PIXELS) {
                throw new IllegalArgumentException("LIVE_TRACK_FRAME_PIXEL_LIMIT");
            }
            this.width = width;
            this.height = height;
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        }

        boolean matches(int candidateWidth, int candidateHeight) {
            return width == candidateWidth && height == candidateHeight;
        }

        int[] pixels() {
            return pixels;
        }

        BufferedImage image() {
            return image;
        }

        long bytes() {
            return (long) pixels.length * Integer.BYTES;
        }
    }

    static final class LiveTrackRasterizer {
        static final int TRACK_ARGB = 0xff16d9e3;
        private static final HorizontalWrap WRAP = HorizontalWrap.webMercator();

        private LiveTrackRasterizer() {}

        static void render(
                double[] positionsX,
                double[] positionsY,
                LiveTrackViewport viewport,
                int[] targetPixels) {
            Objects.requireNonNull(positionsX, "positionsX");
            Objects.requireNonNull(positionsY, "positionsY");
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(targetPixels, "targetPixels");
            if (positionsX.length != positionsY.length
                    || targetPixels.length != viewport.width() * viewport.height()) {
                throw new IllegalArgumentException(
                        "packed frame arrays have inconsistent dimensions");
            }
            Arrays.fill(targetPixels, 0);
            MapViewport transform = viewport.viewport();
            double halfWidth = viewport.width() / 2.0;
            double halfHeight = viewport.height() / 2.0;
            double units = transform.worldUnitsPerPixel();
            Envelope visible = transform.visibleWorldEnvelope();
            HorizontalWrapPlan plan = WRAP.plan(visible.minX(), visible.maxX(), units);
            for (long copyIndex = plan.minimumVisibleCopyIndex();
                    copyIndex <= plan.maximumVisibleCopyIndex();
                    copyIndex++) {
                double offset =
                        WRAP.translate(WRAP.canonicalMinimumX(), copyIndex)
                                - WRAP.canonicalMinimumX();
                for (int index = 0; index < positionsX.length; index++) {
                    double canonicalX = positionsX[index];
                    if (canonicalX < WRAP.canonicalMinimumX()
                            || canonicalX >= WRAP.canonicalMaximumX()) {
                        canonicalX = WRAP.canonicalize(canonicalX).canonicalX();
                    }
                    double screenX =
                            halfWidth + (canonicalX + offset - transform.centerX()) / units;
                    double screenY = halfHeight - (positionsY[index] - transform.centerY()) / units;
                    if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
                        throw new IllegalStateException("LIVE_TRACK_FRAME_POSITION_NON_FINITE");
                    }
                    if (screenX < -1.0
                            || screenY < -1.0
                            || screenX >= viewport.width()
                            || screenY >= viewport.height()) {
                        continue;
                    }
                    int x = (int) StrictMath.floor(screenX);
                    int y = (int) StrictMath.floor(screenY);
                    plot2x2(targetPixels, viewport.width(), viewport.height(), x, y);
                }
            }
        }

        private static void plot2x2(int[] pixels, int width, int height, int x, int y) {
            for (int offsetY = 0; offsetY < 2; offsetY++) {
                int targetY = y + offsetY;
                if (targetY < 0 || targetY >= height) {
                    continue;
                }
                for (int offsetX = 0; offsetX < 2; offsetX++) {
                    int targetX = x + offsetX;
                    if (targetX >= 0 && targetX < width) {
                        pixels[targetY * width + targetX] = TRACK_ARGB;
                    }
                }
            }
        }
    }

    static final class LiveTrackFramePacer {
        private static final int[] CAPS = {1, 2, 5, 10, 15, 30, 60};

        private int cap;
        private long nextRequestNanos = Long.MIN_VALUE;

        LiveTrackFramePacer(int cap) {
            setCap(cap);
        }

        void setCap(int cap) {
            if (cap != 0 && Arrays.binarySearch(CAPS, cap) < 0) {
                throw new IllegalArgumentException("unsupported FPS cap");
            }
            this.cap = cap;
            nextRequestNanos = Long.MIN_VALUE;
        }

        int cap() {
            return cap;
        }

        boolean shouldRequest(long nowNanos) {
            if (cap == 0) {
                return true;
            }
            if (nextRequestNanos != Long.MIN_VALUE && nowNanos < nextRequestNanos) {
                return false;
            }
            long interval = 1_000_000_000L / cap;
            if (nextRequestNanos == Long.MIN_VALUE) {
                nextRequestNanos = saturatedAdd(nowNanos, interval);
                return true;
            }
            long overdue = nowNanos - nextRequestNanos;
            long intervals = overdue / interval + 1L;
            nextRequestNanos =
                    saturatedAdd(nextRequestNanos, saturatedMultiply(interval, intervals));
            return true;
        }

        private static long saturatedMultiply(long left, long right) {
            if (left != 0L && right > Long.MAX_VALUE / left) {
                return Long.MAX_VALUE;
            }
            return left * right;
        }

        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }

    @SuppressWarnings("serial")
    static final class LiveTrackOverlay extends javax.swing.JComponent {
        private static final int PRESENTATION_SAMPLES = 512;

        private final LiveTrackFrameHandoff handoff;
        private final long[] presentationNanos = new long[PRESENTATION_SAMPLES];
        private final long[] paintDurationNanos = new long[PRESENTATION_SAMPLES];
        private long generation;
        private MapViewport viewport;
        private BufferedImage background;
        private MapViewport backgroundViewport;
        private long lastPresentedSequence = -1L;
        private long presentedFrames;
        private long backgroundRefreshes;
        private long backgroundLastNanos;
        private long backgroundMaximumNanos;

        LiveTrackOverlay(LiveTrackFrameHandoff handoff) {
            this.handoff = Objects.requireNonNull(handoff, "handoff");
            setOpaque(false);
            setFocusable(false);
        }

        boolean installBackground(
                MapViewport renderedViewport, BufferedImage image, long renderNanos) {
            requireEdt();
            Objects.requireNonNull(renderedViewport, "renderedViewport");
            Objects.requireNonNull(image, "image");
            if (image.getWidth() != renderedViewport.width()
                    || image.getHeight() != renderedViewport.height()
                    || renderNanos < 0L) {
                throw new IllegalArgumentException("invalid live-track background snapshot");
            }
            if (viewport == null || !StaticMapBackgroundCache.covers(renderedViewport, viewport)) {
                return false;
            }
            background = image;
            backgroundViewport = renderedViewport;
            backgroundRefreshes++;
            backgroundLastNanos = renderNanos;
            backgroundMaximumNanos = Math.max(backgroundMaximumNanos, renderNanos);
            setOpaque(true);
            return true;
        }

        boolean backgroundCovers(MapViewport candidate) {
            requireEdt();
            return backgroundViewport != null
                    && StaticMapBackgroundCache.covers(backgroundViewport, candidate);
        }

        LiveTrackPresentationMetrics presentationMetrics(long nowNanos) {
            int size = (int) Math.min(presentedFrames, PRESENTATION_SAMPLES);
            long[] ordered = Arrays.copyOf(paintDurationNanos, size);
            Arrays.sort(ordered);
            return new LiveTrackPresentationMetrics(
                    presentedFrames,
                    sampleRate(presentationNanos, presentedFrames, nowNanos),
                    quantile(ordered, 0.50),
                    quantile(ordered, 0.95),
                    quantile(ordered, 0.99),
                    ordered.length == 0 ? 0L : ordered[ordered.length - 1],
                    backgroundRefreshes,
                    backgroundLastNanos,
                    backgroundMaximumNanos);
        }

        void setViewport(long generation, MapViewport viewport) {
            requireEdt();
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            this.generation = generation;
            this.viewport = Objects.requireNonNull(viewport, "viewport");
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            long started = System.nanoTime();
            Graphics2D presentation = (Graphics2D) graphics.create();
            try {
                if (background != null && viewport != null && backgroundViewport != null) {
                    presentation.setColor(getBackground());
                    presentation.fillRect(0, 0, getWidth(), getHeight());
                    presentation.drawImage(
                            background, backgroundTransform(backgroundViewport, viewport), null);
                }
            } finally {
                presentation.dispose();
            }
            BufferedImage frame =
                    handoff.frameForPaint(generation, getWidth(), getHeight(), System.nanoTime());
            if (frame != null) {
                ((Graphics2D) graphics).drawImage(frame, 0, 0, null);
                long sequence = handoff.currentSequence();
                if (sequence != lastPresentedSequence) {
                    long completed = System.nanoTime();
                    int slot = (int) (presentedFrames % PRESENTATION_SAMPLES);
                    presentationNanos[slot] = completed;
                    paintDurationNanos[slot] = completed - started;
                    presentedFrames++;
                    lastPresentedSequence = sequence;
                }
            }
        }

        private static void requireEdt() {
            if (!java.awt.EventQueue.isDispatchThread()) {
                throw new IllegalStateException("live-track overlay mutation must run on the EDT");
            }
        }

        private static AffineTransform backgroundTransform(MapViewport source, MapViewport target) {
            double scale = source.worldUnitsPerPixel() / target.worldUnitsPerPixel();
            double translateX =
                    target.width() / 2.0
                            + (source.centerX() - target.centerX()) / target.worldUnitsPerPixel()
                            - source.width() / 2.0 * scale;
            double translateY =
                    target.height() / 2.0
                            - (source.centerY() - target.centerY()) / target.worldUnitsPerPixel()
                            - source.height() / 2.0 * scale;
            return new AffineTransform(scale, 0.0, 0.0, scale, translateX, translateY);
        }

        private static long quantile(long[] ordered, double quantile) {
            if (ordered.length == 0) {
                return 0L;
            }
            int index = (int) StrictMath.ceil(quantile * ordered.length) - 1;
            return ordered[Math.max(0, index)];
        }
    }

    private static double sampleRate(long[] samples, long count, long nowNanos) {
        long cutoff = nowNanos - 5_000_000_000L;
        int size = (int) Math.min(count, samples.length);
        int retained = 0;
        long first = Long.MAX_VALUE;
        for (int index = 0; index < size; index++) {
            long sample = samples[index];
            if (sample >= cutoff && sample <= nowNanos) {
                retained++;
                first = Math.min(first, sample);
            }
        }
        return retained < 2 || nowNanos == first
                ? 0.0
                : (retained - 1L) * 1_000_000_000.0 / (nowNanos - first);
    }
}
