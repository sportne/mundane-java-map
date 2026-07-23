package io.github.mundanej.map.io.se;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeStylesTest {
    private static final NamedSymbolCatalog EMPTY_CATALOG = NamedSymbolCatalog.of(List.of());

    @TempDir Path temporaryDirectory;

    @Test
    void readsMetadataAndLiteralMarkerWithExactPlacementAndPaint() {
        byte[] bytes =
                style(
                                """
                                <se:Name>airports</se:Name>
                                <se:Description>
                                  <se:Title>Airport style</se:Title>
                                  <se:Abstract>Literal point review</se:Abstract>
                                </se:Description>
                                <se:FeatureTypeName>airport</se:FeatureTypeName>
                                <se:SemanticTypeIdentifier>generic:Any</se:SemanticTypeIdentifier>
                                """,
                                """
                                <se:Name>primary</se:Name>
                                <se:Description><se:Title>Primary airports</se:Title></se:Description>
                                """,
                                """
                                <se:Mark>
                                  <se:WellKnownName>triangle</se:WellKnownName>
                                  <se:Fill>
                                    <se:SvgParameter name="fill">#123456</se:SvgParameter>
                                    <se:SvgParameter name="fill-opacity">0.5</se:SvgParameter>
                                  </se:Fill>
                                  <se:Stroke>
                                    <se:SvgParameter name="stroke">#abcdef</se:SvgParameter>
                                    <se:SvgParameter name="stroke-opacity">0.25</se:SvgParameter>
                                    <se:SvgParameter name="stroke-width">2.5</se:SvgParameter>
                                  </se:Stroke>
                                </se:Mark>
                                """,
                                """
                                <se:Opacity>0.75</se:Opacity>
                                <se:Size>18</se:Size>
                                <se:Rotation>-15</se:Rotation>
                                <se:AnchorPoint>
                                  <se:AnchorPointX>1</se:AnchorPointX>
                                  <se:AnchorPointY>0</se:AnchorPointY>
                                </se:AnchorPoint>
                                <se:Displacement>
                                  <se:DisplacementX>3</se:DisplacementX>
                                  <se:DisplacementY>4</se:DisplacementY>
                                </se:Displacement>
                                """)
                        .getBytes(StandardCharsets.UTF_8);

        SeFeatureStyle style =
                SeStyles.read("metadata", bytes, EMPTY_CATALOG, SeReadOptions.defaults());
        bytes[0] = 0;

        assertEquals("airports", style.name().orElseThrow());
        assertEquals("Airport style", style.description().title().orElseThrow());
        assertEquals("Literal point review", style.description().abstractText().orElseThrow());
        assertEquals("airport", style.featureTypeName().orElseThrow());
        assertEquals(List.of("generic:Any"), style.semanticTypeIdentifiers());
        assertEquals("primary", style.rules().getFirst().name().orElseThrow());
        VectorMarkerSymbol marker = marker(style);
        assertEquals(BuiltInMarkers.path(BuiltInMarker.TRIANGLE), marker.path());
        assertEquals(0x12, marker.fill().red());
        assertEquals(128, marker.fill().alpha());
        assertEquals(0xab, marker.stroke().orElseThrow().color().red());
        assertEquals(64, marker.stroke().orElseThrow().color().alpha());
        assertEquals(2.5, marker.stroke().orElseThrow().width().value());
        assertEquals(18, marker.placement().size().width());
        assertEquals(SymbolAnchor.SOUTH_EAST, marker.placement().anchor());
        assertEquals(3, marker.placement().offsetX());
        assertEquals(-4, marker.placement().offsetY());
        assertEquals(345, marker.placement().rotationDegrees());
        assertEquals(SymbolRotationMode.SCREEN_RELATIVE, marker.placement().rotationMode());
        assertEquals(0.75, marker.opacity());
    }

    @Test
    void acceptsAllSixWellKnownNamesAndApprovedDefaults() {
        List<String> names = List.of("square", "circle", "triangle", "star", "cross", "x");
        List<BuiltInMarker> expected =
                List.of(
                        BuiltInMarker.SQUARE,
                        BuiltInMarker.CIRCLE,
                        BuiltInMarker.TRIANGLE,
                        BuiltInMarker.STAR,
                        BuiltInMarker.CROSS,
                        BuiltInMarker.X);
        for (int index = 0; index < names.size(); index++) {
            SeFeatureStyle style =
                    read(
                            style(
                                    "",
                                    "",
                                    "<se:Mark><se:WellKnownName>"
                                            + names.get(index)
                                            + "</se:WellKnownName></se:Mark>",
                                    ""));
            VectorMarkerSymbol marker = marker(style);
            assertEquals(BuiltInMarkers.path(expected.get(index)), marker.path());
            assertEquals(6, marker.placement().size().width());
            assertEquals(SymbolAnchor.CENTER, marker.placement().anchor());
            assertEquals(128, marker.fill().red());
            assertTrue(marker.stroke().isEmpty());
        }
    }

    @Test
    void pathAndBytesHaveParityAndCallerBytesAreNotRetained() throws Exception {
        byte[] input = style("", "", "<se:Mark/>", "").getBytes(StandardCharsets.UTF_8);
        Path path = temporaryDirectory.resolve("style.xml");
        Files.write(path, input);

        SeFeatureStyle fromBytes =
                SeStyles.read("bytes", input, EMPTY_CATALOG, SeReadOptions.defaults());
        SeFeatureStyle fromPath = SeStyles.read(path, EMPTY_CATALOG, SeReadOptions.defaults());
        byte[] before = Files.readAllBytes(path);
        input[5] = 0;

        assertEquals(fromBytes, fromPath);
        assertNotSame(input, before);
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test
    void securityRootNamespaceVersionAndUnsupportedBranchesAreStable() {
        assertCode(
                "SE_XML_SECURITY",
                "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e \"x\">]>"
                        + "<se:FeatureTypeStyle xmlns:se=\"http://www.opengis.net/se\">&e;"
                        + "</se:FeatureTypeStyle>");
        assertCode(
                "SE_XML_SECURITY",
                style(
                        "",
                        "",
                        "<se:Mark><se:WellKnownName><![CDATA[square]]>"
                                + "</se:WellKnownName></se:Mark>",
                        ""));
        assertCode(
                "SE_XML_SECURITY",
                "<?probe disabled?><se:FeatureTypeStyle xmlns:se=\"http://www.opengis.net/se\"/>");
        assertCode(
                "SE_ROOT_UNSUPPORTED",
                "<se:CoverageStyle xmlns:se=\"http://www.opengis.net/se\"/>");
        assertCode("SE_NAMESPACE_UNSUPPORTED", "<se:FeatureTypeStyle xmlns:se=\"urn:not-se\"/>");
        assertCode(
                "SE_VERSION_UNSUPPORTED",
                style("", "", "<se:Mark/>", "").replace("version=\"1.1.0\"", "version=\"1.0.0\""));
        assertCode(
                "SE_NAMESPACE_UNSUPPORTED",
                style(
                                "",
                                "",
                                "<se:Mark/><xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\"/>",
                                "")
                        .replace("</se:Graphic>", "</se:Graphic>"));
        assertCode("SE_FILTER_UNSUPPORTED", style("", "<ogc:Filter/>", "<se:Mark/>", ""));
        assertCode("SE_SYMBOLIZER_UNSUPPORTED", style("", "", "<se:ExternalGraphic/>", ""));
        assertCode("SE_ELEMENT_UNSUPPORTED", style("", "", "<se:Mark/>", "<se:VendorOption/>"));

        SeFeatureStyle commented = read(style("<!-- A & B -->", "", "<se:Mark/>", ""));
        assertEquals(BuiltInMarkers.path(BuiltInMarker.SQUARE), marker(commented).path());
    }

    @Test
    void cancellationAndResourceLimitsFailBeforePartialOutput() {
        SeReadOptions cancelled = new SeReadOptions(SeReadLimits.defaults(), () -> true);
        assertEquals(
                "SE_CANCELLED",
                assertThrows(
                                SeReadException.class,
                                () ->
                                        SeStyles.read(
                                                "cancelled",
                                                validBytes(),
                                                EMPTY_CATALOG,
                                                cancelled))
                        .problem()
                        .code());

        SeReadLimits defaults = SeReadLimits.defaults();
        assertCode(
                "SE_INPUT_LIMIT",
                validBytes(),
                new SeReadOptions(
                        with(
                                defaults,
                                16,
                                defaults.maximumElementDepth(),
                                defaults.maximumValueCharacters()),
                        CancellationToken.none()));
        assertLimit(
                with(defaults, defaults.maximumInputBytes(), 2, defaults.maximumValueCharacters()),
                "elementDepth");
        assertLimit(
                with(defaults, defaults.maximumInputBytes(), defaults.maximumElementDepth(), 3),
                "valueCharacters",
                style("<se:Name>long-name</se:Name>", "", "<se:Mark/>", "")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void pathAndByteInputsShareOwnedBudgetAndCloseFailuresStayStructured() throws Exception {
        byte[] bytes = validBytes();
        Path path = temporaryDirectory.resolve("owned.xml");
        Files.write(path, bytes);
        SeReadLimits constrained = withOwned(SeReadLimits.defaults(), 800);
        SeReadOptions options = new SeReadOptions(constrained, CancellationToken.none());

        SeReadException byteFailure =
                assertThrows(
                        SeReadException.class,
                        () -> SeStyles.read("owned", bytes, EMPTY_CATALOG, options));
        SeReadException pathFailure =
                assertThrows(
                        SeReadException.class, () -> SeStyles.read(path, EMPTY_CATALOG, options));
        assertEquals("SE_LIMIT_EXCEEDED", byteFailure.problem().code());
        assertEquals("ownedBytes", byteFailure.problem().context().get("limit"));
        assertEquals(byteFailure.problem().code(), pathFailure.problem().code());
        assertEquals(
                byteFailure.problem().context().get("limit"),
                pathFailure.problem().context().get("limit"));

        SeReadException closeFailure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.readOpened(
                                        "close",
                                        new CloseFailingInputStream(bytes),
                                        bytes.length,
                                        EMPTY_CATALOG,
                                        SeReadOptions.defaults()));
        assertEquals("SE_IO", closeFailure.problem().code());
        assertEquals("close", closeFailure.problem().context().get("operation"));

        SeReadException parseFailure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.readOpened(
                                        "parse-and-close",
                                        new CloseFailingInputStream(new byte[] {'<'}),
                                        1,
                                        EMPTY_CATALOG,
                                        SeReadOptions.defaults()));
        assertEquals("SE_XML_SYNTAX", parseFailure.problem().code());
        SeReadException suppressed =
                assertInstanceOf(SeReadException.class, parseFailure.getSuppressed()[0]);
        assertEquals("close", suppressed.problem().context().get("operation"));

        RuntimeFailingInputStream readFailureStream =
                new RuntimeFailingInputStream(bytes, true, false);
        SeReadException readFailure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.readOpened(
                                        "runtime-read",
                                        readFailureStream,
                                        bytes.length,
                                        EMPTY_CATALOG,
                                        SeReadOptions.defaults()));
        assertEquals("read", readFailure.problem().context().get("operation"));
        assertEquals("closed", readFailure.problem().context().get("reason"));
        assertTrue(readFailureStream.closed);

        RuntimeFailingInputStream runtimeClose = new RuntimeFailingInputStream(bytes, false, true);
        SeReadException runtimeCloseFailure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.readOpened(
                                        "runtime-close",
                                        runtimeClose,
                                        bytes.length,
                                        EMPTY_CATALOG,
                                        SeReadOptions.defaults()));
        assertEquals("close", runtimeCloseFailure.problem().context().get("operation"));
        assertEquals("accessDenied", runtimeCloseFailure.problem().context().get("reason"));
    }

    @Test
    void malformedUtf8AndXmlNeverEscapeAsRawParserFailures() {
        assertCode("SE_XML_SYNTAX", new byte[] {(byte) 0xc3, 0x28}, SeReadOptions.defaults());
        assertCode(
                "SE_XML_SYNTAX",
                "<se:FeatureTypeStyle xmlns:se=\"http://www.opengis.net/se\">"
                        .getBytes(StandardCharsets.UTF_8),
                SeReadOptions.defaults());
    }

    @Test
    void parsesOrderedRulesFiltersScaleAndElseIntoSharedPlan() {
        String xml =
                """
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se"
                    xmlns:ogc="http://www.opengis.net/ogc" version="1.1.0">
                  <se:Rule>
                    <ogc:Filter><ogc:PropertyIsEqualTo>
                      <ogc:PropertyName>kind</ogc:PropertyName><ogc:Literal>primary</ogc:Literal>
                    </ogc:PropertyIsEqualTo></ogc:Filter>
                    <se:PointSymbolizer><se:Graphic><se:Mark>
                      <se:WellKnownName>circle</se:WellKnownName>
                    </se:Mark></se:Graphic></se:PointSymbolizer>
                  </se:Rule>
                  <se:Rule>
                    <ogc:Filter><ogc:PropertyIsBetween>
                      <ogc:PropertyName>score</ogc:PropertyName>
                      <ogc:LowerBoundary><ogc:Literal>1</ogc:Literal></ogc:LowerBoundary>
                      <ogc:UpperBoundary><ogc:Literal>3</ogc:Literal></ogc:UpperBoundary>
                    </ogc:PropertyIsBetween></ogc:Filter>
                    <se:MinScaleDenominator>100</se:MinScaleDenominator>
                    <se:MaxScaleDenominator>200</se:MaxScaleDenominator>
                    <se:PointSymbolizer><se:Graphic><se:Mark>
                      <se:WellKnownName>triangle</se:WellKnownName>
                    </se:Mark></se:Graphic></se:PointSymbolizer>
                  </se:Rule>
                  <se:Rule>
                    <se:ElseFilter/>
                    <se:PointSymbolizer><se:Graphic><se:Mark/></se:Graphic></se:PointSymbolizer>
                  </se:Rule>
                </se:FeatureTypeStyle>
                """;
        SeFeatureStyle style = read(xml);
        FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(style.portrayal());

        CompositeSymbol composed =
                assertInstanceOf(
                        CompositeSymbol.class,
                        resolver.resolveAll(
                                        java.util.Map.of("kind", "primary", "score", 2L),
                                        PortrayalEvaluationContext.atScale(150))
                                .marker()
                                .orElseThrow());
        assertEquals(2, composed.children().size());
        assertEquals(List.of("kind", "score"), resolver.requiredSymbolAttributes());
        Symbol fallback =
                resolver.resolveAll(
                                java.util.Map.of("kind", "other"),
                                PortrayalEvaluationContext.atScale(150))
                        .marker()
                        .orElseThrow();
        assertEquals(
                BuiltInMarkers.path(BuiltInMarker.SQUARE),
                assertInstanceOf(VectorMarkerSymbol.class, fallback).path());
        assertTrue(
                resolver.resolveAll(
                                java.util.Map.of("kind", "other"),
                                PortrayalEvaluationContext.atScale(250))
                        .marker()
                        .isPresent());
    }

    @Test
    void predicateNodeAndDepthLimitsFailBeforePlanPublication() {
        String xml =
                """
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se"
                    xmlns:ogc="http://www.opengis.net/ogc">
                  <se:Rule>
                    <ogc:Filter><ogc:And>
                      <ogc:PropertyIsNull><ogc:PropertyName>a</ogc:PropertyName>
                      </ogc:PropertyIsNull>
                      <ogc:PropertyIsNull><ogc:PropertyName>b</ogc:PropertyName>
                      </ogc:PropertyIsNull>
                    </ogc:And></ogc:Filter>
                    <se:PointSymbolizer><se:Graphic><se:Mark/></se:Graphic></se:PointSymbolizer>
                  </se:Rule>
                </se:FeatureTypeStyle>
                """;
        SeReadLimits defaults = SeReadLimits.defaults();
        assertCode(
                "SE_LIMIT_EXCEEDED",
                xml.getBytes(StandardCharsets.UTF_8),
                new SeReadOptions(
                        withPredicates(defaults, 2, defaults.maximumPredicateDepth()),
                        CancellationToken.none()));
        assertCode(
                "SE_LIMIT_EXCEEDED",
                xml.getBytes(StandardCharsets.UTF_8),
                new SeReadOptions(
                        withPredicates(defaults, defaults.maximumPredicates(), 1),
                        CancellationToken.none()));
    }

    private static SeFeatureStyle read(String xml) {
        return SeStyles.read(
                "test",
                xml.getBytes(StandardCharsets.UTF_8),
                EMPTY_CATALOG,
                SeReadOptions.defaults());
    }

    private static VectorMarkerSymbol marker(SeFeatureStyle style) {
        return assertInstanceOf(
                VectorMarkerSymbol.class,
                FeaturePortrayalResolver.compile(style.portrayal())
                        .resolveAll(
                                java.util.Map.of(),
                                io.github.mundanej.map.api.PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .orElseThrow());
    }

    private static byte[] validBytes() {
        return style("", "", "<se:Mark/>", "").getBytes(StandardCharsets.UTF_8);
    }

    private static String style(
            String styleMetadata, String ruleMetadata, String graphic, String graphicTail) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se"
                    xmlns:ogc="http://www.opengis.net/ogc" version="1.1.0">
                """
                + styleMetadata
                + """
                  <se:Rule>
                """
                + ruleMetadata
                + """
                    <se:PointSymbolizer>
                      <se:Graphic>
                """
                + graphic
                + graphicTail
                + """
                      </se:Graphic>
                    </se:PointSymbolizer>
                  </se:Rule>
                </se:FeatureTypeStyle>
                """;
    }

    private static void assertCode(String code, String xml) {
        assertCode(code, xml.getBytes(StandardCharsets.UTF_8), SeReadOptions.defaults());
    }

    private static void assertCode(String code, byte[] bytes, SeReadOptions options) {
        assertEquals(
                code,
                assertThrows(
                                SeReadException.class,
                                () -> SeStyles.read("failure", bytes, EMPTY_CATALOG, options))
                        .problem()
                        .code());
    }

    private static void assertLimit(SeReadLimits limits, String name) {
        assertLimit(limits, name, validBytes());
    }

    private static void assertLimit(SeReadLimits limits, String name, byte[] bytes) {
        SeReadException failure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.read(
                                        "limit",
                                        bytes,
                                        EMPTY_CATALOG,
                                        new SeReadOptions(limits, CancellationToken.none())));
        assertEquals("SE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals(name, failure.problem().context().get("limit"));
    }

    private static SeReadLimits with(
            SeReadLimits source, int inputBytes, int depth, int valueCharacters) {
        return new SeReadLimits(
                inputBytes,
                depth,
                source.maximumElements(),
                source.maximumAttributes(),
                source.maximumAggregateTextCharacters(),
                valueCharacters,
                source.maximumRules(),
                source.maximumPredicates(),
                Math.min(source.maximumPredicateDepth(), depth),
                source.maximumSymbolizers(),
                source.maximumCatalogReferences(),
                source.maximumOutputSymbols(),
                source.maximumOwnedBytes());
    }

    private static SeReadLimits withOwned(SeReadLimits source, long ownedBytes) {
        return new SeReadLimits(
                source.maximumInputBytes(),
                source.maximumElementDepth(),
                source.maximumElements(),
                source.maximumAttributes(),
                source.maximumAggregateTextCharacters(),
                source.maximumValueCharacters(),
                source.maximumRules(),
                source.maximumPredicates(),
                source.maximumPredicateDepth(),
                source.maximumSymbolizers(),
                source.maximumCatalogReferences(),
                source.maximumOutputSymbols(),
                ownedBytes);
    }

    private static SeReadLimits withPredicates(
            SeReadLimits source, int predicates, int predicateDepth) {
        return new SeReadLimits(
                source.maximumInputBytes(),
                source.maximumElementDepth(),
                source.maximumElements(),
                source.maximumAttributes(),
                source.maximumAggregateTextCharacters(),
                source.maximumValueCharacters(),
                source.maximumRules(),
                predicates,
                predicateDepth,
                source.maximumSymbolizers(),
                source.maximumCatalogReferences(),
                source.maximumOutputSymbols(),
                source.maximumOwnedBytes());
    }

    private static final class CloseFailingInputStream extends ByteArrayInputStream {
        CloseFailingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            throw new IOException("expected close failure");
        }
    }

    private static final class RuntimeFailingInputStream extends ByteArrayInputStream {
        private final boolean failRead;
        private final boolean failClose;
        private boolean closed;

        RuntimeFailingInputStream(byte[] bytes, boolean failRead, boolean failClose) {
            super(bytes);
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public synchronized int read(byte[] target, int offset, int length) {
            if (failRead) {
                throw new java.nio.file.ClosedFileSystemException();
            }
            return super.read(target, offset, length);
        }

        @Override
        public void close() {
            closed = true;
            if (failClose) {
                throw new SecurityException("expected close failure");
            }
        }
    }
}
