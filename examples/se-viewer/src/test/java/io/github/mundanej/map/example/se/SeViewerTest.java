package io.github.mundanej.map.example.se;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SeViewerTest {
    @Test
    void bundledStyleRendersThroughTheRealMapView() throws Exception {
        assertEquals("review-point", SeViewer.readBundledStyle().name().orElseThrow());
        AtomicReference<MapView> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(SeViewer.createMapView()));
        MapView view = result.get();

        SwingUtilities.invokeAndWait(
                () -> {
                    view.setSize(160, 120);
                    view.fitToData(24);
                    BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    assertTrue(nonWhite(image) > 300);
                    view.close();
                });
    }

    private static int nonWhite(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }
}
