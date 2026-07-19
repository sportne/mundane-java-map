package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoTiffFilesTest {
    private static final SourceIdentity IDENTITY =
            new SourceIdentity("geotiff-test", "GeoTIFF test");

    @Test
    void opensAreaRasterAndServesStrictNearestAndBilinearWindows() {
        try (RasterSource source = open(GeoTiffFixtures.areaGray())) {
            assertEquals(4, source.metadata().width());
            assertEquals(3, source.metadata().height());
            assertEquals(Optional.of(new Envelope(10, 17, 14, 20)), source.metadata().mapBounds());
            assertEquals(
                    "EPSG:4326",
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            var exact =
                    source.read(
                            request(new RasterWindow(1, 1, 2, 2), 2, 2), CancellationToken.none());
            assertArrayEquals(
                    new int[] {gray(100), gray(120), gray(180), gray(200)}, exact.pixels().rgba());
            var scaled =
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(0, 0, 4, 3),
                                    2,
                                    2,
                                    RasterInterpolation.BILINEAR,
                                    Optional.empty()),
                            CancellationToken.none());
            assertEquals(2, scaled.pixels().width());
            SourceException outside =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(3, 2, 2, 1), 2, 1),
                                            CancellationToken.none()));
            assertEquals("RASTER_WINDOW_OUT_OF_RANGE", outside.terminal().code());
        }
    }

    @Test
    void pathAndBytesAreEquivalentAndCallerMutationDoesNotAlias(@TempDir Path directory)
            throws Exception {
        byte[] fixture = GeoTiffFixtures.areaGray();
        Path path = directory.resolve("area.tif");
        Files.write(path, fixture);
        try (RasterSource fromBytes = open(fixture);
                RasterSource fromPath =
                        GeoTiffFiles.openRaster(IDENTITY, path, GeoTiffRasterOptions.defaults())) {
            fixture[274] = (byte) 255;
            assertEquals(fromPath.metadata(), fromBytes.metadata());
            assertArrayEquals(
                    fromPath.read(
                                    request(new RasterWindow(0, 0, 4, 3), 4, 3),
                                    CancellationToken.none())
                            .pixels()
                            .rgba(),
                    fromBytes
                            .read(
                                    request(new RasterWindow(0, 0, 4, 3), 4, 3),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void enforcesOpeningLimitsCancellationAndLifecycle() {
        byte[] fixture = GeoTiffFixtures.areaGray();
        GeoTiffRasterOptions exact =
                GeoTiffRasterOptions.defaults()
                        .withFormatLimits(
                                GeoTiffLimits.defaults()
                                        .withMaximumGeoAsciiBytes(2)
                                        .withMaximumNoDataBytes(2)
                                        .withMaximumTagPayloadBytes(200)
                                        .withMaximumEncodedSegmentBytes(200)
                                        .withMaximumInputBytes(fixture.length));
        try (RasterSource ignored = GeoTiffFiles.openRaster(IDENTITY, fixture, exact)) {
            assertFalse(ignored.isClosed());
        }
        SourceException over =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        fixture,
                                        exact.withFormatLimits(
                                                exact.formatLimits()
                                                        .withMaximumInputBytes(
                                                                fixture.length - 1))));
        assertEquals("SOURCE_LIMIT_EXCEEDED", over.terminal().code());
        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY, fixture, exact, cancellation.token()));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        RasterSource source = GeoTiffFiles.openRaster(IDENTITY, fixture, exact);
        assertFalse(source.isClosed());
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertEquals(4, source.metadata().width());
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.read(
                                request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                CancellationToken.none()));
    }

    @Test
    void rejectsMalformedEnvelopeAndUnsupportedProfile() {
        byte[] version = GeoTiffFixtures.areaGray();
        version[2] = 43;
        SourceException bigTiff = assertThrows(SourceException.class, () -> open(version));
        assertEquals("GEOTIFF_PROFILE_UNSUPPORTED", bigTiff.terminal().code());
        assertEquals("bigTiff", bigTiff.terminal().context().get("construct"));

        byte[] missingTag = GeoTiffFixtures.areaGray();
        missingTag[10] = 1;
        SourceException tag = assertThrows(SourceException.class, () -> open(missingTag));
        assertEquals("GEOTIFF_TAG_INVALID", tag.terminal().code());
    }

    @Test
    void preflightsEveryApplicableFirstSliceOpeningLimit() {
        byte[] fixture = GeoTiffFixtures.areaGray();
        assertLimitBoundary(
                fixture,
                "inputBytes",
                fixture.length,
                fixture.length - 1,
                limits -> limits.withMaximumInputBytes(fixture.length),
                limits -> limits.withMaximumInputBytes(fixture.length - 1));
        assertLimitBoundary(
                fixture,
                "dimension",
                4,
                3,
                limits -> limits.withMaximumDimension(4),
                limits -> limits.withMaximumDimension(3));
        assertLimitBoundary(
                fixture,
                "pixels",
                12,
                11,
                limits -> limits.withMaximumPixels(12),
                limits -> limits.withMaximumPixels(11));
        assertLimitBoundary(
                fixture,
                "ifdEntries",
                13,
                12,
                limits -> limits.withMaximumIfdEntries(13),
                limits -> limits.withMaximumIfdEntries(12));
        assertLimitBoundary(
                fixture,
                "geoKeys",
                3,
                2,
                limits -> limits.withMaximumGeoKeys(3),
                limits -> limits.withMaximumGeoKeys(2));
        assertLimitBoundary(
                GeoTiffFixtures.threeStrips(),
                "segments",
                3,
                2,
                limits -> limits.withMaximumSegments(3),
                limits -> limits.withMaximumSegments(2));
        assertLimitBoundary(
                fixture,
                "encodedSegmentBytes",
                12,
                11,
                limits -> limits.withMaximumEncodedSegmentBytes(12),
                limits -> limits.withMaximumEncodedSegmentBytes(11));
        assertLimitBoundary(
                fixture,
                "decodedSegmentBytes",
                12,
                11,
                limits -> limits.withMaximumDecodedSegmentBytes(12),
                limits -> limits.withMaximumDecodedSegmentBytes(11));
        assertLimitBoundary(
                fixture,
                "tagPayloadBytes",
                48,
                47,
                limits -> limits.withMaximumTagPayloadBytes(48),
                limits -> limits.withMaximumTagPayloadBytes(47));
        assertLimitBoundary(
                fixture,
                "workingBytes",
                928,
                927,
                limits -> limits.withMaximumWorkingBytes(928),
                limits -> limits.withMaximumWorkingBytes(927));
    }

    @Test
    void rejectsPhysicalAliasingAndNonPositiveRangesBeforePublication() {
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 4, 4)),
                "GEOTIFF_HEADER_INVALID",
                "reason",
                "range");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 138, 8)),
                "GEOTIFF_TAG_INVALID",
                "reason",
                "overlap");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 150, 170)),
                "GEOTIFF_TAG_INVALID",
                "reason",
                "overlap");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 78, 170)),
                "GEOTIFF_SEGMENT_INVALID",
                "reason",
                "overlap");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 78, 0)),
                "GEOTIFF_SEGMENT_INVALID",
                "reason",
                "range");
    }

    @Test
    void classifiesNextIfdByStableMalformedAndUnsupportedRules() {
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 166, 275)),
                "GEOTIFF_HEADER_INVALID",
                "reason",
                "alignment");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 166, 1_000)),
                "GEOTIFF_HEADER_INVALID",
                "reason",
                "range");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 166, 4)),
                "GEOTIFF_HEADER_INVALID",
                "reason",
                "range");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 166, 10)),
                "GEOTIFF_HEADER_INVALID",
                "reason",
                "range");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 166, 280)),
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "construct",
                "multipleIfd");
    }

    @Test
    void requiresExactShortGeoKeyDirectoryShape() {
        assertFailure(
                mutate(
                        GeoTiffFixtures.areaGray(),
                        bytes -> {
                            putShort(bytes, 156, 4);
                            putInt(bytes, 158, 8);
                        }),
                "GEOTIFF_TAG_INVALID",
                "reason",
                "type");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putInt(bytes, 158, 15)),
                "GEOTIFF_TAG_INVALID",
                "reason",
                "count");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putShort(bytes, 248, 4)),
                "GEOTIFF_TAG_INVALID",
                "reason",
                "count");
    }

    @Test
    void cancellationDuringOpenAndReadPublishesNoPartialValueAndLeavesSourceReusable() {
        AtomicInteger openChecks = new AtomicInteger();
        CancellationToken duringOpen = () -> openChecks.incrementAndGet() >= 8;
        SourceException openCancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        GeoTiffFixtures.areaGray(),
                                        GeoTiffRasterOptions.defaults(),
                                        duringOpen));
        assertEquals("SOURCE_CANCELLED", openCancelled.terminal().code());
        assertTrue(openChecks.get() >= 8);

        try (RasterSource source = open(GeoTiffFixtures.areaGray())) {
            AtomicInteger readChecks = new AtomicInteger();
            CancellationToken duringRead = () -> readChecks.incrementAndGet() >= 3;
            SourceException readCancelled =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(0, 0, 4, 3), 256, 256),
                                            duringRead));
            assertEquals("SOURCE_CANCELLED", readCancelled.terminal().code());
            assertArrayEquals(
                    new int[] {gray(0)},
                    source.read(
                                    request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void multiStripVectorAndReadPlanningLoopsCheckpointCancellation() {
        AtomicInteger vectorChecks = new AtomicInteger();
        CancellationToken cancelInVector =
                () -> {
                    boolean inVector =
                            java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                                    .anyMatch(
                                            frame ->
                                                    frame.getClassName()
                                                                    .equals(
                                                                            GeoTiffParser.class
                                                                                    .getName())
                                                            && frame.getMethodName()
                                                                    .equals("vector"));
                    return inVector && vectorChecks.incrementAndGet() >= 2;
                };
        SourceException vectorCancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        GeoTiffFixtures.threeStrips(),
                                        GeoTiffRasterOptions.defaults(),
                                        cancelInVector));
        assertEquals("SOURCE_CANCELLED", vectorCancelled.terminal().code());
        assertEquals(2, vectorChecks.get());

        try (RasterSource source = open(GeoTiffFixtures.threeStrips())) {
            AtomicInteger readChecks = new AtomicInteger();
            CancellationToken cancelInStripTotals = () -> readChecks.incrementAndGet() >= 2;
            SourceException readCancelled =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(0, 0, 4, 3), 8_193, 1),
                                            cancelInStripTotals));
            assertEquals("SOURCE_CANCELLED", readCancelled.terminal().code());
            assertEquals(2, readChecks.get());
            assertArrayEquals(
                    new int[] {gray(0)},
                    source.read(
                                    request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void georeferenceDiagnosticsSeparateNonFiniteOrientationAndCollapsedArithmetic() {
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putDouble(bytes, 170, Double.NaN)),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "nonFinite");
        assertFailure(
                mutate(
                        GeoTiffFixtures.areaGray(),
                        bytes -> putDouble(bytes, 210, Double.POSITIVE_INFINITY)),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "nonFinite");
        assertFailure(
                mutate(GeoTiffFixtures.areaGray(), bytes -> putDouble(bytes, 170, 0.0)),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "orientation");
        assertFailure(
                mutate(
                        GeoTiffFixtures.areaGray(),
                        bytes -> putDouble(bytes, 218, Double.MAX_VALUE)),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "collapsed");
        assertFailure(
                mutate(
                        GeoTiffFixtures.areaGray(),
                        bytes -> {
                            putDouble(bytes, 170, 2.0);
                            putDouble(bytes, 194, -Double.MAX_VALUE);
                        }),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "nonFinite");
    }

    @Test
    void decodesBigEndianInlineAndOutOfLineValues() {
        try (RasterSource source = open(GeoTiffFixtures.bigEndianGray())) {
            assertEquals(4, source.metadata().width());
            assertEquals(3, source.metadata().height());
            assertEquals(Optional.of(new Envelope(10, 17, 14, 20)), source.metadata().mapBounds());
            assertArrayEquals(
                    new int[] {
                        GeoTiffFixtures.expectedRgba(1, 0, 1, 1),
                        GeoTiffFixtures.expectedRgba(3, 0, 1, 1),
                        GeoTiffFixtures.expectedRgba(1, 2, 1, 1),
                        GeoTiffFixtures.expectedRgba(3, 2, 1, 1)
                    },
                    source.read(
                                    request(new RasterWindow(0, 0, 4, 3), 2, 2),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void mapsEveryApprovedGrayRgbAndUnassociatedAlphaProfileExactly() {
        assertProfile(GeoTiffFixtures.whiteGray(), 0, 1);
        assertProfile(GeoTiffFixtures.whiteGrayAlpha(), 0, 2);
        assertProfile(GeoTiffFixtures.blackGrayAlpha(), 1, 2);
        assertProfile(GeoTiffFixtures.rgb(), 2, 3);
        assertProfile(GeoTiffFixtures.rgba(), 2, 4);
    }

    @Test
    void readsOnlyIntersectingTilesAndRetainsFullEdgeTileShape() {
        try (RasterSource source = open(GeoTiffFixtures.tiledRgb())) {
            RasterRequestLimits oneTile =
                    new RasterRequestLimits(256, 8_192, 16_777_216, 268_435_456, 268_435_456, 256);
            RasterReadAssert.assertPixels(
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(16, 16, 1, 1),
                                    1,
                                    1,
                                    RasterInterpolation.NEAREST,
                                    Optional.of(oneTile)),
                            CancellationToken.none()),
                    GeoTiffFixtures.expectedRgba(16, 16, 2, 3));

            RasterRequestLimits fourTilesUnder =
                    new RasterRequestLimits(
                            1_023, 8_192, 16_777_216, 268_435_456, 268_435_456, 256);
            SourceException bounded =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            new RasterRequest(
                                                    new RasterWindow(15, 15, 2, 2),
                                                    2,
                                                    2,
                                                    RasterInterpolation.NEAREST,
                                                    Optional.of(fourTilesUnder)),
                                            CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", bounded.terminal().code());
            assertArrayEquals(
                    new int[] {
                        GeoTiffFixtures.expectedRgba(15, 15, 2, 3),
                        GeoTiffFixtures.expectedRgba(16, 15, 2, 3),
                        GeoTiffFixtures.expectedRgba(15, 16, 2, 3),
                        GeoTiffFixtures.expectedRgba(16, 16, 2, 3)
                    },
                    source.read(
                                    request(new RasterWindow(15, 15, 2, 2), 2, 2),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void recognizesProjectedWebMercatorPlacementWithoutReprojection() {
        try (RasterSource source = open(GeoTiffFixtures.projectedGray())) {
            assertEquals(
                    "EPSG:3857",
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            assertEquals(
                    Optional.of(new Envelope(1_000, 1_997, 1_004, 2_000)),
                    source.metadata().mapBounds());
            assertArrayEquals(
                    new int[] {GeoTiffFixtures.expectedRgba(2, 1, 1, 1)},
                    source.read(
                                    request(new RasterWindow(2, 1, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void rejectsMalformedTileShapeCountAndDecodedLength() {
        byte[] shape = GeoTiffFixtures.tiledRgb();
        putShortAtEntryValue(shape, 322, 15);
        assertFailure(shape, "GEOTIFF_PROFILE_UNSUPPORTED", "construct", "sampleOrganization");

        byte[] count = GeoTiffFixtures.tiledRgb();
        putEntryCount(count, 324, 3);
        assertFailure(count, "GEOTIFF_TAG_INVALID", "reason", "count");

        byte[] decoded = GeoTiffFixtures.tiledRgb();
        putFirstLongPayloadValue(decoded, 325, 767);
        assertFailure(decoded, "GEOTIFF_SEGMENT_INVALID", "reason", "decodedLength");
    }

    @Test
    void preflightsExactOverLimitAndOverflowingTileDecodedShapes() {
        byte[] tiled = GeoTiffFixtures.tiledRgb();
        GeoTiffRasterOptions exact =
                GeoTiffRasterOptions.defaults()
                        .withFormatLimits(
                                GeoTiffLimits.defaults().withMaximumDecodedSegmentBytes(768));
        try (RasterSource accepted =
                GeoTiffFiles.openRaster(IDENTITY, tiled, exact, CancellationToken.none())) {
            assertEquals(17, accepted.metadata().width());
        }
        SourceException over =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        tiled,
                                        exact.withFormatLimits(
                                                exact.formatLimits()
                                                        .withMaximumDecodedSegmentBytes(767)),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", over.terminal().code());
        assertEquals("decodedSegmentBytes", over.terminal().context().get("limit"));
        assertEquals("768", over.terminal().context().get("requested"));
        assertEquals("767", over.terminal().context().get("maximum"));

        byte[] overflowing = GeoTiffFixtures.tiledRgb();
        int largestDivisibleBySixteen = 2_147_483_632;
        retainFirstLongValue(overflowing, 324);
        retainFirstLongValue(overflowing, 325);
        putLongAtEntryValue(overflowing, 322, largestDivisibleBySixteen);
        putLongAtEntryValue(overflowing, 323, largestDivisibleBySixteen);
        assertFailure(overflowing, "GEOTIFF_SEGMENT_INVALID", "reason", "decodedLength");
    }

    @Test
    void checkpointsValidSampleVectorsAndRejectsImpossibleCountsBeforeTraversal() {
        AtomicInteger vectorChecks = new AtomicInteger();
        CancellationToken cancelInSampleVector =
                () -> {
                    boolean inVector =
                            java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                                    .anyMatch(
                                            frame ->
                                                    frame.getClassName()
                                                                    .equals(
                                                                            GeoTiffParser.class
                                                                                    .getName())
                                                            && frame.getMethodName()
                                                                    .equals("requireShortArray"));
                    return inVector && vectorChecks.incrementAndGet() == 1;
                };
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        GeoTiffFixtures.rgb(),
                                        GeoTiffRasterOptions.defaults(),
                                        cancelInSampleVector));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals(1, vectorChecks.get());

        byte[] impossible = GeoTiffFixtures.rgb();
        putShortAtEntryValue(impossible, 277, 0xffff);
        AtomicInteger invalidVectorChecks = new AtomicInteger();
        CancellationToken observeSampleVector =
                () -> {
                    if (java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                            .anyMatch(
                                    frame ->
                                            frame.getClassName()
                                                            .equals(GeoTiffParser.class.getName())
                                                    && frame.getMethodName()
                                                            .equals("requireShortArray"))) {
                        invalidVectorChecks.incrementAndGet();
                    }
                    return false;
                };
        SourceException unsupported =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        impossible,
                                        GeoTiffRasterOptions.defaults(),
                                        observeSampleVector));
        assertEquals("GEOTIFF_PROFILE_UNSUPPORTED", unsupported.terminal().code());
        assertEquals("photometric", unsupported.terminal().context().get("construct"));
        assertEquals(0, invalidVectorChecks.get());
    }

    private static void assertProfile(byte[] fixture, int photometric, int samples) {
        try (RasterSource source = open(fixture)) {
            assertArrayEquals(
                    new int[] {
                        GeoTiffFixtures.expectedRgba(1, 1, photometric, samples),
                        GeoTiffFixtures.expectedRgba(2, 1, photometric, samples)
                    },
                    source.read(
                                    request(new RasterWindow(1, 1, 2, 1), 2, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    private static void putShortAtEntryValue(byte[] bytes, int tag, int value) {
        java.nio.ByteBuffer buffer = ordered(bytes);
        buffer.putShort(entryOffset(buffer, tag) + 8, (short) value);
    }

    private static void putEntryCount(byte[] bytes, int tag, int value) {
        java.nio.ByteBuffer buffer = ordered(bytes);
        buffer.putInt(entryOffset(buffer, tag) + 4, value);
    }

    private static void putLongAtEntryValue(byte[] bytes, int tag, int value) {
        java.nio.ByteBuffer buffer = ordered(bytes);
        int entry = entryOffset(buffer, tag);
        buffer.putShort(entry + 2, (short) 4);
        buffer.putInt(entry + 8, value);
    }

    private static void retainFirstLongValue(byte[] bytes, int tag) {
        java.nio.ByteBuffer buffer = ordered(bytes);
        int entry = entryOffset(buffer, tag);
        int payload = buffer.getInt(entry + 8);
        int firstValue = buffer.getInt(payload);
        buffer.putInt(entry + 4, 1);
        buffer.putInt(entry + 8, firstValue);
    }

    private static void putFirstLongPayloadValue(byte[] bytes, int tag, int value) {
        java.nio.ByteBuffer buffer = ordered(bytes);
        int entry = entryOffset(buffer, tag);
        int payload = buffer.getInt(entry + 8);
        buffer.putInt(payload, value);
    }

    private static java.nio.ByteBuffer ordered(byte[] bytes) {
        return java.nio.ByteBuffer.wrap(bytes)
                .order(
                        bytes[0] == 'M'
                                ? java.nio.ByteOrder.BIG_ENDIAN
                                : java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    private static int entryOffset(java.nio.ByteBuffer bytes, int tag) {
        int ifd = bytes.getInt(4);
        int entries = Short.toUnsignedInt(bytes.getShort(ifd));
        for (int index = 0; index < entries; index++) {
            int offset = ifd + 2 + index * 12;
            if (Short.toUnsignedInt(bytes.getShort(offset)) == tag) {
                return offset;
            }
        }
        throw new AssertionError("Fixture has no tag " + tag);
    }

    private static final class RasterReadAssert {
        private RasterReadAssert() {}

        private static void assertPixels(io.github.mundanej.map.api.RasterRead read, int... rgba) {
            assertArrayEquals(rgba, read.pixels().rgba());
        }
    }

    private static void assertLimitBoundary(
            byte[] fixture,
            String limit,
            long exact,
            long oneUnder,
            UnaryOperator<GeoTiffLimits> exactLimits,
            UnaryOperator<GeoTiffLimits> oneUnderLimits) {
        assertLimitBoundary(
                fixture, limit, exact, oneUnder, exactLimits, oneUnderLimits, ignored -> {});
    }

    private static void assertLimitBoundary(
            byte[] fixture,
            String limit,
            long exact,
            long oneUnder,
            UnaryOperator<GeoTiffLimits> exactLimits,
            UnaryOperator<GeoTiffLimits> oneUnderLimits,
            java.util.function.Consumer<byte[]> mutation) {
        byte[] candidate = fixture.clone();
        mutation.accept(candidate);
        GeoTiffLimits base = boundaryBase(candidate.length);
        try (RasterSource accepted =
                GeoTiffFiles.openRaster(
                        IDENTITY,
                        candidate,
                        GeoTiffRasterOptions.defaults()
                                .withFormatLimits(exactLimits.apply(base)))) {
            assertFalse(accepted.isClosed(), () -> limit + " exact boundary did not open");
        }
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        candidate,
                                        GeoTiffRasterOptions.defaults()
                                                .withFormatLimits(oneUnderLimits.apply(base))));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(exact), failure.terminal().context().get("requested"));
        assertEquals(Long.toString(oneUnder), failure.terminal().context().get("maximum"));
    }

    private static GeoTiffLimits boundaryBase(int inputBytes) {
        return GeoTiffLimits.defaults()
                .withMaximumGeoAsciiBytes(2)
                .withMaximumNoDataBytes(2)
                .withMaximumTagPayloadBytes(48)
                .withMaximumEncodedSegmentBytes(12)
                .withMaximumDecodedSegmentBytes(12)
                .withMaximumInputBytes(inputBytes);
    }

    private static byte[] mutate(byte[] fixture, java.util.function.Consumer<byte[]> mutation) {
        mutation.accept(fixture);
        return fixture;
    }

    private static void assertFailure(
            byte[] fixture, String code, String contextKey, String contextValue) {
        SourceException failure = assertThrows(SourceException.class, () -> open(fixture));
        assertEquals(code, failure.terminal().code(), () -> failure.terminal().toString());
        assertEquals(contextValue, failure.terminal().context().get(contextKey));
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(offset, value);
    }

    private static void putShort(byte[] bytes, int offset, int value) {
        java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort(offset, (short) value);
    }

    private static void putDouble(byte[] bytes, int offset, double value) {
        java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putDouble(offset, value);
    }

    private static RasterSource open(byte[] bytes) {
        return GeoTiffFiles.openRaster(IDENTITY, bytes, GeoTiffRasterOptions.defaults());
    }

    private static RasterRequest request(RasterWindow window, int width, int height) {
        return new RasterRequest(window, width, height, Optional.empty());
    }

    private static int gray(int value) {
        return (value << 24) | (value << 16) | (value << 8) | 0xff;
    }
}
