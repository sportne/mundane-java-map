package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Native Image entrypoint that exercises the real AWT offscreen rendering path. */
public final class NativeSmokeMain {
    private NativeSmokeMain() {}

    /** Runs the smoke application. */
    public static void main(String[] arguments) {
        runSmoke();
        System.out.println("mundane-map native smoke: OK");
    }

    /** Renders one feature and fails when the expected center pixel remains untouched. */
    public static void runSmoke() {
        Feature feature =
                new Feature(
                        "origin",
                        "Origin",
                        new PointGeometry(new Coordinate(0.0, 0.0)),
                        Map.of(),
                        FeatureStyle.point(Rgba.rgb(20, 90, 180), 16.0));
        MapView view = new MapView(new WebMercatorProjection());
        view.setSize(128, 128);
        view.setLayers(List.of(new InMemoryLayer("smoke", "Smoke", List.of(feature))));
        view.fitToData(16.0);

        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        if (image.getRGB(64, 64) == Color.WHITE.getRGB()) {
            throw new IllegalStateException("Map smoke render did not modify the center pixel");
        }
    }
}

