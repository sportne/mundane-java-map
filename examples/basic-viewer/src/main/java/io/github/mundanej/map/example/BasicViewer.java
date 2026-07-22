package io.github.mundanej.map.example;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.awt.HorizontalWrapMode;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable demonstration of the initial map vertical slice. */
public final class BasicViewer {
    private BasicViewer() {}

    /** Launches the viewer on the Swing event-dispatch thread. */
    public static void main(String[] arguments) {
        boolean worldWrap = List.of(arguments).contains("--world-wrap");
        SwingUtilities.invokeLater(() -> showWindow(worldWrap));
    }

    /** Creates the configured map view without opening a window. */
    public static MapView createMapView() {
        MapView map = new MapView(new WebMercatorProjection());
        map.setLayers(List.of(sampleLayer()));
        return map;
    }

    /**
     * Creates a mixed local/global point-source demonstration with explicit horizontal repetition.
     *
     * @return configured caller-owned view; closing it closes the demonstration source
     */
    public static MapView createWrappedMapView() {
        MapView map = new MapView(new WebMercatorProjection());
        map.setHorizontalWrap(HorizontalWrap.webMercator());
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("dateline-cities", "Dateline cities"),
                        List.of(
                                cityRecord("suva", "Suva", 178.4501, -18.1248),
                                cityRecord("apia", "Apia", -171.7514, -13.8507)),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.empty(),
                                        Optional.empty())),
                        FeatureSourceLimits.LEVEL_1);
        MapLayerBinding repeating =
                MapLayerBinding.ownedFeature(
                        "dateline-cities",
                        "Repeating dateline cities",
                        source,
                        BuiltInMarkers.filledScreen(
                                BuiltInMarker.DIAMOND, Rgba.rgb(180, 55, 45), 12.0, 1.0),
                        SolidLineSymbol.of(stroke(Rgba.rgb(180, 55, 45), 2.0), 1.0),
                        SolidFillSymbol.of(Rgba.rgb(180, 55, 45), 1.0));
        repeating.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
        map.setLayerBindings(List.of(MapLayerBinding.snapshot(sampleLayer()), repeating));
        return map;
    }

    private static void showWindow(boolean worldWrap) {
        JFrame frame =
                new JFrame(
                        worldWrap
                                ? "mundane-java-map — continuous-world viewer"
                                : "mundane-java-map — basic viewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        MapView map = worldWrap ? createWrappedMapView() : createMapView();
        JLabel coordinates = new JLabel("Move the pointer over the map");
        map.addMapPointerListener(
                event ->
                        coordinates.setText(
                                event.mapCoordinate()
                                        .map(
                                                coordinate ->
                                                        String.format(
                                                                Locale.ROOT,
                                                                "longitude %.5f, latitude %.5f",
                                                                coordinate.x(),
                                                                coordinate.y()))
                                        .orElse("Pointer is outside the map CRS domain")));

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
                        SolidLineSymbol.of(stroke(Rgba.rgb(190, 45, 45), 3.0), 1.0));

        Feature region =
                new Feature(
                        "region",
                        "",
                        new PolygonGeometry(
                                CoordinateSequence.of(
                                        -72.05, 41.65, -70.75, 41.65, -70.75, 42.55, -72.05, 42.55,
                                        -72.05, 41.65)),
                        Map.of("kind", "region"),
                        SolidFillSymbol.of(
                                new Rgba(70, 170, 95, 55),
                                Optional.of(
                                        SolidLineSymbol.of(
                                                stroke(Rgba.rgb(45, 110, 65), 2.0), 1.0)),
                                1.0));

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
                VectorMarkerSymbol.of(
                        BuiltInMarkers.path(BuiltInMarker.CIRCLE),
                        BuiltInMarkers.viewBox(),
                        Rgba.rgb(28, 108, 184),
                        Optional.of(stroke(Rgba.rgb(28, 108, 184), 1.0)),
                        io.github.mundanej.map.api.MarkerPlacement.centeredScreen(10.0),
                        1.0));
    }

    private static FeatureRecord cityRecord(
            String id, String name, double longitude, double latitude) {
        return new FeatureRecord(
                id,
                name,
                new PointGeometry(new Coordinate(longitude, latitude)),
                Map.of("kind", "city"));
    }

    private static SymbolStroke stroke(Rgba color, double widthPixels) {
        return new SymbolStroke(color, new SymbolLength(widthPixels, SymbolUnit.SCREEN_PIXEL));
    }
}
