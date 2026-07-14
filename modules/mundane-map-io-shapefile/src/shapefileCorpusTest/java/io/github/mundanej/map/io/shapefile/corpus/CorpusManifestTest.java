package io.github.mundanej.map.io.shapefile.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CorpusManifestTest {
    private static final Path CORPUS_ROOT = corpusRoot();

    @Test
    void manifestPinsTheCompleteLicensedCorpusInventory() throws Exception {
        CorpusManifest manifest = CorpusManifest.loadAndVerify(CORPUS_ROOT);
        assertEquals(
                Set.of(
                        "curated-point-utf8-4326",
                        "generated-multipoint-null-ibm437",
                        "generated-multipart-line-ibm850",
                        "generated-polygon-hole-windows1252-3857",
                        "generated-point-iso88591-unknown-prj",
                        "generated-point-fallback-deleted",
                        "generated-pointz-rejected",
                        "generated-corrupt-shx",
                        "generated-corrupt-dbf"),
                manifest.datasets().stream()
                        .map(CorpusManifest.Dataset::id)
                        .collect(Collectors.toSet()));
        assertTrue(manifest.rows().size() > manifest.datasets().size());

        Map<String, CorpusManifest.Dataset> byId =
                manifest.datasets().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        CorpusManifest.Dataset::id, Function.identity()));
        CorpusManifest.Dataset curated = byId.get("curated-point-utf8-4326");
        assertEquals("CURATED", curated.role());
        assertEquals("LicenseRef-NaturalEarth-Public-Domain", curated.licenseId());
        assertEquals(
                "https://naturalearth.s3.amazonaws.com/110m_cultural/"
                        + "ne_110m_populated_places_simple.zip",
                curated.origin());
        assertEquals("pyshp-2.3.1", curated.toolAndVersion());
        assertTrue(curated.parentId().isEmpty());

        long curatedCount =
                manifest.datasets().stream()
                        .filter(dataset -> dataset.role().equals("CURATED"))
                        .count();
        assertEquals(1, curatedCount);
        assertFalse(
                manifest.datasets().stream()
                        .filter(dataset -> !dataset.parentId().isEmpty())
                        .toList()
                        .isEmpty());
    }

    private static Path corpusRoot() {
        String value = System.getProperty("mundane.map.shapefile.corpus.root");
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError("Missing fixed corpus source root");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
