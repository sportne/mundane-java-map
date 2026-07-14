package io.github.mundanej.map.example.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.MeasurementTool;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MeasurementViewerTest {
    @Test
    void createsTwoCrsBoundMeasurementViewsWithoutOpeningAWindow() throws Exception {
        AtomicReference<JTabbedPane> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(MeasurementViewer.createContent()));

        JTabbedPane tabs = result.get();
        List<MapView> views = descendants(tabs, MapView.class);
        assertEquals(2, tabs.getTabCount());
        assertEquals(2, views.size());
        assertEquals("EPSG:3857", views.get(0).mapCrs().canonicalIdentifier());
        assertEquals("EPSG:4326", views.get(1).mapCrs().canonicalIdentifier());
        views.forEach(
                view -> {
                    MeasurementTool tool =
                            assertInstanceOf(
                                    MeasurementTool.class, view.activeTool().orElseThrow());
                    assertEquals(view.mapCrs(), tool.distanceStrategy().coordinateCrs());
                    view.setSize(320, 200);
                    click(view, 100, 100);
                    click(view, 200, 100);
                    assertEquals(2, tool.state().vertexCount());
                    BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D graphics = image.createGraphics();
                    try {
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    if (tool.state().displayedDistance().metres() <= 0) {
                        throw new AssertionError("representative measurement must be positive");
                    }
                });
    }

    private static <T> List<T> descendants(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) {
            result.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                result.addAll(descendants(child, type));
            }
        }
        return result;
    }

    private static void click(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        1,
                        false,
                        MouseEvent.BUTTON1));
    }
}
