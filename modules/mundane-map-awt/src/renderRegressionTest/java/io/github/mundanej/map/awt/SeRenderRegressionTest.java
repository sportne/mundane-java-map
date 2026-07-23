package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.se.SeFeatureStyle;
import io.github.mundanej.map.io.se.SeReadOptions;
import io.github.mundanej.map.io.se.SeStyles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SeRenderRegressionTest {
    @Test
    @SuppressWarnings("deprecation")
    void linePolygonOutlineAndHoleHavePortableColorAndTopologyEvidence() throws Exception {
        SeFeatureStyle style =
                SeStyles.read(
                        "regression",
                        xml().getBytes(StandardCharsets.UTF_8),
                        NamedSymbolCatalog.of(List.of()),
                        SeReadOptions.defaults());
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();

        SwingUtilities.invokeAndWait(
                () -> {
                    Feature line =
                            new Feature(
                                    "line",
                                    "line",
                                    new LineStringGeometry(CoordinateSequence.of(-80, 55, 80, 55)),
                                    Map.of(),
                                    FeatureStyle.line(Rgba.rgb(0, 0, 0), 1));
                    PolygonGeometry polygon =
                            new PolygonGeometry(
                                    ring(-70, -55, 70, 25), List.of(ring(-20, -25, 20, 5)));
                    Feature area =
                            new Feature(
                                    "area",
                                    "area",
                                    polygon,
                                    Map.of(),
                                    FeatureStyle.polygon(Rgba.rgb(0, 0, 0), Rgba.rgb(0, 0, 0), 1));
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setSize(240, 180);
                    view.setViewport(new MapViewport(240, 180, 0, 0, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer("se", "SE", List.of(line, area)),
                                            style.portrayal())));
                    BufferedImage image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    viewReference.set(view);
                    imageReference.set(image);
                });

        MapView view = viewReference.get();
        BufferedImage image = imageReference.get();
        assertColorNear(image, view.mapToScreen(new Coordinate(0, 55)).orElseThrow(), true, 5);
        assertColorNear(image, view.mapToScreen(new Coordinate(-50, -20)).orElseThrow(), false, 3);
        assertDarkGreenNear(image, view.mapToScreen(new Coordinate(-70, -20)).orElseThrow(), 4);
        Coordinate hole = view.mapToScreen(new Coordinate(0, -10)).orElseThrow();
        Color holeColor = new Color(image.getRGB((int) hole.x(), (int) hole.y()), true);
        assertTrue(
                holeColor.getRed() > 245 && holeColor.getGreen() > 245 && holeColor.getBlue() > 245,
                "polygon hole must preserve the white background");
        SwingUtilities.invokeAndWait(view::close);
    }

    private static void assertColorNear(
            BufferedImage image, Coordinate center, boolean blue, int radius) {
        int matching = 0;
        for (int y = (int) center.y() - radius; y <= (int) center.y() + radius; y++) {
            for (int x = (int) center.x() - radius; x <= (int) center.x() + radius; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                boolean match =
                        blue
                                ? color.getBlue() > color.getRed() + 30
                                : color.getGreen() > color.getRed() + 30;
                if (match) {
                    matching++;
                }
            }
        }
        assertTrue(matching > 2, "expected tolerant " + (blue ? "blue" : "green") + " evidence");
    }

    private static void assertDarkGreenNear(BufferedImage image, Coordinate center, int radius) {
        int matching = 0;
        for (int y = (int) center.y() - radius; y <= (int) center.y() + radius; y++) {
            for (int x = (int) center.x() - radius; x <= (int) center.x() + radius; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getGreen() > color.getRed() + 20
                        && color.getGreen() < 110
                        && color.getBlue() < 100) {
                    matching++;
                }
            }
        }
        assertTrue(matching > 2, "polygon outline produced no tolerant dark-green evidence");
    }

    private static CoordinateSequence ring(double minX, double minY, double maxX, double maxY) {
        return CoordinateSequence.of(minX, minY, maxX, minY, maxX, maxY, minX, maxY, minX, minY);
    }

    private static String xml() {
        return """
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se">
                  <se:Rule>
                    <se:LineSymbolizer><se:Stroke>
                      <se:SvgParameter name="stroke">#2040d0</se:SvgParameter>
                      <se:SvgParameter name="stroke-width">5</se:SvgParameter>
                    </se:Stroke></se:LineSymbolizer>
                    <se:PolygonSymbolizer>
                      <se:Fill><se:SvgParameter name="fill">#20c040</se:SvgParameter></se:Fill>
                      <se:Stroke><se:SvgParameter name="stroke">#104020</se:SvgParameter>
                        <se:SvgParameter name="stroke-width">3</se:SvgParameter>
                      </se:Stroke>
                    </se:PolygonSymbolizer>
                  </se:Rule>
                </se:FeatureTypeStyle>
                """;
    }
}
