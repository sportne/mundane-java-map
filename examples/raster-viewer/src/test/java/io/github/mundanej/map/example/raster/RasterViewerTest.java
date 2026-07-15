package io.github.mundanej.map.example.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.awt.MapView;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterViewerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validatesTheSingleExplicitArgument() {
        Path image = temporaryDirectory.resolve("image.png");
        assertEquals(image, RasterViewer.parseArguments(new String[] {image.toString()}).path());
        assertTrue(
                RasterViewer.parseArguments(
                                new String[] {image.toString(), "--world-file", "EPSG:4326"})
                        .worldFileCrs()
                        .isPresent());
        assertThrows(
                IllegalArgumentException.class, () -> RasterViewer.parseArguments(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterViewer.parseArguments(new String[] {"one.png", "two.png"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterViewer.parseArguments(
                                new String[] {image.toString(), "--world-file", "EPSG:9999"}));
    }

    @Test
    void explicitlyLoadsAndFitsAWorldFileWithoutGuessingItsCrs() throws Exception {
        Path image = copyGeographicFixture();
        RasterViewer.Arguments arguments =
                RasterViewer.parseArguments(
                        new String[] {image.toString(), "--world-file", "EPSG:4326"});
        RasterSource source = RasterViewer.load(arguments);
        assertEquals(
                RasterGridPlacement.Kind.AFFINE,
                source.metadata().gridPlacement().orElseThrow().kind());
        assertEquals(
                "EPSG:4326",
                source.metadata()
                        .crs()
                        .orElseThrow()
                        .definition()
                        .orElseThrow()
                        .canonicalIdentifier());
        assertEquals(
                new Envelope(-70.125, 39.625, -69.625, 40.125),
                source.metadata().mapBounds().orElseThrow());
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(RasterViewer.createView(source)));
        assertEquals(1, view.get().layerBindings().size());
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void mainFlowReportsFailuresAndSupportsAnExplicitLaunchBoundary() throws Exception {
        java.util.List<String> failures = new java.util.ArrayList<>();
        assertFalse(RasterViewer.runMain(new String[0], failures::add, ignored -> {}));
        assertEquals(1, failures.size());
        assertFalse(
                RasterViewer.runMain(
                        new String[] {temporaryDirectory.resolve("missing.png").toString()},
                        failures::add,
                        ignored -> {}));
        assertTrue(failures.getLast().startsWith("IMAGE_IO_FAILED:"));

        Path image = temporaryDirectory.resolve("flow.png");
        assertTrue(
                ImageIO.write(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        image.toFile()));
        AtomicReference<RasterSource> launched = new AtomicReference<>();
        assertTrue(
                RasterViewer.runMain(
                        new String[] {image.toString()}, failures::add, launched::set));
        assertFalse(launched.get().isClosed());
        launched.get().close();

        assertFalse(
                RasterViewer.runMain(
                        new String[] {image.toString()},
                        failures::add,
                        ignored -> {
                            throw new RuntimeException();
                        }));
        assertEquals("RuntimeException", failures.getLast());
    }

    @Test
    void loadsOffEdtAndTransfersOwnedSourceToARealMapView() throws Exception {
        Path image = temporaryDirectory.resolve("image.png");
        assertTrue(
                ImageIO.write(
                        new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        image.toFile()));
        RasterSource source = RasterViewer.load(image);
        assertFalse(source.isClosed());
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(RasterViewer.createView(source)));
        assertEquals(1, view.get().layerBindings().size());
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void refusesFilesystemDecodeOnTheEdt() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    try {
                        RasterViewer.load(temporaryDirectory.resolve("image.png"));
                    } catch (Throwable caught) {
                        failure.set(caught);
                    }
                });
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    void rejectsAClosedSourceDuringEdtOwnershipTransfer() throws Exception {
        Path image = temporaryDirectory.resolve("closed.png");
        assertTrue(
                ImageIO.write(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        image.toFile()));
        RasterSource source = RasterViewer.load(image);
        source.close();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        RasterViewer.createView(source);
                    } catch (Throwable caught) {
                        failure.set(caught);
                    }
                });
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    void presentationFailureClosesTheViewOwnedSource() throws Exception {
        Path image = temporaryDirectory.resolve("presentation.png");
        assertTrue(
                ImageIO.write(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        image.toFile()));
        RasterSource source = RasterViewer.load(image);
        RuntimeException expected = new RuntimeException("presentation failed");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        RasterViewer.show(
                                source,
                                ignored -> {
                                    throw expected;
                                });
                    } catch (Throwable caught) {
                        failure.set(caught);
                    }
                });
        assertEquals(expected, failure.get());
        assertTrue(source.isClosed());
    }

    @Test
    void worldFileLaunchAndAttachmentFailuresReleaseOwnedResources() throws Exception {
        Path image = temporaryDirectory.resolve("world-cleanup.png");
        Path world = temporaryDirectory.resolve("world-cleanup.pgw");
        assertTrue(
                ImageIO.write(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        image.toFile()));
        java.nio.file.Files.writeString(world, "1\n0\n0\n-1\n0\n0");

        AtomicReference<RasterSource> launched = new AtomicReference<>();
        java.util.List<String> failures = new java.util.ArrayList<>();
        assertFalse(
                RasterViewer.runMain(
                        new String[] {image.toString(), "--world-file", "EPSG:4326"},
                        failures::add,
                        source -> {
                            launched.set(source);
                            throw new IllegalStateException("launch failed");
                        }));
        assertTrue(launched.get().isClosed());
        assertEquals("launch failed", failures.getLast());

        java.nio.file.Files.writeString(world, "1\n0\n0\n-1\n200\n0");
        RasterSource outsideDomain =
                RasterViewer.load(
                        RasterViewer.parseArguments(
                                new String[] {image.toString(), "--world-file", "EPSG:4326"}));
        AtomicReference<Throwable> attachmentFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        RasterViewer.createView(outsideDomain);
                    } catch (Throwable failure) {
                        attachmentFailure.set(failure);
                    }
                });
        assertTrue(attachmentFailure.get() instanceof io.github.mundanej.map.api.CrsException);
        assertTrue(outsideDomain.isClosed());

        java.nio.file.Files.delete(world);
        java.nio.file.Files.delete(image);
    }

    private Path copyGeographicFixture() throws Exception {
        byte[] image =
                Base64.getMimeDecoder().decode(resourceBytes("g6-002/geographic-rgba.png.b64"));
        assertFixture(
                image, 79, "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe");
        byte[] world = resourceBytes("g6-002/geographic-rgba.pgw");
        assertFixture(
                world, 22, "2f04215b9625536b036b768d8bffc03c2045debd66cb34dc0361c5398ffdbbd5");
        Path imagePath = temporaryDirectory.resolve("geographic-rgba.png");
        Files.write(imagePath, image);
        Files.write(temporaryDirectory.resolve("geographic-rgba.pgw"), world);
        return imagePath;
    }

    private static byte[] resourceBytes(String resource) throws IOException {
        try (InputStream input =
                RasterViewerTest.class.getResourceAsStream(
                        "/io/github/mundanej/map/example/raster/" + resource)) {
            if (input == null) {
                throw new IOException("Missing test fixture: " + resource);
            }
            return input.readAllBytes();
        }
    }

    private static void assertFixture(byte[] bytes, int length, String hash) throws Exception {
        assertEquals(length, bytes.length);
        assertEquals(
                hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
