package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.maplibre.style.MapLibreBoundLayer;
import io.github.mundanej.map.io.maplibre.style.MapLibreReadException;
import io.github.mundanej.map.io.maplibre.style.MapLibreSourceRegistry;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyle;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinder;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyleBinding;
import io.github.mundanej.map.io.maplibre.style.MapLibreStyles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared direct-Jackson MapLibre parse, expression, bind, label, and rendering smoke. */
final class NativeMapLibreSmokeScenario {
    private static final byte[] STYLE =
            """
            {
              "version": 8,
              "sources": {"tracks": {"type": "geojson"}},
              "layers": [
                {
                  "id": "alerts",
                  "type": "circle",
                  "source": "tracks",
                  "filter": ["==", ["get", "family"], "alert"],
                  "paint": {
                    "circle-radius": 10,
                    "circle-color": [
                      "match", ["get", "severity"], "high", "#d82828", "#808080"
                    ]
                  }
                },
                {
                  "id": "icons",
                  "type": "symbol",
                  "source": "tracks",
                  "filter": ["==", ["get", "family"], "icon"],
                  "layout": {
                    "symbol-z-order": "source",
                    "icon-image": "native.track",
                    "icon-allow-overlap": true,
                    "icon-ignore-placement": true
                  }
                },
                {
                  "id": "labels",
                  "type": "symbol",
                  "source": "tracks",
                  "filter": ["==", ["get", "family"], "label"],
                  "layout": {
                    "symbol-z-order": "source",
                    "icon-image": "native.track",
                    "icon-allow-overlap": true,
                    "icon-ignore-placement": true,
                    "icon-optional": true,
                    "text-field": ["get", "name"],
                    "text-font": ["SansSerif"],
                    "text-size": 12,
                    "text-anchor": "top",
                    "text-offset": [0, 1.2],
                    "text-optional": true
                  }
                }
              ]
            }
            """
                    .getBytes(StandardCharsets.UTF_8);

    private NativeMapLibreSmokeScenario() {}

    static Result run() {
        MapLibreStyle style = MapLibreStyles.read(STYLE);
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("native-maplibre", "Native MapLibre"),
                        List.of(
                                new FeatureRecord(
                                        "alert",
                                        "Alert",
                                        new PointGeometry(new Coordinate(-30, 0)),
                                        Map.of("family", "alert", "severity", "high")),
                                new FeatureRecord(
                                        "icon",
                                        "Icon",
                                        new PointGeometry(new Coordinate(30, 0)),
                                        Map.of("family", "icon")),
                                new FeatureRecord(
                                        "label",
                                        "Label",
                                        new PointGeometry(new Coordinate(0, 30)),
                                        Map.of("family", "label", "name", "Track 7"))),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.empty(),
                                        Optional.empty())),
                        FeatureSourceLimits.LEVEL_1);
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(
                        List.of(
                                new NamedSymbol(
                                        "native.track",
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.DIAMOND,
                                                Rgba.rgb(35, 105, 215),
                                                18,
                                                1))));
        RenderResult rendered;
        try (source;
                MapLibreStyleBinding binding =
                        MapLibreStyleBinder.bind(
                                style,
                                MapLibreSourceRegistry.builder().register("tracks", source).build(),
                                catalog)) {
            assertLabelBinding(binding.layers().get(2));
            rendered =
                    NativeShapefileSmokeScenario.onEdt(
                            () -> render(binding.layers().subList(0, 2)));
        }
        if (rendered.redPixels() < 100) {
            throw new IllegalStateException("maplibre-native: expression marker did not render");
        }
        if (rendered.bluePixels() < 40) {
            throw new IllegalStateException("maplibre-native: catalog icon did not render");
        }
        String diagnosticCode = assertUnsupportedRootDiagnostic();
        return new Result(rendered.redPixels(), rendered.bluePixels(), 1, diagnosticCode);
    }

    private static void assertLabelBinding(MapLibreBoundLayer layer) {
        FeaturePortrayalResolver resolver =
                FeaturePortrayalResolver.compile(layer.portrayal().orElseThrow());
        if (resolver.pointLabel().isEmpty()
                || !resolver.requiredConfigurationAttributes().equals(List.of("family", "name"))) {
            throw new IllegalStateException("maplibre-native: label binding changed");
        }
    }

    private static RenderResult render(List<MapLibreBoundLayer> layers) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_ARGB);
        try {
            view.setSize(image.getWidth(), image.getHeight());
            view.setViewport(new MapViewport(image.getWidth(), image.getHeight(), 0, 0, 1));
            view.setLayerBindings(
                    layers.stream().map(NativeMapLibreSmokeScenario::borrowedBinding).toList());
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
            int redPixels = 0;
            int bluePixels = 0;
            for (int y = 20; y < 80; y++) {
                for (int x = 20; x < 140; x++) {
                    int packed = image.getRGB(x, y);
                    int red = packed >>> 16 & 0xff;
                    int green = packed >>> 8 & 0xff;
                    int blue = packed & 0xff;
                    if (red > green + 80 && red > blue + 80) {
                        redPixels++;
                    }
                    if (blue > red + 60 && blue > green + 40) {
                        bluePixels++;
                    }
                }
            }
            return new RenderResult(redPixels, bluePixels);
        } finally {
            view.close();
        }
    }

    private static MapLayerBinding borrowedBinding(MapLibreBoundLayer layer) {
        MapLayerBinding binding =
                MapLayerBinding.borrowedFeature(
                        layer.id(), layer.id(), layer.source(), layer.portrayal().orElseThrow());
        binding.setPortrayalZoomRange(layer.minimumZoom(), layer.maximumZoom());
        return binding;
    }

    private static String assertUnsupportedRootDiagnostic() {
        byte[] unsupported =
                """
                {"version":8,"sources":{},"sprite":"https://example.invalid/sprite",
                 "layers":[{"id":"point","type":"circle","source":"memory"}]}
                """
                        .getBytes(StandardCharsets.UTF_8);
        try {
            MapLibreStyles.read(unsupported);
            throw new IllegalStateException("maplibre-native: unsupported root was accepted");
        } catch (MapLibreReadException expected) {
            if (!expected.problem().code().equals("MAPLIBRE_ROOT_UNSUPPORTED")
                    || !expected.problem().location().equals("/sprite")
                    || expected.getCause() != null) {
                throw new IllegalStateException(
                        "maplibre-native: unsupported-root diagnostic changed", expected);
            }
            return expected.problem().code();
        }
    }

    record Result(int redPixels, int bluePixels, int labelCount, String diagnosticCode) {}

    private record RenderResult(int redPixels, int bluePixels) {}
}
