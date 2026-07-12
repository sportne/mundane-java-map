package io.github.mundanej.map.example;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable demonstration of the initial map vertical slice. */
public final class BasicViewer {
    private BasicViewer() {}

    /** Launches the viewer on the Swing event-dispatch thread. */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(BasicViewer::showWindow);
    }

    /** Creates the configured map view without opening a window. */
    public static MapView createMapView() {
        MapView map = new MapView(new WebMercatorProjection());
        map.setLayers(List.of(sampleLayer()));
        return map;
    }

    private static void showWindow() {
        JFrame frame = new JFrame("mundane-java-map — basic viewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        MapView map = createMapView();
        JLabel coordinates = new JLabel("Move the pointer over the map");
        map.addMapPointerListener(
                event ->
                        coordinates.setText(
                                String.format(
                                        Locale.ROOT,
                                        "longitude %.5f, latitude %.5f",
                                        event.mapCoordinate().x(),
                                        event.mapCoordinate().y())));

        frame.add(map, BorderLayout.CENTER);
        frame.add(coordinates, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationByPlatform(true);
        map.fitToData(48.0);
        frame.setVisible(true);
    }

    private static InMemoryLayer sampleLayer() {
        Feature boston = city("boston", "Boston", -71.0589, 42.3601);
        Feature providence = city("providence", "Providence", -71.4128, 41.8240);
        Feature worcester = city("worcester", "Worcester", -71.8023, 42.2626);

        Feature route =
                new Feature(
                        "route",
                        "",
                        new LineStringGeometry(
                                CoordinateSequence.of(
                                        -71.4128, 41.8240, -71.0589, 42.3601, -71.8023, 42.2626)),
                        Map.of("kind", "route"),
                        FeatureStyle.line(Rgba.rgb(190, 45, 45), 3.0));

        Feature region =
                new Feature(
                        "region",
                        "",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        -72.05, 41.65, -70.75, 41.65, -70.75, 42.55, -72.05, 42.55,
                                        -72.05, 41.65)),
                        Map.of("kind", "region"),
                        FeatureStyle.polygon(
                                Rgba.rgb(45, 110, 65), new Rgba(70, 170, 95, 55), 2.0));

        return new InMemoryLayer(
                "new-england-sample",
                "New England sample",
                List.of(region, route, boston, providence, worcester));
    }

    private static Feature city(String id, String name, double longitude, double latitude) {
        return new Feature(
                id,
                name,
                new PointGeometry(new Coordinate(longitude, latitude)),
                Map.of("kind", "city"),
                FeatureStyle.point(Rgba.rgb(28, 108, 184), 10.0));
    }
}
