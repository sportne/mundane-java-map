package io.github.mundanej.map.io.kml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KmlFilesTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("kml-test", "KML test");

    @TempDir Path temporary;

    @Test
    void opensNestedOrderedPointAndLineWithFixedMetadata() throws Exception {
        Path path = temporary.resolve("features.kml");
        Files.writeString(path, pointAndLine(), StandardCharsets.UTF_8);
        try (FeatureSource source =
                        KmlFiles.open(
                                path,
                                IDENTITY,
                                KmlOpenOptions.defaults(),
                                CancellationToken.none());
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertEquals(2, source.metadata().featureCount().orElseThrow());
            assertEquals(
                    new Envelope(-77.05, 38.89, -77.0365, 38.8977),
                    source.metadata().extent().orElseThrow());
            assertEquals(
                    "EPSG:4326",
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
            assertEquals(
                    List.of("kmlId", "description", "geometryKind"),
                    source.metadata().schema().orElseThrow().fields().stream()
                            .map(io.github.mundanej.map.api.AttributeField::name)
                            .toList());

            assertTrue(cursor.advance());
            FeatureRecord point = cursor.current();
            assertEquals("kml:placemark:1", point.id());
            assertEquals("White House", point.name());
            assertEquals(
                    new Coordinate(-77.0365, 38.8977),
                    assertInstanceOf(PointGeometry.class, point.geometry()).coordinate());
            assertEquals("white-house", point.attributes().get("kmlId"));
            assertEquals("point", point.attributes().get("geometryKind"));
            assertEquals("Residence", point.attributes().get("description"));

            assertTrue(cursor.advance());
            FeatureRecord line = cursor.current();
            assertEquals("kml:placemark:2", line.id());
            assertEquals(AttributeNull.INSTANCE, line.attributes().get("kmlId"));
            assertEquals("line", line.attributes().get("geometryKind"));
            assertEquals(
                    2,
                    assertInstanceOf(LineStringGeometry.class, line.geometry())
                            .coordinates()
                            .size());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void queryProjectionAndCursorLifecycleUseOrdinarySourceContracts() {
        FeatureSource source = open(pointAndLine());
        FeatureQuery query =
                new FeatureQuery(
                        java.util.Optional.of(new Envelope(-77.04, 38.895, -77.03, 38.90)),
                        io.github.mundanej.map.api.AttributeSelection.only(List.of("geometryKind")),
                        java.util.Optional.empty());
        FeatureCursor cursor = source.openCursor(query, CancellationToken.none());
        assertTrue(cursor.advance());
        assertEquals(
                List.of("geometryKind"), cursor.current().attributes().keySet().stream().toList());
        cursor.close();
        assertTrue(cursor.isClosed());
        FeatureCursor live = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        source.close();
        assertTrue(live.isClosed());
        assertThrows(IllegalStateException.class, live::advance);
    }

    @Test
    void rejectsExternalAndDynamicConstructsWithoutResolvingThem() {
        SourceException network =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<NetworkLink><Link><href>"
                                                        + "https://invalid.example/secret"
                                                        + "</href></Link></NetworkLink>")));
        assertEquals("KML_PROFILE_UNSUPPORTED", network.terminal().code());
        assertEquals(Map.of("construct", "network"), network.terminal().context());

        SourceException doctype =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        """
                                        <?xml version="1.0" encoding="UTF-8"?>
                                        <!DOCTYPE kml [<!ENTITY xxe SYSTEM "file:///SECRET">]>
                                        <kml xmlns="http://www.opengis.net/kml/2.2">
                                          <Placemark><name>&xxe;</name></Placemark>
                                        </kml>
                                        """));
        assertEquals("KML_XML_INVALID", doctype.terminal().code());
        assertEquals(Map.of("reason", "doctype"), doctype.terminal().context());
    }

    @Test
    void validatesCoordinatesWarningsCancellationAndStableFailures() {
        FeatureSource warning =
                open(
                        document(
                                """
                                <Placemark>
                                  <Style><IconStyle/></Style>
                                  <Point><coordinates>-0,0,12</coordinates></Point>
                                </Placemark>
                                """));
        assertEquals(
                List.of("KML_PRESENTATION_IGNORED", "KML_ALTITUDE_IGNORED"),
                warning.openingDiagnostics().entries().stream()
                        .map(io.github.mundanej.map.api.SourceDiagnostic::code)
                        .toList());
        warning.close();

        SourceException bad =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<Placemark><Point><coordinates>"
                                                        + "181,0"
                                                        + "</coordinates></Point></Placemark>")));
        assertEquals("KML_VALUE_INVALID", bad.terminal().code());
        assertEquals(Map.of("field", "longitude", "reason", "range"), bad.terminal().context());
        assertEquals(1, bad.terminal().location().orElseThrow().recordNumber().orElseThrow());

        SourceException blankId =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<Placemark id=\" \"><Point><coordinates>"
                                                        + "0,0"
                                                        + "</coordinates></Point></Placemark>")));
        assertEquals("KML_VALUE_INVALID", blankId.terminal().code());
        assertEquals(Map.of("field", "id", "reason", "syntax"), blankId.terminal().context());

        SourceException geometryId =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<Placemark><Point id=\"discarded\">"
                                                        + "<coordinates>0,0</coordinates>"
                                                        + "</Point></Placemark>")));
        assertEquals("KML_PROFILE_UNSUPPORTED", geometryId.terminal().code());
        assertEquals(Map.of("construct", "attribute"), geometryId.terminal().context());

        SourceException nestedCoordinates =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<Placemark><Point><coordinates><x/>"
                                                        + "</coordinates></Point></Placemark>")));
        assertEquals(
                Map.of("field", "coordinates", "reason", "nestedContent"),
                nestedCoordinates.terminal().context());
        SourceException nestedName =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<Placemark><name><b/></name><Point><coordinates>"
                                                        + "0,0"
                                                        + "</coordinates></Point></Placemark>")));
        assertEquals(
                Map.of("field", "name", "reason", "nestedContent"),
                nestedName.terminal().context());

        AtomicBoolean cancelled = new AtomicBoolean(true);
        SourceException cancellation =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.openSnapshot(
                                        pointAndLine().getBytes(StandardCharsets.UTF_8),
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        cancelled::get));
        assertEquals("SOURCE_CANCELLED", cancellation.terminal().code());
    }

    @Test
    void parserLimitRetainsEarlierWarningAndCandidateLocation() {
        KmlLimits defaults = KmlLimits.defaults();
        KmlLimits limits =
                new KmlLimits(
                        defaults.maximumInputBytes(),
                        defaults.maximumXmlDepth(),
                        defaults.maximumXmlEvents(),
                        defaults.maximumElements(),
                        defaults.maximumAttributes(),
                        defaults.maximumNamespaceDeclarations(),
                        defaults.maximumFeatureDepth(),
                        defaults.maximumPhysicalFeatures(),
                        1,
                        1,
                        defaults.maximumParts(),
                        defaults.maximumScalarCharacters(),
                        defaults.maximumTextCharacters(),
                        defaults.maximumNumberCharacters(),
                        defaults.maximumOwnedBytes(),
                        defaults.retainedWarnings());
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.openSnapshot(
                                        document(
                                                        """
                                                        <Placemark>
                                                          <Style/>
                                                          <LineString>
                                                            <coordinates>0,0 1,1</coordinates>
                                                          </LineString>
                                                        </Placemark>
                                                        """)
                                                .getBytes(StandardCharsets.UTF_8),
                                        IDENTITY,
                                        KmlOpenOptions.defaults().withFormatLimits(limits),
                                        CancellationToken.none()));
        assertEquals(
                List.of("KML_PRESENTATION_IGNORED", "SOURCE_LIMIT_EXCEEDED"),
                failure.report().entries().stream()
                        .map(io.github.mundanej.map.api.SourceDiagnostic::code)
                        .toList());
        assertEquals(1, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());

        byte[] cancellationDocument =
                document(
                                """
                                <Placemark>
                                  <Style/>
                                  <Point><coordinates>0,0</coordinates></Point>
                                </Placemark>
                                """)
                        .getBytes(StandardCharsets.UTF_8);
        SourceException cancellation = null;
        for (int additionalChecks = 1;
                additionalChecks <= 32 && cancellation == null;
                additionalChecks++) {
            AtomicInteger checks = new AtomicInteger();
            int threshold = cancellationDocument.length + additionalChecks;
            try {
                KmlFiles.openSnapshot(
                                cancellationDocument,
                                IDENTITY,
                                KmlOpenOptions.defaults(),
                                () -> checks.incrementAndGet() > threshold)
                        .close();
            } catch (SourceException candidate) {
                List<String> codes =
                        candidate.report().entries().stream()
                                .map(io.github.mundanej.map.api.SourceDiagnostic::code)
                                .toList();
                if (codes.equals(List.of("KML_PRESENTATION_IGNORED", "SOURCE_CANCELLED"))) {
                    cancellation = candidate;
                }
            }
        }
        assertNotNull(cancellation);
        assertEquals(
                List.of("KML_PRESENTATION_IGNORED", "SOURCE_CANCELLED"),
                cancellation.report().entries().stream()
                        .map(io.github.mundanej.map.api.SourceDiagnostic::code)
                        .toList());
        assertEquals(
                1, cancellation.terminal().location().orElseThrow().recordNumber().orElseThrow());
    }

    @Test
    void rendersPointAndLineThroughOwnedBinding() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureSource source = open(pointAndLine());
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
                    try {
                        view.setSize(120, 120);
                        view.setViewport(new MapViewport(120, 120, -77.043, 38.894, 0.0003));
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.ownedFeature(
                                                "kml",
                                                "KML",
                                                source,
                                                BuiltInMarkers.filledScreen(
                                                        BuiltInMarker.CIRCLE,
                                                        Rgba.rgb(25, 90, 215),
                                                        18,
                                                        1),
                                                io.github.mundanej.map.api.SolidLineSymbol.of(
                                                        new io.github.mundanej.map.api.SymbolStroke(
                                                                Rgba.rgb(25, 90, 215),
                                                                new io.github.mundanej.map.api
                                                                        .SymbolLength(
                                                                        3,
                                                                        io.github.mundanej.map.api
                                                                                .SymbolUnit
                                                                                .SCREEN_PIXEL)),
                                                        1),
                                                io.github.mundanej.map.api.SolidFillSymbol.of(
                                                        Rgba.rgb(25, 90, 215), 1))));
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(Color.WHITE);
                            graphics.fillRect(0, 0, 120, 120);
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        assertTrue(bluePixels(image, 70, 38, 92, 61) > 40);
                        assertTrue(bluePixels(image, 35, 60, 57, 82) > 8);
                    } finally {
                        view.close();
                    }
                    assertTrue(source.isClosed());
                });
    }

    @Test
    void optionsAndLimitsAreImmutableAndValidated() {
        KmlOpenOptions defaults = KmlOpenOptions.defaults();
        assertEquals(
                defaults,
                defaults.withFormatLimits(defaults.formatLimits())
                        .withSourceLimits(defaults.sourceLimits()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KmlLimits(
                                16_777_216,
                                10,
                                4_000_000,
                                1_000_000,
                                1_000_000,
                                65_536,
                                32,
                                100_000,
                                2_000_000,
                                1_000_000,
                                250_000,
                                65_536,
                                16_777_216,
                                128,
                                268_435_456,
                                256));
        assertThrows(
                NullPointerException.class,
                () -> new KmlOpenOptions(null, defaults.sourceLimits()));
    }

    private static FeatureSource open(String document) {
        return KmlFiles.openSnapshot(
                document.getBytes(StandardCharsets.UTF_8),
                IDENTITY,
                KmlOpenOptions.defaults(),
                CancellationToken.none());
    }

    private static String pointAndLine() {
        return document(
                """
                <Document>
                  <name>Nested example</name>
                  <Folder>
                    <Placemark id="white-house">
                      <name>White House</name>
                      <description>Residence</description>
                      <Point><coordinates>-77.0365,38.8977</coordinates></Point>
                    </Placemark>
                  </Folder>
                  <Placemark>
                    <name>Approach</name>
                    <LineString>
                      <coordinates>-77.05,38.89 -77.0365,38.8977</coordinates>
                    </LineString>
                  </Placemark>
                </Document>
                """);
    }

    private static String document(String feature) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                """
                + feature
                + "\n</kml>";
    }

    private static int bluePixels(
            BufferedImage image, int minimumX, int minimumY, int maximumX, int maximumY) {
        int count = 0;
        for (int y = minimumY; y < maximumY; y++) {
            for (int x = minimumX; x < maximumX; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 0xff;
                int blue = rgb & 0xff;
                if (blue > red + 40) {
                    count++;
                }
            }
        }
        return count;
    }
}
