package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoTiffHardeningTest {
    private static final SourceIdentity ID = new SourceIdentity("hardening", "Hardening");

    @TempDir Path temporaryDirectory;

    @Test
    void validatesBenignAncillaryTagsAndBoundedCitationCoverage() {
        try (RasterSource source = openRaster(GeoTiffFixtures.ancillaryRaster())) {
            assertEquals(4, source.metadata().width());
            assertTrue(source.openingDiagnostics().entries().isEmpty());
        }
        byte[] citation = GeoTiffFixtures.citationRaster("citation|", 0, 9);
        GeoTiffRasterOptions exact =
                GeoTiffRasterOptions.defaults()
                        .withFormatLimits(GeoTiffLimits.defaults().withMaximumGeoAsciiBytes(10));
        try (RasterSource source = GeoTiffFiles.openRaster(ID, citation, exact)) {
            assertEquals(3, source.metadata().height());
        }
        SourceException bounded =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        ID,
                                        citation,
                                        exact.withFormatLimits(
                                                exact.formatLimits().withMaximumGeoAsciiBytes(9))));
        assertEquals("SOURCE_LIMIT_EXCEEDED", bounded.terminal().code());
        assertEquals("geoAsciiBytes", bounded.terminal().context().get("limit"));

        assertGeoKeyRange(GeoTiffFixtures.citationRaster("citation|", 1, 8));
        assertGeoKeyRange(GeoTiffFixtures.citationRaster("citation|", 0, 8));
        assertGeoKeyRange(GeoTiffFixtures.citationRaster("citation|", 1, 9));
        assertGeoKeyRange(GeoTiffFixtures.twoCitationRaster("first|second|", 0, 6, 5, 7), 2049);
        try (RasterSource source =
                openRaster(GeoTiffFixtures.twoCitationRaster("first|second|", 0, 6, 6, 7))) {
            assertEquals(4, source.metadata().width());
        }

        SourceException terminator = failure(GeoTiffFixtures.citationRaster("citation", 0, 8));
        assertEquals("GEOTIFF_GEOKEY_INVALID", terminator.terminal().code());
        assertEquals("range", terminator.terminal().context().get("reason"));
        SourceException control = failure(GeoTiffFixtures.citationRaster("bad\n|", 0, 5));
        assertEquals("GEOTIFF_GEOKEY_INVALID", control.terminal().code());
        assertEquals("value", control.terminal().context().get("reason"));
    }

    @Test
    void classifiesAncillaryValuesInlinePaddingAndUnknownTagsExactly() {
        for (int value : new int[] {1, 4, 2}) {
            byte[] fixture = GeoTiffFixtures.ancillaryRaster();
            putIntAtEntryValue(fixture, 254, value);
            SourceException failure = failure(fixture);
            assertEquals("GEOTIFF_PROFILE_UNSUPPORTED", failure.terminal().code());
            assertEquals(
                    value == 1 ? "overview" : value == 4 ? "mask" : "tag",
                    failure.terminal().context().get("construct"));
            assertEquals("254", failure.terminal().context().get("tag"));
        }

        byte[] fill = GeoTiffFixtures.ancillaryRaster();
        putShortAtEntryValue(fill, 266, 2);
        assertEquals("sampleOrganization", failure(fill).terminal().context().get("construct"));
        byte[] denominator = GeoTiffFixtures.ancillaryRaster();
        putRationalDenominator(denominator, 282, 0);
        assertTagFailure(denominator, 282, "value");
        byte[] unit = GeoTiffFixtures.ancillaryRaster();
        putShortAtEntryValue(unit, 296, 4);
        assertTagFailure(unit, 296, "value");
        byte[] padding = GeoTiffFixtures.ancillaryRaster();
        ordered(padding).put(entryOffset(ordered(padding), 296) + 10, (byte) 1);
        assertTagFailure(padding, 296, "value");
        assertTagFailure(GeoTiffFixtures.ancillaryAscii(new byte[] {0}), 269, "count");
        assertTagFailure(GeoTiffFixtures.ancillaryAscii(new byte[] {'a', 'b'}), 269, "encoding");
        assertTagFailure(
                GeoTiffFixtures.ancillaryAscii(new byte[] {'a', 0, 'b', 0}), 269, "encoding");

        byte[] unknown = GeoTiffFixtures.ancillaryRaster();
        putTagId(unknown, 269, 268);
        SourceException unknownFailure = failure(unknown);
        assertEquals("tag", unknownFailure.terminal().context().get("construct"));
        assertEquals("268", unknownFailure.terminal().context().get("tag"));
        byte[] geoDouble = GeoTiffFixtures.areaGray();
        putTagId(geoDouble, 34735, 34736);
        SourceException geoDoubleFailure = failure(geoDouble);
        assertEquals("geoDoubleParams", geoDoubleFailure.terminal().context().get("construct"));
        assertEquals("34736", geoDoubleFailure.terminal().context().get("tag"));

        byte[] unknownKey = GeoTiffFixtures.areaGray();
        putGeoKeyId(unknownKey, 2048, 2047);
        SourceException unknownKeyFailure = failure(unknownKey);
        assertEquals("geoKey", unknownKeyFailure.terminal().context().get("construct"));
        assertEquals("2047", unknownKeyFailure.terminal().context().get("key"));
        byte[] vertical = GeoTiffFixtures.areaGray();
        putGeoKeyId(vertical, 2048, 4096);
        SourceException verticalFailure = failure(vertical);
        assertEquals("verticalCrs", verticalFailure.terminal().context().get("construct"));
        assertEquals("4096", verticalFailure.terminal().context().get("key"));
    }

    @Test
    void deterministicSingleBitMutationNeverEscapesStructuredFailureOrOwnedSuccess() {
        assertTimeout(
                Duration.ofSeconds(10),
                () -> {
                    mutateEveryByte(GeoTiffFixtures.areaGray(), false);
                    mutateEveryByte(
                            GeoTiffFixtures.floatingElevation(
                                    ByteOrder.LITTLE_ENDIAN, 32, false, 1, "nan", Double.NaN),
                            true);
                });
    }

    @Test
    void everyCutTruncationIsBoundedAndStructuredAcrossBothRoutes() {
        assertTimeout(
                Duration.ofSeconds(10),
                () -> {
                    truncateAtEveryCut(GeoTiffFixtures.areaGray(), false);
                    truncateAtEveryCut(
                            GeoTiffFixtures.floatingElevation(
                                    ByteOrder.BIG_ENDIAN, 64, false, 8, "nan", Double.NaN),
                            true);
                });
    }

    @Test
    void diagnosticsDoNotLeakPathPayloadSentinelOrImplementationDetails() {
        String canary = "SECRET_CANARY_1947";
        SourceException token =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openElevation(
                                        ID,
                                        GeoTiffFixtures.floatingElevation(
                                                ByteOrder.LITTLE_ENDIAN,
                                                32,
                                                false,
                                                1,
                                                canary,
                                                null),
                                        GeoTiffElevationOptions.of(ElevationUnit.METRE)));
        assertDiagnosticIsClean(token, canary);
        SourceException path =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        ID,
                                        Path.of("/tmp/" + canary + ".tif"),
                                        GeoTiffRasterOptions.defaults()));
        assertDiagnosticIsClean(path, canary);
    }

    @Test
    void pathSnapshotRejectsAFileThatChangesWhileItIsRead() throws IOException {
        Path path = temporaryDirectory.resolve("changing.tif");
        Files.write(path, GeoTiffFixtures.areaGray());
        AtomicInteger checkpoints = new AtomicInteger();
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoTiffFiles.openRaster(
                                        ID,
                                        path,
                                        GeoTiffRasterOptions.defaults(),
                                        () -> {
                                            if (checkpoints.incrementAndGet() == 2) {
                                                appendByte(path);
                                            }
                                            return false;
                                        }));
        assertEquals("GEOTIFF_IO_FAILED", failure.terminal().code());
        assertEquals("read", failure.terminal().context().get("operation"));
        assertEquals("changed", failure.terminal().context().get("reason"));
    }

    private static void mutateEveryByte(byte[] fixture, boolean elevation) {
        for (int index = 0; index < fixture.length; index++) {
            byte[] candidate = fixture.clone();
            candidate[index] ^= 1;
            try {
                if (elevation) {
                    try (ElevationSource source =
                            GeoTiffFiles.openElevation(
                                    ID,
                                    candidate,
                                    GeoTiffElevationOptions.of(ElevationUnit.METRE))) {
                        assertFalse(source.isClosed());
                    }
                } else {
                    try (RasterSource source = openRaster(candidate)) {
                        assertFalse(source.isClosed());
                    }
                }
            } catch (SourceException expected) {
                assertDiagnosticIsClean(expected, "SECRET_CANARY_1947");
            }
        }
    }

    private static void truncateAtEveryCut(byte[] fixture, boolean elevation) {
        for (int cut = 0; cut <= fixture.length; cut++) {
            byte[] candidate = java.util.Arrays.copyOf(fixture, cut);
            try {
                if (elevation) {
                    try (ElevationSource source =
                            GeoTiffFiles.openElevation(
                                    ID,
                                    candidate,
                                    GeoTiffElevationOptions.of(ElevationUnit.METRE))) {
                        assertEquals(fixture.length, cut);
                        assertFalse(source.isClosed());
                    }
                } else {
                    try (RasterSource source = openRaster(candidate)) {
                        assertEquals(fixture.length, cut);
                        assertFalse(source.isClosed());
                    }
                }
            } catch (SourceException expected) {
                assertTrue(cut < fixture.length);
                assertDiagnosticIsClean(expected, "SECRET_CANARY_1947");
            }
        }
    }

    private static RasterSource openRaster(byte[] fixture) {
        return GeoTiffFiles.openRaster(ID, fixture, GeoTiffRasterOptions.defaults());
    }

    private static SourceException failure(byte[] fixture) {
        return assertThrows(SourceException.class, () -> openRaster(fixture));
    }

    private static void assertTagFailure(byte[] fixture, int tag, String reason) {
        SourceException failure = failure(fixture);
        assertEquals("GEOTIFF_TAG_INVALID", failure.terminal().code());
        assertEquals(Integer.toString(tag), failure.terminal().context().get("tag"));
        assertEquals(reason, failure.terminal().context().get("reason"));
    }

    private static void assertGeoKeyRange(byte[] fixture) {
        assertGeoKeyRange(fixture, 1026);
    }

    private static void assertGeoKeyRange(byte[] fixture, int key) {
        SourceException failure = failure(fixture);
        assertEquals("GEOTIFF_GEOKEY_INVALID", failure.terminal().code());
        assertEquals(Integer.toString(key), failure.terminal().context().get("key"));
        assertEquals("range", failure.terminal().context().get("reason"));
    }

    private static void assertDiagnosticIsClean(SourceException failure, String canary) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            assertTrue(current instanceof SourceException, current.getClass().getName());
            SourceException sourceFailure = (SourceException) current;
            String rendered =
                    String.valueOf(current.getMessage())
                            + sourceFailure.report()
                            + sourceFailure.terminal();
            assertFalse(rendered.contains(canary));
            assertFalse(rendered.contains("GeoTiffParser"));
            assertFalse(rendered.contains("java."));
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            Collections.addAll(pending, current.getSuppressed());
        }
    }

    private static void appendByte(Path path) {
        try {
            Files.write(path, new byte[] {0}, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new AssertionError("Could not mutate the snapshot fixture", failure);
        }
    }

    private static void putTagId(byte[] bytes, int oldTag, int newTag) {
        ByteBuffer buffer = ordered(bytes);
        buffer.putShort(entryOffset(buffer, oldTag), (short) newTag);
    }

    private static void putShortAtEntryValue(byte[] bytes, int tag, int value) {
        ByteBuffer buffer = ordered(bytes);
        buffer.putShort(entryOffset(buffer, tag) + 8, (short) value);
    }

    private static void putIntAtEntryValue(byte[] bytes, int tag, int value) {
        ByteBuffer buffer = ordered(bytes);
        buffer.putInt(entryOffset(buffer, tag) + 8, value);
    }

    private static void putRationalDenominator(byte[] bytes, int tag, int value) {
        ByteBuffer buffer = ordered(bytes);
        int entry = entryOffset(buffer, tag);
        buffer.putInt(buffer.getInt(entry + 8) + 4, value);
    }

    private static void putGeoKeyId(byte[] bytes, int oldKey, int newKey) {
        ByteBuffer buffer = ordered(bytes);
        int entry = entryOffset(buffer, 34735);
        int offset = buffer.getInt(entry + 8);
        int count = Short.toUnsignedInt(buffer.getShort(offset + 6));
        for (int index = 0; index < count; index++) {
            int keyOffset = offset + 8 + index * 8;
            if (Short.toUnsignedInt(buffer.getShort(keyOffset)) == oldKey) {
                buffer.putShort(keyOffset, (short) newKey);
                return;
            }
        }
        throw new AssertionError("Missing fixture key " + oldKey);
    }

    private static ByteBuffer ordered(byte[] bytes) {
        return ByteBuffer.wrap(bytes)
                .order(bytes[0] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
    }

    private static int entryOffset(ByteBuffer bytes, int tag) {
        int ifd = bytes.getInt(4);
        int count = Short.toUnsignedInt(bytes.getShort(ifd));
        for (int index = 0; index < count; index++) {
            int offset = ifd + 2 + index * 12;
            if (Short.toUnsignedInt(bytes.getShort(offset)) == tag) {
                return offset;
            }
        }
        throw new AssertionError("Missing fixture tag " + tag);
    }
}
