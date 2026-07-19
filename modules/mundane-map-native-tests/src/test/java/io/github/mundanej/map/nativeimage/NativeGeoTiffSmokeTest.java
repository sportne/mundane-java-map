package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeGeoTiffSmokeTest {
    @Test
    void sharedScenarioReadsQueriesRendersDiagnosesAndCleansUp() {
        Path directory;
        NativeGeoTiffSmokeScenario.Result result;
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openGeoTiff()) {
            NativeGeoTiffPaths paths = workspace.geoTiffPaths();
            directory = paths.rasterNone().getParent();
            result = NativeGeoTiffSmokeScenario.run(paths);
            assertTrue(Files.isDirectory(directory));
        }

        assertFalse(Files.exists(directory));
        assertEquals(24, result.rasterNone().width());
        assertEquals(16, result.rasterDeflate().width());
        assertEquals(-21_250.0, result.elevationPackBits().probeValue());
        assertEquals(-24_000.0, result.elevationPackBits().firstQueryValue());
        assertEquals(-106.25, result.elevationDeflate().probeValue());
        assertEquals(-120.0, result.elevationDeflate().firstQueryValue());
        assertTrue(result.rasterNone().sourceClosed());
        assertTrue(result.rasterDeflate().sourceClosed());
        assertTrue(result.elevationPackBits().sourceClosed());
        assertTrue(result.elevationDeflate().sourceClosed());
        assertTrue(result.rasterNone().nonWhitePixels() >= 8_000);
        assertTrue(result.rasterDeflate().nonWhitePixels() >= 8_000);
        assertTrue(result.elevationPackBits().nonWhitePixels() >= 8_000);
        assertTrue(result.elevationDeflate().nonWhitePixels() >= 8_000);
        var terminal = result.malformed().entries().getLast();
        assertEquals("GEOTIFF_HEADER_INVALID", terminal.code());
        assertEquals(Map.of("field", "version", "reason", "value"), terminal.context());
    }

    @Test
    void fixedResourcesMatchLiteralLengthsHashesRoutesAndCompressionMatrix() throws Exception {
        assertFixture(NativeGeoTiffResources.RASTER_NONE, 1);
        assertFixture(NativeGeoTiffResources.RASTER_DEFLATE, 8);
        assertFixture(NativeGeoTiffResources.ELEVATION_PACKBITS, 32_773);
        assertFixture(NativeGeoTiffResources.ELEVATION_DEFLATE, 8);
        assertEquals(4, NativeGeoTiffResources.INVENTORY.size());
    }

    private static void assertFixture(NativeGeoTiffResources.Entry entry, int compression)
            throws Exception {
        byte[] bytes = resource(entry.resourceName());
        assertEquals(entry.length(), bytes.length);
        assertEquals(entry.sha256(), hex(sha256().digest(bytes)));
        assertEquals(compression, compression(bytes));
    }

    private static int compression(byte[] encoded) {
        ByteOrder order = encoded[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer data = ByteBuffer.wrap(encoded).order(order);
        int ifd = data.getInt(4);
        int entries = Short.toUnsignedInt(data.getShort(ifd));
        for (int index = 0; index < entries; index++) {
            int entry = ifd + 2 + 12 * index;
            if (Short.toUnsignedInt(data.getShort(entry)) == 259) {
                return Short.toUnsignedInt(data.getShort(entry + 8));
            }
        }
        throw new AssertionError("compression tag is missing");
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = NativeGeoTiffSmokeTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new AssertionError("missing resource " + name);
            }
            return input.readAllBytes();
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
