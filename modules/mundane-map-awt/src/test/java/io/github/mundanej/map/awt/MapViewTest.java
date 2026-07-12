package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewTest {
    @Test
    void rendersPointLinePolygonAndEmitsPointerCoordinates() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        MapView view = new MapView(new WebMercatorProjection());
                        view.setSize(640, 480);
                        view.setLayers(List.of(sampleLayer()));
                        view.fitToData(24.0);

                        BufferedImage image =
                                new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }

                        assertNotEquals(Color.WHITE.getRGB(), image.getRGB(320, 240));
                        Coordinate center = view.screenToMap(320.0, 240.0);
                        assertEquals(-71.0, center.x(), 0.2);
                        assertEquals(42.3, center.y(), 0.2);
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static InMemoryLayer sampleLayer() {
        Feature point =
                new Feature(
                        "point",
                        "Center",
                        new PointGeometry(new Coordinate(-71.0, 42.3)),
                        Map.of(),
                        FeatureStyle.point(Rgba.rgb(20, 100, 200), 12.0));
        Feature line =
                new Feature(
                        "line",
                        "",
                        new LineStringGeometry(
                                CoordinateSequence.of(-71.2, 42.2, -71.0, 42.3, -70.8, 42.4)),
                        Map.of(),
                        FeatureStyle.line(Rgba.rgb(180, 40, 40), 3.0));
        Feature polygon =
                new Feature(
                        "polygon",
                        "",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        -71.1,
                                        42.2,
                                        -70.9,
                                        42.2,
                                        -70.9,
                                        42.4,
                                        -71.1,
                                        42.4,
                                        -71.1,
                                        42.2)),
                        Map.of(),
                        FeatureStyle.polygon(
                                Rgba.rgb(20, 100, 20), new Rgba(30, 180, 60, 80), 2.0));
        return new InMemoryLayer("sample", "Sample", List.of(polygon, line, point));
    }
}

