package io.github.mundanej.map.example.styling;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Demonstrates exact categorical markers and bounded collision-aware point labels. */
public final class StylingLabelViewer {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 640;
    private static final MarkerSymbol BLUE =
            BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(35, 105, 205), 18, 1);
    private static final MarkerSymbol RED =
            BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(195, 55, 55), 20, 1);
    private static final MarkerSymbol GRAY =
            BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(105, 110, 120), 16, 1);

    private StylingLabelViewer() {}

    /**
     * Launches the example on the Swing event-dispatch thread.
     *
     * @param arguments ignored command-line arguments
     */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(StylingLabelViewer::show);
    }

    static MapView createView() {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        view.setSize(WIDTH, HEIGHT);
        view.setBackground(new java.awt.Color(247, 249, 252));
        view.setViewport(new MapViewport(WIDTH, HEIGHT, 0, 0, 1));

        CategoricalSymbolSelector categories =
                new CategoricalSymbolSelector(
                        "status",
                        List.of(
                                new CategoricalSymbolRule(ThematicValue.text("normal"), BLUE),
                                new CategoricalSymbolRule(ThematicValue.text("alert"), RED)),
                        Optional.of(GRAY));
        FeaturePortrayal categoryPortrayal =
                FeaturePortrayal.markers(categories)
                        .withPointLabel(
                                labelProfile(
                                        Rgba.rgb(35, 45, 60),
                                        0,
                                        PointLabelPosition.NE,
                                        PointLabelPosition.NW,
                                        PointLabelPosition.E,
                                        PointLabelPosition.W));
        InMemoryLayer categoriesLayer =
                new InMemoryLayer(
                        "categories",
                        "Exact categories",
                        List.of(
                                feature("normal", "Normal", -300, 150, "normal"),
                                feature("alert", "Alert", 0, 150, "alert"),
                                feature("fallback", "Fallback", 300, 150, "unknown"),
                                feature("edge", "Edge fallback", 435, -180, "normal")));

        Feature collisionAnchor = feature("preferred", "Priority 10 wins", -20, -90, "normal");
        Feature hiddenAnchor = feature("suppressed", "Priority 0 is omitted", -20, -90, "alert");
        FeaturePortrayal preferred =
                FeaturePortrayal.markers(new FixedSymbolSelector(BLUE))
                        .withPointLabel(
                                labelProfile(Rgba.rgb(20, 80, 145), 10, PointLabelPosition.NE));
        FeaturePortrayal suppressed =
                FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                        .withPointLabel(
                                labelProfile(Rgba.rgb(170, 35, 35), 0, PointLabelPosition.NE));

        view.setLayerBindings(
                List.of(
                        MapLayerBinding.portrayedSnapshot(categoriesLayer, categoryPortrayal),
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer(
                                        "preferred",
                                        "Higher label priority",
                                        List.of(collisionAnchor)),
                                preferred),
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer(
                                        "suppressed",
                                        "Topmost lower priority",
                                        List.of(hiddenAnchor)),
                                suppressed)));
        return view;
    }

    private static PointLabelProfile labelProfile(
            Rgba color, int priority, PointLabelPosition... positions) {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                new LabelTextStyle(color, LabelWeight.BOLD, 15),
                List.of(positions),
                7,
                0,
                0,
                2,
                priority,
                ResolutionRange.ALL);
    }

    private static Feature feature(String id, String name, double x, double y, String status) {
        return new Feature(
                id, name, new PointGeometry(new Coordinate(x, y)), Map.of("status", status), GRAY);
    }

    private static void show() {
        MapView view = createView();
        JFrame frame = new JFrame("Mundane Map — Thematic Styling and Point Labels");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        JLabel explanation =
                new JLabel(
                        "  Exact category colors • declared label fallback • priority 10 wins the shared anchor  ");
        explanation.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(explanation, BorderLayout.SOUTH);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        view.close();
                    }
                });
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }
}
