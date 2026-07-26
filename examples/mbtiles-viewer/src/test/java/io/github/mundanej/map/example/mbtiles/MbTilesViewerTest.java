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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
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

    @Test
    void validDatabaseTransfersOwnershipAndLoadingRejectsTheEventThread(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("valid.mbtiles");
        createMbTiles(path);
        AtomicReference<RasterSource> opened = new AtomicReference<>();
        List<String> failures = new ArrayList<>();
        assertTrue(
                MbTilesViewer.runMain(
                        new String[] {path.toString(), "0"},
                        failures::add,
                        source -> {
                            opened.set(source);
                            source.close();
                        }));
        assertTrue(opened.get().isClosed());
        assertTrue(failures.isEmpty());

        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        MbTilesViewer.open(
                                MbTilesViewer.parseArguments(new String[] {path.toString(), "0"}));
                    } catch (Throwable failure) {
                        threadFailure.set(failure);
                    }
                });
        assertTrue(threadFailure.get() instanceof IllegalStateException);
        assertThrows(
                IllegalArgumentException.class,
                () -> MbTilesViewer.parseArguments(new String[] {path.toString(), "23"}));
    }

    @Test
    void commandEntryPointAndInjectedWindowBoundaryRetainStableOwnership() throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            MbTilesViewer.main(new String[0]);
        } finally {
            System.setErr(original);
        }
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("Usage: mbtiles-viewer"));

        RasterSource accepted = recognized("accepted");
        SwingUtilities.invokeAndWait(
                () ->
                        MbTilesViewer.launchWindow(
                                accepted,
                                ignored -> {
                                    throw new AssertionError("unexpected failure");
                                },
                                view -> {
                                    assertEquals(1, view.layerBindings().size());
                                    view.close();
                                }));
        assertTrue(accepted.isClosed());

        RasterSource rejected = recognized("rejected");
        List<String> failures = new ArrayList<>();
        SwingUtilities.invokeAndWait(
                () ->
                        MbTilesViewer.launchWindow(
                                rejected,
                                failures::add,
                                ignored -> {
                                    throw new IllegalStateException("injected");
                                }));
        assertTrue(rejected.isClosed());
        assertEquals(List.of("IllegalStateException: injected"), failures);
    }

    private static RasterSource recognized(String id) {
        return SyntheticRasterSource.open(
                new SourceIdentity(id, ""),
                2,
                2,
                new Envelope(0, 0, 2, 2),
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty()));
    }

    private static void createMbTiles(Path path) throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(
                ImageIO.write(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", encoded));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE metadata (name TEXT NOT NULL, value TEXT NOT NULL)");
            statement.execute(
                    "CREATE TABLE tiles (zoom_level INTEGER NOT NULL,"
                            + " tile_column INTEGER NOT NULL, tile_row INTEGER NOT NULL,"
                            + " tile_data BLOB NOT NULL)");
            statement.execute(
                    "INSERT INTO metadata VALUES"
                            + " ('name','Viewer test'),('format','png'),"
                            + " ('bounds','-180,-85,180,85'),('minzoom','0'),('maxzoom','0')");
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO tiles VALUES (0,0,0,?)")) {
                insert.setBytes(1, encoded.toByteArray());
                insert.executeUpdate();
            }
        }
    }
}
