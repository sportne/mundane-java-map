package io.github.mundanej.map.io.gpx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
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
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GpxFilesTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("gpx-test", "GPX test");

    @TempDir Path temporary;

    @Test
    void opensOrderedWaypointsWithFixedSchemaCrsExtentAndCanonicalValues() throws Exception {
        Path path = temporary.resolve("waypoints.gpx");
        Files.writeString(path, waypoints(), StandardCharsets.UTF_8);

        FeatureSource source =
                GpxFiles.open(path, IDENTITY, GpxOpenOptions.defaults(), CancellationToken.none());
        assertEquals(2, source.metadata().featureCount().orElseThrow());
        assertEquals(
                new io.github.mundanej.map.api.Envelope(-77.04, 38.89, -76.5, 39.25),
                source.metadata().extent().orElseThrow());
        assertEquals(
                "EPSG:4326",
                source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
        var fields = source.metadata().schema().orElseThrow().fields();
        assertEquals(
                List.of(
                        "gpxKind",
                        "trackIndex",
                        "segmentIndex",
                        "elevationMetres",
                        "time",
                        "comment",
                        "description",
                        "source",
                        "symbol",
                        "type",
                        "trackNumber"),
                fields.stream().map(io.github.mundanej.map.api.AttributeField::name).toList());
        assertEquals(AttributeType.FLOATING, fields.get(3).type());

        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            FeatureRecord first = cursor.current();
            assertEquals("gpx:wpt:1", first.id());
            assertEquals("Alpha", first.name());
            assertEquals(
                    new Coordinate(-77.04, 38.89),
                    assertInstanceOf(PointGeometry.class, first.geometry()).coordinate());
            assertEquals("waypoint", first.attributes().get("gpxKind"));
            assertEquals(12.5, first.attributes().get("elevationMetres"));
            assertEquals("2024-01-02T03:04:05Z", first.attributes().get("time"));
            assertEquals("Comment", first.attributes().get("comment"));
            assertEquals(AttributeNull.INSTANCE, first.attributes().get("trackIndex"));

            assertTrue(cursor.advance());
            FeatureRecord second = cursor.current();
            assertEquals("gpx:wpt:2", second.id());
            assertEquals("", second.name());
            assertEquals(AttributeNull.INSTANCE, second.attributes().get("elevationMetres"));
            assertFalse(cursor.advance());
        }
        source.close();
        assertTrue(source.isClosed());
    }

    @Test
    void queryProjectionBoundsAndCursorLifecycleUseOrdinarySourceBehavior() {
        FeatureSource source = open(waypoints());
        FeatureQuery query =
                new FeatureQuery(
                        java.util.Optional.of(
                                new io.github.mundanej.map.api.Envelope(-77.1, 38.8, -77.0, 39.0)),
                        io.github.mundanej.map.api.AttributeSelection.only(
                                List.of("gpxKind", "time")),
                        java.util.Optional.empty());
        FeatureCursor cursor = source.openCursor(query, CancellationToken.none());
        assertTrue(cursor.advance());
        assertEquals(
                List.of("gpxKind", "time"),
                cursor.current().attributes().keySet().stream().toList());
        assertFalse(cursor.advance());
        cursor.close();
        assertTrue(cursor.isClosed());
        assertThrows(IllegalStateException.class, cursor::current);

        FeatureCursor second = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        source.close();
        assertTrue(second.isClosed());
        assertThrows(IllegalStateException.class, second::advance);
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
    }

    @Test
    void cancellationMalformedValuesAndUnsupportedConstructsAreStructured() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        SourceException cancellation =
                assertThrows(
                        SourceException.class,
                        () ->
                                GpxFiles.openSnapshot(
                                        bytes(waypoints()),
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        cancelled::get));
        assertEquals("SOURCE_CANCELLED", cancellation.terminal().code());
        assertEquals(Map.of("operation", "gpx-open"), cancellation.terminal().context());

        assertFailure(
                "GPX_VALUE_INVALID",
                Map.of("field", "latitude", "reason", "range"),
                waypoint("91", "0", ""));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "syntax"),
                "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\""
                        + " creator=\"test\"><wpt lat=\"0\" lon=\"0\"></gpx>");
        assertFailure("GPX_PROFILE_UNSUPPORTED", Map.of("construct", "route"), root("<rte/>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "cardinality"),
                root("<extensions/><extensions/>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "cardinality"),
                root("<wpt lat=\"0\" lon=\"0\"/>").replace("version=\"1.1\"", "version=\"1.0\""));
    }

    @Test
    void parserPolicyRejectsXml11DoctypeAndEntityWithoutResolving() {
        assertFailure(
                "GPX_ENCODING_INVALID",
                Map.of("reason", "xmlVersion"),
                root("").replace(
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                                "<?xml version = \"1.1\" encoding = \"UTF-8\"?>"));
        assertFailure(
                "GPX_ENCODING_INVALID",
                Map.of("reason", "declaredEncoding"),
                root("").replace(
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                                "<?xml version=\"1.0\""
                                        + " ".repeat(600)
                                        + "encoding=\"US-ASCII\"?>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "doctype"),
                root("").replace(
                                "<gpx ",
                                "<!DOCTYPE gpx SYSTEM \"file:///does-not-exist\">" + "<gpx "));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "doctype"),
                root("<wpt lat=\"0\" lon=\"0\"><name>&private;</name></wpt>")
                        .replace(
                                "<gpx ",
                                "<!DOCTYPE gpx [<!ENTITY private SYSTEM"
                                        + " \"file:///does-not-exist\">]><gpx "));
    }

    @Test
    void localOpenRejectsSymbolicLinks() throws Exception {
        Path target = temporary.resolve("target.gpx");
        Path link = temporary.resolve("link.gpx");
        Files.writeString(target, waypoints(), StandardCharsets.UTF_8);
        Files.createSymbolicLink(link, target.getFileName());

        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GpxFiles.open(
                                        link,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GPX_IO_FAILED", failure.terminal().code());
        assertEquals(
                Map.of("operation", "attributes", "reason", "other"), failure.terminal().context());
    }

    @Test
    void bomIgnoredFieldsAndExtensionsProduceOrderedBoundedWarnings() {
        byte[] document =
                bytes(
                        root(
                                "<metadata><name>meta</name></metadata>"
                                        + "<wpt lat=\"0\" lon=\"0\"><magvar>3</magvar>"
                                        + "<extensions><x:data xmlns:x=\"urn:test\">ignored</x:data></extensions>"
                                        + "</wpt>"));
        byte[] bom = new byte[document.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(document, 0, bom, 3, document.length);

        FeatureSource source =
                GpxFiles.openSnapshot(
                        bom, IDENTITY, GpxOpenOptions.defaults(), CancellationToken.none());
        assertEquals(
                List.of(
                        "GPX_UTF8_BOM_IGNORED",
                        "GPX_FIELD_IGNORED",
                        "GPX_FIELD_IGNORED",
                        "GPX_EXTENSION_IGNORED"),
                source.openingDiagnostics().entries().stream()
                        .map(io.github.mundanej.map.api.SourceDiagnostic::code)
                        .toList());
        source.close();
    }

    @Test
    void opensOrderedTrackSegmentsWithInheritedAttributesAndLiteralDatelineCoordinates() {
        FeatureSource source =
                open(
                        root(
                                "<wpt lat=\"1\" lon=\"2\"><name>First</name></wpt>"
                                        + "<trk><name>Patrol</name><cmt>Comment</cmt>"
                                        + "<desc>Description</desc><src>Survey</src>"
                                        + "<number>7</number><type>surface</type>"
                                        + "<trkseg><trkpt lat=\"10\" lon=\"179\"/>"
                                        + "<trkpt lat=\"11\" lon=\"-179\"/></trkseg>"
                                        + "<trkseg><trkpt lat=\"12\" lon=\"-178\"/>"
                                        + "<trkpt lat=\"13\" lon=\"-177\"/></trkseg></trk>"));

        try (source;
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals("gpx:wpt:1", cursor.current().id());

            assertTrue(cursor.advance());
            FeatureRecord firstSegment = cursor.current();
            assertEquals("gpx:trk:1:seg:1", firstSegment.id());
            assertEquals("Patrol", firstSegment.name());
            assertEquals("trackSegment", firstSegment.attributes().get("gpxKind"));
            assertEquals(1L, firstSegment.attributes().get("trackIndex"));
            assertEquals(1L, firstSegment.attributes().get("segmentIndex"));
            assertEquals("Comment", firstSegment.attributes().get("comment"));
            assertEquals("Description", firstSegment.attributes().get("description"));
            assertEquals("Survey", firstSegment.attributes().get("source"));
            assertEquals("surface", firstSegment.attributes().get("type"));
            assertEquals(7L, firstSegment.attributes().get("trackNumber"));
            LineStringGeometry firstLine =
                    assertInstanceOf(LineStringGeometry.class, firstSegment.geometry());
            assertEquals(2, firstLine.coordinates().size());
            assertEquals(179, firstLine.coordinates().x(0));
            assertEquals(-179, firstLine.coordinates().x(1));

            assertTrue(cursor.advance());
            FeatureRecord secondSegment = cursor.current();
            assertEquals("gpx:trk:1:seg:2", secondSegment.id());
            assertEquals(2L, secondSegment.attributes().get("segmentIndex"));
            assertFalse(cursor.advance());
        }
    }

    @Test
    void skippedSegmentsAndTrackPointDataProduceStableWarnings() {
        FeatureSource source =
                open(
                        root(
                                "<trk><link href=\"https://example.invalid/ignored\"/>"
                                        + "<extensions><x:track xmlns:x=\"urn:test\"/></extensions>"
                                        + "<trkseg/>"
                                        + "<trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg>"
                                        + "<trkseg><trkpt lat=\"1\" lon=\"1\">"
                                        + "<ele>4</ele><time>2024-01-02T03:04:05Z</time>"
                                        + "<name>ignored</name>"
                                        + "<extensions><x:point xmlns:x=\"urn:test\"/></extensions>"
                                        + "</trkpt><trkpt lat=\"2\" lon=\"2\"/></trkseg>"
                                        + "</trk>"));
        assertEquals(1, source.metadata().featureCount().orElseThrow());
        assertEquals(
                List.of(
                        "GPX_FIELD_IGNORED",
                        "GPX_EXTENSION_IGNORED",
                        "GPX_TRACK_SEGMENT_SKIPPED",
                        "GPX_TRACK_SEGMENT_SKIPPED",
                        "GPX_TRACK_POINT_DATA_IGNORED",
                        "GPX_TRACK_POINT_DATA_IGNORED",
                        "GPX_TRACK_POINT_DATA_IGNORED",
                        "GPX_EXTENSION_IGNORED"),
                source.openingDiagnostics().entries().stream()
                        .map(io.github.mundanej.map.api.SourceDiagnostic::code)
                        .toList());
        assertEquals(
                List.of("empty", "singlePoint"),
                source.openingDiagnostics().entries().stream()
                        .filter(diagnostic -> diagnostic.code().equals("GPX_TRACK_SEGMENT_SKIPPED"))
                        .map(diagnostic -> diagnostic.context().get("reason"))
                        .toList());
        source.close();
    }

    @Test
    void segmentAndGeometryCoordinateLimitsAreProspectiveAndIndexed() {
        GpxLimits onePart = limits(1, 10);
        FeatureSource exactPart =
                open(
                        root(
                                "<trk><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                        + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>"),
                        onePart);
        exactPart.close();
        SourceException partFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        root(
                                                "<trk><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                                        + "<trkpt lat=\"1\" lon=\"1\"/></trkseg>"
                                                        + "<trkseg><trkpt lat=\"2\" lon=\"2\"/>"
                                                        + "<trkpt lat=\"3\" lon=\"3\"/></trkseg></trk>"),
                                        onePart));
        assertEquals("SOURCE_LIMIT_EXCEEDED", partFailure.terminal().code());
        assertEquals("parts", partFailure.terminal().context().get("limit"));
        assertEquals("2", partFailure.terminal().context().get("requested"));
        assertEquals("1", partFailure.terminal().context().get("maximum"));

        GpxLimits twoCoordinates = limits(10, 2);
        FeatureSource exactCoordinates =
                open(
                        root(
                                "<trk><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                        + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>"),
                        twoCoordinates);
        exactCoordinates.close();
        SourceException coordinateFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        root(
                                                "<trk><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                                        + "<trkpt lat=\"1\" lon=\"1\"/>"
                                                        + "<trkpt lat=\"2\" lon=\"2\"/></trkseg></trk>"),
                                        twoCoordinates));
        assertEquals("SOURCE_LIMIT_EXCEEDED", coordinateFailure.terminal().code());
        assertEquals(
                Map.of(
                        "scope",
                        "gpxOpen",
                        "limit",
                        "geometryCoordinates",
                        "requested",
                        "3",
                        "maximum",
                        "2",
                        "pointIndex",
                        "2"),
                coordinateFailure.terminal().context());
    }

    @Test
    void malformedLaterTrackPointIncludesCandidateAndZeroBasedPointIndex() {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        root(
                                                "<wpt lat=\"4\" lon=\"5\"/>"
                                                        + "<trk><trkseg>"
                                                        + "<trkpt lat=\"0\" lon=\"0\"/>"
                                                        + "<trkpt lat=\"91\" lon=\"1\"/>"
                                                        + "</trkseg></trk>")));
        assertEquals("GPX_VALUE_INVALID", failure.terminal().code());
        assertEquals(
                Map.of("field", "latitude", "reason", "range", "pointIndex", "1"),
                failure.terminal().context());
        assertEquals(2, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
    }

    @Test
    void rendersWaypointThroughOwnedFeatureSourceBinding() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureSource source =
                            open(root("<wpt lat=\"0\" lon=\"0\"><name>P</name></wpt>"));
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                    try {
                        view.setSize(100, 100);
                        view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.ownedFeature(
                                                "gpx",
                                                "GPX",
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
                                                                        2,
                                                                        io.github.mundanej.map.api
                                                                                .SymbolUnit
                                                                                .SCREEN_PIXEL)),
                                                        1),
                                                io.github.mundanej.map.api.SolidFillSymbol.of(
                                                        Rgba.rgb(25, 90, 215), 1))));
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(Color.WHITE);
                            graphics.fillRect(0, 0, 100, 100);
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        assertTrue(bluePixels(image) > 80);
                    } finally {
                        view.close();
                    }
                    assertTrue(source.isClosed());
                });
    }

    @Test
    void optionsAndLimitConstructionAreImmutableAndValidated() {
        GpxOpenOptions defaults = GpxOpenOptions.defaults();
        assertEquals(
                defaults,
                defaults.withFormatLimits(defaults.formatLimits())
                        .withSourceLimits(defaults.sourceLimits()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GpxLimits(
                                0,
                                64,
                                4_000_000,
                                1_000_000,
                                1_000_000,
                                65_536,
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
                () -> new GpxOpenOptions(null, defaults.sourceLimits()));
    }

    private static FeatureSource open(String document) {
        return GpxFiles.openSnapshot(
                bytes(document), IDENTITY, GpxOpenOptions.defaults(), CancellationToken.none());
    }

    private static FeatureSource open(String document, GpxLimits limits) {
        return GpxFiles.openSnapshot(
                bytes(document),
                IDENTITY,
                GpxOpenOptions.defaults().withFormatLimits(limits),
                CancellationToken.none());
    }

    private static GpxLimits limits(int maximumParts, int maximumCoordinatesPerSegment) {
        GpxLimits defaults = GpxLimits.defaults();
        return new GpxLimits(
                defaults.maximumInputBytes(),
                defaults.maximumXmlDepth(),
                defaults.maximumXmlEvents(),
                defaults.maximumElements(),
                defaults.maximumAttributes(),
                defaults.maximumNamespaceDeclarations(),
                defaults.maximumPhysicalFeatures(),
                defaults.maximumTotalCoordinates(),
                maximumCoordinatesPerSegment,
                maximumParts,
                defaults.maximumScalarCharacters(),
                defaults.maximumTextCharacters(),
                defaults.maximumNumberCharacters(),
                defaults.maximumOwnedBytes(),
                defaults.retainedWarnings());
    }

    private static void assertFailure(String code, Map<String, String> context, String document) {
        SourceException failure = assertThrows(SourceException.class, () -> open(document));
        assertEquals(code, failure.terminal().code());
        assertEquals(context, failure.terminal().context());
        assertTrue(failure.terminal().location().orElseThrow().component().isPresent());
    }

    private static byte[] bytes(String document) {
        return document.getBytes(StandardCharsets.UTF_8);
    }

    private static String waypoint(String latitude, String longitude, String children) {
        return root(
                "<wpt lat=\"" + latitude + "\" lon=\"" + longitude + "\">" + children + "</wpt>");
    }

    private static String root(String content) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" "
                + "version=\"1.1\" creator=\"mundane-map-test\">"
                + content
                + "</gpx>";
    }

    private static String waypoints() {
        return root(
                "<wpt lat=\"38.89\" lon=\"-77.04\">"
                        + "<ele>12.5</ele><time>2024-01-01T22:04:05-05:00</time>"
                        + "<name>Alpha</name><cmt>Comment</cmt><desc>Description</desc>"
                        + "<src>Survey</src><sym>Flag</sym><type>poi</type></wpt>"
                        + "<wpt lat=\"39.25\" lon=\"-76.5\"/>");
    }

    private static int bluePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int packed = image.getRGB(x, y);
                int red = packed >>> 16 & 0xff;
                int green = packed >>> 8 & 0xff;
                int blue = packed & 0xff;
                if (blue > red + 50 && blue > green + 40) {
                    count++;
                }
            }
        }
        return count;
    }
}
