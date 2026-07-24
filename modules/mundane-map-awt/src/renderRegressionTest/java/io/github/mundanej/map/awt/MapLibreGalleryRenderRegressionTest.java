package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
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
import io.github.mundanej.map.api.VectorExportSnapshot;
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapLibreGalleryRenderRegressionTest {
    private static final String GALLERY =
            "/io/github/mundanej/map/example/maplibre/gallery-style.json";

    @Test
    void actualGalleryHasPortableFullProfileEvidence() throws Exception {
        MapLibreStyle style = MapLibreStyles.read(galleryStyle());
        InMemoryFeatureSource source = source();
        MapLibreStyleBinding styleBinding =
                MapLibreStyleBinder.bind(
                        style,
                        MapLibreSourceRegistry.builder().register("gallery", source).build(),
                        catalog());
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        AtomicReference<VectorExportSnapshot> exportReference = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        MapView view =
                                new MapView(
                                        CrsRegistry.level1(),
                                        CrsDefinitions.EPSG_3857,
                                        CrsDefinitions.EPSG_3857);
                        view.setSize(600, 400);
                        view.setBackground(Color.WHITE);
                        view.setViewport(new MapViewport(600, 400, 0, 0, 1));
                        Set<FeatureSource> owned =
                                Collections.newSetFromMap(new IdentityHashMap<>());
                        view.setLayerBindings(
                                styleBinding.layers().stream()
                                        .map(layer -> binding(layer, owned.add(layer.source())))
                                        .toList());

                        BufferedImage image =
                                new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        imageReference.set(image);
                        exportReference.set(view.captureVectorExportSnapshot());
                        viewReference.set(view);
                    });

            BufferedImage image = imageReference.get();
            assertNear(image, 80, 200, Rgba.rgb(216, 230, 196), 20, "literal fill");
            assertBlueEvidence(image, 300, 146, 18, "filtered line");
            assertNear(image, 135, 120, Rgba.rgb(200, 63, 73), 20, "match expression");
            assertNear(image, 245, 270, Rgba.rgb(217, 71, 63), 20, "case expression");
            assertNear(image, 305, 120, Rgba.rgb(78, 131, 196), 24, "step expression");
            assertNear(image, 370, 120, Rgba.rgb(111, 85, 165), 24, "zoom interpolation");
            assertNear(image, 425, 270, Rgba.rgb(45, 89, 127), 20, "later-layer ordering");
            assertNear(image, 175, 270, Rgba.rgb(43, 102, 161), 24, "dynamic catalog icon");
            assertNear(image, 445, 120, Rgba.rgb(190, 50, 55), 24, "labeled catalog icon");
            assertNear(image, 355, 270, Rgba.rgb(170, 170, 170), 24, "missing-data fallback");
            assertEquals(1, exportReference.get().labels().size());
            assertTrue(
                    Set.of("symbol capital", "symbol town")
                            .contains(exportReference.get().labels().getFirst().text()));
            assertEquals(
                    "ordered-overlay",
                    viewReference.get().hitTest(425, 270, 0).topmost().orElseThrow().layerId());
        } finally {
            MapView view = viewReference.get();
            if (view != null) {
                SwingUtilities.invokeAndWait(view::close);
            }
            styleBinding.close();
            if (!source.isClosed()) {
                source.close();
            }
        }
        assertTrue(source.isClosed());
    }

    private static MapLayerBinding binding(MapLibreBoundLayer layer, boolean owned) {
        MapLayerBinding result =
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
        result.setPortrayalZoomRange(layer.minimumZoom(), layer.maximumZoom());
        return result;
    }

    private static InMemoryFeatureSource source() {
        return InMemoryFeatureSource.open(
                new SourceIdentity("gallery", "Gallery"),
                List.of(
                        new FeatureRecord(
                                "land",
                                "Land",
                                new PolygonGeometry(
                                        CoordinateSequence.of(
                                                -260, -160, 260, -160, 260, 160, -260, 160, -260,
                                                -160),
                                        List.of()),
                                Map.of()),
                        new FeatureRecord(
                                "route",
                                "Route",
                                new LineStringGeometry(
                                        CoordinateSequence.of(-230, -90, -70, 40, 220, 100)),
                                Map.of("kind", "route")),
                        point("category-capital", -165, 80, "category", "capital", 0),
                        point("category-town", -115, 80, "category", "town", 0),
                        point("step-small", -55, 80, "step", "town", 20),
                        point("step-large", 5, 80, "step", "town", 2000),
                        point("conditional", -55, -70, "conditional", "alert", 0),
                        point("zoom", 70, 80, "zoom", "town", 0),
                        point("symbol-capital", 145, 80, "symbol", "capital", 0),
                        point("symbol-town", 205, 80, "symbol", "town", 0),
                        point("dynamic-icon", -125, -70, "dynamic-symbol", "town", 0),
                        new FeatureRecord(
                                "missing",
                                "Missing attribute",
                                new PointGeometry(new Coordinate(55, -70)),
                                Map.of("family", "missing")),
                        point("ordered", 125, -70, "ordered", "town", 0)),
                Optional.empty(),
                Optional.of(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty())),
                FeatureSourceLimits.LEVEL_1);
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

    private static NamedSymbolCatalog catalog() {
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol(
                                "capital",
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.STAR, Rgba.rgb(190, 50, 55), 22, 1)),
                        new NamedSymbol(
                                "town",
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(43, 102, 161), 16, 1))));
    }

    private static byte[] galleryStyle() throws IOException {
        try (InputStream input =
                MapLibreGalleryRenderRegressionTest.class.getResourceAsStream(GALLERY)) {
            if (input == null) {
                throw new IllegalStateException("Missing shared MapLibre gallery style");
            }
            return input.readAllBytes();
        }
    }

    private static void assertNear(
            BufferedImage image, int x, int y, Rgba expected, int tolerance, String description) {
        Color actual = new Color(image.getRGB(x, y), true);
        int distance =
                Math.max(
                        Math.max(
                                Math.abs(actual.getRed() - expected.red()),
                                Math.abs(actual.getGreen() - expected.green())),
                        Math.abs(actual.getBlue() - expected.blue()));
        assertTrue(distance <= tolerance, description + " color distance was " + distance);
    }

    private static void assertBlueEvidence(
            BufferedImage image, int centerX, int centerY, int radius, String description) {
        int count = 0;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getBlue() > color.getRed() + 30) {
                    count++;
                }
            }
        }
        assertTrue(count > 10, description + " produced insufficient blue evidence");
    }
}
