package io.github.mundanej.map.example.maplibre;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable in-memory gallery for the bounded MapLibre vector-style profile. */
public final class MapLibreStyleViewer {
    private static final String STYLE =
            "/io/github/mundanej/map/example/maplibre/gallery-style.json";

    private MapLibreStyleViewer() {}

    /**
     * Launches the example on the Swing event-dispatch thread.
     *
     * @param arguments ignored command-line arguments
     */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(MapLibreStyleViewer::show);
    }

    static GallerySession createSession() {
        MapLibreStyle style = readStyle();
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(800, 500);
        view.setViewport(new MapViewport(800, 500, 0, 0, 0.5));
        InMemoryFeatureSource world =
                source(
                        "gallery",
                        List.of(
                                land(),
                                route(),
                                point("category-capital", -165, 80, "category", "capital", 0),
                                point("category-town", -115, 80, "category", "town", 0),
                                point("step-small", -55, 80, "step", "town", 20),
                                point("step-large", 5, 80, "step", "town", 2000),
                                point("conditional", -55, -70, "conditional", "alert", 0),
                                point("zoom", 70, 80, "zoom", "town", 0),
                                point("symbol-capital", 145, 80, "symbol", "capital", 0),
                                point("symbol-town", 205, 80, "symbol", "town", 0),
                                point("dynamic-icon", -125, -70, "dynamic-symbol", "town", 0),
                                missing(),
                                point("ordered", 125, -70, "ordered", "town", 0)));
        try {
            MapLibreStyleBinding binding =
                    MapLibreStyleBinder.bind(
                            style,
                            MapLibreSourceRegistry.builder().register("gallery", world).build(),
                            catalog());
            try {
                Set<FeatureSource> ownedSources =
                        Collections.newSetFromMap(new IdentityHashMap<>());
                view.setLayerBindings(
                        binding.layers().stream()
                                .map(layer -> binding(layer, ownedSources.add(layer.source())))
                                .toList());
                return new GallerySession(view, binding);
            } catch (RuntimeException failure) {
                binding.close();
                throw failure;
            }
        } catch (RuntimeException failure) {
            view.close();
            world.close();
            throw failure;
        }
    }

    private static void show() {
        JFrame frame = new JFrame("mundane-java-map — MapLibre vector-style gallery");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        GallerySession session = createSession();
        MapView view = session.view();
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        session.close();
                    }
                });
        frame.add(
                new JLabel(
                        "Literals, filters, categories, steps, zoom interpolation, icons, labels, "
                                + "missing data, and ordering."),
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

    private static FeatureRecord point(
            String id, double x, double y, String family, String kind, long population) {
        return new FeatureRecord(
                id,
                id,
                new PointGeometry(new Coordinate(x, y)),
                Map.of(
                        "family", family,
                        "kind", kind,
                        "population", population,
                        "name", id.replace('-', ' ')));
    }

    private static FeatureRecord missing() {
        return new FeatureRecord(
                "missing",
                "Missing attribute",
                new PointGeometry(new Coordinate(55, -70)),
                Map.of("family", "missing"));
    }

    private static NamedSymbolCatalog catalog() {
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol(
                                "capital",
                                BuiltInMarkers.filledScreen(
                                        io.github.mundanej.map.api.BuiltInMarker.STAR,
                                        Rgba.rgb(190, 50, 55),
                                        22,
                                        1)),
                        new NamedSymbol(
                                "town",
                                BuiltInMarkers.filledScreen(
                                        io.github.mundanej.map.api.BuiltInMarker.DIAMOND,
                                        Rgba.rgb(43, 102, 161),
                                        16,
                                        1))));
    }

    static final class GallerySession implements AutoCloseable {
        private final MapView view;
        private final MapLibreStyleBinding binding;
        private final AtomicBoolean closed = new AtomicBoolean();

        private GallerySession(MapView view, MapLibreStyleBinding binding) {
            this.view = view;
            this.binding = binding;
        }

        MapView view() {
            if (closed.get()) {
                throw new IllegalStateException("Gallery session is closed");
            }
            return view;
        }

        /** Closes the view before releasing its style binding. */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                view.close();
                binding.close();
            }
        }
    }
}
