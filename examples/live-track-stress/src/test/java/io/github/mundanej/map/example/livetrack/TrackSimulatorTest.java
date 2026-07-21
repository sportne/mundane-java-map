package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrackSimulatorTest {
    @Test
    void deterministicStreamsAreIndependentAndIntervalsMatchProfile() {
        long seed = TrackSimulationConfig.REFERENCE_SEED;
        double replay = DeterministicDraws.uniform(seed, 7, 3L, 0, DeterministicDraws.SCHEDULE_TAG);
        assertEquals(
                replay,
                DeterministicDraws.uniform(seed, 7, 3L, 0, DeterministicDraws.SCHEDULE_TAG));
        assertNotEquals(
                replay,
                DeterministicDraws.uniform(seed, 8, 3L, 0, DeterministicDraws.SCHEDULE_TAG));
        assertNotEquals(
                replay,
                DeterministicDraws.uniform(seed, 7, 4L, 0, DeterministicDraws.SCHEDULE_TAG));
        assertNotEquals(
                DeterministicDraws.gaussian(seed, 7, 3L, 0, 0, DeterministicDraws.TRUTH_TAG),
                DeterministicDraws.gaussian(seed, 7, 3L, 0, 1, DeterministicDraws.TRUTH_TAG));
        assertEquals(0.46760007764922706, replay);
        assertEquals(
                0.18489194145480786,
                DeterministicDraws.gaussian(seed, 7, 3L, 0, 0, DeterministicDraws.TRUTH_TAG),
                1.0e-15);
        assertEquals(
                -0.5120712039087338,
                DeterministicDraws.gaussian(seed, 7, 3L, 0, 1, DeterministicDraws.TRUTH_TAG),
                1.0e-15);
        assertEquals(6, DeterministicDraws.intervalSeconds(seed, 7, 3L));

        long total = 0L;
        int maximum = 0;
        int minimum = Integer.MAX_VALUE;
        for (int track = 0; track < 100_000; track++) {
            int interval = DeterministicDraws.intervalSeconds(seed, track, track % 17L);
            minimum = Math.min(minimum, interval);
            maximum = Math.max(maximum, interval);
            total += interval;
        }
        assertEquals(1, minimum);
        assertEquals(60, maximum);
        assertEquals(9.982, total / 100_000.0, 0.08);
    }

    @Test
    void timingWheelRequeuesOnlyDueTracksAndConservesCounters() throws Exception {
        TrackShard shard =
                new TrackShard(
                        100, 250, TrackSimulationConfig.REFERENCE_SEED, IouKalmanConfig.REFERENCE);
        assertEquals(250L, shard.initializationReports());
        assertEquals(250L, shard.scheduledReports());
        assertEquals(250L, shard.pendingReports());
        for (int second = 1; second <= 180; second++) {
            shard.advanceSecond(second, false);
        }
        assertTrue(shard.processedReports() > 3_000L);
        assertEquals(shard.processedReports(), shard.tracksVisited());
        assertEquals(shard.processedReports() + shard.pendingReports(), shard.scheduledReports());
        assertEquals(0L, shard.rejectedReports());
        assertEquals(0L, shard.lateReports());
        assertTrue(shard.hasExactlyOneWheelEntryPerTrack());
        for (int track = 100; track < 350; track++) {
            assertTrue(shard.nextDueSecond(track) > 180);
            assertTrue(shard.nextDueSecond(track) <= 240);
            assertTrue(shard.sequence(track) > 0L);
            assertTrue(Math.abs(shard.truthX(track)) <= TrackShard.WORLD_X);
            assertTrue(Math.abs(shard.truthY(track)) <= TrackShard.MAX_Y);
            assertTrue(Double.isFinite(shard.estimatedX(track)));
            assertTrue(Double.isFinite(shard.estimatedY(track)));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> assertEquals(0.0, shard.truthX(99)));
    }

    @Test
    void motionGoldenVectorProvesPropagateReflectThenPerturbOrdering() throws Exception {
        TrackShard ordinary =
                new TrackShard(
                        7, 1, TrackSimulationConfig.REFERENCE_SEED, IouKalmanConfig.REFERENCE);
        assertEquals(-18_145_579.148643088, ordinary.truthX(7));
        assertEquals(-2_260_850.530090168, ordinary.truthY(7));
        assertEquals(5.313982391238758, ordinary.course(7));
        assertEquals(146.58244264031083, ordinary.speed(7));
        assertEquals(2, ordinary.nextDueSecond(7));
        ordinary.advanceSecond(1, false);
        ordinary.advanceSecond(2, false);
        assertEquals(-18_145_413.229966685, ordinary.truthX(7));
        assertEquals(-2_261_092.22544135, ordinary.truthY(7));
        assertEquals(5.30508074778883, ordinary.course(7));
        assertEquals(146.04950005194837, ordinary.speed(7));
        assertEquals(31, ordinary.nextDueSecond(7));

        TrackShard reflected =
                new TrackShard(
                        10_747, 1, TrackSimulationConfig.REFERENCE_SEED, IouKalmanConfig.REFERENCE);
        assertEquals(11, reflected.nextDueSecond(10_747));
        for (int second = 1; second <= 11; second++) {
            reflected.advanceSecond(second, false);
        }
        assertEquals(15_538_112.083578229, reflected.truthY(10_747));
        assertEquals(4.185742028972423, reflected.course(10_747));
        assertEquals(188.96035993146003, reflected.speed(10_747));
        assertEquals(21, reflected.nextDueSecond(10_747));
    }

    @Test
    void virtualReplayAndStableShardPartitionsAreEquivalent() {
        long oneWorkerChecksum;
        long fourWorkerChecksum;
        try (LiveTrackCoordinator one =
                        new LiveTrackCoordinator(TrackSimulationConfig.reference(2_000, 1));
                LiveTrackCoordinator four =
                        new LiveTrackCoordinator(TrackSimulationConfig.reference(2_000, 4))) {
            one.start(0L);
            four.start(0L);
            one.advanceTo(120);
            four.advanceTo(120);
            oneWorkerChecksum = one.checksum();
            fourWorkerChecksum = four.checksum();
            assertEquals(one.initializationReports(), four.initializationReports());
            assertEquals(one.scheduledReports(), four.scheduledReports());
            assertEquals(one.processedReports(), four.processedReports());
            assertEquals(one.pendingReports(), four.pendingReports());
            assertEquals(2_000L, one.pendingReports());
            assertEquals(one.processedReports(), one.tracksVisited());
            assertEquals(one.scheduledReports(), one.processedReports() + one.pendingReports());
            for (int track : new int[] {0, 499, 500, 1_999}) {
                assertEquals(one.truthX(track), four.truthX(track));
                assertEquals(one.truthY(track), four.truthY(track));
                assertEquals(one.estimatedX(track), four.estimatedX(track));
                assertEquals(one.estimatedY(track), four.estimatedY(track));
                assertEquals(one.sequence(track), four.sequence(track));
                assertEquals(one.nextDueSecond(track), four.nextDueSecond(track));
            }
        }
        assertEquals(oneWorkerChecksum, fourWorkerChecksum);
    }

    @Test
    void packedDisplayCopyIsConsistentAndDoesNotMutateTheEstimator() {
        try (LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(128, 3))) {
            coordinator.start(0L);
            coordinator.advanceTo(60);
            long before = coordinator.checksum();
            double[] positionsX = new double[128];
            double[] positionsY = new double[128];
            coordinator.copyDisplayPositions(60.0, positionsX, positionsY);
            double[] replayX = new double[128];
            double[] replayY = new double[128];
            coordinator.copyDisplayPositions(60.0, replayX, replayY);
            assertTrue(Arrays.equals(positionsX, replayX));
            assertTrue(Arrays.equals(positionsY, replayY));
            assertTrue(Double.isFinite(positionsX[0]));
            assertTrue(Double.isFinite(positionsY[0]));
            assertTrue(Double.isFinite(positionsX[127]));
            assertTrue(Double.isFinite(positionsY[127]));
            assertEquals(before, coordinator.checksum());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> coordinator.copyDisplayPositions(60.0, new double[127], new double[128]));
        }
    }

    @Test
    void fractionalRealTimeDisplayPredictionIsPureAndRespectsPause() {
        try (LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(128, 3))) {
            coordinator.start(1_000_000_000L);
            coordinator.advanceRealTime(61_100_000_000L);
            long before = coordinator.checksum();
            double firstTimestamp = coordinator.displayTimestampSeconds(61_100_000_000L);
            double[] firstX = new double[128];
            double[] firstY = new double[128];
            coordinator.copyDisplayPositions(firstTimestamp, firstX, firstY);

            coordinator.advanceRealTime(61_900_000_000L);
            double secondTimestamp = coordinator.displayTimestampSeconds(61_900_000_000L);
            double[] secondX = new double[128];
            double[] secondY = new double[128];
            coordinator.copyDisplayPositions(secondTimestamp, secondX, secondY);

            assertEquals(60, coordinator.simulationSecond());
            assertEquals(60.1, firstTimestamp, 1.0e-12);
            assertEquals(60.9, secondTimestamp, 1.0e-12);
            assertFalse(Arrays.equals(firstX, secondX) && Arrays.equals(firstY, secondY));
            assertEquals(before, coordinator.checksum());

            coordinator.pause(61_900_000_000L);
            assertEquals(60.9, coordinator.displayTimestampSeconds(99_000_000_000L), 1.0e-12);
            coordinator.resume(100_000_000_000L);
            coordinator.advanceRealTime(100_500_000_000L);
            assertEquals(61.4, coordinator.displayTimestampSeconds(100_500_000_000L), 1.0e-12);
        }
    }

    @Test
    void realTimePauseResumeResetAndCloseHaveExplicitState() {
        TrackSimulationConfig config = TrackSimulationConfig.reference(128, 3);
        LiveTrackCoordinator coordinator = new LiveTrackCoordinator(config);
        assertEquals(LiveTrackCoordinator.State.NEW, coordinator.state());
        assertEquals(128L, coordinator.initializationReports());
        assertTrue(coordinator.logicalBytes() <= 192L * 128L + 3L * 512L);
        coordinator.start(5_000_000_000L);
        coordinator.advanceRealTime(10_000_000_000L);
        assertEquals(5, coordinator.simulationSecond());
        assertTrue(coordinator.lateReports() > 0L);
        coordinator.pause(10_000_000_000L);
        assertEquals(LiveTrackCoordinator.State.PAUSED, coordinator.state());
        assertThrows(IllegalStateException.class, () -> coordinator.advanceTo(6));
        coordinator.resume(20_000_000_000L);
        coordinator.advanceRealTime(23_000_000_000L);
        assertEquals(8, coordinator.simulationSecond());
        coordinator.pause(23_000_000_000L);
        int shardIdentity = coordinator.shardIdentity();
        coordinator.reset();
        assertEquals(LiveTrackCoordinator.State.NEW, coordinator.state());
        assertEquals(0, coordinator.simulationSecond());
        assertEquals(128L, coordinator.initializationReports());
        assertEquals(shardIdentity, coordinator.shardIdentity());
        coordinator.start(0L);
        assertThrows(IllegalArgumentException.class, () -> coordinator.advanceTo(-1));
        assertThrows(
                IllegalArgumentException.class, () -> coordinator.advanceTo(Integer.MAX_VALUE));
        coordinator.close();
        coordinator.close();
        assertEquals(LiveTrackCoordinator.State.CLOSED, coordinator.state());
        assertThrows(IllegalStateException.class, () -> coordinator.start(0L));
    }

    @Test
    void workerFailureCancelsTheRunAndIsObservable() {
        LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(100, 2));
        coordinator.start(0L);
        coordinator.failWorkerForTest(1);
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> coordinator.advanceTo(2));
        assertEquals("LIVE_TRACK_WORKER_FAILURE", failure.getMessage());
        assertEquals(LiveTrackCoordinator.State.FAILED, coordinator.state());
        assertEquals(0, coordinator.liveWorkerCount());
        assertEquals(
                coordinator.scheduledReports(),
                coordinator.processedReports() + coordinator.pendingReports());
        coordinator.close();
        assertEquals(LiveTrackCoordinator.State.CLOSED, coordinator.state());
    }

    @Test
    void activeAdvanceSerializesPauseAndRejectsACompetitor() throws Exception {
        LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(10_000, 4));
        coordinator.start(0L);
        AtomicReference<Throwable> advanceFailure = new AtomicReference<>();
        Thread advance =
                new Thread(
                        () -> {
                            try {
                                coordinator.advanceTo(3_000);
                            } catch (Throwable thrown) {
                                advanceFailure.set(thrown);
                            }
                        },
                        "test-live-track-advance");
        advance.start();
        awaitAdvancing(coordinator);
        assertThrows(IllegalStateException.class, () -> coordinator.advanceTo(3_001));
        assertThrows(
                IllegalStateException.class,
                () -> assertEquals(0L, coordinator.processedReports()));
        assertThrows(IllegalStateException.class, () -> assertEquals(0.0, coordinator.truthX(0)));
        Thread pause =
                new Thread(() -> coordinator.pause(3_000_000_000_000L), "test-live-track-pause");
        pause.start();
        advance.join(15_000L);
        pause.join(15_000L);
        assertFalse(advance.isAlive());
        assertFalse(pause.isAlive());
        assertEquals(null, advanceFailure.get());
        assertEquals(3_000, coordinator.simulationSecond());
        assertEquals(LiveTrackCoordinator.State.PAUSED, coordinator.state());
        coordinator.close();
        assertEquals(0, coordinator.liveWorkerCount());
    }

    @Test
    void closeCancelsAnActiveAdvanceAndJoinsEveryWorker() throws Exception {
        LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(10_000, 4));
        coordinator.start(0L);
        AtomicReference<Throwable> advanceFailure = new AtomicReference<>();
        Thread advance =
                new Thread(
                        () -> {
                            try {
                                coordinator.advanceTo(100_000);
                            } catch (Throwable thrown) {
                                advanceFailure.set(thrown);
                            }
                        },
                        "test-live-track-cancel");
        advance.start();
        awaitAdvancing(coordinator);
        Thread firstClose = new Thread(coordinator::close, "test-live-track-first-close");
        firstClose.start();
        coordinator.close();
        firstClose.join(5_000L);
        advance.join(5_000L);
        assertFalse(firstClose.isAlive());
        assertFalse(advance.isAlive());
        assertTrue(advanceFailure.get() instanceof IllegalStateException);
        assertEquals(LiveTrackCoordinator.State.CLOSED, coordinator.state());
        assertEquals(0, coordinator.liveWorkerCount());
    }

    @Test
    void interruptedCloseStillJoinsWorkersAndRestoresInterrupt() {
        LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(1_000, 2));
        coordinator.start(0L);
        Thread.currentThread().interrupt();
        coordinator.close();
        assertTrue(Thread.interrupted(), "close must restore the caller interrupt status");
        assertEquals(LiveTrackCoordinator.State.CLOSED, coordinator.state());
        assertEquals(0, coordinator.liveWorkerCount());
    }

    @Test
    void tenThousandTracksFilterEndToEndWithinLogicalBounds() {
        int population = 10_000;
        int workers = Math.min(4, TrackSimulationConfig.defaultWorkers(population));
        try (LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(population, workers))) {
            coordinator.start(0L);
            coordinator.advanceTo(120);
            assertEquals(120, coordinator.simulationSecond());
            assertEquals(population, coordinator.initializationReports());
            assertEquals(population, coordinator.pendingReports());
            assertEquals(
                    coordinator.processedReports() + population, coordinator.scheduledReports());
            assertTrue(coordinator.processedReports() > 100_000L);
            assertEquals(0L, coordinator.rejectedReports());
            assertTrue(coordinator.logicalBytes() <= 192L * population + workers * 512L);
            assertNotEquals(0L, coordinator.checksum());
        }
    }

    @Test
    void validatesPopulationAndWorkerLimits() {
        assertThrows(IllegalArgumentException.class, () -> TrackSimulationConfig.reference(0, 1));
        assertThrows(IllegalArgumentException.class, () -> TrackSimulationConfig.reference(10, 0));
        assertThrows(IllegalArgumentException.class, () -> TrackSimulationConfig.reference(10, 11));
        assertThrows(
                IllegalArgumentException.class, () -> TrackSimulationConfig.reference(100, 33));
        assertTrue(TrackSimulationConfig.defaultWorkers(1) >= 1);
        assertFalse(TrackSimulationConfig.defaultWorkers(100) > 8);

        TrackSimulationConfig config = TrackSimulationConfig.reference(10_000, 4);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LiveTrackCoordinator(config, 256L * 1024L * 1024L));
        try (LiveTrackCoordinator coordinator = new LiveTrackCoordinator(config)) {
            assertEquals(1_131_184L, coordinator.logicalBytes());
            assertEquals(20_000L, coordinator.largestAllocation());
        }
    }

    private static void awaitAdvancing(LiveTrackCoordinator coordinator) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!coordinator.isAdvancing() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(coordinator.isAdvancing(), "advance did not become active before timeout");
    }
}
