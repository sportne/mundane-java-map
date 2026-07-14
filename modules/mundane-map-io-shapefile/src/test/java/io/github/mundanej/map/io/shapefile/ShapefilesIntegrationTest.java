package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefilesIntegrationTest {
    @TempDir Path directory;

    @Test
    void iteratesNullPointAndStableIdentity() throws Exception {
        Path path =
                write(
                        "points.shp",
                        ShpFixtures.file(
                                1,
                                0,
                                0,
                                10,
                                10,
                                ShpFixtures.nullShape(),
                                ShpFixtures.point(-0.0, 2)));
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            FeatureRecord record = cursor.current();
            assertEquals("record:2", record.id());
            PointGeometry point = (PointGeometry) record.geometry();
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(point.coordinate().x()));
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void readsPackedMultipointAndAppliesInclusiveQuery() throws Exception {
        Path path =
                write(
                        "multi.shp",
                        ShpFixtures.file(8, 0, 0, 10, 10, ShpFixtures.multipoint(1, 2, 3, 4)));
        try (FeatureSource source = open(path);
                FeatureCursor cursor =
                        source.openCursor(
                                new FeatureQuery(
                                        Optional.of(new Envelope(3, 4, 3, 4)),
                                        io.github.mundanej.map.api.AttributeSelection.NONE,
                                        Optional.empty()),
                                CancellationToken.none())) {
            assertTrue(cursor.advance());
            MultiPointGeometry geometry = (MultiPointGeometry) cursor.current().geometry();
            assertEquals(2, geometry.coordinates().size());
            assertEquals(3, geometry.coordinates().x(1));
        }
    }

    @Test
    void cursorLifecycleSlotAndSourceCloseAreDeterministic() throws Exception {
        Path path = write("life.shp", ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        FeatureSource source = open(path);
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        assertThrows(IllegalStateException.class, cursor::current);
        cursor.close();
        cursor.close();
        assertTrue(cursor.isClosed());
        FeatureCursor next = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        source.close();
        assertTrue(next.isClosed());
        assertTrue(source.isClosed());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
    }

    @Test
    void openingCancellationAndStagedSidecarAreStructured() throws Exception {
        Path path = write("cancel.shp", ShpFixtures.file(0, 0, 0, 0, 0));
        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();
        assertEquals(
                "SOURCE_CANCELLED",
                assertThrows(
                                SourceException.class,
                                () ->
                                        Shapefiles.open(
                                                new SourceIdentity("s", ""),
                                                path,
                                                ShapefileOpenOptions.defaults(),
                                                cancellation.token()))
                        .terminal()
                        .code());
        Files.write(directory.resolve("cancel.dbf"), new byte[] {1});
        SourceException staged = assertThrows(SourceException.class, () -> open(path));
        assertEquals("SHAPEFILE_PROFILE_NOT_IMPLEMENTED", staged.terminal().code());
        assertEquals("dbf", staged.terminal().location().orElseThrow().component().orElseThrow());
    }

    @Test
    void invalidPathValidationPrecedesOpeningCancellation() {
        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Shapefiles.open(
                                new SourceIdentity("s", ""),
                                directory.resolve("not-a-shapefile.txt"),
                                ShapefileOpenOptions.defaults(),
                                cancellation.token()));
    }

    @Test
    void unsupportedHeaderAndRecordMismatchStayDistinct() throws Exception {
        Path unsupported = write("z.shp", ShpFixtures.file(11, 0, 0, 1, 1));
        assertEquals(
                "SHAPEFILE_SHAPE_TYPE_UNSUPPORTED",
                assertThrows(SourceException.class, () -> open(unsupported)).terminal().code());
        Path mismatch =
                write("mismatch.shp", ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.typed(11, 20)));
        try (FeatureSource source = open(mismatch);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertEquals(
                    "SHAPEFILE_RECORD_TYPE_MISMATCH",
                    assertThrows(SourceException.class, cursor::advance).terminal().code());
        }
    }

    @Test
    void stagesPolylineAndPolygonHeadersButRejectsZHeaders() throws Exception {
        Path polyline = write("line.shp", ShpFixtures.file(3, 0, 0, 1, 1));
        SourceException lineFailure = assertThrows(SourceException.class, () -> open(polyline));
        assertEquals("SHAPEFILE_PROFILE_NOT_IMPLEMENTED", lineFailure.terminal().code());
        assertEquals("polyline", lineFailure.terminal().context().get("profile"));
        assertEquals(
                32, lineFailure.terminal().location().orElseThrow().byteOffset().orElseThrow());

        Path polygon = write("area.shp", ShpFixtures.file(5, 0, 0, 1, 1));
        SourceException areaFailure = assertThrows(SourceException.class, () -> open(polygon));
        assertEquals("SHAPEFILE_PROFILE_NOT_IMPLEMENTED", areaFailure.terminal().code());
        assertEquals("polygon", areaFailure.terminal().context().get("profile"));

        Path pointZ = write("point-z.shp", ShpFixtures.file(11, 0, 0, 1, 1));
        SourceException zFailure = assertThrows(SourceException.class, () -> open(pointZ));
        assertEquals("SHAPEFILE_SHAPE_TYPE_UNSUPPORTED", zFailure.terminal().code());
        assertEquals("11", zFailure.terminal().context().get("shapeType"));
    }

    @Test
    void validatesHeaderFieldsAtStableOffsets() throws Exception {
        byte[] badCode = ShpFixtures.file(0, 0, 0, 0, 0);
        ShpFixtures.bigEndian(badCode).putInt(0, 9995);
        assertHeaderFailure(write("code.shp", badCode), 0, "fileCode", null);

        byte[] reserved = ShpFixtures.file(0, 0, 0, 0, 0);
        ShpFixtures.bigEndian(reserved).putInt(12, 1);
        assertHeaderFailure(write("reserved.shp", reserved), 12, "reserved", null);

        byte[] version = ShpFixtures.file(0, 0, 0, 0, 0);
        ShpFixtures.littleEndian(version).putInt(28, 999);
        assertHeaderFailure(write("version.shp", version), 28, "version", null);

        byte[] unordered = ShpFixtures.file(1, 2, 0, 1, 1);
        assertHeaderFailure(write("unordered.shp", unordered), 36, "bounds", "unordered");

        byte[] nonFinite = ShpFixtures.file(1, 0, 0, 1, 1);
        ShpFixtures.littleEndian(nonFinite).putDouble(44, Double.NaN);
        assertHeaderFailure(write("nan.shp", nonFinite), 44, "bounds", "nonFinite");

        byte[] nonZeroNull = ShpFixtures.file(0, 0, 0, 0, 0);
        ShpFixtures.littleEndian(nonZeroNull).putDouble(52, 1);
        assertHeaderFailure(write("null-bounds.shp", nonZeroNull), 52, "bounds", "nonZeroNull");
    }

    @Test
    void acceptsEitherShpExtensionCaseAndIgnoresMixedCaseSidecars() throws Exception {
        Path lower = write("lower.shp", ShpFixtures.file(0, 0, 0, 0, 0));
        try (FeatureSource source = open(lower)) {
            assertTrue(Files.exists(lower));
            assertFalse(source.isClosed());
        }

        Path upper = write("upper.SHP", ShpFixtures.file(0, 0, 0, 0, 0));
        Files.write(directory.resolve("upper.DbF"), new byte[] {1});
        try (FeatureSource source = open(upper)) {
            assertTrue(Files.exists(upper));
            assertFalse(source.isClosed());
        }
    }

    @Test
    void rejectsDistinctLowercaseAndUppercaseSidecarsAsAmbiguous() throws Exception {
        Path ambiguous = write("ambiguous.shp", ShpFixtures.file(0, 0, 0, 0, 0));
        Files.write(directory.resolve("ambiguous.dbf"), new byte[] {1});
        Files.write(directory.resolve("ambiguous.DBF"), new byte[] {2});
        SourceException ambiguity = assertThrows(SourceException.class, () -> open(ambiguous));
        assertEquals("SHAPEFILE_COMPONENT_AMBIGUOUS", ambiguity.terminal().code());
        assertEquals(
                "dbf", ambiguity.terminal().location().orElseThrow().component().orElseThrow());
    }

    @Test
    void treatsLowercaseAndUppercaseHardLinksAsOneStagedSidecar() throws Exception {
        Path alias = write("alias.shp", ShpFixtures.file(0, 0, 0, 0, 0));
        Path lower = Files.write(directory.resolve("alias.dbf"), new byte[] {1});
        Path upper = directory.resolve("alias.DBF");
        try {
            Files.createLink(upper, lower);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            org.junit.jupiter.api.Assumptions.abort(
                    "Hard links unavailable: " + exception.getClass());
        }
        SourceException staged = assertThrows(SourceException.class, () -> open(alias));
        assertEquals("SHAPEFILE_PROFILE_NOT_IMPLEMENTED", staged.terminal().code());
        assertEquals("dbf", staged.terminal().location().orElseThrow().component().orElseThrow());
    }

    @Test
    void missingShpWinsBeforeSidecarAmbiguity() throws Exception {
        Path missing = directory.resolve("missing.shp");
        Files.write(directory.resolve("missing.dbf"), new byte[] {1});
        Files.write(directory.resolve("missing.DBF"), new byte[] {2});

        SourceException failure = assertThrows(SourceException.class, () -> open(missing));

        assertEquals("SHAPEFILE_COMPONENT_MISSING", failure.terminal().code());
        assertEquals("shp", failure.terminal().location().orElseThrow().component().orElseThrow());
    }

    @Test
    void validatesMalformedRecordsBeforeSpatialFiltering() throws Exception {
        Path path =
                write(
                        "malformed-filtered.shp",
                        ShpFixtures.file(1, 0, 0, 10, 10, ShpFixtures.point(Double.NaN, 2)));
        FeatureQuery disjoint =
                new FeatureQuery(
                        Optional.of(new Envelope(100, 100, 101, 101)),
                        io.github.mundanej.map.api.AttributeSelection.NONE,
                        Optional.empty());
        try (FeatureSource source = open(path);
                FeatureCursor cursor = source.openCursor(disjoint, CancellationToken.none())) {
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SHAPEFILE_COORDINATE_NON_FINITE", failure.terminal().code());
            assertEquals(
                    1, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
            assertEquals(
                    112, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        }
    }

    @Test
    void releasesCursorSlotAfterFailureCancellationAndExhaustion() throws Exception {
        Path malformed =
                write("bad-record.shp", ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.typed(8, 20)));
        try (FeatureSource source = open(malformed)) {
            FeatureCursor failed = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            assertThrows(SourceException.class, failed::advance);
            FeatureCursor replacement =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none());
            replacement.close();
        }

        Path valid =
                write("reusable.shp", ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        try (FeatureSource source = open(valid)) {
            CancellationSource cancellation = new CancellationSource();
            FeatureCursor cancelled = source.openCursor(FeatureQuery.all(), cancellation.token());
            cancellation.cancel();
            assertEquals(
                    "SOURCE_CANCELLED",
                    assertThrows(SourceException.class, cancelled::advance).terminal().code());

            try (FeatureCursor exhausted =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(exhausted.advance());
                assertFalse(exhausted.advance());
            }
            try (FeatureCursor afterEof =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                assertTrue(afterEof.advance());
            }
        }
    }

    @Test
    void detectsFileSizeMutationBeforeCursorCreation() throws Exception {
        Path path = write("mutated.shp", ShpFixtures.file(0, 0, 0, 0, 0));
        try (FeatureSource source = open(path)) {
            Files.write(path, new byte[] {0, 0}, StandardOpenOption.APPEND);
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
            assertEquals("SHAPEFILE_FILE_LENGTH_MISMATCH", failure.terminal().code());
            assertEquals("100", failure.terminal().context().get("declaredBytes"));
            assertEquals("102", failure.terminal().context().get("actualBytes"));
            assertEquals(
                    24, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        }
    }

    @Test
    void pointAndCountLimitsFailBeforePublication() throws Exception {
        Path multi =
                write(
                        "limited.shp",
                        ShpFixtures.file(8, 0, 0, 2, 2, ShpFixtures.multipoint(0, 0, 2, 2)));
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults()
                        .withShapefileLimits(ShapefileLimits.defaults().withMaximumPoints(1));
        try (FeatureSource source = Shapefiles.open(new SourceIdentity("s", ""), multi, options);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
            assertEquals("points", failure.terminal().context().get("limit"));
        }
    }

    @Test
    void exactRecordAndAllocationLimitsPassWhileOneLessFails() throws Exception {
        Path point =
                write(
                        "record-boundary.shp",
                        ShpFixtures.file(1, 0, 0, 1, 1, ShpFixtures.point(1, 1)));
        assertSinglePointWithLimits(
                point,
                ShapefileLimits.defaults()
                        .withMaximumRecordBytes(20)
                        .withMaximumParserAllocationBytes(1_000));

        SourceException recordFailure =
                firstAdvanceFailure(
                        point,
                        ShapefileLimits.defaults()
                                .withMaximumRecordBytes(19)
                                .withMaximumParserAllocationBytes(1_000));
        assertEquals("recordBytes", recordFailure.terminal().context().get("limit"));
        assertEquals("20", recordFailure.terminal().context().get("requested"));
        assertEquals("19", recordFailure.terminal().context().get("maximum"));

        Path multipoint =
                write(
                        "allocation-boundary.shp",
                        ShpFixtures.file(8, 0, 0, 1, 1, ShpFixtures.multipoint(0, 0, 1, 1)));
        assertFirstRecordWithLimits(
                multipoint, ShapefileLimits.defaults().withMaximumParserAllocationBytes(164));
        SourceException allocationFailure =
                firstAdvanceFailure(
                        multipoint,
                        ShapefileLimits.defaults().withMaximumParserAllocationBytes(163));
        assertEquals("parserAllocationBytes", allocationFailure.terminal().context().get("limit"));
        assertEquals("164", allocationFailure.terminal().context().get("requested"));
        assertEquals("163", allocationFailure.terminal().context().get("maximum"));
    }

    @Test
    void physicalRecordLimitCountsNullShapes() throws Exception {
        Path path =
                write(
                        "record-count.shp",
                        ShpFixtures.file(
                                1, 0, 0, 1, 1, ShpFixtures.nullShape(), ShpFixtures.point(1, 1)));
        SourceException failure =
                firstAdvanceFailure(path, ShapefileLimits.defaults().withMaximumPhysicalRecords(1));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("physicalRecords", failure.terminal().context().get("limit"));
        assertEquals("2", failure.terminal().context().get("requested"));
        assertEquals(2, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
    }

    private void assertHeaderFailure(Path path, long offset, String field, String reason) {
        SourceException failure = assertThrows(SourceException.class, () -> open(path));
        assertEquals("SHAPEFILE_HEADER_INVALID", failure.terminal().code());
        assertEquals(field, failure.terminal().context().get("field"));
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        if (reason != null) {
            assertEquals(reason, failure.terminal().context().get("reason"));
        }
    }

    private void assertSinglePointWithLimits(Path path, ShapefileLimits limits) throws Exception {
        assertFirstRecordWithLimits(path, limits);
    }

    private void assertFirstRecordWithLimits(Path path, ShapefileLimits limits) throws Exception {
        ShapefileOpenOptions options = ShapefileOpenOptions.defaults().withShapefileLimits(limits);
        try (FeatureSource source =
                        Shapefiles.open(new SourceIdentity("source", "Source"), path, options);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    private SourceException firstAdvanceFailure(Path path, ShapefileLimits limits)
            throws Exception {
        ShapefileOpenOptions options = ShapefileOpenOptions.defaults().withShapefileLimits(limits);
        try (FeatureSource source =
                        Shapefiles.open(new SourceIdentity("source", "Source"), path, options);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            return assertThrows(SourceException.class, cursor::advance);
        }
    }

    private FeatureSource open(Path path) {
        return Shapefiles.open(
                new SourceIdentity("source", "Source"), path, ShapefileOpenOptions.defaults());
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path path = directory.resolve(name);
        Files.write(path, bytes);
        return path;
    }
}
