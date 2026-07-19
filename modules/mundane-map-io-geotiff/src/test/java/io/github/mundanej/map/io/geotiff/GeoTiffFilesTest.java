package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterGridPlacement;
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
                936,
                935,
                limits -> limits.withMaximumWorkingBytes(936),
                limits -> limits.withMaximumWorkingBytes(935));
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
            assertEquals("geoTiffRead", readCancelled.terminal().context().get("operation"));
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

    @Test
    void packBitsAndDeflateMatchUncompressedWindowsExactly() {
        assertRasterParity(GeoTiffFixtures.tiledRgb(), GeoTiffFixtures.packBitsTiledRgb());
        assertRasterParity(GeoTiffFixtures.rgba(), GeoTiffFixtures.deflateRgba());

        byte[] repeated = {(byte) -7, 42};
        try (RasterSource source = open(GeoTiffFixtures.compressedGray(32773, repeated))) {
            assertArrayEquals(
                    new int[] {
                        gray(42), gray(42), gray(42), gray(42),
                        gray(42), gray(42), gray(42), gray(42)
                    },
                    source.read(
                                    request(new RasterWindow(0, 0, 4, 2), 4, 2),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void classifiesEveryClosedPackBitsAndDeflateFailure() {
        assertDecodeFailure(32773, new byte[] {0}, "packet");
        assertDecodeFailure(32773, new byte[] {2, 0, 11, 22}, "truncated");
        assertDecodeFailure(32773, new byte[] {(byte) -9, 42}, "overrun");

        byte[] eight = {0, 11, 22, 33, 17, 28, 39, 50};
        byte[] seven = {0, 11, 22, 33, 17, 28, 39};
        byte[] nine = {0, 11, 22, 33, 17, 28, 39, 50, 61};
        assertDecodeFailure(8, GeoTiffFixtures.deflateWithDictionary(eight), "dictionary");
        assertDecodeFailure(8, GeoTiffFixtures.deflate(seven), "truncated");
        byte[] unfinished = GeoTiffFixtures.deflate(eight);
        assertDecodeFailure(
                8, java.util.Arrays.copyOf(unfinished, unfinished.length - 1), "unfinished");
        assertDecodeFailure(8, GeoTiffFixtures.deflate(nine), "overrun");
        byte[] trailing =
                java.util.Arrays.copyOf(GeoTiffFixtures.deflate(eight), unfinished.length + 1);
        trailing[trailing.length - 1] = 1;
        assertDecodeFailure(8, trailing, "trailing");
        byte[] stream = GeoTiffFixtures.deflate(eight);
        byte[] concatenated = java.util.Arrays.copyOf(stream, stream.length * 2);
        System.arraycopy(stream, 0, concatenated, stream.length, stream.length);
        assertDecodeFailure(8, concatenated, "trailing");
        assertDecodeFailure(8, new byte[] {1, 2, 3, 4}, "packet");
    }

    @Test
    void rejectsUnapprovedCompressionAndReusesSourceAfterDecodeFailure() {
        byte[] lzw = GeoTiffFixtures.areaGray();
        putShortAtEntryValue(lzw, 259, 5);
        assertFailure(lzw, "GEOTIFF_PROFILE_UNSUPPORTED", "compression", "5");
        byte[] oldDeflate = GeoTiffFixtures.areaGray();
        putShortAtEntryValue(oldDeflate, 259, 32946);
        assertFailure(oldDeflate, "GEOTIFF_PROFILE_UNSUPPORTED", "compression", "32946");

        try (RasterSource source =
                open(GeoTiffFixtures.packBitsTiledRgbWithFirstSegment(new byte[] {0}))) {
            SourceException malformed =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                            CancellationToken.none()));
            assertEquals("GEOTIFF_DECODE_FAILED", malformed.terminal().code());
            assertEquals("packet", malformed.terminal().context().get("reason"));
            assertArrayEquals(
                    new int[] {GeoTiffFixtures.expectedRgba(16, 16, 2, 3)},
                    source.read(
                                    request(new RasterWindow(16, 16, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void preflightsCompressedLimitsAndCancellationLeavesSourceReusable() {
        byte[] encoded = GeoTiffFixtures.deflate(new byte[] {0, 11, 22, 33, 17, 28, 39, 50});
        byte[] fixture = GeoTiffFixtures.compressedGray(8, encoded);
        GeoTiffLimits exactLimits =
                GeoTiffLimits.defaults()
                        .withMaximumEncodedSegmentBytes(encoded.length)
                        .withMaximumDecodedSegmentBytes(8);
        try (RasterSource source =
                GeoTiffFiles.openRaster(
                        IDENTITY,
                        fixture,
                        GeoTiffRasterOptions.defaults().withFormatLimits(exactLimits))) {
            assertArrayEquals(
                    new int[] {gray(0)},
                    source.read(
                                    request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
        assertCompressedLimit(
                fixture,
                exactLimits.withMaximumEncodedSegmentBytes(encoded.length - 1),
                "encodedSegmentBytes",
                encoded.length,
                encoded.length - 1);
        assertCompressedLimit(
                fixture,
                exactLimits.withMaximumDecodedSegmentBytes(7),
                "decodedSegmentBytes",
                8,
                7);

        byte[] tiled = GeoTiffFixtures.packBitsTiledRgb();
        GeoTiffLimits exactWorking =
                GeoTiffLimits.defaults()
                        .withMaximumDecodedSegmentBytes(768)
                        .withMaximumWorkingBytes(1_924);
        try (RasterSource source =
                GeoTiffFiles.openRaster(
                        IDENTITY,
                        tiled,
                        GeoTiffRasterOptions.defaults().withFormatLimits(exactWorking))) {
            assertEquals(
                    17 * 17,
                    source.read(
                                    request(new RasterWindow(0, 0, 17, 17), 17, 17),
                                    CancellationToken.none())
                            .pixels()
                            .rgba()
                            .length);
        }
        try (RasterSource source =
                GeoTiffFiles.openRaster(
                        IDENTITY,
                        tiled,
                        GeoTiffRasterOptions.defaults()
                                .withFormatLimits(exactWorking.withMaximumWorkingBytes(1_923)))) {
            SourceException working =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(0, 0, 17, 17), 17, 17),
                                            CancellationToken.none()));
            assertEquals("SOURCE_LIMIT_EXCEEDED", working.terminal().code());
            assertEquals("workingBytes", working.terminal().context().get("limit"));
            assertEquals("1924", working.terminal().context().get("requested"));
            assertEquals("1923", working.terminal().context().get("maximum"));
        }

        try (RasterSource source = open(GeoTiffFixtures.packBitsTiledRgb())) {
            AtomicInteger codecChecks = new AtomicInteger();
            CancellationToken duringCodec =
                    () -> {
                        boolean decoding =
                                java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                                        .anyMatch(
                                                frame ->
                                                        frame.getClassName()
                                                                .equals(
                                                                        GeoTiffSegmentDecoder.class
                                                                                .getName()));
                        return decoding && codecChecks.incrementAndGet() >= 2;
                    };
            SourceException cancelled =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(0, 0, 16, 16), 16, 16),
                                            duringCodec));
            assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
            assertArrayEquals(
                    new int[] {GeoTiffFixtures.expectedRgba(16, 16, 2, 3)},
                    source.read(
                                    request(new RasterWindow(16, 16, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    @Test
    void mapsAffineCellCornersToPixelCentersAndPreservesOuterBounds() {
        double[] geographic = affineMatrix(10, 20);
        try (RasterSource source = open(GeoTiffFixtures.affineRgb(false, geographic))) {
            assertEquals(
                    RasterGridPlacement.Kind.AFFINE,
                    source.metadata().gridPlacement().orElseThrow().kind());
            var transform =
                    source.metadata().gridPlacement().orElseThrow().affineTransform().orElseThrow();
            assertEquals(2.0, transform.a());
            assertEquals(0.25, transform.d());
            assertEquals(0.5, transform.b());
            assertEquals(-1.5, transform.e());
            assertEquals(11.25, transform.c());
            assertEquals(19.375, transform.f());
            assertEquals(
                    Optional.of(new Envelope(10, 15.5, 19.5, 21)), source.metadata().mapBounds());
            assertEquals(10.0, transform.gridToMap(-0.5, -0.5).x());
            assertEquals(20.0, transform.gridToMap(-0.5, -0.5).y());
            assertArrayEquals(
                    new int[] {GeoTiffFixtures.expectedRgba(2, 1, 2, 3)},
                    source.read(
                                    request(new RasterWindow(2, 1, 1, 1), 1, 1),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }

        try (RasterSource source =
                open(GeoTiffFixtures.affineRgb(true, affineMatrix(1_000, 2_000)))) {
            assertEquals(
                    "EPSG:3857",
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            assertEquals(
                    Optional.of(new Envelope(1_000, 1_995.5, 1_009.5, 2_001)),
                    source.metadata().mapBounds());
        }

        double[] northUp = affineMatrix(10, 20);
        northUp[1] = 0;
        northUp[4] = 0;
        try (RasterSource source = open(GeoTiffFixtures.affineRgb(false, northUp))) {
            assertEquals(
                    RasterGridPlacement.Kind.AXIS_ALIGNED,
                    source.metadata().gridPlacement().orElseThrow().kind());
            assertEquals(
                    Optional.of(new Envelope(10, 15.5, 18, 20)), source.metadata().mapBounds());
        }
    }

    @Test
    void classifiesAffineConflictsMatrixShapeAndPlacementFailures() {
        double[] valid = affineMatrix(10, 20);
        assertFailure(
                GeoTiffFixtures.affineRgb(false, valid, true),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "conflict");

        byte[] wrongType = GeoTiffFixtures.affineRgb(false, valid);
        putEntryType(wrongType, 34264, 4);
        assertFailure(wrongType, "GEOTIFF_TAG_INVALID", "reason", "type");
        byte[] wrongCount = GeoTiffFixtures.affineRgb(false, valid);
        putEntryCount(wrongCount, 34264, 15);
        assertFailure(wrongCount, "GEOTIFF_TAG_INVALID", "reason", "count");

        double[] nonFinite = valid.clone();
        nonFinite[0] = Double.NaN;
        assertFailure(
                GeoTiffFixtures.affineRgb(false, nonFinite),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "nonFinite");
        double[] perspective = valid.clone();
        perspective[12] = 0.01;
        assertFailure(
                GeoTiffFixtures.affineRgb(false, perspective),
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "construct",
                "georeference");
        double[] zCoupling = valid.clone();
        zCoupling[2] = 1;
        assertFailure(
                GeoTiffFixtures.affineRgb(false, zCoupling),
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "construct",
                "georeference");
        double[] singular = valid.clone();
        singular[4] = 4;
        singular[5] = 1;
        assertFailure(
                GeoTiffFixtures.affineRgb(false, singular),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "singular");
        double[] collapsed = valid.clone();
        collapsed[3] = 1e300;
        collapsed[7] = 1e300;
        assertFailure(
                GeoTiffFixtures.affineRgb(false, collapsed),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "collapsed");
        double[] outsideCrs = valid.clone();
        outsideCrs[3] = 180;
        assertFailure(
                GeoTiffFixtures.affineRgb(false, outsideCrs),
                "GEOTIFF_GEOREFERENCE_INVALID",
                "reason",
                "orientation");
    }

    private static double[] affineMatrix(double translationX, double translationY) {
        return new double[] {
            2, 0.5, 0, translationX, 0.25, -1.5, 0, translationY, 0, 0, 1, 0, 0, 0, 0, 1
        };
    }

    private static void assertRasterParity(byte[] uncompressed, byte[] compressed) {
        try (RasterSource expected = open(uncompressed);
                RasterSource actual = open(compressed)) {
            RasterWindow window =
                    new RasterWindow(
                            0, 0, expected.metadata().width(), expected.metadata().height());
            assertArrayEquals(
                    expected.read(
                                    request(window, window.width(), window.height()),
                                    CancellationToken.none())
                            .pixels()
                            .rgba(),
                    actual.read(
                                    request(window, window.width(), window.height()),
                                    CancellationToken.none())
                            .pixels()
                            .rgba());
        }
    }

    private static void assertDecodeFailure(int compression, byte[] encoded, String reason) {
        try (RasterSource source = open(GeoTiffFixtures.compressedGray(compression, encoded))) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    source.read(
                                            request(new RasterWindow(0, 0, 4, 2), 4, 2),
                                            CancellationToken.none()));
            assertEquals("GEOTIFF_DECODE_FAILED", failure.terminal().code());
            assertEquals("0", failure.terminal().context().get("segment"));
            assertEquals(
                    Integer.toString(compression), failure.terminal().context().get("compression"));
            assertEquals(reason, failure.terminal().context().get("reason"));
            assertFalse(source.isClosed());
        }
    }

    private static void assertCompressedLimit(
            byte[] fixture, GeoTiffLimits limits, String limit, long requested, long maximum) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        IDENTITY,
                                        fixture,
                                        GeoTiffRasterOptions.defaults().withFormatLimits(limits)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(requested), failure.terminal().context().get("requested"));
        assertEquals(Long.toString(maximum), failure.terminal().context().get("maximum"));
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

    private static void putEntryType(byte[] bytes, int tag, int type) {
        java.nio.ByteBuffer buffer = ordered(bytes);
        buffer.putShort(entryOffset(buffer, tag) + 2, (short) type);
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
