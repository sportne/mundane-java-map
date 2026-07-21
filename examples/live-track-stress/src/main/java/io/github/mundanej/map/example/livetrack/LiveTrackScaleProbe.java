package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameEngine;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackTelemetry;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackViewport;
import java.awt.image.BufferedImage;

/** Opt-in profiling probe for the 100,000-track tier. */
final class LiveTrackScaleProbe {
    private static final int POPULATION = 100_000;

    private LiveTrackScaleProbe() {}

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: live-track-scale-probe <workers> <seconds>");
        }
        int workers = parseWorkers(arguments[0]);
        int targetSecond = parseSeconds(arguments[1]);
        TrackSimulationConfig config = TrackSimulationConfig.reference(POPULATION, workers);
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
        long started = System.nanoTime();
        LiveTrackFrameEngine engine = new LiveTrackFrameEngine(config, 0L);
        long initialized = System.nanoTime();
        long coloredPixels = 0L;
        try {
            for (int second = 0; second <= targetSecond; second += 10) {
                require(engine.requestVirtual(viewport, second), "frame request rejected");
                require(engine.awaitIdle(60_000L), "frame request timed out");
                BufferedImage image =
                        engine.handoff()
                                .frameForPaint(
                                        viewport.generation(),
                                        viewport.width(),
                                        viewport.height(),
                                        System.nanoTime());
                require(image != null, "frame was not published");
                coloredPixels = countColoredPixels(image);
            }
            LiveTrackTelemetry telemetry = engine.telemetry(System.nanoTime());
            require(telemetry.simulationSecond() == targetSecond, "simulation target mismatch");
            require(telemetry.pendingReports() == POPULATION, "report conservation failed");
            require(telemetry.rejectedReports() == 0L, "report rejection occurred");
            require(
                    telemetry.frames().completedFrames() == targetSecond / 10L + 1L,
                    "frame count mismatch");
            long completed = System.nanoTime();
            System.out.printf(
                    "Live-track 100k probe: workers=%d seconds=%d initMs=%.3f totalMs=%.3f reports=%d "
                            + "frames=%d coloredPixels=%d logicalBytes=%d positionBytes=%d "
                            + "shardReports=%d..%d shardReportSkew=%.4f shardWorkSkew=%.4f%n",
                    workers,
                    targetSecond,
                    milliseconds(initialized - started),
                    milliseconds(completed - started),
                    telemetry.processedReports(),
                    telemetry.frames().completedFrames(),
                    coloredPixels,
                    telemetry.logicalTrackBytes(),
                    telemetry.packedPositionBytes(),
                    telemetry.shards().minimumProcessedReports(),
                    telemetry.shards().maximumProcessedReports(),
                    telemetry.shards().reportSkewRatio(),
                    telemetry.shards().workSkewRatio());
        } finally {
            engine.close();
        }
        require(!engine.producerAlive(), "frame producer leaked");
        require(engine.handoff().isClosed(), "frame handoff leaked");
    }

    private static int parseWorkers(String value) {
        try {
            int workers = Integer.parseInt(value);
            TrackSimulationConfig.reference(POPULATION, workers);
            return workers;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("workers must be an integer in [1, 32]", exception);
        }
    }

    private static int parseSeconds(String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 10 || seconds > 3_600 || seconds % 10 != 0) {
                throw new IllegalArgumentException();
            }
            return seconds;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "seconds must be a multiple of 10 in [10, 3600]", exception);
        }
    }

    private static long countColoredPixels(BufferedImage image) {
        long count = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == LiveTrackFrames.LiveTrackRasterizer.TRACK_ARGB) {
                    count++;
                }
            }
        }
        return count;
    }

    private static double milliseconds(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("LIVE_TRACK_100K_PROBE_FAILED: " + message);
        }
    }
}
