package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FilteredSymbolSelector;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.InterpolatedSymbolSelector;
import io.github.mundanej.map.api.InterpolatedSymbolStop;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PortrayalComparison;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalGeometryType;
import io.github.mundanej.map.api.PortrayalOperand;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyles;
import io.github.mundanej.map.io.se.SeReadOptions;
import io.github.mundanej.map.io.se.SeStyles;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalog;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolFixtures;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BrowserPortrayalAdapterParityTest {
    private static final NamedSymbolCatalog EMPTY = NamedSymbolCatalog.of(List.of());
    private static final VectorMarkerSymbol RED = marker(Rgba.rgb(210, 30, 30), 10);
    private static final VectorMarkerSymbol BLUE = marker(Rgba.rgb(30, 60, 210), 10);

    @Test
    void allNativeSelectorFamiliesUseTheUnmodifiedCoreResolutionAndExactProjection() {
        PortrayalPredicate hot =
                new PortrayalPredicate.Comparison(
                        PortrayalComparison.EQUAL,
                        new PortrayalOperand.Property("kind"),
                        new PortrayalOperand.TypedLiteral(ThematicValue.text("hot")));
        PortrayalRule rule =
                new PortrayalRule(
                        Optional.empty(),
                        ScaleInterval.ALL,
                        Optional.of(hot),
                        false,
                        List.of(RED),
                        List.of(),
                        List.of());
        List<FeaturePortrayal> portrayals =
                List.of(
                        FeaturePortrayal.markers(new FixedSymbolSelector(RED)),
                        FeaturePortrayal.markers(
                                new CategoricalSymbolSelector(
                                        "kind",
                                        List.of(
                                                new CategoricalSymbolRule(
                                                        ThematicValue.text("hot"), RED)),
                                        Optional.empty())),
                        FeaturePortrayal.markers(
                                new GraduatedSymbolSelector(
                                        "score",
                                        List.of(
                                                new GraduatedSymbolStep(BigDecimal.ZERO, RED),
                                                new GraduatedSymbolStep(BigDecimal.TEN, BLUE)),
                                        Optional.empty())),
                        FeaturePortrayal.markers(
                                InterpolatedSymbolSelector.attribute(
                                        "score",
                                        List.of(
                                                new InterpolatedSymbolStop(BigDecimal.ZERO, RED),
                                                new InterpolatedSymbolStop(BigDecimal.TEN, BLUE)),
                                        RED)),
                        FeaturePortrayal.markers(
                                new FilteredSymbolSelector(hot, new FixedSymbolSelector(RED))),
                        new RulePortrayalPlan(List.of(rule)).portrayal());
        Map<String, Object> attributes = Map.of("kind", "hot", "score", 10L);
        for (int index = 0; index < portrayals.size(); index++) {
            assertAccepted("native-" + index, portrayals.get(index), EMPTY, attributes);
        }
    }

    @Test
    void seMapLibreAndMilitaryWorkflowsConvergeOnTheSameClosedBrowserProfile() {
        String se =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se"
                    xmlns:ogc="http://www.opengis.net/ogc" version="1.1.0">
                  <se:Rule><se:PointSymbolizer><se:Graphic><se:Mark>
                    <se:WellKnownName>square</se:WellKnownName>
                    <se:Fill><se:SvgParameter name="fill">#123456</se:SvgParameter></se:Fill>
                  </se:Mark><se:Size>10</se:Size></se:Graphic></se:PointSymbolizer></se:Rule>
                </se:FeatureTypeStyle>
                """;
        FeaturePortrayal sePortrayal =
                SeStyles.read(
                                "browser-parity",
                                se.getBytes(StandardCharsets.UTF_8),
                                EMPTY,
                                SeReadOptions.defaults())
                        .portrayal();
        assertAccepted("se", sePortrayal, EMPTY, Map.of());

        String mapLibre =
                """
                {"version":8,"sources":{},"layers":[{
                  "id":"points","type":"circle","source":"s",
                  "paint":{"circle-radius":5,"circle-color":"#123456"}
                }]}
                """;
        FeaturePortrayal mapLibrePortrayal =
                MapLibreStyles.read(mapLibre.getBytes(StandardCharsets.UTF_8))
                        .layers()
                        .getFirst()
                        .portrayal()
                        .orElseThrow();
        assertAccepted("maplibre", mapLibrePortrayal, EMPTY, Map.of());

        FeaturePortrayal military =
                MilitarySymbolCatalog.portrayal(
                        "sidc",
                        MarkerPlacement.centeredScreen(40),
                        MilitarySymbolPalette.lightBackground(),
                        1);
        assertAccepted(
                "milstd2525",
                military,
                EMPTY,
                Map.of("sidc", MilitarySymbolFixtures.FRIEND_INFANTRY_PRESENT));
        assertOmitted("milstd2525-unsupported", military, Map.of("sidc", "unsupported"));
    }

    private static void assertAccepted(
            String id,
            FeaturePortrayal portrayal,
            NamedSymbolCatalog catalog,
            Map<String, Object> attributes) {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity(id, id),
                        List.of(
                                new FeatureRecord(
                                        "selected",
                                        "Selected",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        attributes)),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())),
                        io.github.mundanej.map.api.FeatureSourceLimits.LEVEL_1);
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(id, id, source, portrayal, catalog, Optional.empty());
        FeaturePortrayalResolver expected = FeaturePortrayalResolver.compile(portrayal);
        assertEquals(
                expected.requiredSymbolAttributes().isEmpty()
                        ? AttributeSelection.NONE
                        : AttributeSelection.only(expected.requiredSymbolAttributes()),
                binding.attributes());
        for (Symbol symbol : expected.reachableSymbols()) {
            SceneProtocol.requirePortrayalSymbol(
                    symbol, symbol.role(), binding::authorizes, "binding");
        }
        MapViewport viewport = new MapViewport(100, 100, 0, 0, 1);
        FeatureSourceQueryEngine.Result queried =
                new FeatureSourceQueryEngine()
                        .query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertTrue(!queried.cancelled());
        assertEquals(1, queried.layers().getFirst().features().size());
        assertEquals("selected", queried.layers().getFirst().features().getFirst().id());
        PortrayalEvaluationContext browserContext =
                browserContext(viewport).withGeometryType(PortrayalGeometryType.POINT);
        assertEquals(
                expected.resolveAll(attributes, browserContext).marker().orElseThrow(),
                queried.layers().getFirst().features().getFirst().symbol());
        assertTrue(queried.layers().getFirst().envelope().isPresent());
        SceneProtocol.Result encoded =
                new SceneProtocol(SceneProtocol.DEFAULT_LIMITS)
                        .encode(queried.layers(), Rgba.rgb(255, 255, 255), viewport, 1, 1);
        Map<?, ?> encodedLayer = (Map<?, ?>) ((List<?>) encoded.scene().get("layers")).getFirst();
        Map<?, ?> encodedFeature = (Map<?, ?>) ((List<?>) encodedLayer.get("features")).getFirst();
        assertTrue(!((List<?>) encodedFeature.get("primitives")).isEmpty());
        binding.close();
        source.close();
    }

    private static void assertOmitted(
            String id, FeaturePortrayal portrayal, Map<String, Object> attributes) {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity(id, id),
                        List.of(
                                new FeatureRecord(
                                        "omitted",
                                        "Omitted",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        attributes)),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())),
                        io.github.mundanej.map.api.FeatureSourceLimits.LEVEL_1);
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(id, id, source, portrayal, Optional.empty());
        MapViewport viewport = new MapViewport(100, 100, 0, 0, 1);
        assertTrue(
                FeaturePortrayalResolver.compile(portrayal)
                        .resolveAll(
                                attributes,
                                browserContext(viewport)
                                        .withGeometryType(PortrayalGeometryType.POINT))
                        .marker()
                        .isEmpty());
        FeatureSourceQueryEngine.Result queried =
                new FeatureSourceQueryEngine()
                        .query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertTrue(queried.layers().getFirst().features().isEmpty());
        assertTrue(queried.layers().getFirst().envelope().isPresent());
        SceneProtocol.Result encoded =
                new SceneProtocol(SceneProtocol.DEFAULT_LIMITS)
                        .encode(queried.layers(), Rgba.rgb(255, 255, 255), viewport, 1, 1);
        Map<?, ?> encodedLayer = (Map<?, ?>) ((List<?>) encoded.scene().get("layers")).getFirst();
        assertTrue(((List<?>) encodedLayer.get("features")).isEmpty());
        binding.close();
        source.close();
    }

    private static PortrayalEvaluationContext browserContext(MapViewport viewport) {
        double scale = viewport.worldUnitsPerPixel() / 0.00028;
        double zoom =
                StrictMath.log(
                                CrsDefinitions.EPSG_3857.coordinateDomain().width()
                                        / (512.0 * viewport.worldUnitsPerPixel()))
                        / StrictMath.log(2.0);
        return PortrayalEvaluationContext.atScaleAndZoom(scale, zoom);
    }

    private static VectorMarkerSymbol marker(Rgba fill, double size) {
        VectorPath path =
                VectorPath.builder()
                        .moveTo(0, 0)
                        .lineTo(1, 0)
                        .lineTo(1, 1)
                        .lineTo(0, 1)
                        .close()
                        .build();
        return VectorMarkerSymbol.filledScreen(path, new Envelope(0, 0, 1, 1), fill, size, 1);
    }
}
