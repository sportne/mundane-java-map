package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbfEncodingIntegrationTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");
    private static final DbfFixtures.Field[] TEXT = {DbfFixtures.field("TEXT", 'C', 4, 0)};

    @TempDir Path directory;

    @Test
    void recognizesEveryApprovedCpgAliasIncludingEsri88591() throws Exception {
        assertAliases(
                List.of("UTF-8", "utf8", "65001"), new byte[] {(byte) 0xc3, (byte) 0xa9}, "é");
        assertAliases(List.of("ISO-8859-1", "iso8859-1", "88591"), new byte[] {(byte) 0xe9}, "é");
        assertAliases(List.of("WINDOWS-1252", "cp1252", "1252"), new byte[] {(byte) 0x80}, "€");
        assertAliases(List.of("IBM437", "cp437", "437"), new byte[] {(byte) 0x82}, "é");
        assertAliases(List.of("IBM850", "cp850", "850"), new byte[] {(byte) 0x82}, "é");
    }

    @Test
    void rejectsNearMissAndMalformedCpgTokensBeforeExplicitFallback() throws Exception {
        assertInvalidCpg("near-miss", "28591".getBytes(StandardCharsets.US_ASCII), "unknown", 0);
        assertInvalidCpg("empty", new byte[0], "empty", 0);
        assertInvalidCpg("non-ascii", new byte[] {(byte) 0x80}, "nonAscii", 0);
        assertInvalidCpg(
                "multiple", "UTF-8 1252".getBytes(StandardCharsets.US_ASCII), "multipleTokens", 5);
    }

    @Test
    void emitsEachDifferingPhysicalHintInCpgThenLdidOrder() throws Exception {
        Path path =
                dataset(
                        "three-way",
                        0x57,
                        new byte[] {'a'},
                        "1252".getBytes(StandardCharsets.US_ASCII));
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults().withDbfEncodingOverride(DbfEncoding.UTF_8);

        try (FeatureSource source = Shapefiles.open(IDENTITY, path, options)) {
            List<SourceDiagnostic> conflicts =
                    source.openingDiagnostics().entries().stream()
                            .filter(
                                    diagnostic ->
                                            diagnostic.code().equals("SHAPEFILE_ENCODING_CONFLICT"))
                            .toList();
            assertEquals(2, conflicts.size());
            assertEquals(
                    List.of("cpg", "dbf"),
                    conflicts.stream()
                            .map(
                                    diagnostic ->
                                            diagnostic
                                                    .location()
                                                    .orElseThrow()
                                                    .component()
                                                    .orElseThrow())
                            .toList());
            assertTrue(conflicts.get(0).location().orElseThrow().byteOffset().isEmpty());
            assertEquals(29, conflicts.get(1).location().orElseThrow().byteOffset().orElseThrow());
            assertTrue(
                    conflicts.stream()
                            .allMatch(
                                    diagnostic ->
                                            diagnostic.context().get("selected").equals("UTF_8")
                                                    && diagnostic
                                                            .context()
                                                            .get("ignored")
                                                            .equals("WINDOWS_1252")));
        }
    }

    @Test
    void recognizedCpgWinsOverLdidAndUnknownLdidDoesNotConflict() throws Exception {
        Path conflict =
                dataset(
                        "cpg-wins",
                        0x57,
                        new byte[] {(byte) 0xc3, (byte) 0xa9},
                        "UTF-8".getBytes(StandardCharsets.US_ASCII));
        try (FeatureSource source = open(conflict)) {
            List<SourceDiagnostic> warnings = source.openingDiagnostics().entries();
            assertEquals(
                    List.of("SHAPEFILE_ENCODING_CONFLICT"),
                    warnings.stream()
                            .filter(diagnostic -> diagnostic.code().contains("ENCODING"))
                            .map(SourceDiagnostic::code)
                            .toList());
            assertEquals("é", firstValue(source));
        }

        Path unknown =
                dataset(
                        "unknown-ldid",
                        0x7f,
                        new byte[] {(byte) 0xc3, (byte) 0xa9},
                        "UTF-8".getBytes(StandardCharsets.US_ASCII));
        try (FeatureSource source = open(unknown)) {
            assertTrue(
                    source.openingDiagnostics().entries().stream()
                            .noneMatch(diagnostic -> diagnostic.code().contains("ENCODING")));
            assertEquals("é", firstValue(source));
        }
    }

    @Test
    void absentHintsSelectWindows1252WithOneFallbackWarning() throws Exception {
        for (int ldid : List.of(0, 0x7f)) {
            Path path = dataset("fallback-" + ldid, ldid, new byte[] {(byte) 0x80}, null);
            try (FeatureSource source = open(path)) {
                List<SourceDiagnostic> fallback =
                        source.openingDiagnostics().entries().stream()
                                .filter(
                                        diagnostic ->
                                                diagnostic
                                                        .code()
                                                        .equals("SHAPEFILE_ENCODING_FALLBACK"))
                                .toList();
                assertEquals(1, fallback.size());
                assertEquals("WINDOWS_1252", fallback.get(0).context().get("selected"));
                assertEquals("€", firstValue(source));
            }
        }
    }

    @Test
    void acceptsBomAndWhitespaceAndEnforcesTheCpgByteLimitBeforeAllocation() throws Exception {
        byte[] decorated = {
            (byte) 0xef, (byte) 0xbb, (byte) 0xbf, ' ', '1', '2', '5', '2', '\r', '\n'
        };
        Path decoratedPath = dataset("decorated", 0, new byte[] {(byte) 0x80}, decorated);
        try (FeatureSource source = open(decoratedPath)) {
            assertEquals("€", firstValue(source));
        }

        Path exact =
                dataset("cpg-limit", 0, new byte[] {(byte) 0x80}, new byte[] {'1', '2', '5', '2'});
        ShapefileLimits equality = ShapefileLimits.defaults().withMaximumCpgBytes(4);
        try (FeatureSource source =
                Shapefiles.open(
                        IDENTITY,
                        exact,
                        ShapefileOpenOptions.defaults().withShapefileLimits(equality))) {
            assertEquals("€", firstValue(source));
        }
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                Shapefiles.open(
                                        IDENTITY,
                                        exact,
                                        ShapefileOpenOptions.defaults()
                                                .withShapefileLimits(
                                                        ShapefileLimits.defaults()
                                                                .withMaximumCpgBytes(3))));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("cpgBytes", failure.terminal().context().get("limit"));
        assertEquals("4", failure.terminal().context().get("requested"));
        assertEquals("3", failure.terminal().context().get("maximum"));
    }

    @Test
    void everyUndefinedWindows1252ByteSubstitutesNullWithAnEncodingWarning() throws Exception {
        int[] undefined = {0x81, 0x8d, 0x8f, 0x90, 0x9d};
        byte[][] shapes = new byte[undefined.length][];
        byte[][] rows = new byte[undefined.length][];
        for (int index = 0; index < undefined.length; index++) {
            shapes[index] = ShpFixtures.point(index + 1, index + 1);
            rows[index] = DbfFixtures.row(' ', TEXT, new byte[][] {{(byte) undefined[index]}});
        }
        Path path = directory.resolve("undefined.shp");
        Files.write(path, ShpFixtures.file(1, 0, 0, 10, 10, shapes));
        Files.write(directory.resolve("undefined.dbf"), DbfFixtures.dbf(0x03, 0x57, TEXT, rows));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            for (int ignored : undefined) {
                assertTrue(cursor.advance());
                assertEquals(AttributeNull.INSTANCE, cursor.current().attributes().get("TEXT"));
            }
            assertEquals(
                    List.of("encoding", "encoding", "encoding", "encoding", "encoding"),
                    cursor.diagnostics().entries().stream()
                            .map(diagnostic -> diagnostic.context().get("reason"))
                            .toList());
        }
    }

    @Test
    void cpgWithoutDbfIsWarnedAndIgnoredWithoutSelectingAnEncoding() throws Exception {
        Path path = directory.resolve("orphan.shp");
        Files.write(path, ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        Files.write(directory.resolve("orphan.cpg"), new byte[] {(byte) 0x80});

        try (FeatureSource source =
                        Shapefiles.open(
                                IDENTITY,
                                path,
                                ShapefileOpenOptions.defaults()
                                        .withDbfEncodingOverride(DbfEncoding.IBM850));
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertEquals(
                    List.of(
                            "SHAPEFILE_SHX_MISSING",
                            "SHAPEFILE_DBF_MISSING",
                            "SHAPEFILE_CPG_WITHOUT_DBF"),
                    source.openingDiagnostics().entries().stream()
                            .map(SourceDiagnostic::code)
                            .toList());
            assertTrue(cursor.advance());
            assertTrue(cursor.current().attributes().isEmpty());
        }
    }

    private void assertAliases(List<String> aliases, byte[] encoded, String expected)
            throws Exception {
        for (String alias : aliases) {
            String stem = "alias-" + alias.replace('-', '_');
            Path path = dataset(stem, 0, encoded, alias.getBytes(StandardCharsets.US_ASCII));
            try (FeatureSource source = open(path)) {
                assertEquals(expected, firstValue(source), alias);
                assertTrue(
                        source.openingDiagnostics().entries().stream()
                                .noneMatch(
                                        diagnostic ->
                                                diagnostic.code().equals("SHAPEFILE_CPG_INVALID")
                                                        || diagnostic
                                                                .code()
                                                                .equals(
                                                                        "SHAPEFILE_ENCODING_FALLBACK")),
                        alias);
            }
        }
    }

    private void assertInvalidCpg(String stem, byte[] cpg, String reason, long offset)
            throws Exception {
        Path path = dataset(stem, 0, new byte[] {'a'}, cpg);
        try (FeatureSource source = open(path)) {
            SourceDiagnostic invalid =
                    source.openingDiagnostics().entries().stream()
                            .filter(diagnostic -> diagnostic.code().equals("SHAPEFILE_CPG_INVALID"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(reason, invalid.context().get("reason"));
            assertEquals(offset, invalid.location().orElseThrow().byteOffset().orElseThrow());
            assertEquals(
                    1,
                    source.openingDiagnostics().entries().stream()
                            .filter(
                                    diagnostic ->
                                            diagnostic.code().equals("SHAPEFILE_ENCODING_FALLBACK"))
                            .count());
        }
    }

    private Path dataset(String stem, int ldid, byte[] value, byte[] cpg) throws Exception {
        Path path = directory.resolve(stem + ".shp");
        Files.write(path, ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        Files.write(
                directory.resolve(stem + ".dbf"),
                DbfFixtures.dbf(0x03, ldid, TEXT, DbfFixtures.row(' ', TEXT, value)));
        if (cpg != null) {
            Files.write(directory.resolve(stem + ".cpg"), cpg);
        }
        return path;
    }

    private FeatureSource open(Path path) {
        return Shapefiles.open(IDENTITY, path, ShapefileOpenOptions.defaults());
    }

    private static Object firstValue(FeatureSource source) {
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            return cursor.current().attributes().get("TEXT");
        }
    }
}
