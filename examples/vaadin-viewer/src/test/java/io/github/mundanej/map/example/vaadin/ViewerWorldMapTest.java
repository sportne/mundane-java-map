package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

final class ViewerWorldMapTest {
    @Test
    void explicitBundledResourceLoaderProducesQueryableProjection() {
        int records = 0;
        try (FeatureSource source =
                        ViewerWorldMap.openSource(
                                name -> {
                                    InputStream input =
                                            ViewerWorldMap.class.getResourceAsStream(
                                                    "/io/github/mundanej/map/example/vaadin/naturalearth/"
                                                            + name);
                                    if (input == null) {
                                        throw new IOException("resource absent");
                                    }
                                    return input;
                                });
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                records++;
            }
        }
        assertTrue(records > 100);
    }

    @Test
    void failedBindingConstructionRetainsClosedSourceState() {
        FeatureSource source = ViewerWorldMap.openSource();
        source.close();

        assertThrows(IllegalStateException.class, () -> ViewerWorldMap.openBinding(source));
        assertTrue(source.isClosed());
    }

    @Test
    void rejectsTruncatedResourceBeforeOpeningDecoder() {
        ViewerWorldMap.ManifestEntry first = ViewerWorldMap.manifest().getFirst();

        assertCode(
                "WORLD_MAP_RESOURCE_SIZE_MISMATCH",
                () ->
                        ViewerWorldMap.openSource(
                                ignored ->
                                        new ByteArrayInputStream(
                                                new byte[Math.toIntExact(first.size() - 1)])));
    }

    @Test
    void rejectsOversizedResourceBeforeOpeningDecoder() {
        ViewerWorldMap.ManifestEntry first = ViewerWorldMap.manifest().getFirst();

        assertCode(
                "WORLD_MAP_RESOURCE_SIZE_MISMATCH",
                () ->
                        ViewerWorldMap.openSource(
                                ignored ->
                                        new ByteArrayInputStream(
                                                new byte[Math.toIntExact(first.size() + 1)])));
    }

    @Test
    void rejectsExactLengthResourceWithWrongHash() {
        ViewerWorldMap.ManifestEntry first = ViewerWorldMap.manifest().getFirst();

        assertCode(
                "WORLD_MAP_RESOURCE_HASH_MISMATCH",
                () ->
                        ViewerWorldMap.openSource(
                                ignored ->
                                        new ByteArrayInputStream(
                                                new byte[Math.toIntExact(first.size())])));
    }

    @Test
    void translatesResourceReadFailureToStableCode() {
        assertCode(
                "WORLD_MAP_RESOURCE_READ_FAILED",
                () ->
                        ViewerWorldMap.openSource(
                                ignored ->
                                        new InputStream() {
                                            @Override
                                            public int read() throws IOException {
                                                throw new IOException("fixture failure");
                                            }

                                            @Override
                                            public int read(byte[] buffer, int offset, int length)
                                                    throws IOException {
                                                throw new IOException("fixture failure");
                                            }
                                        }));
    }

    @Test
    void rejectsMissingLoaderAndInvalidManifestValues() {
        assertThrows(NullPointerException.class, () -> ViewerWorldMap.openSource(null));
        assertThrows(NullPointerException.class, () -> ViewerWorldMap.openSource(ignored -> null));
        assertThrows(
                NullPointerException.class,
                () -> new ViewerWorldMap.ManifestEntry(null, 1, "hash"));
        assertThrows(
                NullPointerException.class,
                () -> new ViewerWorldMap.ManifestEntry("name", 1, null));
    }

    private static void assertCode(String code, Runnable operation) {
        ViewerWorldMap.WorldMapResourceException failure =
                assertThrows(ViewerWorldMap.WorldMapResourceException.class, operation::run);
        assertEquals(code, failure.code());
    }
}
