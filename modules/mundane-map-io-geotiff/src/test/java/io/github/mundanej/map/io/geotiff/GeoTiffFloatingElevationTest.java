package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationHillshade;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.ElevationQueries;
import io.github.mundanej.map.core.ElevationRasterization;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeoTiffFloatingElevationTest {
    private static final SourceIdentity ID = new SourceIdentity("floating-terrain", "Terrain");
    private static final GeoTiffElevationOptions OPTIONS =
            GeoTiffElevationOptions.of(ElevationUnit.METRE);

    @Test
    void decodesFloatProfilesAcrossByteOrdersLayoutsAndCompression() {
        for (ByteOrder order : List.of(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            for (int bits : List.of(32, 64)) {
                for (boolean tiled : List.of(false, true)) {
                    for (int compression : List.of(1, 8, 32773)) {
                        try (ElevationSource source =
                                open(
                                        GeoTiffFixtures.floatingElevation(
                                                order, bits, tiled, compression, null, null))) {
                            int width = tiled ? 17 : 4;
                            int height = tiled ? 17 : 3;
                            assertEquals(
                                    GeoTiffFixtures.floatingElevationValue(
                                            width - 1, height - 1, width),
                                    source.sample(width - 1, height - 1).orElseThrow());
                            assertFalse(source.sample(0, 0).isEmpty());
                        }
                    }
                }
            }
        }
    }

    @Test
    void finiteAndNanPoliciesMaskOnlyDeclaredValuesAndNormalizeNegativeZero() {
        try (ElevationSource finite =
                        open(
                                GeoTiffFixtures.floatingElevation(
                                        ByteOrder.LITTLE_ENDIAN, 32, false, 1, "-3.5", -3.5));
                ElevationSource nan =
                        open(
                                GeoTiffFixtures.floatingElevation(
                                        ByteOrder.BIG_ENDIAN, 64, false, 8, "nan", Double.NaN));
                ElevationSource zero =
                        open(
                                GeoTiffFixtures.floatingElevation(
                                        ByteOrder.LITTLE_ENDIAN, 64, false, 32773, null, -0.0))) {
            assertTrue(finite.sample(1, 1).isEmpty());
            assertTrue(nan.sample(1, 1).isEmpty());
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(zero.sample(1, 1).orElseThrow()));
            assertTrue(
                    ElevationQueries.query(
                                    finite,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(10.5, 19.75),
                                    ElevationQueryMode.NEAREST)
                            .isEmpty());
            assertTrue(
                    ElevationQueries.query(
                                    finite,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(10.25, 19.875),
                                    ElevationQueryMode.BILINEAR)
                            .isEmpty());
        }
    }

    @Test
    void appliesExactFiniteNoDataAcrossEveryDeclaredSampleType() {
        for (var fixture :
                List.of(
                        GeoTiffFixtures.integerElevationWithNoData(16, "-995"),
                        GeoTiffFixtures.integerElevationWithNoData(32, "-99500000"),
                        GeoTiffFixtures.floatingElevation(
                                ByteOrder.LITTLE_ENDIAN, 32, false, 1, "-3.5", -3.5),
                        GeoTiffFixtures.floatingElevation(
                                ByteOrder.BIG_ENDIAN, 64, false, 1, "-3.5", -3.5))) {
            try (ElevationSource source = open(fixture)) {
                assertTrue(source.sample(1, 1).isEmpty());
                assertFalse(source.sample(0, 0).isEmpty());
            }
        }
        for (String token : List.of("32768", "-32769")) {
            assertTagValue(GeoTiffFixtures.integerElevationWithNoData(16, token));
        }
        for (String token : List.of("2147483648", "-2147483649")) {
            assertTagValue(GeoTiffFixtures.integerElevationWithNoData(32, token));
        }
    }

    @Test
    void rejectsNonzeroFloatingUnderflowButAcceptsMathematicalZeroSpellings() {
        for (int bits : List.of(32, 64)) {
            String exponent = bits == 32 ? "50" : "400";
            for (String token : List.of("1e-" + exponent, "-1e-" + exponent, "1E-" + exponent)) {
                assertTagValue(
                        GeoTiffFixtures.floatingElevation(
                                ByteOrder.LITTLE_ENDIAN, bits, false, 1, token, -0.0));
            }
            for (String token : List.of("0", "-0", "0e-999", "-0.000E+999")) {
                try (ElevationSource source =
                        open(
                                GeoTiffFixtures.floatingElevation(
                                        ByteOrder.LITTLE_ENDIAN, bits, false, 1, token, -0.0))) {
                    assertTrue(source.sample(1, 1).isEmpty());
                }
            }
        }
    }

    @Test
    void rejectsMalformedNoDataAndUnmaskedNonFiniteSamplesWithStableDiagnostics() {
        for (String token : List.of("", "NaN", "Infinity", "1f", "1e999", "\0x", "\u0080")) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    open(
                                            GeoTiffFixtures.floatingElevation(
                                                    ByteOrder.LITTLE_ENDIAN,
                                                    32,
                                                    false,
                                                    1,
                                                    token,
                                                    null)));
            assertEquals("GEOTIFF_TAG_INVALID", failure.terminal().code());
            assertEquals("42113", failure.terminal().context().get("tag"));
        }
        for (double value : List.of(Double.NaN, Double.POSITIVE_INFINITY)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    open(
                                            GeoTiffFixtures.floatingElevation(
                                                    ByteOrder.BIG_ENDIAN,
                                                    64,
                                                    true,
                                                    8,
                                                    null,
                                                    value)));
            assertEquals("GEOTIFF_SAMPLE_INVALID", failure.terminal().code());
            assertEquals("nonFinite", failure.terminal().context().get("reason"));
            assertEquals("0", failure.terminal().context().get("segment"));
        }
        SourceException infinityUnderNan =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        GeoTiffFixtures.floatingElevation(
                                                ByteOrder.LITTLE_ENDIAN,
                                                64,
                                                false,
                                                1,
                                                "nan",
                                                Double.NEGATIVE_INFINITY)));
        assertEquals("GEOTIFF_SAMPLE_INVALID", infinityUnderNan.terminal().code());
    }

    @Test
    void noDataLimitAndWrongRasterRoutePrecedeSampleAllocation() {
        byte[] nan =
                GeoTiffFixtures.floatingElevation(
                        ByteOrder.LITTLE_ENDIAN, 32, false, 1, "nan", Double.NaN);
        GeoTiffElevationOptions exact =
                OPTIONS.withFormatLimits(GeoTiffLimits.defaults().withMaximumNoDataBytes(4));
        try (ElevationSource source = GeoTiffFiles.openElevation(ID, nan, exact)) {
            assertTrue(source.sample(1, 1).isEmpty());
        }
        SourceException limited =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        nan,
                                        exact.withFormatLimits(
                                                exact.formatLimits().withMaximumNoDataBytes(3))));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limited.terminal().code());
        assertEquals("noDataBytes", limited.terminal().context().get("limit"));

        SourceException wrongRoute =
                assertThrows(
                        SourceException.class,
                        () -> GeoTiffFiles.openRaster(ID, nan, GeoTiffRasterOptions.defaults()));
        assertEquals("route", wrongRoute.terminal().context().get("construct"));
        SourceException rasterNoData =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        ID,
                                        GeoTiffFixtures.floatingAreaWithNoData("nan"),
                                        GeoTiffRasterOptions.defaults()));
        assertEquals("rasterNoData", rasterNoData.terminal().context().get("construct"));
    }

    @Test
    void validatesNoDataTerminationTypeCountMaskAccountingAndAllocationCancellation() {
        assertTagReason(
                GeoTiffFixtures.floatingElevationWithRawNoData(32, new byte[] {'n', 'a', 'n'}),
                "encoding");
        assertTagReason(
                GeoTiffFixtures.floatingElevationWithRawNoData(32, new byte[] {0}), "count");
        assertTagReason(GeoTiffFixtures.floatingElevationWithWrongNoDataType(), "type");

        byte[] fixture =
                GeoTiffFixtures.floatingElevation(
                        ByteOrder.LITTLE_ENDIAN, 32, false, 1, "nan", Double.NaN);
        GeoTiffLimits exactLimits =
                GeoTiffLimits.defaults()
                        .withMaximumDecodedSegmentBytes(32)
                        .withMaximumEncodedSegmentBytes(32)
                        .withMaximumGeoAsciiBytes(2)
                        .withMaximumNoDataBytes(4)
                        .withMaximumTagPayloadBytes(48)
                        .withMaximumInputBytes(fixture.length)
                        .withMaximumWorkingBytes(1_110);
        GeoTiffElevationOptions exact = OPTIONS.withFormatLimits(exactLimits);
        try (ElevationSource source = GeoTiffFiles.openElevation(ID, fixture, exact)) {
            assertTrue(source.sample(1, 1).isEmpty());
        }
        SourceException over =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        fixture,
                                        exact.withFormatLimits(
                                                exactLimits.withMaximumWorkingBytes(1_109))));
        assertEquals("workingBytes", over.terminal().context().get("limit"));
        assertEquals("1110", over.terminal().context().get("requested"));

        AtomicInteger maskPhaseChecks = new AtomicInteger();
        CancellationToken beforeMask =
                () -> {
                    boolean elevationDecode =
                            StackWalker.getInstance()
                                    .walk(
                                            frames ->
                                                    frames.anyMatch(
                                                            frame ->
                                                                    frame.getClassName()
                                                                                    .equals(
                                                                                            GeoTiffParser
                                                                                                    .class
                                                                                                    .getName())
                                                                            && frame.getMethodName()
                                                                                    .equals(
                                                                                            "decodeElevation")));
                    return elevationDecode && maskPhaseChecks.incrementAndGet() == 2;
                };
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () -> GeoTiffFiles.openElevation(ID, fixture, OPTIONS, beforeMask));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals("geoTiffOpen", cancelled.terminal().context().get("operation"));
        assertEquals(2, maskPhaseChecks.get());
    }

    @Test
    void compressedTiledColorAndHillshadeAreDeterministicWithMaskedSamples() {
        int[][] rendered = new int[3][];
        int index = 0;
        for (int compression : List.of(1, 8, 32773)) {
            try (ElevationSource source =
                    open(
                            GeoTiffFixtures.floatingElevation(
                                    ByteOrder.LITTLE_ENDIAN,
                                    32,
                                    true,
                                    compression,
                                    "nan",
                                    Double.NaN))) {
                var plan =
                        ElevationRasterization.plan(
                                        source.metadata(),
                                        source.metadata().sampleBounds(),
                                        0.5,
                                        RasterInterpolation.BILINEAR,
                                        RasterRequestLimits.LEVEL_1)
                                .orElseThrow();
                ElevationRasterStyle style =
                        ElevationRasterStyle.of(
                                        new ElevationColorRamp(
                                                ElevationUnit.METRE,
                                                List.of(
                                                        new ElevationColorStop(
                                                                -5, Rgba.rgb(10, 30, 80)),
                                                        new ElevationColorStop(
                                                                300, Rgba.rgb(240, 230, 180)))))
                                .withHillshade(ElevationHillshade.defaults());
                rendered[index++] =
                        ElevationRasterization.rasterize(
                                        source, plan, style, CancellationToken.none())
                                .pixels()
                                .rgba();
            }
        }
        assertArrayEquals(rendered[0], rendered[1]);
        assertArrayEquals(rendered[0], rendered[2]);
    }

    private static ElevationSource open(byte[] encoded) {
        return GeoTiffFiles.openElevation(ID, encoded, OPTIONS);
    }

    private static void assertTagValue(byte[] fixture) {
        assertTagReason(fixture, "value");
    }

    private static void assertTagReason(byte[] fixture, String reason) {
        SourceException failure = assertThrows(SourceException.class, () -> open(fixture));
        assertEquals("GEOTIFF_TAG_INVALID", failure.terminal().code());
        assertEquals("42113", failure.terminal().context().get("tag"));
        assertEquals(reason, failure.terminal().context().get("reason"));
    }
}
