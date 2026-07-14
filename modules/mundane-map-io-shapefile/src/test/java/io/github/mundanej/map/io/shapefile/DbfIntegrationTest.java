package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbfIntegrationTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("source", "Source");

    @TempDir Path directory;
    private int fixture;

    @Test
    void missingDbfPublishesAnEmptyKnownSchemaAndAttributes() throws Exception {
        Path path = dataset("missing", ShpFixtures.point(1, 1));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(source.metadata().schema().isPresent());
            assertTrue(source.metadata().schema().orElseThrow().fields().isEmpty());
            assertEquals(
                    List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING"),
                    codes(source.openingDiagnostics().entries()));
            assertTrue(cursor.advance());
            assertTrue(cursor.current().attributes().isEmpty());
        }
    }

    @Test
    void decodesEverySupportedScalarAndItsBlankForm() throws Exception {
        DbfFixtures.Field[] fields = scalarFields();
        byte[] valid =
                DbfFixtures.row(' ', fields, " lead", "-42", "12.34", "1.25E2", "Y", "20240229");
        byte[] blank = DbfFixtures.row(' ', fields, "", "", "", "", "?", "00000000");
        Path path =
                paired(
                        "scalars",
                        new byte[][] {ShpFixtures.point(1, 1), ShpFixtures.point(2, 2)},
                        DbfFixtures.dbf(0x03, 0x57, fields, valid, blank));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertEquals(
                    List.of(
                            AttributeType.TEXT,
                            AttributeType.INTEGER,
                            AttributeType.DECIMAL,
                            AttributeType.FLOATING,
                            AttributeType.LOGICAL,
                            AttributeType.DATE),
                    source.metadata().schema().orElseThrow().fields().stream()
                            .map(field -> field.type())
                            .toList());
            assertTrue(cursor.advance());
            assertEquals(
                    Map.of(
                            "TEXT",
                            " lead",
                            "INT",
                            -42L,
                            "DEC",
                            new BigDecimal("12.34"),
                            "FLOAT",
                            125.0,
                            "LOGICAL",
                            true,
                            "DATE",
                            LocalDate.of(2024, 2, 29)),
                    cursor.current().attributes());
            assertTrue(cursor.advance());
            assertEquals(
                    List.of(
                            AttributeNull.INSTANCE,
                            AttributeNull.INSTANCE,
                            AttributeNull.INSTANCE,
                            AttributeNull.INSTANCE,
                            AttributeNull.INSTANCE,
                            AttributeNull.INSTANCE),
                    new ArrayList<>(cursor.current().attributes().values()));
            assertTrue(cursor.diagnostics().entries().isEmpty());
        }
    }

    @Test
    void malformedSelectedScalarsBecomeNullWithPhysicalFieldOrderedWarnings() throws Exception {
        DbfFixtures.Field[] fields = scalarFields();
        byte[] embedded = new byte[] {'A', 0, 'B'};
        byte[] invalid =
                DbfFixtures.row(
                        ' ',
                        fields,
                        embedded,
                        "99999999999999999999".getBytes(StandardCharsets.US_ASCII),
                        "1.234".getBytes(StandardCharsets.US_ASCII),
                        "1e309".getBytes(StandardCharsets.US_ASCII),
                        "X".getBytes(StandardCharsets.US_ASCII),
                        "20230229".getBytes(StandardCharsets.US_ASCII));
        Path path =
                paired(
                        "invalid-scalars",
                        new byte[][] {ShpFixtures.point(1, 1)},
                        DbfFixtures.dbf(0x03, 0x57, fields, invalid));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertTrue(
                    cursor.current().attributes().values().stream()
                            .allMatch(AttributeNull.INSTANCE::equals));
            assertEquals(
                    List.of("TEXT", "INT", "DEC", "FLOAT", "LOGICAL", "DATE"),
                    cursor.diagnostics().entries().stream()
                            .map(
                                    diagnostic ->
                                            diagnostic
                                                    .location()
                                                    .orElseThrow()
                                                    .fieldName()
                                                    .orElseThrow())
                            .toList());
            assertEquals(
                    List.of("embeddedZero", "overflow", "scale", "nonFinite", "logical", "date"),
                    cursor.diagnostics().entries().stream()
                            .map(diagnostic -> diagnostic.context().get("reason"))
                            .toList());
        }
    }

    @Test
    void projectionControlsOutputOrderAndAvoidsUnselectedWarnings() throws Exception {
        DbfFixtures.Field[] fields = {
            DbfFixtures.field("A", 'C', 4, 0),
            DbfFixtures.field("BAD", 'L', 1, 0),
            DbfFixtures.field("C", 'N', 3, 0)
        };
        Path path =
                paired(
                        "projection",
                        new byte[][] {ShpFixtures.point(1, 1)},
                        DbfFixtures.dbf(
                                0x03, 0x57, fields, DbfFixtures.row(' ', fields, "a", "X", "7")));

        try (FeatureSource source = open(path)) {
            FeatureQuery only = query(AttributeSelection.only(List.of("C", "A")));
            try (FeatureCursor cursor = source.openCursor(only, CancellationToken.none())) {
                assertTrue(cursor.advance());
                assertEquals(
                        List.of("C", "A"), new ArrayList<>(cursor.current().attributes().keySet()));
                assertEquals(
                        List.of(7L, "a"), new ArrayList<>(cursor.current().attributes().values()));
                assertTrue(cursor.diagnostics().entries().isEmpty());
            }
            try (FeatureCursor cursor =
                    source.openCursor(query(AttributeSelection.NONE), CancellationToken.none())) {
                assertTrue(cursor.advance());
                assertTrue(cursor.current().attributes().isEmpty());
                assertTrue(cursor.diagnostics().entries().isEmpty());
            }
            assertThrows(
                    SourceException.class,
                    () ->
                            source.openCursor(
                                    query(AttributeSelection.only(List.of("UNKNOWN"))),
                                    CancellationToken.none()));
            try (FeatureCursor cursor =
                    source.openCursor(query(AttributeSelection.ALL), CancellationToken.none())) {
                assertTrue(cursor.advance());
            }
        }
    }

    @Test
    void reorderedProjectionRetainsPhysicalDiagnosticOrderAndImmutableRequestedOutputOrder()
            throws Exception {
        DbfFixtures.Field[] fields = {
            DbfFixtures.field("FIRST", 'L', 1, 0), DbfFixtures.field("SECOND", 'L', 1, 0)
        };
        Path path =
                paired(
                        "warning-order",
                        onePoint(),
                        DbfFixtures.dbf(
                                0x03, 0x57, fields, DbfFixtures.row(' ', fields, "X", "Z")));

        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(
                                query(AttributeSelection.only(List.of("SECOND", "FIRST"))),
                                CancellationToken.none())) {
            assertTrue(cursor.advance());
            FeatureRecord record = cursor.current();
            assertEquals(List.of("SECOND", "FIRST"), new ArrayList<>(record.attributes().keySet()));
            assertEquals(
                    List.of("FIRST", "SECOND"),
                    cursor.diagnostics().entries().stream()
                            .map(
                                    diagnostic ->
                                            diagnostic
                                                    .location()
                                                    .orElseThrow()
                                                    .fieldName()
                                                    .orElseThrow())
                            .toList());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> record.attributes().put("FIRST", true));
        }
    }

    @Test
    void nullDeletedAndFilteredRowsRemainPhysicallyAlignedWithoutValueWarnings() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("VALUE", 'L', 1, 0)};
        byte[][] shapes = {
            ShpFixtures.point(1, 1),
            ShpFixtures.nullShape(),
            ShpFixtures.point(3, 3),
            ShpFixtures.point(20, 20)
        };
        byte[] dbf =
                DbfFixtures.dbf(
                        0x03,
                        0x57,
                        fields,
                        DbfFixtures.row(' ', fields, "T"),
                        DbfFixtures.row(' ', fields, "X"),
                        DbfFixtures.row('*', fields, "X"),
                        DbfFixtures.row(' ', fields, "X"));
        Path path = paired("alignment", shapes, dbf);
        FeatureQuery nearby =
                new FeatureQuery(
                        Optional.of(new Envelope(0, 0, 2, 2)),
                        AttributeSelection.ALL,
                        Optional.empty());

        try (FeatureSource source = open(path);
                FeatureCursor cursor = source.openCursor(nearby, CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals("record:1", cursor.current().id());
            assertEquals(true, cursor.current().attributes().get("VALUE"));
            assertFalse(cursor.advance());
            assertTrue(cursor.diagnostics().entries().isEmpty());
        }
    }

    @Test
    void validatesLiveAndDeletedMarkersBeforeOmission() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("VALUE", 'C', 1, 0)};
        Path path =
                paired(
                        "marker",
                        new byte[][] {ShpFixtures.point(1, 1)},
                        DbfFixtures.dbf(0x03, 0x57, fields, DbfFixtures.row('!', fields, "x")));

        SourceException failure = firstFailure(path);

        assertEquals("SHAPEFILE_DBF_RECORD_MARKER_INVALID", failure.terminal().code());
        assertEquals(65, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(1, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
    }

    @Test
    void deletedRowsDoNotConcealMalformedShpPayloads() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("VALUE", 'C', 1, 0)};
        Path path =
                paired(
                        "deleted-malformed-shp",
                        new byte[][] {ShpFixtures.typed(8, 40)},
                        DbfFixtures.dbf(0x03, 0x57, fields, DbfFixtures.row('*', fields, "x")));

        SourceException failure = firstFailure(path);

        assertEquals("SHAPEFILE_RECORD_TYPE_MISMATCH", failure.terminal().code());
    }

    @Test
    void supportedHeaderVersionsHonorTheirVersionSpecificStatusRules() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("A", 'C', 1, 0)};
        for (int version : List.of(0x03, 0x04, 0x05)) {
            byte[] dbf = DbfFixtures.dbf(version, 0x57, fields, DbfFixtures.row(' ', fields, "x"));
            ByteBuffer header = DbfFixtures.little(dbf);
            if (version == 0x03) {
                for (int offset = 12; offset <= 28; offset++) {
                    header.put(offset, (byte) 0xa5);
                }
                header.put(30, (byte) 0xa5).put(31, (byte) 0xa5);
            } else {
                header.put(28, (byte) 1);
                header.put(32 + 31, (byte) 1);
            }
            Path path = paired("version-" + version, onePoint(), dbf);
            try (FeatureSource source = open(path);
                    FeatureCursor cursor =
                            source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(cursor.advance());
            }
        }
    }

    @Test
    void rejectsVersionTransactionEncryptionAndMdxStatusAtStableHeaderBytes() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("A", 'C', 1, 0)};
        assertHeaderFailure(
                mutateHeader(DbfFixtures.dbf(0x83, 0x57, fields), 0, 0x83),
                0,
                "version",
                "unsupported");
        assertHeaderFailure(
                mutateHeader(DbfFixtures.dbf(0x04, 0x57, fields), 14, 1),
                14,
                "transaction",
                "nonZero");
        assertHeaderFailure(
                mutateHeader(DbfFixtures.dbf(0x04, 0x57, fields), 15, 1),
                15,
                "encryption",
                "nonZero");
        assertHeaderFailure(
                mutateHeader(DbfFixtures.dbf(0x05, 0x57, fields), 28, 2),
                28,
                "mdxFlag",
                "unsupported");
    }

    @Test
    void rejectsDescriptorNamesWidthsDecimalsDuplicatesAndRowLayout() throws Exception {
        DbfFixtures.Field[] one = {DbfFixtures.field("A", 'C', 1, 0)};
        byte[] emptyName = DbfFixtures.dbf(0x03, 0x57, one);
        DbfFixtures.little(emptyName).put(32, (byte) 0);
        assertFieldFailure(emptyName, 32, 0, "nameEmpty");

        byte[] unterminated = DbfFixtures.dbf(0x03, 0x57, one);
        for (int offset = 32; offset <= 42; offset++) {
            unterminated[offset] = 'A';
        }
        assertFieldFailure(unterminated, 32, 0, "nameUnterminated");

        byte[] nonAscii = DbfFixtures.dbf(0x03, 0x57, one);
        nonAscii[32] = (byte) 0x80;
        assertFieldFailure(nonAscii, 32, 0, "nameNonAscii");

        byte[] whitespace = DbfFixtures.dbf(0x03, 0x57, one);
        whitespace[32] = ' ';
        assertFieldFailure(whitespace, 32, 0, "nameWhitespace");

        byte[] duplicate =
                DbfFixtures.dbf(
                        0x03,
                        0x57,
                        new DbfFixtures.Field[] {
                            DbfFixtures.field("Name", 'C', 1, 0),
                            DbfFixtures.field("nAME", 'C', 1, 0)
                        });
        assertFieldFailure(duplicate, 64, 1, "nameDuplicate");

        byte[] width = DbfFixtures.dbf(0x03, 0x57, one);
        DbfFixtures.little(width).put(48, (byte) 0);
        assertFieldFailure(width, 48, 0, "width");

        byte[] decimals =
                DbfFixtures.dbf(
                        0x03, 0x57, new DbfFixtures.Field[] {DbfFixtures.field("N", 'N', 4, 3)});
        assertFieldFailure(decimals, 49, 0, "decimals");

        byte[] layout = DbfFixtures.dbf(0x03, 0x57, one);
        DbfFixtures.little(layout).putShort(10, (short) 3);
        assertFieldFailure(layout, 10, -1, "rowLayout");
    }

    @Test
    void validatesHeaderLengthsTerminatorAndOptionalEofSuffixExactly() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("A", 'C', 1, 0)};
        byte[] base = DbfFixtures.dbf(0x03, 0x57, fields);

        Path eof = paired("eof", new byte[0][], DbfFixtures.withEof(base));
        try (FeatureSource source = open(eof);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertFalse(cursor.advance());
        }

        byte[] headerLength = base.clone();
        DbfFixtures.little(headerLength).putShort(8, (short) 64);
        assertOpeningFailure(
                headerLength,
                "SHAPEFILE_DBF_HEADER_INVALID",
                8,
                Map.of("field", "headerLength", "reason", "mismatch"));

        byte[] terminator = base.clone();
        terminator[64] = 0;
        assertOpeningFailure(
                terminator, "SHAPEFILE_DBF_HEADER_INVALID", 64, Map.of("field", "terminator"));

        byte[] trailing = java.util.Arrays.copyOf(base, base.length + 2);
        trailing[base.length] = 0x1a;
        trailing[base.length + 1] = 1;
        assertOpeningFailure(
                trailing,
                "SHAPEFILE_DBF_HEADER_INVALID",
                base.length + 1,
                Map.of("field", "fileLayout", "reason", "trailingData"));
    }

    @Test
    void validShxMakesDbfCountMismatchAnOpeningFailure() throws Exception {
        byte[][] shapes = {ShpFixtures.point(1, 1), ShpFixtures.point(2, 2)};
        DbfFixtures.Field[] fields = {DbfFixtures.field("A", 'C', 1, 0)};
        Path path =
                paired(
                        "indexed-count",
                        shapes,
                        DbfFixtures.dbf(0x03, 0x57, fields, DbfFixtures.row(' ', fields, "a")));
        Files.write(
                directory.resolve("indexed-count.shx"),
                ShxFixtures.file(1, 0, 0, 100, 100, shapes));

        SourceException failure = assertThrows(SourceException.class, () -> open(path));

        assertEquals("SHAPEFILE_DBF_RECORD_COUNT_MISMATCH", failure.terminal().code());
        assertEquals(4, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals("1", failure.terminal().context().get("dbfRows"));
        assertEquals("2", failure.terminal().context().get("shpRecords"));
    }

    @Test
    void enforcesDbfFieldWidthRowAndDecodedCharacterLimitsAtEqualityAndPlusOne() throws Exception {
        DbfFixtures.Field[] twoFields = {
            DbfFixtures.field("A", 'C', 2, 0), DbfFixtures.field("B", 'C', 2, 0)
        };
        byte[] twoRows =
                DbfFixtures.dbf(
                        0x03,
                        0x57,
                        twoFields,
                        DbfFixtures.row(' ', twoFields, "aa", "bb"),
                        DbfFixtures.row(' ', twoFields, "cc", "dd"));
        Path accepted =
                paired(
                        "limit-equal",
                        new byte[][] {ShpFixtures.point(1, 1), ShpFixtures.point(2, 2)},
                        twoRows);
        ShapefileLimits equal =
                ShapefileLimits.defaults()
                        .withMaximumDbfFields(2)
                        .withMaximumDbfFieldWidth(2)
                        .withMaximumPhysicalRecords(2)
                        .withMaximumDecodedTextCharacters(8);
        try (FeatureSource source = open(accepted, equal);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertTrue(cursor.advance());
            assertFalse(cursor.advance());
        }

        assertLimitFailure(
                accepted, ShapefileLimits.defaults().withMaximumDbfFields(1), "dbfFields", 2, 1);
        assertLimitFailure(
                accepted,
                ShapefileLimits.defaults().withMaximumDbfFieldWidth(1),
                "dbfFieldWidth",
                2,
                1);
        assertLimitFailure(
                accepted,
                ShapefileLimits.defaults().withMaximumPhysicalRecords(1),
                "physicalRecords",
                2,
                1);

        try (FeatureSource source =
                        open(
                                accepted,
                                ShapefileLimits.defaults().withMaximumDecodedTextCharacters(3));
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("decodedTextCharacters", failure.terminal().context().get("limit"));
            assertEquals("4", failure.terminal().context().get("requested"));
            assertEquals("3", failure.terminal().context().get("maximum"));
        }
    }

    @Test
    void enforcesOpeningAndCumulativeCursorAllocationAtExactBoundaries() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("NAME", 'C', 16, 0)};
        Path path =
                paired(
                        "allocation-boundary",
                        new byte[][] {ShpFixtures.point(1, 1), ShpFixtures.point(2, 2)},
                        DbfFixtures.dbf(
                                0x03,
                                0x57,
                                fields,
                                DbfFixtures.row(' ', fields, "name"),
                                DbfFixtures.row(' ', fields, "name")));

        try (FeatureSource source =
                open(path, ShapefileLimits.defaults().withMaximumParserAllocationBytes(254))) {
            assertTrue(source.metadata().schema().isPresent());
        }
        SourceException opening =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        path,
                                        ShapefileLimits.defaults()
                                                .withMaximumParserAllocationBytes(253)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", opening.terminal().code());
        assertEquals("parserAllocationBytes", opening.terminal().context().get("limit"));
        assertEquals("254", opening.terminal().context().get("requested"));

        try (FeatureSource source =
                        open(
                                path,
                                ShapefileLimits.defaults().withMaximumParserAllocationBytes(289));
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertTrue(cursor.advance());
            assertFalse(cursor.advance());
        }
        try (FeatureSource source =
                        open(
                                path,
                                ShapefileLimits.defaults().withMaximumParserAllocationBytes(288));
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            SourceException cursorFailure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SOURCE_LIMIT_EXCEEDED", cursorFailure.terminal().code());
            assertEquals("parserAllocationBytes", cursorFailure.terminal().context().get("limit"));
            assertEquals("289", cursorFailure.terminal().context().get("requested"));
            assertEquals(
                    2,
                    cursorFailure.terminal().location().orElseThrow().recordNumber().orElseThrow());
        }
    }

    @Test
    void unsupportedFieldsWarnInDescriptorOrderAndCannotBeSelected() throws Exception {
        DbfFixtures.Field[] fields = {
            DbfFixtures.field("MEMO", 'M', 4, 7),
            DbfFixtures.field("NAME", 'C', 4, 0),
            DbfFixtures.field("BINARY", 'B', 2, 9)
        };
        Path path =
                paired(
                        "unsupported",
                        onePoint(),
                        DbfFixtures.dbf(
                                0x03,
                                0x57,
                                fields,
                                DbfFixtures.row(' ', fields, "memo", "name", "xx")));

        try (FeatureSource source = open(path)) {
            assertEquals(
                    List.of("NAME"),
                    source.metadata().schema().orElseThrow().fields().stream()
                            .map(field -> field.name())
                            .toList());
            List<SourceDiagnostic> unsupported =
                    source.openingDiagnostics().entries().stream()
                            .filter(
                                    diagnostic ->
                                            diagnostic
                                                    .code()
                                                    .equals("SHAPEFILE_DBF_FIELD_UNSUPPORTED"))
                            .toList();
            assertEquals(
                    List.of("MEMO", "BINARY"),
                    unsupported.stream()
                            .map(
                                    diagnostic ->
                                            diagnostic
                                                    .location()
                                                    .orElseThrow()
                                                    .fieldName()
                                                    .orElseThrow())
                            .toList());
            assertThrows(
                    SourceException.class,
                    () ->
                            source.openCursor(
                                    query(AttributeSelection.only(List.of("MEMO"))),
                                    CancellationToken.none()));
        }
    }

    @Test
    void retainsUnsupportedFieldWarningBeforeALaterDbfTerminal() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("MEMO", 'M', 4, 0)};
        byte[] dbf = DbfFixtures.dbf(0x03, 0x57, fields, DbfFixtures.row(' ', fields, "memo"));
        dbf[64] = 0;
        Path path = paired("unsupported-then-terminal", onePoint(), dbf);

        SourceException failure = assertThrows(SourceException.class, () -> open(path));

        assertEquals(
                List.of(
                        "SHAPEFILE_SHX_MISSING",
                        "SHAPEFILE_DBF_FIELD_UNSUPPORTED",
                        "SHAPEFILE_DBF_HEADER_INVALID"),
                codes(failure.report().entries()));
        assertEquals(failure.terminal(), failure.report().entries().getLast());

        FeatureQueryLimits defaults = FeatureQueryLimits.LEVEL_1;
        ShapefileOpenOptions capped =
                ShapefileOpenOptions.defaults()
                        .withFeatureSourceLimits(
                                new FeatureSourceLimits(
                                        new FeatureQueryLimits(
                                                defaults.recordsExamined(),
                                                defaults.recordsReturned(),
                                                defaults.coordinatesReturned(),
                                                defaults.attributeValuesReturned(),
                                                defaults.decodedTextCharactersReturned(),
                                                defaults.ownedPayloadBytes(),
                                                1)));
        SourceException cappedFailure =
                assertThrows(SourceException.class, () -> Shapefiles.open(IDENTITY, path, capped));
        assertEquals(
                List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_HEADER_INVALID"),
                codes(cappedFailure.report().entries()));
        assertEquals(1, cappedFailure.report().omittedWarningCount());
    }

    @Test
    void sequentialCountMismatchUsesTheSameStableCodeInBothDirections() throws Exception {
        DbfFixtures.Field[] fields = {DbfFixtures.field("A", 'C', 1, 0)};
        Path tooFew =
                paired(
                        "too-few",
                        new byte[][] {ShpFixtures.point(1, 1), ShpFixtures.point(2, 2)},
                        DbfFixtures.dbf(0x03, 0x57, fields, DbfFixtures.row(' ', fields, "a")));
        try (FeatureSource source = open(tooFew);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SHAPEFILE_DBF_RECORD_COUNT_MISMATCH", failure.terminal().code());
            assertEquals("2", failure.terminal().context().get("requiredOrdinal"));
        }

        Path tooMany =
                paired(
                        "too-many",
                        onePoint(),
                        DbfFixtures.dbf(
                                0x03,
                                0x57,
                                fields,
                                DbfFixtures.row(' ', fields, "a"),
                                DbfFixtures.row(' ', fields, "b")));
        try (FeatureSource source = open(tooMany);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SHAPEFILE_DBF_RECORD_COUNT_MISMATCH", failure.terminal().code());
            assertEquals("1", failure.terminal().context().get("shpRecords"));
            assertEquals("2", failure.terminal().context().get("dbfRows"));
        }
    }

    private void assertHeaderFailure(byte[] dbf, long offset, String field, String reason)
            throws Exception {
        Path path = paired("header-failure-" + fixture++, new byte[0][], dbf);
        SourceException failure = assertThrows(SourceException.class, () -> open(path));
        assertEquals("SHAPEFILE_DBF_HEADER_INVALID", failure.terminal().code());
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(field, failure.terminal().context().get("field"));
        assertEquals(reason, failure.terminal().context().get("reason"));
    }

    private void assertOpeningFailure(
            byte[] dbf, String code, long offset, Map<String, String> context) throws Exception {
        Path path = paired("opening-failure-" + fixture++, new byte[0][], dbf);
        SourceException failure = assertThrows(SourceException.class, () -> open(path));
        assertEquals(code, failure.terminal().code());
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        context.forEach(
                (key, value) -> assertEquals(value, failure.terminal().context().get(key), key));
    }

    private void assertFieldFailure(byte[] dbf, long offset, int fieldIndex, String reason)
            throws Exception {
        Path path = paired("field-failure-" + fixture++, new byte[0][], dbf);
        SourceException failure = assertThrows(SourceException.class, () -> open(path));
        assertEquals("SHAPEFILE_DBF_FIELD_INVALID", failure.terminal().code());
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(reason, failure.terminal().context().get("reason"));
        if (fieldIndex >= 0) {
            assertEquals(
                    fieldIndex,
                    failure.terminal().location().orElseThrow().fieldIndex().orElseThrow());
        }
    }

    private SourceException firstFailure(Path path) throws Exception {
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            return assertThrows(SourceException.class, cursor::advance);
        }
    }

    private void assertLimitFailure(
            Path path, ShapefileLimits limits, String limit, long requested, long maximum) {
        SourceException failure = assertThrows(SourceException.class, () -> open(path, limits));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(requested), failure.terminal().context().get("requested"));
        assertEquals(Long.toString(maximum), failure.terminal().context().get("maximum"));
    }

    private Path dataset(String stem, byte[]... contents) throws Exception {
        Path path = directory.resolve(stem + ".shp");
        Files.write(path, ShpFixtures.file(1, 0, 0, 100, 100, contents));
        return path;
    }

    private Path paired(String stem, byte[][] contents, byte[] dbf) throws Exception {
        Path path = dataset(stem, contents);
        Files.write(directory.resolve(stem + ".dbf"), dbf);
        return path;
    }

    private FeatureSource open(Path path) {
        return Shapefiles.open(IDENTITY, path, ShapefileOpenOptions.defaults());
    }

    private FeatureSource open(Path path, ShapefileLimits limits) {
        return Shapefiles.open(
                IDENTITY, path, ShapefileOpenOptions.defaults().withShapefileLimits(limits));
    }

    private static FeatureQuery query(AttributeSelection selection) {
        return new FeatureQuery(Optional.empty(), selection, Optional.empty());
    }

    private static DbfFixtures.Field[] scalarFields() {
        return new DbfFixtures.Field[] {
            DbfFixtures.field("TEXT", 'C', 10, 0),
            DbfFixtures.field("INT", 'N', 20, 0),
            DbfFixtures.field("DEC", 'N', 7, 2),
            DbfFixtures.field("FLOAT", 'F', 10, 0),
            DbfFixtures.field("LOGICAL", 'L', 1, 0),
            DbfFixtures.field("DATE", 'D', 8, 0)
        };
    }

    private static byte[][] onePoint() {
        return new byte[][] {ShpFixtures.point(1, 1)};
    }

    private static byte[] mutateHeader(byte[] dbf, int offset, int value) {
        dbf[offset] = (byte) value;
        return dbf;
    }

    private static List<String> codes(List<SourceDiagnostic> diagnostics) {
        return diagnostics.stream().map(SourceDiagnostic::code).toList();
    }
}
