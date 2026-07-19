package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelPlacementProblem;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointLabelTexts;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ScreenBox;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fixed logical-font metrics and operation-local Java2D label drawing. */
final class LabelTextMetrics {
    private static final FontRenderContext CONTEXT =
            new FontRenderContext(
                    new AffineTransform(),
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    private LabelTextMetrics() {}

    static Measurement measure(
            String text, LabelTextStyle style, int layerIndex, int featureIndex) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        validateText(text, layerIndex, featureIndex);
        return measureValidated(text, style, layerIndex, featureIndex);
    }

    static Measurement measureValidated(
            String text, LabelTextStyle style, int layerIndex, int featureIndex) {
        Font font =
                new Font(
                                Font.SANS_SERIF,
                                style.weight() == LabelWeight.BOLD ? Font.BOLD : Font.PLAIN,
                                1)
                        .deriveFont((float) style.sizePixels());
        TextLayout layout = new TextLayout(text, font, CONTEXT);
        Rectangle2D bounds = layout.getBounds();
        double advance = layout.getAdvance();
        if (!finite(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY())) {
            throw metricFailure(layerIndex, featureIndex, "visualBounds");
        }
        if (!Double.isFinite(advance) || advance < 0.0) {
            throw metricFailure(layerIndex, featureIndex, "advance");
        }
        return new Measurement(
                layout,
                new ScreenBox(
                        bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY()),
                advance);
    }

    static void draw(Graphics2D graphics, Measurement measurement, PlacedPointLabel label) {
        Graphics2D child = (Graphics2D) Objects.requireNonNull(graphics, "graphics").create();
        try {
            child.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            child.setRenderingHint(
                    RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            child.setComposite(AlphaComposite.SrcOver);
            Rgba color = label.style().color();
            child.setColor(new Color(color.red(), color.green(), color.blue(), color.alpha()));
            measurement.layout().draw(child, (float) label.baselineX(), (float) label.baselineY());
        } finally {
            child.dispose();
        }
    }

    static int validateText(String text, int layerIndex, int featureIndex) {
        try {
            return PointLabelTexts.requireSupported(text);
        } catch (PointLabelTexts.ValidationException failure) {
            if (failure.reason() == PointLabelTexts.FailureReason.TOO_LONG) {
                throw new LabelPlacementException(
                        new LabelPlacementProblem(
                                "LABEL_TEXT_LIMIT_EXCEEDED",
                                "Point-label text exceeds the supported code-point limit",
                                orderedContext(
                                        layerIndex,
                                        featureIndex,
                                        "limit",
                                        Integer.toString(PointLabelTexts.MAXIMUM_CODE_POINTS),
                                        "attemptedAtLeast",
                                        Integer.toString(
                                                PointLabelTexts.MAXIMUM_CODE_POINTS + 1))));
            }
            if (failure.reason() == PointLabelTexts.FailureReason.MULTILINE) {
                throw new LabelPlacementException(
                        new LabelPlacementProblem(
                                "LABEL_TEXT_MULTILINE_UNSUPPORTED",
                                "Point-label text contains an unsupported line separator",
                                orderedContext(
                                        layerIndex,
                                        featureIndex,
                                        "codePoint",
                                        Integer.toString(failure.codePoint()))));
            }
            throw failure;
        }
    }

    private static LabelPlacementException metricFailure(
            int layerIndex, int featureIndex, String quantity) {
        return new LabelPlacementException(
                new LabelPlacementProblem(
                        "LABEL_METRICS_NON_FINITE",
                        "Point-label metrics are non-finite",
                        orderedContext(layerIndex, featureIndex, "quantity", quantity)));
    }

    private static Map<String, String> orderedContext(
            int layerIndex, int featureIndex, String... entries) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("layerIndex", Integer.toString(layerIndex));
        context.put("featureIndex", Integer.toString(featureIndex));
        for (int index = 0; index < entries.length; index += 2) {
            context.put(entries[index], entries[index + 1]);
        }
        return context;
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    record Measurement(TextLayout layout, ScreenBox relativeVisualBounds, double advance) {
        Measurement {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(relativeVisualBounds, "relativeVisualBounds");
        }
    }
}
