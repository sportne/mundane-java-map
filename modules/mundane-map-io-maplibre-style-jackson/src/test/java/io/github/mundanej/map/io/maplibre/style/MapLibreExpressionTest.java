package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapLibreExpressionTest {
    @Test
    void matchStepCaseAndConversionsSelectDeterministically() {
        FeaturePortrayalResolver match =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"match","type":"circle","source":"s",
                          "paint":{"circle-color":
                            ["match",["get","kind"],"city","#ff0000","port","#0000ff","#00ff00"]}
                        }]}
                        """);
        assertEquals(Rgba.rgb(255, 0, 0), marker(match, Map.of("kind", "city")).fill());
        assertEquals(Rgba.rgb(0, 255, 0), marker(match, Map.of()).fill());
        assertEquals(java.util.List.of("kind"), match.requiredSymbolAttributes());

        FeaturePortrayalResolver convertedMatch =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"converted-match","type":"circle","source":"s",
                          "paint":{"circle-color":
                            ["match",["to-number",["get","kind"]],16,"#ff0000","#00ff00"]}
                        }]}
                        """);
        assertEquals(Rgba.rgb(255, 0, 0), marker(convertedMatch, Map.of("kind", "0x10")).fill());

        FeaturePortrayalResolver step =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"step","type":"line","source":"s",
                          "layout":{"line-cap":"round","line-join":"round"},
                          "paint":{"line-width":
                            ["step",["to-number",["get","rank"],["get","backup"],0],
                             2,10,4,20,8]}
                        }]}
                        """);
        assertEquals(2.0, line(step, Map.of("rank", 5L)).stroke().width().value());
        assertEquals(4.0, line(step, Map.of("rank", 10L)).stroke().width().value());
        assertEquals(8.0, line(step, Map.of("rank", 99L)).stroke().width().value());
        assertEquals(4.0, line(step, Map.of("rank", "0x10")).stroke().width().value());
        assertEquals(
                4.0,
                line(step, Map.of("rank", "\u00a0" + "10" + "\ufeff")).stroke().width().value());
        assertEquals(
                8.0, line(step, Map.of("rank", "bad", "backup", 20L)).stroke().width().value());
        assertEquals(2.0, line(step, Map.of("rank", "bad")).stroke().width().value());
        assertEquals(java.util.List.of("rank", "backup"), step.requiredSymbolAttributes());

        FeaturePortrayalResolver conditional =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"case","type":"fill","source":"s",
                          "paint":{"fill-color":
                            ["case",["has","selected"],"#ffff00",
                                    ["==",["get","kind"],"water"],"#0000ff","#808080"]}
                        }]}
                        """);
        assertEquals(Rgba.rgb(255, 255, 0), fill(conditional, Map.of("selected", true)).fill());
        assertEquals(Rgba.rgb(0, 0, 255), fill(conditional, Map.of("kind", "water")).fill());
        assertEquals(Rgba.rgb(128, 128, 128), fill(conditional, Map.of()).fill());
    }

    @Test
    void interpolationUsesDataOrZoomAndFallsBackOnInvalidInput() {
        FeaturePortrayalResolver data =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"data","type":"line","source":"s",
                          "layout":{"line-cap":"round","line-join":"round"},
                          "paint":{"line-color":
                            ["interpolate",["linear"],["to-number",["get","score"]],
                             0,"#000000",10,"#ffffff"]}
                        }]}
                        """);
        assertEquals(Rgba.rgb(128, 128, 128), line(data, Map.of("score", 5L)).stroke().color());
        assertEquals(Rgba.rgb(128, 128, 128), line(data, Map.of("score", "0x5")).stroke().color());
        assertEquals(Rgba.rgb(0, 0, 0), line(data, Map.of("score", "bad")).stroke().color());

        FeaturePortrayalResolver zoom =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"zoom","type":"circle","source":"s",
                          "paint":{"circle-radius":
                            ["interpolate",["linear"],["zoom"],0,4,10,14]}
                        }]}
                        """);
        assertTrue(zoom.requiresZoomContext());
        VectorMarkerSymbol middle =
                (VectorMarkerSymbol)
                        zoom.resolveAll(Map.of(), PortrayalEvaluationContext.atScaleAndZoom(1, 5))
                                .forRole(SymbolRole.MARKER)
                                .orElseThrow();
        assertEquals(18.0, middle.placement().size().width());
    }

    @Test
    void matchTreatsMissingAndExplicitNullEquallyAndZoomStepUsesContext() {
        FeaturePortrayalResolver nullable =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"null","type":"circle","source":"s",
                          "paint":{"circle-color":
                            ["match",["get","kind"],null,"#ff0000","#0000ff"]}
                        }]}
                        """);
        assertEquals(Rgba.rgb(255, 0, 0), marker(nullable, Map.of()).fill());
        assertEquals(
                Rgba.rgb(255, 0, 0),
                marker(nullable, Map.of("kind", AttributeNull.INSTANCE)).fill());

        FeaturePortrayalResolver zoom =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"zoom","type":"line","source":"s",
                          "layout":{"line-cap":"round","line-join":"round"},
                          "paint":{"line-width":["step",["zoom"],1,5,3,10,7]}
                        }]}
                        """);
        assertTrue(zoom.requiresZoomContext());
        SolidLineSymbol selected =
                (SolidLineSymbol)
                        zoom.resolveAll(Map.of(), PortrayalEvaluationContext.atScaleAndZoom(1, 7))
                                .line()
                                .orElseThrow();
        assertEquals(3.0, selected.stroke().width().value());
    }

    @Test
    void omissionsAndHiddenDynamicLayersRemainValid() {
        FeaturePortrayalResolver resolver =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"zero","type":"line","source":"s",
                          "layout":{"line-cap":"round","line-join":"round"},
                          "paint":{"line-width":["step",["get","rank"],0,1,2]}
                        }]}
                        """);
        assertTrue(resolver.resolve(SymbolRole.LINE, Map.of("rank", 0L)).isEmpty());
        assertEquals(2.0, line(resolver, Map.of("rank", 1L)).stroke().width().value());

        MapLibreStyle hidden =
                read(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"hidden","type":"line","source":"s",
                          "layout":{"visibility":"none"},
                          "paint":{"line-width":["step",["get","rank"],0,1,2]}
                        }]}
                        """);
        assertTrue(hidden.layers().getFirst().portrayal().isEmpty());

        MapLibreStyle hiddenInterpolation =
                read(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"hidden-interpolation","type":"line","source":"s",
                          "layout":{"visibility":"none"},
                          "paint":{"line-width":
                            ["interpolate",["linear"],["get","rank"],0,1,10,2]}
                        }]}
                        """);
        assertTrue(hiddenInterpolation.layers().getFirst().portrayal().isEmpty());
    }

    @Test
    void unsupportedFilterAndPropertyShapesUseStableDiagnostics() {
        MapLibreReadException branchingFilter =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                read(
                                        """
                                        {"version":8,"sources":{},"layers":[{
                                          "id":"filter","type":"circle","source":"s",
                                          "filter":["case",["has","enabled"],true,false],
                                          "paint":{"circle-color":"#123456"}
                                        }]}
                                        """));
        assertEquals("MAPLIBRE_EXPRESSION_UNSUPPORTED", branchingFilter.problem().code());

        MapLibreReadException multiple =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                read(
                                        """
                                        {"version":8,"sources":{},"layers":[{
                                          "id":"x","type":"circle","source":"s",
                                          "paint":{
                                            "circle-radius":["step",["get","x"],1,2,3],
                                            "circle-color":["match",["get","x"],1,"#fff000","#000000"]
                                          }
                                        }]}
                                        """));
        assertEquals("MAPLIBRE_EXPRESSION_UNSUPPORTED", multiple.problem().code());

        MapLibreReadException literalOnly =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                read(
                                        """
                                        {"version":8,"sources":{},"layers":[{
                                          "id":"literal","type":"line","source":"s",
                                          "layout":{"line-cap":"round","line-join":"round"},
                                          "paint":{"line-offset":["step",["get","x"],0,1,2]}
                                        }]}
                                        """));
        assertEquals("MAPLIBRE_EXPRESSION_UNSUPPORTED", literalOnly.problem().code());

        MapLibreReadException convertedResult =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                read(
                                        """
                                        {"version":8,"sources":{},"layers":[{
                                          "id":"result","type":"circle","source":"s",
                                          "paint":{"circle-radius":
                                            ["match",["get","x"],1,["to-number","2"],3]}
                                        }]}
                                        """));
        assertEquals("MAPLIBRE_EXPRESSION_TYPE", convertedResult.problem().code());

        FeaturePortrayalResolver constantInequality =
                resolver(
                        """
                        {"version":8,"sources":{},"layers":[{
                          "id":"constant","type":"circle","source":"s",
                          "filter":["!=",null,1],
                          "paint":{"circle-color":"#123456"}
                        }]}
                        """);
        assertTrue(constantInequality.resolve(SymbolRole.MARKER, Map.of()).isPresent());
    }

    @Test
    void incompatibleInterpolationFailsWhileReading() {
        MapLibreReadException failure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                read(
                                        """
                                        {"version":8,"sources":{},"layers":[{
                                          "id":"incompatible","type":"line","source":"s",
                                          "layout":{"line-cap":"round","line-join":"round"},
                                          "paint":{"line-width":
                                            ["interpolate",["linear"],["get","x"],0,0,10,4]}
                                        }]}
                                        """));
        assertEquals("MAPLIBRE_EXPRESSION_TYPE", failure.problem().code());
        assertEquals("/layers/0/paint/line-width", failure.problem().location());
    }

    @Test
    void callerExpressionLimitsAreAppliedBeforeCompilation() {
        MapLibreReadLimits defaults = MapLibreReadLimits.defaults();
        MapLibreReadLimits limits =
                new MapLibreReadLimits(
                        defaults.maximumInputBytes(),
                        defaults.maximumNestingDepth(),
                        defaults.maximumTokens(),
                        defaults.maximumStringCharacters(),
                        defaults.maximumAggregateCharacters(),
                        defaults.maximumObjectMembers(),
                        defaults.maximumSources(),
                        defaults.maximumLayers(),
                        defaults.maximumMetadataEntries(),
                        5,
                        defaults.maximumExpressionDepth(),
                        defaults.maximumStops(),
                        defaults.maximumCategories(),
                        defaults.maximumCatalogReferences(),
                        defaults.maximumProducedRules(),
                        defaults.maximumOwnedBytes());
        MapLibreReadException failure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreStyles.read(
                                        """
                                        {"version":8,"sources":{},"layers":[{
                                          "id":"limited","type":"circle","source":"s",
                                          "paint":{"circle-radius":
                                            ["case",["has","a"],1,["has","b"],2,3]}
                                        }]}
                                        """
                                                .getBytes(StandardCharsets.UTF_8),
                                        new MapLibreReadOptions(limits, CancellationToken.none())));
        assertEquals("MAPLIBRE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals("expressionNodes", failure.problem().context().get("limit"));
    }

    @Test
    void accountingAndFilterCompilationPollCancellation() {
        List<Object> property = new ArrayList<>();
        property.add("all");
        for (int index = 0; index < 300; index++) {
            property.add(true);
        }
        MapLibreReadException accounting =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreExpressionAccounting.count(
                                        Map.of(),
                                        Map.of("circle-radius", property),
                                        MapLibreReadLimits.defaults(),
                                        () -> true,
                                        "/layers/0"));
        assertEquals("MAPLIBRE_CANCELLED", accounting.problem().code());

        List<Object> filter = new ArrayList<>();
        filter.add("all");
        for (int index = 0; index < 300; index++) {
            filter.add(true);
        }
        MapLibreReadException compilation =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreFilters.compileLayerFilter(
                                        filter,
                                        MapLibreReadLimits.defaults(),
                                        0,
                                        () -> true,
                                        "/layers/0/filter"));
        assertEquals("MAPLIBRE_CANCELLED", compilation.problem().code());
    }

    private static FeaturePortrayalResolver resolver(String json) {
        return FeaturePortrayalResolver.compile(
                read(json).layers().getFirst().portrayal().orElseThrow());
    }

    private static MapLibreStyle read(String json) {
        return MapLibreStyles.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static VectorMarkerSymbol marker(
            FeaturePortrayalResolver resolver, Map<String, Object> attributes) {
        return (VectorMarkerSymbol) resolver.resolve(SymbolRole.MARKER, attributes).orElseThrow();
    }

    private static SolidLineSymbol line(
            FeaturePortrayalResolver resolver, Map<String, Object> attributes) {
        return (SolidLineSymbol) resolver.resolve(SymbolRole.LINE, attributes).orElseThrow();
    }

    private static SolidFillSymbol fill(
            FeaturePortrayalResolver resolver, Map<String, Object> attributes) {
        return (SolidFillSymbol) resolver.resolve(SymbolRole.FILL, attributes).orElseThrow();
    }
}
