package io.github.mundanej.map.io.mbtiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.image.ImageSourceLimits;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.jdbc4.JDBC4Connection;

class MbTilesHardeningTest {
    @TempDir Path temporary;

    @Test
    void enforcesOperationalLimitsAtExactAndOneOverBoundaries() throws Exception {
        Path baseline = fixture("limits.mbtiles");
        long fileBytes = Files.size(baseline);
        inspect(baseline, limits("inputBytes", fileBytes));
        assertLimit(baseline, limits("inputBytes", fileBytes - 1), "inputBytes");

        Path schema = fixture("schema-limit.mbtiles");
        execute(schema, "CREATE INDEX first_index ON tiles(zoom_level)");
        inspect(schema, limits("schemaObjects", 3));
        execute(schema, "CREATE INDEX second_index ON tiles(tile_column)");
        assertLimit(schema, limits("schemaObjects", 3), "schemaObjects");

        inspect(baseline, limits("columns", 4));
        assertLimit(baseline, limits("columns", 3), "columns");
        inspect(baseline, limits("identifierCharacters", 8));
        assertLimit(baseline, limits("identifierCharacters", 7), "identifierCharacters");
        inspect(baseline, limits("metadataRows", 4));
        assertLimit(baseline, limits("metadataRows", 3), "metadataRows");

        int longestText = "Synthetic hardening".length();
        inspect(baseline, limits("textValueCharacters", longestText));
        assertLimit(
                baseline, limits("textValueCharacters", longestText - 1), "textValueCharacters");
        long aggregateText =
                (long) "name".length()
                        + "Synthetic hardening".length()
                        + "format".length()
                        + "png".length()
                        + "minzoom".length()
                        + "2".length()
                        + "maxzoom".length()
                        + "2".length();
        inspect(baseline, textLimits(longestText, aggregateText));
        assertLimit(baseline, textLimits(longestText, aggregateText - 1), "textCharacters");

        int encodedBytes = png(Color.RED).length;
        MbTilesLimits exactBlob = blobAndTextLimits(encodedBytes, longestText, aggregateText);
        inspect(baseline, exactBlob);
        try (RasterSource source = open(baseline, exactBlob)) {
            source.read(
                    new RasterRequest(
                            new RasterWindow(0, 0, 1, 1), 1, 1, java.util.Optional.empty()),
                    CancellationToken.none());
        }
        assertLimit(baseline, limits("blobBytes", encodedBytes - 1), "blobBytes");
        inspect(baseline, limits("rows", 3));
        assertLimit(baseline, limits("rows", 2), "rows");
        inspect(baseline, limits("zoom", 2));
        assertLimit(baseline, limits("zoom", 1), "zoom");
        inspect(baseline, limits("matrixAxis", 3));
        assertLimit(baseline, limits("matrixAxis", 2), "matrixAxis");

        Path zooms = fixture("zoom-level-limit.mbtiles");
        execute(zooms, "UPDATE metadata SET value='3' WHERE name='maxzoom'");
        insertTile(zooms, 3, 0, 0, png(Color.GREEN));
        inspect(zooms, limits("zoomLevels", 2));
        assertLimit(zooms, limits("zoomLevels", 1), "zoomLevels");

        long exactOwned = 8L + 3L * encodedBytes + 4L * 256L * 256L * Integer.BYTES;
        RasterRequest onePixel =
                new RasterRequest(
                        new RasterWindow(0, 0, 1, 1),
                        1,
                        1,
                        RasterInterpolation.NEAREST,
                        java.util.Optional.empty());
        RasterSourceLimits onePixelLimits =
                new RasterSourceLimits(new RasterRequestLimits(1, 1, 1, 262_148, 4, 1));
        try (RasterSource source =
                open(baseline, limits("ownedBytes", exactOwned), onePixelLimits)) {
            source.read(onePixel, CancellationToken.none());
        }
        try (RasterSource source =
                open(baseline, limits("ownedBytes", exactOwned - 1), onePixelLimits)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.read(onePixel, CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("ownedBytes", failure.terminal().context().get("limit"));
            assertEquals(Long.toString(exactOwned), failure.terminal().context().get("requested"));
        }
    }

    @Test
    void translatesProgressCancellationAndVmBudgetAndReadsBackPolicy() throws Exception {
        Path path = fixture("progress.mbtiles");
        MbTilesLimits limited = limits("vmOpcodes", 50_000);
        MbTilesFile.Fingerprint fingerprint =
                MbTilesFile.preflight("progress", path, limited, CancellationToken.none());
        try (MbTilesSession session =
                MbTilesSession.open("progress", fingerprint, limited, CancellationToken.none())) {
            assertPragma(session, "query_only", "1");
            assertPragma(session, "trusted_schema", "0");
            assertPragma(session, "foreign_keys", "1");
            assertPragma(session, "cell_size_check", "1");
            assertPragma(session, "temp_store", "2");
            assertPragma(session, "mmap_size", "0");
            assertPragma(session, "automatic_index", "0");
            assertPragma(session, "cache_size", "-8192");
            session.beforeOperation(CancellationToken.none(), "inspect");
            SourceException limit =
                    assertThrows(SourceException.class, () -> executeExpensiveQuery(session));
            assertEquals("SOURCE_LIMIT_EXCEEDED", limit.terminal().code());
            assertEquals("vmOpcodes", limit.terminal().context().get("limit"));
            session.suppressOperationCleanup(limit, CancellationToken.none(), "inspect");
        }

        fingerprint =
                MbTilesFile.preflight(
                        "cancel-progress", path, MbTilesLimits.DEFAULTS, CancellationToken.none());
        AtomicBoolean cancelled = new AtomicBoolean();
        try (MbTilesSession session =
                MbTilesSession.open(
                        "cancel-progress", fingerprint, MbTilesLimits.DEFAULTS, cancelled::get)) {
            session.beforeOperation(cancelled::get, "inspect");
            cancelled.set(true);
            SourceException failure =
                    assertThrows(SourceException.class, () -> executeExpensiveQuery(session));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            session.suppressOperationCleanup(failure, cancelled::get, "inspect");
        }
    }

    @Test
    void cacheChangesCommitOnlyAfterSuccessAndDecoderErrorsCleanUp() throws Exception {
        Path path = fixture("cache-hardening.mbtiles");
        AtomicBoolean cancelled = new AtomicBoolean();
        CancelAfterDecode decoder =
                new CancelAfterDecode(
                        AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow(),
                        cancelled);
        MbTilesOpenOptions cached =
                new MbTilesOpenOptions(
                        MbTilesLimits.DEFAULTS,
                        RasterSourceLimits.LEVEL_1,
                        MbTilesTileCachePolicy.bounded(1, 262_144));
        RasterRequest request =
                new RasterRequest(
                        new RasterWindow(0, 0, 256, 256), 32, 32, java.util.Optional.empty());
        try (RasterSource source =
                MbTiles.open(
                        path,
                        new SourceIdentity("transactional-cache", ""),
                        2,
                        cached,
                        EncodedRasterDecoderRegistry.builder()
                                .register(EncodedRasterFormat.PNG, decoder)
                                .build(),
                        CancellationToken.none())) {
            SourceException failure =
                    assertThrows(SourceException.class, () -> source.read(request, cancelled::get));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            cancelled.set(false);
            decoder.disableCancellation();
            source.read(request, cancelled::get);
        }
        assertEquals(2, decoder.decodes());

        ErrorOnceDecoder error =
                new ErrorOnceDecoder(
                        AwtRasterDecoders.level1().find(EncodedRasterFormat.PNG).orElseThrow());
        try (RasterSource source =
                MbTiles.open(
                        path,
                        new SourceIdentity("decoder-error", ""),
                        2,
                        MbTilesOpenOptions.defaults(),
                        EncodedRasterDecoderRegistry.builder()
                                .register(EncodedRasterFormat.PNG, error)
                                .build(),
                        CancellationToken.none())) {
            assertThrows(
                    AssertionError.class, () -> source.read(request, CancellationToken.none()));
            source.read(request, CancellationToken.none());
        }
    }

    @Test
    void detectsMutationAndSidecarsPermanentlyWithoutLeakingThePath() throws Exception {
        Path path = fixture("mutation-canary-secret.mbtiles");
        RasterSource mutationSource = open(path, MbTilesLimits.DEFAULTS);
        FileTime original = Files.getLastModifiedTime(path);
        Files.setLastModifiedTime(path, FileTime.fromMillis(original.toMillis() + 10_000));
        SourceException changed =
                assertThrows(
                        SourceException.class,
                        () ->
                                mutationSource.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, 1, 1),
                                                1,
                                                1,
                                                java.util.Optional.empty()),
                                        CancellationToken.none()));
        assertEquals("SQLITE_INPUT_CHANGED", changed.terminal().code());
        Files.setLastModifiedTime(path, original);
        SourceException permanent =
                assertThrows(
                        SourceException.class,
                        () ->
                                mutationSource.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, 1, 1),
                                                1,
                                                1,
                                                java.util.Optional.empty()),
                                        CancellationToken.none()));
        assertEquals("SQLITE_INPUT_CHANGED", permanent.terminal().code());
        mutationSource.close();

        Path sidecar = fixture("sidecar-change.mbtiles");
        RasterSource sidecarSource = open(sidecar, MbTilesLimits.DEFAULTS);
        Files.writeString(sidecar.resolveSibling(sidecar.getFileName() + "-wal"), "canary");
        SourceException sidecarFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                sidecarSource.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, 1, 1),
                                                1,
                                                1,
                                                java.util.Optional.empty()),
                                        CancellationToken.none()));
        assertEquals("SQLITE_INPUT_CHANGED", sidecarFailure.terminal().code());
        assertFalse(sidecarFailure.getMessage().contains("mutation-canary-secret"));
        sidecarSource.close();
    }

    @Test
    void rejectsTruncatedCorruptAndMalformedInputsWithClosedDiagnostics() throws Exception {
        Path truncated = fixture("truncated.mbtiles");
        try (FileChannel channel = FileChannel.open(truncated, StandardOpenOption.WRITE)) {
            channel.truncate(Files.size(truncated) - 512);
        }
        assertFailure(
                "SQLITE_INPUT_INVALID",
                "reason",
                "pageLayout",
                () -> inspect(truncated, MbTilesLimits.DEFAULTS));

        Path corrupt = fixture("corrupt-secret.mbtiles");
        try (FileChannel channel = FileChannel.open(corrupt, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] {(byte) 0xff}), 100);
        }
        SourceException failure =
                assertThrows(SourceException.class, () -> inspect(corrupt, MbTilesLimits.DEFAULTS));
        assertEquals("SQLITE_OPEN_FAILED", failure.terminal().code());
        assertEquals(java.util.Map.of("phase", "policy"), failure.terminal().context());
        assertFalse(failure.getMessage().contains("corrupt-secret"));

        Path badHeader = fixture("header.mbtiles");
        try (FileChannel channel = FileChannel.open(badHeader, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] {'X'}), 3);
        }
        assertFailure(
                "SQLITE_INPUT_INVALID",
                "reason",
                "header",
                () -> inspect(badHeader, MbTilesLimits.DEFAULTS));

        Path sidecar = fixture("sidecar.mbtiles");
        Files.writeString(sidecar.resolveSibling(sidecar.getFileName() + "-journal"), "canary");
        assertFailure(
                "SQLITE_INPUT_INVALID",
                "reason",
                "sidecar",
                () -> inspect(sidecar, MbTilesLimits.DEFAULTS));
    }

    @Test
    void opensPinnedIndependentlyGeneratedFixture() throws Exception {
        byte[] transport;
        try (InputStream input =
                MbTilesHardeningTest.class.getResourceAsStream(
                        "/mbtiles-fixtures/independent.mbtiles.gz.b64")) {
            transport =
                    Base64.getMimeDecoder()
                            .decode(java.util.Objects.requireNonNull(input).readAllBytes());
        }
        byte[] bytes;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(transport))) {
            bytes = gzip.readAllBytes();
        }
        assertEquals(2_048, bytes.length);
        assertEquals(
                "de23b7b1a132fbfdd9ada38cb9aaa92366adb2d468aad7fdddd9597b8b4f4979",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Path path = temporary.resolve("independent.mbtiles");
        Files.write(path, bytes);
        try (JDBC4Connection connection = connection(path);
                Statement statement = connection.createStatement();
                ResultSet tile = statement.executeQuery("SELECT tile_data FROM tiles")) {
            assertTrue(tile.next());
            byte[] encoded = tile.getBytes(1);
            assertEquals(666, encoded.length);
            assertEquals(
                    "a8c8402738cedf28b11baddfdc5645b1882c52f72f0b782aae06e3210201e3ed",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)));
            assertFalse(tile.next());
        }
        MbTilesMetadata metadata =
                MbTiles.inspect(
                        path,
                        new SourceIdentity("independent", ""),
                        MbTilesInspectOptions.defaults(),
                        CancellationToken.none());
        assertEquals("Independent synthetic tiles", metadata.name());
        assertEquals(List.of(2), metadata.zoomLevels());
        try (RasterSource source = open(path, MbTilesLimits.DEFAULTS)) {
            RasterRead read =
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(0, 0, 256, 256),
                                    32,
                                    32,
                                    java.util.Optional.empty()),
                            CancellationToken.none());
            int green = read.pixels().rgbaAt(10, 10);
            assertTrue(((green >>> 16) & 0xff) > 150);
        }
    }

    @Test
    void translatesDeploymentFailuresAndThenSucceedsInFreshJvms() throws Exception {
        Path path = deploymentFixture("deployment.mbtiles");
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|nativeLoad", runDeploymentProbe(path, false, null));
        Path unusable = temporary.resolve("not-a-directory");
        Files.writeString(unusable, "occupied");
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|temporaryDirectory",
                runDeploymentProbe(path, true, unusable));
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|unsupportedPlatform",
                runDeploymentProbe(path, true, temporary, "Not Linux", "x86_64"));
        assertEquals(
                "SQLITE_ADAPTER_UNAVAILABLE|unsupportedPlatform",
                runDeploymentProbe(path, true, temporary, "Linux", "not-x86"));
        assertEquals("SUCCESS", runDeploymentProbe(path, true, temporary));
    }

    @Test
    void valueObjectsExposeStableEqualityAndRejectUnreachableHardLimits() {
        assertEquals(MbTilesLimits.DEFAULTS, MbTilesLimits.DEFAULTS);
        assertEquals(
                MbTilesTileCachePolicy.bounded(2, 524_288),
                MbTilesTileCachePolicy.bounded(2, 524_288));
        assertEquals(
                MbTilesOpenOptions.defaults(),
                new MbTilesOpenOptions(
                        MbTilesLimits.DEFAULTS,
                        RasterSourceLimits.LEVEL_1,
                        MbTilesTileCachePolicy.disabled()));
        assertThrows(IllegalArgumentException.class, () -> limits("coordinates", 0));
        assertThrows(IllegalArgumentException.class, () -> limits("parts", 0));
        assertThrows(IllegalArgumentException.class, () -> limits("cacheEntries", 0));
        assertThrows(IllegalArgumentException.class, () -> limits("cacheBytes", 0));
        assertThrows(IllegalArgumentException.class, () -> limits("cacheBytes", 262_143));
        assertEquals(262_144, limits("cacheBytes", 262_144).maximumCacheBytes());
        assertEquals(524_288, limits("ownedBytes", 524_288).maximumOwnedBytes());
        assertThrows(IllegalArgumentException.class, () -> limits("ownedBytes", 524_287));
        MbTilesLimits cacheLimits = limits("cacheEntries", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MbTilesOpenOptions(
                                cacheLimits,
                                RasterSourceLimits.LEVEL_1,
                                MbTilesTileCachePolicy.bounded(2, 524_288)));
    }

    private MbTilesMetadata inspect(Path path, MbTilesLimits limits) {
        return MbTiles.inspect(
                path,
                new SourceIdentity("hardening", ""),
                new MbTilesInspectOptions(limits),
                CancellationToken.none());
    }

    private RasterSource open(Path path, MbTilesLimits limits) {
        return open(path, limits, RasterSourceLimits.LEVEL_1);
    }

    private RasterSource open(
            Path path, MbTilesLimits limits, RasterSourceLimits rasterSourceLimits) {
        return MbTiles.open(
                path,
                new SourceIdentity("hardening", ""),
                2,
                new MbTilesOpenOptions(
                        limits, rasterSourceLimits, MbTilesTileCachePolicy.disabled()),
                AwtRasterDecoders.level1(),
                CancellationToken.none());
    }

    private void assertLimit(Path path, MbTilesLimits limits, String name) {
        SourceException failure = assertThrows(SourceException.class, () -> inspect(path, limits));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(name, failure.terminal().context().get("limit"));
    }

    private static void assertFailure(
            String code, String key, String value, ThrowingOperation operation) {
        SourceException failure = assertThrows(SourceException.class, operation::run);
        assertEquals(code, failure.terminal().code());
        assertEquals(value, failure.terminal().context().get(key));
    }

    private Path fixture(String name) throws Exception {
        Path path = temporary.resolve(name);
        createSchema(path);
        try (JDBC4Connection connection = connection(path);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO metadata VALUES"
                            + " ('name','Synthetic hardening'),('format','png'),"
                            + " ('minzoom','2'),('maxzoom','2')");
        }
        byte[] tile = png(Color.RED);
        insertTile(path, 2, 0, 3, tile);
        insertTile(path, 2, 1, 3, tile);
        insertTile(path, 2, 2, 3, tile);
        return path;
    }

    private Path deploymentFixture(String name) throws Exception {
        Path path = temporary.resolve(name);
        MbTilesConsumerFixtureGenerator.main(new String[] {path.toString()});
        return path;
    }

    private static void createSchema(Path path) throws Exception {
        Files.deleteIfExists(path);
        try (JDBC4Connection connection = connection(path);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id=1297105496");
            statement.execute("CREATE TABLE metadata (name TEXT NOT NULL, value TEXT NOT NULL)");
            statement.execute(
                    "CREATE TABLE tiles (zoom_level INTEGER NOT NULL,"
                            + " tile_column INTEGER NOT NULL,tile_row INTEGER NOT NULL,"
                            + " tile_data BLOB NOT NULL)");
        }
    }

    private static JDBC4Connection connection(Path path) throws SQLException {
        return new JDBC4Connection("jdbc:sqlite:" + path, path.toString(), new Properties());
    }

    private static void insertTile(Path path, int zoom, int x, int tmsY, byte[] tile)
            throws Exception {
        try (JDBC4Connection connection = connection(path);
                PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO tiles VALUES (?,?,?,?)")) {
            statement.setInt(1, zoom);
            statement.setInt(2, x);
            statement.setInt(3, tmsY);
            statement.setBytes(4, tile);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void execute(Path path, String sql) throws Exception {
        try (JDBC4Connection connection = connection(path);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeExpensiveQuery(MbTilesSession session) throws SQLException {
        try (Statement statement = session.connection().createStatement()) {
            try {
                statement
                        .executeQuery(
                                """
                                WITH RECURSIVE values_(value) AS (
                                  VALUES(1) UNION ALL SELECT value+1 FROM values_ WHERE value<1000000
                                )
                                SELECT sum(value) FROM values_
                                """)
                        .close();
            } catch (SQLException exception) {
                throw session.queryFailure(exception, "metadata");
            }
        }
    }

    private static void assertPragma(MbTilesSession session, String name, String expected)
            throws SQLException {
        try (Statement statement = session.connection().createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(result.next());
            assertEquals(expected, result.getString(1));
            assertFalse(result.next());
        }
    }

    private static byte[] png(Color color) throws Exception {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 256, 256);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
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
        entries.add(codeSource(MbTilesDeploymentProbe.class));
        entries.add(codeSource(MbTiles.class));
        entries.add(codeSource(SourceIdentity.class));
        entries.add(codeSource(CrsRegistry.class));
        entries.add(codeSource(MapView.class));
        entries.add(codeSource(ImageSourceLimits.class));
        entries.add(codeSource(JDBC4Connection.class));
        if (includeNative) {
            var resource =
                    java.util.Objects.requireNonNull(
                            MbTilesHardeningTest.class
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
        command.add(MbTilesDeploymentProbe.class.getName());
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

    private static MbTilesLimits textLimits(int valueCharacters, long textCharacters) {
        MbTilesLimits defaults = MbTilesLimits.DEFAULTS;
        return new MbTilesLimits(
                defaults.maximumInputBytes(),
                defaults.maximumSchemaObjects(),
                defaults.maximumColumns(),
                defaults.maximumIdentifierCharacters(),
                defaults.maximumMetadataRows(),
                valueCharacters,
                textCharacters,
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
    }

    private static MbTilesLimits blobAndTextLimits(
            int blobBytes, int valueCharacters, long textCharacters) {
        MbTilesLimits defaults = MbTilesLimits.DEFAULTS;
        return new MbTilesLimits(
                defaults.maximumInputBytes(),
                defaults.maximumSchemaObjects(),
                defaults.maximumColumns(),
                defaults.maximumIdentifierCharacters(),
                defaults.maximumMetadataRows(),
                valueCharacters,
                textCharacters,
                blobBytes,
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
    }

    private static MbTilesLimits limits(String name, long value) {
        MbTilesLimits limits = MbTilesLimits.DEFAULTS;
        return new MbTilesLimits(
                name.equals("inputBytes") ? value : limits.maximumInputBytes(),
                name.equals("schemaObjects")
                        ? Math.toIntExact(value)
                        : limits.maximumSchemaObjects(),
                name.equals("columns") ? Math.toIntExact(value) : limits.maximumColumns(),
                name.equals("identifierCharacters")
                        ? Math.toIntExact(value)
                        : limits.maximumIdentifierCharacters(),
                name.equals("metadataRows") ? value : limits.maximumMetadataRows(),
                name.equals("textValueCharacters")
                        ? Math.toIntExact(value)
                        : limits.maximumTextValueCharacters(),
                name.equals("textCharacters") ? value : limits.maximumTextCharacters(),
                name.equals("blobBytes")
                        ? Math.toIntExact(value)
                        : name.equals("ownedBytes") ? 131_072 : limits.maximumBlobBytes(),
                name.equals("rows") ? value : limits.maximumRows(),
                name.equals("vmOpcodes") ? value : limits.maximumVmOpcodes(),
                name.equals("ownedBytes") ? value : limits.maximumOwnedBytes(),
                name.equals("zoomLevels") ? Math.toIntExact(value) : limits.maximumZoomLevels(),
                name.equals("zoom") ? Math.toIntExact(value) : limits.maximumZoom(),
                name.equals("matrixAxis") ? Math.toIntExact(value) : limits.maximumMatrixAxis(),
                name.equals("coordinates") ? Math.toIntExact(value) : limits.maximumCoordinates(),
                name.equals("parts") ? Math.toIntExact(value) : limits.maximumParts(),
                name.equals("cacheEntries") ? Math.toIntExact(value) : limits.maximumCacheEntries(),
                name.equals("cacheBytes") ? value : limits.maximumCacheBytes());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
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
            RgbaPixelBuffer pixels = delegate.decode(borrowedInput, context);
            if (cancel) {
                cancellation.set(true);
            }
            return pixels;
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
