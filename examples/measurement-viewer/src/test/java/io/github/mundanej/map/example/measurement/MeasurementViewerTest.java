package io.github.mundanej.map.example.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapToolCancelReason;
import io.github.mundanej.map.api.MapToolCommand;
import io.github.mundanej.map.api.MapToolCommandEvent;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.MeasurementTool;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        assertEquals("Planar metres", tabs.getTitleAt(0));
        assertEquals("EPSG:4326 great circle", tabs.getTitleAt(1));
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
                    move(view, 150, 80);
                    assertEquals(MeasurementPhase.MEASURING, tool.state().phase());
                    assertTrue(tool.state().preview().isPresent());
                    assertTrue(tool.state().displayedDistance().metres() > 0);
                    click(view, 200, 100);
                    assertEquals(2, tool.state().vertexCount());
                    double committed = tool.state().committedDistance().metres();
                    double scale = view.viewport().worldUnitsPerPixel();
                    view.dispatchEvent(
                            new MouseWheelEvent(
                                    view,
                                    MouseEvent.MOUSE_WHEEL,
                                    3L,
                                    0,
                                    160,
                                    100,
                                    0,
                                    false,
                                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                    1,
                                    -1));
                    assertNotEquals(scale, view.viewport().worldUnitsPerPixel());
                    assertEquals(committed, tool.state().committedDistance().metres());
                    BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D graphics = image.createGraphics();
                    try {
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    assertTrue(countCrimson(image) > 5);
                    if (tool.state().displayedDistance().metres() <= 0) {
                        throw new AssertionError("representative measurement must be positive");
                    }
                    tool.onMapToolCommand(
                            new MapToolCommandEvent(1, MapToolCommand.DELETE_BACKWARD),
                            context(view));
                    assertEquals(1, tool.state().vertexCount());
                    tool.onMapToolEvent(
                            new MapToolEvent(
                                    2,
                                    MapToolEvent.Type.CANCEL,
                                    0,
                                    0,
                                    Optional.empty(),
                                    MapPointerButton.NONE,
                                    Set.of(),
                                    Set.of(),
                                    0,
                                    0,
                                    false,
                                    Optional.of(MapToolCancelReason.USER_CANCEL)),
                            context(view));
                    assertEquals(MeasurementPhase.EMPTY, tool.state().phase());
                    double centerX = view.viewport().centerX();
                    mouse(view, MouseEvent.MOUSE_PRESSED, 140, 100, MouseEvent.BUTTON1, 0, 1);
                    mouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            150,
                            100,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK,
                            0);
                    mouse(view, MouseEvent.MOUSE_RELEASED, 150, 100, MouseEvent.BUTTON1, 0, 1);
                    assertNotEquals(centerX, view.viewport().centerX());
                    view.close();
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
        mouse(view, MouseEvent.MOUSE_CLICKED, x, y, MouseEvent.BUTTON1, 0, 1);
    }

    private static void move(MapView view, int x, int y) {
        mouse(view, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON, 0, 0);
    }

    private static void mouse(
            MapView view, int id, int x, int y, int button, int modifiers, int clickCount) {
        view.dispatchEvent(
                new MouseEvent(view, id, 1L, modifiers, x, y, clickCount, false, button));
    }

    private static int countCrimson(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int red = (pixel >>> 16) & 0xff;
                int green = (pixel >>> 8) & 0xff;
                int blue = pixel & 0xff;
                if (red >= 128 && red - green >= 40 && red - blue >= 40) {
                    count++;
                }
            }
        }
        return count;
    }

    private static MapToolContext context(MapView view) {
        return new MapToolContext() {
            @Override
            public io.github.mundanej.map.api.CrsDefinition mapCrs() {
                return view.mapCrs();
            }

            @Override
            public io.github.mundanej.map.api.CrsDefinition displayCrs() {
                return view.displayCrs();
            }

            @Override
            public Optional<io.github.mundanej.map.api.Coordinate> mapToScreen(
                    io.github.mundanej.map.api.Coordinate coordinate) {
                return Optional.empty();
            }

            @Override
            public Optional<io.github.mundanej.map.api.Coordinate> screenToMap(
                    double screenX, double screenY) {
                return Optional.empty();
            }

            @Override
            public void requestRepaint() {}
        };
    }
}
