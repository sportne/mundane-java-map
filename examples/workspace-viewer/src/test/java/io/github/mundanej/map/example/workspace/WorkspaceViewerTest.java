package io.github.mundanej.map.example.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer;
import io.github.mundanej.map.workspace.WorkspaceException;
import io.github.mundanej.map.workspace.WorkspaceFiles;
import io.github.mundanej.map.workspace.WorkspaceLimits;
import io.github.mundanej.map.workspace.WorkspaceOpenContext;
import io.github.mundanej.map.workspace.WorkspaceOpener;
import io.github.mundanej.map.workspace.WorkspaceSession;
import io.github.mundanej.map.workspace.WorkspaceSourceRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceViewerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void materializedFixtureIsCompleteLocalAndRegular() throws IOException {
        Path root = fixture().getParent();
        List<String> files;
        try (var paths = Files.walk(root)) {
            files =
                    paths.filter(Files::isRegularFile)
                            .map(root::relativize)
                            .map(Path::toString)
                            .map(value -> value.replace('\\', '/'))
                            .sorted()
                            .toList();
        }
        assertEquals(
                List.of(
                        "PROVENANCE.md",
                        "data/image.pgw",
                        "data/image.png",
                        "data/map.cpg",
                        "data/map.dbf",
                        "data/map.prj",
                        "data/map.shp",
                        "data/map.shx",
                        "example.mmap.xml"),
                files);
        for (String relative : files) {
            assertFalse(Files.isSymbolicLink(root.resolve(relative)));
        }
    }

    @Test
    void restoresRealLayerOrderNamesSymbolsRasterStateAndRuntimeViewport() throws Exception {
        WorkspaceSession workspace = WorkspaceViewer.open(fixture());
        OpenedWorkspaceRasterLayer raster =
                (OpenedWorkspaceRasterLayer) workspace.layers().getFirst();
        OpenedWorkspaceFeatureLayer feature =
                (OpenedWorkspaceFeatureLayer) workspace.layers().getLast();
        WorkspaceViewer.ViewerSession viewer =
                onEdt(() -> WorkspaceViewer.restore(workspace, 640, 480));

        assertEquals(List.of("image", "areas"), ids(viewer.view().layerBindings()));
        assertEquals(
                List.of("Reference raster", "Mapped areas"),
                viewer.view().layerBindings().stream().map(MapLayerBinding::name).toList());
        assertEquals(640, viewer.view().viewport().width());
        assertEquals(480, viewer.view().viewport().height());
        assertEquals(40.0, viewer.view().viewport().centerX());
        assertEquals(20.0, viewer.view().viewport().centerY());
        assertEquals(0.1, viewer.view().viewport().worldUnitsPerPixel());
        assertEquals("BILINEAR", raster.definition().interpolation().name());
        assertEquals(0.65, raster.definition().opacity());
        assertEquals("MARKER", feature.marker().role().name());
        assertEquals("LINE", feature.line().role().name());
        assertEquals("FILL", feature.fill().role().name());

        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
        onEdt(
                () -> {
                    Graphics2D graphics = image.createGraphics();
                    try {
                        viewer.view().paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    return null;
                });
        assertTrue(nonWhitePixels(image) > 1_000);
        assertFalse(raster.source().isClosed());
        assertFalse(feature.source().isClosed());

        List<MapLayerBinding> bindings = viewer.view().layerBindings();
        onEdt(
                () -> {
                    viewer.close();
                    return null;
                });
        assertTrue(viewer.isClosed());
        assertTrue(workspace.isClosed());
        assertTrue(raster.source().isClosed());
        assertTrue(feature.source().isClosed());
        assertTrue(bindings.stream().noneMatch(MapLayerBinding::isClosed));
        bindings.forEach(MapLayerBinding::close);
        assertTrue(bindings.stream().allMatch(MapLayerBinding::isClosed));
        onEdt(
                () -> {
                    viewer.close();
                    return null;
                });
    }

    @Test
    void runnableSmokeUsesTheFixtureWithoutOpeningAWindow() {
        List<String> failures = new ArrayList<>();
        assertTrue(
                WorkspaceViewer.runMain(
                        new String[] {fixture().toString()},
                        failures::add,
                        WorkspaceViewer.ViewerSession::close));
        assertTrue(failures.isEmpty());
        assertFalse(WorkspaceViewer.runMain(new String[] {"a", "b"}, failures::add, ignored -> {}));
        assertEquals("workspace-viewer: WORKSPACE_VIEWER_ARGUMENT_INVALID", failures.getLast());
    }

    @Test
    void failedViewRestorationClosesBindingsAndTheWorkspaceSession() throws Exception {
        WorkspaceSession workspace = WorkspaceViewer.open(fixture());
        OpenedWorkspaceRasterLayer raster =
                (OpenedWorkspaceRasterLayer) workspace.layers().getFirst();
        OpenedWorkspaceFeatureLayer feature =
                (OpenedWorkspaceFeatureLayer) workspace.layers().getLast();

        assertThrows(
                IllegalArgumentException.class,
                () -> onEdt(() -> WorkspaceViewer.restore(workspace, 0, 480)));

        assertTrue(workspace.isClosed());
        assertTrue(raster.source().isClosed());
        assertTrue(feature.source().isClosed());
    }

    @Test
    void missingAndUnregisteredFixtureReferencesRemainStructured() throws IOException {
        String document = Files.readString(fixture());
        copyFixture(temporaryDirectory);
        Path tampered = temporaryDirectory.resolve("example.mmap.xml");
        Files.writeString(tampered, document.replace("data/map.shp", "data/missing.shp"));
        WorkspaceException missing =
                assertThrows(WorkspaceException.class, () -> WorkspaceViewer.open(tampered));
        assertEquals("WORKSPACE_RESOURCE_MISSING", missing.problem().code());
        assertEquals("1", missing.problem().context().get("layerIndex"));

        var file = WorkspaceFiles.read(fixture(), WorkspaceLimits.DEFAULT);
        WorkspaceOpenContext empty =
                new WorkspaceOpenContext(
                        CrsRegistry.level1(),
                        WorkspaceSourceRegistry.builder().build(),
                        WorkspaceSymbolCatalogRegistry.builder().build());
        WorkspaceException unregistered =
                assertThrows(
                        WorkspaceException.class,
                        () -> WorkspaceOpener.open(file, empty, CancellationToken.none()));
        assertEquals("WORKSPACE_SOURCE_OPENER_UNREGISTERED", unregistered.problem().code());
        assertEquals("0", unregistered.problem().context().get("layerIndex"));

        Files.writeString(tampered, document.replace("application.default", "application.missing"));
        WorkspaceException catalog =
                assertThrows(WorkspaceException.class, () -> WorkspaceViewer.open(tampered));
        assertEquals("WORKSPACE_SYMBOL_CATALOG_UNREGISTERED", catalog.problem().code());
        assertEquals("1", catalog.problem().context().get("layerIndex"));
    }

    @Test
    void incompatibleRasterCrsRemainsStructuredAndClosesTheFailedSession() throws Exception {
        copyFixture(temporaryDirectory);
        Path tampered = temporaryDirectory.resolve("example.mmap.xml");
        String document = Files.readString(tampered);
        Files.writeString(
                tampered,
                document.replace("display-crs=\"EPSG:3857\"", "display-crs=\"EPSG:4326\""));
        WorkspaceSession workspace = WorkspaceViewer.open(tampered);
        OpenedWorkspaceRasterLayer raster =
                (OpenedWorkspaceRasterLayer) workspace.layers().getFirst();
        OpenedWorkspaceFeatureLayer feature =
                (OpenedWorkspaceFeatureLayer) workspace.layers().getLast();

        CrsException failure =
                assertThrows(
                        CrsException.class,
                        () -> onEdt(() -> WorkspaceViewer.restore(workspace, 640, 480)));

        assertEquals("CRS_RASTER_WARP_UNSUPPORTED", failure.problem().code());
        assertTrue(workspace.isClosed());
        assertTrue(raster.source().isClosed());
        assertTrue(feature.source().isClosed());
        List<String> failures = new ArrayList<>();
        assertFalse(
                WorkspaceViewer.runMain(
                        new String[] {tampered.toString()},
                        failures::add,
                        WorkspaceViewer.ViewerSession::close));
        assertEquals(List.of("workspace-viewer: CRS_RASTER_WARP_UNSUPPORTED"), failures);
    }

    @Test
    void exampleSourceContainsNoNetworkDiscoveryOrOwnershipTransfer() throws IOException {
        Path source =
                Path.of(System.getProperty("workspace.viewer.sources"))
                        .resolve("io/github/mundanej/map/example/workspace/WorkspaceViewer.java");
        String text = Files.readString(source);
        assertFalse(text.contains("java.net"));
        assertFalse(text.contains("ServiceLoader"));
        assertFalse(text.contains("Class.forName"));
        assertFalse(text.contains("ownedFeature"));
        assertFalse(text.contains("ownedRaster"));
        assertTrue(text.contains("borrowedFeature"));
        assertTrue(text.contains("borrowedRaster"));
    }

    private static Path fixture() {
        return Path.of(System.getProperty("workspace.viewer.fixture"));
    }

    private static List<String> ids(List<MapLayerBinding> bindings) {
        return bindings.stream().map(MapLayerBinding::id).toList();
    }

    private static int nonWhitePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != 0xffffffff) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void copyFixture(Path target) throws IOException {
        Path source = fixture().getParent();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path output = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(output);
                } else {
                    Files.copy(path, output);
                }
            }
        }
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> callable) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        result.set(callable.call());
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
        if (failure.get() != null) {
            Throwable thrown = failure.get();
            if (thrown instanceof Exception exception) {
                throw exception;
            }
            throw (Error) thrown;
        }
        return result.get();
    }
}
