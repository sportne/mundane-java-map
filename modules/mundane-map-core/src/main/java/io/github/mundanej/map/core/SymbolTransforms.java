package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolUnit;
import java.util.Map;
import java.util.Objects;

/** Validated toolkit-neutral symbol placement calculations. */
public final class SymbolTransforms {
    private SymbolTransforms() {}

    /** Derives a marker's local-to-screen coefficients and nominal bounds. */
    public static MarkerTransform marker(
            Envelope viewBox,
            MarkerPlacement placement,
            Coordinate featureScreen,
            MapScreenBasis basis) {
        Objects.requireNonNull(viewBox, "viewBox");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(featureScreen, "featureScreen");
        Objects.requireNonNull(basis, "basis");
        if (!Double.isFinite(viewBox.width())
                || !Double.isFinite(viewBox.height())
                || viewBox.width() <= 0.0
                || viewBox.height() <= 0.0) {
            throw new IllegalArgumentException(
                    "viewBox must have finite positive width and height");
        }
        double unitScale =
                placement.size().unit() == SymbolUnit.SCREEN_PIXEL ? 1.0 : basis.uniformScale();
        double width = finite(placement.size().width() * unitScale, "marker-width");
        double height = finite(placement.size().height() * unitScale, "marker-height");
        double offsetScreenX;
        double offsetScreenY;
        if (placement.size().unit() == SymbolUnit.SCREEN_PIXEL) {
            offsetScreenX = placement.offsetX();
            offsetScreenY = placement.offsetY();
        } else {
            offsetScreenX =
                    finite(
                            finite(
                                            placement.offsetX() * basis.xUnitScreenDelta().x(),
                                            "marker-offset-x-product")
                                    + finite(
                                            placement.offsetY() * basis.yUnitScreenDelta().x(),
                                            "marker-offset-y-product"),
                            "marker-offset-screen-x");
            offsetScreenY =
                    finite(
                            finite(
                                            placement.offsetX() * basis.xUnitScreenDelta().y(),
                                            "marker-offset-x-product")
                                    + finite(
                                            placement.offsetY() * basis.yUnitScreenDelta().y(),
                                            "marker-offset-y-product"),
                            "marker-offset-screen-y");
        }
        double anchorX = finite(featureScreen.x() + offsetScreenX, "marker-anchor-x");
        double anchorY = finite(featureScreen.y() + offsetScreenY, "marker-anchor-y");
        double bearing =
                placement.rotationMode() == SymbolRotationMode.SCREEN_RELATIVE
                        ? placement.rotationDegrees()
                        : basis.xAxisScreenBearingDegrees() + placement.rotationDegrees();
        double radians = StrictMath.toRadians(bearing);
        double cosine = finite(StrictMath.cos(radians), "marker-rotation-cosine");
        double sine = finite(StrictMath.sin(radians), "marker-rotation-sine");
        double sx = finite(width / viewBox.width(), "marker-scale-x");
        double sy = finite(height / viewBox.height(), "marker-scale-y");
        double[] anchorFractions = anchorFractions(placement.anchor());
        double tx =
                finite(
                        finite(-viewBox.minX() * sx, "marker-viewbox-translation-x")
                                - finite(anchorFractions[0] * width, "marker-anchor-width"),
                        "marker-local-translation-x");
        double ty =
                finite(
                        finite(-viewBox.minY() * sy, "marker-viewbox-translation-y")
                                - finite(anchorFractions[1] * height, "marker-anchor-height"),
                        "marker-local-translation-y");
        double m00 = finite(cosine * sx, "marker-m00");
        double m01 = finite(-sine * sy, "marker-m01");
        double m10 = finite(sine * sx, "marker-m10");
        double m11 = finite(cosine * sy, "marker-m11");
        double m02 =
                finite(
                        anchorX
                                + finite(cosine * tx, "marker-rotated-translation-x")
                                - finite(sine * ty, "marker-rotated-translation-y"),
                        "marker-m02");
        double m12 =
                finite(
                        anchorY
                                + finite(sine * tx, "marker-rotated-translation-x")
                                + finite(cosine * ty, "marker-rotated-translation-y"),
                        "marker-m12");
        Envelope bounds = bounds(viewBox, m00, m10, m01, m11, m02, m12);
        return new MarkerTransform(m00, m10, m01, m11, m02, m12, bounds);
    }

    /** Converts a positive symbol length into logical screen pixels. */
    public static double screenLength(SymbolLength length, MapScreenBasis basis) {
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(basis, "basis");
        double scale = length.unit() == SymbolUnit.SCREEN_PIXEL ? 1.0 : basis.uniformScale();
        double result = finite(length.value() * scale, "symbol-screen-length");
        if (result <= 0.0) {
            throw nonFinite("symbol-screen-length");
        }
        return result;
    }

    private static Envelope bounds(
            Envelope viewBox,
            double m00,
            double m10,
            double m01,
            double m11,
            double m02,
            double m12) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double[] xValues = {viewBox.minX(), viewBox.maxX()};
        double[] yValues = {viewBox.minY(), viewBox.maxY()};
        for (double x : xValues) {
            for (double y : yValues) {
                double screenX =
                        finite(
                                finite(m00 * x, "marker-corner-x-product")
                                        + finite(m01 * y, "marker-corner-y-product")
                                        + m02,
                                "marker-corner-x");
                double screenY =
                        finite(
                                finite(m10 * x, "marker-corner-x-product")
                                        + finite(m11 * y, "marker-corner-y-product")
                                        + m12,
                                "marker-corner-y");
                minimumX = StrictMath.min(minimumX, screenX);
                minimumY = StrictMath.min(minimumY, screenY);
                maximumX = StrictMath.max(maximumX, screenX);
                maximumY = StrictMath.max(maximumY, screenY);
            }
        }
        return new Envelope(minimumX, minimumY, maximumX, maximumY);
    }

    private static double[] anchorFractions(SymbolAnchor anchor) {
        return switch (anchor) {
            case NORTH_WEST -> new double[] {0.0, 0.0};
            case NORTH -> new double[] {0.5, 0.0};
            case NORTH_EAST -> new double[] {1.0, 0.0};
            case WEST -> new double[] {0.0, 0.5};
            case CENTER -> new double[] {0.5, 0.5};
            case EAST -> new double[] {1.0, 0.5};
            case SOUTH_WEST -> new double[] {0.0, 1.0};
            case SOUTH -> new double[] {0.5, 1.0};
            case SOUTH_EAST -> new double[] {1.0, 1.0};
        };
    }

    private static double finite(double value, String quantity) {
        if (!Double.isFinite(value)) {
            throw nonFinite(quantity);
        }
        return value == 0.0 ? 0.0 : value;
    }

    private static SymbolException nonFinite(String quantity) {
        return new SymbolException(
                SymbolException.TRANSFORM_NON_FINITE,
                "Symbol transform produced a non-finite value",
                Map.of("quantity", quantity));
    }
}
