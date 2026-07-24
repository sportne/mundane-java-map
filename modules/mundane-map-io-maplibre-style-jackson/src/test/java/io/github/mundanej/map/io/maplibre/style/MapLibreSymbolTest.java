package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelAnchorBasis;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapLibreSymbolTest {
    private static final Envelope BOX = new Envelope(0, 0, 1, 1);
    private static final VectorPath SQUARE =
            VectorPath.builder()
                    .moveTo(0, 0)
                    .lineTo(1, 0)
                    .lineTo(1, 1)
                    .lineTo(0, 1)
                    .close()
                    .build();

    @Test
    void literalIconAndPointLabelMapToExistingContracts() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"places","type":"symbol","source":"s",
                          "filter":["==",["get","show"],true],
                          "layout":{
                            "symbol-z-order":"source",
                            "icon-image":"town",
                            "icon-size":2,
                            "icon-anchor":"bottom",
                            "icon-offset":[1,-2],
                            "icon-allow-overlap":true,
                            "icon-ignore-placement":true,
                            "icon-optional":true,
                            "text-field":["to-string",["get","name"]],
                            "text-font":["SansSerif"],
                            "text-size":20,
                            "text-variable-anchor":["center","top"],
                            "text-offset":[1,-0.5],
                            "text-padding":3,
                            "text-optional":true
                          },
                          "paint":{"icon-opacity":0.5,"text-color":"#12345680","text-opacity":0.5}
                        }]}
                        """);
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(List.of(new NamedSymbol("town", vector(10, 8, 0.8))));
        try (Bound bound = bind(style, catalog)) {
            MapLibreBoundLayer layer = bound.binding().layers().getFirst();
            FeaturePortrayal portrayal = layer.portrayal().orElseThrow();
            FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(portrayal);
            VectorMarkerSymbol icon =
                    assertInstanceOf(
                            VectorMarkerSymbol.class,
                            resolver.resolve(SymbolRole.MARKER, Map.of("show", true, "name", 12L))
                                    .orElseThrow());

            assertEquals(20, icon.placement().size().width());
            assertEquals(16, icon.placement().size().height());
            assertEquals(SymbolAnchor.SOUTH, icon.placement().anchor());
            assertEquals(2, icon.placement().offsetX());
            assertEquals(-4, icon.placement().offsetY());
            assertEquals(0.4, icon.opacity(), 1.0e-12);
            assertTrue(resolver.resolve(SymbolRole.MARKER, Map.of("show", false)).isEmpty());

            var label = portrayal.pointLabel().orElseThrow();
            assertEquals(PointLabelAnchorBasis.FEATURE_POINT, label.anchorBasis());
            assertEquals(
                    List.of(PointLabelPosition.CENTER, PointLabelPosition.N), label.positions());
            assertEquals(20, label.offsetXPixels());
            assertEquals(-10, label.offsetYPixels());
            assertEquals(new Rgba(18, 52, 86, 64), label.style().color());
            assertEquals("12", resolver.resolveLabelText("", Map.of("name", 12L), 1).orElseThrow());
            assertEquals(
                    java.util.Set.of("show", "name"),
                    java.util.Set.copyOf(layer.queryAttributes().orderedNames()));
        }
    }

    @Test
    void attributeMatchAndCaseIconsResolveDeterministically() {
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(
                        List.of(
                                new NamedSymbol("red", vector(6, 6, 1)),
                                new NamedSymbol(
                                        "blue",
                                        RasterIconSymbol.nativeScreenSize(
                                                2,
                                                1,
                                                new int[] {0x0000ffff, 0x0000ffff},
                                                RasterInterpolation.NEAREST,
                                                1))));
        assertSelected(
                "[\"get\",\"icon\"]", catalog, Map.of("icon", "red"), VectorMarkerSymbol.class);
        assertSelected(
                "[\"to-string\",[\"get\",\"icon\"]]",
                NamedSymbolCatalog.of(List.of(new NamedSymbol("12", vector(6, 6, 1)))),
                Map.of("icon", 12L),
                VectorMarkerSymbol.class);
        assertSelected(
                "[\"match\",[\"get\",\"kind\"],\"road\",\"red\",\"blue\"]",
                catalog,
                Map.of("kind", "other"),
                RasterIconSymbol.class);
        assertSelected(
                "[\"case\",[\"has\",\"selected\"],\"red\","
                        + "[\"==\",[\"get\",\"kind\"],\"water\"],\"blue\",\"red\"]",
                catalog,
                Map.of("selected", true, "kind", "water"),
                VectorMarkerSymbol.class);

        FeaturePortrayalResolver unresolved = resolver("[\"get\",\"icon\"]", catalog);
        assertTrue(unresolved.resolve(SymbolRole.MARKER, Map.of("icon", "missing")).isEmpty());
        assertTrue(unresolved.resolve(SymbolRole.MARKER, Map.of()).isEmpty());
    }

    @Test
    void bindingAdmitsSingularPointsAndPreservesRasterDisplaySize() {
        RasterIconSymbol scaled =
                RasterIconSymbol.screenWidth(
                        2,
                        1,
                        new int[] {0x0000ffff, 0x0000ffff},
                        20,
                        RasterInterpolation.BILINEAR,
                        1);
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(List.of(new NamedSymbol("scaled", scaled)));
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("memory", "memory"),
                        List.of(
                                new FeatureRecord(
                                        "point",
                                        "",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of()),
                                new FeatureRecord(
                                        "multi",
                                        "",
                                        new MultiPointGeometry(CoordinateSequence.of(1, 1, 2, 2)),
                                        Map.of())));
        try (source) {
            try (MapLibreStyleBinding binding =
                    MapLibreStyleBinder.bind(
                            read(symbolStyle("\"scaled\"", "")),
                            MapLibreSourceRegistry.builder().register("s", source).build(),
                            catalog)) {
                MapLibreBoundLayer layer = binding.layers().getFirst();
                RasterIconSymbol icon =
                        assertInstanceOf(
                                RasterIconSymbol.class,
                                FeaturePortrayalResolver.compile(layer.portrayal().orElseThrow())
                                        .resolve(SymbolRole.MARKER, Map.of())
                                        .orElseThrow());
                assertEquals(20, icon.placement().size().width());
                assertEquals(10, icon.placement().size().height());
                try (FeatureCursor cursor =
                        layer.source()
                                .openCursor(
                                        new FeatureQuery(
                                                Optional.empty(),
                                                AttributeSelection.NONE,
                                                Optional.empty()),
                                        CancellationToken.none())) {
                    assertTrue(cursor.advance());
                    assertEquals("point", cursor.current().id());
                    assertFalse(cursor.advance());
                }
            }
            assertFalse(source.isClosed());
        }
    }

    @Test
    void catalogReferenceLimitsApplyAtReadAndDynamicBind() {
        MapLibreReadLimits defaults = MapLibreReadLimits.defaults();
        MapLibreReadLimits oneReference = limits(defaults, 1, defaults.maximumExpressionNodes());
        byte[] twoLiterals =
                """
                {"version":8,"sources":{},"layers":[
                  {"id":"a","type":"symbol","source":"s","layout":{
                    "symbol-z-order":"source","icon-image":"red",
                    "icon-allow-overlap":true,"icon-ignore-placement":true}},
                  {"id":"b","type":"symbol","source":"s","layout":{
                    "symbol-z-order":"source","icon-image":"blue",
                    "icon-allow-overlap":true,"icon-ignore-placement":true}}
                ]}
                """
                        .getBytes(StandardCharsets.UTF_8);
        MapLibreReadException aggregate =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        twoLiterals,
                                        new MapLibreReadOptions(
                                                oneReference, CancellationToken.none())));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", aggregate.problem().code());
        assertEquals("catalogReferences", aggregate.problem().context().get("limit"));

        MapLibreStyle dynamic =
                MapLibreStyles.read(
                        symbolStyle("[\"get\",\"icon\"]", "").getBytes(StandardCharsets.UTF_8),
                        new MapLibreReadOptions(oneReference, CancellationToken.none()));
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(
                        List.of(
                                new NamedSymbol("red", vector(4, 4, 1)),
                                new NamedSymbol("blue", vector(4, 4, 1))));
        MapLibreBindException failure =
                assertThrows(MapLibreBindException.class, () -> bind(dynamic, catalog));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals("2", failure.problem().context().get("actual"));
        assertEquals("1", failure.problem().context().get("maximum"));

        MapLibreReadLimits twoReferences = limits(defaults, 2, defaults.maximumExpressionNodes());
        String mixed =
                """
                {"version":8,"sources":{},"layers":[
                  {"id":"literal","type":"symbol","source":"s","layout":{
                    "symbol-z-order":"source","icon-image":"red",
                    "icon-allow-overlap":true,"icon-ignore-placement":true}},
                  {"id":"dynamic","type":"symbol","source":"s","layout":{
                    "symbol-z-order":"source","icon-image":["get","icon"],
                    "icon-allow-overlap":true,"icon-ignore-placement":true}}
                ]}
                """;
        MapLibreStyle mixedStyle =
                MapLibreStyles.read(
                        mixed.getBytes(StandardCharsets.UTF_8),
                        new MapLibreReadOptions(twoReferences, CancellationToken.none()));
        MapLibreBindException mixedFailure =
                assertThrows(MapLibreBindException.class, () -> bind(mixedStyle, catalog));
        assertEquals("3", mixedFailure.problem().context().get("actual"));
        assertEquals("2", mixedFailure.problem().context().get("maximum"));
    }

    @Test
    void textOffsetAndLiteralArrayBoundariesAreStable() {
        assertReadCode("MAPLIBRE_VALUE_INVALID", labeledStyle("\" \"", ""));
        assertReadCode("MAPLIBRE_VALUE_INVALID", labeledStyle("\"a\\nb\"", ""));
        assertReadCode("MAPLIBRE_VALUE_INVALID", labeledStyle("\"" + "x".repeat(257) + "\"", ""));
        assertReadCode("MAPLIBRE_VALUE_INVALID", labeledStyle("\"x\"", ",\"text-offset\":[65,0]"));

        MapLibreStyle maximum =
                read(labeledStyle("\"x\"", ",\"text-size\":512,\"text-radial-offset\":64"));
        try (Bound bound =
                bind(
                        maximum,
                        NamedSymbolCatalog.of(List.of(new NamedSymbol("red", vector(4, 4, 1)))))) {
            assertEquals(
                    32_768,
                    bound.binding()
                            .layers()
                            .getFirst()
                            .portrayal()
                            .orElseThrow()
                            .pointLabel()
                            .orElseThrow()
                            .gapPixels());
        }

        MapLibreReadLimits defaults = MapLibreReadLimits.defaults();
        MapLibreStyle expressionFree =
                MapLibreStyles.read(
                        labeledStyle("\"label\"", ",\"text-variable-anchor\":[\"center\",\"top\"]")
                                .getBytes(StandardCharsets.UTF_8),
                        new MapLibreReadOptions(
                                limits(defaults, defaults.maximumCatalogReferences(), 1),
                                CancellationToken.none()));
        assertEquals(MapLibreLayerType.SYMBOL, expressionFree.layers().getFirst().type());
        assertEquals(
                expressionFree,
                MapLibreStyles.read(
                        labeledStyle("\"label\"", ",\"text-variable-anchor\":[\"center\",\"top\"]")
                                .getBytes(StandardCharsets.UTF_8),
                        new MapLibreReadOptions(
                                limits(defaults, defaults.maximumCatalogReferences(), 1),
                                CancellationToken.none())));
    }

    @Test
    void symbolSourceViewsShareIdentityRetainCrsRewriteDiagnosticsAndCloseLocally() {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"a","type":"symbol","source":"s","layout":{
                            "symbol-z-order":"source","icon-image":"red",
                            "icon-allow-overlap":true,"icon-ignore-placement":true}},
                          {"id":"b","type":"symbol","source":"s","layout":{
                            "symbol-z-order":"source","icon-image":"red",
                            "icon-allow-overlap":true,"icon-ignore-placement":true}}
                        ]}
                        """);
        Bound bound =
                bind(
                        style,
                        NamedSymbolCatalog.of(List.of(new NamedSymbol("red", vector(4, 4, 1)))));
        try {
            FeatureSource first = bound.binding().layers().get(0).source();
            FeatureSource second = bound.binding().layers().get(1).source();
            assertSame(first, second);
            assertNotEquals(bound.source().metadata().identity(), first.metadata().identity());
            assertEquals(bound.source().metadata().crs(), first.metadata().crs());
            bound.binding().close();
            assertTrue(first.isClosed());
            assertFalse(bound.source().isClosed());
        } finally {
            bound.close();
        }

        DiagnosticFeatureSource diagnostic = new DiagnosticFeatureSource();
        SingularPointFeatureSource view = new SingularPointFeatureSource(diagnostic);
        assertNotEquals(diagnostic.metadata().identity().id(), view.metadata().identity().id());
        assertEquals(
                view.metadata().identity().id(),
                view.openingDiagnostics().entries().getFirst().sourceId());
        FeatureCursor cursor =
                view.openCursor(
                        new FeatureQuery(
                                Optional.empty(), AttributeSelection.NONE, Optional.empty()),
                        CancellationToken.none());
        view.close();
        assertTrue(cursor.isClosed());
        assertFalse(diagnostic.isClosed());
        diagnostic.close();
    }

    @Test
    void dynamicCaseCompilationPollsAggregateCancellation() {
        ArrayList<Object> expression = new ArrayList<>();
        expression.add("case");
        for (int index = 0; index < 300; index++) {
            expression.add(List.of("has", "p" + index));
            expression.add("red");
        }
        expression.add("red");
        MapLibreReadException failure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreSymbolParser.parse(
                                        Map.of(
                                                "symbol-z-order",
                                                "source",
                                                "icon-image",
                                                expression,
                                                "icon-allow-overlap",
                                                true,
                                                "icon-ignore-placement",
                                                true),
                                        Map.of(),
                                        MapLibreReadLimits.defaults(),
                                        () -> true,
                                        "/layers/0"));
        assertEquals("MAPLIBRE_CANCELLED", failure.problem().code());
    }

    @Test
    void catalogAndUnsupportedPropertiesFailStably() {
        MapLibreBindException missing =
                assertThrows(
                        MapLibreBindException.class,
                        () ->
                                bind(
                                        read(symbolStyle("\"missing\"", "")),
                                        NamedSymbolCatalog.of(
                                                List.of(
                                                        new NamedSymbol(
                                                                "other", vector(4, 4, 1))))));
        assertEquals("MAPLIBRE_ICON_UNRESOLVED", missing.problem().code());
        assertEquals("/layers/0/layout/icon-image", missing.problem().location());

        VectorMarkerSymbol mapUnit =
                VectorMarkerSymbol.of(
                        SQUARE,
                        BOX,
                        Rgba.rgb(1, 2, 3),
                        Optional.empty(),
                        new MarkerPlacement(
                                SymbolSize.square(4, SymbolUnit.MAP_UNIT),
                                SymbolAnchor.CENTER,
                                0,
                                0,
                                0,
                                SymbolRotationMode.SCREEN_RELATIVE),
                        1);
        MapLibreBindException incompatible =
                assertThrows(
                        MapLibreBindException.class,
                        () ->
                                bind(
                                        read(symbolStyle("\"map\"", "")),
                                        NamedSymbolCatalog.of(
                                                List.of(new NamedSymbol("map", mapUnit)))));
        assertEquals("MAPLIBRE_RENDERER_UNAVAILABLE", incompatible.problem().code());

        assertReadCode(
                "MAPLIBRE_VALUE_INVALID",
                labeledStyle("\"x\"", "").replace("[\"SansSerif\"]", "[\"Arial\"]"));
        assertReadCode(
                "MAPLIBRE_VALUE_INVALID",
                symbolStyle("\"red\"", "")
                        .replace("\"icon-allow-overlap\":true", "\"icon-allow-overlap\":false"));
        assertReadCode(
                "MAPLIBRE_PROPERTY_UNSUPPORTED", labeledStyle("\"x\"", ",\"text-halo-width\":2"));
    }

    private static void assertSelected(
            String expression,
            NamedSymbolCatalog catalog,
            Map<String, Object> attributes,
            Class<?> expected) {
        assertInstanceOf(
                expected,
                resolver(expression, catalog).resolve(SymbolRole.MARKER, attributes).orElseThrow());
    }

    private static FeaturePortrayalResolver resolver(
            String expression, NamedSymbolCatalog catalog) {
        try (Bound bound = bind(read(symbolStyle(expression, "")), catalog)) {
            return FeaturePortrayalResolver.compile(
                    bound.binding().layers().getFirst().portrayal().orElseThrow());
        }
    }

    private static Bound bind(MapLibreStyle style, NamedSymbolCatalog catalog) {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("memory", "memory"),
                        List.of(),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.empty(),
                                        Optional.empty())),
                        FeatureSourceLimits.LEVEL_1);
        try {
            return new Bound(
                    MapLibreStyleBinder.bind(
                            style,
                            MapLibreSourceRegistry.builder().register("s", source).build(),
                            catalog),
                    source);
        } catch (RuntimeException failure) {
            source.close();
            throw failure;
        }
    }

    private static VectorMarkerSymbol vector(double width, double height, double opacity) {
        return VectorMarkerSymbol.of(
                SQUARE,
                BOX,
                Rgba.rgb(255, 0, 0),
                Optional.empty(),
                new MarkerPlacement(
                        new SymbolSize(width, height, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE),
                opacity);
    }

    private static String symbolStyle(String icon, String extraLayout) {
        return """
                {"version":8,"sources":{},"layers":[{
                  "id":"symbol","type":"symbol","source":"s",
                  "layout":{
                    "symbol-z-order":"source",
                    "icon-image":__ICON__,
                    "icon-allow-overlap":true,
                    "icon-ignore-placement":true
                    __EXTRA__
                  }
                }]}
                """
                .replace("__ICON__", icon)
                .replace("__EXTRA__", extraLayout);
    }

    private static String labeledStyle(String textField, String extraLayout) {
        return symbolStyle(
                "\"red\"",
                ",\"icon-optional\":true,\"text-field\":"
                        + textField
                        + ",\"text-font\":[\"SansSerif\"],\"text-optional\":true"
                        + extraLayout);
    }

    private static MapLibreReadLimits limits(
            MapLibreReadLimits source, int catalogReferences, int expressionNodes) {
        return new MapLibreReadLimits(
                source.maximumInputBytes(),
                source.maximumNestingDepth(),
                source.maximumTokens(),
                source.maximumStringCharacters(),
                source.maximumAggregateCharacters(),
                source.maximumObjectMembers(),
                source.maximumSources(),
                source.maximumLayers(),
                source.maximumMetadataEntries(),
                expressionNodes,
                source.maximumExpressionDepth(),
                source.maximumStops(),
                source.maximumCategories(),
                catalogReferences,
                source.maximumProducedRules(),
                source.maximumOwnedBytes());
    }

    private static MapLibreStyle read(String json) {
        return MapLibreStyles.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertReadCode(String expected, String json) {
        MapLibreReadException failure = assertThrows(MapLibreReadException.class, () -> read(json));
        assertEquals(expected, failure.problem().code());
        assertFalse(failure.getMessage().contains(json));
    }

    private record Bound(MapLibreStyleBinding binding, InMemoryFeatureSource source)
            implements AutoCloseable {
        @Override
        public void close() {
            binding.close();
            source.close();
        }
    }

    private static final class DiagnosticFeatureSource implements FeatureSource {
        private final InMemoryFeatureSource delegate =
                InMemoryFeatureSource.open(
                        new SourceIdentity("diagnostic", "Diagnostic"), List.of());
        private final DiagnosticReport diagnostics =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "TEST_WARNING",
                                        DiagnosticSeverity.WARNING,
                                        "diagnostic",
                                        Optional.empty(),
                                        "test",
                                        Map.of())),
                        0);

        @Override
        public FeatureSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return diagnostics;
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            return delegate.openCursor(query, cancellation);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
