package io.github.mundanej.map.io.se;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SeRenderingTest {
    @Test
    @SuppressWarnings("deprecation")
    void mixedLinePolygonHoleAndMultipartPortrayalPaintsAndHitsCompatibleRoles() throws Exception {
        SeFeatureStyle style =
                SeStyles.read(
                        "mixed",
                        mixedStyle().getBytes(StandardCharsets.UTF_8),
                        NamedSymbolCatalog.of(List.of()),
                        SeReadOptions.defaults());

        SwingUtilities.invokeAndWait(
                () -> {
                    Feature line =
                            new Feature(
                                    "line",
                                    "line",
                                    new LineStringGeometry(CoordinateSequence.of(-90, 70, -20, 70)),
                                    Map.of(),
                                    FeatureStyle.line(Rgba.rgb(0, 0, 0), 1));
                    Feature multiline =
                            new Feature(
                                    "multiline",
                                    "multiline",
                                    MultiLineStringGeometry.ofParts(
                                            List.of(
                                                    CoordinateSequence.of(20, 70, 90, 70),
                                                    CoordinateSequence.of(20, 50, 90, 50))),
                                    Map.of(),
                                    FeatureStyle.line(Rgba.rgb(0, 0, 0), 1));
                    PolygonGeometry holed =
                            new PolygonGeometry(
                                    ring(-90, -30, -20, 30), List.of(ring(-70, -10, -40, 10)));
                    Feature polygon =
                            new Feature(
                                    "polygon",
                                    "polygon",
                                    holed,
                                    Map.of(),
                                    FeatureStyle.polygon(Rgba.rgb(0, 0, 0), Rgba.rgb(0, 0, 0), 1));
                    Feature multipolygon =
                            new Feature(
                                    "multipolygon",
                                    "multipolygon",
                                    MultiPolygonGeometry.ofPolygons(
                                            List.of(
                                                    new PolygonGeometry(ring(20, -30, 45, 0)),
                                                    new PolygonGeometry(ring(60, -30, 90, 0)))),
                                    Map.of(),
                                    FeatureStyle.polygon(Rgba.rgb(0, 0, 0), Rgba.rgb(0, 0, 0), 1));
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setSize(200, 200);
                    view.setViewport(new MapViewport(200, 200, 0, 20, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "mixed",
                                                    "mixed",
                                                    List.of(
                                                            line,
                                                            multiline,
                                                            polygon,
                                                            multipolygon)),
                                            style.portrayal())));

                    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals(
                            "line", view.hitTest(45, 50, 1).topmost().orElseThrow().featureId());
                    assertEquals(
                            "multiline",
                            view.hitTest(145, 70, 1).topmost().orElseThrow().featureId());
                    assertEquals(
                            "polygon",
                            view.hitTest(15, 100, 0).topmost().orElseThrow().featureId());
                    assertTrue(view.hitTest(45, 120, 0).topmost().isEmpty());
                    assertEquals(
                            "multipolygon",
                            view.hitTest(130, 135, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "multipolygon",
                            view.hitTest(175, 135, 0).topmost().orElseThrow().featureId());
                    view.close();
                });
    }

    private static CoordinateSequence ring(double minX, double minY, double maxX, double maxY) {
        return CoordinateSequence.of(minX, minY, maxX, minY, maxX, maxY, minX, maxY, minX, minY);
    }

    private static String mixedStyle() {
        return """
                <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se">
                  <se:Rule>
                    <se:LineSymbolizer><se:Stroke>
                      <se:SvgParameter name="stroke">#204080</se:SvgParameter>
                      <se:SvgParameter name="stroke-width">4</se:SvgParameter>
                    </se:Stroke></se:LineSymbolizer>
                    <se:PolygonSymbolizer>
                      <se:Fill><se:SvgParameter name="fill">#80c060</se:SvgParameter></se:Fill>
                      <se:Stroke><se:SvgParameter name="stroke">#183010</se:SvgParameter>
                        <se:SvgParameter name="stroke-width">2</se:SvgParameter>
                      </se:Stroke>
                    </se:PolygonSymbolizer>
                  </se:Rule>
                </se:FeatureTypeStyle>
                """;
    }
}
