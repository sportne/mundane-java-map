package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileBoundaryMatrixTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("boundary", "Boundary");

    @TempDir Path temporaryDirectory;

    @Test
    void everyAccountingLimitAcceptsEqualityAndOneMoreButRejectsOneLess() {
        List<AccountingCase> cases =
                List.of(
                        new AccountingCase(
                                "physicalRecords",
                                "shp",
                                limits -> limits.withMaximumPhysicalRecords(2),
                                limits -> limits.withMaximumPhysicalRecords(1),
                                accounting -> {
                                    accounting.physicalRecord(1, 100);
                                    accounting.physicalRecord(2, 128);
                                }),
                        new AccountingCase(
                                "recordBytes",
                                "shp",
                                limits -> limits.withMaximumRecordBytes(2),
                                limits -> limits.withMaximumRecordBytes(1),
                                accounting -> accounting.recordBytes(2, 1, 104)),
                        new AccountingCase(
                                "parts",
                                "shp",
                                limits -> limits.withMaximumParts(2),
                                limits -> limits.withMaximumParts(1),
                                accounting -> accounting.parts(2, 1, 144)),
                        new AccountingCase(
                                "points",
                                "shp",
                                limits -> limits.withMaximumPoints(2),
                                limits -> limits.withMaximumPoints(1),
                                accounting -> accounting.points(2, 1, 148)),
                        new AccountingCase(
                                "physicalRecords",
                                "dbf",
                                limits -> limits.withMaximumPhysicalRecords(2),
                                limits -> limits.withMaximumPhysicalRecords(1),
                                accounting -> accounting.dbfRows(2, 4)),
                        new AccountingCase(
                                "dbfFields",
                                "dbf",
                                limits -> limits.withMaximumDbfFields(2),
                                limits -> limits.withMaximumDbfFields(1),
                                accounting -> accounting.dbfFields(2, 8)),
                        new AccountingCase(
                                "dbfFieldWidth",
                                "dbf",
                                limits -> limits.withMaximumDbfFieldWidth(2),
                                limits -> limits.withMaximumDbfFieldWidth(1),
                                accounting -> accounting.dbfFieldWidth(2, 0, 48)),
                        new AccountingCase(
                                "decodedTextCharacters",
                                "dbf",
                                limits -> limits.withMaximumDecodedTextCharacters(2),
                                limits -> limits.withMaximumDecodedTextCharacters(1),
                                accounting -> accounting.decodedCharacters(2, 1, 0, "name", 66)),
                        new AccountingCase(
                                "parserAllocationBytes",
                                "prj",
                                limits -> limits.withMaximumParserAllocationBytes(2),
                                limits -> limits.withMaximumParserAllocationBytes(1),
                                accounting ->
                                        accounting.allocate("prj", 2, OptionalLong.empty(), 0)));

        for (AccountingCase value : cases) {
            ShapefileLimits defaults = ShapefileLimits.defaults();
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    value.action()
                                            .run(
                                                    new ShapefileAccounting(
                                                            "boundary",
                                                            "matrix",
                                                            value.oneLess().apply(defaults))),
                            value.limit());
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code(), value.limit());
            assertEquals(value.limit(), failure.terminal().context().get("limit"), value.limit());
            assertEquals(
                    value.component(),
                    failure.terminal().location().orElseThrow().component().orElseThrow(),
                    value.limit());
            assertDoesNotThrow(
                    () ->
                            value.action()
                                    .run(
                                            new ShapefileAccounting(
                                                    "boundary",
                                                    "matrix",
                                                    value.equal().apply(defaults))),
                    value.limit());
            assertDoesNotThrow(
                    () ->
                            value.action()
                                    .run(
                                            new ShapefileAccounting(
                                                    "boundary", "matrix", oneMore(value.limit()))),
                    value.limit());
        }
    }

    @Test
    void componentAndSidecarByteLimitsUseInclusiveBoundaries() throws Exception {
        byte[] point = ShpFixtures.point(1, 2);
        byte[] shp = ShpFixtures.file(1, 1, 2, 1, 2, point);
        Path component = write("component.shp", shp);
        assertOpenLimit(component, "componentBytes", "shp", shp.length - 1, shp.length);
        assertOpens(component, ShapefileLimits.defaults().withMaximumComponentBytes(shp.length));
        assertOpens(
                component, ShapefileLimits.defaults().withMaximumComponentBytes(shp.length + 1));

        Path cpg = pairedText("cpg", "UTF-8".getBytes(StandardCharsets.US_ASCII), null);
        assertOpenLimit(cpg, "cpgBytes", "cpg", 4, 5);
        assertOpens(cpg, ShapefileLimits.defaults().withMaximumCpgBytes(5));
        assertOpens(cpg, ShapefileLimits.defaults().withMaximumCpgBytes(6));

        byte[] prj = PrjFixtures.utf8("A[0]");
        Path prjPath = pairedText("prj", null, prj);
        assertOpenLimit(prjPath, "prjBytes", "prj", prj.length - 1, prj.length);
        assertOpens(prjPath, ShapefileLimits.defaults().withMaximumPrjBytes(prj.length));
        assertOpens(prjPath, ShapefileLimits.defaults().withMaximumPrjBytes(prj.length + 1));
    }

    @Test
    void topologyComparisonLimitUsesTheApprovedProspectiveBoundary() throws Exception {
        byte[] polygon =
                ShpFixtures.polygon(
                        new int[] {0, 5},
                        0,
                        0,
                        0,
                        10,
                        10,
                        10,
                        10,
                        0,
                        0,
                        0,
                        2,
                        2,
                        4,
                        2,
                        4,
                        4,
                        2,
                        4,
                        2,
                        2);
        Path path = write("topology.shp", ShpFixtures.file(5, 0, 0, 10, 10, polygon));
        SourceException failure =
                cursorFailure(path, ShapefileLimits.defaults().withMaximumTopologyComparisons(20));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("topologyComparisons", failure.terminal().context().get("limit"));
        assertEquals("21", failure.terminal().context().get("requested"));
        assertCursorSucceeds(path, ShapefileLimits.defaults().withMaximumTopologyComparisons(21));
        assertCursorSucceeds(path, ShapefileLimits.defaults().withMaximumTopologyComparisons(22));
    }

    @Test
    void supportedTwentyCharacterDecimalUsesExactLogicalAllocationBoundary() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("value", 'N', 20, 3)};
        byte[] points = ShpFixtures.multipoint(1, 2, 2, 3, 3, 4, 4, 5);
        Path path = write("decimal.shp", ShpFixtures.file(8, 1, 2, 4, 5, points));
        Files.write(
                temporaryDirectory.resolve("decimal.dbf"),
                DbfFixtures.dbf(
                        0x03, 0, fields, DbfFixtures.row(' ', fields, "1234567890123456.789")));

        assertCursorSucceeds(
                path, ShapefileLimits.defaults().withMaximumParserAllocationBytes(421));
        assertCursorSucceeds(
                path, ShapefileLimits.defaults().withMaximumParserAllocationBytes(422));
        SourceException failure =
                cursorFailure(
                        path, ShapefileLimits.defaults().withMaximumParserAllocationBytes(420));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("parserAllocationBytes", failure.terminal().context().get("limit"));
        assertEquals("421", failure.terminal().context().get("requested"));
        assertEquals("dbf", failure.terminal().location().orElseThrow().component().orElseThrow());

        SourceException decimalCharge =
                cursorFailure(
                        path, ShapefileLimits.defaults().withMaximumParserAllocationBytes(348));
        assertEquals("SOURCE_LIMIT_EXCEEDED", decimalCharge.terminal().code());
        assertEquals("349", decimalCharge.terminal().context().get("requested"));
        assertEquals(
                "value",
                decimalCharge.terminal().location().orElseThrow().fieldName().orElseThrow());
    }

    @Test
    void publicQueryLimitsUseSeparateMinusEqualAndPlusOneBoundaries() throws Exception {
        Path path = twoAttributedPoints("query");
        FeatureQueryLimits defaults = FeatureQueryLimits.LEVEL_1;
        List<QueryCase> cases =
                List.of(
                        new QueryCase(
                                "recordsExamined",
                                2,
                                maximum ->
                                        new FeatureQueryLimits(
                                                maximum,
                                                defaults.recordsReturned(),
                                                defaults.coordinatesReturned(),
                                                defaults.attributeValuesReturned(),
                                                defaults.decodedTextCharactersReturned(),
                                                defaults.ownedPayloadBytes(),
                                                defaults.retainedWarnings())),
                        new QueryCase(
                                "recordsReturned",
                                2,
                                maximum ->
                                        new FeatureQueryLimits(
                                                defaults.recordsExamined(),
                                                maximum,
                                                defaults.coordinatesReturned(),
                                                defaults.attributeValuesReturned(),
                                                defaults.decodedTextCharactersReturned(),
                                                defaults.ownedPayloadBytes(),
                                                defaults.retainedWarnings())),
                        new QueryCase(
                                "coordinatesReturned",
                                2,
                                maximum ->
                                        new FeatureQueryLimits(
                                                defaults.recordsExamined(),
                                                defaults.recordsReturned(),
                                                maximum,
                                                defaults.attributeValuesReturned(),
                                                defaults.decodedTextCharactersReturned(),
                                                defaults.ownedPayloadBytes(),
                                                defaults.retainedWarnings())),
                        new QueryCase(
                                "attributeValuesReturned",
                                2,
                                maximum ->
                                        new FeatureQueryLimits(
                                                defaults.recordsExamined(),
                                                defaults.recordsReturned(),
                                                defaults.coordinatesReturned(),
                                                maximum,
                                                defaults.decodedTextCharactersReturned(),
                                                defaults.ownedPayloadBytes(),
                                                defaults.retainedWarnings())),
                        new QueryCase(
                                "decodedTextCharactersReturned",
                                34,
                                maximum ->
                                        new FeatureQueryLimits(
                                                defaults.recordsExamined(),
                                                defaults.recordsReturned(),
                                                defaults.coordinatesReturned(),
                                                defaults.attributeValuesReturned(),
                                                maximum,
                                                defaults.ownedPayloadBytes(),
                                                defaults.retainedWarnings())),
                        new QueryCase(
                                "ownedPayloadBytes",
                                132,
                                maximum ->
                                        new FeatureQueryLimits(
                                                defaults.recordsExamined(),
                                                defaults.recordsReturned(),
                                                defaults.coordinatesReturned(),
                                                defaults.attributeValuesReturned(),
                                                defaults.decodedTextCharactersReturned(),
                                                maximum,
                                                defaults.retainedWarnings())));

        for (QueryCase value : cases) {
            SourceException failure = queryFailure(path, value.limits().apply(value.actual() - 1));
            assertEquals(value.limit(), failure.terminal().context().get("limit"), value.limit());
            assertEquals(
                    Long.toString(value.actual()),
                    failure.terminal().context().get("requested"),
                    value.limit());
            assertQuerySucceeds(path, value.limits().apply(value.actual()));
            assertQuerySucceeds(path, value.limits().apply(value.actual() + 1));
        }
    }

    @Test
    void retainedOpeningWarningsExposeExactPrefixAndOmittedCountBoundaries() throws Exception {
        byte[] point = ShpFixtures.point(1, 2);
        Path path = write("warning-cap.shp", ShpFixtures.file(1, 1, 2, 1, 2, point));
        FeatureQueryLimits defaults = FeatureQueryLimits.LEVEL_1;
        for (int maximum : List.of(1, 2, 3)) {
            FeatureQueryLimits limits =
                    new FeatureQueryLimits(
                            defaults.recordsExamined(),
                            defaults.recordsReturned(),
                            defaults.coordinatesReturned(),
                            defaults.attributeValuesReturned(),
                            defaults.decodedTextCharactersReturned(),
                            defaults.ownedPayloadBytes(),
                            maximum);
            ShapefileOpenOptions options =
                    ShapefileOpenOptions.defaults()
                            .withFeatureSourceLimits(new FeatureSourceLimits(limits));
            try (FeatureSource source = Shapefiles.open(IDENTITY, path, options)) {
                assertEquals(Math.min(2, maximum), source.openingDiagnostics().entries().size());
                assertEquals(
                        Math.max(0, 2 - maximum),
                        source.openingDiagnostics().omittedWarningCount());
                assertEquals(
                        List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING")
                                .subList(0, Math.min(2, maximum)),
                        source.openingDiagnostics().entries().stream()
                                .map(diagnostic -> diagnostic.code())
                                .toList());
            }
        }
    }

    private void assertOpenLimit(
            Path path, String limit, String component, long maximum, long requested) {
        ShapefileLimits limits =
                switch (limit) {
                    case "componentBytes" ->
                            ShapefileLimits.defaults().withMaximumComponentBytes(maximum);
                    case "cpgBytes" -> ShapefileLimits.defaults().withMaximumCpgBytes(maximum);
                    case "prjBytes" -> ShapefileLimits.defaults().withMaximumPrjBytes(maximum);
                    default -> throw new IllegalArgumentException(limit);
                };
        SourceException failure = assertThrows(SourceException.class, () -> open(path, limits));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(requested), failure.terminal().context().get("requested"));
        assertEquals(
                component, failure.terminal().location().orElseThrow().component().orElseThrow());
    }

    private void assertOpens(Path path, ShapefileLimits limits) {
        try (FeatureSource source = open(path, limits)) {
            assertFalse(source.isClosed());
        }
    }

    private void assertCursorSucceeds(Path path, ShapefileLimits limits) throws Exception {
        try (FeatureSource source = open(path, limits);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    private SourceException cursorFailure(Path path, ShapefileLimits limits) throws Exception {
        try (FeatureSource source = open(path, limits);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            return assertThrows(SourceException.class, cursor::advance);
        }
    }

    private SourceException queryFailure(Path path, FeatureQueryLimits limits) throws Exception {
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults()
                        .withFeatureSourceLimits(new FeatureSourceLimits(limits));
        try (FeatureSource source = Shapefiles.open(IDENTITY, path, options);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            return assertThrows(SourceException.class, cursor::advance);
        }
    }

    private void assertQuerySucceeds(Path path, FeatureQueryLimits limits) throws Exception {
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults()
                        .withFeatureSourceLimits(new FeatureSourceLimits(limits));
        try (FeatureSource source = Shapefiles.open(IDENTITY, path, options);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertTrue(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    private FeatureSource open(Path path, ShapefileLimits limits) {
        return Shapefiles.open(
                IDENTITY, path, ShapefileOpenOptions.defaults().withShapefileLimits(limits));
    }

    private Path pairedText(String stem, byte[] cpg, byte[] prj) throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("name", 'C', 1, 0)};
        byte[] point = ShpFixtures.point(1, 2);
        Path shp = write(stem + ".shp", ShpFixtures.file(1, 1, 2, 1, 2, point));
        Files.write(
                temporaryDirectory.resolve(stem + ".dbf"),
                DbfFixtures.dbf(0x03, 0, fields, DbfFixtures.row(' ', fields, "a")));
        if (cpg != null) {
            Files.write(temporaryDirectory.resolve(stem + ".cpg"), cpg);
        }
        if (prj != null) {
            Files.write(temporaryDirectory.resolve(stem + ".prj"), prj);
        }
        return shp;
    }

    private Path twoAttributedPoints(String stem) throws Exception {
        byte[] first = ShpFixtures.point(1, 2);
        byte[] second = ShpFixtures.point(2, 3);
        Path shp = write(stem + ".shp", ShpFixtures.file(1, 1, 2, 2, 3, first, second));
        Files.write(
                temporaryDirectory.resolve(stem + ".shx"),
                ShxFixtures.file(1, 1, 2, 2, 3, first, second));
        DbfFixtures.Field[] fields = {DbfFixtures.field("name", 'C', 5, 0)};
        Files.write(
                temporaryDirectory.resolve(stem + ".dbf"),
                DbfFixtures.dbf(
                        0x03,
                        0,
                        fields,
                        DbfFixtures.row(' ', fields, "alpha"),
                        DbfFixtures.row(' ', fields, "bravo")));
        Files.write(
                temporaryDirectory.resolve(stem + ".cpg"), new byte[] {'U', 'T', 'F', '-', '8'});
        return shp;
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static ShapefileLimits oneMore(String limit) {
        ShapefileLimits defaults = ShapefileLimits.defaults();
        return switch (limit) {
            case "physicalRecords" -> defaults.withMaximumPhysicalRecords(3);
            case "recordBytes" -> defaults.withMaximumRecordBytes(3);
            case "parts" -> defaults.withMaximumParts(3);
            case "points" -> defaults.withMaximumPoints(3);
            case "dbfFields" -> defaults.withMaximumDbfFields(3);
            case "dbfFieldWidth" -> defaults.withMaximumDbfFieldWidth(3);
            case "decodedTextCharacters" -> defaults.withMaximumDecodedTextCharacters(3);
            case "parserAllocationBytes" -> defaults.withMaximumParserAllocationBytes(3);
            default -> throw new IllegalArgumentException(limit);
        };
    }

    private record AccountingCase(
            String limit,
            String component,
            UnaryOperator<ShapefileLimits> equal,
            UnaryOperator<ShapefileLimits> oneLess,
            AccountingAction action) {}

    private record QueryCase(
            String limit,
            long actual,
            java.util.function.LongFunction<FeatureQueryLimits> limits) {}

    @FunctionalInterface
    private interface AccountingAction {
        void run(ShapefileAccounting accounting);
    }
}
