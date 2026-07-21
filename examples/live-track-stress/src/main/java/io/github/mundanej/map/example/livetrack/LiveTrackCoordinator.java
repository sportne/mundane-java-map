package io.github.mundanej.map.example.livetrack;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Owns stable simulator shards and coordinates deterministic virtual or real-time advancement. */
final class LiveTrackCoordinator implements AutoCloseable {
    private static final int MAX_SIMULATION_SECOND = Integer.MAX_VALUE - 60;

    enum State {
        NEW,
        RUNNING,
        PAUSED,
        RESETTING,
        FAILED,
        CLOSED
    }

    record AccuracySummary(
            double positionRmse,
            long innovationCount,
            double normalizedInnovationMean,
            double normalizedInnovationMaximum) {}

    private final TrackSimulationConfig config;
    private final TrackStoragePlan storagePlan;
    private TrackShard[] shards;
    private volatile ShardWorker[] workers;
    private State state = State.NEW;
    private int simulationSecond;
    private long realTimeOriginNanos;
    private long pausedElapsedNanos;
    private boolean advancing;
    private boolean transitioning;
    private AtomicReference<RuntimeException> runFailure;

    LiveTrackCoordinator(TrackSimulationConfig config) {
        this(config, Runtime.getRuntime().maxMemory());
    }

    LiveTrackCoordinator(TrackSimulationConfig config, long maximumHeap) {
        this(config, TrackStoragePlan.preflight(config, maximumHeap));
    }

    LiveTrackCoordinator(TrackSimulationConfig config, TrackStoragePlan storagePlan) {
        this.config = Objects.requireNonNull(config, "config");
        this.storagePlan = Objects.requireNonNull(storagePlan, "storagePlan");
        shards = createShards(config);
    }

    synchronized void start(long nowNanos) {
        requireState(State.NEW);
        runFailure = new AtomicReference<>();
        workers = new ShardWorker[shards.length];
        for (int index = 0; index < shards.length; index++) {
            workers[index] = new ShardWorker(index, shards[index], runFailure, this::workerFailed);
            workers[index].start();
        }
        pausedElapsedNanos = Math.multiplyExact((long) simulationSecond, 1_000_000_000L);
        realTimeOriginNanos = Math.subtractExact(nowNanos, pausedElapsedNanos);
        state = State.RUNNING;
    }

    synchronized void resume(long nowNanos) {
        requireState(State.PAUSED);
        realTimeOriginNanos = Math.subtractExact(nowNanos, pausedElapsedNanos);
        state = State.RUNNING;
    }

    void advanceTo(int targetSecond) {
        advance(targetSecond, false);
    }

    private void advance(int targetSecond, boolean realTime) {
        ShardWorker[] activeWorkers;
        synchronized (this) {
            requireState(State.RUNNING);
            if (targetSecond < simulationSecond) {
                throw new IllegalArgumentException("targetSecond cannot move backwards");
            }
            if (targetSecond > MAX_SIMULATION_SECOND) {
                throw new IllegalArgumentException("targetSecond exceeds the supported time range");
            }
            if (advancing) {
                throw new IllegalStateException("a live-track advance is already in progress");
            }
            if (targetSecond == simulationSecond) {
                return;
            }
            advancing = true;
            activeWorkers = workers.clone();
            for (ShardWorker worker : activeWorkers) {
                worker.request(targetSecond, realTime ? targetSecond : Integer.MIN_VALUE);
            }
        }
        RuntimeException failure = null;
        for (ShardWorker worker : activeWorkers) {
            try {
                worker.await(targetSecond);
            } catch (RuntimeException exception) {
                failure = exception;
                break;
            }
        }
        ShardWorker[] workersToJoin = null;
        RuntimeException outcome = failure;
        synchronized (this) {
            if (failure != null) {
                if (state != State.CLOSED) {
                    state = State.FAILED;
                }
                workersToJoin = workers;
                workers = null;
                transitioning = workersToJoin != null;
            } else if (state != State.RUNNING) {
                outcome = new IllegalStateException("live-track advance was cancelled");
            } else {
                simulationSecond = targetSecond;
            }
            if (workersToJoin == null) {
                advancing = false;
                notifyAll();
            }
        }
        if (workersToJoin != null) {
            boolean interrupted = stopAndJoin(workersToJoin);
            synchronized (this) {
                transitioning = false;
                advancing = false;
                notifyAll();
            }
            restoreInterrupt(interrupted);
        }
        if (outcome != null) {
            throw outcome;
        }
    }

    void advanceRealTime(long nowNanos) {
        int dueSecond;
        synchronized (this) {
            requireState(State.RUNNING);
            long elapsed = elapsedNanos(nowNanos);
            long due = elapsed / 1_000_000_000L;
            if (due > MAX_SIMULATION_SECOND) {
                throw new IllegalArgumentException("real-time target exceeds supported simulation");
            }
            dueSecond = (int) due;
        }
        advance(dueSecond, true);
    }

    synchronized void pause(long nowNanos) {
        requireState(State.RUNNING);
        while (advancing) {
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while pausing live tracks", exception);
            }
            requireState(State.RUNNING);
        }
        pausedElapsedNanos = elapsedNanos(nowNanos);
        state = State.PAUSED;
    }

    void reset() {
        ShardWorker[] workersToJoin;
        synchronized (this) {
            if (state != State.PAUSED && state != State.NEW) {
                throw new IllegalStateException("reset requires a new or paused coordinator");
            }
            state = State.RESETTING;
            transitioning = true;
            workersToJoin = workers;
            workers = null;
        }
        boolean interrupted = stopAndJoin(workersToJoin);
        synchronized (this) {
            if (state != State.CLOSED) {
                for (TrackShard shard : shards) {
                    shard.reset();
                }
                simulationSecond = 0;
                pausedElapsedNanos = 0L;
                runFailure = null;
                state = State.NEW;
            }
            transitioning = false;
            notifyAll();
        }
        restoreInterrupt(interrupted);
    }

    synchronized State state() {
        return state;
    }

    synchronized int simulationSecond() {
        return simulationSecond;
    }

    synchronized double displayTimestampSeconds(long nowNanos) {
        requireQuiescentRead();
        if (state == State.PAUSED) {
            return pausedElapsedNanos / 1_000_000_000.0;
        }
        requireState(State.RUNNING);
        long elapsed = elapsedNanos(nowNanos);
        double timestamp = elapsed / 1_000_000_000.0;
        if (!Double.isFinite(timestamp) || timestamp > MAX_SIMULATION_SECOND) {
            throw new IllegalArgumentException("real-time target exceeds supported simulation");
        }
        return timestamp;
    }

    synchronized boolean isAdvancing() {
        return advancing;
    }

    synchronized int liveWorkerCount() {
        if (workers == null) {
            return 0;
        }
        int alive = 0;
        for (ShardWorker worker : workers) {
            if (worker.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    synchronized long initializationReports() {
        requireQuiescentRead();
        return sum(TrackShard::initializationReports);
    }

    synchronized long scheduledReports() {
        requireQuiescentRead();
        return sum(TrackShard::scheduledReports);
    }

    synchronized long processedReports() {
        requireQuiescentRead();
        return sum(TrackShard::processedReports);
    }

    synchronized long rejectedReports() {
        requireQuiescentRead();
        return sum(TrackShard::rejectedReports);
    }

    synchronized long tracksVisited() {
        requireQuiescentRead();
        return sum(TrackShard::tracksVisited);
    }

    synchronized long pendingReports() {
        requireQuiescentRead();
        return sum(TrackShard::pendingReports);
    }

    synchronized long logicalBytes() {
        return storagePlan.logicalBytes();
    }

    synchronized long largestAllocation() {
        return storagePlan.largestAllocation();
    }

    synchronized int shardIdentity() {
        requireQuiescentRead();
        int identity = 1;
        for (TrackShard shard : shards) {
            identity = 31 * identity + System.identityHashCode(shard);
        }
        return identity;
    }

    synchronized long lateReports() {
        requireQuiescentRead();
        return sum(TrackShard::lateReports);
    }

    synchronized void copyShardMetrics(long[] target) {
        requireQuiescentRead();
        if (target.length < 4) {
            throw new IllegalArgumentException("shard metrics target needs four entries");
        }
        long minimumReports = Long.MAX_VALUE;
        long maximumReports = 0L;
        long minimumWork = Long.MAX_VALUE;
        long maximumWork = 0L;
        for (int index = 0; index < shards.length; index++) {
            long reports = shards[index].evidenceProcessedReports();
            minimumReports = Math.min(minimumReports, reports);
            maximumReports = Math.max(maximumReports, reports);
            long work = workers == null ? 0L : workers[index].workNanos();
            minimumWork = Math.min(minimumWork, work);
            maximumWork = Math.max(maximumWork, work);
        }
        target[0] = minimumReports == Long.MAX_VALUE ? 0L : minimumReports;
        target[1] = maximumReports;
        target[2] = minimumWork == Long.MAX_VALUE ? 0L : minimumWork;
        target[3] = maximumWork;
    }

    synchronized AccuracySummary accuracySummary() {
        requireQuiescentRead();
        double[] totals = new double[5];
        for (TrackShard shard : shards) {
            shard.addAccuracy(totals);
        }
        double positionRmse = totals[1] == 0.0 ? 0.0 : StrictMath.sqrt(totals[0] / totals[1]);
        double innovationMean = totals[3] == 0.0 ? 0.0 : totals[2] / totals[3];
        return new AccuracySummary(positionRmse, (long) totals[3], innovationMean, totals[4]);
    }

    synchronized void resetEvidenceMetrics() {
        requireQuiescentRead();
        for (TrackShard shard : shards) {
            shard.resetEvidenceMetrics();
        }
        if (workers != null) {
            for (ShardWorker worker : workers) {
                worker.resetWorkNanos();
            }
        }
    }

    synchronized long checksum() {
        requireQuiescentRead();
        long value = 1L;
        for (TrackShard shard : shards) {
            value = shard.checksum(value);
        }
        return value;
    }

    synchronized double truthX(int trackId) {
        requireQuiescentRead();
        return shard(trackId).truthX(trackId);
    }

    synchronized double truthY(int trackId) {
        requireQuiescentRead();
        return shard(trackId).truthY(trackId);
    }

    synchronized double estimatedX(int trackId) {
        requireQuiescentRead();
        return shard(trackId).estimatedX(trackId);
    }

    synchronized double estimatedY(int trackId) {
        requireQuiescentRead();
        return shard(trackId).estimatedY(trackId);
    }

    synchronized void copyDisplayPositions(
            double timestampSeconds, double[] positionsX, double[] positionsY) {
        requireQuiescentRead();
        if (positionsX.length != config.population() || positionsY.length != config.population()) {
            throw new IllegalArgumentException("position arrays have the wrong length");
        }
        int offset = 0;
        for (TrackShard shard : shards) {
            shard.copyDisplayPositions(timestampSeconds, positionsX, positionsY, offset);
            offset += shard.trackCount();
        }
    }

    int population() {
        return config.population();
    }

    int workerCount() {
        return config.workers();
    }

    long seed() {
        return config.seed();
    }

    synchronized int nextDueSecond(int trackId) {
        requireQuiescentRead();
        return shard(trackId).nextDueSecond(trackId);
    }

    synchronized long sequence(int trackId) {
        requireQuiescentRead();
        return shard(trackId).sequence(trackId);
    }

    synchronized void failWorkerForTest(int workerIndex) {
        if (workers == null || workerIndex < 0 || workerIndex >= workers.length) {
            throw new IndexOutOfBoundsException("workerIndex");
        }
        workers[workerIndex].failNextRequest();
    }

    @Override
    public void close() {
        ShardWorker[] workersToJoin;
        boolean interrupted = false;
        synchronized (this) {
            state = State.CLOSED;
            while (transitioning) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (workers == null) {
                advancing = false;
                notifyAll();
                restoreInterrupt(interrupted);
                return;
            }
            transitioning = true;
            workersToJoin = workers;
            workers = null;
        }
        interrupted |= stopAndJoin(workersToJoin);
        synchronized (this) {
            transitioning = false;
            advancing = false;
            notifyAll();
        }
        restoreInterrupt(interrupted);
    }

    private void workerFailed(RuntimeException failure) {
        ShardWorker[] current = workers;
        ShardWorker[] activeWorkers = current == null ? new ShardWorker[0] : current.clone();
        for (ShardWorker worker : activeWorkers) {
            worker.stop();
        }
    }

    private TrackShard shard(int trackId) {
        if (trackId < 0 || trackId >= config.population()) {
            throw new IndexOutOfBoundsException("trackId");
        }
        int quotient = config.population() / config.workers();
        int remainder = config.population() % config.workers();
        int largerBoundary = (quotient + 1) * remainder;
        int shardIndex =
                trackId < largerBoundary
                        ? trackId / (quotient + 1)
                        : remainder + (trackId - largerBoundary) / quotient;
        return shards[shardIndex];
    }

    private long sum(ShardMetric metric) {
        long total = 0L;
        for (TrackShard shard : shards) {
            total = Math.addExact(total, metric.value(shard));
        }
        return total;
    }

    private void requireState(State required) {
        if (state != required) {
            throw new IllegalStateException(
                    "coordinator state is " + state + ", expected " + required);
        }
    }

    private void requireQuiescentRead() {
        if (advancing || transitioning) {
            throw new IllegalStateException("live-track state is changing");
        }
    }

    private long elapsedNanos(long nowNanos) {
        long elapsed;
        try {
            elapsed = Math.subtractExact(nowNanos, realTimeOriginNanos);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "real-time target exceeds supported simulation", exception);
        }
        if (elapsed < 0L) {
            throw new IllegalArgumentException("real time cannot precede the run origin");
        }
        return elapsed;
    }

    private static boolean stopAndJoin(ShardWorker[] workersToJoin) {
        if (workersToJoin == null) {
            return false;
        }
        for (ShardWorker worker : workersToJoin) {
            worker.stop();
        }
        boolean interrupted = false;
        for (ShardWorker worker : workersToJoin) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }
        return interrupted;
    }

    private static void restoreInterrupt(boolean interrupted) {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static TrackShard[] createShards(TrackSimulationConfig config) {
        TrackShard[] result = new TrackShard[config.workers()];
        int quotient = config.population() / config.workers();
        int remainder = config.population() % config.workers();
        int first = 0;
        for (int index = 0; index < result.length; index++) {
            int count = quotient + (index < remainder ? 1 : 0);
            result[index] = new TrackShard(first, count, config.seed(), config.filterConfig());
            first += count;
        }
        return result;
    }

    @FunctionalInterface
    private interface ShardMetric {
        long value(TrackShard shard);
    }

    private static final class ShardWorker implements Runnable {
        private final Object monitor = new Object();
        private final TrackShard shard;
        private final Thread thread;
        private final AtomicReference<RuntimeException> sharedFailure;
        private final WorkerFailureHandler failureHandler;
        private int requestedSecond;
        private int lateBeforeSecond = Integer.MIN_VALUE;
        private int completedSecond;
        private boolean stop;
        private boolean failNextRequest;
        private long workNanos;

        void resetWorkNanos() {
            synchronized (monitor) {
                workNanos = 0L;
            }
        }

        ShardWorker(
                int index,
                TrackShard shard,
                AtomicReference<RuntimeException> sharedFailure,
                WorkerFailureHandler failureHandler) {
            this.shard = shard;
            this.sharedFailure = sharedFailure;
            this.failureHandler = failureHandler;
            thread = new Thread(this, "live-track-shard-" + index);
            thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        void request(int targetSecond, int requestLateBeforeSecond) {
            synchronized (monitor) {
                if (targetSecond > requestedSecond) {
                    requestedSecond = targetSecond;
                    lateBeforeSecond = requestLateBeforeSecond;
                }
                monitor.notifyAll();
            }
        }

        void await(int targetSecond) {
            synchronized (monitor) {
                while (completedSecond < targetSecond && sharedFailure.get() == null && !stop) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "interrupted while awaiting shard", exception);
                    }
                }
            }
            RuntimeException thrown = sharedFailure.get();
            if (thrown != null) {
                throw thrown;
            }
            if (completedSecond < targetSecond) {
                throw new IllegalStateException("shard stopped before target");
            }
        }

        void failNextRequest() {
            synchronized (monitor) {
                failNextRequest = true;
            }
        }

        void stop() {
            synchronized (monitor) {
                stop = true;
                thread.interrupt();
                monitor.notifyAll();
            }
        }

        void join() throws InterruptedException {
            thread.join();
        }

        boolean isAlive() {
            return thread.isAlive();
        }

        long workNanos() {
            synchronized (monitor) {
                return workNanos;
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int target;
                    int lateBefore;
                    synchronized (monitor) {
                        while (!stop && requestedSecond <= completedSecond) {
                            monitor.wait();
                        }
                        if (stop) {
                            return;
                        }
                        if (failNextRequest) {
                            failNextRequest = false;
                            throw new IllegalStateException("LIVE_TRACK_WORKER_FAILURE");
                        }
                        target = requestedSecond;
                        lateBefore = lateBeforeSecond;
                    }
                    long workStarted = System.nanoTime();
                    try {
                        while (completedSecond < target) {
                            int nextSecond = completedSecond + 1;
                            shard.advanceSecond(nextSecond, nextSecond < lateBefore);
                            synchronized (monitor) {
                                completedSecond++;
                                monitor.notifyAll();
                            }
                        }
                    } finally {
                        synchronized (monitor) {
                            workNanos = Math.addExact(workNanos, System.nanoTime() - workStarted);
                        }
                    }
                }
            } catch (InterruptedException exception) {
                if (!stop) {
                    recordFailure(
                            new IllegalStateException("live-track worker interrupted", exception));
                }
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                recordFailure(exception);
            }
        }

        private void recordFailure(RuntimeException exception) {
            boolean first = sharedFailure.compareAndSet(null, exception);
            synchronized (monitor) {
                if (first) {
                    stop = true;
                }
                monitor.notifyAll();
            }
            if (first) {
                failureHandler.failed(exception);
            }
        }
    }

    @FunctionalInterface
    private interface WorkerFailureHandler {
        void failed(RuntimeException failure);
    }
}
