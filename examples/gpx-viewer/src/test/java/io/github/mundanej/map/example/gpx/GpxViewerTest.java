package io.github.mundanej.map.example.gpx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.awt.MapView;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GpxViewerTest {
    private static final String FIXTURE_ROOT = "/io/github/mundanej/map/example/gpx/fixtures/";
    private static final String GPXPY_DIGEST =
            "9ba7ee5166f37d2ce817b4eff012c614f86d143829dc5df3719baa34c5b21a86";

    @TempDir Path temporary;

    @Test
    void rendersWaypointAndTrackWithTolerantGeometryAndColorEvidence() throws Exception {
        Path path = write("render.gpx", validDocument());
        FeatureSource source = GpxViewer.open(path);
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = GpxViewer.createMapView(source);
                    BufferedImage image = new BufferedImage(900, 640, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    viewReference.set(view);
                    imageReference.set(image);
                });

        MapView view = viewReference.get();
        BufferedImage image = imageReference.get();
        Coordinate waypoint = view.mapToScreen(new Coordinate(-77.1, 38.9)).orElseThrow();
        Coordinate lineMiddle = view.mapToScreen(new Coordinate(-76.8, 39.05)).orElseThrow();
        assertColorNear(image, waypoint, true);
        assertColorNear(image, lineMiddle, false);
        EventQueue.invokeAndWait(view::close);
        assertTrue(source.isClosed());
    }

    @Test
    void cliValidatesArgumentsClosesRejectedLaunchAndReportsStableDiagnostics() throws Exception {
        List<String> summaries = new ArrayList<>();
        assertFalse(GpxViewer.runMain(new String[0], summaries::add, ignored -> {}));
        assertEquals(List.of("gpx-viewer: ERROR INPUT_INVALID"), summaries);

        Path malformed = write("malformed-secret.gpx", "<not-gpx/>");
        summaries.clear();
        assertFalse(
                GpxViewer.runMain(
                        new String[] {malformed.toString()}, summaries::add, ignored -> {}));
        assertEquals(
                List.of("gpx-viewer: ERROR GPX_XML_INVALID context={reason=namespace}"), summaries);
        assertFalse(summaries.getFirst().contains("malformed-secret"));

        Path valid = write("valid.gpx", validDocument());
        AtomicReference<FeatureSource> opened = new AtomicReference<>();
        summaries.clear();
        assertFalse(
                GpxViewer.runMain(
                        new String[] {valid.toString()},
                        summaries::add,
                        source -> {
                            opened.set(source);
                            throw new IllegalStateException("injected");
                        }));
        assertTrue(opened.get().isClosed());
        assertEquals(List.of("gpx-viewer: ERROR INPUT_INVALID"), summaries);
    }

    @Test
    void publicPathFactoryLoadsOffEdtAndTransfersOwnershipToView() throws Exception {
        Path path = write("public-factory.gpx", validDocument());
        MapView view = GpxViewer.createMapView(path);
        assertEquals(1, view.layerBindings().size());
        EventQueue.invokeAndWait(view::close);

        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    try {
                        GpxViewer.createMapView(path);
                    } catch (RuntimeException expected) {
                        failure.set(expected);
                    }
                });
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    void successfulCliTransfersTheOpenSource() throws Exception {
        Path path = write("cli.gpx", validDocument());
        AtomicReference<FeatureSource> opened = new AtomicReference<>();
        List<String> summaries = new ArrayList<>();

        assertTrue(
                GpxViewer.runMain(
                        new String[] {path.toString()},
                        summaries::add,
                        source -> {
                            opened.set(source);
                            source.close();
                        }));
        assertTrue(opened.get().isClosed());
        assertTrue(summaries.isEmpty());
    }

    @Test
    void commandEntryPointAndInterruptedFactoryReportStableFailures() throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            GpxViewer.main(new String[0]);
        } finally {
            System.setErr(original);
        }
        assertEquals(
                "gpx-viewer: ERROR INPUT_INVALID" + System.lineSeparator(),
                captured.toString(StandardCharsets.UTF_8));

        Path path = write("interrupted.gpx", validDocument());
        CountDownLatch eventThreadBlocked = new CountDownLatch(1);
        CountDownLatch releaseEventThread = new CountDownLatch(1);
        EventQueue.invokeLater(
                () -> {
                    eventThreadBlocked.countDown();
                    try {
                        releaseEventThread.await();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    }
                });
        eventThreadBlocked.await();
        AtomicReference<Throwable> interrupted = new AtomicReference<>();
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                GpxViewer.createMapView(path);
                            } catch (Throwable failure) {
                                interrupted.set(failure);
                            }
                        });
        try {
            worker.start();
            awaitWaiting(worker);
            worker.interrupt();
            worker.join(5_000);
        } finally {
            releaseEventThread.countDown();
        }
        EventQueue.invokeAndWait(() -> {});
        assertTrue(interrupted.get() instanceof IllegalStateException);
        assertTrue(interrupted.get().getMessage().contains("Interrupted"));
    }

    @Test
    void closedSourceTransferFailsAndSuccessfulInstallerRetainsOwnershipRules() throws Exception {
        FeatureSource closed = GpxViewer.open(write("closed.gpx", validDocument()));
        closed.close();
        AtomicReference<Throwable> transferFailure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    try {
                        GpxViewer.createMapView(closed);
                    } catch (Throwable failure) {
                        transferFailure.set(failure);
                    }
                });
        assertTrue(transferFailure.get() instanceof IllegalStateException);

        FeatureSource installed = GpxViewer.open(write("installed.gpx", validDocument()));
        EventQueue.invokeAndWait(
                () ->
                        GpxViewer.installWindow(
                                installed,
                                view -> {
                                    assertEquals(1, view.layerBindings().size());
                                    view.close();
                                }));
        assertTrue(installed.isClosed());
    }

    @Test
    void asynchronousFactoryPropagatesRuntimeErrorAndCheckedCausesAfterClosing() throws Exception {
        RuntimeException runtime = new IllegalArgumentException("runtime");
        FeatureSource runtimeSource = GpxViewer.open(write("runtime.gpx", validDocument()));
        FutureTask<MapView> runtimeTask = failedTask(runtime);
        assertSame(
                runtime,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> GpxViewer.awaitMapView(runtimeSource, runtimeTask)));
        assertTrue(runtimeSource.isClosed());

        AssertionError error = new AssertionError("error");
        FeatureSource errorSource = GpxViewer.open(write("error.gpx", validDocument()));
        FutureTask<MapView> errorTask = failedTask(error);
        assertSame(
                error,
                assertThrows(
                        AssertionError.class,
                        () -> GpxViewer.awaitMapView(errorSource, errorTask)));
        assertTrue(errorSource.isClosed());

        Exception checked = new Exception("checked");
        FeatureSource checkedSource = GpxViewer.open(write("checked.gpx", validDocument()));
        FutureTask<MapView> checkedTask = failedTask(checked);
        IllegalStateException wrapped =
                assertThrows(
                        IllegalStateException.class,
                        () -> GpxViewer.awaitMapView(checkedSource, checkedTask));
        assertSame(checked, wrapped.getCause());
        assertTrue(checkedSource.isClosed());
    }

    private static FutureTask<MapView> failedTask(Throwable failure) {
        FutureTask<MapView> task =
                new FutureTask<>(
                        () -> {
                            if (failure instanceof Exception exception) {
                                throw exception;
                            }
                            throw (Error) failure;
                        });
        task.run();
        return task;
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("factory thread did not reach its wait state");
    }

    @Test
    void windowInstallationFailureClosesViewAndOwnedSource() throws Exception {
        FeatureSource source = GpxViewer.open(write("window-failure.gpx", validDocument()));
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    try {
                        GpxViewer.installWindow(
                                source,
                                ignored -> {
                                    throw new IllegalStateException("injected");
                                });
                    } catch (RuntimeException expected) {
                        failure.set(expected);
                    }
                });
        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(source.isClosed());
    }

    @Test
    void independentProducerFixtureAndProvenanceAreStableAndReadable() throws Exception {
        byte[] fixture = resource("gpxpy-waypoint-track.gpx");
        assertEquals(
                GPXPY_DIGEST,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture)));
        String provenance = new String(resource("PROVENANCE.md"), StandardCharsets.UTF_8);
        assertTrue(provenance.contains("gpxpy` 1.6.2"));
        assertTrue(provenance.contains("Apache-2.0"));
        assertTrue(provenance.contains("BSD-3-Clause"));
        assertTrue(provenance.contains(GPXPY_DIGEST));

        Path path = temporary.resolve("gpxpy-waypoint-track.gpx");
        Files.write(path, fixture);
        FeatureSource source = GpxViewer.open(path);
        assertEquals(3, source.metadata().featureCount().orElseThrow());
        assertTrue(
                source.openingDiagnostics().entries().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code().equals("GPX_TRACK_POINT_DATA_IGNORED")));
        source.close();
    }

    private Path write(String name, String content) throws Exception {
        Path path = temporary.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static byte[] resource(String name) throws Exception {
        try (var input = GpxViewerTest.class.getResourceAsStream(FIXTURE_ROOT + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing GPX fixture: " + name);
            }
            return input.readAllBytes();
        }
    }

    private static String validDocument() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx xmlns="http://www.topografix.com/GPX/1/1"
                     version="1.1" creator="viewer-test">
                  <wpt lat="38.9" lon="-77.1"><name>Waypoint</name></wpt>
                  <trk><name>Track</name><trkseg>
                    <trkpt lat="38.8" lon="-77.0"/>
                    <trkpt lat="39.05" lon="-76.8"/>
                    <trkpt lat="39.2" lon="-76.6"/>
                  </trkseg></trk>
                </gpx>
                """;
    }

    private static void assertColorNear(
            BufferedImage image, Coordinate coordinate, boolean expectRed) {
        int centerX = (int) Math.round(coordinate.x());
        int centerY = (int) Math.round(coordinate.y());
        boolean found = false;
        for (int y = Math.max(0, centerY - 5);
                y <= Math.min(image.getHeight() - 1, centerY + 5);
                y++) {
            for (int x = Math.max(0, centerX - 5);
                    x <= Math.min(image.getWidth() - 1, centerX + 5);
                    x++) {
                int packed = image.getRGB(x, y);
                int red = packed >>> 16 & 0xff;
                int green = packed >>> 8 & 0xff;
                int blue = packed & 0xff;
                if (expectRed ? red > blue + 50 : blue > red + 40 && blue > green + 20) {
                    found = true;
                }
            }
        }
        assertTrue(found, "expected tolerant color evidence near " + coordinate);
    }
}
