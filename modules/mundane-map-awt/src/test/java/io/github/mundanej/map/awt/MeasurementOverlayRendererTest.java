package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class MeasurementOverlayRendererTest {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 32;

    @Test
    void fixedGlyphBadgePaintsEveryFormattedCharacterDeterministically() {
        int[] first = paintBadge("0123456789. km");
        int[] second = paintBadge("0123456789. km");

        assertArrayEquals(first, second);
        int blackPixels = 0;
        int minX = WIDTH;
        int minY = HEIGHT;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (first[y * WIDTH + x] == Color.BLACK.getRGB()) {
                    blackPixels++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(blackPixels >= 500, "black pixels: " + blackPixels);
        assertTrue(minX >= 4 && minY >= 4, "minimum: " + minX + ',' + minY);
        assertTrue(maxX < WIDTH - 4 && maxY <= 17, "maximum: " + maxX + ',' + maxY);
    }

    @Test
    void fixedGlyphBadgeRejectsCharactersOutsideTheDistanceFormat() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            MeasurementOverlayRenderer.drawBadge(
                                    graphics, "12 mi", 0, 0, WIDTH, HEIGHT));
        } finally {
            graphics.dispose();
        }
    }

    private static int[] paintBadge(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            MeasurementOverlayRenderer.drawBadge(graphics, text, 0, 0, WIDTH, HEIGHT);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
    }
}
