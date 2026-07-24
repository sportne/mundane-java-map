package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeGpxSmokeTest {
    @Test
    void sharedScenarioQueriesRendersWarnsDiagnosesAndCleansUp() {
        Path directory;
        NativeGpxSmokeScenario.Result result;
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openGpx()) {
            Path path = workspace.gpxPath();
            directory = path.getParent();
            result = NativeGpxSmokeScenario.run(path);
            assertTrue(Files.isDirectory(directory));
        }

        assertFalse(Files.exists(directory));
        assertEquals(3, result.records());
        assertTrue(result.coloredPixels() >= 80);
        assertTrue(result.sourceClosed());
        assertTrue(
                result.warnings().entries().stream()
                        .anyMatch(entry -> entry.code().equals("GPX_FIELD_IGNORED")));
        var terminal = result.malformed().entries().getLast();
        assertEquals("GPX_XML_INVALID", terminal.code());
        assertEquals(Map.of("reason", "syntax"), terminal.context());
    }

    @Test
    void fixedResourceMatchesLiteralLengthAndHash() throws Exception {
        NativeGpxResources.Entry entry = NativeGpxResources.VALID;
        byte[] bytes = resource(entry.resourceName());
        assertEquals(entry.length(), bytes.length);
        assertEquals(entry.sha256(), hex(sha256().digest(bytes)));
        assertEquals(1, NativeGpxResources.INVENTORY.size());
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = NativeGpxSmokeTest.class.getResourceAsStream(name)) {
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
