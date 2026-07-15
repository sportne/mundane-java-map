package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapView;
import java.awt.Color;
import java.awt.image.BufferedImage;

/** Tolerant semantic render oracle for the fixed native Shapefile fixture. */
final class NativeShapefileSmokeAssertions {
    private static final int TOLERANCE = 20;
    private static final Rgba WHITE = Rgba.rgb(255, 255, 255);

    private NativeShapefileSmokeAssertions() {}

    static int verify(BufferedImage image, MapView view, Envelope sourceEnvelope) {
        return verify(image, (x, y) -> screen(view, x, y), sourceEnvelope);
    }

    static int verify(BufferedImage image, ScreenTransform transform, Envelope sourceEnvelope) {
        requireProbeMajority(
                image, transform.screen(5, 5), NativeShapefileSmokeScenario.FILL, "shell");
        requireProbeMajority(
                image, transform.screen(55, 5), NativeShapefileSmokeScenario.FILL, "part-1");
        requireProbeMajority(
                image, transform.screen(75, 25), NativeShapefileSmokeScenario.FILL, "part-2");
        requireProbeMajority(image, transform.screen(15, 15), WHITE, "hole");
        requireProbeMajority(image, transform.screen(45, 35), WHITE, "outside");
        requireOutline(image, transform.screen(0, 20));

        Coordinate first = transform.screen(sourceEnvelope.minX(), sourceEnvelope.maxY());
        Coordinate second = transform.screen(sourceEnvelope.maxX(), sourceEnvelope.minY());
        int allowedMinX = (int) Math.floor(Math.min(first.x(), second.x()) - 3.0);
        int allowedMaxX = (int) Math.ceil(Math.max(first.x(), second.x()) + 3.0);
        int allowedMinY = (int) Math.floor(Math.min(first.y(), second.y()) - 3.0);
        int allowedMaxY = (int) Math.ceil(Math.max(first.y(), second.y()) + 3.0);
        int nonWhite = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!matches(new Color(image.getRGB(x, y), true), WHITE)) {
                    nonWhite++;
                    if (x < allowedMinX || x > allowedMaxX || y < allowedMinY || y > allowedMaxY) {
                        throw new IllegalStateException(
                                "shapefile-render: paint escaped transformed source envelope");
                    }
                }
            }
        }
        if (nonWhite < 100 || nonWhite > 20_000) {
            throw new IllegalStateException(
                    "shapefile-render: unexpected painted area " + nonWhite);
        }
        return nonWhite;
    }

    private static Coordinate screen(MapView view, double x, double y) {
        return view.mapToScreen(new Coordinate(x, y))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "shapefile-render: map coordinate is not representable"));
    }

    private static void requireProbeMajority(
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
            throw new IllegalStateException("shapefile-render: " + name + " probe failed");
        }
    }

    private static void requireOutline(BufferedImage image, Coordinate center) {
        int centerX = (int) Math.round(center.x());
        int centerY = (int) Math.round(center.y());
        for (int y = centerY - 2; y <= centerY + 2; y++) {
            for (int x = centerX - 2; x <= centerX + 2; x++) {
                if (matches(
                        new Color(image.getRGB(x, y), true),
                        NativeShapefileSmokeScenario.OUTLINE)) {
                    return;
                }
            }
        }
        throw new IllegalStateException("shapefile-render: outline probe failed");
    }

    private static boolean matches(Color actual, Rgba expected) {
        return Math.abs(actual.getRed() - expected.red()) <= TOLERANCE
                && Math.abs(actual.getGreen() - expected.green()) <= TOLERANCE
                && Math.abs(actual.getBlue() - expected.blue()) <= TOLERANCE
                && Math.abs(actual.getAlpha() - expected.alpha()) <= TOLERANCE;
    }

    @FunctionalInterface
    interface ScreenTransform {
        Coordinate screen(double x, double y);
    }
}
