package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameEngine;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFrameMetrics;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackFramePacer;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackOverlay;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackPresentationMetrics;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackTelemetry;
import io.github.mundanej.map.example.livetrack.LiveTrackFrames.LiveTrackViewport;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/** Interactive 10,000-, 100,000-, and 1,000,000-track stress picture. */
final class LiveTrackViewer {
    private static final int DEFAULT_FPS = 10;

    private LiveTrackViewer() {}

    static void launch() {
        launch(ViewerConfiguration.reference(10_000));
    }

    static void launch(int population) {
        launch(ViewerConfiguration.reference(population));
    }

    static void launch(ViewerConfiguration configuration) {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("live-track loading must run off the event thread");
        }
        Objects.requireNonNull(configuration, "configuration");
        requireViewerPopulation(configuration.simulation().population());
        ViewerResources resources =
                acquire(
                        () -> NaturalEarthChart.startHeadless(System.err::println),
                        () ->
                                new LiveTrackFrameEngine(
                                        configuration.simulation(), System.nanoTime()));
        NaturalEarthChart.ChartSession chart = resources.chart();
        LiveTrackFrameEngine engine = resources.engine();
        try {
            EventQueue.invokeLater(
                    () -> {
                        try {
                            start(
                                    chart,
                                    engine,
                                    true,
                                    configuration.fpsCap(),
                                    configuration.reportProfile(),
                                    configuration.telemetryStdout());
                        } catch (RuntimeException | Error failure) {
                            Thread cleanup =
                                    new Thread(
                                            () -> {
                                                try {
                                                    engine.close();
                                                } finally {
                                                    chart.close();
                                                }
                                            },
                                            "live-track-start-cleanup");
                            cleanup.setDaemon(true);
                            cleanup.start();
                            throw failure;
                        }
                    });
        } catch (RuntimeException | Error failure) {
            engine.close();
            chart.close();
            throw failure;
        }
    }

    static ViewerSession startHeadless() {
        return startHeadless(ViewerConfiguration.reference(10_000));
    }

    static ViewerSession startHeadless(int population) {
        return startHeadless(ViewerConfiguration.reference(population));
    }

    static ViewerSession startHeadless(ViewerConfiguration configuration) {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("live-track loading must run off the event thread");
        }
        Objects.requireNonNull(configuration, "configuration");
        requireViewerPopulation(configuration.simulation().population());
        ViewerResources resources =
                acquire(
                        NaturalEarthChart::startHeadless,
                        () ->
                                new LiveTrackFrameEngine(
                                        configuration.simulation(), System.nanoTime()));
        NaturalEarthChart.ChartSession chart = resources.chart();
        LiveTrackFrameEngine engine = resources.engine();
        try {
            ViewerSession[] result = new ViewerSession[1];
            EventQueue.invokeAndWait(
                    () ->
                            result[0] =
                                    start(
                                            chart,
                                            engine,
                                            false,
                                            configuration.fpsCap(),
                                            configuration.reportProfile(),
                                            configuration.telemetryStdout()));
            return Objects.requireNonNull(result[0]);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            engine.close();
            chart.close();
            throw new IllegalStateException("LIVE_TRACK_VIEWER_START_INTERRUPTED", failure);
        } catch (InvocationTargetException failure) {
            engine.close();
            chart.close();
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("LIVE_TRACK_VIEWER_START_FAILED", cause);
        }
    }

    static ViewerResources acquire(ChartFactory chartFactory, EngineFactory engineFactory) {
        Objects.requireNonNull(chartFactory, "chartFactory");
        Objects.requireNonNull(engineFactory, "engineFactory");
        NaturalEarthChart.ChartSession chart = chartFactory.open();
        try {
            return new ViewerResources(chart, engineFactory.create());
        } catch (RuntimeException | Error failure) {
            try {
                chart.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    @FunctionalInterface
    interface ChartFactory {
        NaturalEarthChart.ChartSession open();
    }

    @FunctionalInterface
    interface EngineFactory {
        LiveTrackFrameEngine create();
    }

    record ViewerResources(NaturalEarthChart.ChartSession chart, LiveTrackFrameEngine engine) {
        ViewerResources {
            Objects.requireNonNull(chart, "chart");
            Objects.requireNonNull(engine, "engine");
        }
    }

    record ViewerConfiguration(
            TrackSimulationConfig simulation,
            int fpsCap,
            String reportProfile,
            boolean telemetryStdout) {
        ViewerConfiguration {
            Objects.requireNonNull(simulation, "simulation");
            Objects.requireNonNull(reportProfile, "reportProfile");
            requireViewerPopulation(simulation.population());
            if (fpsCap != 0
                    && fpsCap != 1
                    && fpsCap != 2
                    && fpsCap != 5
                    && fpsCap != 10
                    && fpsCap != 15
                    && fpsCap != 30
                    && fpsCap != 60) {
                throw new IllegalArgumentException("fps cap is outside the approved set");
            }
            if (!reportProfile.equals("reference")) {
                throw new IllegalArgumentException("unsupported report profile");
            }
        }

        static ViewerConfiguration reference(int population) {
            return new ViewerConfiguration(
                    TrackSimulationConfig.reference(
                            population, TrackSimulationConfig.defaultWorkers(population)),
                    DEFAULT_FPS,
                    "reference",
                    false);
        }
    }

    private static ViewerSession start(
            NaturalEarthChart.ChartSession chart,
            LiveTrackFrameEngine engine,
            boolean installWindow,
            int initialFps,
            String reportProfile,
            boolean telemetryStdout) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("live-track viewer must start on the event thread");
        }
        MapView map = chart.view();
        LiveTrackOverlay overlay = new LiveTrackOverlay(engine.handoff());
        overlay.setBackground(NaturalEarthChart.OCEAN);
        MapStack stack = new MapStack(map, overlay);
        ViewerControls controls =
                new ViewerControls(
                        map,
                        overlay,
                        engine,
                        chart.projectedFeatures(),
                        chart.metadata().extent().orElseThrow(),
                        initialFps,
                        reportProfile,
                        telemetryStdout);
        JPanel content = new JPanel(new BorderLayout());
        content.add(controls.toolbar(), BorderLayout.NORTH);
        content.add(stack, BorderLayout.CENTER);
        content.add(controls.telemetryLabel(), BorderLayout.SOUTH);
        JFrame frame = null;
        if (installWindow) {
            int population = engine.telemetry(System.nanoTime()).population();
            frame = new JFrame("mundane-java-map — " + tierName(population) + " live-track stress");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setContentPane(content);
            frame.pack();
            frame.setLocationByPlatform(true);
        }
        ViewerSession session =
                new ViewerSession(
                        chart, engine, controls, stack, overlay, Optional.ofNullable(frame));
        if (frame != null) {
            JFrame installedFrame = frame;
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent event) {
                            session.closeAsynchronously(installedFrame);
                        }
                    });
            frame.setVisible(true);
        }
        controls.start();
        return session;
    }

    static void requireViewerPopulation(int population) {
        if (population != 10_000 && population != 100_000 && population != 1_000_000) {
            throw new IllegalArgumentException(
                    "viewer population must be 10000, 100000, or 1000000");
        }
    }

    private static String tierName(int population) {
        if (population == 10_000) {
            return "10k";
        }
        return population == 100_000 ? "100k" : "1m";
    }

    static final class ViewerSession implements AutoCloseable {
        private final NaturalEarthChart.ChartSession chart;
        private final LiveTrackFrameEngine engine;
        private final ViewerControls controls;
        private final MapStack stack;
        private final LiveTrackOverlay overlay;
        private final Optional<JFrame> frame;
        private boolean closing;

        ViewerSession(
                NaturalEarthChart.ChartSession chart,
                LiveTrackFrameEngine engine,
                ViewerControls controls,
                MapStack stack,
                LiveTrackOverlay overlay,
                Optional<JFrame> frame) {
            this.chart = chart;
            this.engine = engine;
            this.controls = controls;
            this.stack = stack;
            this.overlay = overlay;
            this.frame = frame;
        }

        MapView map() {
            return chart.view();
        }

        LiveTrackOverlay overlay() {
            return overlay;
        }

        LiveTrackFrameEngine engine() {
            return engine;
        }

        JLayeredPane stack() {
            return stack;
        }

        String telemetryText() {
            return controls.telemetryLabel().getText();
        }

        JTextField telemetryComponent() {
            return controls.telemetryLabel();
        }

        String configurationText() {
            return controls.configurationText();
        }

        int fpsCap() {
            return controls.fpsCap();
        }

        void refreshNow() {
            controls.frameTick();
        }

        void stopTimers() {
            controls.stop();
        }

        boolean chartClosed() {
            return chart.sourceClosed();
        }

        private void closeAsynchronously(JFrame installedFrame) {
            if (!EventQueue.isDispatchThread()) {
                throw new IllegalStateException("window close must begin on the EDT");
            }
            if (closing) {
                return;
            }
            closing = true;
            controls.close();
            installedFrame.setEnabled(false);
            Thread closer =
                    new Thread(
                            () -> {
                                try {
                                    engine.close();
                                } finally {
                                    try {
                                        chart.close();
                                    } finally {
                                        EventQueue.invokeLater(installedFrame::dispose);
                                    }
                                }
                            },
                            "live-track-viewer-close");
            closer.setDaemon(true);
            closer.start();
        }

        @Override
        public void close() {
            if (EventQueue.isDispatchThread()) {
                throw new IllegalStateException("synchronous viewer close must run off the EDT");
            }
            try {
                stopControls();
            } finally {
                try {
                    engine.close();
                } finally {
                    chart.close();
                    frame.ifPresent(value -> EventQueue.invokeLater(value::dispose));
                }
            }
        }

        private void stopControls() {
            try {
                EventQueue.invokeAndWait(controls::close);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("LIVE_TRACK_VIEWER_CLOSE_INTERRUPTED", failure);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("LIVE_TRACK_VIEWER_CLOSE_FAILED", cause);
            }
        }
    }

    @SuppressWarnings("serial")
    private static final class MapStack extends JLayeredPane {
        private final MapView map;
        private final LiveTrackOverlay overlay;

        MapStack(MapView map, LiveTrackOverlay overlay) {
            this.map = map;
            this.overlay = overlay;
            setPreferredSize(new Dimension(1_200, 700));
            add(map, JLayeredPane.DEFAULT_LAYER);
            add(overlay, JLayeredPane.PALETTE_LAYER);
        }

        @Override
        public void doLayout() {
            map.setBounds(0, 0, getWidth(), getHeight());
            overlay.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    private static final class ViewerControls {
        private final MapView map;
        private final LiveTrackOverlay overlay;
        private final LiveTrackFrameEngine engine;
        private final JPanel toolbar = new JPanel();
        private final JTextField telemetry = new JTextField("Starting live picture…");
        private final JButton pause = new JButton("Pause");
        private final LiveTrackFramePacer pacer;
        private final String configurationText;
        private final boolean telemetryStdout;
        private final Timer frameTimer;
        private final Timer telemetryTimer;
        private final StaticMapBackgroundCache backgroundCache;
        private final Envelope chartExtent;
        private MapViewport lastViewport;
        private long generation;
        private long lastCompleted;
        private boolean mapContentDetached;

        ViewerControls(
                MapView map,
                LiveTrackOverlay overlay,
                LiveTrackFrameEngine engine,
                List<FeatureRecord> projectedFeatures,
                Envelope chartExtent,
                int initialFps,
                String reportProfile,
                boolean telemetryStdout) {
            this.map = map;
            this.overlay = overlay;
            this.engine = engine;
            this.telemetryStdout = telemetryStdout;
            this.chartExtent = Objects.requireNonNull(chartExtent, "chartExtent");
            backgroundCache =
                    new StaticMapBackgroundCache(
                            new NaturalEarthBackgroundRenderer(projectedFeatures),
                            this::installBackgroundSnapshot,
                            this::backgroundFailure);
            pacer = new LiveTrackFramePacer(initialFps);
            telemetry.setEditable(false);
            telemetry.setBorder(null);
            configurationText =
                    String.format(
                            Locale.ROOT,
                            "Population: %,d Seed: 0x%s Workers: %d Reports: %s",
                            engine.configuration().population(),
                            Long.toHexString(engine.configuration().seed()),
                            engine.configuration().workers(),
                            reportProfile);
            toolbar.add(
                    new JLabel(
                            String.format(
                                    Locale.ROOT,
                                    "Population: %,d",
                                    engine.telemetry(System.nanoTime()).population())));
            toolbar.add(new JLabel(" Seed: 0x" + Long.toHexString(engine.configuration().seed())));
            toolbar.add(new JLabel(" Workers: " + engine.telemetry(System.nanoTime()).workers()));
            toolbar.add(new JLabel(" Reports: " + reportProfile));
            toolbar.add(new JLabel(" Max FPS:"));
            JComboBox<String> fps =
                    new JComboBox<>(
                            new String[] {"1", "2", "5", "10", "15", "30", "60", "Uncapped"});
            fps.setSelectedItem(initialFps == 0 ? "Uncapped" : Integer.toString(initialFps));
            fps.addActionListener(
                    ignored -> {
                        String selected = Objects.requireNonNull((String) fps.getSelectedItem());
                        pacer.setCap(selected.equals("Uncapped") ? 0 : Integer.parseInt(selected));
                    });
            toolbar.add(fps);
            pause.addActionListener(ignored -> togglePause());
            toolbar.add(pause);
            JButton reset = new JButton("Reset");
            reset.addActionListener(ignored -> engine.requestReset(System.nanoTime()));
            toolbar.add(reset);
            JButton fit = new JButton("Fit world");
            fit.addActionListener(ignored -> fitWorld());
            toolbar.add(fit);
            JButton copy = new JButton("Copy telemetry");
            copy.addActionListener(
                    ignored -> {
                        telemetry.requestFocusInWindow();
                        telemetry.selectAll();
                        telemetry.copy();
                    });
            toolbar.add(copy);
            frameTimer = new Timer(16, ignored -> frameTick());
            frameTimer.setCoalesce(true);
            telemetryTimer = new Timer(1_000, ignored -> telemetryTick());
            telemetryTimer.setCoalesce(true);
        }

        JPanel toolbar() {
            return toolbar;
        }

        JTextField telemetryLabel() {
            return telemetry;
        }

        String configurationText() {
            return configurationText;
        }

        int fpsCap() {
            return pacer.cap();
        }

        void start() {
            requireEdt();
            frameTimer.start();
            telemetryTimer.start();
            telemetryTick();
        }

        void stop() {
            requireEdt();
            frameTimer.stop();
            telemetryTimer.stop();
        }

        void close() {
            requireEdt();
            stop();
            backgroundCache.close();
        }

        private void frameTick() {
            requireEdt();
            MapViewport viewport = map.viewport();
            if (!viewport.equals(lastViewport)) {
                lastViewport = viewport;
                generation++;
                overlay.setViewport(generation, viewport);
                if (!overlay.backgroundCovers(viewport)) {
                    backgroundCache.request(viewport);
                }
            }
            long now = System.nanoTime();
            if (pacer.shouldRequest(now)) {
                engine.requestRealTime(new LiveTrackViewport(generation, viewport), now);
            }
            long completed = engine.handoff().completedFrames();
            if (completed != lastCompleted) {
                lastCompleted = completed;
                overlay.repaint();
            }
        }

        private void telemetryTick() {
            requireEdt();
            LiveTrackTelemetry snapshot = engine.telemetry(System.nanoTime());
            LiveTrackFrameMetrics frames = snapshot.frames();
            LiveTrackPresentationMetrics presentation =
                    overlay.presentationMetrics(System.nanoTime());
            pause.setText(
                    snapshot.state() == LiveTrackCoordinator.State.PAUSED ? "Resume" : "Pause");
            String telemetryText =
                    String.format(
                            Locale.ROOT,
                            "State %s | t=%ds | screen %.1f FPS, engine %.1f FPS (cap %s) | "
                                    + "frames r/c/present %d/%d/%d, "
                                    + "skip/stale %d/%d | build p50/p95/p99/max %.2f/%.2f/%.2f/%.2f ms | "
                                    + "EDT paint p50/p95/p99/max %.2f/%.2f/%.2f/%.2f ms | "
                                    + "map cache refresh/last/max %d/%.2f/%.2f ms | "
                                    + "reports scheduled/processed/pending/rejected/late %d/%d/%d/%d/%d | "
                                    + "backlog %ds | shard reports %d..%d (%.3fx), work %.3fx | "
                                    + "memory logical/frame/heap %.1f/%.1f/%.1f MiB%s",
                            snapshot.state(),
                            snapshot.simulationSecond(),
                            presentation.achievedFps(),
                            frames.achievedFps(),
                            pacer.cap() == 0 ? "uncapped" : Integer.toString(pacer.cap()),
                            frames.requestedFrames(),
                            frames.completedFrames(),
                            presentation.presentedFrames(),
                            frames.skippedRequests(),
                            frames.staleDiscards(),
                            milliseconds(frames.buildP50Nanos()),
                            milliseconds(frames.buildP95Nanos()),
                            milliseconds(frames.buildP99Nanos()),
                            milliseconds(frames.buildMaximumNanos()),
                            milliseconds(presentation.paintP50Nanos()),
                            milliseconds(presentation.paintP95Nanos()),
                            milliseconds(presentation.paintP99Nanos()),
                            milliseconds(presentation.paintMaximumNanos()),
                            presentation.backgroundRefreshes(),
                            milliseconds(presentation.backgroundLastNanos()),
                            milliseconds(presentation.backgroundMaximumNanos()),
                            snapshot.scheduledReports(),
                            snapshot.processedReports(),
                            snapshot.pendingReports(),
                            snapshot.rejectedReports(),
                            snapshot.lateReports(),
                            snapshot.backlogSeconds(),
                            snapshot.shards().minimumProcessedReports(),
                            snapshot.shards().maximumProcessedReports(),
                            snapshot.shards().reportSkewRatio(),
                            snapshot.shards().workSkewRatio(),
                            mebibytes(
                                    snapshot.logicalTrackBytes() + snapshot.packedPositionBytes()),
                            mebibytes(frames.frameBufferBytes()),
                            mebibytes(snapshot.observedHeap()),
                            snapshot.failureCategory().isEmpty()
                                    ? ""
                                    : " | failure " + snapshot.failureCategory());
            telemetry.setText(telemetryText);
            if (telemetryStdout) {
                System.out.println("live-track telemetry: " + telemetryText);
            }
        }

        private void installBackgroundSnapshot(StaticMapBackgroundCache.Snapshot snapshot) {
            requireEdt();
            if (overlay.installBackground(
                    snapshot.renderViewport(), snapshot.image(), snapshot.renderNanos())) {
                if (!mapContentDetached) {
                    map.setLayerBindings(List.of());
                    mapContentDetached = true;
                }
                overlay.repaint();
            }
        }

        private void fitWorld() {
            requireEdt();
            int width = map.getWidth();
            int height = map.getHeight();
            if (width > 0 && height > 0) {
                map.setViewport(MapViewport.fit(width, height, chartExtent, 24.0));
            }
        }

        private void backgroundFailure(Throwable failure) {
            requireEdt();
            telemetry.setText("Static map rendering failed: " + failure.getClass().getSimpleName());
        }

        private void togglePause() {
            LiveTrackCoordinator.State state = engine.telemetry(System.nanoTime()).state();
            if (state == LiveTrackCoordinator.State.PAUSED) {
                engine.requestResume(System.nanoTime());
            } else if (state == LiveTrackCoordinator.State.RUNNING) {
                engine.requestPause(System.nanoTime());
            }
        }

        private static double milliseconds(long nanos) {
            return nanos / 1_000_000.0;
        }

        private static double mebibytes(long bytes) {
            return bytes / (1024.0 * 1024.0);
        }

        private static void requireEdt() {
            if (!EventQueue.isDispatchThread()) {
                throw new IllegalStateException("live-track controls require the EDT");
            }
        }
    }
}
