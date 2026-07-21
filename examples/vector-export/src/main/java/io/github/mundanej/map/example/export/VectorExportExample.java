package io.github.mundanej.map.example.export;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.svg.SvgExportException;
import io.github.mundanej.map.io.svg.SvgMapExports;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Displays a vector viewport and exports that exact visible picture as static SVG. */
public final class VectorExportExample {
    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;

    private VectorExportExample() {}

    /** Launches the example; an optional first argument selects the output SVG path. */
    public static void main(String[] arguments) {
        Path target =
                arguments.length == 0
                        ? Path.of("build", "example-output", "map.svg")
                        : Path.of(arguments[0]);
        SwingUtilities.invokeLater(() -> showWindow(target));
    }

    /** Creates the example panel without opening a top-level window. */
    public static JPanel createPanel(Path target) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("vector-export example must be created on the EDT");
        }
        MapView map = createMap();
        JLabel status = new JLabel("Ready — output: " + target.toAbsolutePath());
        status.setName("export-status");
        JButton export = new JButton("Export visible map");
        export.setName("export-button");
        export.addActionListener(
                ignored -> {
                    try {
                        SvgMapExports.writeAtomically(target, map.captureVectorExportSnapshot());
                        status.setText("Exported: " + target.toAbsolutePath());
                    } catch (SvgExportException exception) {
                        status.setText(
                                "Export failed ["
                                        + exception.problem().code()
                                        + "]: "
                                        + exception.problem().context());
                    } catch (VectorExportSnapshotException exception) {
                        status.setText(
                                "Capture failed ["
                                        + exception.problem().code()
                                        + "]: "
                                        + exception.problem().context());
                    } catch (RuntimeException exception) {
                        status.setText("Capture failed: " + exception.getMessage());
                    }
                });
        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.add(export, BorderLayout.WEST);
        controls.add(status, BorderLayout.CENTER);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(map, BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private static MapView createMap() {
        SymbolStroke outlineStroke = stroke(Rgba.rgb(52, 68, 84), 2);
        SolidLineSymbol outline = SolidLineSymbol.of(outlineStroke, 1);
        Feature land =
                new Feature(
                        "land",
                        "Land with lake",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        -300, 180, -30, 180, -30, 30, -300, 30, -300, 180),
                                List.of(
                                        CoordinateSequence.of(
                                                -225, 135, -105, 135, -105, 75, -225, 75, -225,
                                                135))),
                        Map.of(),
                        SolidFillSymbol.of(Rgba.rgb(91, 163, 106), Optional.of(outline), 1));
        Feature route =
                new Feature(
                        "route",
                        "Route",
                        new LineStringGeometry(CoordinateSequence.of(-300, -30, -45, -30, 15, 15)),
                        Map.of(),
                        SolidLineSymbol.of(
                                stroke(Rgba.rgb(52, 68, 84), 3),
                                Optional.empty(),
                                Optional.of(
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.ARROW, Rgba.rgb(52, 68, 84), 16, 1)),
                                1));
        Feature hatch =
                new Feature(
                        "hatch",
                        "Hatched region",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        105, -30, 315, -30, 315, -180, 105, -180, 105, -30)),
                        Map.of(),
                        HatchFillSymbol.of(
                                HatchPattern.CROSS_DIAGONAL,
                                stroke(Rgba.rgb(208, 75, 61), 2),
                                new SymbolLength(12, SymbolUnit.SCREEN_PIXEL),
                                SymbolRotationMode.SCREEN_RELATIVE,
                                Optional.of(outline),
                                0.9,
                                512));
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(35, 110, 175), 18, 1);
        Feature track =
                new Feature(
                        "track-7",
                        "TRACK 7",
                        new PointGeometry(new Coordinate(75, 105)),
                        Map.of(),
                        marker);
        FeaturePortrayal trackPortrayal =
                FeaturePortrayal.markers(new FixedSymbolSelector(marker))
                        .withPointLabel(
                                new PointLabelProfile(
                                        FeatureName.INSTANCE,
                                        new LabelTextStyle(
                                                Rgba.rgb(35, 55, 75), LabelWeight.BOLD, 14),
                                        List.of(
                                                PointLabelPosition.NE,
                                                PointLabelPosition.NW,
                                                PointLabelPosition.E),
                                        6,
                                        0,
                                        0,
                                        2,
                                        4,
                                        ResolutionRange.ALL));
        MapView map =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        map.setName("vector-export-map");
        map.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        map.setSize(WIDTH, HEIGHT);
        map.setBackground(new Color(245, 246, 247));
        map.setViewport(new MapViewport(WIDTH, HEIGHT, 0, 0, 1));
        map.setLayerBindings(
                List.of(
                        MapLayerBinding.snapshot(
                                new InMemoryLayer(
                                        "export-shapes",
                                        "Vector export shapes",
                                        List.of(land, route, hatch))),
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer(
                                        "export-track", "Vector export track", List.of(track)),
                                trackPortrayal)));
        return map;
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static void showWindow(Path target) {
        JFrame frame = new JFrame("mundane-java-map — vector export");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(createPanel(target));
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }
}
