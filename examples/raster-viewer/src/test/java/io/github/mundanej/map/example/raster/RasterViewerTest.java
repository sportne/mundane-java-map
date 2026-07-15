package io.github.mundanej.map.example.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.awt.MapView;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
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
        assertEquals(image, RasterViewer.parseArguments(new String[] {image.toString()}));
        assertThrows(
                IllegalArgumentException.class, () -> RasterViewer.parseArguments(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterViewer.parseArguments(new String[] {"one.png", "two.png"}));
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
}
