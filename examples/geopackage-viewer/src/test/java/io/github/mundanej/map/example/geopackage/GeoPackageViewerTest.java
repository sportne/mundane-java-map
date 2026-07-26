package io.github.mundanej.map.example.geopackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.SyntheticRasterSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoPackageViewerTest {
    @Test
    void validatesExactPathAndTableArguments() {
        GeoPackageViewer.Arguments arguments =
                GeoPackageViewer.parseArguments(new String[] {"map.gpkg", "features"});
        assertTrue(arguments.path().isAbsolute());
        assertEquals("features", arguments.table());
        assertThrows(
                IllegalArgumentException.class,
                () -> GeoPackageViewer.parseArguments(new String[] {"map.gpkg"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> GeoPackageViewer.parseArguments(new String[] {"map.gpkg", " "}));
        GeoPackageViewer.TileArguments tiles =
                GeoPackageViewer.parseTileArguments(new String[] {"map.gpkg", "tiles", "3"});
        assertEquals(3, tiles.zoom());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GeoPackageViewer.parseTileArguments(
                                new String[] {"map.gpkg", "tiles", "automatic"}));
    }

    @Test
    void createsFittedOwnedRasterViewForRecognizedTileSource() throws Exception {
        RasterSource source =
                SyntheticRasterSource.open(
                        new SourceIdentity("tile-viewer-test", ""),
                        4,
                        4,
                        new Envelope(0, 0, 4, 4),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857,
                                Optional.of("EPSG:3857"),
                                Optional.empty()));
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(GeoPackageViewer.createRasterView(source)));
        assertEquals(1, view.get().layerBindings().size());
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void createsFittedOwnedViewForRecognizedFeatureSource() throws Exception {
        FeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("viewer-test", ""),
                        List.of(
                                new FeatureRecord(
                                        "1",
                                        "",
                                        new PointGeometry(new Coordinate(2, 3)),
                                        Map.of())),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())),
                        io.github.mundanej.map.api.FeatureSourceLimits.LEVEL_1);
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(GeoPackageViewer.createView(source)));
        assertEquals(1, view.get().layerBindings().size());
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void rejectsViewCreationOffEventThread() {
        FeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("off-edt", ""),
                        List.of(
                                new FeatureRecord(
                                        "1",
                                        "",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of())));
        assertThrows(IllegalStateException.class, () -> GeoPackageViewer.createView(source));
        source.close();
    }

    @Test
    void cliReportsArgumentAndStructuredPathFailures(@TempDir Path directory) {
        List<String> failures = new ArrayList<>();
        assertFalse(GeoPackageViewer.runMain(new String[0], failures::add, ignored -> {}));
        assertTrue(failures.getFirst().startsWith("IllegalArgumentException: Usage:"));

        failures.clear();
        assertFalse(
                GeoPackageViewer.runMain(
                        new String[] {directory.resolve("missing.gpkg").toString(), "features"},
                        failures::add,
                        ignored -> {}));
        assertTrue(failures.getFirst().startsWith("SQLITE_INPUT_INVALID [reason=path]:"));

        failures.clear();
        assertFalse(
                GeoPackageViewer.runTileMain(
                        new String[] {directory.resolve("missing.gpkg").toString(), "tiles", "1"},
                        failures::add,
                        ignored -> {}));
        assertTrue(failures.getFirst().startsWith("SQLITE_INPUT_INVALID [reason=path]:"));
    }

    @Test
    void openRejectsEventDispatchThreadAndUnknownCrsCannotCreateView(@TempDir Path directory)
            throws Exception {
        GeoPackageViewer.Arguments arguments =
                new GeoPackageViewer.Arguments(directory.resolve("missing.gpkg"), "features");
        AtomicReference<RuntimeException> openFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        GeoPackageViewer.open(arguments);
                    } catch (RuntimeException expected) {
                        openFailure.set(expected);
                    }
                });
        assertTrue(openFailure.get() instanceof IllegalStateException);

        FeatureSource unknown =
                InMemoryFeatureSource.open(
                        new SourceIdentity("unknown", ""),
                        List.of(
                                new FeatureRecord(
                                        "1",
                                        "",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of())));
        AtomicReference<RuntimeException> viewFailure = new AtomicReference<>();
        List<String> failures = new ArrayList<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        GeoPackageViewer.createView(unknown);
                    } catch (RuntimeException expected) {
                        viewFailure.set(expected);
                    }
                });
        assertTrue(viewFailure.get() instanceof IllegalArgumentException);
        assertFalse(unknown.isClosed());
        SwingUtilities.invokeAndWait(() -> GeoPackageViewer.launchWindow(unknown, failures::add));
        assertTrue(unknown.isClosed());
        assertEquals(
                List.of("IllegalArgumentException: GeoPackage feature table CRS is not recognized"),
                failures);

        RasterSource unknownRaster =
                SyntheticRasterSource.open(
                        new SourceIdentity("unknown-raster", ""),
                        2,
                        2,
                        Optional.of(new Envelope(0, 0, 2, 2)),
                        Optional.empty(),
                        io.github.mundanej.map.api.RasterSourceLimits.LEVEL_1);
        assertThrows(
                IllegalStateException.class,
                () -> GeoPackageViewer.createRasterView(unknownRaster));
        failures.clear();
        SwingUtilities.invokeAndWait(
                () -> GeoPackageViewer.launchRasterWindow(unknownRaster, failures::add));
        assertTrue(unknownRaster.isClosed());
        assertEquals(
                List.of("IllegalArgumentException: GeoPackage tile table CRS is not recognized"),
                failures);
    }
}
