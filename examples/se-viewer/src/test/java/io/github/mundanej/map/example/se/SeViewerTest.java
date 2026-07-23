package io.github.mundanej.map.example.se;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SeViewerTest {
    @Test
    void bundledGalleryConstructsOnEdtAndRendersEveryDemonstratedRole() throws Exception {
        assertEquals("review-gallery", SeViewer.readBundledStyle().name().orElseThrow());
        AtomicReference<MapView> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(SeViewer.createMapView()));
        MapView view = result.get();

        SwingUtilities.invokeAndWait(
                () -> {
                    view.setSize(240, 220);
                    view.setViewport(new MapViewport(240, 220, 0, 0, 1));
                    BufferedImage image = new BufferedImage(240, 220, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    assertTrue(nonWhite(image) > 1_000);
                    assertEquals(
                            "ordered-point",
                            view.hitTest(50, 40, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "catalog-point",
                            view.hitTest(120, 40, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "scale-point",
                            view.hitTest(190, 40, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "ordered-line",
                            view.hitTest(120, 95, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "ordered-area",
                            view.hitTest(60, 150, 0).topmost().orElseThrow().featureId());
                    assertTrue(view.hitTest(120, 165, 0).topmost().isEmpty());
                    view.setViewport(new MapViewport(240, 220, 0, 0, 2));
                    assertTrue(view.hitTest(155, 75, 0).topmost().isEmpty());
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
