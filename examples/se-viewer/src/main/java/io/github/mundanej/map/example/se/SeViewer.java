package io.github.mundanej.map.example.se;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.se.SeFeatureStyle;
import io.github.mundanej.map.io.se.SeReadOptions;
import io.github.mundanej.map.io.se.SeStyles;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable review gallery for the bounded OGC SE vector profile. */
public final class SeViewer {
    static final String STYLE_RESOURCE = "/io/github/mundanej/map/example/se/gallery-style.xml";

    private SeViewer() {}

    /**
     * Launches the viewer on the Swing event-dispatch thread.
     *
     * @param arguments ignored command-line arguments
     */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(SeViewer::showWindow);
    }

    /**
     * Creates a configured map view without opening a window.
     *
     * @return caller-owned configured view
     */
    public static MapView createMapView() {
        SeFeatureStyle style = readBundledStyle();
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(800, 500);
        view.setViewport(new MapViewport(800, 500, 0, 0, 1));
        view.setLayerBindings(
                List.of(
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer("se-gallery", "SE gallery", features()),
                                style.portrayal())));
        return view;
    }

    static SeFeatureStyle readBundledStyle() {
        try (InputStream input = SeViewer.class.getResourceAsStream(STYLE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled SE style");
            }
            return SeStyles.read(
                    "bundled-gallery-style",
                    input.readAllBytes(),
                    catalog(),
                    SeReadOptions.defaults());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read bundled SE style", failure);
        }
    }

    private static void showWindow() {
        JFrame frame = new JFrame("mundane-java-map — OGC SE vector gallery");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        MapView view = createMapView();
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton inRange = new JButton("Scale rule visible");
        inRange.addActionListener(
                event ->
                        view.setViewport(
                                new MapViewport(view.getWidth(), view.getHeight(), 0, 0, 1)));
        JButton outOfRange = new JButton("Scale rule hidden");
        outOfRange.addActionListener(
                event ->
                        view.setViewport(
                                new MapViewport(view.getWidth(), view.getHeight(), 0, 0, 2)));
        controls.add(inRange);
        controls.add(outOfRange);
        controls.add(
                new JLabel(
                        "Top: ordered point, catalog marker, scale-filtered star. "
                                + "Middle: ordered line. Bottom: atomic polygon outlines."));
        frame.add(controls, BorderLayout.NORTH);
        frame.add(view, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationByPlatform(true);
        view.setViewport(new MapViewport(view.getWidth(), view.getHeight(), 0, 0, 1));
        frame.setVisible(true);
    }

    @SuppressWarnings("deprecation")
    private static List<Feature> features() {
        FeatureStyle pointStyle = FeatureStyle.point(Rgba.rgb(128, 128, 128), 6);
        FeatureStyle lineStyle = FeatureStyle.line(Rgba.rgb(128, 128, 128), 1);
        FeatureStyle fillStyle =
                FeatureStyle.polygon(Rgba.rgb(128, 128, 128), Rgba.rgb(128, 128, 128), 1);
        return List.of(
                new Feature(
                        "ordered-point",
                        "Ordered point",
                        new PointGeometry(new Coordinate(-70, 70)),
                        Map.of("kind", "ordered"),
                        pointStyle),
                new Feature(
                        "catalog-point",
                        "Catalog point",
                        new PointGeometry(new Coordinate(0, 70)),
                        Map.of("kind", "catalog"),
                        pointStyle),
                new Feature(
                        "scale-point",
                        "Scale point",
                        new PointGeometry(new Coordinate(70, 70)),
                        Map.of("kind", "scale"),
                        pointStyle),
                new Feature(
                        "ordered-line",
                        "Ordered line",
                        new LineStringGeometry(CoordinateSequence.of(-110, 15, 110, 15)),
                        Map.of("kind", "line"),
                        lineStyle),
                new Feature(
                        "ordered-area",
                        "Ordered area",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        -80, -85, 80, -85, 80, -25, -80, -25, -80, -85),
                                List.of(
                                        CoordinateSequence.of(
                                                -20, -65, 20, -65, 20, -45, -20, -45, -20, -65))),
                        Map.of("kind", "area"),
                        fillStyle));
    }

    private static NamedSymbolCatalog catalog() {
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol(
                                "gallery.airport",
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(170, 60, 150), 34, 1))));
    }
}
