package io.github.mundanej.map.io.geopackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
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
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.jdbc4.JDBC4Connection;

class GeoPackagesTest {
    @TempDir Path temporary;

    @Test
    void catalogsAndQueriesPointAndMultiPointInPrimaryKeyOrder() throws Exception {
        Path fixture = fixture("strict.gpkg");
        SourceIdentity identity = new SourceIdentity("strict", "");

        GeoPackageCatalog catalog =
                GeoPackages.inspect(
                        fixture,
                        identity,
                        GeoPackageInspectOptions.defaults(),
                        CancellationToken.none());

        assertEquals(List.of("multipoints", "points"), names(catalog));
        assertTrue(
                catalog.featureTables().stream()
                        .allMatch(table -> table.crs().definition().isPresent()));

        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                identity,
                                "points",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            List<FeatureRecord> records = read(cursor);
            assertEquals(List.of("2", "10"), records.stream().map(FeatureRecord::id).toList());
            assertInstanceOf(PointGeometry.class, records.getFirst().geometry());
            assertEquals(2.0, ((PointGeometry) records.getFirst().geometry()).coordinate().x());
        }

        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                identity,
                                "multipoints",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(
                                new FeatureQuery(
                                        java.util.Optional.of(new Envelope(0, 0, 5, 5)),
                                        io.github.mundanej.map.api.AttributeSelection.NONE,
                                        java.util.Optional.empty()),
                                CancellationToken.none())) {
            FeatureRecord record = read(cursor).getFirst();
            MultiPointGeometry geometry = (MultiPointGeometry) record.geometry();
            assertEquals(2, geometry.coordinates().size());
        }
    }

    @Test
    void reportsCancellationAndSidecarsWithoutLeakingPath() throws Exception {
        Path fixture = fixture("guarded.gpkg");
        SourceIdentity identity = new SourceIdentity("guarded", "");
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        fixture,
                                        identity,
                                        GeoPackageInspectOptions.defaults(),
                                        () -> true));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());

        Files.writeString(fixture.resolveSibling("guarded.gpkg-wal"), "not a journal");
        SourceException sidecar =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        fixture,
                                        identity,
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("SQLITE_INPUT_INVALID", sidecar.terminal().code());
        assertEquals("sidecar", sidecar.terminal().context().get("reason"));
        assertTrue(!sidecar.getMessage().contains(temporary.toString()));
    }

    @Test
    void validatesArgumentsThenCancellationBeforeFileOrPlatformWork() {
        Path missing = temporary.resolve("missing.gpkg");
        SourceIdentity identity = new SourceIdentity("precedence", "");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GeoPackages.openFeatures(
                                missing,
                                identity,
                                " ",
                                GeoPackageFeatureOptions.defaults(),
                                () -> true));
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.openFeatures(
                                        missing,
                                        identity,
                                        "points",
                                        GeoPackageFeatureOptions.defaults(),
                                        () -> true));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
    }

    @Test
    void rejectsWrongVersionBeforeOpeningNativeSession() throws Exception {
        Path fixture = fixture("version.gpkg");
        try (var channel =
                java.nio.channels.FileChannel.open(
                        fixture, java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(
                    ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(10_300).flip(), 60);
        }
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackages.inspect(
                                        fixture,
                                        new SourceIdentity("version", ""),
                                        GeoPackageInspectOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_PROFILE_UNSUPPORTED", failure.terminal().code());
        assertEquals("version", failure.terminal().context().get("construct"));
    }

    @Test
    void rejectsInvalidHeaderBeforeNativeConnection() throws Exception {
        Path fixture = fixture("header.gpkg");
        try (var channel =
                java.nio.channels.FileChannel.open(
                        fixture, java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] {'X'}), 3);
        }
        assertFailure(
                "SQLITE_INPUT_INVALID",
                "reason",
                "header",
                () -> inspect(fixture, GeoPackageInspectOptions.defaults()));
    }

    @Test
    void translatesNativeLoadAndTemporaryDirectoryFailuresInCleanJvms() throws Exception {
        Path fixture = fixture("deployment.gpkg");
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|nativeLoad", runDeploymentProbe(fixture, false, null));

        Path unusableTemporary = temporary.resolve("not-a-directory");
        Files.writeString(unusableTemporary, "occupied");
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|temporaryDirectory",
                runDeploymentProbe(fixture, true, unusableTemporary));
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|unsupportedPlatform",
                runDeploymentProbe(fixture, true, temporary, "Not Linux", "x86_64"));
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|unsupportedPlatform",
                runDeploymentProbe(fixture, true, temporary, "Linux", "not-x86"));
        assertEquals("SUCCESS", runDeploymentProbe(fixture, true, temporary));
    }

    @Test
    void readsBackFixedSessionPolicy() throws Exception {
        Path fixture = fixture("policy.gpkg");
        GeoPackageLimits limits = GeoPackageLimits.DEFAULTS;
        GeoPackageFile.Fingerprint fingerprint =
                GeoPackageFile.preflight("policy", fixture, limits, CancellationToken.none());
        try (GeoPackageSession session =
                GeoPackageSession.open("policy", fingerprint, limits, CancellationToken.none())) {
            assertPragma(session, "query_only", "1");
            assertPragma(session, "trusted_schema", "0");
            assertPragma(session, "foreign_keys", "1");
            assertPragma(session, "cell_size_check", "1");
            assertPragma(session, "temp_store", "2");
            assertPragma(session, "mmap_size", "0");
            assertPragma(session, "automatic_index", "0");
            assertPragma(session, "cache_size", "-8192");
        }
    }

    @Test
    void translatesProgressCancellationAndVmBudget() throws Exception {
        Path fixture = fixture("progress.gpkg");
        GeoPackageLimits limited = GeoPackageLimits.DEFAULTS.withMaximumVmOpcodes(50_000);
        GeoPackageFile.Fingerprint fingerprint =
                GeoPackageFile.preflight("progress", fixture, limited, CancellationToken.none());
        try (GeoPackageSession session =
                GeoPackageSession.open(
                        "progress", fingerprint, limited, CancellationToken.none())) {
            session.beforeOperation(CancellationToken.none(), "inspect");
            SourceException limit =
                    assertThrows(SourceException.class, () -> executeExpensiveQuery(session));
            assertEquals("SOURCE_LIMIT_EXCEEDED", limit.terminal().code());
            assertEquals("vmOpcodes", limit.terminal().context().get("limit"));
            session.suppressOperationCleanup(limit, CancellationToken.none(), "inspect");
        }

        fingerprint =
                GeoPackageFile.preflight(
                        "cancel-progress",
                        fixture,
                        GeoPackageLimits.DEFAULTS,
                        CancellationToken.none());
        AtomicBoolean cancelled = new AtomicBoolean();
        try (GeoPackageSession session =
                GeoPackageSession.open(
                        "cancel-progress",
                        fingerprint,
                        GeoPackageLimits.DEFAULTS,
                        cancelled::get)) {
            session.beforeOperation(cancelled::get, "inspect");
            cancelled.set(true);
            SourceException failure =
                    assertThrows(SourceException.class, () -> executeExpensiveQuery(session));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            session.suppressOperationCleanup(failure, cancelled::get, "inspect");
        }
    }

    @Test
    void rejectsStrictSchemaCrsAndObjectInventoryViolations() throws Exception {
        Path extra = fixture("extra.gpkg");
        executeWrite(extra, "CREATE TABLE unlisted(value TEXT)");
        assertFailure(
                "GEOPACKAGE_PROFILE_UNSUPPORTED",
                "construct",
                "contentType",
                () -> inspect(extra, GeoPackageInspectOptions.defaults()));

        Path crs = fixture("crs.gpkg");
        executeWrite(crs, "UPDATE gpkg_spatial_ref_sys SET organization='OTHER' WHERE srs_id=4326");
        assertFailure(
                "GEOPACKAGE_SCHEMA_INVALID",
                "field",
                "organizationCode",
                () -> inspect(crs, GeoPackageInspectOptions.defaults()));

        Path attributes = fixture("attributes.gpkg");
        executeWrite(attributes, "ALTER TABLE points ADD COLUMN name VARCHAR");
        assertFailure(
                "GEOPACKAGE_SCHEMA_INVALID",
                "field",
                "columns",
                () -> inspect(attributes, GeoPackageInspectOptions.defaults()));
    }

    @Test
    void enforcesLimitsAndRejectsMalformedGeometry() throws Exception {
        Path fixture = fixture("limits.gpkg");
        GeoPackageLimits limits = GeoPackageLimits.DEFAULTS.withMaximumSchemaObjects(3);
        assertFailure(
                "SOURCE_LIMIT_EXCEEDED",
                "limit",
                "schemaObjects",
                () -> inspect(fixture, new GeoPackageInspectOptions(limits)));

        executeWrite(fixture, "UPDATE points SET geom=x'47500001' WHERE fid=2");
        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                new SourceIdentity("malformed", ""),
                                "points",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            SourceException malformed = assertThrows(SourceException.class, cursor::advance);
            assertEquals("GEOPACKAGE_RECORD_INVALID", malformed.terminal().code());
            assertEquals(
                    1, malformed.terminal().location().orElseThrow().recordNumber().orElseThrow());
            assertTrue(cursor.isClosed());
        }
    }

    @Test
    void validatesOptionalXyEnvelopeBeforePublishingGeometry() {
        var decoded =
                GeoPackageGeometryDecoder.decode(
                        "envelope",
                        pointWithEnvelope(2, 3, 1, 4, 2, 5),
                        4326,
                        GeoPackageGeometryType.POINT,
                        GeoPackageLimits.DEFAULTS,
                        CancellationToken.none());
        assertEquals(
                new PointGeometry(new io.github.mundanej.map.api.Coordinate(2, 3)),
                decoded.geometry());

        SourceException outside =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "envelope",
                                        pointWithEnvelope(2, 3, 3, 4, 2, 5),
                                        4326,
                                        GeoPackageGeometryType.POINT,
                                        GeoPackageLimits.DEFAULTS,
                                        CancellationToken.none()));
        assertEquals("GEOPACKAGE_RECORD_INVALID", outside.terminal().code());
    }

    @Test
    void pollsCancellationWhileDecodingLargeMultiPoint() throws Exception {
        double[] coordinates = new double[10_000];
        for (int index = 0; index < coordinates.length; index++) {
            coordinates[index] = index;
        }
        AtomicInteger polls = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoPackageGeometryDecoder.decode(
                                        "cancel-decode",
                                        multiPoint(coordinates),
                                        4326,
                                        GeoPackageGeometryType.MULTI_POINT,
                                        GeoPackageLimits.DEFAULTS,
                                        () -> polls.incrementAndGet() > 1));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertTrue(polls.get() >= 2);
    }

    @Test
    void detectsMutationBeforeRecordPublicationAndClosesPermanently() throws Exception {
        Path fixture = fixture("mutation.gpkg");
        FeatureSource source =
                GeoPackages.openFeatures(
                        fixture,
                        new SourceIdentity("mutation", ""),
                        "points",
                        GeoPackageFeatureOptions.defaults(),
                        CancellationToken.none());
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        FileTime original = Files.getLastModifiedTime(fixture);
        Files.setLastModifiedTime(fixture, FileTime.fromMillis(original.toMillis() + 10_000));
        SourceException changed = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SQLITE_INPUT_CHANGED", changed.terminal().code());
        assertTrue(cursor.isClosed());
        Files.setLastModifiedTime(fixture, original);
        SourceException stillChanged =
                assertThrows(
                        SourceException.class,
                        () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        assertEquals("SQLITE_INPUT_CHANGED", stillChanged.terminal().code());
        source.close();
        assertTrue(source.isClosed());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
    }

    @Test
    void treatsPostOpenSidecarAsPermanentInputChange() throws Exception {
        Path fixture = fixture("sidecar-change.gpkg");
        FeatureSource source =
                GeoPackages.openFeatures(
                        fixture,
                        new SourceIdentity("sidecar-change", ""),
                        "points",
                        GeoPackageFeatureOptions.defaults(),
                        CancellationToken.none());
        Path sidecar = fixture.resolveSibling("sidecar-change.gpkg-wal");
        Files.writeString(sidecar, "appeared");
        SourceException changed =
                assertThrows(
                        SourceException.class,
                        () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        assertEquals("SQLITE_INPUT_CHANGED", changed.terminal().code());
        assertEquals("cursor", changed.terminal().context().get("phase"));
        Files.delete(sidecar);
        assertThrows(
                SourceException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        source.close();
    }

    @Test
    void skipsStandardEmptyGeometryWithBoundedWarning() throws Exception {
        Path fixture = fixture("empty.gpkg");
        executeBlobInsert(fixture, 20, emptyPoint());
        try (FeatureSource source =
                        GeoPackages.openFeatures(
                                fixture,
                                new SourceIdentity("empty", ""),
                                "points",
                                GeoPackageFeatureOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertEquals(2, read(cursor).size());
            assertEquals(
                    "GEOPACKAGE_GEOMETRY_EMPTY", cursor.diagnostics().entries().getFirst().code());
            assertEquals(
                    3,
                    cursor.diagnostics()
                            .entries()
                            .getFirst()
                            .location()
                            .orElseThrow()
                            .recordNumber()
                            .orElseThrow());
        }
    }

    @Test
    void rendersPointFeaturesThroughTheG4SourceStack() throws Exception {
        Path fixture = fixture("render.gpkg");
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureSource source =
                            GeoPackages.openFeatures(
                                    fixture,
                                    new SourceIdentity("render", ""),
                                    "points",
                                    GeoPackageFeatureOptions.defaults(),
                                    CancellationToken.none());
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    view.setSize(120, 100);
                    view.setViewport(new MapViewport(120, 100, 6, 6, 0.2));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedFeature(
                                            "geopackage",
                                            "GeoPackage",
                                            source,
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.CIRCLE,
                                                    Rgba.rgb(20, 70, 210),
                                                    9,
                                                    1),
                                            SolidLineSymbol.of(
                                                    new SymbolStroke(
                                                            Rgba.rgb(20, 70, 210),
                                                            new SymbolLength(
                                                                    1, SymbolUnit.SCREEN_PIXEL)),
                                                    1),
                                            SolidFillSymbol.of(Rgba.rgb(20, 70, 210), 1))));
                    BufferedImage image = new BufferedImage(120, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    assertTrue(countNonWhite(image) > 50);
                    view.close();
                    assertTrue(source.isClosed());
                });
    }

    private Path fixture(String filename) throws Exception {
        Path path = temporary.resolve(filename);
        JDBC4Connection connection =
                new JDBC4Connection("jdbc:sqlite:" + path, path.toString(), new Properties());
        try (connection;
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
                      ('WGS 84',4326,'EPSG',4326,'WGS84','')
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
            statement.execute("CREATE TABLE points (fid INTEGER PRIMARY KEY, geom BLOB NOT NULL)");
            statement.execute(
                    "CREATE TABLE multipoints (fid INTEGER PRIMARY KEY, geom BLOB NOT NULL)");
            statement.execute(
                    """
                    INSERT INTO gpkg_contents VALUES
                      ('points','features','points','',strftime('%Y-%m-%dT%H:%M:%fZ','now'),0,0,20,20,4326),
                      ('multipoints','features','multipoints','',strftime('%Y-%m-%dT%H:%M:%fZ','now'),0,0,5,5,4326)
                    """);
            statement.execute(
                    """
                    INSERT INTO gpkg_geometry_columns VALUES
                      ('points','geom','POINT',4326,0,0),
                      ('multipoints','geom','MULTIPOINT',4326,0,0)
                    """);
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO points VALUES (?,?)")) {
                insert.setLong(1, 10);
                insert.setBytes(2, point(10, 10, ByteOrder.LITTLE_ENDIAN));
                insert.executeUpdate();
                insert.setLong(1, 2);
                insert.setBytes(2, point(2, 3, ByteOrder.BIG_ENDIAN));
                insert.executeUpdate();
            }
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO multipoints VALUES (?,?)")) {
                insert.setLong(1, 1);
                insert.setBytes(2, multiPoint(new double[] {1, 1, 4, 4}));
                insert.executeUpdate();
            }
        }
        return path;
    }

    private static byte[] point(double x, double y, ByteOrder order) {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 1 + 4 + 16);
        bytes.put((byte) 'G').put((byte) 'P').put((byte) 0);
        bytes.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 1 : 0));
        bytes.order(order).putInt(4326);
        bytes.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 1 : 0));
        bytes.order(order).putInt(1).putDouble(x).putDouble(y);
        return bytes.array();
    }

    private static byte[] multiPoint(double[] coordinates) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[] {'G', 'P', 0, 1});
        output.write(littleInt(4326));
        output.write(1);
        output.write(littleInt(4));
        output.write(littleInt(coordinates.length / 2));
        for (int index = 0; index < coordinates.length; index += 2) {
            output.write(1);
            output.write(littleInt(1));
            output.write(littleDouble(coordinates[index]));
            output.write(littleDouble(coordinates[index + 1]));
        }
        return output.toByteArray();
    }

    private static byte[] emptyPoint() {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 1 + 4 + 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 0x11);
        bytes.putInt(4326);
        bytes.put((byte) 1).putInt(1).putDouble(Double.NaN).putDouble(Double.NaN);
        return bytes.array();
    }

    private static byte[] pointWithEnvelope(
            double x,
            double y,
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY) {
        ByteBuffer bytes = ByteBuffer.allocate(40 + 1 + 4 + 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 3);
        bytes.putInt(4326);
        bytes.putDouble(minimumX).putDouble(maximumX).putDouble(minimumY).putDouble(maximumY);
        bytes.put((byte) 1).putInt(1).putDouble(x).putDouble(y);
        return bytes.array();
    }

    private static byte[] littleInt(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static byte[] littleDouble(double value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array();
    }

    private static List<FeatureRecord> read(FeatureCursor cursor) {
        List<FeatureRecord> values = new ArrayList<>();
        while (cursor.advance()) {
            values.add(cursor.current());
        }
        return values;
    }

    private static List<String> names(GeoPackageCatalog catalog) {
        return catalog.featureTables().stream().map(GeoPackageFeatureTable::tableName).toList();
    }

    private static int countNonWhite(BufferedImage image) {
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

    private static String runDeploymentProbe(
            Path fixture, boolean includeNative, Path temporaryDirectory) throws Exception {
        return runDeploymentProbe(fixture, includeNative, temporaryDirectory, null, null);
    }

    private static String runDeploymentProbe(
            Path fixture,
            boolean includeNative,
            Path temporaryDirectory,
            String osName,
            String architecture)
            throws Exception {
        List<Path> entries = new ArrayList<>();
        entries.add(codeSource(GeoPackageDeploymentProbe.class));
        entries.add(codeSource(GeoPackages.class));
        entries.add(codeSource(SourceIdentity.class));
        entries.add(codeSource(CrsRegistry.class));
        entries.add(codeSource(MapView.class));
        entries.add(codeSource(JDBC4Connection.class));
        if (includeNative) {
            var resource =
                    java.util.Objects.requireNonNull(
                            GeoPackagesTest.class
                                    .getClassLoader()
                                    .getResource("org/sqlite/native/Linux/x86_64/libsqlitejdbc.so"),
                            "Xerial native test resource");
            var connection = resource.openConnection();
            if (!(connection instanceof java.net.JarURLConnection jar)) {
                throw new IllegalStateException("Xerial native resource is not in a JAR");
            }
            entries.add(Path.of(jar.getJarFileURL().toURI()));
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Djava.awt.headless=true");
        if (osName != null) {
            command.add("-Dos.name=" + osName);
        }
        if (architecture != null) {
            command.add("-Dos.arch=" + architecture);
        }
        if (temporaryDirectory != null) {
            command.add("-Dorg.sqlite.tmpdir=" + temporaryDirectory);
        }
        command.add("-cp");
        command.add(
                entries.stream()
                        .distinct()
                        .map(Path::toString)
                        .collect(Collectors.joining(java.io.File.pathSeparator)));
        command.add(GeoPackageDeploymentProbe.class.getName());
        command.add(fixture.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output =
                new String(
                                process.getInputStream().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8)
                        .trim();
        assertEquals(0, process.waitFor(), output);
        return output.lines().reduce((first, second) -> second).orElse("");
    }

    private static Path codeSource(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static void assertPragma(GeoPackageSession session, String name, String expected)
            throws SQLException {
        try (Statement statement = session.connection().createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(result.next());
            assertEquals(expected, result.getString(1));
            assertTrue(!result.next());
        }
    }

    private static void executeExpensiveQuery(GeoPackageSession session) throws SQLException {
        try (Statement statement = session.connection().createStatement()) {
            try {
                statement
                        .executeQuery(
                                """
                                WITH RECURSIVE values_(value) AS (
                                  VALUES(1) UNION ALL SELECT value+1 FROM values_ WHERE value<1000000
                                ) SELECT sum(value) FROM values_
                                """)
                        .close();
            } catch (SQLException exception) {
                throw session.queryFailure(exception, "catalog");
            }
        }
    }

    private static void executeWrite(Path path, String sql) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeBlobInsert(Path path, long id, byte[] bytes) throws Exception {
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO points(fid,geom) VALUES (?,?)")) {
            statement.setLong(1, id);
            statement.setBytes(2, bytes);
            statement.executeUpdate();
        }
    }

    private static GeoPackageCatalog inspect(Path path, GeoPackageInspectOptions options) {
        return GeoPackages.inspect(
                path, new SourceIdentity("test", ""), options, CancellationToken.none());
    }

    private static void assertFailure(
            String code,
            String contextKey,
            String contextValue,
            org.junit.jupiter.api.function.Executable operation) {
        SourceException failure = assertThrows(SourceException.class, operation);
        assertEquals(code, failure.terminal().code());
        assertEquals(contextValue, failure.terminal().context().get(contextKey));
    }
}
