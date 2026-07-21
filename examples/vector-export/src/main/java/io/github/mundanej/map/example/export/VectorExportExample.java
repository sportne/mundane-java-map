package io.github.mundanej.map.example.export;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.svg.SvgExportException;
import io.github.mundanej.map.io.svg.SvgMapExports;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
        Feature point =
                new Feature(
                        "point",
                        "Exported point",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        BuiltInMarkers.filledScreen(
                                BuiltInMarker.STAR, Rgba.rgb(210, 70, 40), 28, 1));
        Feature route =
                new Feature(
                        "route",
                        "Route",
                        new LineStringGeometry(
                                CoordinateSequence.of(-30, -15, -5, 18, 25, -5, 45, 20)),
                        Map.of(),
                        SolidLineSymbol.of(
                                new SymbolStroke(
                                        Rgba.rgb(30, 90, 180),
                                        new SymbolLength(3, SymbolUnit.SCREEN_PIXEL)),
                                1));
        MapView map = new MapView(new WebMercatorProjection());
        map.setName("vector-export-map");
        map.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        map.setSize(WIDTH, HEIGHT);
        map.setLayers(List.of(new InMemoryLayer("export", "Vector export", List.of(route, point))));
        map.fitToData(60);
        return map;
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
