package io.github.mundanej.map.example.maplibre;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer;
import io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyle;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinder;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinding;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyles;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        InMemoryFeatureSource world = source("world", List.of(land(), route(), place()));
        MapLibreStyleBinding binding =
                MapLibreStyleBinder.bind(
                        style,
                        MapLibreSourceRegistry.builder().register("world-data", world).build());
        Set<FeatureSource> ownedSources = Collections.newSetFromMap(new IdentityHashMap<>());
        view.setLayerBindings(
                binding.layers().stream()
                        .map(layer -> binding(layer, ownedSources.add(layer.source())))
                        .toList());
        binding.close();
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

    private static MapLayerBinding binding(MapLibreBoundLayer layer, boolean owned) {
        MapLayerBinding binding =
                owned
                        ? MapLayerBinding.ownedFeature(
                                layer.id(),
                                layer.id(),
                                layer.source(),
                                layer.portrayal().orElseThrow())
                        : MapLayerBinding.borrowedFeature(
                                layer.id(),
                                layer.id(),
                                layer.source(),
                                layer.portrayal().orElseThrow());
        binding.setPortrayalZoomRange(layer.minimumZoom(), layer.maximumZoom());
        return binding;
    }

    private static InMemoryFeatureSource source(String id, List<FeatureRecord> records) {
        return InMemoryFeatureSource.open(
                new SourceIdentity(id, id),
                records,
                Optional.empty(),
                Optional.of(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty())),
                FeatureSourceLimits.LEVEL_1);
    }

    private static FeatureRecord land() {
        return new FeatureRecord(
                "land",
                "Land",
                new PolygonGeometry(
                        CoordinateSequence.of(
                                -260, -160, 260, -160, 260, 160, -260, 160, -260, -160),
                        List.of()),
                Map.of());
    }

    private static FeatureRecord route() {
        return new FeatureRecord(
                "route",
                "Route",
                new LineStringGeometry(CoordinateSequence.of(-230, -90, -70, 40, 220, 100)),
                Map.of("kind", "route"));
    }

    private static FeatureRecord place() {
        return new FeatureRecord(
                "place",
                "Place",
                new PointGeometry(new Coordinate(30, 25)),
                Map.of("kind", "city"));
    }
}
