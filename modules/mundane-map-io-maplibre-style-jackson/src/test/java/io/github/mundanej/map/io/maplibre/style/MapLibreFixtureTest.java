package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MapLibreFixtureTest {
    private static final String ROOT = "/io/github/mundanej/map/io/maplibre/style/fixtures/";

    @Test
    void manifestPinsEveryInteroperabilityFixture() throws IOException {
        Properties manifest = properties("manifest.properties");
        assertEquals(
                java.util.Set.of(
                        "camera-interpolation-supported.json",
                        "remote-resources-rejected.json",
                        "LICENSE-maplibre-style-spec.txt"),
                manifest.stringPropertyNames());
        for (String name : manifest.stringPropertyNames()) {
            assertEquals(manifest.getProperty(name), sha256(bytes(name)), name);
        }
    }

    @Test
    void supportedAndRejectedFixturesHaveExactOracles() throws IOException {
        MapLibreStyle supported = MapLibreStyles.read(bytes("camera-interpolation-supported.json"));
        assertEquals(1, supported.layers().size());
        assertEquals("points", supported.layers().getFirst().id());

        MapLibreReadException rejected =
                org.junit.jupiter.api.Assertions.assertThrows(
                        MapLibreReadException.class,
                        () -> MapLibreStyles.read(bytes("remote-resources-rejected.json")));
        assertEquals("MAPLIBRE_ROOT_UNSUPPORTED", rejected.problem().code());
        assertEquals("/sprite", rejected.problem().location());
    }

    @Test
    void provenanceNamesEveryPinnedFixtureAndOfflinePolicy() throws IOException {
        String provenance =
                new String(bytes("PROVENANCE.md"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(provenance.contains("camera-interpolation-supported.json"));
        assertTrue(provenance.contains("remote-resources-rejected.json"));
        assertTrue(provenance.contains("BSD-3-Clause"));
        assertTrue(provenance.contains("LICENSE-maplibre-style-spec.txt"));
        assertTrue(provenance.contains("No test performs network I/O"));
    }

    private static Properties properties(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource(name)) {
            properties.load(input);
        }
        return properties;
    }

    private static byte[] bytes(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return input.readAllBytes();
        }
    }

    private static InputStream resource(String name) {
        InputStream input = MapLibreFixtureTest.class.getResourceAsStream(ROOT + name);
        if (input == null) {
            throw new IllegalStateException("Missing MapLibre fixture: " + name);
        }
        return input;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Required SHA-256 unavailable", failure);
        }
    }
}
