package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Rgba;
import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class NativeShapefileSmokeAssertionsTest {
    private static final int WHITE = Color.WHITE.getRGB();
    private static final int FILL = color(NativeShapefileSmokeScenario.FILL);
    private static final int OUTLINE = color(NativeShapefileSmokeScenario.OUTLINE);
    private static final Envelope EXTENT = NativeShapefileSmokeScenario.EXTENT;
    private static final NativeShapefileSmokeAssertions.ScreenTransform TRANSFORM =
            (x, y) -> new Coordinate(x + 10, 50 - y);

    @Test
    void fixedSemanticImagePassesTheOracle() {
        assertDoesNotThrow(() -> verify(image()));
    }

    @Test
    void oracleRejectsMissingShellCoverage() {
        BufferedImage image = image();
        paintProbe(image, 5, 5, WHITE);
        assertFailure(image, "shell probe failed");
    }

    @Test
    void oracleRejectsWrongFillColor() {
        BufferedImage image = image();
        paintProbe(image, 5, 5, Color.BLUE.getRGB());
        assertFailure(image, "shell probe failed");
    }

    @Test
    void oracleRejectsAFilledHole() {
        BufferedImage image = image();
        paintProbe(image, 15, 15, FILL);
        assertFailure(image, "hole probe failed");
    }

    @Test
    void oracleRejectsMissingMultipartCoverage() {
        BufferedImage image = image();
        paintProbe(image, 55, 5, WHITE);
        assertFailure(image, "part-1 probe failed");
    }

    @Test
    void oracleRejectsMissingOutline() {
        BufferedImage image = image();
        Coordinate center = TRANSFORM.screen(0, 20);
        fill(image, (int) center.x() - 2, (int) center.y() - 2, 5, 5, FILL);
        assertFailure(image, "outline probe failed");
    }

    private static BufferedImage image() {
        BufferedImage image = new BufferedImage(101, 61, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, image.getWidth(), image.getHeight(), WHITE);
        fillMapRectangle(image, 0, 0, 40, 40, FILL);
        fillMapRectangle(image, 10, 10, 20, 20, WHITE);
        fillMapRectangle(image, 50, 0, 60, 10, FILL);
        fillMapRectangle(image, 70, 20, 80, 30, FILL);
        Coordinate outline = TRANSFORM.screen(0, 20);
        fill(image, (int) outline.x() - 1, (int) outline.y() - 2, 3, 5, OUTLINE);
        return image;
    }

    private static void paintProbe(BufferedImage image, double x, double y, int color) {
        Coordinate center = TRANSFORM.screen(x, y);
        fill(image, (int) center.x() - 1, (int) center.y() - 1, 3, 3, color);
    }

    private static void fillMapRectangle(
            BufferedImage image, int minX, int minY, int maxX, int maxY, int color) {
        Coordinate first = TRANSFORM.screen(minX, maxY);
        Coordinate second = TRANSFORM.screen(maxX, minY);
        fill(
                image,
                (int) first.x(),
                (int) first.y(),
                (int) (second.x() - first.x() + 1),
                (int) (second.y() - first.y() + 1),
                color);
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int color) {
        for (int targetY = y; targetY < y + height; targetY++) {
            for (int targetX = x; targetX < x + width; targetX++) {
                image.setRGB(targetX, targetY, color);
            }
        }
    }

    private static void verify(BufferedImage image) {
        NativeShapefileSmokeAssertions.verify(image, TRANSFORM, EXTENT);
    }

    private static void assertFailure(BufferedImage image, String message) {
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> verify(image));
        assertTrue(failure.getMessage().endsWith(message));
    }

    private static int color(Rgba rgba) {
        return new Color(rgba.red(), rgba.green(), rgba.blue(), rgba.alpha()).getRGB();
    }
}
