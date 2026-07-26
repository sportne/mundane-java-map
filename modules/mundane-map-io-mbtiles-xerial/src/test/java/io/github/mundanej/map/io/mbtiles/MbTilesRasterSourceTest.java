package io.github.mundanej.map.io.mbtiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.jdbc4.JDBC4Connection;

class MbTilesRasterSourceTest {
    @TempDir Path temporary;

    @Test
    void inspectsMetadataAndReadsSparseTmsTilesOnSmallestExtent() throws Exception {
        Path path = fixture("basic.mbtiles", "png", false);
        MbTilesMetadata metadata =
                MbTiles.inspect(
                        path,
                        new SourceIdentity("metadata", ""),
                        MbTilesInspectOptions.defaults(),
                        CancellationToken.none());
        assertEquals("Synthetic raster", metadata.name());
        assertEquals(EncodedRasterFormat.PNG, metadata.format());
        assertEquals(java.util.List.of(2), metadata.zoomLevels());
        assertEquals(1, metadata.openingDiagnostics().entries().size());
        assertEquals(
                "MBTILES_METADATA_IGNORED",
                metadata.openingDiagnostics().entries().getFirst().code());

        try (RasterSource source =
                MbTiles.open(
                        path,
                        new SourceIdentity("tiles", ""),
                        2,
                        MbTilesOpenOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            assertEquals(512, source.metadata().width());
            assertEquals(512, source.metadata().height());
            assertEquals(
                    "EPSG:3857",
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            RasterRead read =
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(0, 0, 512, 512),
                                    128,
                                    128,
                                    RasterInterpolation.NEAREST,
                                    java.util.Optional.empty()),
                            CancellationToken.none());
            assertEquals(0xff0000ff, read.pixels().rgbaAt(10, 10));
            assertEquals(0x0000ffff, read.pixels().rgbaAt(100, 100));
            assertEquals(0, read.pixels().rgbaAt(100, 10));
            assertEquals("2", read.diagnostics().entries().getFirst().context().get("count"));
            assertTrue(source.metadata().mapBounds().orElseThrow().width() > 0);
        }
    }

    @Test
    void supportsJpegAndUsesTransactionalBoundedCache() throws Exception {
        assertThrows(
                IllegalArgumentException.class, () -> MbTilesTileCachePolicy.bounded(1, 262_143));
        Path jpegPath = fixture("jpeg.mbtiles", "jpg", true);
        CountingDecoder jpeg =
                new CountingDecoder(
                        AwtRasterDecoders.level1().find(EncodedRasterFormat.JPEG).orElseThrow());
        MbTilesOpenOptions cached =
                new MbTilesOpenOptions(
                        MbTilesLimits.DEFAULTS,
                        io.github.mundanej.map.api.RasterSourceLimits.LEVEL_1,
                        MbTilesTileCachePolicy.bounded(1, 262_144));
        RasterRequest northWest =
                new RasterRequest(
                        new RasterWindow(0, 0, 256, 256), 32, 32, java.util.Optional.empty());
        RasterRequest southEast =
                new RasterRequest(
                        new RasterWindow(256, 256, 256, 256), 32, 32, java.util.Optional.empty());
        try (RasterSource source =
                MbTiles.open(
                        jpegPath,
                        new SourceIdentity("jpeg", ""),
                        2,
                        cached,
                        EncodedRasterDecoderRegistry.builder()
                                .register(EncodedRasterFormat.JPEG, jpeg)
                                .build(),
                        CancellationToken.none())) {
            source.read(northWest, CancellationToken.none());
            source.read(northWest, CancellationToken.none());
            source.read(southEast, CancellationToken.none());
            source.read(northWest, CancellationToken.none());
        }
        assertEquals(3, jpeg.decodes());
    }

    @Test
    void rendersTheRealSparseSourceThroughMapViewWithTolerantEvidence() throws Exception {
        Path path = fixture("render.mbtiles", "png", false);
        SwingUtilities.invokeAndWait(
                () -> {
                    RasterSource source =
                            MbTiles.open(
                                    path,
                                    new SourceIdentity("render", ""),
                                    2,
                                    MbTilesOpenOptions.defaults(),
                                    AwtRasterDecoders.level1(),
                                    CancellationToken.none());
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setLayerBindings(
                            java.util.List.of(
                                    MapLayerBinding.ownedRaster(
                                            "tiles", "MBTiles raster", source)));
                    view.setSize(320, 240);
                    view.fitToData(10);
                    BufferedImage rendered =
                            new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = rendered.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, rendered.getWidth(), rendered.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    long colorful = 0;
                    for (int y = 0; y < rendered.getHeight(); y++) {
                        for (int x = 0; x < rendered.getWidth(); x++) {
                            int rgb = rendered.getRGB(x, y) & 0x00ff_ffff;
                            if (rgb != 0x00ff_ffff && rgb != 0) {
                                colorful++;
                            }
                        }
                    }
                    assertTrue(colorful > 5_000);
                    view.close();
                    assertTrue(source.isClosed());
                });
    }

    @Test
    void rejectsUnsupportedZoomFormatDuplicatesAndViews() throws Exception {
        Path path = fixture("invalid.mbtiles", "png", false);
        SourceException zoom =
                assertThrows(
                        SourceException.class,
                        () ->
                                MbTiles.open(
                                        path,
                                        new SourceIdentity("zoom", ""),
                                        1,
                                        MbTilesOpenOptions.defaults(),
                                        AwtRasterDecoders.level1(),
                                        CancellationToken.none()));
        assertEquals("zoom", zoom.terminal().context().get("construct"));

        Path format = fixture("format.mbtiles", "pbf", false);
        SourceException unsupportedFormat =
                assertThrows(
                        SourceException.class,
                        () ->
                                MbTiles.inspect(
                                        format,
                                        new SourceIdentity("format", ""),
                                        MbTilesInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("format", unsupportedFormat.terminal().context().get("construct"));

        Path duplicate = fixture("duplicate.mbtiles", "png", false);
        execute(
                duplicate,
                "INSERT INTO tiles SELECT zoom_level,tile_column,tile_row,tile_data "
                        + "FROM tiles LIMIT 1");
        SourceException duplicateFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                MbTiles.inspect(
                                        duplicate,
                                        new SourceIdentity("duplicate", ""),
                                        MbTilesInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("duplicate", duplicateFailure.terminal().context().get("reason"));

        Path duplicateUnknown = fixture("duplicate-unknown.mbtiles", "png", false);
        execute(duplicateUnknown, "INSERT INTO metadata VALUES ('unknown-key','also ignored')");
        SourceException duplicateUnknownFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                MbTiles.inspect(
                                        duplicateUnknown,
                                        new SourceIdentity("duplicate-unknown", ""),
                                        MbTilesInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("name", duplicateUnknownFailure.terminal().context().get("field"));
        assertEquals("duplicate", duplicateUnknownFailure.terminal().context().get("reason"));

        Path view = fixture("view.mbtiles", "png", false);
        execute(view, "CREATE VIEW unexpected AS SELECT * FROM tiles");
        SourceException viewFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                MbTiles.inspect(
                                        view,
                                        new SourceIdentity("view", ""),
                                        MbTilesInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("view", viewFailure.terminal().context().get("construct"));

        Path mismatch = fixture("format-mismatch.mbtiles", "png", false);
        replaceFirstTile(mismatch, image(Color.GREEN, true));
        try (RasterSource source =
                MbTiles.open(
                        mismatch,
                        new SourceIdentity("format-mismatch", ""),
                        2,
                        MbTilesOpenOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            SourceException mismatchFailure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            new RasterRequest(
                                                    new RasterWindow(0, 0, 256, 256),
                                                    32,
                                                    32,
                                                    java.util.Optional.empty()),
                                            CancellationToken.none()));
            assertEquals("MBTILES_TILE_INVALID", mismatchFailure.terminal().code());
            assertEquals(
                    "IMAGE_EXPECTED_FORMAT_MISMATCH",
                    mismatchFailure.terminal().context().get("imageCode"));
        }
    }

    @Test
    void preservesAscendingPopulatedZoomsAndValidatesPublicMetadataValues() throws Exception {
        Path path = fixture("multi-zoom.mbtiles", "png", false);
        execute(path, "UPDATE metadata SET value='3' WHERE name='maxzoom'");
        insertTile(path, 3, 0, 0, image(Color.GREEN, false));
        MbTilesMetadata metadata =
                MbTiles.inspect(
                        path,
                        new SourceIdentity("multi-zoom", ""),
                        MbTilesInspectOptions.defaults(),
                        CancellationToken.none());
        assertEquals(java.util.List.of(2, 3), metadata.zoomLevels());

        assertThrows(
                IllegalArgumentException.class,
                () -> copyWithZooms(metadata, java.util.List.of(3, 2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> copyWithZooms(metadata, java.util.List.of(2, 2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> copyWithZooms(metadata, java.util.List.of(2, 23)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MbTilesMetadata(
                                metadata.name(),
                                metadata.format(),
                                metadata.bounds(),
                                metadata.center(),
                                java.util.OptionalInt.of(4),
                                java.util.OptionalInt.of(3),
                                metadata.type(),
                                metadata.revision(),
                                metadata.description(),
                                metadata.attribution(),
                                java.util.List.of(3),
                                metadata.openingDiagnostics()));
    }

    @Test
    void closesPermanentlyAndHonorsOpeningAndReadCancellation() throws Exception {
        Path path = fixture("lifecycle.mbtiles", "png", false);
        SourceException cancellation =
                assertThrows(
                        SourceException.class,
                        () ->
                                MbTiles.inspect(
                                        path,
                                        new SourceIdentity("cancelled", ""),
                                        MbTilesInspectOptions.defaults(),
                                        () -> true));
        assertEquals("SOURCE_CANCELLED", cancellation.terminal().code());

        RasterSource source =
                MbTiles.open(
                        path,
                        new SourceIdentity("lifecycle", ""),
                        2,
                        MbTilesOpenOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none());
        SourceException readCancellation =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, 1, 1),
                                                1,
                                                1,
                                                java.util.Optional.empty()),
                                        () -> true));
        assertEquals("SOURCE_CANCELLED", readCancellation.terminal().code());
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.read(
                                new RasterRequest(
                                        new RasterWindow(0, 0, 1, 1),
                                        1,
                                        1,
                                        java.util.Optional.empty()),
                                CancellationToken.none()));
    }

    @Test
    void detectsFileMutationBeforePublishingARead() throws Exception {
        Path path = fixture("mutation.mbtiles", "png", false);
        try (RasterSource source =
                MbTiles.open(
                        path,
                        new SourceIdentity("mutation", ""),
                        2,
                        MbTilesOpenOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            FileTime original = Files.getLastModifiedTime(path);
            Files.setLastModifiedTime(path, FileTime.fromMillis(original.toMillis() + 10_000));
            SourceException changed =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            new RasterRequest(
                                                    new RasterWindow(0, 0, 1, 1),
                                                    1,
                                                    1,
                                                    java.util.Optional.empty()),
                                            CancellationToken.none()));
            assertEquals("SQLITE_INPUT_CHANGED", changed.terminal().code());
        }
    }

    private Path fixture(String name, String format, boolean jpeg) throws Exception {
        Path path = temporary.resolve(name);
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE metadata (name TEXT NOT NULL, value TEXT NOT NULL)");
            statement.execute(
                    """
                    CREATE TABLE tiles (
                      zoom_level INTEGER NOT NULL,
                      tile_column INTEGER NOT NULL,
                      tile_row INTEGER NOT NULL,
                      tile_data BLOB NOT NULL)
                    """);
            try (PreparedStatement metadata =
                    connection.prepareStatement("INSERT INTO metadata VALUES (?,?)")) {
                String[][] values = {
                    {"name", "Synthetic raster"},
                    {"format", format},
                    {"bounds", "-90,-66.5132604431,90,66.5132604431"},
                    {"center", "0,0,2"},
                    {"minzoom", "2"},
                    {"maxzoom", "2"},
                    {"type", "baselayer"},
                    {"version", "1"},
                    {"description", "Synthetic fixture"},
                    {"attribution", "Public domain synthetic"},
                    {"unknown-key", "ignored"}
                };
                for (String[] value : values) {
                    metadata.setString(1, value[0]);
                    metadata.setString(2, value[1]);
                    metadata.addBatch();
                }
                metadata.executeBatch();
            }
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO tiles VALUES (?,?,?,?)")) {
                insert.setInt(1, 2);
                insert.setInt(2, 1);
                insert.setInt(3, 2);
                insert.setBytes(4, image(Color.RED, jpeg));
                insert.executeUpdate();
                insert.setInt(1, 2);
                insert.setInt(2, 2);
                insert.setInt(3, 1);
                insert.setBytes(4, image(Color.BLUE, jpeg));
                insert.executeUpdate();
            }
        }
        return path;
    }

    private static byte[] image(Color color, boolean jpeg) throws Exception {
        int type = jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(256, 256, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 256, 256);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, jpeg ? "jpeg" : "png", output));
        return output.toByteArray();
    }

    private static void execute(Path path, String sql) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void replaceFirstTile(Path path, byte[] tile) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                PreparedStatement statement =
                        connection.prepareStatement(
                                "UPDATE tiles SET tile_data=? WHERE rowid=(SELECT MIN(rowid) FROM tiles)")) {
            statement.setBytes(1, tile);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertTile(Path path, int zoom, int x, int tmsY, byte[] tile)
            throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO tiles VALUES (?,?,?,?)")) {
            statement.setInt(1, zoom);
            statement.setInt(2, x);
            statement.setInt(3, tmsY);
            statement.setBytes(4, tile);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static MbTilesMetadata copyWithZooms(
            MbTilesMetadata metadata, java.util.List<Integer> zooms) {
        return new MbTilesMetadata(
                metadata.name(),
                metadata.format(),
                metadata.bounds(),
                metadata.center(),
                metadata.minimumZoom(),
                metadata.maximumZoom(),
                metadata.type(),
                metadata.revision(),
                metadata.description(),
                metadata.attribution(),
                zooms,
                metadata.openingDiagnostics());
    }

    private static final class CountingDecoder implements EncodedRasterDecoder {
        private final EncodedRasterDecoder delegate;
        private final AtomicInteger decodes = new AtomicInteger();

        private CountingDecoder(EncodedRasterDecoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean supportsInterpolation(RasterInterpolation interpolation) {
            return delegate.supportsInterpolation(interpolation);
        }

        @Override
        public RgbaPixelBuffer decode(
                InputStream borrowedInput, EncodedRasterDecodeContext context) {
            decodes.incrementAndGet();
            return delegate.decode(borrowedInput, context);
        }

        private int decodes() {
            return decodes.get();
        }
    }
}
