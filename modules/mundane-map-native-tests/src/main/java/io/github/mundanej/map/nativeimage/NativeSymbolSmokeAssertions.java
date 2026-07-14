package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;

final class NativeSymbolSmokeAssertions {
    private static final int TOLERANCE = 20;
    private static final Rgba WHITE = Rgba.rgb(255, 255, 255);

    private NativeSymbolSmokeAssertions() {}

    static void verify(
            NativeSymbolSmokeScenario scenario,
            BufferedImage image,
            Coordinate[] anchors,
            MapViewport viewport) {
        Objects.requireNonNull(scenario, "scenario");
        requireClose(viewport.worldUnitsPerPixel(), 1_000.0, 1.0e-9, "viewport");
        requireAnchor(anchors[0], 64.0, 64.0);
        requireAnchor(anchors[1], 128.0, 64.0);
        requireAnchor(anchors[2], 192.0, 64.0);

        verifyVector(image, anchors[0]);
        verifyComposite(image, anchors[1]);
        verifyRaster(image, anchors[2]);
        requireBounds(image, anchors[0], 16, 16, 0, 96, "vector-bounds");
        requireBounds(image, anchors[1], 14, 14, 96, 160, "composite-order");
        requireBounds(image, anchors[2], 16, 8, 160, image.getWidth(), "raster-bounds");
    }

    private static void verifyVector(BufferedImage image, Coordinate anchor) {
        requireProbe(
                image,
                local(anchor, -0.32, -0.30),
                NativeSymbolSmokeScenario.VECTOR_GREEN,
                "vector-quadratic");
        requireProbe(image, local(anchor, -0.45, -0.45), WHITE, "vector-quadratic");
        requireProbe(
                image,
                local(anchor, 0.30, -0.31),
                NativeSymbolSmokeScenario.VECTOR_GREEN,
                "vector-cubic");
        requireProbe(image, local(anchor, 0.42, -0.45), WHITE, "vector-cubic");
    }

    private static void verifyComposite(BufferedImage image, Coordinate anchor) {
        requireProbe(image, anchor, NativeSymbolSmokeScenario.COMPOSITE_YELLOW, "composite-order");
        requireProbe(
                image,
                new Coordinate(anchor.x() + 10.0, anchor.y() + 10.0),
                NativeSymbolSmokeScenario.COMPOSITE_BLUE,
                "composite-order");
    }

    private static void verifyRaster(BufferedImage image, Coordinate anchor) {
        requireProbe(image, offset(anchor, -12, 4), Rgba.rgb(255, 255, 0), "raster-rows");
        requireProbe(image, offset(anchor, -12, -4), Rgba.rgb(255, 0, 0), "raster-resource");
        requireProbe(image, offset(anchor, 12, -4), WHITE, "raster-resource");
        requireProbe(image, offset(anchor, 4, 4), Rgba.rgb(255, 127, 255), "raster-alpha");
    }

    private static Coordinate local(Coordinate anchor, double x, double y) {
        return new Coordinate(anchor.x() + 32.0 * x, anchor.y() + 32.0 * y);
    }

    private static Coordinate offset(Coordinate anchor, double x, double y) {
        return new Coordinate(anchor.x() + x, anchor.y() + y);
    }

    private static void requireAnchor(Coordinate actual, double x, double y) {
        requireClose(actual.x(), x, 1.0e-6, "viewport");
        requireClose(actual.y(), y, 1.0e-6, "viewport");
    }

    private static void requireClose(
            double actual, double expected, double tolerance, String name) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new IllegalStateException(name + ": unexpected numeric value " + actual);
        }
    }

    private static void requireProbe(
            BufferedImage image, Coordinate center, Rgba expected, String name) {
        int matches = 0;
        int centerX = (int) Math.round(center.x());
        int centerY = (int) Math.round(center.y());
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (matches(new Color(image.getRGB(x, y), true), expected)) {
                    matches++;
                }
            }
        }
        if (matches < 5) {
            throw new IllegalStateException(name + ": expected probe majority was not rendered");
        }
    }

    private static boolean matches(Color actual, Rgba expected) {
        return Math.abs(actual.getRed() - expected.red()) <= TOLERANCE
                && Math.abs(actual.getGreen() - expected.green()) <= TOLERANCE
                && Math.abs(actual.getBlue() - expected.blue()) <= TOLERANCE
                && Math.abs(actual.getAlpha() - expected.alpha()) <= TOLERANCE;
    }

    private static void requireBounds(
            BufferedImage image,
            Coordinate anchor,
            int halfWidth,
            int halfHeight,
            int ownershipMinX,
            int ownershipMaxX,
            String name) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = ownershipMinX; x < ownershipMaxX; x++) {
                if (!matches(new Color(image.getRGB(x, y), true), WHITE)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        double allowedMinX = anchor.x() - halfWidth - 2;
        double allowedMaxX = anchor.x() + halfWidth + 2;
        double allowedMinY = anchor.y() - halfHeight - 2;
        double allowedMaxY = anchor.y() + halfHeight + 2;
        if (maxX < minX
                || minX < allowedMinX
                || maxX > allowedMaxX
                || minY < allowedMinY
                || maxY > allowedMaxY) {
            throw new IllegalStateException(name + ": painted bounds escaped the marker rectangle");
        }
    }
}
