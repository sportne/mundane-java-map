package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameEngine;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackOverlay;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackRasterizer;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackTelemetry;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackViewport;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic headless 10,000-track smoke entry point. */
final class LiveTrackSmoke {
    private LiveTrackSmoke() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("live-track smoke accepts no arguments");
        }
        NaturalEarthChart.ChartSession chart = NaturalEarthChart.startHeadless();
        int workers = TrackSimulationConfig.defaultWorkers(10_000);
        LiveTrackFrameEngine engine =
                new LiveTrackFrameEngine(TrackSimulationConfig.reference(10_000, workers), 0L);
        LiveTrackOverlay overlay = new LiveTrackOverlay(engine.handoff());
        AtomicInteger coloredPixels = new AtomicInteger();
        try {
            MapViewport viewport =
                    EventQueue.isDispatchThread()
                            ? chart.view().viewport()
                            : viewport(chart, overlay);
            LiveTrackViewport snapshot = new LiveTrackViewport(1L, viewport);
            for (int second = 0; second <= 120; second += 10) {
                require(engine.requestVirtual(snapshot, second), "frame request rejected");
                require(engine.awaitIdle(30_000L), "frame request timed out");
                paint(overlay, snapshot, coloredPixels);
            }
            LiveTrackTelemetry telemetry = engine.telemetry(System.nanoTime());
            require(telemetry.simulationSecond() == 120, "simulation did not reach 120 seconds");
            require(telemetry.processedReports() > 100_000L, "too few reports were processed");
            require(telemetry.rejectedReports() == 0L, "reports were rejected");
            require(telemetry.pendingReports() == 10_000L, "pending report conservation failed");
            require(telemetry.frames().completedFrames() == 13L, "frame completion mismatch");
            require(telemetry.frames().paintedFrames() == 13L, "frame paint mismatch");
            require(coloredPixels.get() > 1_000, "track overlay contained too few pixels");

            LiveTrackViewport stale = new LiveTrackViewport(2L, viewport);
            require(engine.requestVirtual(stale, 120), "stale frame request rejected");
            require(engine.awaitIdle(30_000L), "stale frame request timed out");
            EventQueue.invokeAndWait(
                    () -> {
                        overlay.setViewport(3L, stale.viewport());
                        BufferedImage image =
                                new BufferedImage(
                                        stale.width(), stale.height(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            overlay.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    });
            require(
                    engine.telemetry(System.nanoTime()).frames().staleDiscards() == 1L,
                    "stale frame was not discarded");
            System.out.printf(
                    "Live-track smoke: population=10000 workers=%d seconds=120 reports=%d "
                            + "frames=%d coloredPixels=%d%n",
                    workers,
                    telemetry.processedReports(),
                    telemetry.frames().paintedFrames(),
                    coloredPixels.get());
        } finally {
            engine.close();
            chart.close();
        }
        require(!engine.producerAlive(), "frame producer leaked");
        require(engine.handoff().isClosed(), "frame buffers did not close");
    }

    private static MapViewport viewport(
            NaturalEarthChart.ChartSession chart, LiveTrackOverlay overlay) throws Exception {
        MapViewport[] result = new MapViewport[1];
        EventQueue.invokeAndWait(
                () -> {
                    chart.view().setSize(900, 500);
                    chart.view().fitToData(24.0);
                    overlay.setSize(900, 500);
                    result[0] = chart.view().viewport();
                    overlay.setViewport(1L, result[0]);
                });
        return result[0];
    }

    private static void paint(
            LiveTrackOverlay overlay, LiveTrackViewport viewport, AtomicInteger coloredPixels)
            throws Exception {
        EventQueue.invokeAndWait(
                () -> {
                    overlay.setViewport(viewport.generation(), viewport.viewport());
                    BufferedImage image =
                            new BufferedImage(
                                    viewport.width(),
                                    viewport.height(),
                                    BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        overlay.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    int count = 0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            if (image.getRGB(x, y) == LiveTrackRasterizer.TRACK_ARGB) {
                                count++;
                            }
                        }
                    }
                    coloredPixels.set(count);
                });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("LIVE_TRACK_SMOKE_FAILED: " + message);
        }
    }
}
