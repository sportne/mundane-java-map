package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrjIntegrationTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");
    private static final CrsDefinition WGS84 = CrsRegistry.level1().resolve("EPSG:4326");
    private static final CrsDefinition WEB_MERCATOR = CrsRegistry.level1().resolve("EPSG:3857");

    @TempDir Path directory;
    private int fixture;

    @Test
    void recognizesBothApprovedTreesAndRetainsExactTextAfterAnOptionalBom() throws Exception {
        assertRecognized(PrjFixtures.utf8(PrjFixtures.EPSG_4326), WGS84, PrjFixtures.EPSG_4326);
        assertRecognized(
                PrjFixtures.withBom(PrjFixtures.EPSG_3857), WEB_MERCATOR, PrjFixtures.EPSG_3857);
        try (FeatureSource source =
                open(dataset(PrjFixtures.utf8(PrjFixtures.EPSG_4326), ".PRJ"))) {
            assertMetadata(source, WGS84, PrjFixtures.EPSG_4326);
        }

        String equivalent =
                " \r\n"
                        + PrjFixtures.EPSG_4326
                                .replace("GEOGCS", "geogcs")
                                .replace("DATUM", "DaTuM")
                                .replace("SPHEROID", "spheroid")
                                .replace("PRIMEM", "primem")
                                .replace("UNIT", "unit")
                                .replace("6378137", "+6378137.0E+0")
                                .replace("298.257223563", "298257223563e-9")
                                .replace("0.0174532925199433", "174532925199433e-16")
                        + "\t";
        assertRecognized(PrjFixtures.utf8(equivalent), WGS84, equivalent);
        String saturatedZeroExponent =
                PrjFixtures.EPSG_4326.replace(
                        "PRIMEM[\"Greenwich\",0]",
                        "PRIMEM[\"Greenwich\",-0e999999999999999999999999999]");
        assertRecognized(PrjFixtures.utf8(saturatedZeroExponent), WGS84, saturatedZeroExponent);
    }

    @Test
    void onlyExactQuotedNamesConstantsOrderAndTreeShapeAreRecognized() throws Exception {
        String geographic = PrjFixtures.EPSG_4326;
        String projected = PrjFixtures.EPSG_3857;
        List<String> nearMisses =
                List.of(
                        geographic.replace("GCS_WGS_1984", "WGS_1984"),
                        geographic.replace("D_WGS_1984", "WGS_1984"),
                        geographic.replace("\"WGS_1984\",6378137", "\"WGS 1984\",6378137"),
                        geographic.replace("Greenwich", "GREENWICH"),
                        geographic.replace("Degree", "degree"),
                        geographic.replace("6378137", "6378138"),
                        geographic.replace("298.257223563", "298.257223564"),
                        geographic
                                .replace(
                                        "PRIMEM[\"Greenwich\",0],UNIT",
                                        "UNIT[\"Degree\",0.0174532925199433],PRIMEM")
                                .replace(
                                        "PRIMEM[\"Degree\",0.0174532925199433]",
                                        "PRIMEM[\"Greenwich\",0]"),
                        geographic.substring(0, geographic.length() - 1)
                                + ",AUTHORITY[\"EPSG\",\"4326\"]]",
                        projected.replace(
                                "WGS_1984_Web_Mercator_Auxiliary_Sphere", "WGS_1984_Web_Mercator"),
                        projected.replace("Mercator_Auxiliary_Sphere", "Mercator"),
                        projected.replace("False_Easting", "false_easting"),
                        projected.replace("Standard_Parallel_1", "Standard_Parallel"),
                        projected.replace("Auxiliary_Sphere_Type", "Auxiliary_Sphere"),
                        projected.replace("\"Meter\",1", "\"metre\",1"),
                        projected.replace("\"False_Easting\",0", "\"False_Easting\",1"),
                        projected.replace(
                                "PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0]",
                                "PARAMETER[\"False_Northing\",0],PARAMETER[\"False_Easting\",0]"),
                        projected.substring(0, projected.length() - 1) + ",AXIS[\"X\",EAST]]");

        for (String nearMiss : nearMisses) {
            try (FeatureSource source = open(dataset(PrjFixtures.utf8(nearMiss)))) {
                CrsMetadata metadata = source.metadata().crs().orElseThrow();
                assertTrue(metadata.definition().isEmpty(), nearMiss);
                assertEquals(nearMiss, metadata.retainedDefinition().orElseThrow(), nearMiss);
                assertEquals(
                        List.of(
                                "SHAPEFILE_SHX_MISSING",
                                "SHAPEFILE_DBF_MISSING",
                                "SHAPEFILE_PRJ_CRS_UNRECOGNIZED"),
                        codes(source.openingDiagnostics().entries()),
                        nearMiss);
            }
        }
    }

    @Test
    void appliesTheCompleteMissingBlankRecognizedUnknownAndInvalidOverrideMatrix()
            throws Exception {
        Path missing = dataset(null);
        try (FeatureSource source = open(missing)) {
            assertTrue(source.metadata().crs().isEmpty());
            assertEquals(
                    List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING"),
                    codes(source.openingDiagnostics().entries()));
        }
        try (FeatureSource source = open(missing, options(WGS84))) {
            assertOverrideOnly(source, WGS84);
        }

        for (byte[] blank :
                List.of(new byte[0], PrjFixtures.utf8(" \t\r\n"), PrjFixtures.withBom("\t"))) {
            long expectedOffset = blank.length > 0 && blank[0] == (byte) 0xef ? 3 : 0;
            try (FeatureSource source = open(dataset(blank))) {
                assertTrue(source.metadata().crs().isEmpty());
                assertEquals("SHAPEFILE_PRJ_BLANK", lastCode(source));
                SourceDiagnostic warning = source.openingDiagnostics().entries().get(2);
                assertEquals(
                        expectedOffset,
                        warning.location().orElseThrow().byteOffset().orElseThrow());
                assertTrue(warning.context().isEmpty());
            }
            try (FeatureSource source = open(dataset(blank), options(WGS84))) {
                assertOverrideOnly(source, WGS84);
                assertEquals("SHAPEFILE_PRJ_BLANK", lastCode(source));
            }
        }

        Path recognized = dataset(PrjFixtures.utf8(PrjFixtures.EPSG_4326));
        try (FeatureSource source = open(recognized, options(WGS84))) {
            assertMetadata(source, WGS84, PrjFixtures.EPSG_4326);
            assertEquals(2, source.openingDiagnostics().entries().size());
        }
        SourceException conflict =
                assertThrows(SourceException.class, () -> open(recognized, options(WEB_MERCATOR)));
        assertEquals("SHAPEFILE_CRS_CONFLICT", conflict.terminal().code());
        assertEquals("EPSG:4326", conflict.terminal().context().get("declared"));
        assertEquals("EPSG:3857", conflict.terminal().context().get("override"));
        assertEquals(0, conflict.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(
                List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING", "SHAPEFILE_CRS_CONFLICT"),
                codes(conflict.report().entries()));

        String unknown = "LOCAL_CS[\"Café \u6771 \ud83d\ude00\",UNIT[\"metre\",1]]";
        try (FeatureSource source = open(dataset(PrjFixtures.withBom(unknown)))) {
            CrsMetadata metadata = source.metadata().crs().orElseThrow();
            assertTrue(metadata.definition().isEmpty());
            assertTrue(metadata.declaredIdentifier().isEmpty());
            assertEquals(unknown, metadata.retainedDefinition().orElseThrow());
            assertEquals("SHAPEFILE_PRJ_CRS_UNRECOGNIZED", lastCode(source));
            assertEquals(
                    3,
                    source.openingDiagnostics()
                            .entries()
                            .get(2)
                            .location()
                            .orElseThrow()
                            .byteOffset()
                            .orElseThrow());
        }
        try (FeatureSource source =
                open(dataset(PrjFixtures.utf8(unknown)), options(WEB_MERCATOR))) {
            assertMetadata(source, WEB_MERCATOR, unknown);
            SourceDiagnostic warning = source.openingDiagnostics().entries().get(2);
            assertEquals("SHAPEFILE_PRJ_OVERRIDE_USED", warning.code());
            assertEquals("EPSG:3857", warning.context().get("selected"));
            assertFalse(
                    codes(source.openingDiagnostics().entries())
                            .contains("SHAPEFILE_PRJ_CRS_UNRECOGNIZED"));
        }

        byte[] invalid = PrjFixtures.utf8("A[]");
        SourceException invalidWithOverride =
                assertThrows(SourceException.class, () -> open(dataset(invalid), options(WGS84)));
        assertEquals("SHAPEFILE_PRJ_INVALID", invalidWithOverride.terminal().code());
        assertEquals("syntax", invalidWithOverride.terminal().context().get("reason"));
    }

    @Test
    void recognizedAndUnknownMetadataDriveTheExistingCrsBoundaryWithoutGuessing() throws Exception {
        CrsRegistry registry = CrsRegistry.level1();
        try (FeatureSource geographic = open(dataset(PrjFixtures.utf8(PrjFixtures.EPSG_4326)))) {
            Coordinate projected =
                    registry.operationFromMetadata(geographic.metadata().crs(), WEB_MERCATOR)
                            .transform(new Coordinate(10, 0));
            assertEquals(1_113_194.9079, projected.x(), 0.001);
            assertEquals(0, projected.y(), 0.001);
        }
        try (FeatureSource projected = open(dataset(PrjFixtures.utf8(PrjFixtures.EPSG_3857)))) {
            Coordinate coordinate = new Coordinate(10, 20);
            assertEquals(
                    coordinate,
                    registry.operationFromMetadata(projected.metadata().crs(), WEB_MERCATOR)
                            .transform(coordinate));
        }

        try (FeatureSource missing = open(dataset(null))) {
            CrsException failure =
                    assertThrows(
                            CrsException.class,
                            () ->
                                    registry.operationFromMetadata(
                                            missing.metadata().crs(), WEB_MERCATOR));
            assertEquals("CRS_METADATA_MISSING", failure.problem().code());
        }
        try (FeatureSource unknown = open(dataset(PrjFixtures.utf8("LOCAL_CS[\"x\"]")))) {
            CrsException failure =
                    assertThrows(
                            CrsException.class,
                            () ->
                                    registry.operationFromMetadata(
                                            unknown.metadata().crs(), WEB_MERCATOR));
            assertEquals("CRS_DEFINITION_UNKNOWN", failure.problem().code());
        }
    }

    @Test
    void enforcesPrjByteDecodedCharacterRetainedCharacterAndAllocationBoundaries()
            throws Exception {
        byte[] small = PrjFixtures.utf8("A[0]");
        assertOpensWithLimits(small, ShapefileLimits.defaults().withMaximumPrjBytes(small.length));
        assertLimitFailure(
                small,
                ShapefileLimits.defaults().withMaximumPrjBytes(small.length - 1),
                "prjBytes",
                small.length,
                small.length - 1);

        assertOpensWithLimits(
                small, ShapefileLimits.defaults().withMaximumDecodedTextCharacters(small.length));
        assertLimitFailure(
                small,
                ShapefileLimits.defaults().withMaximumDecodedTextCharacters(small.length - 1),
                "decodedTextCharacters",
                small.length,
                small.length - 1);

        String retainedEqual = PrjFixtures.characterBoundary(CrsMetadata.RETAINED_DEFINITION_LIMIT);
        assertOpensWithLimits(PrjFixtures.utf8(retainedEqual), ShapefileLimits.defaults());
        SourceException retainedFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        dataset(
                                                PrjFixtures.utf8(
                                                        PrjFixtures.characterBoundary(
                                                                CrsMetadata
                                                                                .RETAINED_DEFINITION_LIMIT
                                                                        + 1)))));
        assertEquals("CRS_RETAINED_DEFINITION_TOO_LONG", retainedFailure.terminal().code());
        assertEquals("16384", retainedFailure.terminal().context().get("maximum"));
        assertEquals("16385", retainedFailure.terminal().context().get("requested"));
        assertTrue(retainedFailure.terminal().location().orElseThrow().byteOffset().isEmpty());

        long exactAllocation = 4_744;
        assertOpensWithLimits(
                small,
                ShapefileLimits.defaults().withMaximumParserAllocationBytes(exactAllocation));
        assertLimitFailure(
                small,
                ShapefileLimits.defaults().withMaximumParserAllocationBytes(exactAllocation - 1),
                "parserAllocationBytes",
                exactAllocation,
                exactAllocation - 1);
    }

    @Test
    void warningRetentionKeepsEarlierSidecarsAheadOfPrjWarnings() throws Exception {
        FeatureQueryLimits defaults = FeatureQueryLimits.LEVEL_1;
        FeatureQueryLimits twoWarnings =
                new FeatureQueryLimits(
                        defaults.recordsExamined(),
                        defaults.recordsReturned(),
                        defaults.coordinatesReturned(),
                        defaults.attributeValuesReturned(),
                        defaults.decodedTextCharactersReturned(),
                        defaults.ownedPayloadBytes(),
                        2);
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults()
                        .withFeatureSourceLimits(new FeatureSourceLimits(twoWarnings));
        try (FeatureSource source = open(dataset(PrjFixtures.utf8("LOCAL_CS[\"x\"]")), options)) {
            assertEquals(
                    List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING"),
                    codes(source.openingDiagnostics().entries()));
            assertEquals(1, source.openingDiagnostics().omittedWarningCount());
        }
    }

    @Test
    void prjWarningsFollowShxDbfAndCpgWarningsInEncounterOrder() throws Exception {
        Path path = dataset(PrjFixtures.utf8("LOCAL_CS[\"x\"]"));
        Files.write(sidecar(path, ".cpg"), PrjFixtures.utf8("UTF-8"));

        try (FeatureSource source = open(path)) {
            assertEquals(
                    List.of(
                            "SHAPEFILE_SHX_MISSING",
                            "SHAPEFILE_DBF_MISSING",
                            "SHAPEFILE_CPG_WITHOUT_DBF",
                            "SHAPEFILE_PRJ_CRS_UNRECOGNIZED"),
                    codes(source.openingDiagnostics().entries()));
        }
    }

    private void assertRecognized(byte[] prj, CrsDefinition definition, String retained)
            throws Exception {
        try (FeatureSource source = open(dataset(prj))) {
            assertMetadata(source, definition, retained);
            assertEquals(
                    List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING"),
                    codes(source.openingDiagnostics().entries()));
        }
    }

    private void assertOpensWithLimits(byte[] prj, ShapefileLimits limits) throws Exception {
        ShapefileOpenOptions options = ShapefileOpenOptions.defaults().withShapefileLimits(limits);
        try (FeatureSource source = open(dataset(prj), options)) {
            assertTrue(source.metadata().crs().isPresent());
        }
    }

    private void assertLimitFailure(
            byte[] prj, ShapefileLimits limits, String name, long requested, long maximum)
            throws Exception {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        dataset(prj),
                                        ShapefileOpenOptions.defaults()
                                                .withShapefileLimits(limits)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(name, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(requested), failure.terminal().context().get("requested"));
        assertEquals(Long.toString(maximum), failure.terminal().context().get("maximum"));
    }

    private static void assertOverrideOnly(FeatureSource source, CrsDefinition expected) {
        CrsMetadata metadata = source.metadata().crs().orElseThrow();
        assertEquals(expected, metadata.definition().orElseThrow());
        assertTrue(metadata.declaredIdentifier().isEmpty());
        assertTrue(metadata.retainedDefinition().isEmpty());
    }

    private static void assertMetadata(
            FeatureSource source, CrsDefinition expected, String retained) {
        CrsMetadata metadata = source.metadata().crs().orElseThrow();
        assertEquals(expected, metadata.definition().orElseThrow());
        assertTrue(metadata.declaredIdentifier().isEmpty());
        assertEquals(retained, metadata.retainedDefinition().orElseThrow());
    }

    private static String lastCode(FeatureSource source) {
        List<SourceDiagnostic> entries = source.openingDiagnostics().entries();
        return entries.get(entries.size() - 1).code();
    }

    private Path dataset(byte[] prj) throws Exception {
        return dataset(prj, ".prj");
    }

    private Path dataset(byte[] prj, String extension) throws Exception {
        String stem = "prj-" + fixture++;
        Path shp = directory.resolve(stem + ".shp");
        Files.write(shp, ShpFixtures.file(1, 0, 0, 20, 20, ShpFixtures.point(10, 10)));
        if (prj != null) {
            Files.write(directory.resolve(stem + extension), prj);
        }
        return shp;
    }

    private static ShapefileOpenOptions options(CrsDefinition definition) {
        return ShapefileOpenOptions.defaults().withCrsOverride(definition);
    }

    private static Path sidecar(Path shp, String extension) {
        String filename = Objects.requireNonNull(shp.getFileName()).toString();
        return shp.resolveSibling(filename.substring(0, filename.length() - 4) + extension);
    }

    private static FeatureSource open(Path path) {
        return open(path, ShapefileOpenOptions.defaults());
    }

    private static FeatureSource open(Path path, ShapefileOpenOptions options) {
        return Shapefiles.open(IDENTITY, path, options);
    }

    private static List<String> codes(List<SourceDiagnostic> diagnostics) {
        List<String> result = new ArrayList<>(diagnostics.size());
        for (SourceDiagnostic diagnostic : diagnostics) {
            result.add(diagnostic.code());
        }
        return List.copyOf(result);
    }
}
