package io.github.mundanej.map.example.styling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StylingLabelViewerTest {
    @Test
    void buildsPaintsAndRetainsGeometryOnlyHitFootprints() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        MapView view = StylingLabelViewer.createView();
                        BufferedImage image =
                                new BufferedImage(960, 640, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }

                        assertEquals(
                                "normal",
                                view.hitTest(180, 170, 2).topmost().orElseThrow().featureId());
                        assertEquals(
                                "suppressed",
                                view.hitTest(460, 410, 2).topmost().orElseThrow().featureId());
                        assertTrue(nonBackgroundPixels(image) > 500);
                        assertFalse(view.hitTest(220, 140, 0).topmost().isPresent());
                        view.close();
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static int nonBackgroundPixels(BufferedImage image) {
        int background = new java.awt.Color(247, 249, 252).getRGB();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }
}
