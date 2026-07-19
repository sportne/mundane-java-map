package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.ElevationQueries;
import io.github.mundanej.map.core.ElevationRasterization;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoTiffElevationTest {
    private static final SourceIdentity ID = new SourceIdentity("terrain", "Terrain");
    private static final GeoTiffElevationOptions OPTIONS =
            GeoTiffElevationOptions.of(ElevationUnit.METRE);

    @Test
    void decodesSignedIntegerProfilesAcrossByteOrdersLayoutsAndCompression() {
        for (ByteOrder order : List.of(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            for (int bits : List.of(16, 32)) {
                for (boolean tiled : List.of(false, true)) {
                    for (int compression : List.of(1, 8, 32773)) {
                        try (ElevationSource source =
                                open(GeoTiffFixtures.elevation(order, bits, tiled, compression))) {
                            int width = tiled ? 17 : 4;
                            int height = tiled ? 17 : 3;
                            double multiplier = bits == 16 ? 1 : 100_000;
                            assertEquals(width, source.metadata().columnCount());
                            assertEquals(height, source.metadata().rowCount());
                            assertEquals(
                                    new Envelope(
                                            10,
                                            20 - (height - 1) * 0.25,
                                            10 + (width - 1) * 0.5,
                                            20),
                                    source.metadata().sampleBounds());
                            assertEquals(ElevationUnit.METRE, source.metadata().elevationUnit());
                            assertEquals(
                                    GeoTiffFixtures.elevationValue(width - 1, height - 1, width)
                                            * multiplier,
                                    source.sample(width - 1, height - 1).orElseThrow());
                        }
                    }
                }
            }
        }
    }

    @Test
    void pathBytesMutationQueriesColorizationAndLifecycleAreIntegrated(@TempDir Path directory)
            throws Exception {
        byte[] encoded = GeoTiffFixtures.elevation(ByteOrder.LITTLE_ENDIAN, 16, false, 1);
        Path path = directory.resolve("terrain.tif");
        Files.write(path, encoded);
        try (ElevationSource bytes = open(encoded);
                ElevationSource file = GeoTiffFiles.openElevation(ID, path, OPTIONS)) {
            encoded[encoded.length - 1] ^= 0x7f;
            assertEquals(file.metadata(), bytes.metadata());
            assertEquals(file.sample(3, 2), bytes.sample(3, 2));
            assertEquals(
                    -997,
                    ElevationQueries.query(
                                    bytes,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(10.5, 19.875),
                                    ElevationQueryMode.BILINEAR)
                            .orElseThrow()
                            .value());
            assertEquals(
                    -995,
                    ElevationQueries.query(
                                    bytes,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(10.5, 19.75),
                                    ElevationQueryMode.NEAREST)
                            .orElseThrow()
                            .value());
            var plan =
                    ElevationRasterization.plan(
                                    bytes.metadata(),
                                    bytes.metadata().sampleBounds(),
                                    0.5,
                                    RasterInterpolation.NEAREST,
                                    RasterRequestLimits.LEVEL_1)
                            .orElseThrow();
            var style =
                    ElevationRasterStyle.of(
                            new ElevationColorRamp(
                                    ElevationUnit.METRE,
                                    List.of(
                                            new ElevationColorStop(-1000, Rgba.rgb(0, 0, 0)),
                                            new ElevationColorStop(
                                                    -989, Rgba.rgb(255, 255, 255)))));
            assertTrue(
                    ElevationRasterization.rasterize(bytes, plan, style, CancellationToken.none())
                                    .pixels()
                                    .rgba()
                                    .length
                            > 0);
        }
        ElevationSource closed =
                open(GeoTiffFixtures.elevation(ByteOrder.LITTLE_ENDIAN, 16, false, 1));
        closed.close();
        closed.close();
        assertTrue(closed.isClosed());
        assertThrows(IllegalStateException.class, () -> closed.sample(0, 0));
    }

    @Test
    void enforcesExplicitRoutesSourceLimitsAndCancellationWithoutPartialPublication() {
        byte[] elevation = GeoTiffFixtures.elevation(ByteOrder.LITTLE_ENDIAN, 16, false, 1);
        GeoTiffLimits rasterRouteLimits =
                GeoTiffLimits.defaults()
                        .withMaximumDecodedSegmentBytes(16)
                        .withMaximumWorkingBytes(768);
        SourceException wrongRaster =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        ID,
                                        elevation,
                                        GeoTiffRasterOptions.defaults()
                                                .withFormatLimits(rasterRouteLimits)));
        assertEquals("route", wrongRaster.terminal().context().get("construct"));
        GeoTiffElevationOptions elevationRouteOptions =
                OPTIONS.withFormatLimits(
                                GeoTiffLimits.defaults()
                                        .withMaximumDecodedSegmentBytes(12)
                                        .withMaximumWorkingBytes(832))
                        .withSourceLimits(new ElevationSourceLimits(1, 1, 1, 1, 1));
        SourceException wrongElevation =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID, GeoTiffFixtures.areaGray(), elevationRouteOptions));
        assertEquals("route", wrongElevation.terminal().context().get("construct"));

        var small = new ElevationSourceLimits(3, 3, 9, 80, 1);
        SourceException limited =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID, elevation, OPTIONS.withSourceLimits(small)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limited.terminal().code());
        assertEquals("columns", limited.terminal().context().get("limit"));

        CancellationSource before = new CancellationSource();
        before.cancel();
        assertEquals(
                "SOURCE_CANCELLED",
                assertThrows(
                                SourceException.class,
                                () ->
                                        GeoTiffFiles.openElevation(
                                                ID, elevation, OPTIONS, before.token()))
                        .terminal()
                        .code());
        AtomicInteger checks = new AtomicInteger();
        CancellationToken duringDecode =
                () -> {
                    boolean decoding =
                            StackWalker.getInstance()
                                    .walk(
                                            frames ->
                                                    frames.anyMatch(
                                                            frame ->
                                                                    frame.getClassName()
                                                                            .endsWith(
                                                                                    "GeoTiffSegmentDecoder")));
                    return decoding && checks.incrementAndGet() >= 2;
                };
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        GeoTiffFixtures.elevation(
                                                ByteOrder.LITTLE_ENDIAN, 16, true, 8),
                                        OPTIONS,
                                        duringDecode));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals("geoTiffOpen", cancelled.terminal().context().get("operation"));
        assertTrue(checks.get() >= 2);
        try (ElevationSource source = open(elevation)) {
            assertFalse(source.isClosed());
        }
    }

    @Test
    void propagatesNonMetreUnitsAndPreflightsExactWorkingAndRetainedStorage() {
        byte[] fixture = GeoTiffFixtures.elevation(ByteOrder.LITTLE_ENDIAN, 16, false, 1);
        GeoTiffLimits exactFormat =
                GeoTiffLimits.defaults()
                        .withMaximumDecodedSegmentBytes(16)
                        .withMaximumEncodedSegmentBytes(16)
                        .withMaximumGeoAsciiBytes(2)
                        .withMaximumNoDataBytes(2)
                        .withMaximumTagPayloadBytes(48)
                        .withMaximumInputBytes(fixture.length)
                        .withMaximumWorkingBytes(1_016);
        ElevationSourceLimits exactSource = new ElevationSourceLimits(4, 3, 12, 104, 1);
        GeoTiffElevationOptions exact =
                GeoTiffElevationOptions.of(ElevationUnit.INTERNATIONAL_FOOT)
                        .withFormatLimits(exactFormat)
                        .withSourceLimits(exactSource);
        try (ElevationSource source = GeoTiffFiles.openElevation(ID, fixture, exact)) {
            assertEquals(ElevationUnit.INTERNATIONAL_FOOT, source.metadata().elevationUnit());
            assertEquals(exactSource, source.limits());
        }
        SourceException working =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        fixture,
                                        exact.withFormatLimits(
                                                exactFormat.withMaximumWorkingBytes(1_015))));
        assertEquals("workingBytes", working.terminal().context().get("limit"));
        assertEquals("1016", working.terminal().context().get("requested"));
        SourceException retained =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        fixture,
                                        exact.withSourceLimits(
                                                new ElevationSourceLimits(4, 3, 12, 103, 1))));
        assertEquals("retainedSampleBytes", retained.terminal().context().get("limit"));
        assertEquals("104", retained.terminal().context().get("requested"));
    }

    @Test
    void cancellationArbitratesBeforeTemporaryStorageBeforeCopyAndExactlyOnceAfterCopy() {
        byte[] fixture = GeoTiffFixtures.elevation(ByteOrder.LITTLE_ENDIAN, 16, false, 1);
        assertElevationPhaseCancellation(fixture, 1);
        assertElevationPhaseCancellation(fixture, 10);
        AtomicInteger checks = new AtomicInteger();
        CancellationToken nonMonotonicAfterCopy = elevationParserCheck(checks, 11);
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID, fixture, OPTIONS, nonMonotonicAfterCopy));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals("geoTiffOpen", cancelled.terminal().context().get("operation"));
        assertEquals(11, checks.get());
    }

    private static void assertElevationPhaseCancellation(byte[] fixture, int target) {
        AtomicInteger checks = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        fixture,
                                        OPTIONS,
                                        elevationParserCheck(checks, target)));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals("geoTiffOpen", cancelled.terminal().context().get("operation"));
        assertEquals(target, checks.get());
    }

    private static CancellationToken elevationParserCheck(AtomicInteger checks, int target) {
        return () -> {
            boolean parserElevation =
                    StackWalker.getInstance()
                            .walk(
                                    frames -> {
                                        List<StackWalker.StackFrame> stack = frames.toList();
                                        return stack.stream()
                                                        .anyMatch(
                                                                frame ->
                                                                        frame.getClassName()
                                                                                        .equals(
                                                                                                GeoTiffParser
                                                                                                        .class
                                                                                                        .getName())
                                                                                && frame.getMethodName()
                                                                                        .equals(
                                                                                                "decodeElevation"))
                                                && stack.stream()
                                                        .noneMatch(
                                                                frame ->
                                                                        frame.getClassName()
                                                                                .equals(
                                                                                        GeoTiffSegmentDecoder
                                                                                                .class
                                                                                                .getName()));
                                    });
            return parserElevation && checks.incrementAndGet() == target;
        };
    }

    private static ElevationSource open(byte[] encoded) {
        return GeoTiffFiles.openElevation(ID, encoded, OPTIONS);
    }
}
