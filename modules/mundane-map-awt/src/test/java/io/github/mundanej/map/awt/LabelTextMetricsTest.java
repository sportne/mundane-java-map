package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ScreenBox;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class LabelTextMetricsTest {
    private static final LabelTextStyle STYLE =
            new LabelTextStyle(new Rgba(10, 20, 30, 180), LabelWeight.BOLD, 14);

    @Test
    void fixedMetricsReturnFiniteVisualBoundsAndAdvance() {
        LabelTextMetrics.Measurement first = LabelTextMetrics.measure("Label", STYLE, 2, 3);
        LabelTextMetrics.Measurement second = LabelTextMetrics.measure("Label", STYLE, 2, 3);

        assertEquals(first.relativeVisualBounds(), second.relativeVisualBounds());
        assertEquals(first.advance(), second.advance());
        assertTrue(first.advance() > 0);
        assertTrue(Double.isFinite(first.relativeVisualBounds().minX()));
        assertTrue(Double.isFinite(first.relativeVisualBounds().maxY()));
    }

    @Test
    void rejectsOversizedAndMultilineTextWithStableBoundedContext() {
        LabelPlacementException oversized =
                assertThrows(
                        LabelPlacementException.class,
                        () -> LabelTextMetrics.measure("x".repeat(257), STYLE, 2, 3));
        assertEquals("LABEL_TEXT_LIMIT_EXCEEDED", oversized.problem().code());
        assertEquals("2", oversized.problem().context().get("layerIndex"));
        assertEquals("3", oversized.problem().context().get("featureIndex"));
        assertEquals("256", oversized.problem().context().get("limit"));

        LabelPlacementException multiline =
                assertThrows(
                        LabelPlacementException.class,
                        () -> LabelTextMetrics.measure("first\nsecond", STYLE, 4, 5));
        assertEquals("LABEL_TEXT_MULTILINE_UNSUPPORTED", multiline.problem().code());
        assertEquals("10", multiline.problem().context().get("codePoint"));

        LabelPlacementException oversizedMultiline =
                assertThrows(
                        LabelPlacementException.class,
                        () -> LabelTextMetrics.measure("\n" + "x".repeat(256), STYLE, 6, 7));
        assertEquals("LABEL_TEXT_LIMIT_EXCEEDED", oversizedMultiline.problem().code());
    }

    @Test
    void drawingUsesChildGraphicsAndLeavesCallerStateUnchanged() {
        LabelTextMetrics.Measurement measurement = LabelTextMetrics.measure("State", STYLE, 0, 0);
        ScreenBox visual = measurement.relativeVisualBounds().translated(20, 30);
        PlacedPointLabel placed =
                new PlacedPointLabel(
                        "layer",
                        "feature",
                        "State",
                        STYLE,
                        20,
                        30,
                        measurement.advance(),
                        visual,
                        visual,
                        0);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.MAGENTA);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.setTransform(AffineTransform.getTranslateInstance(3, 4));
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

            LabelTextMetrics.draw(graphics, measurement, placed);

            assertEquals(Color.MAGENTA, graphics.getColor());
            assertEquals(AlphaComposite.Clear, graphics.getComposite());
            assertEquals(AffineTransform.getTranslateInstance(3, 4), graphics.getTransform());
            assertEquals(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                    graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING));
        } finally {
            graphics.dispose();
        }
    }
}
