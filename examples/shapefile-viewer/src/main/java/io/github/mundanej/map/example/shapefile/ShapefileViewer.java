package io.github.mundanej.map.example.shapefile;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Explicit-CRS viewer for the supported read-only shapefile profile. */
public final class ShapefileViewer {
    private static final String LAYER_ID = "shapefile";
    private static final String SOURCE_ID = "shapefile-source";

    private ShapefileViewer() {}

    /**
     * Validates arguments before scheduling and launches the Swing window.
     *
     * @param arguments the SHP path and explicit supported CRS identifier
     */
    public static void main(String[] arguments) {
        LaunchArguments launch = parseArguments(arguments);
        SwingUtilities.invokeLater(() -> showWindow(launch));
    }

    static LaunchArguments parseArguments(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: shapefile-viewer <path.shp> <EPSG:4326|EPSG:3857>");
        }
        String crsKey = Objects.requireNonNull(arguments[1], "arguments[1]");
        if (!crsKey.equals("EPSG:4326") && !crsKey.equals("EPSG:3857")) {
            throw new IllegalArgumentException("CRS must be EPSG:4326 or EPSG:3857");
        }
        Path path = Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
        CrsDefinition crs = CrsRegistry.level1().resolve(crsKey);
        return new LaunchArguments(path, crs);
    }

    static MapView createMapView(Path shapefile, CrsDefinition crsOverride) {
        Objects.requireNonNull(shapefile, "shapefile");
        Objects.requireNonNull(crsOverride, "crsOverride");
        FeatureSource source = null;
        MapLayerBinding binding = null;
        MapView view = null;
        boolean attached = false;
        try {
            source =
                    Shapefiles.open(
                            new SourceIdentity(SOURCE_ID, "Shapefile"),
                            shapefile,
                            ShapefileOpenOptions.defaults().withCrsOverride(crsOverride));
            binding =
                    MapLayerBinding.ownedFeature(
                            LAYER_ID, "Shapefile", source, marker(), line(), fill());
            source = null;
            view =
                    new MapView(
                            CrsRegistry.level1(),
                            CrsDefinitions.EPSG_4326,
                            CrsDefinitions.EPSG_3857);
            view.setLayerBindings(List.of(binding));
            attached = true;
            view.fitToData(32.0);
            return view;
        } catch (RuntimeException | Error failure) {
            if (view != null) {
                closeSuppressing(view, failure);
            }
            if (!attached && binding != null) {
                closeSuppressing(binding, failure);
            } else if (binding == null && source != null) {
                closeSuppressing(source, failure);
            }
            throw failure;
        }
    }

    private static void showWindow(LaunchArguments launch) {
        MapView map = createMapView(launch.path(), launch.crs());
        try {
            JFrame frame = new JFrame("mundane-java-map — shapefile viewer");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.add(map, BorderLayout.CENTER);
            Path fileName = launch.path().getFileName();
            frame.add(
                    new JLabel(fileName == null ? launch.path().toString() : fileName.toString()),
                    BorderLayout.SOUTH);
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent event) {
                            try {
                                map.close();
                            } finally {
                                frame.dispose();
                            }
                        }
                    });
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        } catch (RuntimeException | Error failure) {
            closeSuppressing(map, failure);
            throw failure;
        }
    }

    private static VectorMarkerSymbol marker() {
        return BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(35, 105, 190), 10.0, 1.0);
    }

    private static SolidLineSymbol line() {
        return SolidLineSymbol.of(stroke(Rgba.rgb(35, 105, 190), 2.0), 1.0);
    }

    private static SolidFillSymbol fill() {
        return SolidFillSymbol.of(new Rgba(35, 105, 190, 70), Optional.of(line()), 1.0);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Throwable cleanup) {
            if (cleanup != primary) {
                primary.addSuppressed(cleanup);
            }
        }
    }

    record LaunchArguments(Path path, CrsDefinition crs) {}
}
