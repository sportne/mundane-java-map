package io.github.mundanej.map.example.mbtiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.SyntheticRasterSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MbTilesViewerTest {
    @Test
    void parsesPathAndZoom() {
        MbTilesViewer.Arguments arguments =
                MbTilesViewer.parseArguments(new String[] {"map.mbtiles", "3"});
        assertTrue(arguments.path().isAbsolute());
        assertEquals(3, arguments.zoom());
        assertThrows(
                IllegalArgumentException.class,
                () -> MbTilesViewer.parseArguments(new String[] {"map.mbtiles"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MbTilesViewer.parseArguments(new String[] {"map.mbtiles", "auto"}));
    }

    @Test
    void createsFittedOwnedRasterView() throws Exception {
        RasterSource source =
                SyntheticRasterSource.open(
                        new SourceIdentity("viewer-test", ""),
                        4,
                        4,
                        new Envelope(0, 0, 4, 4),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857,
                                Optional.of("EPSG:3857"),
                                Optional.empty()));
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(MbTilesViewer.createView(source)));
        assertEquals(1, view.get().layerBindings().size());
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void rejectsViewOffEventThreadAndClosesUnknownSourceAfterLaunchFailure() throws Exception {
        RasterSource recognized =
                SyntheticRasterSource.open(
                        new SourceIdentity("off-edt", ""),
                        2,
                        2,
                        new Envelope(0, 0, 2, 2),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857,
                                Optional.of("EPSG:3857"),
                                Optional.empty()));
        assertThrows(IllegalStateException.class, () -> MbTilesViewer.createView(recognized));
        recognized.close();

        RasterSource unknown =
                SyntheticRasterSource.open(
                        new SourceIdentity("unknown", ""),
                        2,
                        2,
                        Optional.of(new Envelope(0, 0, 2, 2)),
                        Optional.empty(),
                        io.github.mundanej.map.api.RasterSourceLimits.LEVEL_1);
        List<String> failures = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> MbTilesViewer.launchWindow(unknown, failures::add));
        assertTrue(unknown.isClosed());
        assertEquals(
                List.of("IllegalArgumentException: MBTiles raster CRS is not recognized"),
                failures);
    }

    @Test
    void reportsUsageAndMissingInput(@TempDir Path directory) {
        List<String> failures = new ArrayList<>();
        assertFalse(MbTilesViewer.runMain(new String[0], failures::add, ignored -> {}));
        assertTrue(failures.getFirst().startsWith("IllegalArgumentException: Usage:"));
        failures.clear();
        assertFalse(
                MbTilesViewer.runMain(
                        new String[] {directory.resolve("missing.mbtiles").toString(), "1"},
                        failures::add,
                        ignored -> {}));
        assertTrue(failures.getFirst().startsWith("SQLITE_INPUT_INVALID [reason=path]:"));
    }
}
