package io.github.mundanej.map.io.gpx;

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

class GpxHardeningTest {
    private static final SourceIdentity IDENTITY =
            new SourceIdentity("gpx-hardening", "GPX hardening");

    @TempDir Path temporary;

    @Test
    void completeIgnoredGrammarIsBoundedWarnedAndStructurallyValidated() {
        FeatureSource source =
                open(
                        root(
                                "<metadata>"
                                        + "<name>Metadata</name><desc>Description</desc>"
                                        + "<author><name>Author</name>"
                                        + "<email id=\"person\" domain=\"example.test\"/>"
                                        + "<link href=\"https://example.test/author\">"
                                        + "<text>Author link</text><type>text/plain</type></link>"
                                        + "</author>"
                                        + "<copyright author=\"Example\"><year>2024</year>"
                                        + "<license>https://example.test/license</license></copyright>"
                                        + "<link href=\"https://example.test/one\"/>"
                                        + "<link href=\"https://example.test/two\"><text>Two</text></link>"
                                        + "<time>not-retained</time><keywords>one two</keywords>"
                                        + "<bounds minlat=\"-1\" minlon=\"-2\" maxlat=\"3\" maxlon=\"4\"/>"
                                        + "<extensions><x:any xmlns:x=\"urn:test\" arbitrary=\"yes\">"
                                        + "<x:nested/></x:any></extensions></metadata>"
                                        + "<wpt lat=\"0\" lon=\"0\">"
                                        + "<magvar>1</magvar><geoidheight>2</geoidheight>"
                                        + "<name>Waypoint</name>"
                                        + "<link href=\"https://example.test/wpt\"><text>W</text></link>"
                                        + "<fix>3d</fix><sat>4</sat><hdop>1</hdop><vdop>2</vdop>"
                                        + "<pdop>3</pdop><ageofdgpsdata>4</ageofdgpsdata>"
                                        + "<dgpsid>5</dgpsid><extensions><x:w xmlns:x=\"urn:test\"/>"
                                        + "</extensions></wpt>"
                                        + "<trk><link href=\"https://example.test/track\"/>"
                                        + "<trkseg><trkpt lat=\"1\" lon=\"1\">"
                                        + "<name>ignored</name><link href=\"https://example.test/p\"/>"
                                        + "</trkpt><trkpt lat=\"2\" lon=\"2\"/></trkseg></trk>"));

        assertEquals(2, source.metadata().featureCount().orElseThrow());
        assertTrue(source.openingDiagnostics().entries().size() >= 15);
        assertTrue(
                source.openingDiagnostics().entries().stream()
                        .allMatch(diagnostic -> diagnostic.severity().name().equals("WARNING")));
        source.close();

        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "cardinality"),
                root("<metadata><name>one</name><name>two</name></metadata>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "order"),
                root("<metadata><desc>later</desc><name>earlier</name></metadata>"));
        assertFailure(
                "GPX_PROFILE_UNSUPPORTED",
                Map.of("construct", "attribute"),
                root("<wpt lat=\"0\" lon=\"0\"><name extra=\"x\">bad</name></wpt>"));
        assertFailure(
                "GPX_PROFILE_UNSUPPORTED",
                Map.of("construct", "foreignElement"),
                root("<foreign xmlns=\"urn:foreign\"/>"));
        assertFailure("GPX_PROFILE_UNSUPPORTED", Map.of("construct", "route"), root("<rte/>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "cardinality"),
                root("<wpt lat=\"0\" lon=\"0\"><magvar>1</magvar><magvar>2</magvar></wpt>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "cardinality"),
                root("<trk><extensions/><extensions/></trk>"));
        assertFailure(
                "GPX_XML_INVALID",
                Map.of("reason", "cardinality", "pointIndex", "0"),
                root(
                        "<trk><trkseg><trkpt lat=\"0\" lon=\"0\">"
                                + "<extensions/><extensions/></trkpt>"
                                + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>"));
    }

    @Test
    void warningRetentionAndTerminalPrecedenceStayBounded() {
        GpxLimits limits = withRetainedWarnings(GpxLimits.defaults(), 2);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                open(
                                        root(
                                                "<metadata><name>a</name><desc>b</desc>"
                                                        + "<time>c</time></metadata>"
                                                        + "<wpt lat=\"91\" lon=\"0\"/>"),
                                        limits));
        assertEquals("GPX_VALUE_INVALID", failure.terminal().code());
        assertEquals(3, failure.report().entries().size());
        assertEquals(
                List.of("GPX_FIELD_IGNORED", "GPX_FIELD_IGNORED", "GPX_VALUE_INVALID"),
                failure.report().entries().stream().map(entry -> entry.code()).toList());
        assertEquals(1, failure.report().omittedWarningCount());
    }

    @Test
    void exactAndOneOverInputDepthAttributeNamespaceFeatureAndNumberLimits() {
        String empty = root("");
        GpxLimits exactInput = withInputBytes(GpxLimits.defaults(), bytes(empty).length);
        open(empty, exactInput).close();
        assertLimit("inputBytes", () -> open(empty + " ", exactInput));

        GpxLimits depthOne = withXmlDepth(GpxLimits.defaults(), 1);
        open(empty, depthOne).close();
        assertLimit("xmlDepth", () -> open(root("<wpt lat=\"0\" lon=\"0\"/>"), depthOne));

        GpxLimits twoAttributes = withAttributes(GpxLimits.defaults(), 2);
        open(empty, twoAttributes).close();
        assertLimit(
                "attributes",
                () ->
                        open(
                                empty.replace(
                                                "<gpx ",
                                                "<gpx xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                                        .replace(
                                                " version=\"1.1\"",
                                                " xsi:schemaLocation=\"urn:a urn:b\" version=\"1.1\""),
                                twoAttributes));

        GpxLimits oneNamespace = withNamespaces(GpxLimits.defaults(), 1);
        open(empty, oneNamespace).close();
        assertLimit(
                "namespaceDeclarations",
                () -> open(empty.replace("<gpx ", "<gpx xmlns:x=\"urn:test\" "), oneNamespace));

        GpxLimits oneFeature = withFeatures(GpxLimits.defaults(), 1);
        open(root("<wpt lat=\"0\" lon=\"0\"/>"), oneFeature).close();
        assertLimit(
                "features",
                () ->
                        open(
                                root("<wpt lat=\"0\" lon=\"0\"/>" + "<wpt lat=\"1\" lon=\"1\"/>"),
                                oneFeature));

        GpxLimits oneNumberCharacter = withNumberCharacters(GpxLimits.defaults(), 1);
        open(root("<wpt lat=\"0\" lon=\"0\"/>"), oneNumberCharacter).close();
        assertLimit(
                "numberCharacters",
                () -> open(root("<wpt lat=\".0\" lon=\"0\"/>"), oneNumberCharacter));
    }

    @Test
    void structuralEventElementScalarTextCoordinateAndOwnedLimitsAreDeterministic() {
        GpxLimits twoElements = structuralLimits(2, 6);
        open(root("<wpt lat=\"0\" lon=\"0\"/>"), twoElements).close();
        assertLimit(
                "elements",
                () -> open(root("<metadata/>" + "<wpt lat=\"0\" lon=\"0\"/>"), twoElements));

        GpxLimits fourEvents = structuralLimits(1, 4);
        open(root(""), fourEvents).close();
        assertLimit("xmlEvents", () -> open(root("<!--extra-event-->"), fourEvents));

        GpxLimits scalar64 = withScalarCharacters(GpxLimits.defaults(), 64);
        open(root("<wpt lat=\"0\" lon=\"0\"><name>" + "a".repeat(64) + "</name></wpt>"), scalar64)
                .close();
        String exactToken = "x".repeat(64);
        open(
                        root(
                                "<!--"
                                        + exactToken
                                        + "--><?review "
                                        + exactToken
                                        + "?><extensions><x:"
                                        + exactToken
                                        + " xmlns:x=\"urn:test\" value=\""
                                        + exactToken
                                        + "\">"
                                        + exactToken
                                        + "</x:"
                                        + exactToken
                                        + "></extensions>"),
                        scalar64)
                .close();
        assertLimit(
                "scalarCharacters",
                () ->
                        open(
                                root(
                                        "<wpt lat=\"0\" lon=\"0\"><name>"
                                                + "a".repeat(65)
                                                + "</name></wpt>"),
                                scalar64));
        assertLimit(
                "scalarCharacters",
                () ->
                        open(
                                root(
                                        "<extensions><x:"
                                                + "n".repeat(65)
                                                + " xmlns:x=\"urn:test\"/></extensions>"),
                                scalar64));
        assertLimit(
                "scalarCharacters",
                () ->
                        open(
                                root(
                                        "<extensions><x:item xmlns:x=\"urn:test\" value=\""
                                                + "v".repeat(65)
                                                + "\"/></extensions>"),
                                scalar64));
        assertLimit(
                "scalarCharacters",
                () ->
                        open(
                                root(
                                        "<extensions><x:item xmlns:x=\"urn:test\">"
                                                + "t".repeat(65)
                                                + "</x:item></extensions>"),
                                scalar64));
        assertLimit(
                "scalarCharacters", () -> open(root("<!--" + "c".repeat(65) + "-->"), scalar64));
        assertLimit(
                "scalarCharacters",
                () -> open(root("<?review " + "p".repeat(65) + "?>"), scalar64));

        String textDocument =
                root("").replace(
                                "<gpx ",
                                "<gpx xmlns:a=\"urn:aaaaaaaaaaaaaaaaaaaa\" "
                                        + "xmlns:b=\"urn:bbbbbbbbbbbbbbbbbbbb\" ");
        int textLow = 64;
        int textHigh = 1_024;
        while (textLow < textHigh) {
            int middle = textLow + (textHigh - textLow) / 2;
            try {
                open(textDocument, textLimits(middle)).close();
                textHigh = middle;
            } catch (SourceException failure) {
                assertEquals("textCharacters", failure.terminal().context().get("limit"));
                textLow = middle + 1;
            }
        }
        open(textDocument, textLimits(textLow)).close();
        int textThreshold = textLow;
        assertLimit("textCharacters", () -> open(textDocument, textLimits(textThreshold - 1)));

        GpxLimits twoCoordinates = coordinateLimits(2, 2);
        open(
                        root(
                                "<trk><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                        + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>"),
                        twoCoordinates)
                .close();
        assertLimit(
                "coordinates",
                () ->
                        open(
                                root(
                                        "<wpt lat=\"2\" lon=\"2\"/><trk><trkseg>"
                                                + "<trkpt lat=\"0\" lon=\"0\"/>"
                                                + "<trkpt lat=\"1\" lon=\"1\"/>"
                                                + "</trkseg></trk>"),
                                twoCoordinates));

        String document =
                root(
                        "<trk><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>");
        GpxLimits base = compactLimits(bytes(document).length, 1_000_000);
        long minimum = minimumOwned(base);
        long low = minimum;
        long high = 1_000_000;
        while (low < high) {
            long middle = low + (high - low) / 2;
            try {
                open(document, withOwnedBytes(base, middle)).close();
                high = middle;
            } catch (SourceException failure) {
                assertEquals("ownedBytes", failure.terminal().context().get("limit"));
                low = middle + 1;
            }
        }
        open(document, withOwnedBytes(base, low)).close();
        assertTrue(low > minimum);
        long threshold = low;
        assertLimit("ownedBytes", () -> open(document, withOwnedBytes(base, threshold - 1)));
        assertOwnedThreshold(
                root(
                        "<trk><extensions/><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                                + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>"));
        assertOwnedThreshold(root("<metadata><name>" + "s".repeat(64) + "</name></metadata>"));
    }

    @Test
    void mutationCancellationAndCleanupPrecedenceAreDeterministic() throws Exception {
        Path path = temporary.resolve("changing.gpx");
        Files.writeString(path, root("<wpt lat=\"0\" lon=\"0\"/>"), StandardCharsets.UTF_8);
        AtomicInteger attributes = new AtomicInteger();
        GpxFileAccess mutating =
                new SystemFileAccess() {
                    @Override
                    public BasicFileAttributes readAttributes(Path requested) throws IOException {
                        if (attributes.incrementAndGet() == 2) {
                            Files.writeString(
                                    requested,
                                    root("<wpt lat=\"1\" lon=\"1\"/>"),
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
                                GpxFiles.open(
                                        path,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        mutating));
        assertEquals(
                Map.of("operation", "read", "reason", "changed"), changed.terminal().context());

        for (IOException finalFailure :
                List.of(
                        new java.nio.file.NoSuchFileException("removed"),
                        new java.nio.file.AccessDeniedException("denied"),
                        new IOException("attribute failure"))) {
            AtomicInteger reads = new AtomicInteger();
            GpxFileAccess failingFinalAttributes =
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
                                    GpxFiles.open(
                                            path,
                                            IDENTITY,
                                            GpxOpenOptions.defaults(),
                                            CancellationToken.none(),
                                            failingFinalAttributes));
            assertEquals(
                    Map.of("operation", "read", "reason", "changed"),
                    finalFingerprint.terminal().context());
        }

        Path closePath = temporary.resolve("close.gpx");
        Files.writeString(closePath, root(""), StandardCharsets.UTF_8);
        GpxFileAccess closeFailure =
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
                                GpxFiles.open(
                                        closePath,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        closeFailure));
        assertEquals(Map.of("operation", "close", "reason", "other"), close.terminal().context());

        GpxFileAccess readAndCloseFailure =
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
                                GpxFiles.open(
                                        closePath,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
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
                                GpxFiles.open(
                                        closePath,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        () -> cancellationChecks.incrementAndGet() >= 2));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
    }

    @Test
    void deterministicMutationFuzzNeverEscapesUnstructuredParserFailures() {
        byte[] seed = bytes(root("<wpt lat=\"1\" lon=\"2\"><name>seed</name></wpt>"));
        SplittableRandom random = new SplittableRandom(0x475058L);
        for (int sample = 0; sample < 96; sample++) {
            byte[] mutated = seed.clone();
            int changes = 1 + random.nextInt(4);
            for (int change = 0; change < changes; change++) {
                int index = random.nextInt(mutated.length);
                mutated[index] = (byte) random.nextInt(256);
            }
            try {
                FeatureSource source =
                        GpxFiles.openSnapshot(
                                mutated,
                                IDENTITY,
                                GpxOpenOptions.defaults(),
                                CancellationToken.none());
                source.close();
            } catch (SourceException expected) {
                assertTrue(
                        Set.of(
                                        "GPX_ENCODING_INVALID",
                                        "GPX_XML_INVALID",
                                        "GPX_PROFILE_UNSUPPORTED",
                                        "GPX_VALUE_INVALID",
                                        "SOURCE_LIMIT_EXCEEDED")
                                .contains(expected.terminal().code()));
                assertFalse(expected.terminal().message().contains("[B@"));
            }
        }
    }

    @Test
    void checkedInSecurityFixturesFailClosedWithoutLeakingExternalTargets() throws Exception {
        assertSecurityFixture(
                "doctype-external.gpx", "GPX_XML_INVALID", Map.of("reason", "doctype"));
        assertSecurityFixture(
                "malformed-truncated.gpx", "GPX_XML_INVALID", Map.of("reason", "syntax"));
        assertSecurityFixture(
                "foreign-xinclude.gpx",
                "GPX_PROFILE_UNSUPPORTED",
                Map.of("construct", "foreignElement"));

        assertEncodingFailure("bom", new byte[] {(byte) 0xff, (byte) 0xfe, 0, 0});
        assertEncodingFailure("utf8", new byte[] {'<', (byte) 0xc3, '(', '>'});
    }

    @Test
    void limitHardMaximaAndCrossFieldArithmeticAreValidatedAtConstruction() {
        GpxLimits maximum =
                new GpxLimits(
                        268_435_456,
                        128,
                        32_000_000,
                        8_000_000,
                        8_000_000,
                        1_048_576,
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
                        new GpxLimits(
                                268_435_457,
                                128,
                                32_000_000,
                                8_000_000,
                                8_000_000,
                                1_048_576,
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
                () -> new GpxLimits(4_096, 16, 10, 10, 20, 10, 1, 2, 2, 1, 32, 64, 16, 10_000, 2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GpxLimits(
                                4_096, 16, 100, 20, 20, 10, 2, 10, 11, 2, 32, 64, 16, 10_000, 2));
    }

    private static void assertSecurityFixture(String name, String code, Map<String, String> context)
            throws Exception {
        String root = "/io/github/mundanej/map/io/gpx/security/";
        byte[] content;
        try (InputStream input = GpxHardeningTest.class.getResourceAsStream(root + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing GPX security fixture: " + name);
            }
            content = input.readAllBytes();
        }
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GpxFiles.openSnapshot(
                                        content,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals(code, failure.terminal().code());
        assertEquals(context, failure.terminal().context());
        assertFalse(failure.toString().contains("gpx-secret-canary"));
    }

    private static void assertEncodingFailure(String reason, byte[] content) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                GpxFiles.openSnapshot(
                                        content,
                                        IDENTITY,
                                        GpxOpenOptions.defaults(),
                                        CancellationToken.none()));
        assertEquals("GPX_ENCODING_INVALID", failure.terminal().code());
        assertEquals(Map.of("reason", reason), failure.terminal().context());
    }

    private static FeatureSource open(String document) {
        return open(document, GpxLimits.defaults());
    }

    private static FeatureSource open(String document, GpxLimits limits) {
        return GpxFiles.openSnapshot(
                bytes(document),
                IDENTITY,
                GpxOpenOptions.defaults().withFormatLimits(limits),
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

    private static String root(String content) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" "
                + "version=\"1.1\" creator=\"a\">"
                + content
                + "</gpx>";
    }

    private static GpxLimits withRetainedWarnings(GpxLimits value, int retained) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                retained);
    }

    private static GpxLimits withInputBytes(GpxLimits value, int input) {
        return copy(
                value,
                input,
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withXmlDepth(GpxLimits value, int depth) {
        return copy(
                value,
                value.maximumInputBytes(),
                depth,
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withAttributes(GpxLimits value, int attributes) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                attributes,
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withNamespaces(GpxLimits value, int namespaces) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                namespaces,
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withFeatures(GpxLimits value, int features) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                features,
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withNumberCharacters(GpxLimits value, int numberCharacters) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                numberCharacters,
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withScalarCharacters(GpxLimits value, int scalarCharacters) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                scalarCharacters,
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                value.maximumOwnedBytes(),
                value.retainedWarnings());
    }

    private static GpxLimits withOwnedBytes(GpxLimits value, long owned) {
        return copy(
                value,
                value.maximumInputBytes(),
                value.maximumXmlDepth(),
                value.maximumXmlEvents(),
                value.maximumElements(),
                value.maximumAttributes(),
                value.maximumNamespaceDeclarations(),
                value.maximumPhysicalFeatures(),
                value.maximumTotalCoordinates(),
                value.maximumCoordinatesPerSegment(),
                value.maximumParts(),
                value.maximumScalarCharacters(),
                value.maximumTextCharacters(),
                value.maximumNumberCharacters(),
                owned,
                value.retainedWarnings());
    }

    private static GpxLimits structuralLimits(int elements, int events) {
        return new GpxLimits(
                4_096, 16, events, elements, 32, 16, 1, 4, 4, 2, 128, 2_048, 32, 100_000, 8);
    }

    private static GpxLimits coordinateLimits(int total, int perSegment) {
        GpxLimits defaults = GpxLimits.defaults();
        return copy(
                defaults,
                defaults.maximumInputBytes(),
                defaults.maximumXmlDepth(),
                defaults.maximumXmlEvents(),
                defaults.maximumElements(),
                defaults.maximumAttributes(),
                defaults.maximumNamespaceDeclarations(),
                defaults.maximumPhysicalFeatures(),
                total,
                perSegment,
                defaults.maximumParts(),
                defaults.maximumScalarCharacters(),
                defaults.maximumTextCharacters(),
                defaults.maximumNumberCharacters(),
                defaults.maximumOwnedBytes(),
                defaults.retainedWarnings());
    }

    private static GpxLimits compactLimits(int inputBytes, long ownedBytes) {
        return new GpxLimits(
                inputBytes, 16, 202, 100, 64, 16, 1, 2, 2, 1, 64, 300, 32, ownedBytes, 8);
    }

    private static GpxLimits textLimits(int textCharacters) {
        GpxLimits defaults = GpxLimits.defaults();
        return copy(
                defaults,
                defaults.maximumInputBytes(),
                defaults.maximumXmlDepth(),
                defaults.maximumXmlEvents(),
                defaults.maximumElements(),
                defaults.maximumAttributes(),
                defaults.maximumNamespaceDeclarations(),
                defaults.maximumPhysicalFeatures(),
                defaults.maximumTotalCoordinates(),
                defaults.maximumCoordinatesPerSegment(),
                defaults.maximumParts(),
                64,
                textCharacters,
                defaults.maximumNumberCharacters(),
                defaults.maximumOwnedBytes(),
                defaults.retainedWarnings());
    }

    private static long minimumOwned(GpxLimits value) {
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

    private static void assertOwnedThreshold(String document) {
        GpxLimits base = compactLimits(bytes(document).length, 1_000_000);
        long minimum = minimumOwned(base);
        long low = minimum;
        long high = 1_000_000;
        while (low < high) {
            long middle = low + (high - low) / 2;
            try {
                open(document, withOwnedBytes(base, middle)).close();
                high = middle;
            } catch (SourceException failure) {
                assertEquals("ownedBytes", failure.terminal().context().get("limit"));
                low = middle + 1;
            }
        }
        open(document, withOwnedBytes(base, low)).close();
        long threshold = low;
        assertLimit("ownedBytes", () -> open(document, withOwnedBytes(base, threshold - 1)));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static GpxLimits copy(
            GpxLimits ignored,
            int input,
            int depth,
            int events,
            int elements,
            int attributes,
            int namespaces,
            int features,
            int coordinates,
            int segmentCoordinates,
            int parts,
            int scalarCharacters,
            int textCharacters,
            int numberCharacters,
            long ownedBytes,
            int warnings) {
        java.util.Objects.requireNonNull(ignored, "ignored");
        return new GpxLimits(
                input,
                depth,
                events,
                elements,
                attributes,
                namespaces,
                features,
                coordinates,
                segmentCoordinates,
                parts,
                scalarCharacters,
                textCharacters,
                numberCharacters,
                ownedBytes,
                warnings);
    }

    private static class SystemFileAccess implements GpxFileAccess {
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
}
