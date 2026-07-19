package io.github.mundanej.map.example.geojson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.awt.MapView;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoJsonViewerTest {
    private static final String ROOT = "/io/github/mundanej/map/example/geojson/fixtures/";
    private static final Map<String, String> DIGESTS =
            Map.of(
                    "all-geometries.geojson",
                    "cedc5fb33655355aad861f2effbc07460b64fb40b4d239a9febac238eef5f93f",
                    "python-json.geojson",
                    "b8b2ab35577dd91f6e2ebb9b873aff7c4a19a4073ae5c6a95c9f936133bb1412",
                    "unsupported-geometry-collection.geojson",
                    "cc8a4df519362b050e41ef61f55ca6c1a2309a25bf33ad6f6413224cfa374910",
                    "malformed-truncated.geojson",
                    "26e977e6b044673c161396ccbed5e3d70dde21889cbc54d6ee819a7f0a0d0aa7");

    @TempDir Path temporaryDirectory;

    @Test
    void fixtureDigestsAndProvenanceAreStable() throws Exception {
        for (Map.Entry<String, String> fixture : DIGESTS.entrySet()) {
            assertEquals(fixture.getValue(), sha256(resource(fixture.getKey())));
        }
        String provenance = new String(resource("PROVENANCE.md"), StandardCharsets.UTF_8);
        assertTrue(provenance.contains("Apache-2.0"));
        DIGESTS.values().forEach(digest -> assertTrue(provenance.contains(digest)));
    }

    @Test
    void opensEverySupportedFixtureAndRetainsProducerValues() {
        FeatureSource geometries =
                GeoJsonViewer.openResource(ROOT + "all-geometries.geojson", "all-geometries");
        assertEquals(6, count(geometries));
        geometries.close();

        FeatureSource producer =
                GeoJsonViewer.openResource(ROOT + "python-json.geojson", "python-json");
        FeatureCursor cursor = producer.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(cursor.advance());
        assertEquals("number:17", cursor.current().id());
        assertEquals(
                Map.of("producer", "python-json", "enabled", true, "rank", 2L),
                cursor.current().attributes());
        assertFalse(cursor.advance());
        cursor.close();
        producer.close();
    }

    @Test
    void reportsStableUnsupportedAndMalformedFixtureOutcomes() {
        SourceException unsupported =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonViewer.openResource(
                                        ROOT + "unsupported-geometry-collection.geojson",
                                        "unsupported"));
        assertEquals("GEOJSON_PROFILE_UNSUPPORTED", unsupported.terminal().code());
        assertEquals(Map.of("construct", "geometryCollection"), unsupported.terminal().context());

        SourceException malformed =
                assertThrows(
                        SourceException.class,
                        () ->
                                GeoJsonViewer.openResource(
                                        ROOT + "malformed-truncated.geojson", "malformed"));
        assertEquals("GEOJSON_JSON_INVALID", malformed.terminal().code());
        assertEquals(Map.of("reason", "syntax"), malformed.terminal().context());
    }

    @Test
    void cliReportsOnlyStableStructuredDiagnostics() throws Exception {
        Path unsupported = temporaryDirectory.resolve("unsupported.geojson");
        Path malformed = temporaryDirectory.resolve("malformed-SECRET_CANARY.geojson");
        Files.write(unsupported, resource("unsupported-geometry-collection.geojson"));
        Files.write(malformed, resource("malformed-truncated.geojson"));
        List<String> summaries = new java.util.ArrayList<>();

        assertFalse(
                GeoJsonViewer.runMain(
                        new String[] {unsupported.toString()}, summaries::add, ignored -> {}));
        assertFalse(
                GeoJsonViewer.runMain(
                        new String[] {malformed.toString()}, summaries::add, ignored -> {}));

        assertEquals(
                List.of(
                        "geojson-viewer: ERROR GEOJSON_PROFILE_UNSUPPORTED component=geojson "
                                + "context={construct=geometryCollection}",
                        "geojson-viewer: ERROR GEOJSON_JSON_INVALID component=geojson context={reason=syntax}"),
                summaries);
        assertFalse(String.join("", summaries).contains("SECRET_CANARY"));
        assertFalse(String.join("", summaries).contains("Jackson"));
    }

    @Test
    void launchFailureClosesTheSourceBeforeReporting() {
        AtomicReference<FeatureSource> opened = new AtomicReference<>();
        List<String> summaries = new java.util.ArrayList<>();

        assertFalse(
                GeoJsonViewer.runMain(
                        new String[0],
                        summaries::add,
                        source -> {
                            opened.set(source);
                            throw new IllegalStateException("SECRET_LAUNCH_FAILURE");
                        }));

        assertTrue(opened.get().isClosed());
        assertEquals(List.of("geojson-viewer: ERROR INPUT_INVALID"), summaries);
        assertFalse(summaries.getFirst().contains("SECRET_LAUNCH_FAILURE"));
    }

    @Test
    void windowInstallationFailureClosesTheConfiguredViewAndOwnedSource() throws Exception {
        FeatureSource source =
                GeoJsonViewer.openResource(ROOT + "python-json.geojson", "install-failure");

        SwingUtilities.invokeAndWait(
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        GeoJsonViewer.installWindow(
                                                source,
                                                ignored -> {
                                                    throw new IllegalStateException(
                                                            "injected window failure");
                                                })));

        assertTrue(source.isClosed());
    }

    @Test
    void rendersAllGeometryFamiliesAndLeavesThePolygonHoleUnfilled() throws Exception {
        FeatureSource source =
                GeoJsonViewer.openResource(ROOT + "all-geometries.geojson", "render-fixture");
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = GeoJsonViewer.createMapView(source);
                    view.setSize(640, 480);
                    view.fitToData(40);
                    BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    viewReference.set(view);
                    imageReference.set(image);
                });

        MapView view = viewReference.get();
        BufferedImage image = imageReference.get();
        int nonWhite = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    nonWhite++;
                }
            }
        }
        assertTrue(nonWhite > 2_000, "expected broad line, marker, and polygon evidence");
        assertBlueEvidence(image, view, new Coordinate(-1.6, 1.6), 8, "Point");
        assertBlueEvidence(image, view, new Coordinate(-0.8, 1.6), 8, "MultiPoint");
        assertBlueEvidence(image, view, new Coordinate(-1.3, 0.95), 6, "LineString");
        assertBlueEvidence(image, view, new Coordinate(1.2, 1.43), 6, "MultiLineString");
        assertBlueEvidence(image, view, new Coordinate(-1.6, -0.5), 4, "Polygon");
        assertBlueEvidence(image, view, new Coordinate(0.5, -1.0), 4, "MultiPolygon");
        Coordinate hole = view.mapToScreen(new Coordinate(-1.0, -0.9)).orElseThrow();
        assertNearWhite(image.getRGB((int) hole.x(), (int) hole.y()));
        SwingUtilities.invokeAndWait(view::close);
        assertTrue(source.isClosed());
    }

    @Test
    void rejectsInvalidCliShapesBeforeSchedulingSwing() {
        List<String> summaries = new java.util.ArrayList<>();
        assertFalse(GeoJsonViewer.runMain(null, summaries::add, ignored -> {}));
        assertFalse(
                GeoJsonViewer.runMain(new String[] {"one", "two"}, summaries::add, ignored -> {}));
        assertEquals(
                List.of(
                        "geojson-viewer: ERROR INPUT_INVALID",
                        "geojson-viewer: ERROR INPUT_INVALID"),
                summaries);
    }

    @Test
    void publicPathEntryPointOpensFitsAndTransfersOwnership() throws Exception {
        Path fixture = temporaryDirectory.resolve("viewer.geojson");
        Files.write(fixture, resource("all-geometries.geojson"));

        MapView view = GeoJsonViewer.createMapView(fixture);

        assertEquals(1, view.layerBindings().size());
        SwingUtilities.invokeAndWait(view::close);
        assertTrue(view.layerBindings().isEmpty());
        assertThrows(NullPointerException.class, () -> GeoJsonViewer.createMapView((Path) null));
        assertThrows(
                IllegalArgumentException.class,
                () -> GeoJsonViewer.openResource(ROOT + "missing.geojson", "missing"));

        FeatureSource closed = GeoJsonViewer.openResource(ROOT + "python-json.geojson", "closed");
        closed.close();
        assertThrows(IllegalStateException.class, () -> GeoJsonViewer.createMapView(closed));
        assertThrows(
                NullPointerException.class,
                () -> GeoJsonViewer.createMapView((FeatureSource) null));
    }

    private static int count(FeatureSource source) {
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        int count = 0;
        while (cursor.advance()) {
            cursor.current();
            count++;
        }
        cursor.close();
        return count;
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream input = GeoJsonViewerTest.class.getResourceAsStream(ROOT + name)) {
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void assertNearWhite(int pixel) {
        Color color = new Color(pixel, true);
        assertTrue(color.getRed() > 245 && color.getGreen() > 245 && color.getBlue() > 245);
    }

    private static void assertBlueEvidence(
            BufferedImage image, MapView view, Coordinate map, int radius, String family) {
        Coordinate screen = view.mapToScreen(map).orElseThrow();
        int colored = 0;
        for (int y = Math.max(0, (int) screen.y() - radius);
                y <= Math.min(image.getHeight() - 1, (int) screen.y() + radius);
                y++) {
            for (int x = Math.max(0, (int) screen.x() - radius);
                    x <= Math.min(image.getWidth() - 1, (int) screen.x() + radius);
                    x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getBlue() > color.getRed() + 10) {
                    colored++;
                }
            }
        }
        assertTrue(colored > 2, family + " produced no tolerant blue evidence");
    }
}
