package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class NaturalEarthChartTest {
    @Test
    void bundledMembersMatchTheApprovedManifest() throws IOException {
        assertEquals(5, NaturalEarthChart.manifest().size());
        for (NaturalEarthChart.ManifestEntry entry : NaturalEarthChart.manifest()) {
            try (InputStream input = classpath(entry.name())) {
                byte[] content = input.readAllBytes();
                assertEquals(entry.size(), content.length, entry.name());
                assertEquals(entry.sha256(), sha256(content), entry.name());
            }
        }
    }

    @Test
    void sourceIsPreparedOnceInTheDisplayProjection() throws IOException {
        NaturalEarthChart.MaterializedDataset dataset = NaturalEarthChart.openDataset();
        Path directory = dataset.directory();
        FeatureSource source = dataset.source();
        try {
            assertTrue(Files.isDirectory(directory));
            assertEquals(127L, source.metadata().featureCount().orElseThrow());
            assertEquals(
                    CrsDefinitions.EPSG_3857.canonicalIdentifier(),
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            assertTrue(
                    source.metadata().extent().orElseThrow().minX()
                            >= -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(
                    source.metadata().extent().orElseThrow().maxX()
                            <= WebMercatorProjection.WORLD_LIMIT);

            int records = 0;
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                while (cursor.advance()) {
                    FeatureRecord record = cursor.current();
                    assertWithinProjectedMercatorDomain(record.geometry());
                    records++;
                }
                assertTrue(cursor.diagnostics().entries().isEmpty());
            }
            assertEquals(127, records);
        } finally {
            source.close();
        }
        assertTrue(source.isClosed());
        assertFalse(Files.exists(directory));
    }

    @Test
    void missingAndCorruptResourcesHaveStableFailures() {
        NaturalEarthChart.NaturalEarthResourceException missing =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                NaturalEarthChart.openDataset(
                                        name -> {
                                            if (name.equals("ne_110m_land.dbf")) {
                                                throw new IOException("not present");
                                            }
                                            return classpath(name);
                                        }));
        assertEquals("NATURAL_EARTH_RESOURCE_READ_FAILED", missing.code());
        assertEquals("ne_110m_land.dbf", missing.context().get("resource"));

        NaturalEarthChart.NaturalEarthResourceException corrupt =
                assertThrows(
                        NaturalEarthChart.NaturalEarthResourceException.class,
                        () ->
                                NaturalEarthChart.openDataset(
                                        name -> {
                                            if (name.equals("ne_110m_land.shp")) {
                                                return new ByteArrayInputStream(new byte[89_504]);
                                            }
                                            return classpath(name);
                                        }));
        assertEquals("NATURAL_EARTH_RESOURCE_HASH_MISMATCH", corrupt.code());
        assertEquals("ne_110m_land.shp", corrupt.context().get("resource"));
    }

    @Test
    void detachedBackgroundRendererProducesThePreparedChartOffTheEdt() {
        NaturalEarthChart.MaterializedDataset dataset = NaturalEarthChart.openDataset();
        try {
            MapViewport viewport =
                    MapViewport.fit(
                            900, 500, dataset.source().metadata().extent().orElseThrow(), 24.0);
            BufferedImage image =
                    new NaturalEarthBackgroundRenderer(dataset.projectedFeatures())
                            .render(viewport);
            assertTrue(countColor(image, NaturalEarthChart.OCEAN.getRGB()) > 100_000);
            assertTrue(countLandLike(image) > 20_000);
        } finally {
            dataset.source().close();
        }
    }

    @Test
    void offscreenChartShowsAFramedWorldWithLandAndOcean() throws Exception {
        List<String> diagnostics = new java.util.ArrayList<>();
        NaturalEarthChart.ChartSession session = NaturalEarthChart.startHeadless(diagnostics::add);
        Path directory = session.materializedDirectory();
        try {
            assertEquals(
                    List.of("SHAPEFILE_PRJ_OVERRIDE_USED"),
                    session.openingDiagnostics().entries().stream()
                            .map(value -> value.code())
                            .toList());
            assertEquals(
                    List.of(
                            "natural-earth layer=natural-earth-land WARNING "
                                    + "SHAPEFILE_PRJ_OVERRIDE_USED: Shapefile "
                                    + "coordinate-reference diagnostic"),
                    diagnostics);
            assertEquals(
                    CrsDefinitions.EPSG_3857.canonicalIdentifier(),
                    session.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());

            BufferedImage image = new BufferedImage(900, 500, BufferedImage.TYPE_INT_ARGB);
            EventQueue.invokeAndWait(
                    () -> {
                        session.view().setSize(900, 500);
                        session.view().fitToData(24.0);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            session.view().paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    });

            Path reports = Path.of("build", "reports");
            Files.createDirectories(reports);
            Path report = reports.resolve("natural-earth-chart.png");
            ImageIO.write(image, "png", report.toFile());
            int oceanPixels = countColor(image, NaturalEarthChart.OCEAN.getRGB());
            int landPixels = countLandLike(image);
            assertTrue(oceanPixels > 100_000, "ocean pixels=" + oceanPixels);
            assertTrue(
                    landPixels > 20_000,
                    "land pixels=" + landPixels + " reports=" + session.view().sourceReports());
            assertEquals(
                    List.of(
                            "natural-earth layer=natural-earth-land WARNING "
                                    + "SHAPEFILE_PRJ_OVERRIDE_USED: Shapefile "
                                    + "coordinate-reference diagnostic",
                            "natural-earth layer=natural-earth-land WARNING "
                                    + "CRS_QUERY_ENVELOPE_CLIPPED: Visible query envelope was "
                                    + "clipped to the CRS domain"),
                    diagnostics);
            assertTrue(
                    session.view().viewport().visibleWorldEnvelope().minX()
                            < -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(
                    session.view().viewport().visibleWorldEnvelope().maxX()
                            > WebMercatorProjection.WORLD_LIMIT);
        } finally {
            session.close();
        }
        assertTrue(session.sourceClosed());
        assertFalse(Files.exists(directory));
    }

    @Test
    void commandLineRejectsUnknownModes() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> LiveTrackStress.main(new String[] {"--unknown"}));
        assertEquals(LiveTrackStress.USAGE, failure.getMessage());
    }

    @Test
    void commandLineConfigurationIsValidatedBeforeViewerAllocation() {
        LiveTrackViewer.ViewerConfiguration configuration =
                LiveTrackStress.parseViewerConfiguration(
                        new String[] {
                            "--population=1000000",
                            "--seed=0x1234",
                            "--workers=4",
                            "--report-profile=reference",
                            "--fps=30",
                            "--telemetry-stdout"
                        });
        assertEquals(1_000_000, configuration.simulation().population());
        assertEquals(0x1234L, configuration.simulation().seed());
        assertEquals(4, configuration.simulation().workers());
        assertEquals("reference", configuration.reportProfile());
        assertEquals(30, configuration.fpsCap());
        assertTrue(configuration.telemetryStdout());

        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackStress.parseViewerConfiguration(new String[] {"--workers=33"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LiveTrackStress.parseViewerConfiguration(
                                new String[] {"--report-profile=unknown"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackStress.parseViewerConfiguration(new String[] {"--fps=20"}));
    }

    private static InputStream classpath(String name) throws IOException {
        InputStream stream =
                NaturalEarthChartTest.class.getResourceAsStream(
                        NaturalEarthChart.RESOURCE_ROOT + name);
        if (stream == null) {
            throw new IOException("missing test resource " + name);
        }
        return stream;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void assertWithinProjectedMercatorDomain(Geometry geometry) {
        if (geometry instanceof PolygonGeometry polygon) {
            assertWithinProjectedMercatorDomain(polygon.exterior());
            polygon.holes().forEach(NaturalEarthChartTest::assertWithinProjectedMercatorDomain);
            return;
        }
        MultiPolygonGeometry polygons = assertInstanceOf(MultiPolygonGeometry.class, geometry);
        assertWithinProjectedMercatorDomain(polygons.coordinates());
    }

    private static void assertWithinProjectedMercatorDomain(CoordinateSequence coordinates) {
        for (int index = 0; index < coordinates.size(); index++) {
            assertTrue(coordinates.x(index) >= -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(coordinates.x(index) <= WebMercatorProjection.WORLD_LIMIT);
            assertTrue(coordinates.y(index) >= -WebMercatorProjection.WORLD_LIMIT);
            assertTrue(coordinates.y(index) <= WebMercatorProjection.WORLD_LIMIT);
        }
    }

    private static int countColor(BufferedImage image, int color) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countLandLike(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 0xff;
                int green = (rgb >>> 8) & 0xff;
                int blue = rgb & 0xff;
                if (green > red && green > blue && green >= 70) {
                    count++;
                }
            }
        }
        return count;
    }
}
