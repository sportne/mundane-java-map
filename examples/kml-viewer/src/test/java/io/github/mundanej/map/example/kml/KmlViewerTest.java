package io.github.mundanej.map.example.kml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KmlViewerTest {
    private static final String FIXTURE = "/io/github/mundanej/map/example/kml/all-geometries.kml";
    private static final String PRODUCER_FIXTURE =
            "/io/github/mundanej/map/example/kml/fixtures/simplekml-static-profile.kml";
    private static final String PRODUCER_DIGEST =
            "32fc9de3e4cc1a09254f01a3b922a406b2237f79c3c6dc403ede3b5c7f37e2f2";

    @TempDir Path temporary;

    @Test
    void opensAllSupportedGeometryFamiliesInSourceOrder() throws IOException {
        Path path = writeFixture();
        try (FeatureSource source = KmlViewer.open(path);
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            List<String> kinds = new ArrayList<>();
            while (cursor.advance()) {
                kinds.add((String) cursor.current().attributes().get("geometryKind"));
            }
            assertEquals(
                    List.of("point", "line", "polygon", "multipoint", "multiline", "multipolygon"),
                    kinds);
        }
    }

    @Test
    void createsOwningViewAndRendersPolygonHoleTolerantly() throws Exception {
        MapView view = KmlViewer.createMapView(writeFixture());
        BufferedImage image = new BufferedImage(900, 640, BufferedImage.TYPE_INT_ARGB);
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(Color.WHITE);
                            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    });
            var operation =
                    CrsRegistry.level1()
                            .operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
            Coordinate hole =
                    view.viewport().worldToScreen(operation.transform(new Coordinate(0, 0)));
            Coordinate fill =
                    view.viewport().worldToScreen(operation.transform(new Coordinate(2, 0)));
            assertTrue(isNearWhite(image.getRGB((int) hole.x(), (int) hole.y())));
            assertFalse(isNearWhite(image.getRGB((int) fill.x(), (int) fill.y())));
            assertDominantRed(image, view, operation, new Coordinate(-4, 4));
            assertDominantRed(image, view, operation, new Coordinate(4, 4));
            assertDominantBlue(image, view, operation, new Coordinate(-3.5, -3.5));
            assertDominantBlue(image, view, operation, new Coordinate(3.5, -3.5));
            assertDominantBlue(image, view, operation, new Coordinate(-4.5, 5.5));
            assertDominantBlue(image, view, operation, new Coordinate(4.5, 5.5));
        } finally {
            SwingUtilities.invokeAndWait(view::close);
        }
    }

    @Test
    void commandLineRejectsInvalidInputWithStableSummary() {
        List<String> summaries = new ArrayList<>();
        assertFalse(KmlViewer.runMain(new String[0], summaries::add, ignored -> {}));
        assertEquals(List.of("kml-viewer: ERROR INPUT_INVALID"), summaries);

        Path malformed = temporary.resolve("malformed-secret.kml");
        try {
            Files.writeString(malformed, "<not-kml/>");
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
        summaries.clear();
        assertFalse(
                KmlViewer.runMain(
                        new String[] {malformed.toString()}, summaries::add, ignored -> {}));
        assertEquals(
                List.of("kml-viewer: ERROR KML_XML_INVALID context={reason=namespace}"), summaries);
        assertFalse(summaries.getFirst().contains("malformed-secret"));
        assertEquals(Path.of("features.kml"), KmlViewer.parsePath(new String[] {"features.kml"}));
        assertThrows(NullPointerException.class, () -> KmlViewer.parsePath(null));
        assertThrows(NullPointerException.class, () -> KmlViewer.parsePath(new String[] {null}));
    }

    @Test
    void commandLineTransfersSuccessAndClosesRejectedLaunches() throws IOException {
        Path path = writeFixture();
        List<String> summaries = new ArrayList<>();
        AtomicReference<FeatureSource> opened = new AtomicReference<>();
        assertTrue(
                KmlViewer.runMain(
                        new String[] {path.toString()},
                        summaries::add,
                        source -> {
                            opened.set(source);
                            source.close();
                        }));
        assertTrue(opened.get().isClosed());
        assertTrue(summaries.isEmpty());

        opened.set(null);
        assertFalse(
                KmlViewer.runMain(
                        new String[] {path.toString()},
                        summaries::add,
                        source -> {
                            opened.set(source);
                            throw new IllegalStateException("injected");
                        }));
        assertTrue(opened.get().isClosed());
        assertEquals(List.of("kml-viewer: ERROR INPUT_INVALID"), summaries);
    }

    @Test
    void publicFactoryRejectsEdtLoadingAndWindowFailureClosesOwnership() throws Exception {
        Path path = writeFixture();
        AtomicReference<RuntimeException> edtFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        KmlViewer.createMapView(path);
                    } catch (RuntimeException expected) {
                        edtFailure.set(expected);
                    }
                });
        assertTrue(edtFailure.get() instanceof IllegalStateException);

        FeatureSource source = KmlViewer.open(path);
        AtomicReference<RuntimeException> installFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        KmlViewer.installWindow(
                                source,
                                ignored -> {
                                    throw new IllegalStateException("injected");
                                });
                    } catch (RuntimeException expected) {
                        installFailure.set(expected);
                    }
                });
        assertTrue(installFailure.get() instanceof IllegalStateException);
        assertTrue(source.isClosed());

        FeatureSource offEdtSource = KmlViewer.open(path);
        assertThrows(IllegalStateException.class, () -> KmlViewer.createMapView(offEdtSource));
        offEdtSource.close();
    }

    @Test
    void independentProducerFixtureAndProvenanceAreStableAndReadable() throws Exception {
        byte[] fixture = resource(PRODUCER_FIXTURE);
        assertEquals(1330, fixture.length);
        assertEquals(
                PRODUCER_DIGEST,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture)));
        String provenance =
                new String(
                        resource("/io/github/mundanej/map/example/kml/fixtures/PROVENANCE.md"),
                        StandardCharsets.UTF_8);
        assertTrue(provenance.contains("simplekml` 1.3.6"));
        assertTrue(provenance.contains("LGPL-3.0-or-later"));
        assertTrue(provenance.contains("BSD-3-Clause"));
        assertTrue(provenance.contains(PRODUCER_DIGEST));

        Path path = temporary.resolve("simplekml-static-profile.kml");
        Files.write(path, fixture);
        try (FeatureSource source = KmlViewer.open(path)) {
            assertEquals(3, source.metadata().featureCount().orElseThrow());
            List<String> codes =
                    source.openingDiagnostics().entries().stream()
                            .map(entry -> entry.code())
                            .toList();
            assertEquals(14, codes.size());
            assertEquals("KML_PRESENTATION_IGNORED", codes.getFirst());
            assertTrue(
                    codes.subList(1, codes.size()).stream()
                            .allMatch(code -> code.equals("KML_ALTITUDE_IGNORED")));
        }
    }

    private Path writeFixture() throws IOException {
        Path path = temporary.resolve("all-geometries.kml");
        try (InputStream input =
                java.util.Objects.requireNonNull(
                        KmlViewerTest.class.getResourceAsStream(FIXTURE))) {
            Files.copy(input, path);
        }
        return path;
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input =
                java.util.Objects.requireNonNull(
                        KmlViewerTest.class.getResourceAsStream(path), path)) {
            return input.readAllBytes();
        }
    }

    private static boolean isNearWhite(int argb) {
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        return red > 245 && green > 245 && blue > 245;
    }

    private static void assertDominantRed(
            BufferedImage image, MapView view, CrsOperation operation, Coordinate coordinate) {
        int argb = pixel(image, view, operation, coordinate);
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        assertTrue(red > green + 30 && red > blue + 30);
    }

    private static void assertDominantBlue(
            BufferedImage image, MapView view, CrsOperation operation, Coordinate coordinate) {
        int argb = pixel(image, view, operation, coordinate);
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        assertTrue(blue > red + 20 && blue > green);
    }

    private static int pixel(
            BufferedImage image, MapView view, CrsOperation operation, Coordinate coordinate) {
        Coordinate screen = view.viewport().worldToScreen(operation.transform(coordinate));
        return image.getRGB((int) screen.x(), (int) screen.y());
    }
}
