package io.github.mundanej.map.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.awt.MapView;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class BasicViewerTest {
    @Test
    void createsTheDocumentedMapWithoutOpeningAWindow() throws Exception {
        AtomicReference<MapView> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(BasicViewer.createMapView()));
        MapView map = result.get();

        assertEquals(1, map.layers().size());
        assertEquals(5, map.layers().getFirst().features().size());
        assertTrue(
                map.layers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof PointGeometry));
        assertTrue(
                map.layers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof LineStringGeometry));
        assertTrue(
                map.layers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof PolygonGeometry));
        assertTrue(
                map.layers().getFirst().features().stream()
                        .noneMatch(
                                feature ->
                                        feature.symbol()
                                                instanceof
                                                io.github.mundanej.map.api.FeatureStyle));

        List<io.github.mundanej.map.api.MapPointerEvent> pointers = new ArrayList<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    map.setSize(320, 200);
                    map.fitToData(24.0);
                    assertEquals(320, map.viewport().width());
                    assertEquals(200, map.viewport().height());
                    map.addMapPointerListener(pointers::add);
                    map.dispatchEvent(
                            new MouseEvent(map, MouseEvent.MOUSE_MOVED, 1L, 0, 160, 100, 0, false));
                    double scale = map.viewport().worldUnitsPerPixel();
                    map.dispatchEvent(
                            new MouseWheelEvent(
                                    map,
                                    MouseEvent.MOUSE_WHEEL,
                                    2L,
                                    0,
                                    160,
                                    100,
                                    0,
                                    false,
                                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                    1,
                                    -1));
                    assertNotEquals(scale, map.viewport().worldUnitsPerPixel());

                    BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        map.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    assertTrue(countNonWhite(image) > 100);
                    map.close();
                });
        assertEquals(1, pointers.size());
        assertEquals(
                io.github.mundanej.map.api.MapPointerEvent.Type.MOVED, pointers.getFirst().type());
        assertTrue(pointers.getFirst().mapCoordinate().isPresent());
    }

    private static int countNonWhite(BufferedImage image) {
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
