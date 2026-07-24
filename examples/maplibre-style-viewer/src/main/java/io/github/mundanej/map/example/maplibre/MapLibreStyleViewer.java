package io.github.mundanej.map.example.maplibre;

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
import io.github.mundanej.map.io.maplibre.style.MapLibreLayer;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyle;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyles;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable in-memory example for the bounded literal MapLibre vector-style slice. */
public final class MapLibreStyleViewer {
    private static final String STYLE =
            "/io/github/mundanej/map/example/maplibre/literal-style.json";

    private MapLibreStyleViewer() {}

    /**
     * Launches the example on the Swing event-dispatch thread.
     *
     * @param arguments ignored command-line arguments
     */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(MapLibreStyleViewer::show);
    }

    /**
     * Creates a caller-owned configured map view.
     *
     * @return configured view
     */
    public static MapView createMapView() {
        MapLibreStyle style = readStyle();
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(800, 500);
        view.setViewport(new MapViewport(800, 500, 0, 0, 0.5));
        view.setLayerBindings(
                List.of(
                        binding(style.layers().get(0), land()),
                        binding(style.layers().get(1), route()),
                        binding(style.layers().get(2), place())));
        return view;
    }

    private static void show() {
        JFrame frame = new JFrame("mundane-java-map — MapLibre literal vector style");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        MapView view = createMapView();
        frame.add(
                new JLabel(
                        "Detached v8 JSON styles caller-owned in-memory point, line, and polygon data."),
                BorderLayout.NORTH);
        frame.add(view, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationByPlatform(true);
        view.setViewport(new MapViewport(view.getWidth(), view.getHeight(), 0, 0, 0.5));
        frame.setVisible(true);
    }

    private static MapLibreStyle readStyle() {
        try (InputStream input = MapLibreStyleViewer.class.getResourceAsStream(STYLE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled MapLibre style");
            }
            return MapLibreStyles.read(input.readAllBytes());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read bundled MapLibre style", failure);
        }
    }

    private static MapLayerBinding binding(MapLibreLayer layer, Feature feature) {
        return MapLayerBinding.portrayedSnapshot(
                new InMemoryLayer(layer.id(), layer.id(), List.of(feature)),
                layer.portrayal().orElseThrow());
    }

    @SuppressWarnings("deprecation")
    private static Feature land() {
        return new Feature(
                "land",
                "Land",
                new PolygonGeometry(
                        CoordinateSequence.of(
                                -260, -160, 260, -160, 260, 160, -260, 160, -260, -160),
                        List.of()),
                Map.of(),
                FeatureStyle.polygon(Rgba.rgb(0, 0, 0), Rgba.rgb(0, 0, 0), 1));
    }

    @SuppressWarnings("deprecation")
    private static Feature route() {
        return new Feature(
                "route",
                "Route",
                new LineStringGeometry(CoordinateSequence.of(-230, -90, -70, 40, 220, 100)),
                Map.of(),
                FeatureStyle.line(Rgba.rgb(0, 0, 0), 1));
    }

    @SuppressWarnings("deprecation")
    private static Feature place() {
        return new Feature(
                "place",
                "Place",
                new PointGeometry(new Coordinate(30, 25)),
                Map.of(),
                FeatureStyle.point(Rgba.rgb(0, 0, 0), 1));
    }
}
