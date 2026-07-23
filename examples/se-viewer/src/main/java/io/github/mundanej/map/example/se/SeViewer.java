package io.github.mundanej.map.example.se;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.io.se.SeFeatureStyle;
import io.github.mundanej.map.io.se.SeReadOptions;
import io.github.mundanej.map.io.se.SeStyles;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable demonstration of the first bounded OGC SE point-symbolizer slice. */
public final class SeViewer {
    static final String STYLE_RESOURCE = "/io/github/mundanej/map/example/se/point-style.xml";

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
        Feature point =
                new Feature(
                        "styled-point",
                        "Styled point",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        BuiltInMarkers.filledScreen(
                                BuiltInMarker.SQUARE, Rgba.rgb(128, 128, 128), 6, 1));
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setLayerBindings(
                List.of(
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer("se-point", "SE point", List.of(point)),
                                style.portrayal())));
        return view;
    }

    static SeFeatureStyle readBundledStyle() {
        try (InputStream input = SeViewer.class.getResourceAsStream(STYLE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled SE style");
            }
            return SeStyles.read(
                    "bundled-point-style",
                    input.readAllBytes(),
                    NamedSymbolCatalog.of(List.of()),
                    SeReadOptions.defaults());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read bundled SE style", failure);
        }
    }

    private static void showWindow() {
        JFrame frame = new JFrame("mundane-java-map — OGC SE point style");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        MapView view = createMapView();
        frame.add(view, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationByPlatform(true);
        view.fitToData(64);
        frame.setVisible(true);
    }
}
