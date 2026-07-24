package io.github.mundanej.map.io.kml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KmlHardeningTest {
    private static final SourceIdentity IDENTITY =
            new SourceIdentity("kml-hardening", "KML hardening");

    @TempDir Path temporary;

    @Test
    void completeIgnoredGrammarIsBoundedWarnedAndOrdered() {
        FeatureSource source =
                open(
                        document(
                                """
                                <Document>
                                  <name>Container</name>
                                  <visibility>true</visibility>
                                  <open>1</open>
                                  <atom:author xmlns:atom="http://www.w3.org/2005/Atom">
                                    <atom:name>Author</atom:name>
                                  </atom:author>
                                  <address>Address</address>
                                  <Snippet>Snippet</Snippet>
                                  <description>Description</description>
                                  <LookAt><longitude>0</longitude></LookAt>
                                  <styleUrl>file:///kml-secret-canary</styleUrl>
                                  <Style><IconStyle><Icon>
                                    <href>https://invalid.example/kml-secret-canary</href>
                                  </Icon></IconStyle></Style>
                                  <ExtendedData><Data name="ignored"><value>x</value></Data></ExtendedData>
                                  <Placemark>
                                    <name>Point</name>
                                    <Point>
                                      <extrude>false</extrude>
                                      <altitudeMode>clampToGround</altitudeMode>
                                      <coordinates>0,0,3</coordinates>
                                    </Point>
                                  </Placemark>
                                </Document>
                                """));
        assertEquals(1, source.metadata().featureCount().orElseThrow());
        assertEquals(
                List.of(
                        "open",
                        "contact",
                        "contact",
                        "snippet",
                        "view",
                        "styleUrl",
                        "style",
                        "extendedData"),
                source.openingDiagnostics().entries().stream()
                        .filter(entry -> entry.code().equals("KML_PRESENTATION_IGNORED"))
                        .map(entry -> entry.context().get("construct"))
                        .toList());
        assertEquals(
                1,
                source.openingDiagnostics().entries().stream()
                        .filter(entry -> entry.code().equals("KML_ALTITUDE_IGNORED"))
                        .count());
        source.close();

        assertFailure(
                "KML_XML_INVALID",
                Map.of("reason", "order"),
                document(
                        "<Document><description>later</description><name>earlier</name></Document>"));
        assertFailure(
                "KML_XML_INVALID",
                Map.of("reason", "cardinality"),
                document(
                        "<Document><visibility>1</visibility><visibility>true</visibility></Document>"));
        assertFailure(
                "KML_XML_INVALID",
                Map.of("reason", "cardinality"),
                document(
                        "<Document><atom:author xmlns:atom=\"http://www.w3.org/2005/Atom\"/>"
                                + "<atom:author xmlns:atom=\"http://www.w3.org/2005/Atom\"/>"
                                + "</Document>"));
        assertFailure(
                "KML_XML_INVALID",
                Map.of("reason", "order"),
                document(
                        "<Placemark><phoneNumber>later</phoneNumber>"
                                + "<address>earlier</address></Placemark>"));
        assertFailure(
                "KML_XML_INVALID",
                Map.of("reason", "order"),
                document(
                        "<Placemark><Point><coordinates>0,0</coordinates>"
                                + "<extrude>0</extrude></Point></Placemark>"));
    }

    @Test
    void everyRecognizedDynamicOrSemanticConstructFailsClosed() {
        Map<String, String> roots =
                Map.ofEntries(
                        Map.entry("NetworkLink", "network"),
                        Map.entry("GroundOverlay", "overlay"),
                        Map.entry("PhotoOverlay", "overlay"),
                        Map.entry("ScreenOverlay", "overlay"),
                        Map.entry("Model", "model"),
                        Map.entry("Update", "update"),
                        Map.entry("Region", "region"),
                        Map.entry("TimeSpan", "time"),
                        Map.entry("TimeStamp", "time"),
                        Map.entry("Schema", "schema"));
        roots.forEach(
                (element, construct) ->
                        assertFailure(
                                "KML_PROFILE_UNSUPPORTED",
                                Map.of("construct", construct),
                                document("<" + element + "/>")));
        assertFailure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "tour"),
                """
                <kml xmlns="http://www.opengis.net/kml/2.2"
                     xmlns:gx="http://www.google.com/kml/ext/2.2"><gx:Tour/></kml>
                """);
        assertFailure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "visibility"),
                document("<Placemark><visibility>0</visibility></Placemark>"));
        assertFailure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "altitudeMode"),
                document(
                        "<Placemark><Point><altitudeMode>absolute</altitudeMode>"
                                + "<coordinates>0,0</coordinates></Point></Placemark>"));
        assertFailure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "altitudeMode"),
                """
                <kml xmlns="http://www.opengis.net/kml/2.2"
                     xmlns:gx="http://www.google.com/kml/ext/2.2">
                  <Placemark><Point><gx:altitudeMode>relativeToSeaFloor</gx:altitudeMode>
                    <coordinates>0,0</coordinates></Point></Placemark>
                </kml>
                """);
        assertFailure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "extrude"),
                document(
                        "<Placemark><LineString><extrude>1</extrude>"
                                + "<coordinates>0,0 1,1</coordinates></LineString></Placemark>"));
        assertFailure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "tessellate"),
                document(
                        "<Placemark><LineString><tessellate>true</tessellate>"
                                + "<coordinates>0,0 1,1</coordinates></LineString></Placemark>"));
    }

    @Test
    void warningRetentionAndTerminalPrecedenceStayBounded() {
        KmlLimits limits = withWarnings(KmlLimits.defaults(), 2);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        document(
                                                "<Placemark><Style/><Style/><Style/>"
                                                        + "<Point><coordinates>181,0</coordinates>"
                                                        + "</Point></Placemark>"),
                                        limits));
        assertEquals(
                List.of(
                        "KML_PRESENTATION_IGNORED",
                        "KML_PRESENTATION_IGNORED",
                        "KML_VALUE_INVALID"),
                failure.report().entries().stream().map(entry -> entry.code()).toList());
        assertEquals(1, failure.report().omittedWarningCount());
    }

    @Test
    void exactAndOneOverPrimaryLimitsAreDeterministic() {
        String empty = document("<Placemark/>");
        KmlLimits exactInput = withInput(KmlLimits.defaults(), bytes(empty).length);
        open(empty, exactInput).close();
        assertLimit("inputBytes", () -> open(empty + " ", exactInput));

        String deepest =
                document(
                        "<Placemark><MultiGeometry><Polygon><outerBoundaryIs><LinearRing>"
                                + "<coordinates>0,0 1,0 1,1 0,0</coordinates>"
                                + "</LinearRing></outerBoundaryIs></Polygon>"
                                + "</MultiGeometry></Placemark>");
        open(deepest, withDepth(KmlLimits.defaults(), 7)).close();
        assertLimit(
                "xmlDepth",
                () ->
                        open(
                                document(
                                        "<Placemark><Style><a><b><c><d><e><f/></e></d></c></b></a>"
                                                + "</Style><Point><coordinates>0,0</coordinates>"
                                                + "</Point></Placemark>"),
                                withDepth(KmlLimits.defaults(), 7)));

        KmlLimits oneAttribute = withAttributes(KmlLimits.defaults(), 1);
        open(document("<Placemark id=\"one\"/>"), oneAttribute).close();
        assertLimit(
                "attributes",
                () -> open(document("<Placemark id=\"one\" extra=\"two\"/>"), oneAttribute));

        KmlLimits oneNamespace = withNamespaces(KmlLimits.defaults(), 1);
        open(empty, oneNamespace).close();
        assertLimit(
                "namespaceDeclarations",
                () ->
                        open(
                                empty.replace(
                                        "<kml ",
                                        "<kml xmlns:gx=\"http://www.google.com/kml/ext/2.2\" "),
                                oneNamespace));

        KmlLimits oneFeature = withFeatures(KmlLimits.defaults(), 1);
        open(empty, oneFeature).close();
        assertLimit(
                "features",
                () -> open(document("<Document><Placemark/><Placemark/></Document>"), oneFeature));

        KmlLimits oneFeatureDepth = withFeatureDepth(KmlLimits.defaults(), 1);
        open(empty, oneFeatureDepth).close();
        assertLimit(
                "featureDepth",
                () -> open(document("<Document><Placemark/></Document>"), oneFeatureDepth));

        KmlLimits oneNumberCharacter = withNumberCharacters(KmlLimits.defaults(), 1);
        open(
                        document(
                                "<Placemark><Point><coordinates>0,0</coordinates></Point></Placemark>"),
                        oneNumberCharacter)
                .close();
        assertLimit(
                "numberCharacters",
                () ->
                        open(
                                document(
                                        "<Placemark><Point><coordinates>.0,0</coordinates>"
                                                + "</Point></Placemark>"),
                                oneNumberCharacter));
    }

    @Test
    void structuralTextCoordinatePartAndOwnedLimitsAreDeterministic() {
        KmlLimits twoElements = structuralLimits(2, 6);
        open(document("<Placemark/>"), twoElements).close();
        assertLimit(
                "elements", () -> open(document("<Document><Placemark/></Document>"), twoElements));

        KmlLimits sixEvents = structuralLimits(2, 6);
        open(document("<Placemark/>"), sixEvents).close();
        assertLimit("xmlEvents", () -> open(document("<Placemark/><!--x-->"), sixEvents));

        KmlLimits scalar64 = withScalar(KmlLimits.defaults(), 64);
        open(
                        document(
                                "<Placemark><name>"
                                        + "a".repeat(64)
                                        + "</name><Point><coordinates>0,0</coordinates>"
                                        + "</Point></Placemark>"),
                        scalar64)
                .close();
        assertLimit(
                "scalarCharacters",
                () ->
                        open(
                                document(
                                        "<Placemark><name>"
                                                + "a".repeat(65)
                                                + "</name><Point><coordinates>0,0</coordinates>"
                                                + "</Point></Placemark>"),
                                scalar64));

        String textDocument =
                "<kml xmlns=\"http://www.opengis.net/kml/2.2\" "
                        + "xmlns:a=\"urn:aaaaaaaaaaaaaaaaaaaa\" "
                        + "xmlns:b=\"urn:bbbbbbbbbbbbbbbbbbbb\"><Placemark/></kml>";
        int threshold = findTextThreshold(textDocument);
        open(textDocument, withText(KmlLimits.defaults(), threshold)).close();
        assertLimit(
                "textCharacters",
                () -> open(textDocument, withText(KmlLimits.defaults(), threshold - 1)));

        KmlLimits coordinateLimits = withCoordinates(KmlLimits.defaults(), 8, 8, 4);
        String twoRingPolygon =
                document(
                        "<Placemark><Polygon><outerBoundaryIs><LinearRing>"
                                + "<coordinates>0,0 2,0 2,2 0,0</coordinates>"
                                + "</LinearRing></outerBoundaryIs><innerBoundaryIs><LinearRing>"
                                + "<coordinates>.5,.5 1,.5 1,1 .5,.5</coordinates>"
                                + "</LinearRing></innerBoundaryIs></Polygon></Placemark>");
        open(twoRingPolygon, coordinateLimits).close();
        assertLimit(
                "coordinates",
                () ->
                        open(
                                document(
                                        "<Document>"
                                                + twoRingPolygon
                                                        .replaceFirst("(?s).*?<kml[^>]*>", "")
                                                        .replace("</kml>", "")
                                                + "<Placemark><Point><coordinates>3,3</coordinates>"
                                                + "</Point></Placemark></Document>"),
                                coordinateLimits));

        String twoPolygons =
                document(
                        "<Placemark><MultiGeometry>"
                                + polygon("0,0 1,0 1,1 0,0")
                                + polygon("2,2 3,2 3,3 2,2")
                                + "</MultiGeometry></Placemark>");
        open(twoPolygons, withCoordinates(KmlLimits.defaults(), 8, 8, 4)).close();
        assertLimit(
                "parts", () -> open(twoPolygons, withCoordinates(KmlLimits.defaults(), 8, 8, 3)));

        String ownedDocument =
                document(
                        "<Placemark><Polygon><outerBoundaryIs><LinearRing>"
                                + "<coordinates>0,0,0 2,0,0 2,2,0 0,0,0</coordinates>"
                                + "</LinearRing></outerBoundaryIs></Polygon></Placemark>");
        KmlLimits base = compactLimits(bytes(ownedDocument).length, 1_000_000);
        long minimum = minimumOwned(base);
        long ownedThreshold = findOwnedThreshold(ownedDocument, base, minimum, 1_000_000);
        open(ownedDocument, withOwned(base, ownedThreshold)).close();
        assertTrue(ownedThreshold > minimum);
        assertLimit("ownedBytes", () -> open(ownedDocument, withOwned(base, ownedThreshold - 1)));

        byte[] plain =
                bytes(
                        document(
                                "<Placemark><Point><coordinates>0,0</coordinates></Point></Placemark>"));
        byte[] bom = new byte[plain.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(plain, 0, bom, 3, plain.length);
        KmlLimits plainBase = compactLimits(plain.length, 1_000_000);
        KmlLimits bomBase = compactLimits(bom.length, 1_000_000);
        long plainThreshold =
                findOwnedThreshold(
                        plain, plainBase, minimumOwned(plainBase), plainBase.maximumOwnedBytes());
        long bomThreshold =
                findOwnedThreshold(
                        bom, bomBase, minimumOwned(bomBase), bomBase.maximumOwnedBytes());
        assertEquals(259, bomThreshold - plainThreshold);
        open(bom, withOwned(bomBase, bomThreshold)).close();
        assertLimit("ownedBytes", () -> open(bom, withOwned(bomBase, bomThreshold - 1)));
    }

    @Test
    void mutationCancellationAndCleanupPrecedenceAreDeterministic() throws Exception {
        Path path = temporary.resolve("changing.kml");
        Files.writeString(path, document("<Placemark/>"), StandardCharsets.UTF_8);
        AtomicInteger attributes = new AtomicInteger();
        KmlFileAccess mutating =
                new SystemFileAccess() {
                    @Override
                    public BasicFileAttributes readAttributes(Path requested) throws IOException {
                        if (attributes.incrementAndGet() == 2) {
                            Files.writeString(
                                    requested,
                                    document(
                                            "<Placemark><Point><coordinates>1,1</coordinates>"
                                                    + "</Point></Placemark>"),
                                    StandardCharsets.UTF_8);
                            Files.setLastModifiedTime(
                                    requested,
                                    java.nio.file.attribute.FileTime.fromMillis(
                                            System.currentTimeMillis() + 2_000));
                        }
                        return super.readAttributes(requested);
                    }
                };
        SourceException changed =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.open(
                                        path,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        mutating));
        assertEquals(
                Map.of("operation", "read", "reason", "changed"), changed.terminal().context());

        KmlFileAccess staleSize =
                new SystemFileAccess() {
                    @Override
                    public SeekableByteChannel open(Path requested) throws IOException {
                        return new ExtraByteChannel(super.open(requested));
                    }
                };
        SourceException grew =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.open(
                                        path,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        staleSize));
        assertEquals(Map.of("operation", "read", "reason", "changed"), grew.terminal().context());

        for (IOException finalFailure :
                List.of(
                        new java.nio.file.NoSuchFileException("removed"),
                        new java.nio.file.AccessDeniedException("denied"),
                        new IOException("attribute failure"))) {
            AtomicInteger reads = new AtomicInteger();
            KmlFileAccess failingFinalAttributes =
                    new SystemFileAccess() {
                        @Override
                        public BasicFileAttributes readAttributes(Path requested)
                                throws IOException {
                            if (reads.incrementAndGet() == 2) {
                                throw finalFailure;
                            }
                            return super.readAttributes(requested);
                        }
                    };
            SourceException finalFingerprint =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    KmlFiles.open(
                                            path,
                                            IDENTITY,
                                            KmlOpenOptions.defaults(),
                                            CancellationToken.none(),
                                            failingFinalAttributes));
            assertEquals(
                    Map.of("operation", "read", "reason", "changed"),
                    finalFingerprint.terminal().context());
        }

        Path closePath = temporary.resolve("close.kml");
        Files.writeString(closePath, document("<Placemark/>"), StandardCharsets.UTF_8);
        KmlFileAccess closeFailure =
                new SystemFileAccess() {
                    @Override
                    public SeekableByteChannel open(Path requested) throws IOException {
                        return new FailingChannel(super.open(requested), false, true);
                    }
                };
        SourceException close =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.open(
                                        closePath,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        closeFailure));
        assertEquals(Map.of("operation", "close", "reason", "other"), close.terminal().context());

        KmlFileAccess readAndCloseFailure =
                new SystemFileAccess() {
                    @Override
                    public SeekableByteChannel open(Path requested) throws IOException {
                        return new FailingChannel(super.open(requested), true, true);
                    }
                };
        SourceException read =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.open(
                                        closePath,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        readAndCloseFailure));
        assertEquals(Map.of("operation", "read", "reason", "other"), read.terminal().context());
        SourceException suppressed =
                assertInstanceOf(SourceException.class, read.getSuppressed()[0]);
        assertEquals(
                Map.of("operation", "close", "reason", "other"), suppressed.terminal().context());

        AtomicInteger cancellationChecks = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.open(
                                        closePath,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        () -> cancellationChecks.incrementAndGet() >= 2));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
    }

    @Test
    void securityFixturesAndSeededMutationsFailOnlyThroughStructuredOutcomes() throws Exception {
        assertSecurityFixture(
                "doctype-external.kml", "KML_XML_INVALID", Map.of("reason", "doctype"));
        assertSecurityFixture(
                "malformed-truncated.kml", "KML_XML_INVALID", Map.of("reason", "syntax"));
        assertSecurityFixture(
                "foreign-xinclude.kml",
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", "foreignElement"));
        assertEncodingFailure("bom", new byte[] {(byte) 0xff, (byte) 0xfe, 0, 0});
        assertEncodingFailure("utf8", new byte[] {'<', (byte) 0xc3, '(', '>'});

        try (ServerSocket canary =
                new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            canary.setSoTimeout(100);
            String schemaCanary =
                    "<kml xmlns=\"http://www.opengis.net/kml/2.2\""
                            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                            + " xsi:schemaLocation=\"http://127.0.0.1:"
                            + canary.getLocalPort()
                            + "/kml-secret-canary schema.xsd\"><Placemark/></kml>";
            assertFailure(
                    "KML_PROFILE_UNSUPPORTED", Map.of("construct", "attribute"), schemaCanary);
            assertThrows(SocketTimeoutException.class, canary::accept);
        }

        byte[] seed =
                bytes(
                        document(
                                "<Placemark><Point><coordinates>1,2</coordinates>"
                                        + "</Point></Placemark>"));
        SplittableRandom random = new SplittableRandom(0x4b4d4cL);
        for (int sample = 0; sample < 96; sample++) {
            byte[] mutated = seed.clone();
            int changes = 1 + random.nextInt(4);
            for (int change = 0; change < changes; change++) {
                mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
            }
            try {
                KmlFiles.openSnapshot(
                                mutated,
                                IDENTITY,
                                KmlOpenOptions.defaults(),
                                CancellationToken.none())
                        .close();
            } catch (SourceException expected) {
                assertTrue(
                        Set.of(
                                        "KML_ENCODING_INVALID",
                                        "KML_XML_INVALID",
                                        "KML_PROFILE_UNSUPPORTED",
                                        "KML_VALUE_INVALID",
                                        "SOURCE_LIMIT_EXCEEDED")
                                .contains(expected.terminal().code()));
                assertFalse(expected.toString().contains("[B@"));
            }
        }
    }

    @Test
    void limitHardMaximaAndCrossFieldArithmeticAreValidatedAtConstruction() {
        KmlLimits maximum =
                new KmlLimits(
                        268_435_456,
                        128,
                        32_000_000,
                        8_000_000,
                        8_000_000,
                        1_048_576,
                        64,
                        1_000_000,
                        16_000_000,
                        16_000_000,
                        2_000_000,
                        1_048_576,
                        134_217_728,
                        256,
                        1_073_741_824,
                        4_096);
        assertEquals(268_435_456, maximum.maximumInputBytes());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KmlLimits(
                                268_435_457,
                                128,
                                32_000_000,
                                8_000_000,
                                8_000_000,
                                1_048_576,
                                64,
                                1_000_000,
                                16_000_000,
                                16_000_000,
                                2_000_000,
                                1_048_576,
                                134_217_728,
                                256,
                                1_073_741_824,
                                4_096));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KmlLimits(
                                4_096, 7, 10, 10, 20, 10, 1, 1, 2, 2, 1, 32, 64, 16, 10_000, 2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KmlLimits(
                                4_096, 16, 100, 20, 20, 10, 2, 2, 10, 11, 2, 32, 64, 16, 10_000,
                                2));
    }

    private static String polygon(String coordinates) {
        return "<Polygon><outerBoundaryIs><LinearRing><coordinates>"
                + coordinates
                + "</coordinates></LinearRing></outerBoundaryIs></Polygon>";
    }

    private static void assertSecurityFixture(String name, String code, Map<String, String> context)
            throws Exception {
        String root = "/io/github/mundanej/map/io/kml/security/";
        byte[] content;
        try (InputStream input = KmlHardeningTest.class.getResourceAsStream(root + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing KML security fixture: " + name);
            }
            content = input.readAllBytes();
        }
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.openSnapshot(
                                        content,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals(code, failure.terminal().code());
        assertEquals(context, failure.terminal().context());
        assertFalse(failure.toString().contains("kml-secret-canary"));
    }

    private static void assertEncodingFailure(String reason, byte[] content) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                KmlFiles.openSnapshot(
                                        content,
                                        IDENTITY,
                                        KmlOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("KML_ENCODING_INVALID", failure.terminal().code());
        assertEquals(Map.of("reason", reason), failure.terminal().context());
    }

    private static FeatureSource open(String document) {
        return open(document, KmlLimits.defaults());
    }

    private static FeatureSource open(String document, KmlLimits limits) {
        return open(bytes(document), limits);
    }

    private static FeatureSource open(byte[] content, KmlLimits limits) {
        return KmlFiles.openSnapshot(
                content,
                IDENTITY,
                KmlOpenOptions.defaults().withFormatLimits(limits),
                CancellationToken.none());
    }

    private static void assertFailure(String code, Map<String, String> context, String document) {
        SourceException failure = assertThrows(SourceException.class, () -> open(document));
        assertEquals(code, failure.terminal().code());
        assertEquals(context, failure.terminal().context());
    }

    private static void assertLimit(String limit, Runnable operation) {
        SourceException failure = assertThrows(SourceException.class, operation::run);
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
    }

    private static byte[] bytes(String document) {
        return document.getBytes(StandardCharsets.UTF_8);
    }

    private static String document(String feature) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\">"
                + feature
                + "</kml>";
    }

    private static int findTextThreshold(String document) {
        int low = 64;
        int high = 1_024;
        while (low < high) {
            int middle = low + (high - low) / 2;
            try {
                open(document, withText(KmlLimits.defaults(), middle)).close();
                high = middle;
            } catch (SourceException failure) {
                assertEquals("textCharacters", failure.terminal().context().get("limit"));
                low = middle + 1;
            }
        }
        return low;
    }

    private static long findOwnedThreshold(String document, KmlLimits base, long low, long high) {
        return findOwnedThreshold(bytes(document), base, low, high);
    }

    private static long findOwnedThreshold(byte[] content, KmlLimits base, long low, long high) {
        while (low < high) {
            long middle = low + (high - low) / 2;
            try {
                open(content, withOwned(base, middle)).close();
                high = middle;
            } catch (SourceException failure) {
                assertEquals("ownedBytes", failure.terminal().context().get("limit"));
                low = middle + 1;
            }
        }
        return low;
    }

    private static KmlLimits withWarnings(KmlLimits value, int warnings) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                warnings);
    }

    private static KmlLimits withInput(KmlLimits value, int input) {
        return copy(
                value,
                input,
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withDepth(KmlLimits value, int depth) {
        return copy(
                value,
                value.maximumInputBytes(),
                depth,
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                1,
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withAttributes(KmlLimits value, int attributes) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                attributes,
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withNamespaces(KmlLimits value, int namespaces) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                namespaces,
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withFeatures(KmlLimits value, int features) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                features,
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withFeatureDepth(KmlLimits value, int featureDepth) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                featureDepth,
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withNumberCharacters(KmlLimits value, int characters) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                characters,
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withScalar(KmlLimits value, int scalar) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                scalar,
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withText(KmlLimits value, int text) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                Math.min(value.maximumScalarCharacters(), text),
                text,
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withCoordinates(
            KmlLimits value, int total, int perGeometry, int parts) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                total,
                perGeometry,
                parts,
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static KmlLimits withOwned(KmlLimits value, long owned) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumFeatureDepth(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerGeometry(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                owned,
                value.retainedWarnings());
    }

    private static KmlLimits structuralLimits(int elements, int events) {
        return new KmlLimits(
                4_096, 8, events, elements, 32, 16, 1, 1, 4, 4, 2, 128, 2_048, 32, 100_000, 8);
    }

    private static KmlLimits compactLimits(int inputBytes, long ownedBytes) {
        return new KmlLimits(
                inputBytes, 16, 202, 100, 64, 16, 1, 1, 4, 4, 1, 64, 300, 32, ownedBytes, 8);
    }

    private static long minimumOwned(KmlLimits value) {
        return Math.addExact(
                value.maximumInputBytes(),
                Math.addExact(
                        Math.multiplyExact(16L, value.maximumTotalCoordinates()),
                        Math.addExact(
                                Math.multiplyExact(4L, value.maximumParts()),
                                Math.addExact(
                                        Math.multiplyExact(8L, value.maximumPhysicalFeatures()),
                                        Math.multiplyExact(2L, value.maximumTextCharacters())))));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static KmlLimits copy(
            KmlLimits ignored,
            int input,
            int depth,
            int events,
            int elements,
            int attributes,
            int namespaces,
            int featureDepth,
            int features,
            int coordinates,
            int geometryCoordinates,
            int parts,
            int scalarCharacters,
            int textCharacters,
            int numberCharacters,
            long ownedBytes,
            int warnings) {
        java.util.Objects.requireNonNull(ignored, "ignored");
        return new KmlLimits(
                input,
                depth,
                events,
                elements,
                attributes,
                namespaces,
                featureDepth,
                features,
                coordinates,
                geometryCoordinates,
                parts,
                scalarCharacters,
                textCharacters,
                numberCharacters,
                ownedBytes,
                warnings);
    }

    private static class SystemFileAccess implements KmlFileAccess {
        @Override
        public BasicFileAttributes readAttributes(Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public SeekableByteChannel open(Path path) throws IOException {
            return Files.newByteChannel(
                    path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        }
    }

    private static final class FailingChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        private final boolean failRead;
        private final boolean failClose;

        FailingChannel(SeekableByteChannel delegate, boolean failRead, boolean failClose) {
            this.delegate = delegate;
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public int read(ByteBuffer target) throws IOException {
            if (failRead) {
                throw new IOException("injected read failure");
            }
            return delegate.read(target);
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
            if (failClose) {
                throw new IOException("injected close failure");
            }
        }
    }

    private static final class ExtraByteChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        private boolean extraReturned;

        ExtraByteChannel(SeekableByteChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(ByteBuffer target) throws IOException {
            int count = delegate.read(target);
            if (count >= 0 || extraReturned || !target.hasRemaining()) {
                return count;
            }
            target.put((byte) 'x');
            extraReturned = true;
            return 1;
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() throws IOException {
            return delegate.position() + (extraReturned ? 1 : 0);
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            extraReturned = false;
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
