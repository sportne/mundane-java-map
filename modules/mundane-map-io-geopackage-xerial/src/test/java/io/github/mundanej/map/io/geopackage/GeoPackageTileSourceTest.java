package io.github.mundanej.map.io.geopackage;

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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.jdbc4.JDBC4Connection;

class GeoPackageTileSourceTest {
    @TempDir Path temporary;

    @Test
    void catalogsReadsAndRendersSparseMixedTileMatrix() throws Exception {
        Path path = fixture("mixed.gpkg");
        GeoPackageCatalog catalog =
                GeoPackages.inspect(
                        path,
                        new SourceIdentity("catalog", ""),
                        GeoPackageInspectOptions.defaults(),
                        CancellationToken.none());
        assertEquals(1, catalog.tileTables().size());
        assertEquals("tiles", catalog.tileTables().getFirst().tableName());
        assertEquals(java.util.List.of(1), catalog.tileTables().getFirst().zoomLevels());

        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("tiles", ""),
                        "tiles",
                        1,
                        GeoPackageTileOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            assertEquals(512, source.metadata().width());
            assertEquals(512, source.metadata().height());
            assertEquals(
                    "EPSG:3857",
                    source.metadata().crs().orElseThrow().declaredIdentifier().orElseThrow());
            RasterRead read =
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(0, 0, 512, 512),
                                    128,
                                    128,
                                    RasterInterpolation.NEAREST,
                                    java.util.Optional.empty()),
                            CancellationToken.none());
            assertEquals("GEOPACKAGE_TILE_MISSING", read.diagnostics().entries().getFirst().code());
            assertEquals("2", read.diagnostics().entries().getFirst().context().get("count"));
            assertEquals(0xff0000ff, read.pixels().rgbaAt(10, 10));
            int jpegBlue = read.pixels().rgbaAt(100, 10);
            assertTrue(((jpegBlue >>> 8) & 0xff) > 240);
            assertEquals(0, read.pixels().rgbaAt(10, 100));
        }
    }

    @Test
    void decodedCacheIsDisabledByDefaultAndCommitsOnlySuccessfulReads() throws Exception {
        Path path = fixture("cache.gpkg");
        CountingDecoder png = counting(EncodedRasterFormat.PNG);
        CountingDecoder jpeg = counting(EncodedRasterFormat.JPEG);
        EncodedRasterDecoderRegistry registry =
                EncodedRasterDecoderRegistry.builder()
                        .register(EncodedRasterFormat.PNG, png)
                        .register(EncodedRasterFormat.JPEG, jpeg)
                        .build();
        RasterRequest request =
                new RasterRequest(
                        new RasterWindow(0, 0, 256, 256),
                        64,
                        64,
                        RasterInterpolation.NEAREST,
                        java.util.Optional.empty());
        RasterRequest adjacentRequest =
                new RasterRequest(
                        new RasterWindow(256, 0, 256, 256),
                        64,
                        64,
                        RasterInterpolation.NEAREST,
                        java.util.Optional.empty());
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("uncached", ""),
                        "tiles",
                        1,
                        GeoPackageTileOptions.defaults(),
                        registry,
                        CancellationToken.none())) {
            source.read(request, CancellationToken.none());
            source.read(request, CancellationToken.none());
        }
        assertEquals(2, png.decodes());

        CountingDecoder cachedPng = counting(EncodedRasterFormat.PNG);
        CountingDecoder cachedJpeg = counting(EncodedRasterFormat.JPEG);
        GeoPackageTileOptions cached =
                new GeoPackageTileOptions(
                        GeoPackageLimits.DEFAULTS,
                        io.github.mundanej.map.api.RasterSourceLimits.LEVEL_1,
                        GeoPackageTileCachePolicy.bounded(1, 262_144));
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("cached", ""),
                        "tiles",
                        1,
                        cached,
                        EncodedRasterDecoderRegistry.builder()
                                .register(EncodedRasterFormat.PNG, cachedPng)
                                .register(EncodedRasterFormat.JPEG, cachedJpeg)
                                .build(),
                        CancellationToken.none())) {
            source.read(request, CancellationToken.none());
            source.read(request, CancellationToken.none());
            source.read(adjacentRequest, CancellationToken.none());
            source.read(request, CancellationToken.none());
        }
        assertEquals(2, cachedPng.decodes());
        assertEquals(1, cachedJpeg.decodes());

        AtomicBoolean cancelled = new AtomicBoolean();
        CancelAfterDecode cancelPng =
                new CancelAfterDecode(
                        AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow(),
                        cancelled);
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("transactional-cache", ""),
                        "tiles",
                        1,
                        cached,
                        EncodedRasterDecoderRegistry.builder()
                                .register(EncodedRasterFormat.PNG, cancelPng)
                                .register(
                                        EncodedRasterFormat.JPEG,
                                        AwtRasterDecoders.level1()
                                                .find(EncodedRasterFormat.JPEG)
                                                .orElseThrow())
                                .build(),
                        CancellationToken.none())) {
            SourceException failure =
                    assertThrows(SourceException.class, () -> source.read(request, cancelled::get));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            cancelled.set(false);
            cancelPng.disableCancellation();
            source.read(request, cancelled::get);
        }
        assertEquals(2, cancelPng.decodes());
    }

    @Test
    void rejectsWrongTileSizeDuplicateCoordinatesAndBadMatrixMath() throws Exception {
        Path wrongSize = fixture("wrong-size.gpkg");
        updateTile(wrongSize, 1, png(128, 256, Color.RED));
        try (RasterSource source =
                GeoPackages.openTiles(
                        wrongSize,
                        new SourceIdentity("wrong-size", ""),
                        "tiles",
                        1,
                        GeoPackageTileOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            SourceException failure =
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
            assertEquals("GEOPACKAGE_TILE_INVALID", failure.terminal().code());
            assertEquals("size", failure.terminal().context().get("reason"));
        }

        Path duplicate = fixture("duplicate.gpkg");
        execute(
                duplicate,
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) "
                        + "SELECT zoom_level,tile_column,tile_row,tile_data FROM tiles WHERE id=1");
        SourceException duplicateFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.openTiles(
                                        duplicate,
                                        new SourceIdentity("duplicate", ""),
                                        "tiles",
                                        1,
                                        GeoPackageTileOptions.defaults(),
                                        AwtRasterDecoders.level1(),
                                        CancellationToken.none()));
        assertEquals("duplicate", duplicateFailure.terminal().context().get("reason"));

        Path badMatrix = fixture("bad-matrix.gpkg");
        execute(badMatrix, "UPDATE gpkg_tile_matrix SET pixel_x_size=2");
        SourceException matrixFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        badMatrix,
                                        new SourceIdentity("bad-matrix", ""),
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_SCHEMA_INVALID", matrixFailure.terminal().code());
        assertEquals("pixelXSize", matrixFailure.terminal().context().get("field"));
    }

    @Test
    void acceptsNullableOrContainedContentBoundsAndRejectsHostileNumericStorage() throws Exception {
        Path nullable = fixture("nullable-bounds.gpkg");
        execute(nullable, "UPDATE gpkg_contents SET min_x=NULL,min_y=NULL,max_x=NULL,max_y=NULL");
        assertEquals(
                1,
                GeoPackages.inspect(
                                nullable,
                                new SourceIdentity("nullable-bounds", ""),
                                GeoPackageInspectOptions.defaults(),
                                CancellationToken.none())
                        .tileTables()
                        .size());

        Path subset = fixture("subset-bounds.gpkg");
        execute(subset, "UPDATE gpkg_contents SET min_x=64,min_y=64,max_x=448,max_y=448");
        assertEquals(
                1,
                GeoPackages.inspect(
                                subset,
                                new SourceIdentity("subset-bounds", ""),
                                GeoPackageInspectOptions.defaults(),
                                CancellationToken.none())
                        .tileTables()
                        .size());

        Path overflow = fixture("overflow-matrix.gpkg");
        execute(overflow, "UPDATE gpkg_tile_matrix SET matrix_width=4294967296");
        assertSchemaField(overflow, "matrixWidth");

        Path fractional = fixture("fractional-matrix.gpkg");
        execute(fractional, "UPDATE gpkg_tile_matrix SET matrix_width=1.5");
        assertSchemaField(fractional, "matrixWidth");

        Path textual = fixture("textual-bounds.gpkg");
        execute(textual, "UPDATE gpkg_tile_matrix_set SET min_x='zero'");
        assertSchemaField(textual, "minX");

        Path coordinate = fixture("overflow-coordinate.gpkg");
        execute(coordinate, "UPDATE tiles SET tile_column=4294967296 WHERE id=1");
        SourceException coordinateFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.openTiles(
                                        coordinate,
                                        new SourceIdentity("overflow-coordinate", ""),
                                        "tiles",
                                        1,
                                        GeoPackageTileOptions.defaults(),
                                        AwtRasterDecoders.level1(),
                                        CancellationToken.none()));
        assertEquals("x", coordinateFailure.terminal().context().get("field"));
        assertEquals("range", coordinateFailure.terminal().context().get("reason"));

        Path fractionalCoordinate = fixture("fractional-coordinate.gpkg");
        execute(fractionalCoordinate, "UPDATE tiles SET tile_row=0.5 WHERE id=1");
        SourceException fractionalCoordinateFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.openTiles(
                                        fractionalCoordinate,
                                        new SourceIdentity("fractional-coordinate", ""),
                                        "tiles",
                                        1,
                                        GeoPackageTileOptions.defaults(),
                                        AwtRasterDecoders.level1(),
                                        CancellationToken.none()));
        assertEquals("y", fractionalCoordinateFailure.terminal().context().get("field"));
        assertEquals("range", fractionalCoordinateFailure.terminal().context().get("reason"));
    }

    @Test
    void enforcesExactContainerOwnedBudgetAndCleansUpAfterDecoderError() throws Exception {
        Path path = fixture("owned-budget.gpkg");
        RasterRequest missing =
                new RasterRequest(
                        new RasterWindow(0, 256, 256, 256),
                        1,
                        1,
                        RasterInterpolation.NEAREST,
                        java.util.Optional.empty());
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("exact-owned-budget", ""),
                        "tiles",
                        1,
                        optionsWithOwnedBudget(262_148),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            assertEquals(
                    "GEOPACKAGE_TILE_MISSING",
                    source.read(missing, CancellationToken.none())
                            .diagnostics()
                            .entries()
                            .getFirst()
                            .code());
        }
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("exceeded-owned-budget", ""),
                        "tiles",
                        1,
                        optionsWithOwnedBudget(262_147),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(missing, CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("ownedBytes", failure.terminal().context().get("limit"));
            assertEquals("262148", failure.terminal().context().get("requested"));
        }

        RasterRequest present =
                new RasterRequest(
                        new RasterWindow(0, 0, 256, 256),
                        1,
                        1,
                        RasterInterpolation.NEAREST,
                        java.util.Optional.empty());
        long tileBytes = 256L * 256 * Integer.BYTES;
        long exactPresentBudget = 262_148L + 3L * png(256, 256, Color.RED).length + 4L * tileBytes;
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("exact-present-owned-budget", ""),
                        "tiles",
                        1,
                        optionsWithOwnedBudget(exactPresentBudget),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            assertEquals(
                    0xff0000ff,
                    source.read(present, CancellationToken.none()).pixels().rgbaAt(0, 0));
        }
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("exceeded-present-owned-budget", ""),
                        "tiles",
                        1,
                        optionsWithOwnedBudget(exactPresentBudget - 1),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(present, CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("ownedBytes", failure.terminal().context().get("limit"));
            assertEquals(
                    Long.toString(exactPresentBudget),
                    failure.terminal().context().get("requested"));
        }

        ErrorOnceDecoder errorPng =
                new ErrorOnceDecoder(
                        AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow());
        RasterRequest errorRequest =
                new RasterRequest(
                        new RasterWindow(0, 0, 256, 256), 32, 32, java.util.Optional.empty());
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("decoder-error", ""),
                        "tiles",
                        1,
                        GeoPackageTileOptions.defaults(),
                        EncodedRasterDecoderRegistry.builder()
                                .register(EncodedRasterFormat.PNG, errorPng)
                                .register(
                                        EncodedRasterFormat.JPEG,
                                        AwtRasterDecoders.level1()
                                                .find(EncodedRasterFormat.JPEG)
                                                .orElseThrow())
                                .build(),
                        CancellationToken.none())) {
            assertThrows(
                    AssertionError.class,
                    () -> source.read(errorRequest, CancellationToken.none()));
            assertEquals(
                    0xff0000ff,
                    source.read(errorRequest, CancellationToken.none()).pixels().rgbaAt(0, 0));
        }
    }

    @Test
    void rendersSparseTileMatrixThroughMapViewWithoutPixelIdentityAssumptions() throws Exception {
        Path path = fixture("render.gpkg");
        SwingUtilities.invokeAndWait(
                () -> {
                    RasterSource source =
                            GeoPackages.openTiles(
                                    path,
                                    new SourceIdentity("render", ""),
                                    "tiles",
                                    1,
                                    GeoPackageTileOptions.defaults(),
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
                                            "tiles", "GeoPackage tiles", source)));
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
    void opensPinnedIndependentlyGeneratedTileFixture() throws Exception {
        byte[] transport;
        try (InputStream input =
                GeoPackageTileSourceTest.class.getResourceAsStream(
                        "/geopackage-fixtures/independent-tiles.gpkg.gz.b64")) {
            transport =
                    Base64.getMimeDecoder()
                            .decode(java.util.Objects.requireNonNull(input).readAllBytes());
        }
        byte[] bytes;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(transport))) {
            bytes = gzip.readAllBytes();
        }
        assertEquals(9_728, bytes.length);
        assertEquals(
                "c594671796cf80a361de2be38045c12658b359bd2ededc8230649a9882dbe69a",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Path path = temporary.resolve("independent-tiles.gpkg");
        Files.write(path, bytes);

        GeoPackageCatalog catalog =
                GeoPackages.inspect(
                        path,
                        new SourceIdentity("independent", ""),
                        GeoPackageInspectOptions.defaults(),
                        CancellationToken.none());
        assertEquals(java.util.List.of(1), catalog.tileTables().getFirst().zoomLevels());
        try (RasterSource source =
                GeoPackages.openTiles(
                        path,
                        new SourceIdentity("independent", ""),
                        "tiles",
                        1,
                        GeoPackageTileOptions.defaults(),
                        AwtRasterDecoders.level1(),
                        CancellationToken.none())) {
            RasterRead read =
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(0, 0, 512, 256),
                                    64,
                                    32,
                                    java.util.Optional.empty()),
                            CancellationToken.none());
            assertEquals("1", read.diagnostics().entries().getFirst().context().get("count"));
            int green = read.pixels().rgbaAt(10, 10);
            assertTrue(((green >>> 16) & 0xff) > 170);
            assertEquals(0, read.pixels().rgbaAt(50, 10));
        }
    }

    private Path fixture(String name) throws Exception {
        Path path = temporary.resolve(name);
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id=1196444487");
            statement.execute("PRAGMA user_version=10400");
            statement.execute(
                    """
                    CREATE TABLE gpkg_spatial_ref_sys (
                      srs_name TEXT NOT NULL,
                      srs_id INTEGER NOT NULL PRIMARY KEY,
                      organization TEXT NOT NULL,
                      organization_coordsys_id INTEGER NOT NULL,
                      definition TEXT NOT NULL,
                      description TEXT)
                    """);
            statement.execute(
                    """
                    INSERT INTO gpkg_spatial_ref_sys VALUES
                      ('Undefined Cartesian',-1,'NONE',-1,'undefined',''),
                      ('Undefined Geographic',0,'NONE',0,'undefined',''),
                      ('WGS 84',4326,'EPSG',4326,'WGS84',''),
                      ('Web Mercator',3857,'EPSG',3857,'WebMercator','')
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_contents (
                      table_name TEXT NOT NULL PRIMARY KEY,
                      data_type TEXT NOT NULL,
                      identifier TEXT UNIQUE,
                      description TEXT DEFAULT '',
                      last_change DATETIME NOT NULL,
                      min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE,
                      srs_id INTEGER,
                      FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_geometry_columns (
                      table_name TEXT NOT NULL,
                      column_name TEXT NOT NULL,
                      geometry_type_name TEXT NOT NULL,
                      srs_id INTEGER NOT NULL,
                      z TINYINT NOT NULL,
                      m TINYINT NOT NULL,
                      PRIMARY KEY (table_name, column_name),
                      FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                      FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_tile_matrix_set (
                      table_name TEXT NOT NULL PRIMARY KEY,
                      srs_id INTEGER NOT NULL,
                      min_x DOUBLE NOT NULL, min_y DOUBLE NOT NULL,
                      max_x DOUBLE NOT NULL, max_y DOUBLE NOT NULL,
                      FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                      FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_tile_matrix (
                      table_name TEXT NOT NULL,
                      zoom_level INTEGER NOT NULL,
                      matrix_width INTEGER NOT NULL, matrix_height INTEGER NOT NULL,
                      tile_width INTEGER NOT NULL, tile_height INTEGER NOT NULL,
                      pixel_x_size DOUBLE NOT NULL, pixel_y_size DOUBLE NOT NULL,
                      PRIMARY KEY (table_name, zoom_level),
                      FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name))
                    """);
            statement.execute(
                    """
                    CREATE TABLE tiles (
                      id INTEGER PRIMARY KEY,
                      zoom_level INTEGER NOT NULL,
                      tile_column INTEGER NOT NULL,
                      tile_row INTEGER NOT NULL,
                      tile_data BLOB NOT NULL)
                    """);
            statement.execute(
                    """
                    INSERT INTO gpkg_contents VALUES
                      ('tiles','tiles','tiles','',
                       '2026-07-25T00:00:00.000Z',0,0,512,512,3857)
                    """);
            statement.execute("INSERT INTO gpkg_tile_matrix_set VALUES ('tiles',3857,0,0,512,512)");
            statement.execute("INSERT INTO gpkg_tile_matrix VALUES ('tiles',1,2,2,256,256,1,1)");
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO tiles VALUES (?,?,?,?,?)")) {
                insert.setInt(1, 1);
                insert.setInt(2, 1);
                insert.setInt(3, 0);
                insert.setInt(4, 0);
                insert.setBytes(5, png(256, 256, Color.RED));
                insert.executeUpdate();
                insert.setInt(1, 2);
                insert.setInt(2, 1);
                insert.setInt(3, 1);
                insert.setInt(4, 0);
                insert.setBytes(5, jpeg(256, 256, Color.BLUE));
                insert.executeUpdate();
            }
        }
        return path;
    }

    private static void updateTile(Path path, int id, byte[] bytes) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE tiles SET tile_data=? WHERE id=?")) {
            statement.setBytes(1, bytes);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    private static void execute(Path path, String sql) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void assertSchemaField(Path path, String field) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        path,
                                        new SourceIdentity("hostile-numeric", ""),
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_SCHEMA_INVALID", failure.terminal().code());
        assertEquals(field, failure.terminal().context().get("field"));
    }

    private static GeoPackageTileOptions optionsWithOwnedBudget(long maximumOwnedBytes) {
        GeoPackageLimits defaults = GeoPackageLimits.DEFAULTS;
        GeoPackageLimits limits =
                new GeoPackageLimits(
                        defaults.maximumInputBytes(),
                        defaults.maximumSchemaObjects(),
                        defaults.maximumColumns(),
                        defaults.maximumIdentifierCharacters(),
                        defaults.maximumMetadataRows(),
                        defaults.maximumTextValueCharacters(),
                        defaults.maximumTextCharacters(),
                        131_072,
                        defaults.maximumRows(),
                        defaults.maximumVmOpcodes(),
                        maximumOwnedBytes,
                        defaults.maximumZoomLevels(),
                        defaults.maximumZoom(),
                        defaults.maximumMatrixAxis(),
                        defaults.maximumCoordinates(),
                        defaults.maximumParts(),
                        defaults.maximumCacheEntries(),
                        defaults.maximumCacheBytes());
        return new GeoPackageTileOptions(
                limits,
                io.github.mundanej.map.api.RasterSourceLimits.LEVEL_1,
                GeoPackageTileCachePolicy.disabled());
    }

    private static byte[] png(int width, int height, Color color) throws Exception {
        return image(width, height, color, "png", BufferedImage.TYPE_INT_ARGB);
    }

    private static byte[] jpeg(int width, int height, Color color) throws Exception {
        return image(width, height, color, "jpeg", BufferedImage.TYPE_INT_RGB);
    }

    private static byte[] image(int width, int height, Color color, String format, int type)
            throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static CountingDecoder counting(EncodedRasterFormat format) {
        return new CountingDecoder(AwtRasterDecoders.level1().find(format).orElseThrow());
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

    private static final class CancelAfterDecode implements EncodedRasterDecoder {
        private final EncodedRasterDecoder delegate;
        private final AtomicBoolean cancellation;
        private final AtomicInteger decodes = new AtomicInteger();
        private boolean cancel = true;

        private CancelAfterDecode(EncodedRasterDecoder delegate, AtomicBoolean cancellation) {
            this.delegate = delegate;
            this.cancellation = cancellation;
        }

        @Override
        public boolean supportsInterpolation(RasterInterpolation interpolation) {
            return delegate.supportsInterpolation(interpolation);
        }

        @Override
        public RgbaPixelBuffer decode(
                InputStream borrowedInput, EncodedRasterDecodeContext context) {
            decodes.incrementAndGet();
            RgbaPixelBuffer result = delegate.decode(borrowedInput, context);
            if (cancel) {
                cancellation.set(true);
            }
            return result;
        }

        private void disableCancellation() {
            cancel = false;
        }

        private int decodes() {
            return decodes.get();
        }
    }

    private static final class ErrorOnceDecoder implements EncodedRasterDecoder {
        private final EncodedRasterDecoder delegate;
        private boolean fail = true;

        private ErrorOnceDecoder(EncodedRasterDecoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean supportsInterpolation(RasterInterpolation interpolation) {
            return delegate.supportsInterpolation(interpolation);
        }

        @Override
        public RgbaPixelBuffer decode(
                InputStream borrowedInput, EncodedRasterDecodeContext context) {
            if (fail) {
                fail = false;
                throw new AssertionError("synthetic decoder failure");
            }
            return delegate.decode(borrowedInput, context);
        }
    }
}
