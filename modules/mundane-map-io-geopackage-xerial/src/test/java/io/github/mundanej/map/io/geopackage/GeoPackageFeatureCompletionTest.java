package io.github.mundanej.map.io.geopackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.jdbc4.JDBC4Connection;

class GeoPackageFeatureCompletionTest {
    @TempDir Path temporary;

    @Test
    void catalogsAndProjectsCompleteGeometryAndAttributeProfile() throws Exception {
        Path fixture = fixture("complete.gpkg");
        GeoPackageCatalog catalog =
                GeoPackages.inspect(
                        fixture,
                        new SourceIdentity("complete", ""),
                        GeoPackageInspectOptions.defaults(),
                        CancellationToken.none());
        GeoPackageFeatureTable table =
                catalog.featureTables().stream()
                        .filter(value -> value.tableName().equals("features"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(table.crs().definition().isPresent());
        assertEquals("EPSG:3857", table.crs().declaredIdentifier().orElseThrow());
        assertEquals(12, table.attributeSchema().fields().size());
        assertEquals(AttributeType.LOGICAL, table.attributeSchema().fields().get(0).type());
        assertEquals(AttributeType.BINARY, table.attributeSchema().fields().get(8).type());
        assertEquals(AttributeType.DATE, table.attributeSchema().fields().get(9).type());

        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                new SourceIdentity("complete", ""),
                                "features",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            List<FeatureRecord> records = read(cursor);
            assertEquals(
                    List.of(
                            PointGeometry.class,
                            MultiPointGeometry.class,
                            LineStringGeometry.class,
                            MultiLineStringGeometry.class,
                            PolygonGeometry.class,
                            MultiPolygonGeometry.class),
                    records.stream().map(record -> record.geometry().getClass()).toList());
            Map<String, Object> attributes = records.getFirst().attributes();
            assertEquals(true, attributes.get("flag"));
            assertEquals(12L, attributes.get("tiny"));
            assertEquals(32_000L, attributes.get("small"));
            assertEquals(1_000_000L, attributes.get("medium"));
            assertEquals(9_000_000_000L, attributes.get("whole"));
            assertEquals(1.5D, attributes.get("ratio"));
            assertEquals(2.25D, attributes.get("measure"));
            assertEquals("feature-1", attributes.get("label"));
            assertEquals(new AttributeBytes(new byte[] {1, 2, 3}), attributes.get("payload"));
            assertEquals(LocalDate.of(2026, 7, 25), attributes.get("day"));
            assertEquals("2026-07-25T12:34:56.789Z", attributes.get("stamp"));
            assertEquals(AttributeNull.INSTANCE, attributes.get("optional"));
        }

        FeatureQuery projected =
                new FeatureQuery(
                        java.util.Optional.empty(),
                        AttributeSelection.only(List.of("label", "flag", "optional")),
                        java.util.Optional.empty());
        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                new SourceIdentity("projection", ""),
                                "features",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor = source.openCursor(projected, CancellationToken.none())) {
            FeatureRecord first = read(cursor).getFirst();
            assertEquals(
                    List.of("label", "flag", "optional"),
                    new ArrayList<>(first.attributes().keySet()));
        }
    }

    @Test
    void retainsUnknownCrsWithoutHeuristicRecognition() throws Exception {
        Path fixture = fixture("unknown.gpkg");
        try (FeatureSource source =
                GeoPackages.openFeatures(
                        fixture,
                        new SourceIdentity("unknown", ""),
                        "unknown_features",
                        GeoPackageFeatureOptions.defaults(),
                        CancellationToken.none())) {
            assertFalse(source.metadata().crs().orElseThrow().definition().isPresent());
            assertEquals(
                    "GPKG:9999",
                    source.metadata().crs().orElseThrow().declaredIdentifier().orElseThrow());
        }
    }

    @Test
    void reportsUnknownProjectionAndAttributeStorageViolations() throws Exception {
        Path fixture = fixture("invalid-attribute.gpkg");
        try (FeatureSource source =
                GeoPackages.openFeatures(
                        fixture,
                        new SourceIdentity("unknown-projection", ""),
                        "features",
                        GeoPackageFeatureOptions.defaults(),
                        CancellationToken.none())) {
            SourceException unknown =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.openCursor(
                                            new FeatureQuery(
                                                    java.util.Optional.empty(),
                                                    AttributeSelection.only(List.of("absent")),
                                                    java.util.Optional.empty()),
                                            CancellationToken.none()));
            assertEquals("SOURCE_QUERY_ATTRIBUTE_UNKNOWN", unknown.terminal().code());
        }

        execute(fixture, "UPDATE features SET flag='yes' WHERE fid=1");
        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                new SourceIdentity("invalid-attribute", ""),
                                "features",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            SourceException invalid = assertThrows(SourceException.class, cursor::advance);
            assertEquals("GEOPACKAGE_RECORD_INVALID", invalid.terminal().code());
            assertEquals("attribute", invalid.terminal().context().get("field"));
            assertEquals("storageClass", invalid.terminal().context().get("reason"));
            assertEquals(
                    1, invalid.terminal().location().orElseThrow().recordNumber().orElseThrow());
        }
    }

    @Test
    void rendersEverySupportedGeometryFamilyThroughMapView() throws Exception {
        Path fixture = fixture("render-complete.gpkg");
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureSource source =
                            GeoPackages.openFeatures(
                                    fixture,
                                    new SourceIdentity("render-complete", ""),
                                    "features",
                                    GeoPackageFeatureOptions.defaults(),
                                    CancellationToken.none());
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedFeature(
                                            "geopackage",
                                            "GeoPackage features",
                                            source,
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.CIRCLE,
                                                    Rgba.rgb(20, 70, 210),
                                                    8,
                                                    1),
                                            SolidLineSymbol.of(
                                                    new SymbolStroke(
                                                            Rgba.rgb(20, 70, 210),
                                                            new SymbolLength(
                                                                    2, SymbolUnit.SCREEN_PIXEL)),
                                                    1),
                                            SolidFillSymbol.of(new Rgba(40, 120, 220, 140), 1))));
                    view.setSize(360, 260);
                    view.fitToData(20);
                    BufferedImage image = new BufferedImage(360, 260, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    assertTrue(nonWhitePixels(image) > 500);
                    view.close();
                    assertTrue(source.isClosed());
                });
    }

    @Test
    void rejectsOversizedDeclarationsAndInvalidCatalogTimestamp() throws Exception {
        Path oversized = fixture("oversized-declaration.gpkg");
        execute(oversized, "ALTER TABLE unknown_features ADD COLUMN excessive TEXT(2000000)");
        SourceException declaration =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        oversized,
                                        new SourceIdentity("oversized", ""),
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_SCHEMA_INVALID", declaration.terminal().code());
        assertEquals("columns", declaration.terminal().context().get("field"));

        Path timestamp = fixture("invalid-timestamp.gpkg");
        execute(timestamp, "UPDATE gpkg_contents SET last_change='2026-07-25'");
        SourceException invalidTime =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        timestamp,
                                        new SourceIdentity("timestamp", ""),
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_SCHEMA_INVALID", invalidTime.terminal().code());
        assertEquals("lastChange", invalidTime.terminal().context().get("field"));
    }

    @Test
    void rejectsAmbiguousGeometryColumnsAndEnforcesOneCatalogMetadataBudget() throws Exception {
        Path ambiguous = fixture("ambiguous-geometry.gpkg");
        execute(
                ambiguous,
                """
                CREATE TABLE ambiguous_features (
                  fid INTEGER PRIMARY KEY,
                  geom_a BLOB NOT NULL,
                  geom_b BLOB NOT NULL)
                """);
        execute(
                ambiguous,
                """
                INSERT INTO gpkg_contents VALUES
                  ('ambiguous_features','features','ambiguous_features','',
                   '2026-07-25T00:00:00.000Z',NULL,NULL,NULL,NULL,3857)
                """);
        execute(
                ambiguous,
                """
                INSERT INTO gpkg_geometry_columns VALUES
                  ('ambiguous_features','geom_a','POINT',3857,0,0),
                  ('ambiguous_features','geom_b','POINT',3857,0,0)
                """);
        SourceException duplicate =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        ambiguous,
                                        new SourceIdentity("ambiguous", ""),
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_SCHEMA_INVALID", duplicate.terminal().code());
        assertEquals("duplicate", duplicate.terminal().context().get("reason"));

        Path bounded = fixture("aggregate-budget.gpkg");
        GeoPackageLimits defaults = GeoPackageLimits.DEFAULTS;
        GeoPackageLimits limits =
                new GeoPackageLimits(
                        defaults.maximumInputBytes(),
                        defaults.maximumSchemaObjects(),
                        defaults.maximumColumns(),
                        defaults.maximumIdentifierCharacters(),
                        10,
                        defaults.maximumTextValueCharacters(),
                        defaults.maximumTextCharacters(),
                        defaults.maximumBlobBytes(),
                        defaults.maximumRows(),
                        defaults.maximumVmOpcodes(),
                        defaults.maximumOwnedBytes(),
                        defaults.maximumZoomLevels(),
                        defaults.maximumZoom(),
                        defaults.maximumMatrixAxis(),
                        defaults.maximumCoordinates(),
                        defaults.maximumParts(),
                        defaults.maximumCacheEntries(),
                        defaults.maximumCacheBytes());
        SourceException metadata =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        bounded,
                                        new SourceIdentity("aggregate-budget", ""),
                                        new GeoPackageInspectOptions(limits),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", metadata.terminal().code());
        assertEquals("metadataRows", metadata.terminal().context().get("limit"));
    }

    private Path fixture(String filename) throws Exception {
        Path path = temporary.resolve(filename);
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
                      ('Web Mercator',3857,'EPSG',3857,'WebMercator',''),
                      ('Retained local',9999,'LOCAL',7,'LOCAL-CS','')
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_contents (
                      table_name TEXT NOT NULL PRIMARY KEY,
                      data_type TEXT NOT NULL,
                      identifier TEXT UNIQUE,
                      description TEXT DEFAULT '',
                      last_change DATETIME NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
                      min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE,
                      srs_id INTEGER,
                      CONSTRAINT fk_gc_r_srs_id
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
                      CONSTRAINT fk_gc_tn
                        FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                      CONSTRAINT fk_gc_srs
                        FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))
                    """);
            statement.execute(
                    """
                    CREATE TABLE features (
                      fid INTEGER PRIMARY KEY,
                      geom BLOB NOT NULL,
                      flag BOOLEAN NOT NULL,
                      tiny TINYINT NOT NULL,
                      small SMALLINT NOT NULL,
                      medium MEDIUMINT NOT NULL,
                      whole INTEGER NOT NULL,
                      ratio FLOAT NOT NULL,
                      measure DOUBLE NOT NULL,
                      label TEXT(16) NOT NULL,
                      payload BLOB(8) NOT NULL,
                      day DATE NOT NULL,
                      stamp DATETIME NOT NULL,
                      optional TEXT)
                    """);
            statement.execute(
                    "CREATE TABLE unknown_features (fid INTEGER PRIMARY KEY, geom BLOB NOT NULL)");
            statement.execute(
                    """
                    INSERT INTO gpkg_contents VALUES
                      ('features','features','features','',
                       '2026-07-25T00:00:00.000Z',0,0,30,30,3857),
                      ('unknown_features','features','unknown_features','',
                       '2026-07-25T00:00:00.000Z',0,0,1,1,9999)
                    """);
            statement.execute(
                    """
                    INSERT INTO gpkg_geometry_columns VALUES
                      ('features','geom','GEOMETRY',3857,0,0),
                      ('unknown_features','geom','POINT',9999,0,0)
                    """);
            try (PreparedStatement insert =
                    connection.prepareStatement(
                            """
                            INSERT INTO features VALUES
                              (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                            """)) {
                List<byte[]> geometries =
                        List.of(
                                packageGeometry(point(1, 1), 3857),
                                packageGeometry(
                                        collection(4, List.of(point(2, 2), point(3, 3))), 3857),
                                packageGeometry(line(new double[] {4, 4, 6, 6}), 3857),
                                packageGeometry(
                                        collection(
                                                5,
                                                List.of(
                                                        line(new double[] {7, 7, 8, 8}),
                                                        line(new double[] {9, 9, 10, 10}))),
                                        3857),
                                packageGeometry(
                                        polygon(
                                                List.of(
                                                        new double[] {
                                                            12, 12, 18, 12, 18, 18, 12, 18, 12, 12
                                                        },
                                                        new double[] {
                                                            14, 14, 14, 16, 16, 16, 16, 14, 14, 14
                                                        })),
                                        3857),
                                packageGeometry(
                                        collection(
                                                6,
                                                List.of(
                                                        polygon(
                                                                List.of(
                                                                        new double[] {
                                                                            20, 20, 22, 20, 22, 22,
                                                                            20, 22, 20, 20
                                                                        })),
                                                        polygon(
                                                                List.of(
                                                                        new double[] {
                                                                            24, 24, 26, 24, 26, 26,
                                                                            24, 26, 24, 24
                                                                        })))),
                                        3857));
                for (int index = 0; index < geometries.size(); index++) {
                    insert.setLong(1, index + 1L);
                    insert.setBytes(2, geometries.get(index));
                    insert.setInt(3, 1);
                    insert.setInt(4, 12);
                    insert.setInt(5, 32_000);
                    insert.setInt(6, 1_000_000);
                    insert.setLong(7, 9_000_000_000L);
                    insert.setDouble(8, 1.5);
                    insert.setDouble(9, 2.25);
                    insert.setString(10, "feature-" + (index + 1));
                    insert.setBytes(11, new byte[] {1, 2, 3});
                    insert.setString(12, "2026-07-25");
                    insert.setString(13, "2026-07-25T12:34:56.789Z");
                    insert.setNull(14, java.sql.Types.VARCHAR);
                    insert.executeUpdate();
                }
            }
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO unknown_features VALUES (?,?)")) {
                insert.setLong(1, 1);
                insert.setBytes(2, packageGeometry(point(0, 0), 9999));
                insert.executeUpdate();
            }
        }
        return path;
    }

    private static byte[] packageGeometry(byte[] wkb, int srs) {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(srs);
        return concatenate(header.array(), wkb);
    }

    private static byte[] point(double x, double y) {
        return ByteBuffer.allocate(21)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1)
                .putInt(1)
                .putDouble(x)
                .putDouble(y)
                .array();
    }

    private static byte[] line(double[] coordinates) {
        ByteBuffer bytes =
                ByteBuffer.allocate(9 + coordinates.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 1).putInt(2).putInt(coordinates.length / 2);
        for (double coordinate : coordinates) {
            bytes.putDouble(coordinate);
        }
        return bytes.array();
    }

    private static byte[] polygon(List<double[]> rings) {
        int size = 9;
        for (double[] ring : rings) {
            size += 4 + ring.length * 8;
        }
        ByteBuffer bytes = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 1).putInt(3).putInt(rings.size());
        for (double[] ring : rings) {
            bytes.putInt(ring.length / 2);
            for (double coordinate : ring) {
                bytes.putDouble(coordinate);
            }
        }
        return bytes.array();
    }

    private static byte[] collection(int type, List<byte[]> children) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(1);
        output.writeBytes(
                ByteBuffer.allocate(8)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(type)
                        .putInt(children.size())
                        .array());
        children.forEach(output::writeBytes);
        return output.toByteArray();
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(first);
        output.writeBytes(second);
        return output.toByteArray();
    }

    private static List<FeatureRecord> read(FeatureCursor cursor) {
        List<FeatureRecord> records = new ArrayList<>();
        while (cursor.advance()) {
            records.add(cursor.current());
        }
        return records;
    }

    private static void execute(Path path, String sql) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int nonWhitePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }
}
