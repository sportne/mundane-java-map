package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import org.junit.jupiter.api.Test;

class NaturalEarthChartTest {
    @Test
    void bundledMembersMatchTheApprovedManifest() throws IOException {
        assertEquals(5, NaturalEarthChart.manifest().size());
        for (NaturalEarthChart.ManifestEntry entry : NaturalEarthChart.manifest()) {
            try (InputStream input = classpath(entry.name())) {
                byte[] content = input.readAllBytes();
                assertEquals(entry.size(), content.length, entry.name());
                assertEquals(entry.sha256(), sha256(content), entry.name());
            }
        }
    }

    @Test
    void sourceIsPreparedOnceInTheDisplayProjection() throws IOException {
        NaturalEarthChart.MaterializedDataset dataset = NaturalEarthChart.openDataset();
        Path directory = dataset.directory();
        FeatureSource source = dataset.source();
        try {
            assertTrue(Files.isDirectory(directory));
            assertEquals(127L, source.metadata().featureCount().orElseThrow());
            assertEquals(
                    CrsDefinitions.EPSG_3857.canonicalIdentifier(),
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            assertTrue(
                    source.metadata().extent().orElseThrow().minX()
                            >= -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(
                    source.metadata().extent().orElseThrow().maxX()
                            <= WebMercatorProjection.WORLD_LIMIT);

            int records = 0;
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                while (cursor.advance()) {
                    FeatureRecord record = cursor.current();
                    assertWithinProjectedMercatorDomain(record.geometry());
                    records++;
                }
                assertTrue(cursor.diagnostics().entries().isEmpty());
            }
            assertEquals(127, records);
        } finally {
            source.close();
        }
        assertTrue(source.isClosed());
        assertFalse(Files.exists(directory));
    }

    @Test
    void missingAndCorruptResourcesHaveStableFailures() {
        NaturalEarthChart.NaturalEarthResourceException missing =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                NaturalEarthChart.openDataset(
                                        name -> {
                                            if (name.equals("ne_110m_land.dbf")) {
                                                throw new IOException("not present");
                                            }
                                            return classpath(name);
                                        }));
        assertEquals("NATURAL_EARTH_RESOURCE_READ_FAILED", missing.code());
        assertEquals("ne_110m_land.dbf", missing.context().get("resource"));

        NaturalEarthChart.NaturalEarthResourceException corrupt =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                NaturalEarthChart.openDataset(
                                        name -> {
                                            if (name.equals("ne_110m_land.shp")) {
                                                return new ByteArrayInputStream(new byte[89_504]);
                                            }
                                            return classpath(name);
                                        }));
        assertEquals("NATURAL_EARTH_RESOURCE_HASH_MISMATCH", corrupt.code());
        assertEquals("ne_110m_land.shp", corrupt.context().get("resource"));

        NaturalEarthChart.NaturalEarthResourceException shortResource =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                NaturalEarthChart.openDataset(
                                        name ->
                                                name.equals("ne_110m_land.shp")
                                                        ? new ByteArrayInputStream(new byte[1])
                                                        : classpath(name)));
        assertEquals("NATURAL_EARTH_RESOURCE_SIZE_MISMATCH", shortResource.code());

        NaturalEarthChart.NaturalEarthResourceException longResource =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                NaturalEarthChart.openDataset(
                                        name ->
                                                name.equals("ne_110m_land.cpg")
                                                        ? new ByteArrayInputStream(new byte[6])
                                                        : classpath(name)));
        assertEquals("NATURAL_EARTH_RESOURCE_SIZE_MISMATCH", longResource.code());
    }

    @Test
    void guiLaunchersFailOnTheEventThreadWithoutLeakingHeadlessResources() throws Exception {
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        CountDownLatch failures = new CountDownLatch(2);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, failure) -> {
                    uncaught.set(failure);
                    failures.countDown();
                });
        try {
            NaturalEarthChart.launch();
            LiveTrackViewer.launch(
                    new LiveTrackViewer.ViewerConfiguration(
                            TrackSimulationConfig.reference(10_000, 1), 0, "reference", false));
            assertTrue(failures.await(30, TimeUnit.SECONDS));
            assertInstanceOf(HeadlessException.class, uncaught.get());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }

        EventQueue.invokeAndWait(
                () -> {
                    assertThrows(IllegalStateException.class, NaturalEarthChart::startHeadless);
                    assertThrows(IllegalStateException.class, LiveTrackViewer::startHeadless);
                    assertThrows(IllegalStateException.class, () -> LiveTrackViewer.launch(10_000));
                });
    }

    @Test
    void viewerControlsAndEdtCloseExerciseInteractiveActions() throws Exception {
        LiveTrackViewer.ViewerSession session =
                LiveTrackViewer.startHeadless(
                        new LiveTrackViewer.ViewerConfiguration(
                                TrackSimulationConfig.reference(10_000, 1), 0, "reference", true));
        try {
            EventQueue.invokeAndWait(
                    () -> {
                        session.stack().setSize(900, 500);
                        session.stack().doLayout();
                        session.map().setSize(900, 500);
                        for (var component : session.toolbar().getComponents()) {
                            if (component instanceof JComboBox<?> choices) {
                                choices.setSelectedItem("30");
                            } else if (component instanceof JButton button) {
                                if (button.getText().equals("Pause")
                                        || button.getText().equals("Resume")
                                        || button.getText().equals("Reset")
                                        || button.getText().equals("Fit world")) {
                                    button.doClick();
                                }
                            }
                        }
                        session.refreshNow();
                        assertEquals(30, session.fpsCap());
                        assertTrue(session.configurationText().contains("Population"));
                        assertTrue(session.telemetryText().contains("State"));
                    });
        } finally {
            session.close();
        }

        LiveTrackViewer.ViewerSession edtClosed = LiveTrackViewer.startHeadless();
        try {
            EventQueue.invokeAndWait(
                    () -> assertThrows(IllegalStateException.class, edtClosed::close));
        } finally {
            edtClosed.close();
        }
        assertTrue(edtClosed.chartClosed());
    }

    @Test
    void diagnosticAndCleanupHelpersPreserveSuppressedFailures() throws Exception {
        List<String> diagnostics = new java.util.ArrayList<>();
        invokePrivate(
                NaturalEarthChart.class,
                "report",
                new Class<?>[] {
                    String.class, DiagnosticReport.class, java.util.function.Consumer.class
                },
                "coverage",
                new DiagnosticReport(List.of(), 2),
                (java.util.function.Consumer<String>) diagnostics::add);
        assertEquals(List.of("natural-earth coverage WARNING OMITTED: 2"), diagnostics);

        Throwable runtimePrimary = new Throwable("primary");
        invokePrivate(
                NaturalEarthChart.class,
                "closeSuppressing",
                new Class<?>[] {AutoCloseable.class, Throwable.class},
                (AutoCloseable)
                        () -> {
                            throw new IllegalStateException("close");
                        },
                runtimePrimary);
        Throwable checkedPrimary = new Throwable("primary");
        invokePrivate(
                NaturalEarthChart.class,
                "closeSuppressing",
                new Class<?>[] {AutoCloseable.class, Throwable.class},
                (AutoCloseable)
                        () -> {
                            throw new IOException("close");
                        },
                checkedPrimary);
        invokePrivate(
                NaturalEarthChart.class,
                "closeSuppressing",
                new Class<?>[] {AutoCloseable.class, Throwable.class},
                null,
                new Throwable("ignored"));
        assertEquals(1, runtimePrimary.getSuppressed().length);
        assertEquals(1, checkedPrimary.getSuppressed().length);

        Path nonempty = Files.createTempDirectory("natural-earth-cleanup-");
        Path manifestDirectory = Files.createDirectory(nonempty.resolve("ne_110m_land.cpg"));
        Files.writeString(manifestDirectory.resolve("child"), "retained");
        NaturalEarthChart.NaturalEarthResourceException cleanup =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                invokePrivate(
                                        NaturalEarthChart.class,
                                        "deleteTree",
                                        new Class<?>[] {Path.class},
                                        nonempty));
        assertEquals("NATURAL_EARTH_CLEANUP_FAILED", cleanup.code());
        Files.delete(manifestDirectory.resolve("child"));
        Files.delete(manifestDirectory);
        Files.delete(nonempty);
    }

    @Test
    void mercatorClipperRetainsCrossingPolygonsAndDropsOutsidePolygons() {
        PolygonGeometry crossing =
                new PolygonGeometry(
                        CoordinateSequence.of(-200, -90, 200, -90, 200, 90, -200, 90, -200, -90),
                        List.of());
        PolygonGeometry outside =
                new PolygonGeometry(
                        CoordinateSequence.of(181, 86, 182, 86, 182, 87, 181, 87, 181, 86),
                        List.of());
        assertTrue(
                ((java.util.Optional<?>)
                                invokePrivate(
                                        MercatorDomainFeatureSource.class,
                                        "clipGeometry",
                                        new Class<?>[] {Geometry.class},
                                        crossing))
                        .isPresent());
        assertTrue(
                ((java.util.Optional<?>)
                                invokePrivate(
                                        MercatorDomainFeatureSource.class,
                                        "clipGeometry",
                                        new Class<?>[] {Geometry.class},
                                        outside))
                        .isEmpty());

        MultiPolygonGeometry mixed =
                MultiPolygonGeometry.of(
                        CoordinateSequence.of(
                                -10, -10, 10, -10, 10, 10, -10, 10, -10, -10, 181, 86, 182, 86, 182,
                                87, 181, 87, 181, 86),
                        new int[] {0, 5, 10},
                        new int[] {0, 1, 2});
        PolygonGeometry retained =
                assertInstanceOf(
                        PolygonGeometry.class,
                        ((java.util.Optional<?>)
                                        invokePrivate(
                                                MercatorDomainFeatureSource.class,
                                                "clipGeometry",
                                                new Class<?>[] {Geometry.class},
                                                mixed))
                                .orElseThrow());
        assertEquals(5, retained.exterior().size());
    }

    @Test
    void detachedBackgroundRendererProducesThePreparedChartOffTheEdt() {
        NaturalEarthChart.MaterializedDataset dataset = NaturalEarthChart.openDataset();
        try {
            MapViewport viewport =
                    MapViewport.fit(
                            900, 500, dataset.source().metadata().extent().orElseThrow(), 24.0);
            BufferedImage image =
                    new NaturalEarthBackgroundRenderer(dataset.projectedFeatures())
                            .render(viewport);
            assertTrue(countColor(image, NaturalEarthChart.OCEAN.getRGB()) > 100_000);
            assertTrue(countLandLike(image) > 20_000);
        } finally {
            dataset.source().close();
        }
    }

    @Test
    void interruptedStartAndCloseRestoreTheInterruptAndAllowCleanup() throws Exception {
        NaturalEarthChart.MaterializedDataset dataset = NaturalEarthChart.openDataset();
        Throwable start =
                interruptWhileEdtBlocked(
                        () ->
                                invokePrivate(
                                        NaturalEarthChart.class,
                                        "startHeadless",
                                        new Class<?>[] {
                                            NaturalEarthChart.MaterializedDataset.class,
                                            java.util.function.Consumer.class
                                        },
                                        dataset,
                                        (java.util.function.Consumer<String>) ignored -> {}));
        assertInstanceOf(IllegalStateException.class, start);
        assertTrue(start.getMessage().contains("START_INTERRUPTED"));

        NaturalEarthChart.ChartSession session = NaturalEarthChart.startHeadless();
        Throwable close = interruptWhileEdtBlocked(session::close);
        assertInstanceOf(IllegalStateException.class, close);
        assertTrue(close.getMessage().contains("CLOSE_INTERRUPTED"));
        session.close();
        assertTrue(session.sourceClosed());
    }

    @Test
    void detachedBackgroundRepeatsAcrossTheDatelineAndMultipleWorlds() {
        NaturalEarthChart.MaterializedDataset dataset = NaturalEarthChart.openDataset();
        try {
            HorizontalWrap wrap = HorizontalWrap.webMercator();
            NaturalEarthBackgroundRenderer renderer =
                    new NaturalEarthBackgroundRenderer(dataset.projectedFeatures(), wrap);
            MapViewport base =
                    MapViewport.fit(
                            900, 500, dataset.source().metadata().extent().orElseThrow(), 24.0);
            BufferedImage canonical = renderer.render(base);
            BufferedImage east =
                    renderer.render(
                            new MapViewport(
                                    base.width(),
                                    base.height(),
                                    base.centerX() + 3.0 * wrap.period(),
                                    base.centerY(),
                                    base.worldUnitsPerPixel()));
            assertEquals(countLandLike(canonical), countLandLike(east));
            assertEquals(
                    countColor(canonical, NaturalEarthChart.OCEAN.getRGB()),
                    countColor(east, NaturalEarthChart.OCEAN.getRGB()));

            BufferedImage seam =
                    renderer.render(
                            new MapViewport(
                                    600, 400, WebMercatorProjection.WORLD_LIMIT, 0.0, 50_000.0));
            assertTrue(countLandLike(seam) > 5_000);
            assertTrue(countColor(seam, NaturalEarthChart.OCEAN.getRGB()) > 20_000);

            for (double centerX :
                    new double[] {
                        WebMercatorProjection.WORLD_LIMIT - 100_000.0,
                        WebMercatorProjection.WORLD_LIMIT + 100_000.0
                    }) {
                MapViewport before = new MapViewport(600, 400, centerX, 0.0, 50_000.0);
                double anchorX = centerX < WebMercatorProjection.WORLD_LIMIT ? 100.0 : 500.0;
                double anchoredWorldX = before.screenToWorld(anchorX, 200.0).x();
                MapViewport after = before.zoomAt(anchorX, 200.0, 2.0);
                assertEquals(
                        anchoredWorldX,
                        after.screenToWorld(anchorX, 200.0).x(),
                        Math.ulp(anchoredWorldX) * 4.0);
                BufferedImage zoomed = renderer.render(after);
                assertTrue(countLandLike(zoomed) > 1_000);
                assertTrue(countColor(zoomed, NaturalEarthChart.OCEAN.getRGB()) > 20_000);
            }
        } finally {
            dataset.source().close();
        }
    }

    @Test
    void offscreenChartShowsAFramedWorldWithLandAndOcean() throws Exception {
        List<String> diagnostics = new java.util.ArrayList<>();
        NaturalEarthChart.ChartSession session = NaturalEarthChart.startHeadless(diagnostics::add);
        Path directory = session.materializedDirectory();
        try {
            assertEquals(
                    List.of("SHAPEFILE_PRJ_OVERRIDE_USED"),
                    session.openingDiagnostics().entries().stream()
                            .map(value -> value.code())
                            .toList());
            assertEquals(
                    List.of(
                            "natural-earth layer=natural-earth-land WARNING "
                                    + "SHAPEFILE_PRJ_OVERRIDE_USED: Shapefile "
                                    + "coordinate-reference diagnostic"),
                    diagnostics);
            assertEquals(
                    CrsDefinitions.EPSG_3857.canonicalIdentifier(),
                    session.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());

            BufferedImage image = new BufferedImage(900, 500, BufferedImage.TYPE_INT_ARGB);
            EventQueue.invokeAndWait(
                    () -> {
                        session.view().setSize(900, 500);
                        session.view().fitToData(24.0);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            session.view().paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    });

            Path reports = Path.of("build", "reports");
            Files.createDirectories(reports);
            Path report = reports.resolve("natural-earth-chart.png");
            ImageIO.write(image, "png", report.toFile());
            int oceanPixels = countColor(image, NaturalEarthChart.OCEAN.getRGB());
            int landPixels = countLandLike(image);
            assertTrue(oceanPixels > 100_000, "ocean pixels=" + oceanPixels);
            assertTrue(
                    landPixels > 20_000,
                    "land pixels=" + landPixels + " reports=" + session.view().sourceReports());
            assertEquals(
                    List.of(
                            "natural-earth layer=natural-earth-land WARNING "
                                    + "SHAPEFILE_PRJ_OVERRIDE_USED: Shapefile "
                                    + "coordinate-reference diagnostic",
                            "natural-earth layer=natural-earth-land WARNING "
                                    + "CRS_QUERY_ENVELOPE_CLIPPED: Visible query envelope was "
                                    + "clipped to the CRS domain"),
                    diagnostics);
            assertTrue(
                    session.view().viewport().visibleWorldEnvelope().minX()
                            < -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(
                    session.view().viewport().visibleWorldEnvelope().maxX()
                            > WebMercatorProjection.WORLD_LIMIT);
        } finally {
            session.close();
        }
        assertTrue(session.sourceClosed());
        assertFalse(Files.exists(directory));
    }

    @Test
    void commandLineRejectsUnknownModes() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> LiveTrackStress.main(new String[] {"--unknown"}));
        assertEquals(LiveTrackStress.USAGE, failure.getMessage());
    }

    @Test
    void commandLineConfigurationIsValidatedBeforeViewerAllocation() {
        LiveTrackViewer.ViewerConfiguration configuration =
                LiveTrackStress.parseViewerConfiguration(
                        new String[] {
                            "--population=1000000",
                            "--seed=0x1234",
                            "--workers=4",
                            "--report-profile=reference",
                            "--fps=30",
                            "--telemetry-stdout"
                        });
        assertEquals(1_000_000, configuration.simulation().population());
        assertEquals(0x1234L, configuration.simulation().seed());
        assertEquals(4, configuration.simulation().workers());
        assertEquals("reference", configuration.reportProfile());
        assertEquals(30, configuration.fpsCap());
        assertTrue(configuration.telemetryStdout());

        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackStress.parseViewerConfiguration(new String[] {"--workers=33"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LiveTrackStress.parseViewerConfiguration(
                                new String[] {"--report-profile=unknown"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackStress.parseViewerConfiguration(new String[] {"--fps=20"}));
    }

    private static InputStream classpath(String name) throws IOException {
        InputStream stream =
                NaturalEarthChartTest.class.getResourceAsStream(
                        NaturalEarthChart.RESOURCE_ROOT + name);
        if (stream == null) {
            throw new IOException("missing test resource " + name);
        }
        return stream;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Throwable interruptWhileEdtBlocked(ThrowingRunnable action) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EventQueue.invokeLater(
                () -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                action.run();
                            } catch (Throwable thrown) {
                                failure.set(thrown);
                            }
                        },
                        "natural-earth-interruption-test");
        worker.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        worker.interrupt();
        worker.join(10_000);
        release.countDown();
        assertFalse(worker.isAlive());
        return failure.get();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertWithinProjectedMercatorDomain(Geometry geometry) {
        if (geometry instanceof PolygonGeometry polygon) {
            assertWithinProjectedMercatorDomain(polygon.exterior());
            polygon.holes().forEach(NaturalEarthChartTest::assertWithinProjectedMercatorDomain);
            return;
        }
        MultiPolygonGeometry polygons = assertInstanceOf(MultiPolygonGeometry.class, geometry);
        assertWithinProjectedMercatorDomain(polygons.coordinates());
    }

    private static void assertWithinProjectedMercatorDomain(CoordinateSequence coordinates) {
        for (int index = 0; index < coordinates.size(); index++) {
            assertTrue(coordinates.x(index) >= -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(coordinates.x(index) <= WebMercatorProjection.WORLD_LIMIT);
            assertTrue(coordinates.y(index) >= -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(coordinates.y(index) <= WebMercatorProjection.WORLD_LIMIT);
        }
    }

    private static int countColor(BufferedImage image, int color) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countLandLike(BufferedImage image) {
        int count = 0;
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

    private static Object invokePrivate(
            Class<?> owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new AssertionError(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
        }
    }
}
