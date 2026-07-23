package io.github.mundanej.map.io.se;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeHardeningTest {
    private static final String FIXTURES = "/se-fixtures/";
    private static final NamedSymbolCatalog EMPTY_CATALOG = NamedSymbolCatalog.of(List.of());

    @Test
    void manifestChecksumsProvenanceAndPublicOutcomesAreExact() throws Exception {
        List<String> lines = resourceText("manifest.tsv").lines().toList();
        assertFalse(lines.isEmpty());
        int fixtures = 0;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            fixtures++;
            String[] fields = line.split("\\t", -1);
            assertEquals(6, fields.length, line);
            byte[] bytes = resource(fields[0]);
            assertEquals(fields[1], sha256(bytes), fields[0]);
            assertEquals("BSD-3-Clause", fields[4]);
            assertEquals("project-authored", fields[5]);
            if ("SUPPORTED".equals(fields[2])) {
                SeFeatureStyle style =
                        SeStyles.read(fields[0], bytes, EMPTY_CATALOG, SeReadOptions.defaults());
                assertEquals("project-authored-mixed", style.name().orElseThrow());
                assertEquals(2, style.rules().size());
                FeaturePortrayalResolver resolver =
                        FeaturePortrayalResolver.compile(style.portrayal());
                assertEquals(List.of("kind"), resolver.requiredSymbolAttributes());
                var primary =
                        resolver.resolveAll(
                                java.util.Map.of("kind", "primary"),
                                PortrayalEvaluationContext.UNSCALED);
                VectorMarkerSymbol marker =
                        org.junit.jupiter.api.Assertions.assertInstanceOf(
                                VectorMarkerSymbol.class, primary.marker().orElseThrow());
                assertEquals(BuiltInMarkers.path(BuiltInMarker.CIRCLE), marker.path());
                assertEquals(0x28, marker.fill().red());
                assertEquals(14, marker.placement().size().width());
                SolidLineSymbol lineSymbol =
                        org.junit.jupiter.api.Assertions.assertInstanceOf(
                                SolidLineSymbol.class, primary.line().orElseThrow());
                assertEquals(0x28, lineSymbol.stroke().color().red());
                assertEquals(3, lineSymbol.stroke().width().value());
                SolidFillSymbol fillSymbol =
                        org.junit.jupiter.api.Assertions.assertInstanceOf(
                                SolidFillSymbol.class, primary.fill().orElseThrow());
                assertEquals(0x8e, fillSymbol.fill().red());
                assertEquals(
                        0x28,
                        org.junit.jupiter.api.Assertions.assertInstanceOf(
                                        SolidLineSymbol.class, fillSymbol.outline().orElseThrow())
                                .stroke()
                                .color()
                                .red());
                var fallback =
                        resolver.resolveAll(
                                java.util.Map.of("kind", "other"),
                                PortrayalEvaluationContext.UNSCALED);
                VectorMarkerSymbol square =
                        org.junit.jupiter.api.Assertions.assertInstanceOf(
                                VectorMarkerSymbol.class, fallback.marker().orElseThrow());
                assertEquals(BuiltInMarkers.path(BuiltInMarker.SQUARE), square.path());
                assertFalse(fallback.line().isPresent());
                assertFalse(fallback.fill().isPresent());
            } else {
                assertEquals(
                        fields[2],
                        assertThrows(
                                        SeReadException.class,
                                        () ->
                                                SeStyles.read(
                                                        fields[0],
                                                        bytes,
                                                        EMPTY_CATALOG,
                                                        SeReadOptions.defaults()))
                                .problem()
                                .code());
            }
        }
        assertEquals(3, fixtures);
        String provenance = resourceText("PROVENANCE.md");
        assertFalse(provenance.contains("PENDING"));
        assertFalse(provenance.isBlank());
    }

    @Test
    void deterministicByteMutationNeverEscapesAsRawRuntimeOrPartialState() throws Exception {
        byte[] original = resource("supported-mixed.xml");
        List<byte[]> mutations = new ArrayList<>();
        for (int length = 0; length < original.length; length += 37) {
            mutations.add(java.util.Arrays.copyOf(original, length));
        }
        for (int index = 0; index < original.length; index += 31) {
            byte[] mutation = original.clone();
            mutation[index] = (byte) (mutation[index] ^ 0x5a);
            mutations.add(mutation);
        }
        for (int index = 0; index < mutations.size(); index++) {
            byte[] mutation = mutations.get(index);
            try {
                SeFeatureStyle result =
                        SeStyles.read(
                                "mutation-" + index,
                                mutation,
                                EMPTY_CATALOG,
                                SeReadOptions.defaults());
                assertNotNull(result.portrayal());
                assertFalse(result.rules().isEmpty());
            } catch (SeReadException expected) {
                assertFalse(expected.problem().code().isBlank());
                assertFalse(expected.problem().context().isEmpty());
            }
        }
    }

    @Test
    void remainingStructuralLimitsHaveStablePublicNames() {
        SeReadLimits defaults = SeReadLimits.defaults();
        assertLimit(
                "elements",
                supported(),
                replace(defaults, 8, defaults.maximumAttributes(), 64, 8, 8));
        assertLimit(
                "attributes",
                supported(),
                replace(defaults, defaults.maximumElements(), 1, 64, 8, 8));
        String aggregate =
                """
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se">
                  <se:Name>a</se:Name><se:Rule><se:Name>b</se:Name>
                    <se:PointSymbolizer><se:Graphic><se:Mark/></se:Graphic></se:PointSymbolizer>
                  </se:Rule>
                </se:FeatureTypeStyle>
                """;
        assertLimit(
                "textCharacters",
                aggregate,
                replace(
                        defaults,
                        defaults.maximumElements(),
                        defaults.maximumAttributes(),
                        1,
                        defaults.maximumRules(),
                        defaults.maximumSymbolizers()));
        assertLimit(
                "rules",
                supported(),
                replace(
                        defaults,
                        defaults.maximumElements(),
                        defaults.maximumAttributes(),
                        defaults.maximumAggregateTextCharacters(),
                        1,
                        defaults.maximumSymbolizers()));
        assertLimit(
                "symbolizers",
                supported(),
                replace(
                        defaults,
                        defaults.maximumElements(),
                        defaults.maximumAttributes(),
                        defaults.maximumAggregateTextCharacters(),
                        defaults.maximumRules(),
                        1));
    }

    @Test
    void excludedProfileFamiliesHaveStableDiagnostics() {
        List<UnsupportedCase> cases =
                List.of(
                        unsupportedRoot(
                                "coverage",
                                "<se:CoverageStyle xmlns:se=\"http://www.opengis.net/se\"/>"),
                        unsupportedRoot(
                                "sld",
                                "<sld:StyledLayerDescriptor xmlns:sld=\"http://www.opengis.net/sld\"/>"),
                        unsupportedSymbolizer("text", "<se:TextSymbolizer/>"),
                        unsupportedSymbolizer("raster", "<se:RasterSymbolizer/>"),
                        unsupportedSymbolizer(
                                "geometry",
                                "<se:PointSymbolizer><se:Geometry/>"
                                        + "<se:Graphic><se:Mark/></se:Graphic>"
                                        + "</se:PointSymbolizer>"),
                        unsupportedSymbolizer(
                                "metre-uom",
                                lineWithAttribute(
                                        "uom=\"http://www.opengeospatial.org/se/units/metre\"")),
                        unsupportedSymbolizer(
                                "foot-uom",
                                lineWithAttribute(
                                        "uom=\"http://www.opengeospatial.org/se/units/foot\"")),
                        unsupportedSymbolizer(
                                "other-uom",
                                "<se:PointSymbolizer uom=\"urn:unit:furlong\">"
                                        + "<se:Graphic><se:Mark/></se:Graphic>"
                                        + "</se:PointSymbolizer>"),
                        unsupportedSymbolizer(
                                "graphic-fill",
                                "<se:PolygonSymbolizer><se:Fill><se:GraphicFill/>"
                                        + "</se:Fill></se:PolygonSymbolizer>"),
                        unsupportedSymbolizer(
                                "graphic-stroke",
                                "<se:LineSymbolizer><se:Stroke><se:GraphicStroke/>"
                                        + "</se:Stroke></se:LineSymbolizer>"),
                        unsupportedParameter("dash-array", "stroke-dasharray", "2 2"),
                        unsupportedParameter("line-cap", "stroke-linecap", "square"),
                        unsupportedParameter("line-join", "stroke-linejoin", "bevel"),
                        unsupportedElement(
                                "perpendicular-offset",
                                line().replace(
                                                "</se:LineSymbolizer>",
                                                "<se:PerpendicularOffset>2</se:PerpendicularOffset>"
                                                        + "</se:LineSymbolizer>")),
                        unsupportedSymbolizer(
                                "color-map",
                                "<se:RasterSymbolizer><se:ColorMap/></se:RasterSymbolizer>"),
                        unsupportedFilter("filter-function", "<ogc:Function name=\"f\"/>"),
                        unsupportedFilter(
                                "filter-arithmetic",
                                "<ogc:Add><ogc:Literal>1</ogc:Literal>"
                                        + "<ogc:Literal>2</ogc:Literal></ogc:Add>"),
                        unsupportedFilter(
                                "environment-expression",
                                "<ogc:Function name=\"env\"><ogc:Literal>x</ogc:Literal>"
                                        + "</ogc:Function>"),
                        unsupportedElement(
                                "dynamic-symbol-parameter",
                                "<se:LineSymbolizer><se:Stroke>"
                                        + "<se:SvgParameter name=\"stroke\">"
                                        + "<ogc:PropertyName xmlns:ogc=\"http://www.opengis.net/ogc\">"
                                        + "color</ogc:PropertyName></se:SvgParameter>"
                                        + "</se:Stroke></se:LineSymbolizer>"),
                        unsupportedElement(
                                "external-mark",
                                "<se:PointSymbolizer><se:Graphic><se:Mark>"
                                        + "<se:OnlineResource/></se:Mark></se:Graphic>"
                                        + "</se:PointSymbolizer>"),
                        unsupportedElement(
                                "inline-content",
                                "<se:PointSymbolizer><se:Graphic><se:ExternalGraphic>"
                                        + onlineResource("local")
                                        + "<se:InlineContent/></se:ExternalGraphic></se:Graphic>"
                                        + "</se:PointSymbolizer>"),
                        unsupportedElement("legend-graphic", "<se:LegendGraphic/>"),
                        new UnsupportedCase(
                                "style-online-resource",
                                "<se:FeatureTypeStyle xmlns:se=\"http://www.opengis.net/se\">"
                                        + "<se:OnlineResource/></se:FeatureTypeStyle>",
                                "SE_ELEMENT_UNSUPPORTED"),
                        unsupportedElement(
                                "rule-online-resource",
                                "<se:OnlineResource/><se:PointSymbolizer><se:Graphic>"
                                        + "<se:Mark/></se:Graphic></se:PointSymbolizer>"),
                        new UnsupportedCase(
                                "remote-resource",
                                rule(
                                        "<se:PointSymbolizer><se:Graphic><se:ExternalGraphic>"
                                                + onlineResource("https://example.test/a.svg")
                                                + "<se:Format>application/vnd.mundane-map.symbol"
                                                + "</se:Format></se:ExternalGraphic></se:Graphic>"
                                                + "</se:PointSymbolizer>"),
                                "SE_RESOURCE_UNRESOLVED"),
                        unsupportedElement(
                                "alternative-graphics",
                                "<se:PointSymbolizer><se:Graphic><se:Mark/><se:Mark/>"
                                        + "</se:Graphic></se:PointSymbolizer>"),
                        unsupportedElement(
                                "vendor-option",
                                "<se:PointSymbolizer><se:Graphic><se:Mark/></se:Graphic>"
                                        + "<se:VendorOption/></se:PointSymbolizer>"));
        for (UnsupportedCase testCase : cases) {
            assertEquals(
                    testCase.code(),
                    assertThrows(
                                    SeReadException.class,
                                    () ->
                                            SeStyles.read(
                                                    testCase.name(),
                                                    testCase.xml().getBytes(StandardCharsets.UTF_8),
                                                    EMPTY_CATALOG,
                                                    SeReadOptions.defaults()))
                            .problem()
                            .code(),
                    testCase.name());
        }
    }

    private static UnsupportedCase unsupportedRoot(String name, String xml) {
        return new UnsupportedCase(name, xml, "SE_ROOT_UNSUPPORTED");
    }

    private static UnsupportedCase unsupportedSymbolizer(String name, String body) {
        return new UnsupportedCase(name, rule(body), "SE_SYMBOLIZER_UNSUPPORTED");
    }

    private static UnsupportedCase unsupportedElement(String name, String body) {
        return new UnsupportedCase(name, rule(body), "SE_ELEMENT_UNSUPPORTED");
    }

    private static UnsupportedCase unsupportedParameter(
            String name, String parameter, String value) {
        return unsupportedSymbolizer(
                name,
                line().replace(
                                "</se:Stroke>",
                                "<se:SvgParameter name=\""
                                        + parameter
                                        + "\">"
                                        + value
                                        + "</se:SvgParameter></se:Stroke>"));
    }

    private static UnsupportedCase unsupportedFilter(String name, String operand) {
        return new UnsupportedCase(
                name,
                """
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se"
                    xmlns:ogc="http://www.opengis.net/ogc"><se:Rule>
                  <ogc:Filter><ogc:PropertyIsEqualTo>
                    <ogc:PropertyName>x</ogc:PropertyName>
                """
                        + operand
                        + """
                  </ogc:PropertyIsEqualTo></ogc:Filter>
                  <se:PointSymbolizer><se:Graphic><se:Mark/></se:Graphic></se:PointSymbolizer>
                </se:Rule></se:FeatureTypeStyle>
                """,
                "SE_FILTER_UNSUPPORTED");
    }

    private static String lineWithAttribute(String attribute) {
        return line().replace("<se:LineSymbolizer>", "<se:LineSymbolizer " + attribute + ">");
    }

    private static String line() {
        return "<se:LineSymbolizer><se:Stroke>"
                + "<se:SvgParameter name=\"stroke\">#000000</se:SvgParameter>"
                + "</se:Stroke></se:LineSymbolizer>";
    }

    private static String onlineResource(String key) {
        return "<se:OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\""
                + " xlink:type=\"simple\" xlink:href=\""
                + key
                + "\"/>";
    }

    private static void assertLimit(String name, String xml, SeReadLimits limits) {
        SeReadException failure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.read(
                                        name,
                                        xml.getBytes(StandardCharsets.UTF_8),
                                        EMPTY_CATALOG,
                                        new SeReadOptions(limits, CancellationToken.none())));
        assertEquals("SE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals(name, failure.problem().context().get("limit"));
    }

    private static SeReadLimits replace(
            SeReadLimits source,
            int elements,
            int attributes,
            int text,
            int rules,
            int symbolizers) {
        return new SeReadLimits(
                source.maximumInputBytes(),
                source.maximumElementDepth(),
                elements,
                attributes,
                text,
                Math.min(source.maximumValueCharacters(), text),
                rules,
                source.maximumPredicates(),
                source.maximumPredicateDepth(),
                symbolizers,
                source.maximumCatalogReferences(),
                source.maximumOutputSymbols(),
                source.maximumOwnedBytes());
    }

    private static String supported() {
        try {
            return resourceText("supported-mixed.xml");
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String rule(String symbolizer) {
        return "<se:FeatureTypeStyle xmlns:se=\"http://www.opengis.net/se\"><se:Rule>"
                + symbolizer
                + "</se:Rule></se:FeatureTypeStyle>";
    }

    private static String resourceText(String name) throws IOException {
        return new String(resource(name), StandardCharsets.UTF_8);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = SeHardeningTest.class.getResourceAsStream(FIXTURES + name)) {
            if (input == null) {
                throw new IOException("missing fixture: " + name);
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private record UnsupportedCase(String name, String xml, String code) {}
}
