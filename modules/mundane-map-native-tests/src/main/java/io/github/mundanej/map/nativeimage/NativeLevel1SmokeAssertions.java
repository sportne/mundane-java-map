package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.DistanceStrategy;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.api.SymbolException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Semantic assertions shared by the JVM and native Level 1 aggregate scenario. */
final class NativeLevel1SmokeAssertions {
    static final double GEOGRAPHIC_METRES = 222_390.1604670658;

    private static final int OPAQUE_WHITE = 0xffffffff;
    private static final int TEAL = 0xff187060;
    private static final int PURPLE = 0xff9148c4;

    private NativeLevel1SmokeAssertions() {}

    static void verifyDuplicateRenderer(SymbolException failure) {
        require(
                SymbolException.RENDERER_DUPLICATE.equals(failure.code()),
                "registration-diagnostic",
                "duplicate renderer code changed");
        require(
                Map.of("role", "MARKER", "key", NativeLevel1SmokeScenario.RENDERER_KEY.value())
                        .equals(failure.context()),
                "registration-diagnostic",
                "duplicate renderer context changed");
        require(
                List.copyOf(failure.context().keySet()).equals(List.of("role", "key")),
                "registration-diagnostic",
                "duplicate renderer context order changed");
    }

    static void verifyPlanar(
            DistanceStrategy strategy, CrsDefinition expectedCrs, DistanceResult result) {
        require(
                strategy.coordinateCrs().equals(expectedCrs),
                "measurement-planar",
                "planar strategy CRS changed");
        require(result.metres() == 5_000.0, "measurement-planar", "planar distance changed");
    }

    static void verifyGeographic(
            DistanceStrategy strategy, CrsDefinition expectedCrs, DistanceResult result) {
        require(
                strategy.coordinateCrs().equals(expectedCrs),
                "measurement-geographic",
                "geographic strategy CRS changed");
        double tolerance = Math.max(1.0e-6, Math.abs(GEOGRAPHIC_METRES) * 1.0e-12);
        require(
                Math.abs(result.metres() - GEOGRAPHIC_METRES) <= tolerance,
                "measurement-geographic",
                "antimeridian distance changed");
    }

    static void verifyPreview(MeasurementState state) {
        require(
                state.phase() == MeasurementPhase.MEASURING,
                "measurement-preview",
                "preview phase changed");
        require(state.vertexCount() == 1, "measurement-preview", "preview vertex count changed");
        require(
                state.vertex(0).equals(new Coordinate(0.0, 0.0)),
                "measurement-preview",
                "preview first vertex changed");
        require(
                state.preview().equals(java.util.Optional.of(new Coordinate(3_000.0, 4_000.0))),
                "measurement-preview",
                "preview endpoint changed");
        require(
                state.displayedDistance().metres() == 5_000.0,
                "measurement-preview",
                "preview distance changed");
    }

    static void verifyComplete(MeasurementState state) {
        require(
                state.phase() == MeasurementPhase.COMPLETE,
                "measurement-state",
                "completion phase changed");
        require(state.vertexCount() == 3, "measurement-state", "completed vertex count changed");
        require(
                state.vertex(0).equals(new Coordinate(0.0, 0.0))
                        && state.vertex(1).equals(new Coordinate(3_000.0, 4_000.0))
                        && state.vertex(2).equals(new Coordinate(6_000.0, 0.0)),
                "measurement-state",
                "completed vertices changed");
        require(state.preview().isEmpty(), "measurement-state", "completed preview remained");
        require(
                state.committedDistance().metres() == 10_000.0
                        && state.displayedDistance().metres() == 10_000.0,
                "measurement-state",
                "completed distance changed");
    }

    static RenderSummary verifyRender(int[] first, int[] second, int width, int height) {
        require(
                first.length == width * height && second.length == width * height,
                "final-render-repeat",
                "render dimensions changed");
        require(
                Arrays.equals(first, second),
                "final-render-repeat",
                "unchanged repeated paints differed");
        verifyLineProbes(first, width, height);
        verifyMarkerProbe(first, width, height);
        verifyMeasurementProbe(first, width);
        verifyMeasurementLabelProbe(first, width, height);
        verifyBlankProbe(first, width, height);
        return verifyNonWhiteBounds(first, width, height);
    }

    static void verifyLineProbes(int[] pixels, int width, int height) {
        verifyMajority(pixels, width, height, 1, 130, TEAL, 20, "final-render-line-clip");
        verifyMajority(pixels, width, height, 46, 130, TEAL, 20, "final-render-line");
    }

    static void verifyMarkerProbe(int[] pixels, int width, int height) {
        verifyMajority(pixels, width, height, 30, 65, PURPLE, 20, "final-render-marker");
    }

    static void verifyMeasurementProbe(int[] pixels, int width) {
        verifyCrimson(pixels, width, 111, 60);
    }

    static void verifyMeasurementLabelProbe(int[] pixels, int width, int height) {
        int blackPixels = 0;
        int maximumX = Math.min(width, 116);
        int maximumY = Math.min(height, 30);
        for (int y = 8; y < maximumY; y++) {
            for (int x = 8; x < maximumX; x++) {
                if (pixels[y * width + x] == 0xff000000) {
                    blackPixels++;
                }
            }
        }
        require(blackPixels >= 50, "measurement-label-render", "measurement label changed");
    }

    static void verifyBlankProbe(int[] pixels, int width, int height) {
        verifyWhite(pixels, width, height, 184, 148, 2);
    }

    static RenderSummary verifyNonWhiteBounds(int[] pixels, int width, int height) {
        int count = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (pixels[y * width + x] != OPAQUE_WHITE) {
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        require(count >= 200 && count <= 10_000, "final-render-repeat", "paint count changed");
        require(
                minX >= 0 && minY >= 0 && maxX <= 191 && maxY <= 136,
                "final-render-repeat",
                "paint bounds changed: " + minX + ',' + minY + ',' + maxX + ',' + maxY);
        return new RenderSummary(count, minX, minY, maxX, maxY);
    }

    private static void verifyMajority(
            int[] pixels,
            int width,
            int height,
            int centerX,
            int centerY,
            int target,
            int tolerance,
            String invariant) {
        int matches = 0;
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (x >= 0
                        && x < width
                        && y >= 0
                        && y < height
                        && within(pixels[y * width + x], target, tolerance)) {
                    matches++;
                }
            }
        }
        require(matches >= 5, invariant, "color probe changed");
    }

    private static void verifyCrimson(int[] pixels, int width, int centerX, int centerY) {
        int matches = 0;
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                int pixel = pixels[y * width + x];
                int alpha = pixel >>> 24;
                int red = pixel >>> 16 & 0xff;
                int green = pixel >>> 8 & 0xff;
                int blue = pixel & 0xff;
                if (alpha == 255 && red >= 128 && red - green >= 40 && red - blue >= 40) {
                    matches++;
                }
            }
        }
        require(matches >= 5, "measurement-render", "measurement segment probe changed");
    }

    private static void verifyWhite(
            int[] pixels, int width, int height, int centerX, int centerY, int radius) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                require(
                        x >= 0
                                && x < width
                                && y >= 0
                                && y < height
                                && pixels[y * width + x] == OPAQUE_WHITE,
                        "final-render-repeat",
                        "blank probe changed");
            }
        }
    }

    private static boolean within(int actual, int expected, int tolerance) {
        return Math.abs((actual >>> 24) - (expected >>> 24)) <= tolerance
                && Math.abs((actual >>> 16 & 0xff) - (expected >>> 16 & 0xff)) <= tolerance
                && Math.abs((actual >>> 8 & 0xff) - (expected >>> 8 & 0xff)) <= tolerance
                && Math.abs((actual & 0xff) - (expected & 0xff)) <= tolerance;
    }

    static void require(boolean condition, String invariant, String detail) {
        if (!condition) {
            throw new IllegalStateException(invariant + ": " + detail);
        }
    }

    record RenderSummary(int nonWhitePixels, int minX, int minY, int maxX, int maxY) {}
}
