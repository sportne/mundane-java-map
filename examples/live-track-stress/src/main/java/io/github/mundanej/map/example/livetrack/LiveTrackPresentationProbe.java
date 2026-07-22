package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameMetrics;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackPresentationMetrics;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Opt-in headless probe for cached-map composition and EDT paint cost. */
public final class LiveTrackPresentationProbe {
    private static final int WIDTH = 900;
    private static final int HEIGHT = 500;
    private static final int FRAMES = 60;
    private static final int NAVIGATION_STEPS = 12;

    private LiveTrackPresentationProbe() {}

    /**
     * Runs the requested supported population through sixty actual overlay paints.
     *
     * @param args exactly one of {@code 10k}, {@code 100k}, or {@code 1m}
     * @throws Exception if EDT coordination fails
     */
    public static void main(String[] args) throws Exception {
        int population = parsePopulation(args);
        TrackSimulationConfig simulation =
                TrackSimulationConfig.reference(
                        population, TrackSimulationConfig.defaultWorkers(population));
        LiveTrackViewer.ViewerConfiguration configuration =
                new LiveTrackViewer.ViewerConfiguration(simulation, 0, "reference", false);
        LiveTrackViewer.ViewerSession viewer = LiveTrackViewer.startHeadless(configuration);
        BufferedImage target = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        try {
            EventQueue.invokeAndWait(
                    () -> {
                        viewer.stopTimers();
                        viewer.stack().setSize(WIDTH, HEIGHT);
                        viewer.stack().doLayout();
                        viewer.refreshNow();
                    });
            awaitBackgroundRefresh(viewer, 1L);
            for (int frame = 0; frame < FRAMES; frame++) {
                if (!viewer.engine().awaitIdle(120_000L)) {
                    throw new IllegalStateException("LIVE_TRACK_PRESENTATION_FRAME_TIMEOUT");
                }
                boolean requestNext = frame + 1 < FRAMES;
                EventQueue.invokeAndWait(
                        () -> {
                            Graphics2D graphics = target.createGraphics();
                            try {
                                viewer.overlay().paint(graphics);
                            } finally {
                                graphics.dispose();
                            }
                            if (requestNext) {
                                viewer.refreshNow();
                            }
                        });
            }
            long[] navigationPaints = cachedNavigationPaints(viewer, target);
            long refreshesBeforeZoom =
                    viewer.overlay().presentationMetrics(System.nanoTime()).backgroundRefreshes();
            EventQueue.invokeAndWait(
                    () -> {
                        MapViewport current = viewer.map().viewport();
                        viewer.map()
                                .setViewport(
                                        current.zoomAt(
                                                current.width() / 2.0,
                                                current.height() / 2.0,
                                                2.0));
                        viewer.refreshNow();
                    });
            awaitBackgroundRefresh(viewer, refreshesBeforeZoom + 1L);
            LiveTrackFrameMetrics engine = viewer.engine().telemetry(System.nanoTime()).frames();
            LiveTrackPresentationMetrics presentation =
                    viewer.overlay().presentationMetrics(System.nanoTime());
            System.out.printf(
                    "Live-track presentation: population=%d frames=%d "
                            + "build-p95=%.3fms EDT-paint-p95=%.3fms "
                            + "map-cache-refreshes=%d map-cache-last/max=%.3f/%.3fms "
                            + "cached-navigation-p50/p95/max=%.3f/%.3f/%.3fms%n",
                    population,
                    presentation.presentedFrames(),
                    milliseconds(engine.buildP95Nanos()),
                    milliseconds(presentation.paintP95Nanos()),
                    presentation.backgroundRefreshes(),
                    milliseconds(presentation.backgroundLastNanos()),
                    milliseconds(presentation.backgroundMaximumNanos()),
                    milliseconds(percentile(navigationPaints, 0.50)),
                    milliseconds(percentile(navigationPaints, 0.95)),
                    milliseconds(navigationPaints[navigationPaints.length - 1]));
        } finally {
            viewer.close();
        }
    }

    private static long[] cachedNavigationPaints(
            LiveTrackViewer.ViewerSession viewer, BufferedImage target) throws Exception {
        long[] durations = new long[NAVIGATION_STEPS];
        long refreshes =
                viewer.overlay().presentationMetrics(System.nanoTime()).backgroundRefreshes();
        EventQueue.invokeAndWait(
                () -> {
                    for (int index = 0; index < NAVIGATION_STEPS; index++) {
                        long started = System.nanoTime();
                        viewer.map().setViewport(viewer.map().viewport().panByPixels(8.0, 0.0));
                        viewer.refreshNow();
                        Graphics2D graphics = target.createGraphics();
                        try {
                            viewer.overlay().paint(graphics);
                        } finally {
                            durations[index] = System.nanoTime() - started;
                            graphics.dispose();
                        }
                    }
                });
        long after = viewer.overlay().presentationMetrics(System.nanoTime()).backgroundRefreshes();
        if (after != refreshes) {
            throw new IllegalStateException("cached pan unexpectedly refreshed the static map");
        }
        Arrays.sort(durations);
        return durations;
    }

    private static void awaitBackgroundRefresh(
            LiveTrackViewer.ViewerSession viewer, long minimumRefreshes) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            long[] refreshes = new long[1];
            EventQueue.invokeAndWait(
                    () ->
                            refreshes[0] =
                                    viewer.overlay()
                                            .presentationMetrics(System.nanoTime())
                                            .backgroundRefreshes());
            if (refreshes[0] >= minimumRefreshes) {
                return;
            }
            Thread.sleep(2L);
        }
        throw new IllegalStateException("static map background refresh timed out");
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static int parsePopulation(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: live-track-presentation <10k|100k|1m>");
        }
        return switch (args[0]) {
            case "10k" -> 10_000;
            case "100k" -> 100_000;
            case "1m" -> 1_000_000;
            default ->
                    throw new IllegalArgumentException(
                            "Usage: live-track-presentation <10k|100k|1m>");
        };
    }

    private static double milliseconds(long nanos) {
        return nanos / 1_000_000.0;
    }
}
