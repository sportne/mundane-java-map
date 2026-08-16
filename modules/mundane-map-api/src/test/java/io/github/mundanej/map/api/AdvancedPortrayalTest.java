package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class AdvancedPortrayalTest {
    private static final Rgba BLACK = Rgba.rgb(0, 0, 0);
    private static final SymbolLength TWO_PIXELS = new SymbolLength(2, SymbolUnit.SCREEN_PIXEL);
    private static final TestMarker MARKER = new TestMarker(1);

    @Test
    void structuredAttributesCanonicalizeDefensivelyAndRemainScalarCompatible() {
        byte[] bytes = {1, 2};
        ArrayList<Object> array = new ArrayList<>(List.of(1, "two", bytes));
        LinkedHashMap<String, Object> object = new LinkedHashMap<>();
        object.put("array", array);
        object.put("nested", Map.of("logical", true));

        StructuredAttributeValue structured = StructuredAttributeValue.of(object);
        object.clear();
        array.clear();
        Map<?, ?> result = (Map<?, ?>) structured.value();
        List<?> resultArray = (List<?>) result.get("array");

        assertEquals(2, result.size());
        assertEquals(1L, resultArray.get(0));
        assertEquals(new AttributeBytes(bytes), resultArray.get(2));
        assertEquals(7, structured.valueCount());
        assertEquals(2, structured.depth());
        assertTrue(structured.logicalSizeBytes() > 0);
        assertThrows(
                UnsupportedOperationException.class, () -> ((Map<Object, Object>) result).clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((List<Object>) resultArray).add("changed"));
        assertSame(structured, AttributeValues.canonicalizeValue(structured));
        assertEquals(4L, AttributeValues.canonicalizeValue(4));
        assertTrue(
                new AttributeField("properties", AttributeType.STRUCTURED, false)
                        .accepts(structured));
        assertEquals(structured, StructuredAttributeValue.of(structured.value()));
        assertEquals(
                structured.hashCode(), StructuredAttributeValue.of(structured.value()).hashCode());
        assertTrue(structured.toString().contains("array"));
    }

    @Test
    void structuredAttributesRejectEveryHostileBoundary() {
        assertThrows(IllegalArgumentException.class, () -> StructuredAttributeValue.of("scalar"));
        assertThrows(
                IllegalArgumentException.class,
                () -> StructuredAttributeValue.of(Map.of(1, "bad")));
        assertThrows(
                IllegalArgumentException.class,
                () -> StructuredAttributeValue.of(List.of(new Object())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StructuredAttributeValue.of(
                                List.of(List.of(1)), new StructuredAttributeLimits(0, 10, 10, 10)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StructuredAttributeValue.of(
                                List.of(1, 2), new StructuredAttributeLimits(2, 2, 10, 10)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StructuredAttributeValue.of(
                                List.of(1, 2), new StructuredAttributeLimits(2, 10, 10, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StructuredAttributeValue.of(
                                Map.of("a", 1, "b", 2),
                                new StructuredAttributeLimits(2, 10, 1, 10)));
        assertThrows(
                IllegalArgumentException.class, () -> new StructuredAttributeLimits(-1, 1, 1, 1));
    }

    @Test
    void neutralExpressionsAreImmutableValueTreesWithExplicitLimits() {
        PortrayalExpression literal = PortrayalExpression.literal(2);
        PortrayalExpression attribute = PortrayalExpression.attribute("population");
        List<PortrayalExpression> inputs =
                List.of(
                        PortrayalExpression.input(PortrayalExpression.Operator.SCALE_DENOMINATOR),
                        PortrayalExpression.input(PortrayalExpression.Operator.ZOOM_LEVEL),
                        PortrayalExpression.input(PortrayalExpression.Operator.GEOMETRY_TYPE));
        ArrayList<PortrayalExpression> arguments = new ArrayList<>(List.of(literal, attribute));
        PortrayalExpression add =
                PortrayalExpression.call(PortrayalExpression.Operator.ADD, arguments);
        arguments.clear();
        PortrayalExpression equal =
                PortrayalExpression.call(
                        PortrayalExpression.Operator.EQUAL, List.of(literal, attribute));

        assertEquals(2L, literal.literal());
        assertEquals("population", attribute.attributeName());
        assertEquals(3, add.nodeCount());
        assertEquals(1, add.depth());
        assertEquals(2, add.arguments().size());
        assertEquals(3, inputs.size());
        assertEquals(
                add,
                PortrayalExpression.call(
                        PortrayalExpression.Operator.ADD, List.of(literal, attribute)));
        assertEquals(
                add.hashCode(),
                PortrayalExpression.call(
                                PortrayalExpression.Operator.ADD, List.of(literal, attribute))
                        .hashCode());
        assertTrue(add.toString().contains("ADD"));
        assertNotEquals(add, equal);
        assertThrows(UnsupportedOperationException.class, () -> add.arguments().add(literal));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PortrayalExpression.call(
                                PortrayalExpression.Operator.EQUAL, List.of(literal)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortrayalExpression.call(PortrayalExpression.Operator.CONCAT, List.of()));
        assertThrows(
                RuntimeException.class,
                () -> PortrayalExpression.input(PortrayalExpression.Operator.LITERAL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PortrayalExpression.call(
                                PortrayalExpression.Operator.ADD,
                                List.of(literal, literal),
                                new PortrayalExpressionLimits(2, 10, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PortrayalExpression.call(
                                PortrayalExpression.Operator.ADD,
                                List.of(add),
                                new PortrayalExpressionLimits(0, 10, 2)));
        assertThrows(IllegalArgumentException.class, () -> new PortrayalExpressionLimits(-1, 1, 1));
    }

    @Test
    void evaluationResultsAreExactlySuccessOrFailure() {
        PortrayalEvaluationResult success = PortrayalEvaluationResult.success(1);
        PortrayalEvaluationResult failure =
                PortrayalEvaluationResult.failure("PORTRAYAL_TEST", "test failure");

        assertTrue(success.succeeded());
        assertEquals(1L, success.value().orElseThrow());
        assertFalse(failure.succeeded());
        assertEquals("PORTRAYAL_TEST", failure.code());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortrayalEvaluationResult(Optional.empty(), "", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortrayalEvaluationResult.failure(" ", "message"));
    }

    @Test
    void advancedStrokeGraphicFillAndLineCaptureEveryPrimitive() {
        GraphicPaint graphic =
                new GraphicPaint(
                        MARKER,
                        SymbolSize.square(8, SymbolUnit.SCREEN_PIXEL),
                        new SymbolLength(4, SymbolUnit.SCREEN_PIXEL),
                        30,
                        0.75);
        ArrayList<Double> dashes = new ArrayList<>(List.of(4.0, 2.0));
        AdvancedStroke stroke =
                new AdvancedStroke(
                        BLACK,
                        TWO_PIXELS,
                        AdvancedStroke.Cap.SQUARE,
                        AdvancedStroke.Join.BEVEL,
                        dashes,
                        1,
                        -2,
                        Optional.of(graphic));
        dashes.clear();
        AdvancedLineSymbol line = new AdvancedLineSymbol(stroke, 0.5);
        AdvancedFillSymbol fill =
                new AdvancedFillSymbol(
                        Optional.empty(), Optional.of(graphic), Optional.of(stroke), 0.75);

        assertEquals(List.of(4.0, 2.0), stroke.dashArray());
        assertEquals(AdvancedStroke.Cap.SQUARE, stroke.cap());
        assertEquals(AdvancedStroke.Join.BEVEL, stroke.join());
        assertEquals(AdvancedLineSymbol.RENDERER_KEY, line.rendererKey());
        assertEquals(SymbolRole.LINE, line.role());
        assertEquals(AdvancedFillSymbol.RENDERER_KEY, fill.rendererKey());
        assertEquals(SymbolRole.FILL, fill.role());
        assertEquals(AdvancedStroke.Cap.BUTT, AdvancedStroke.solid(BLACK, TWO_PIXELS).cap());
        assertEquals(3, AdvancedStroke.Cap.values().length);
        assertEquals(3, AdvancedStroke.Join.values().length);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdvancedStroke(
                                BLACK,
                                TWO_PIXELS,
                                AdvancedStroke.Cap.ROUND,
                                AdvancedStroke.Join.ROUND,
                                List.of(0.0),
                                0,
                                0,
                                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AdvancedLineSymbol(stroke, 2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdvancedFillSymbol(
                                Optional.of(BLACK), Optional.of(graphic), Optional.empty(), 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphicPaint(
                                new TestLine(),
                                SymbolSize.square(1, SymbolUnit.SCREEN_PIXEL),
                                TWO_PIXELS,
                                0,
                                1));
    }

    @Test
    void textPlacementHaloAndFallbacksAreBounded() {
        TextPortrayal.Placement point =
                new TextPortrayal.Placement(TextPortrayal.Mode.POINT, 0.5, 1, 2, -2, 15, 0, 180);
        TextPortrayal.Placement line =
                new TextPortrayal.Placement(TextPortrayal.Mode.LINE, 0.5, 0.5, 0, 0, 0, 10, 30);
        TextPortrayal.Halo halo = new TextPortrayal.Halo(Rgba.rgb(255, 255, 255), TWO_PIXELS);
        TextPortrayal text =
                new TextPortrayal(
                        PortrayalExpression.attribute("name"),
                        List.of("Noto Sans", "sans-serif"),
                        600,
                        new SymbolLength(12, SymbolUnit.SCREEN_PIXEL),
                        BLACK,
                        point,
                        Optional.of(halo),
                        0.9);

        assertEquals(2, text.fontFamilies().size());
        assertEquals(TextPortrayal.Mode.LINE, line.mode());
        assertEquals(2, TextPortrayal.Mode.values().length);
        assertEquals(halo, text.halo().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextPortrayal.Placement(TextPortrayal.Mode.POINT, -1, 0, 0, 0, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TextPortrayal(
                                PortrayalExpression.literal("x"),
                                List.of(),
                                400,
                                TWO_PIXELS,
                                BLACK,
                                point,
                                Optional.empty(),
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TextPortrayal(
                                PortrayalExpression.literal("x"),
                                List.of("font"),
                                0,
                                TWO_PIXELS,
                                BLACK,
                                point,
                                Optional.empty(),
                                1));
    }

    @Test
    void rasterBandsColorMapsAndFallbackAreDeterministic() {
        RasterPortrayal.ColorStop low = new RasterPortrayal.ColorStop(0, BLACK, Optional.of("low"));
        RasterPortrayal.ColorStop high =
                new RasterPortrayal.ColorStop(10, Rgba.rgb(255, 255, 255), Optional.empty());
        RasterPortrayal ramp =
                new RasterPortrayal(
                        List.of(0),
                        List.of(low, high),
                        RasterPortrayal.ColorMapMode.RAMP,
                        Rgba.TRANSPARENT,
                        RasterInterpolation.BILINEAR,
                        0.8);
        RasterPortrayal direct =
                new RasterPortrayal(
                        List.of(0, 1, 2),
                        List.of(),
                        RasterPortrayal.ColorMapMode.VALUES,
                        Rgba.TRANSPARENT,
                        RasterInterpolation.NEAREST,
                        1);

        assertEquals(2, ramp.colorMap().size());
        assertEquals(3, direct.bands().size());
        assertEquals(3, RasterPortrayal.ColorMapMode.values().length);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterPortrayal(
                                List.of(0, 0),
                                List.of(),
                                RasterPortrayal.ColorMapMode.INTERVALS,
                                BLACK,
                                RasterInterpolation.NEAREST,
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterPortrayal(
                                List.of(0),
                                List.of(high, low),
                                RasterPortrayal.ColorMapMode.RAMP,
                                BLACK,
                                RasterInterpolation.NEAREST,
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterPortrayal.ColorStop(Double.NaN, BLACK, Optional.empty()));
    }

    @Test
    void rendererCapabilityRequiresExplicitAcceptApproximateOrRejectDecision() {
        RendererCapability accepted = RendererCapability.accept();
        RendererCapability approximated = RendererCapability.approximate("drop-dashes");
        RendererCapability rejected =
                RendererCapability.reject("RENDERER_GRAPHIC_FILL_UNSUPPORTED");
        SymbolRendererCapabilities capabilities =
                symbol -> symbol instanceof AdvancedLineSymbol ? accepted : rejected;

        assertEquals(RendererCapability.Support.ACCEPT, accepted.support());
        assertEquals("drop-dashes", approximated.approximationPolicy().orElseThrow());
        assertEquals(RendererCapability.Support.REJECT, capabilities.capability(MARKER).support());
        assertEquals(3, RendererCapability.Support.values().length);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RendererCapability(
                                RendererCapability.Support.ACCEPT, Optional.of("bad"), "CODE"));
        assertThrows(IllegalArgumentException.class, () -> RendererCapability.approximate(" "));
        assertThrows(IllegalArgumentException.class, () -> RendererCapability.reject(" "));
    }

    private record TestMarker(double opacity) implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("test.marker");
        }
    }

    private record TestLine() implements LineSymbol {
        @Override
        public double opacity() {
            return 1;
        }

        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("test.line");
        }
    }
}
