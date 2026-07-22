package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.FrameBuffer;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameEngine;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameHandoff;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameMetrics;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFramePacer;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackOverlay;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackRasterizer;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackTelemetry;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackViewport;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LiveTrackFramesTest {
    private static final long ONE_GIBIBYTE = 1_024L * 1_024L * 1_024L;

    @Test
    void rasterizerProjectsPackedPositionsIntoFixedTwoByTwoMarks() {
        MapViewport map = new MapViewport(6, 4, 0.0, 0.0, 1.0);
        LiveTrackViewport viewport = new LiveTrackViewport(3L, map);
        int[] pixels = new int[24];
        LiveTrackRasterizer.render(
                new double[] {0.0, -3.0, 100.0}, new double[] {0.0, 2.0, 100.0}, viewport, pixels);

        assertEquals(LiveTrackRasterizer.TRACK_ARGB, pixels[2 * 6 + 3]);
        assertEquals(LiveTrackRasterizer.TRACK_ARGB, pixels[2 * 6 + 4]);
        assertEquals(LiveTrackRasterizer.TRACK_ARGB, pixels[3 * 6 + 3]);
        assertEquals(LiveTrackRasterizer.TRACK_ARGB, pixels[3 * 6 + 4]);
        assertEquals(LiveTrackRasterizer.TRACK_ARGB, pixels[0]);
        assertEquals(8L, Arrays.stream(pixels).filter(value -> value != 0).count());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LiveTrackRasterizer.render(
                                new double[] {1.0}, new double[0], viewport, pixels));
        assertThrows(
                IllegalStateException.class,
                () ->
                        LiveTrackRasterizer.render(
                                new double[] {Double.NaN}, new double[] {0.0}, viewport, pixels));
    }

    @Test
    void handoffBoundsOwnershipReplacesPendingAndRejectsStaleFrames() {
        LiveTrackFrameHandoff handoff = new LiveTrackFrameHandoff(ONE_GIBIBYTE, 1_000L);
        LiveTrackViewport first = new LiveTrackViewport(1L, new MapViewport(8, 6, 0.0, 0.0, 1.0));
        assertTrue(handoff.beginRequest());
        assertFalse(handoff.beginRequest());
        FrameBuffer firstBuffer = handoff.acquireProducerBuffer(first);
        firstBuffer.pixels()[0] = LiveTrackRasterizer.TRACK_ARGB;
        handoff.publish(firstBuffer, first, 10, 100, 10L);

        assertTrue(handoff.beginRequest());
        FrameBuffer replacement = handoff.acquireProducerBuffer(first);
        replacement.pixels()[1] = LiveTrackRasterizer.TRACK_ARGB;
        handoff.publish(replacement, first, 11, 100, 20L);
        LiveTrackFrameMetrics replaced = handoff.metrics(10_000_000_000L);
        assertEquals(2L, replaced.completedFrames());
        assertEquals(1L, replaced.replacedPendingFrames());
        assertTrue(replaced.allocatedBuffers() <= 3);

        BufferedImage current = handoff.frameForPaint(1L, 8, 6, 10_000_000_000L);
        assertNotNull(current);
        assertEquals(11.0, handoff.currentDisplayTimestampSeconds());
        assertEquals(2L, handoff.currentSequence());
        assertEquals(1L, handoff.metrics(10_000_000_000L).paintedFrames());
        assertNotNull(handoff.frameForPaint(1L, 8, 6, 10_100_000_000L));
        assertEquals(1L, handoff.metrics(10_100_000_000L).paintedFrames());

        LiveTrackViewport stale = new LiveTrackViewport(2L, new MapViewport(8, 6, 0.0, 0.0, 1.0));
        assertTrue(handoff.beginRequest());
        FrameBuffer staleBuffer = handoff.acquireProducerBuffer(stale);
        handoff.publish(staleBuffer, stale, 12, 100, 30L);
        assertNull(handoff.frameForPaint(3L, 8, 6, 10_200_000_000L));
        assertEquals(1L, handoff.metrics(10_200_000_000L).staleDiscards());

        LiveTrackViewport oldSize = new LiveTrackViewport(4L, new MapViewport(8, 6, 0.0, 0.0, 1.0));
        assertTrue(handoff.beginRequest());
        FrameBuffer oldSizeBuffer = handoff.acquireProducerBuffer(oldSize);
        handoff.publish(oldSizeBuffer, oldSize, 13, 100, 40L);
        assertNull(handoff.frameForPaint(4L, 10, 5, 10_300_000_000L));
        assertEquals(2L, handoff.metrics(10_300_000_000L).staleDiscards());

        LiveTrackViewport resized =
                new LiveTrackViewport(4L, new MapViewport(10, 5, 0.0, 0.0, 1.0));
        assertTrue(handoff.beginRequest());
        FrameBuffer resizedBuffer = handoff.acquireProducerBuffer(resized);
        handoff.publish(resizedBuffer, resized, 14, 100, 50L);
        assertNotNull(handoff.frameForPaint(4L, 10, 5, 10_400_000_000L));
        assertNull(handoff.frameForPaint(4L, 12, 7, 10_500_000_000L));

        LiveTrackViewport finalSize =
                new LiveTrackViewport(4L, new MapViewport(12, 7, 0.0, 0.0, 1.0));
        assertTrue(handoff.beginRequest());
        FrameBuffer finalBuffer = handoff.acquireProducerBuffer(finalSize);
        handoff.publish(finalBuffer, finalSize, 15, 100, 60L);
        assertNotNull(handoff.frameForPaint(4L, 12, 7, 10_600_000_000L));
        assertEquals(1, handoff.metrics(10_600_000_000L).allocatedBuffers());

        handoff.close();
        assertTrue(handoff.isClosed());
        assertEquals(0, handoff.metrics(10_200_000_000L).allocatedBuffers());
        assertNull(handoff.frameForPaint(3L, 8, 6, 10_200_000_000L));
    }

    @Test
    void viewportAndFrameStorageLimitsFailBeforeAllocation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LiveTrackViewport(0L, new MapViewport(4_096, 2_049, 0.0, 0.0, 1.0)));

        LiveTrackFrameHandoff handoff =
                new LiveTrackFrameHandoff(260L * 1_024L * 1_024L, 1_024L * 1_024L);
        LiveTrackViewport viewport =
                new LiveTrackViewport(0L, new MapViewport(1_000, 1_000, 0.0, 0.0, 1.0));
        assertTrue(handoff.beginRequest());
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> handoff.acquireProducerBuffer(viewport));
        assertEquals("LIVE_TRACK_FRAME_STORAGE_LIMIT", failure.getMessage());
        handoff.cancelRequest();
        handoff.close();

        TrackSimulationConfig million = TrackSimulationConfig.reference(1_000_000, 8);
        IllegalArgumentException compositeFailure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new LiveTrackFrameEngine(million, 0L, 375L * 1_024L * 1_024L));
        assertEquals("LIVE_TRACK_DISPLAY_STORAGE_LIMIT", compositeFailure.getMessage());
    }

    @Test
    void achievedFpsUsesObservedIntervalsWithoutFiveSecondStartupDepression() {
        LiveTrackFrameHandoff handoff = new LiveTrackFrameHandoff(ONE_GIBIBYTE, 1_000L);
        LiveTrackViewport viewport =
                new LiveTrackViewport(1L, new MapViewport(2, 2, 0.0, 0.0, 1.0));
        for (int frame = 0; frame < 2; frame++) {
            assertTrue(handoff.beginRequest());
            FrameBuffer buffer = handoff.acquireProducerBuffer(viewport);
            handoff.publish(buffer, viewport, frame, 1, 1L);
            assertNotNull(handoff.frameForPaint(1L, 2, 2, 10_000_000_000L + frame * 100_000_000L));
        }
        assertEquals(10.0, handoff.metrics(10_100_000_000L).achievedFps(), 1.0e-12);
        assertTrue(handoff.metrics(14_900_000_000L).achievedFps() < 1.0);
        assertEquals(0.0, handoff.metrics(15_200_000_000L).achievedFps(), 1.0e-12);
        handoff.close();
    }

    @Test
    void framePacerSupportsOnlyTheApprovedCapsWithoutCatchUpBursts() {
        LiveTrackFramePacer pacer = new LiveTrackFramePacer(10);
        assertTrue(pacer.shouldRequest(1_000_000_000L));
        assertFalse(pacer.shouldRequest(1_099_999_999L));
        assertTrue(pacer.shouldRequest(1_100_000_000L));
        assertTrue(pacer.shouldRequest(10_000_000_000L));
        assertFalse(pacer.shouldRequest(10_000_000_001L));
        pacer.setCap(0);
        assertEquals(0, pacer.cap());
        assertTrue(pacer.shouldRequest(10L));
        assertTrue(pacer.shouldRequest(10L));
        for (int cap : new int[] {1, 2, 5, 10, 15, 30, 60}) {
            pacer.setCap(cap);
            assertEquals(cap, pacer.cap());
        }
        assertThrows(IllegalArgumentException.class, () -> pacer.setCap(20));

        LiveTrackFramePacer cadenced = new LiveTrackFramePacer(10);
        int requests = 0;
        for (long now = 0L; now < 1_000_000_000L; now += 16_000_000L) {
            if (cadenced.shouldRequest(now)) {
                requests++;
            }
        }
        assertEquals(10, requests);
    }

    @Test
    void engineBuildsDeterministicFramesSkipsOverlapAndRunsControls() throws Exception {
        TrackSimulationConfig config = TrackSimulationConfig.reference(10_000, 4);
        LiveTrackFrameEngine engine = new LiveTrackFrameEngine(config, 0L, ONE_GIBIBYTE);
        LiveTrackViewport viewport =
                new LiveTrackViewport(
                        1L,
                        MapViewport.fit(
                                320,
                                200,
                                new Envelope(
                                        -TrackShard.WORLD_X,
                                        -TrackShard.MAX_Y,
                                        TrackShard.WORLD_X,
                                        TrackShard.MAX_Y),
                                4.0));
        try {
            assertTrue(engine.requestVirtual(viewport, 120));
            assertFalse(engine.requestVirtual(viewport, 120));
            assertTrue(engine.awaitIdle(30_000L));
            LiveTrackTelemetry complete = engine.telemetry(10_000_000_000L);
            assertEquals(120, complete.simulationSecond());
            assertEquals(120_621L, complete.processedReports());
            assertEquals(10_000L, complete.pendingReports());
            assertEquals(1L, complete.frames().completedFrames());
            assertEquals(1L, complete.frames().skippedRequests());
            assertEquals(8L * config.population(), engine.largestPositionAllocationBytes());
            BufferedImage frame = engine.handoff().frameForPaint(1L, 320, 200, 10_000_000_000L);
            assertNotNull(frame);
            assertTrue(countTrackPixels(frame) > 1_000);

            assertTrue(engine.requestPause(120_000_000_000L));
            assertTrue(engine.awaitIdle(10_000L));
            assertEquals(
                    LiveTrackCoordinator.State.PAUSED, engine.telemetry(10_000_000_000L).state());
            assertTrue(engine.requestVirtual(viewport, 120));
            assertTrue(engine.awaitIdle(10_000L));
            assertEquals(120, engine.telemetry(10_000_000_000L).simulationSecond());

            assertTrue(engine.requestResume(200_000_000_000L));
            assertTrue(engine.awaitIdle(10_000L));
            assertEquals(
                    LiveTrackCoordinator.State.RUNNING, engine.telemetry(200_000_000_000L).state());
            assertTrue(engine.requestReset(210_000_000_000L));
            assertTrue(engine.awaitIdle(10_000L));
            LiveTrackTelemetry reset = engine.telemetry(210_000_000_000L);
            assertEquals(0, reset.simulationSecond());
            assertEquals(0, reset.backlogSeconds());
            assertNull(engine.handoff().frameForPaint(1L, 320, 200, 210_000_000_000L));
        } finally {
            engine.close();
        }
        assertFalse(engine.producerAlive());
        assertTrue(engine.handoff().isClosed());
    }

    @Test
    void realTimeFramesRetainFractionalDisplayInstantsWithinOneReportSecond() {
        LiveTrackFrameEngine engine =
                new LiveTrackFrameEngine(
                        TrackSimulationConfig.reference(128, 3), 1_000_000_000L, ONE_GIBIBYTE);
        LiveTrackViewport viewport =
                new LiveTrackViewport(1L, new MapViewport(64, 32, 0.0, 0.0, 1_000_000.0));
        try {
            assertTrue(engine.requestRealTime(viewport, 1_100_000_000L));
            assertTrue(engine.awaitIdle(10_000L));
            assertNotNull(engine.handoff().frameForPaint(1L, 64, 32, 1_100_000_000L));
            assertEquals(0.1, engine.handoff().currentDisplayTimestampSeconds(), 1.0e-12);

            assertTrue(engine.requestRealTime(viewport, 1_900_000_000L));
            assertTrue(engine.awaitIdle(10_000L));
            assertNotNull(engine.handoff().frameForPaint(1L, 64, 32, 1_900_000_000L));
            assertEquals(0.9, engine.handoff().currentDisplayTimestampSeconds(), 1.0e-12);
            assertEquals(0, engine.telemetry(1_900_000_000L).simulationSecond());
        } finally {
            engine.close();
        }
    }

    @Test
    void resumeAndResetCancelOlderQueuedFramesWithoutLeakingTheRequest() {
        Semaphore selections = new Semaphore(0);
        Runnable selectionGate =
                () -> {
                    try {
                        selections.acquire();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                };
        LiveTrackFrameEngine engine =
                new LiveTrackFrameEngine(
                        TrackSimulationConfig.reference(128, 3), 0L, ONE_GIBIBYTE, selectionGate);
        LiveTrackViewport viewport =
                new LiveTrackViewport(1L, new MapViewport(64, 32, 0.0, 0.0, 1_000_000.0));
        try {
            assertTrue(engine.requestRealTime(viewport, 1_000_000_000L));
            assertTrue(engine.requestReset(2_000_000_000L));
            assertFalse(engine.handoff().requestActive());
            selections.release();
            assertTrue(engine.awaitIdle(10_000L));
            assertEquals(LiveTrackCoordinator.State.RUNNING, engine.telemetry(0L).state());

            assertTrue(engine.requestPause(3_000_000_000L));
            selections.release();
            assertTrue(engine.awaitIdle(10_000L));
            assertEquals(LiveTrackCoordinator.State.PAUSED, engine.telemetry(0L).state());

            assertTrue(engine.requestRealTime(viewport, 4_000_000_000L));
            assertTrue(engine.requestResume(5_000_000_000L));
            assertFalse(engine.handoff().requestActive());
            selections.release();
            assertTrue(engine.awaitIdle(10_000L));
            LiveTrackTelemetry telemetry = engine.telemetry(5_000_000_000L);
            assertEquals(LiveTrackCoordinator.State.RUNNING, telemetry.state());
            assertEquals("", telemetry.failureCategory());
            assertEquals(2L, telemetry.frames().requestedFrames());
            assertEquals(0L, telemetry.frames().completedFrames());
            assertEquals(2L, telemetry.frames().skippedRequests());
        } finally {
            selections.release();
            engine.close();
        }
        assertFalse(engine.producerAlive());
        assertTrue(engine.handoff().isClosed());
    }

    @Test
    void overlayCachesBackgroundRemainsNonInterceptingAndIsEdtConfined() throws Exception {
        LiveTrackFrameHandoff handoff = new LiveTrackFrameHandoff(ONE_GIBIBYTE, 1_000L);
        LiveTrackOverlay overlay = new LiveTrackOverlay(handoff);
        assertFalse(overlay.isOpaque());
        assertFalse(overlay.contains(0, 0));
        MapViewport mapViewport = new MapViewport(20, 10, 0.0, 0.0, 1.0);
        assertThrows(IllegalStateException.class, () -> overlay.setViewport(1L, mapViewport));

        LiveTrackViewport viewport = new LiveTrackViewport(1L, mapViewport);
        assertTrue(handoff.beginRequest());
        FrameBuffer buffer = handoff.acquireProducerBuffer(viewport);
        buffer.pixels()[0] = LiveTrackRasterizer.TRACK_ARGB;
        handoff.publish(buffer, viewport, 1.0, 1, 1L);

        BufferedImage target = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        EventQueue.invokeAndWait(
                () -> {
                    overlay.setSize(20, 10);
                    overlay.setViewport(1L, mapViewport);
                    BufferedImage background =
                            new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
                    Graphics2D backgroundGraphics = background.createGraphics();
                    try {
                        backgroundGraphics.setColor(java.awt.Color.BLUE);
                        backgroundGraphics.fillRect(0, 0, 40, 20);
                    } finally {
                        backgroundGraphics.dispose();
                    }
                    assertTrue(
                            overlay.installBackground(
                                    new MapViewport(40, 20, 0.0, 0.0, 1.0),
                                    background,
                                    1_000_000L));
                    assertTrue(overlay.backgroundCovers(mapViewport.panByPixels(5.0, 0.0)));
                    Graphics2D graphics = target.createGraphics();
                    try {
                        overlay.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                });
        assertTrue(overlay.isOpaque());
        assertEquals(LiveTrackRasterizer.TRACK_ARGB, target.getRGB(0, 0));
        assertEquals(java.awt.Color.BLUE.getRGB(), target.getRGB(10, 5));
        assertEquals(1L, overlay.presentationMetrics(System.nanoTime()).presentedFrames());
        assertEquals(1L, overlay.presentationMetrics(System.nanoTime()).backgroundRefreshes());
        assertEquals(
                1_000_000L, overlay.presentationMetrics(System.nanoTime()).backgroundLastNanos());
        handoff.close();
    }

    @Test
    void concurrentCloseCancelsWorkAndBothCallersObserveCompletion() throws Exception {
        LiveTrackFrameEngine engine =
                new LiveTrackFrameEngine(
                        TrackSimulationConfig.reference(10_000, 4), 0L, ONE_GIBIBYTE);
        LiveTrackViewport viewport =
                new LiveTrackViewport(1L, new MapViewport(320, 200, 0.0, 0.0, 200_000.0));
        assertTrue(engine.requestVirtual(viewport, 100_000));
        Thread first = new Thread(engine::close, "test-frame-engine-close-one");
        Thread second = new Thread(engine::close, "test-frame-engine-close-two");
        first.start();
        second.start();
        first.join(10_000L);
        second.join(10_000L);
        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertFalse(engine.producerAlive());
        assertTrue(engine.handoff().isClosed());
        assertEquals(
                LiveTrackCoordinator.State.CLOSED, engine.telemetry(System.nanoTime()).state());
    }

    @Test
    void fullPictureRendersTenThousandTracksOverTheRealChart() throws Exception {
        NaturalEarthChart.ChartSession chart = NaturalEarthChart.startHeadless();
        LiveTrackFrameEngine engine =
                new LiveTrackFrameEngine(
                        TrackSimulationConfig.reference(10_000, 4), 0L, ONE_GIBIBYTE);
        LiveTrackOverlay overlay = new LiveTrackOverlay(engine.handoff());
        BufferedImage image = new BufferedImage(900, 500, BufferedImage.TYPE_INT_ARGB);
        try {
            MapViewport[] viewport = new MapViewport[1];
            EventQueue.invokeAndWait(
                    () -> {
                        chart.view().setSize(900, 500);
                        chart.view().fitToData(24.0);
                        overlay.setSize(900, 500);
                        viewport[0] = chart.view().viewport();
                        overlay.setViewport(1L, viewport[0]);
                    });
            LiveTrackViewport snapshot =
                    new LiveTrackViewport(1L, java.util.Objects.requireNonNull(viewport[0]));
            assertTrue(engine.requestVirtual(snapshot, 120));
            assertTrue(engine.awaitIdle(30_000L));
            EventQueue.invokeAndWait(
                    () -> {
                        Graphics2D graphics = image.createGraphics();
                        try {
                            chart.view().paint(graphics);
                            overlay.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    });
            assertTrue(countTrackPixels(image) > 10_000L);
            assertTrue(countExactColor(image, NaturalEarthChart.OCEAN.getRGB()) > 100_000L);
            assertTrue(countLandLike(image) > 20_000L);
            Path reports = Path.of("build", "reports");
            Files.createDirectories(reports);
            ImageIO.write(image, "png", reports.resolve("live-track-10k.png").toFile());
        } finally {
            engine.close();
            chart.close();
        }
        assertFalse(engine.producerAlive());
        assertTrue(engine.handoff().isClosed());
        assertTrue(chart.sourceClosed());
    }

    @Test
    void hundredThousandTrackTierBuildsBoundedDeterministicFrame() {
        TrackSimulationConfig config = TrackSimulationConfig.reference(100_000, 8);
        LiveTrackFrameEngine engine = new LiveTrackFrameEngine(config, 0L, ONE_GIBIBYTE);
        LiveTrackViewport viewport =
                new LiveTrackViewport(
                        1L,
                        MapViewport.fit(
                                900,
                                500,
                                new Envelope(
                                        -TrackShard.WORLD_X,
                                        -TrackShard.MAX_Y,
                                        TrackShard.WORLD_X,
                                        TrackShard.MAX_Y),
                                24.0));
        try {
            assertTrue(engine.requestVirtual(viewport, 120));
            assertTrue(engine.awaitIdle(30_000L));
            BufferedImage image = engine.handoff().frameForPaint(1L, 900, 500, System.nanoTime());
            assertNotNull(image);
            LiveTrackTelemetry telemetry = engine.telemetry(System.nanoTime());
            assertEquals(100_000, telemetry.population());
            assertEquals(1_200_483L, telemetry.processedReports());
            assertEquals(100_000L, telemetry.pendingReports());
            assertEquals(0, telemetry.backlogSeconds());
            assertEquals(8, telemetry.shards().shardCount());
            assertTrue(telemetry.shards().reportSkewRatio() >= 1.0);
            assertTrue(telemetry.shards().workSkewRatio() >= 1.0);
            assertEquals(11_325_600L, telemetry.logicalTrackBytes());
            assertEquals(1_600_000L, telemetry.packedPositionBytes());
            assertEquals(1, telemetry.frames().allocatedBuffers());
            assertTrue(countTrackPixels(image) > 100_000L);
        } finally {
            engine.close();
        }
        assertFalse(engine.producerAlive());
        assertTrue(engine.handoff().isClosed());
    }

    @Test
    void virtualHourSoakKeepsFrameAndLogicalStorageBoundedAcrossResizes() {
        TrackSimulationConfig config = TrackSimulationConfig.reference(10_000, 8);
        LiveTrackFrameEngine engine = new LiveTrackFrameEngine(config, 0L, ONE_GIBIBYTE);
        long logicalBytes = engine.telemetry(0L).logicalTrackBytes();
        try {
            for (int second = 0; second <= 3_600; second += 60) {
                int width = second % 120 == 0 ? 320 : 480;
                int height = second % 120 == 0 ? 180 : 270;
                LiveTrackViewport viewport =
                        new LiveTrackViewport(
                                second / 60L + 1L,
                                MapViewport.fit(
                                        width,
                                        height,
                                        new Envelope(
                                                -TrackShard.WORLD_X,
                                                -TrackShard.MAX_Y,
                                                TrackShard.WORLD_X,
                                                TrackShard.MAX_Y),
                                        8.0));
                assertTrue(engine.requestVirtual(viewport, second));
                assertTrue(engine.awaitIdle(30_000L));
                assertNotNull(
                        engine.handoff()
                                .frameForPaint(
                                        viewport.generation(),
                                        viewport.width(),
                                        viewport.height(),
                                        System.nanoTime()));
                LiveTrackTelemetry telemetry = engine.telemetry(System.nanoTime());
                assertEquals(logicalBytes, telemetry.logicalTrackBytes());
                assertTrue(telemetry.frames().allocatedBuffers() <= 3);
                assertTrue(
                        telemetry.frames().frameBufferBytes() <= 3L * 480L * 270L * Integer.BYTES);
            }
            LiveTrackTelemetry terminal = engine.telemetry(System.nanoTime());
            assertEquals(3_600, terminal.simulationSecond());
            assertTrue(terminal.processedReports() > 3_000_000L);
            assertEquals(10_000L, terminal.pendingReports());
            assertEquals(61L, terminal.frames().completedFrames());
            assertEquals(61L, terminal.frames().paintedFrames());
        } finally {
            engine.close();
        }
        assertTrue(engine.workersTerminated());
        assertTrue(engine.handoff().isClosed());
        assertEquals(0, engine.telemetry(System.nanoTime()).frames().allocatedBuffers());
    }

    @Test
    void workerFailureIsTerminalVisibleAndRejectsFurtherFrameDemand() {
        LiveTrackFrameEngine engine =
                new LiveTrackFrameEngine(
                        TrackSimulationConfig.reference(1_000, 2), 0L, ONE_GIBIBYTE);
        LiveTrackViewport viewport =
                new LiveTrackViewport(1L, new MapViewport(64, 32, 0.0, 0.0, 1_000_000.0));
        try {
            assertTrue(engine.requestVirtual(viewport, 1));
            assertTrue(engine.awaitIdle(10_000L));
            assertNotNull(engine.handoff().frameForPaint(1L, 64, 32, System.nanoTime()));
            assertEquals(1, engine.handoff().metrics(System.nanoTime()).allocatedBuffers());

            engine.failWorkerForTest(1);
            assertTrue(engine.requestVirtual(viewport, 2));
            assertTrue(engine.awaitIdle(10_000L));
            LiveTrackTelemetry failure = engine.telemetry(System.nanoTime());
            assertEquals(LiveTrackCoordinator.State.FAILED, failure.state());
            assertEquals("LIVE_TRACK_WORKER_FAILURE", failure.failureCategory());
            assertEquals(2L, failure.frames().requestedFrames());
            assertEquals(1L, failure.frames().completedFrames());
            assertFalse(engine.requestVirtual(viewport, 3));
            assertFalse(engine.handoff().requestActive());
        } finally {
            engine.close();
        }
        assertTrue(engine.workersTerminated());
        assertTrue(engine.handoff().isClosed());
        assertEquals(0, engine.handoff().metrics(System.nanoTime()).allocatedBuffers());
    }

    @Test
    void viewerKeepsNavigationOnTheMapAndClosesEveryOwnedResource() throws Exception {
        LiveTrackViewer.ViewerSession viewer = LiveTrackViewer.startHeadless();
        try {
            EventQueue.invokeAndWait(
                    () -> {
                        viewer.stack().setSize(800, 500);
                        viewer.stack().doLayout();
                        viewer.refreshNow();
                        assertSame(
                                viewer.map(),
                                SwingUtilities.getDeepestComponentAt(viewer.stack(), 400, 250));
                        assertTrue(viewer.telemetryText().contains("State RUNNING"));
                    });
            awaitBackground(viewer);
            EventQueue.invokeAndWait(
                    () -> {
                        assertTrue(viewer.map().layerBindings().isEmpty());
                        assertTrue(viewer.chartClosed());
                        MapViewport panned = viewer.map().viewport().panByPixels(25.0, 0.0);
                        assertTrue(viewer.overlay().backgroundCovers(panned));
                        viewer.map().setViewport(panned);
                        viewer.refreshNow();
                    });
        } finally {
            viewer.close();
        }
        assertFalse(viewer.engine().producerAlive());
        assertTrue(viewer.engine().handoff().isClosed());
        assertTrue(viewer.chartClosed());
    }

    private static void awaitBackground(LiveTrackViewer.ViewerSession viewer) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            long[] refreshes = new long[1];
            EventQueue.invokeAndWait(
                    () ->
                            refreshes[0] =
                                    viewer.overlay()
                                            .presentationMetrics(System.nanoTime())
                                            .backgroundRefreshes());
            if (refreshes[0] > 0L) {
                return;
            }
            Thread.sleep(2L);
        }
        throw new AssertionError("static map background timed out");
    }

    @Test
    void viewerAcquiresTheHundredThousandTierBeforeStartingTheUi() throws Exception {
        LiveTrackViewer.ViewerSession viewer = LiveTrackViewer.startHeadless(100_000);
        try {
            assertEquals(100_000, viewer.engine().telemetry(System.nanoTime()).population());
            EventQueue.invokeAndWait(
                    () -> assertTrue(viewer.telemetryText().contains("State RUNNING")));
        } finally {
            viewer.close();
        }
        assertTrue(viewer.chartClosed());
        assertFalse(viewer.engine().producerAlive());
        assertThrows(
                IllegalArgumentException.class, () -> LiveTrackViewer.startHeadless(1_000_001));
    }

    @Test
    void configuredViewerCarriesSettingsIntoTheEngineAndControls() {
        TrackSimulationConfig simulation =
                new TrackSimulationConfig(10_000, 0x1234L, 4, IouKalmanConfig.REFERENCE);
        LiveTrackViewer.ViewerConfiguration configuration =
                new LiveTrackViewer.ViewerConfiguration(simulation, 30, "reference", false);
        LiveTrackViewer.ViewerSession viewer = LiveTrackViewer.startHeadless(configuration);
        try {
            assertEquals(simulation, viewer.engine().configuration());
            assertEquals(30, viewer.fpsCap());
            assertFalse(viewer.telemetryComponent().isEditable());
            assertTrue(viewer.telemetryComponent().getLineWrap());
            assertTrue(viewer.telemetryComponent().getWrapStyleWord());
            assertEquals(5, viewer.telemetryText().lines().count());
            assertEquals(
                    "Population: 10,000 Seed: 0x1234 Workers: 4 Reports: reference",
                    viewer.configurationText());
        } finally {
            viewer.close();
        }
        assertTrue(viewer.chartClosed());
        assertFalse(viewer.engine().producerAlive());
    }

    @Test
    void viewerClosesChartWhenFrameEngineAcquisitionFails() {
        AtomicReference<NaturalEarthChart.ChartSession> opened = new AtomicReference<>();
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                LiveTrackViewer.acquire(
                                        () -> {
                                            NaturalEarthChart.ChartSession chart =
                                                    NaturalEarthChart.startHeadless();
                                            opened.set(chart);
                                            return chart;
                                        },
                                        () -> {
                                            throw new IllegalArgumentException(
                                                    "LIVE_TRACK_DISPLAY_STORAGE_LIMIT");
                                        }));
        NaturalEarthChart.ChartSession chart = opened.get();
        assertNotNull(chart);
        assertEquals("LIVE_TRACK_DISPLAY_STORAGE_LIMIT", failure.getMessage());
        assertTrue(chart.sourceClosed());
        assertFalse(Files.exists(chart.materializedDirectory()));
    }

    private static long countTrackPixels(BufferedImage image) {
        long count = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == LiveTrackRasterizer.TRACK_ARGB) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long countExactColor(BufferedImage image, int color) {
        long count = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long countLandLike(BufferedImage image) {
        long count = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 0xff;
                int green = (rgb >>> 8) & 0xff;
                int blue = rgb & 0xff;
                if (green > red && green > blue && green >= 70) {
                    count++;
                }
            }
        }
        return count;
    }
}
