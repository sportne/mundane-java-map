package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShxIntegrationTest {
    @TempDir Path directory;

    @Test
    void validMissingAndIgnoredIndexesProduceEquivalentRecords() throws Exception {
        byte[][] contents = {
            ShpFixtures.nullShape(), ShpFixtures.point(1, 2), ShpFixtures.point(3, 4)
        };
        Path indexed = dataset("indexed", 1, 0, 0, 4, 4, contents);
        write("indexed.shx", ShxFixtures.file(1, 0, 0, 4, 4, contents));
        Path missing = dataset("missing", 1, 0, 0, 4, 4, contents);
        Path ignored = dataset("ignored", 1, 0, 0, 4, 4, contents);
        byte[] malformed = ShxFixtures.file(1, 0, 0, 4, 4, contents);
        ByteBuffer.wrap(malformed).order(ByteOrder.BIG_ENDIAN).putInt(0, 9995);
        write("ignored.shx", malformed);

        FeatureQuery query =
                new FeatureQuery(
                        Optional.of(new Envelope(0, 0, 2, 3)),
                        io.github.mundanej.map.api.AttributeSelection.NONE,
                        Optional.empty());
        List<String> expected = readPointRecords(indexed, query);
        assertEquals(List.of("record:2@1.0,2.0"), expected);
        assertEquals(expected, readPointRecords(missing, query));
        assertEquals(expected, readPointRecords(ignored, query));

        try (FeatureSource source = open(indexed)) {
            assertMissingDbfOnly(source.openingDiagnostics());
        }
        try (FeatureSource source = open(missing)) {
            assertWarning(source.openingDiagnostics(), "SHAPEFILE_SHX_MISSING", null, -1);
        }
        try (FeatureSource source = open(ignored)) {
            assertWarning(source.openingDiagnostics(), "SHAPEFILE_SHX_IGNORED", "header", 0);
        }
    }

    @Test
    void selectsEitherLowercaseOrUppercaseIndexName() throws Exception {
        byte[] content = ShpFixtures.point(1, 1);
        for (String extension : List.of(".shx", ".SHX")) {
            String stem = extension.equals(".shx") ? "lower" : "upper";
            Path shp = dataset(stem, 1, 0, 0, 1, 1, content);
            write(stem + extension, ShxFixtures.file(1, 0, 0, 1, 1, content));

            try (FeatureSource source = open(shp);
                    FeatureCursor cursor =
                            source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertMissingDbfOnly(source.openingDiagnostics());
                assertTrue(cursor.advance());
                assertEquals("record:1", cursor.current().id());
                assertFalse(cursor.advance());
            }
        }
    }

    @Test
    void missingOrIgnoredIndexWarningPrecedesInvalidPrjTerminal() throws Exception {
        Path missing = dataset("missing-before-prj", 0, 0, 0, 0, 0);
        write("missing-before-prj.prj", new byte[] {1});
        assertWarningBeforeStagedTerminal(missing, "SHAPEFILE_SHX_MISSING", "prj");

        Path ignored = dataset("ignored-before-prj", 0, 0, 0, 0, 0);
        byte[] malformed = ShxFixtures.file(0, 0, 0, 0, 0);
        ByteBuffer.wrap(malformed).order(ByteOrder.BIG_ENDIAN).putInt(0, 9995);
        write("ignored-before-prj.shx", malformed);
        write("ignored-before-prj.prj", new byte[] {1});
        assertWarningBeforeStagedTerminal(ignored, "SHAPEFILE_SHX_IGNORED", "prj");
    }

    @Test
    void acceptsEmptyIndexSignedZeroBoundsAndIgnoredZmBytes() throws Exception {
        Path empty = dataset("empty", 0, 0, 0, 0, 0);
        byte[] emptyIndex = ShxFixtures.file(0, -0.0, -0.0, 0.0, 0.0);
        ByteBuffer little = ByteBuffer.wrap(emptyIndex).order(ByteOrder.LITTLE_ENDIAN);
        little.putLong(68, 0x7ff0000000000000L);
        little.putLong(76, 0xfff0000000000000L);
        little.putLong(84, 0x0123456789abcdefL);
        little.putLong(92, 0xfedcba9876543210L);
        write("empty.shx", emptyIndex);

        try (FeatureSource source = open(empty);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertMissingDbfOnly(source.openingDiagnostics());
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void validatesEveryReservedWordAndHeaderReasonOrder() throws Exception {
        byte[] content = ShpFixtures.point(1, 1);
        for (int offset = 4; offset <= 20; offset += 4) {
            String stem = "reserved-" + offset;
            Path shp = dataset(stem, 1, 0, 0, 1, 1, content);
            byte[] shx = ShxFixtures.file(1, 0, 0, 1, 1, content);
            ByteBuffer.wrap(shx).order(ByteOrder.BIG_ENDIAN).putInt(offset, 1);
            write(stem + ".shx", shx);
            assertIgnored(shp, "header", offset);
        }

        Path declared = dataset("declared", 1, 0, 0, 1, 1, content);
        byte[] declaredIndex = ShxFixtures.file(1, 0, 0, 1, 1, content);
        ByteBuffer.wrap(declaredIndex).order(ByteOrder.BIG_ENDIAN).putInt(24, 53);
        write("declared.shx", declaredIndex);
        assertIgnored(declared, "length", 24);

        Path version = dataset("version", 1, 0, 0, 1, 1, content);
        byte[] versionIndex = ShxFixtures.file(1, 0, 0, 1, 1, content);
        ByteBuffer.wrap(versionIndex).order(ByteOrder.LITTLE_ENDIAN).putInt(28, 999);
        write("version.shx", versionIndex);
        assertIgnored(version, "header", 28);

        Path shape = dataset("shape", 1, 0, 0, 1, 1, content);
        byte[] shapeIndex = ShxFixtures.file(8, 0, 0, 1, 1, content);
        write("shape.shx", shapeIndex);
        assertIgnored(shape, "shpMismatch", 32);

        Path bounds = dataset("bounds", 1, 0, 0, 1, 1, content);
        byte[] boundsIndex = ShxFixtures.file(1, 0, 0, 1, 1, content);
        ByteBuffer.wrap(boundsIndex).order(ByteOrder.LITTLE_ENDIAN).putDouble(52, 2);
        write("bounds.shx", boundsIndex);
        assertIgnored(bounds, "shpMismatch", 52);

        Path nonFinite = dataset("non-finite", 1, 0, 0, 1, 1, content);
        byte[] nonFiniteIndex = ShxFixtures.file(1, 0, 0, 1, 1, content);
        ByteBuffer.wrap(nonFiniteIndex).order(ByteOrder.LITTLE_ENDIAN).putDouble(36, Double.NaN);
        write("non-finite.shx", nonFiniteIndex);
        assertIgnored(nonFinite, "header", 36);

        Path unordered = dataset("unordered", 1, 0, 0, 1, 1, content);
        byte[] unorderedIndex = ShxFixtures.file(1, 0, 0, 1, 1, content);
        ByteBuffer.wrap(unorderedIndex).order(ByteOrder.LITTLE_ENDIAN).putDouble(36, 2);
        write("unordered.shx", unorderedIndex);
        assertIgnored(unordered, "header", 36);
    }

    @Test
    void rejectsCapturedLengthEntryAndFinalEofDisagreementsAtStableLocations() throws Exception {
        byte[] first = ShpFixtures.point(1, 1);
        byte[] second = ShpFixtures.point(2, 2);

        Path trailing = dataset("trailing", 1, 0, 0, 2, 2, first);
        byte[] misaligned = ShxFixtures.file(1, 0, 0, 2, 2, first);
        write("trailing.shx", java.util.Arrays.copyOf(misaligned, misaligned.length + 1));
        assertIgnored(trailing, "length", 0);

        Path zero = dataset("zero-offset", 1, 0, 0, 2, 2, first);
        byte[] zeroIndex = ShxFixtures.file(1, 0, 0, 2, 2, first);
        ByteBuffer.wrap(zeroIndex).order(ByteOrder.BIG_ENDIAN).putInt(100, 0);
        write("zero-offset.shx", zeroIndex);
        assertIgnored(zero, "entry", 100);

        Path shortContent = dataset("short-content", 1, 0, 0, 2, 2, first);
        byte[] shortIndex = ShxFixtures.file(1, 0, 0, 2, 2, first);
        ByteBuffer.wrap(shortIndex).order(ByteOrder.BIG_ENDIAN).putInt(104, 1);
        write("short-content.shx", shortIndex);
        assertIgnored(shortContent, "entry", 104);

        Path gap = dataset("gap", 1, 0, 0, 2, 2, first, second);
        byte[] gapIndex = ShxFixtures.file(1, 0, 0, 2, 2, first, second);
        ByteBuffer.wrap(gapIndex).order(ByteOrder.BIG_ENDIAN).putInt(108, 65);
        write("gap.shx", gapIndex);
        assertIgnored(gap, "shpMismatch", 108);

        Path duplicate = dataset("duplicate", 1, 0, 0, 2, 2, first, second);
        byte[] duplicateIndex = ShxFixtures.file(1, 0, 0, 2, 2, first, second);
        ByteBuffer.wrap(duplicateIndex).order(ByteOrder.BIG_ENDIAN).putInt(108, 50);
        write("duplicate.shx", duplicateIndex);
        assertIgnored(duplicate, "entry", 108);

        Path overlap = dataset("overlap", 1, 0, 0, 2, 2, first, second);
        byte[] overlapIndex = ShxFixtures.file(1, 0, 0, 2, 2, first, second);
        ByteBuffer.wrap(overlapIndex).order(ByteOrder.BIG_ENDIAN).putInt(108, 63);
        write("overlap.shx", overlapIndex);
        assertIgnored(overlap, "shpMismatch", 108);

        Path contentMismatch = dataset("content-mismatch", 1, 0, 0, 2, 2, first);
        byte[] mismatchIndex = ShxFixtures.file(1, 0, 0, 2, 2, first);
        ByteBuffer.wrap(mismatchIndex).order(ByteOrder.BIG_ENDIAN).putInt(104, 11);
        write("content-mismatch.shx", mismatchIndex);
        assertIgnored(contentMismatch, "shpMismatch", 104);

        Path missingEntry = dataset("missing-entry", 1, 0, 0, 2, 2, first, second);
        write("missing-entry.shx", ShxFixtures.file(1, 0, 0, 2, 2, first));
        assertIgnored(missingEntry, "shpMismatch", 108);
    }

    @Test
    void shxOnlyLimitsRecoverToSequentialAccessWithoutLimitDiagnostic() throws Exception {
        byte[] first = ShpFixtures.point(1, 1);
        byte[] second = ShpFixtures.point(2, 2);
        Path shp = dataset("limited", 1, 0, 0, 2, 2, first, second);
        write("limited.shx", ShxFixtures.file(1, 0, 0, 2, 2, first, second));
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults()
                        .withShapefileLimits(
                                ShapefileLimits.defaults().withMaximumPhysicalRecords(1));

        try (FeatureSource source =
                Shapefiles.open(new SourceIdentity("source", "Source"), shp, options)) {
            assertWarning(source.openingDiagnostics(), "SHAPEFILE_SHX_IGNORED", "entry", 100);
            assertTrue(
                    source.openingDiagnostics().entries().stream()
                            .noneMatch(value -> value.code().equals("SOURCE_LIMIT_EXCEEDED")));
        }

        Path allocation = dataset("allocation", 1, 0, 0, 1, 1, first);
        write("allocation.shx", ShxFixtures.file(1, 0, 0, 1, 1, first));
        ShapefileOpenOptions allocationOptions =
                ShapefileOpenOptions.defaults()
                        .withShapefileLimits(
                                ShapefileLimits.defaults().withMaximumParserAllocationBytes(107));
        try (FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity("source", "Source"), allocation, allocationOptions)) {
            assertWarning(source.openingDiagnostics(), "SHAPEFILE_SHX_IGNORED", "entry", 100);
        }
    }

    @Test
    void physicalRecordAndPackedAllocationLimitsAcceptEqualityAndRejectOneMore() throws Exception {
        byte[] first = ShpFixtures.point(1, 1);
        byte[] second = ShpFixtures.point(2, 2);

        Path recordsEqual = dataset("records-equal", 1, 0, 0, 2, 2, first);
        write("records-equal.shx", ShxFixtures.file(1, 0, 0, 2, 2, first));
        assertAcceptedAtOpeningLimit(
                recordsEqual, ShapefileLimits.defaults().withMaximumPhysicalRecords(1));

        Path recordsPlusOne = dataset("records-plus-one", 1, 0, 0, 2, 2, first, second);
        write("records-plus-one.shx", ShxFixtures.file(1, 0, 0, 2, 2, first, second));
        assertIgnoredAtOpeningLimit(
                recordsPlusOne, ShapefileLimits.defaults().withMaximumPhysicalRecords(1));

        Path allocationEqual = dataset("allocation-equal", 1, 0, 0, 2, 2, first);
        write("allocation-equal.shx", ShxFixtures.file(1, 0, 0, 2, 2, first));
        assertAcceptedAtOpeningLimit(
                allocationEqual, ShapefileLimits.defaults().withMaximumParserAllocationBytes(108));

        Path allocationPlusOne = dataset("allocation-plus-one", 1, 0, 0, 2, 2, first);
        write("allocation-plus-one.shx", ShxFixtures.file(1, 0, 0, 2, 2, first));
        assertIgnoredAtOpeningLimit(
                allocationPlusOne,
                ShapefileLimits.defaults().withMaximumParserAllocationBytes(107));
    }

    @Test
    void indexedAndFallbackModesMatchForNullAndMultipointRecords() throws Exception {
        byte[][] contents = {
            ShpFixtures.nullShape(),
            ShpFixtures.multipoint(0, 0, 1, 1),
            ShpFixtures.multipoint(3, 3, 4, 4)
        };
        Path indexed = dataset("multipoint-indexed", 8, 0, 0, 4, 4, contents);
        write("multipoint-indexed.shx", ShxFixtures.file(8, 0, 0, 4, 4, contents));
        Path fallback = dataset("multipoint-fallback", 8, 0, 0, 4, 4, contents);

        FeatureQuery query =
                new FeatureQuery(
                        Optional.of(new Envelope(2.5, 2.5, 4, 4)),
                        io.github.mundanej.map.api.AttributeSelection.NONE,
                        Optional.empty());
        assertEquals(readMultiPointRecords(fallback, query), readMultiPointRecords(indexed, query));
    }

    @Test
    void indexedAndFallbackModesMatchPayloadDiagnosticsAndQueryLimits() throws Exception {
        byte[] malformed = ShpFixtures.multipoint(0, 0, 2, 2);
        ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putDouble(48, 3);
        Path indexedMalformed = dataset("bad-indexed", 8, 0, 0, 2, 2, malformed);
        write("bad-indexed.shx", ShxFixtures.file(8, 0, 0, 2, 2, malformed));
        Path fallbackMalformed = dataset("bad-fallback", 8, 0, 0, 2, 2, malformed);
        assertEquals(
                payloadFailure(fallbackMalformed).terminal(),
                payloadFailure(indexedMalformed).terminal());

        byte[][] contents = {ShpFixtures.nullShape(), ShpFixtures.multipoint(1, 1)};
        Path indexedLimited = dataset("limited-indexed", 8, 0, 0, 1, 1, contents);
        write("limited-indexed.shx", ShxFixtures.file(8, 0, 0, 1, 1, contents));
        Path fallbackLimited = dataset("limited-fallback", 8, 0, 0, 1, 1, contents);
        assertEquals(
                queryLimitFailure(fallbackLimited).terminal(),
                queryLimitFailure(indexedLimited).terminal());
    }

    @Test
    void indexedAndFallbackCursorsShareLifecycleAndLeaveSourceReusable() throws Exception {
        byte[][] contents = {ShpFixtures.nullShape(), ShpFixtures.multipoint(1, 1)};
        Path indexed = dataset("lifecycle-indexed", 8, 0, 0, 1, 1, contents);
        write("lifecycle-indexed.shx", ShxFixtures.file(8, 0, 0, 1, 1, contents));
        Path fallback = dataset("lifecycle-fallback", 8, 0, 0, 1, 1, contents);

        assertCursorLifecycle(indexed);
        assertCursorLifecycle(fallback);
    }

    @Test
    void indexedExhaustionRechecksShpSizeAndReleasesTheCursorSlot() throws Exception {
        byte[] content = ShpFixtures.point(1, 1);
        Path shp = dataset("indexed-size-mutation", 1, 0, 0, 1, 1, content);
        write("indexed-size-mutation.shx", ShxFixtures.file(1, 0, 0, 1, 1, content));

        try (FeatureSource source = open(shp)) {
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            assertTrue(cursor.advance());
            Files.write(shp, new byte[] {0}, StandardOpenOption.APPEND);

            SourceException failure = assertThrows(SourceException.class, cursor::advance);

            assertEquals("SHAPEFILE_FILE_LENGTH_MISMATCH", failure.terminal().code());
            assertEquals("128", failure.terminal().context().get("declaredBytes"));
            assertEquals("129", failure.terminal().context().get("actualBytes"));
            assertThrows(
                    SourceException.class,
                    () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        }
    }

    @Test
    void indexedCursorDetectsRecordNumberAndLengthMutationWithoutFallback() throws Exception {
        byte[] first = ShpFixtures.point(1, 1);
        byte[] second = ShpFixtures.point(2, 2);

        Path number = dataset("number", 1, 0, 0, 2, 2, first, second);
        write("number.shx", ShxFixtures.file(1, 0, 0, 2, 2, first, second));
        try (FeatureSource source = open(number)) {
            putBigEndianInt(number, 100, 9);
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("SHAPEFILE_RECORD_NUMBER_INVALID", failure.terminal().code());
                assertEquals(
                        100,
                        failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
            }
        }

        Path length = dataset("length", 1, 0, 0, 2, 2, first, second);
        write("length.shx", ShxFixtures.file(1, 0, 0, 2, 2, first, second));
        try (FeatureSource source = open(length)) {
            putBigEndianInt(length, 104, 8);
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("SHAPEFILE_RECORD_LENGTH_INVALID", failure.terminal().code());
                assertEquals("indexMismatch", failure.terminal().context().get("reason"));
                assertEquals("16", failure.terminal().context().get("actualBytes"));
                assertEquals("20", failure.terminal().context().get("expectedBytes"));
                assertEquals(
                        104,
                        failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
            }
        }

        Path framing = dataset("framing", 1, 0, 0, 2, 2, first, second);
        write("framing.shx", ShxFixtures.file(1, 0, 0, 2, 2, first, second));
        try (FeatureSource source = open(framing)) {
            putBigEndianInt(framing, 104, Integer.MAX_VALUE);
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                SourceException failure = assertThrows(SourceException.class, cursor::advance);
                assertEquals("SHAPEFILE_RECORD_LENGTH_INVALID", failure.terminal().code());
                assertFalse("indexMismatch".equals(failure.terminal().context().get("reason")));
            }
        }
    }

    private List<String> readPointRecords(Path path, FeatureQuery query) throws Exception {
        List<String> values = new ArrayList<>();
        try (FeatureSource source = open(path);
                FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            while (cursor.advance()) {
                FeatureRecord record = cursor.current();
                PointGeometry point = (PointGeometry) record.geometry();
                values.add(
                        record.id() + '@' + point.coordinate().x() + ',' + point.coordinate().y());
            }
        }
        return values;
    }

    private List<String> readMultiPointRecords(Path path, FeatureQuery query) throws Exception {
        List<String> values = new ArrayList<>();
        try (FeatureSource source = open(path);
                FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            while (cursor.advance()) {
                FeatureRecord record = cursor.current();
                MultiPointGeometry geometry = (MultiPointGeometry) record.geometry();
                values.add(
                        record.id()
                                + '@'
                                + java.util.Arrays.toString(geometry.coordinates().toArray()));
            }
        }
        return values;
    }

    private SourceException payloadFailure(Path path) throws Exception {
        try (FeatureSource source = open(path)) {
            SourceException first;
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                first = assertThrows(SourceException.class, cursor::advance);
            }
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertEquals(
                        first.terminal(),
                        assertThrows(SourceException.class, replacement::advance).terminal());
            }
            return first;
        }
    }

    private SourceException queryLimitFailure(Path path) throws Exception {
        FeatureQueryLimits defaults = FeatureQueryLimits.LEVEL_1;
        FeatureQueryLimits oneExamined =
                new FeatureQueryLimits(
                        1,
                        defaults.recordsReturned(),
                        defaults.coordinatesReturned(),
                        defaults.attributeValuesReturned(),
                        defaults.decodedTextCharactersReturned(),
                        defaults.ownedPayloadBytes(),
                        defaults.retainedWarnings());
        FeatureQuery query =
                new FeatureQuery(
                        Optional.empty(),
                        io.github.mundanej.map.api.AttributeSelection.NONE,
                        Optional.of(oneExamined));
        try (FeatureSource source = open(path)) {
            SourceException failure;
            try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
                failure = assertThrows(SourceException.class, cursor::advance);
            }
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
                assertEquals("record:2", replacement.current().id());
            }
            return failure;
        }
    }

    private void assertCursorLifecycle(Path path) throws Exception {
        try (FeatureSource source = open(path)) {
            FeatureCursor first = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            assertThrows(IllegalStateException.class, first::current);
            assertThrows(
                    IllegalStateException.class,
                    () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
            assertTrue(first.advance());
            assertEquals("record:2", first.current().id());
            first.close();
            first.close();

            try (FeatureCursor exhausted =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(exhausted.advance());
                assertFalse(exhausted.advance());
                assertFalse(exhausted.advance());
                assertThrows(IllegalStateException.class, exhausted::current);
            }
            try (FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(replacement.advance());
            }
        }
    }

    private void assertAcceptedAtOpeningLimit(Path path, ShapefileLimits limits) throws Exception {
        ShapefileOpenOptions options = ShapefileOpenOptions.defaults().withShapefileLimits(limits);
        try (FeatureSource source =
                Shapefiles.open(new SourceIdentity("source", "Source"), path, options)) {
            assertMissingDbfOnly(source.openingDiagnostics());
        }
    }

    private void assertIgnoredAtOpeningLimit(Path path, ShapefileLimits limits) throws Exception {
        ShapefileOpenOptions options = ShapefileOpenOptions.defaults().withShapefileLimits(limits);
        try (FeatureSource source =
                Shapefiles.open(new SourceIdentity("source", "Source"), path, options)) {
            assertWarning(source.openingDiagnostics(), "SHAPEFILE_SHX_IGNORED", "entry", 100);
            assertTrue(
                    source.openingDiagnostics().entries().stream()
                            .noneMatch(value -> value.code().equals("SOURCE_LIMIT_EXCEEDED")));
        }
    }

    private void assertWarningBeforeStagedTerminal(
            Path path, String warningCode, String component) {
        SourceException failure = assertThrows(SourceException.class, () -> open(path));
        assertEquals(3, failure.report().entries().size());
        assertEquals(warningCode, failure.report().entries().get(0).code());
        assertEquals("SHAPEFILE_DBF_MISSING", failure.report().entries().get(1).code());
        assertEquals("SHAPEFILE_PRJ_INVALID", failure.report().entries().get(2).code());
        assertEquals(failure.terminal(), failure.report().entries().get(2));
        assertEquals(
                component, failure.terminal().location().orElseThrow().component().orElseThrow());
    }

    private void assertIgnored(Path path, String reason, long offset) throws Exception {
        try (FeatureSource source = open(path)) {
            assertWarning(source.openingDiagnostics(), "SHAPEFILE_SHX_IGNORED", reason, offset);
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(cursor.advance());
                assertEquals("record:1", cursor.current().id());
            }
        }
    }

    private static void assertWarning(
            DiagnosticReport report, String code, String reason, long offset) {
        assertEquals(2, report.entries().size());
        SourceDiagnostic warning = report.entries().get(0);
        assertEquals(code, warning.code());
        assertEquals("shx", warning.location().orElseThrow().component().orElseThrow());
        assertTrue(warning.location().orElseThrow().recordNumber().isEmpty());
        if (offset < 0) {
            assertTrue(warning.location().orElseThrow().byteOffset().isEmpty());
        } else {
            assertEquals(offset, warning.location().orElseThrow().byteOffset().orElseThrow());
        }
        if (reason == null) {
            assertTrue(warning.context().isEmpty());
        } else {
            assertEquals(reason, warning.context().get("reason"));
        }
        assertEquals("SHAPEFILE_DBF_MISSING", report.entries().get(1).code());
    }

    private static void assertMissingDbfOnly(DiagnosticReport report) {
        assertEquals(1, report.entries().size());
        SourceDiagnostic warning = report.entries().get(0);
        assertEquals("SHAPEFILE_DBF_MISSING", warning.code());
        assertEquals("dbf", warning.location().orElseThrow().component().orElseThrow());
    }

    private Path dataset(
            String stem,
            int type,
            double minX,
            double minY,
            double maxX,
            double maxY,
            byte[]... contents)
            throws Exception {
        return write(stem + ".shp", ShpFixtures.file(type, minX, minY, maxX, maxY, contents));
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path path = directory.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static void putBigEndianInt(Path path, long offset, int value) throws Exception {
        try (var channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.write(
                    ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).flip(),
                    offset);
        }
    }

    private FeatureSource open(Path path) {
        return Shapefiles.open(
                new SourceIdentity("source", "Source"), path, ShapefileOpenOptions.defaults());
    }
}
