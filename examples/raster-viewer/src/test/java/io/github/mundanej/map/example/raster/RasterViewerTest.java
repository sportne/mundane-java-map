package io.github.mundanej.map.example.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.awt.MapView;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterViewerTest {
    private static final String SENSITIVE_PARENT_TOKEN = "SENSITIVE_RASTER_PARENT_3B8E";
    private static final String SENSITIVE_STEM_TOKEN = "SENSITIVE_RASTER_STEM_6D1A";

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
    void explicitlyLoadsFitsAndPresentsAWorldFileWithoutExposingItsPath() throws Exception {
        Path sensitiveDirectory = temporaryDirectory.resolve(SENSITIVE_PARENT_TOKEN);
        Files.createDirectories(sensitiveDirectory);
        Path image = copyGeographicFixture(sensitiveDirectory, SENSITIVE_STEM_TOKEN);
        RasterViewer.Arguments arguments =
                RasterViewer.parseArguments(
                        new String[] {image.toString(), "--world-file", "EPSG:4326"});
        RasterSource source = RasterViewer.load(arguments);
        assertEquals("raster-viewer", source.metadata().identity().id());
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
        AtomicReference<List<String>> presentation = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView created = RasterViewer.createView(source);
                    JLabel status = new JLabel();
                    JPanel controls = RasterViewer.createControls(created, status);
                    view.set(created);
                    presentation.set(presentationStrings(source, created, controls, status));
                });
        assertEquals(1, view.get().layerBindings().size());
        assertTrue(presentation.get().contains("raster-viewer"));
        assertTrue(presentation.get().contains("Raster image"));
        assertTrue(presentation.get().contains("image"));
        assertTrue(presentation.get().contains(RasterViewer.WORLD_FILE_LABEL));
        assertTrue(presentation.get().stream().anyMatch(value -> value.contains("NEAREST")));
        assertNoPresentedPath(
                presentation.get(), image, SENSITIVE_PARENT_TOKEN, SENSITIVE_STEM_TOKEN);
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void mainFlowReportsFailuresAndSupportsAnExplicitLaunchBoundary() throws Exception {
        java.util.List<String> failures = new java.util.ArrayList<>();
        assertFalse(RasterViewer.runMain(new String[0], failures::add, ignored -> {}));
        assertEquals(List.of("raster-viewer: IMAGE_VIEWER_ARGUMENT_INVALID"), failures);
        failures.clear();
        Path sensitiveDirectory = temporaryDirectory.resolve(SENSITIVE_PARENT_TOKEN);
        Files.createDirectory(sensitiveDirectory);
        Path missing = sensitiveDirectory.resolve(SENSITIVE_STEM_TOKEN + ".png");
        assertFalse(
                RasterViewer.runMain(
                        new String[] {missing.toString()}, failures::add, ignored -> {}));
        assertTrue(failures.getLast().startsWith("raster-viewer: ERROR IMAGE_IO_FAILED"));
        assertNoPresentedPath(failures, missing, SENSITIVE_PARENT_TOKEN, SENSITIVE_STEM_TOKEN);

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
        assertEquals("raster-viewer: IMAGE_VIEWER_STARTUP_FAILED", failures.getLast());
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
    void controlsReplaceOnlyViewOwnedInterpolationOpacityAndStatus() throws Exception {
        Path image = temporaryDirectory.resolve("controls.png");
        assertTrue(
                ImageIO.write(
                        new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        image.toFile()));
        RasterSource source = RasterViewer.load(image);
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = RasterViewer.createView(source);
                    var binding = view.layerBindings().getFirst();
                    JLabel status = new JLabel();
                    JPanel controls = RasterViewer.createControls(view, status);
                    @SuppressWarnings("unchecked")
                    JComboBox<RasterInterpolation> interpolation =
                            (JComboBox<RasterInterpolation>) controls.getComponent(1);
                    JSlider opacity = (JSlider) controls.getComponent(3);
                    interpolation.setSelectedItem(RasterInterpolation.BILINEAR);
                    opacity.setValue(40);
                    assertEquals(List.of(binding), view.layerBindings());
                    assertTrue(status.getText().contains("BILINEAR"));
                    assertTrue(status.getText().contains("40%"));
                    assertFalse(source.isClosed());
                    view.close();
                });
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
        assertEquals("raster-viewer: IMAGE_VIEWER_STARTUP_FAILED", failures.getLast());

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

    private Path copyGeographicFixture(Path directory, String baseName) throws Exception {
        byte[] image =
                Base64.getMimeDecoder().decode(resourceBytes("g6-002/geographic-rgba.png.b64"));
        assertFixture(
                image, 79, "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe");
        byte[] world = resourceBytes("g6-002/geographic-rgba.pgw");
        assertFixture(
                world, 22, "2f04215b9625536b036b768d8bffc03c2045debd66cb34dc0361c5398ffdbbd5");
        Path imagePath = directory.resolve(baseName + ".png");
        Files.write(imagePath, image);
        Files.write(directory.resolve(baseName + ".pgw"), world);
        return imagePath;
    }

    private static List<String> presentationStrings(
            RasterSource source, MapView view, JPanel controls, JLabel status) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        var metadata = source.metadata();
        values.add(metadata.identity().id());
        values.add(metadata.identity().displayName());
        values.add(view.layerBindings().getFirst().id());
        values.add(view.layerBindings().getFirst().name());
        values.add(String.valueOf(view.getClientProperty("raster-placement-label")));
        values.add(status.getText());
        appendComponentStrings(controls, values);
        return List.copyOf(values);
    }

    private static void appendComponentStrings(Component component, List<String> values) {
        if (component instanceof JLabel label) {
            values.add(label.getText());
        } else if (component instanceof JComboBox<?> comboBox) {
            values.add(String.valueOf(comboBox.getSelectedItem()));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                appendComponentStrings(child, values);
            }
        }
    }

    private static void assertNoPresentedPath(
            List<String> presentation, Path source, String parentToken, String stemToken) {
        List<String> forbidden =
                List.of(
                        source.toAbsolutePath().toString(),
                        Objects.requireNonNull(source.getFileName(), "source file name").toString(),
                        stemToken,
                        parentToken);
        for (String value : presentation) {
            for (String candidate : forbidden) {
                assertFalse(
                        value.contains(candidate),
                        () -> "presentation exposed '" + candidate + "': " + value);
            }
        }
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
