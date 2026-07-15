package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NativeShapefileSmokeTest {
    private static final String DATASET = "generated-polygon-hole-windows1252-3857";

    @Test
    void sharedScenarioReturnsStableSemanticSummary() {
        NativeShapefileSmokeScenario.Result result = NativeShapefileSmokeScenario.run();

        assertEquals(2, result.featureCount());
        assertEquals("record:1", result.firstFeatureId());
        assertEquals("Café", result.windows1252Value());
        assertTrue(result.undefinedByteBecameNull());
        assertTrue(result.nonWhitePixels() >= 100 && result.nonWhitePixels() <= 20_000);
        NativeShapefileSmokeScenario.assertCursorWarning(result.retainedDiagnostics());
    }

    @Test
    void copiedResourcesExactlyMatchSelectedManifestAuthorityAndLicense() throws Exception {
        Path corpus = fixedPath("mundane.map.shapefile.native.corpus.root");
        Path manifest = corpus.resolve("manifest.tsv");
        List<String[]> selected =
                Files.readAllLines(manifest).stream()
                        .filter(line -> !line.startsWith("datasetId\t"))
                        .map(line -> line.split("\t", -1))
                        .filter(columns -> columns[0].equals(DATASET))
                        .sorted(Comparator.comparing(columns -> columns[2]))
                        .toList();
        assertEquals(5, selected.size());
        assertEquals(
                List.of("cpg", "dbf", "prj", "shp", "shx"),
                selected.stream()
                        .map(columns -> extension(columns[2]))
                        .collect(Collectors.toList()));

        Map<String, NativeShapefileResources.Entry> entries =
                NativeShapefileResources.INVENTORY.stream()
                        .filter(entry -> !entry.equals(NativeShapefileResources.MALFORMED))
                        .collect(
                                Collectors.toMap(
                                        entry -> extension(entry.localName()), entry -> entry));
        for (String[] columns : selected) {
            assertEquals("GENERATED", columns[1]);
            assertEquals("generated:pyshp-profile-v1", columns[5]);
            assertEquals("pyshp-2.3.1", columns[6]);
            assertEquals("BSD-3-Clause", columns[7]);
            assertEquals("licenses/BSD-3-Clause.txt", columns[8]);
            String extension = extension(columns[2]);
            NativeShapefileResources.Entry entry = entries.get(extension);
            assertEquals(Long.parseLong(columns[3]), entry.length());
            assertEquals(columns[4], entry.sha256());
            Path source = corpus.resolve(columns[2]);
            assertEquals(entry.length(), Files.size(source));
            assertEquals(entry.sha256(), sha256(Files.readAllBytes(source)));
            assertArrayEquals(Files.readAllBytes(source), resource(entry));
        }

        Path corpusLicense = corpus.resolve("licenses/BSD-3-Clause.txt");
        Path rootLicense = fixedPath("mundane.map.shapefile.native.root.license");
        assertArrayEquals(Files.readAllBytes(rootLicense), Files.readAllBytes(corpusLicense));
    }

    @Test
    void malformedResourceMatchesTheFixedByteBuilderOracle() throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(108);
        bytes.order(ByteOrder.BIG_ENDIAN).putInt(9994);
        for (int index = 0; index < 5; index++) {
            bytes.putInt(0);
        }
        bytes.putInt(54);
        bytes.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(5);
        bytes.putDouble(0.0).putDouble(0.0).putDouble(1.0).putDouble(1.0);
        for (int index = 0; index < 4; index++) {
            bytes.putDouble(Double.NaN);
        }
        bytes.order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(10);

        byte[] actual = resource(NativeShapefileResources.MALFORMED);
        assertArrayEquals(bytes.array(), actual);
        assertEquals(NativeShapefileResources.MALFORMED.sha256(), sha256(actual));
    }

    @Test
    void unrelatedCorpusAndProvenanceResourcesAreNotPackaged() {
        assertNull(NativeSmokeMain.class.getResource("/shapefile-corpus/manifest.tsv"));
        assertNull(
                NativeSmokeMain.class.getResource(
                        "/io/github/mundanej/map/nativeimage/shapefile/manifest.tsv"));
        assertNull(
                NativeSmokeMain.class.getResource(
                        "/io/github/mundanej/map/nativeimage/shapefile/BSD-3-Clause.txt"));
        assertNull(
                NativeSmokeMain.class.getResource(
                        "/io/github/mundanej/map/nativeimage/shapefile/curated-point-utf8-4326.shp"));
    }

    private static Path fixedPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new AssertionError("Missing fixed test input: " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static byte[] resource(NativeShapefileResources.Entry entry) throws IOException {
        try (InputStream input = NativeSmokeMain.class.getResourceAsStream(entry.resourceName())) {
            if (input == null) {
                throw new AssertionError("Missing native resource: " + entry.resourceName());
            }
            return input.readAllBytes();
        }
    }

    private static String extension(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }
}
