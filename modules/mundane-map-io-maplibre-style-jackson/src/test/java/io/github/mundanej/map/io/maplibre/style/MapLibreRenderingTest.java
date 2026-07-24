package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapLibreRenderingTest {
    @Test
    @SuppressWarnings("deprecation")
    void literalLayersRenderWithTolerantColorAndOpacityEvidence() throws Exception {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"point","type":"circle","source":"memory",
                           "paint":{"circle-radius":6,"circle-color":"#0000ff80",
                                    "circle-stroke-width":3,"circle-stroke-color":"#ff0000"}},
                          {"id":"line","type":"line","source":"memory",
                           "layout":{"line-cap":"round","line-join":"round"},
                           "paint":{"line-width":5,"line-color":"#2040d0","line-opacity":0.5}},
                          {"id":"fill","type":"fill","source":"memory",
                           "paint":{"fill-color":"#40c060","fill-outline-color":"#102010",
                                    "fill-opacity":0.5}}
                        ]}
                        """);
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view(200, 200);
                    view.setLayerBindings(
                            List.of(
                                    binding(
                                            "point",
                                            point("point", -50, 50),
                                            style.layers().get(0)),
                                    binding(
                                            "line",
                                            line("line", -80, 0, 80, 0),
                                            style.layers().get(1)),
                                    binding(
                                            "fill",
                                            polygon("fill", 20, -70, 80, -20),
                                            style.layers().get(2))));
                    BufferedImage image = paint(view, 200, 200);
                    assertEquals(
                            "point", view.hitTest(50, 50, 1).topmost().orElseThrow().featureId());
                    assertEquals(
                            "line", view.hitTest(100, 100, 1).topmost().orElseThrow().featureId());
                    assertEquals(
                            "fill", view.hitTest(150, 145, 0).topmost().orElseThrow().featureId());
                    assertColor(image, 50, 50, 127, 127, 255, 3);
                    assertColor(image, 57, 50, 255, 0, 0, 3);
                    assertColor(image, 100, 100, 143, 159, 231, 6);
                    assertColor(image, 150, 145, 159, 223, 175, 6);
                    view.close();
                });
    }

    @Test
    @SuppressWarnings("deprecation")
    void laterStyleLayerPaintsAboveEarlierOverlappingGeometry() throws Exception {
        MapLibreStyle style =
                read(
                        """
                        {"version":8,"sources":{},"layers":[
                          {"id":"fill","type":"fill","source":"memory",
                           "paint":{"fill-color":"#00ff00","fill-outline-color":"#00ff00"}},
                          {"id":"line","type":"line","source":"memory",
                           "layout":{"line-cap":"round","line-join":"round"},
                           "paint":{"line-width":12,"line-color":"#0000ff"}},
                          {"id":"point","type":"circle","source":"memory",
                           "paint":{"circle-radius":10,"circle-color":"#ff0000"}}
                        ]}
                        """);
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view(100, 100);
                    view.setLayerBindings(
                            List.of(
                                    binding(
                                            "fill",
                                            polygon("fill", -30, -30, 30, 30),
                                            style.layers().get(0)),
                                    binding(
                                            "line",
                                            line("line", -30, 0, 30, 0),
                                            style.layers().get(1)),
                                    binding("point", point("point", 0, 0), style.layers().get(2))));
                    BufferedImage image = paint(view, 100, 100);
                    assertColor(image, 50, 50, 255, 0, 0, 2);
                    assertEquals(
                            "point", view.hitTest(50, 50, 0).topmost().orElseThrow().featureId());
                    view.close();
                });
    }

    private static MapLibreStyle read(String json) {
        return MapLibreStyles.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static MapView view(int width, int height) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(width, height);
        view.setBackground(Color.WHITE);
        view.setViewport(new MapViewport(width, height, 0, 0, 1));
        return view;
    }

    private static BufferedImage paint(MapView view, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static MapLayerBinding binding(String id, Feature feature, MapLibreLayer layer) {
        return MapLayerBinding.portrayedSnapshot(
                new InMemoryLayer(id, id, List.of(feature)), layer.portrayal().orElseThrow());
    }

    @SuppressWarnings("deprecation")
    private static Feature point(String id, double x, double y) {
        return new Feature(
                id,
                id,
                new PointGeometry(new Coordinate(x, y)),
                Map.of(),
                FeatureStyle.point(Rgba.rgb(0, 0, 0), 1));
    }

    @SuppressWarnings("deprecation")
    private static Feature line(String id, double x1, double y1, double x2, double y2) {
        return new Feature(
                id,
                id,
                new LineStringGeometry(CoordinateSequence.of(x1, y1, x2, y2)),
                Map.of(),
                FeatureStyle.line(Rgba.rgb(0, 0, 0), 1));
    }

    @SuppressWarnings("deprecation")
    private static Feature polygon(
            String id, double minimumX, double minimumY, double maximumX, double maximumY) {
        return new Feature(
                id,
                id,
                new PolygonGeometry(
                        CoordinateSequence.of(
                                minimumX, minimumY, maximumX, minimumY, maximumX, maximumY,
                                minimumX, maximumY, minimumX, minimumY),
                        List.of()),
                Map.of(),
                FeatureStyle.polygon(Rgba.rgb(0, 0, 0), Rgba.rgb(0, 0, 0), 1));
    }

    private static void assertColor(
            BufferedImage image, int x, int y, int red, int green, int blue, int tolerance) {
        Color actual = new Color(image.getRGB(x, y), true);
        assertTrue(StrictMath.abs(actual.getRed() - red) <= tolerance, actual::toString);
        assertTrue(StrictMath.abs(actual.getGreen() - green) <= tolerance, actual::toString);
        assertTrue(StrictMath.abs(actual.getBlue() - blue) <= tolerance, actual::toString);
    }
}
