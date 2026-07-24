package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeValueCandidate;
import io.github.mundanej.map.api.AttributeValueConversion;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.InterpolatedSymbolSelector;
import io.github.mundanej.map.api.InterpolatedSymbolStop;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InterpolatedFeaturePortrayalResolverTest {
    private static final SolidLineSymbol BLACK = line(Rgba.rgb(0, 0, 0), 2);
    private static final SolidLineSymbol WHITE = line(Rgba.rgb(255, 255, 255), 10);
    private static final SolidLineSymbol FALLBACK = line(Rgba.rgb(255, 0, 0), 1);

    @Test
    void attributeInterpolationClampsAndUsesFallbackForNonNumericValues() {
        FeaturePortrayalResolver resolver =
                resolver(
                        InterpolatedSymbolSelector.attribute(
                                "score", List.of(stop(0, BLACK), stop(10, WHITE)), FALLBACK));

        assertEquals(BLACK, selected(resolver, Map.of("score", -1L)));
        SolidLineSymbol middle = selected(resolver, Map.of("score", 5L));
        assertEquals(Rgba.rgb(128, 128, 128), middle.stroke().color());
        assertEquals(6.0, middle.stroke().width().value());
        assertEquals(WHITE, selected(resolver, Map.of("score", 12L)));
        assertEquals(FALLBACK, selected(resolver, Map.of()));
        assertEquals(FALLBACK, selected(resolver, Map.of("score", "bad")));
        assertEquals(List.of("score"), resolver.requiredSymbolAttributes());
    }

    @Test
    void zoomInterpolationUsesExplicitContextAndRejectsIncompatibleEndpoints() {
        FeaturePortrayalResolver resolver =
                resolver(
                        InterpolatedSymbolSelector.zoom(
                                List.of(stop(0, BLACK), stop(10, WHITE)), FALLBACK));
        SolidLineSymbol middle =
                (SolidLineSymbol)
                        resolver.resolveAll(
                                        Map.of(), PortrayalEvaluationContext.atScaleAndZoom(100, 5))
                                .line()
                                .orElseThrow();
        assertEquals(6.0, middle.stroke().width().value());
        assertTrue(resolver.requiresZoomContext());
        assertEquals(
                FALLBACK,
                resolver.resolveAll(Map.of(), PortrayalEvaluationContext.atScale(100))
                        .line()
                        .orElseThrow());

        SolidLineSymbol mapUnits =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(0, 0, 0), new SymbolLength(2, SymbolUnit.MAP_UNIT)),
                        1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        resolver(
                                InterpolatedSymbolSelector.zoom(
                                        List.of(stop(0, BLACK), stop(10, mapUnits)), FALLBACK)));

        assertEquals(
                new InterpolatedSymbolStop(new BigDecimal("1.0"), BLACK),
                new InterpolatedSymbolStop(new BigDecimal("1.00"), BLACK));
    }

    @Test
    void explicitConversionCandidatesDefineTheExactRequiredAttributes() {
        AttributeValueConversion conversion =
                AttributeValueConversion.toNumber(
                        List.of(new AttributeValueCandidate.Attribute("actual")));
        FeaturePortrayalResolver resolver =
                resolver(
                        InterpolatedSymbolSelector.expressionInput(
                                "nominal",
                                List.of(stop(0, BLACK), stop(10, WHITE)),
                                FALLBACK,
                                conversion));

        assertEquals(List.of("actual"), resolver.requiredSymbolAttributes());
        assertEquals(WHITE, selected(resolver, Map.of("actual", 10L)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AttributeValueCandidate.Literal(
                                ThematicValue.date(LocalDate.of(2026, 1, 1))));
    }

    private static FeaturePortrayalResolver resolver(InterpolatedSymbolSelector selector) {
        return FeaturePortrayalResolver.compile(
                new FeaturePortrayal(Optional.empty(), Optional.of(selector), Optional.empty()));
    }

    private static SolidLineSymbol selected(
            FeaturePortrayalResolver resolver, Map<String, Object> attributes) {
        return (SolidLineSymbol) resolver.resolve(SymbolRole.LINE, attributes).orElseThrow();
    }

    private static InterpolatedSymbolStop stop(long value, SolidLineSymbol symbol) {
        return new InterpolatedSymbolStop(BigDecimal.valueOf(value), symbol);
    }

    private static SolidLineSymbol line(Rgba color, double width) {
        return SolidLineSymbol.of(
                new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL)), 1);
    }
}
