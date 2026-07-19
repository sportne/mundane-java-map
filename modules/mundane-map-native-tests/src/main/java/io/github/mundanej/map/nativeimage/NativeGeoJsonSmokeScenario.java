package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import io.github.mundanej.map.io.geojson.GeoJsonWriteLimits;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Exact bounded GeoJSON parser, generator, source, writer, and renderer native scenario. */
final class NativeGeoJsonSmokeScenario {
    private static final SourceIdentity ID = new SourceIdentity("native-geojson", "Native GeoJSON");
    private static final byte[] DOCUMENT =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","id":"area","geometry":{"type":"Polygon","coordinates":[
                [[-2,-2],[2,-2],[2,2],[-2,2],[-2,-2]],
                [[-0.6,-0.6],[-0.6,0.6],[0.6,0.6],[0.6,-0.6],[-0.6,-0.6]]]},"properties":{"kind":"native"}},
              {"type":"Feature","id":"line","geometry":{"type":"LineString",
                "coordinates":[[-3,0],[3,0]]},"properties":{}},
              {"type":"Feature","id":"point","geometry":{"type":"Point",
                "coordinates":[-2.6,2.6]},"properties":{}}
            ]}
            """
                    .getBytes(StandardCharsets.UTF_8);

    private NativeGeoJsonSmokeScenario() {}

    static void run() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("mundane-map-native-geojson-");
            Path output = directory.resolve("round-trip.geojson");
            Path secondOutput = directory.resolve("round-trip-second.geojson");
            try (FeatureSource source = open(DOCUMENT)) {
                assertRecords(source, 3);
                GeoJsonFiles.write(
                        output, source, GeoJsonWriteLimits.defaults(), CancellationToken.none());
                GeoJsonFiles.write(
                        secondOutput,
                        source,
                        GeoJsonWriteLimits.defaults(),
                        CancellationToken.none());
            }
            byte[] first = Files.readAllBytes(output);
            if (!java.util.Arrays.equals(first, Files.readAllBytes(secondOutput))) {
                throw new IllegalStateException("geojson-native: deterministic output changed");
            }
            try (FeatureSource reopened =
                    GeoJsonFiles.open(
                            output, ID, GeoJsonOpenOptions.defaults(), CancellationToken.none())) {
                assertRecords(reopened, 3);
            }
            render(open(first));
            assertMalformedDiagnostic();
            Files.delete(secondOutput);
            Files.delete(output);
            Files.delete(directory);
            directory = null;
        } catch (IOException exception) {
            throw new IllegalStateException("geojson-native: temporary I/O failed", exception);
        } finally {
            if (directory != null) {
                cleanup(directory);
            }
        }
    }

    private static FeatureSource open(byte[] bytes) {
        return GeoJsonFiles.open(
                bytes, ID, GeoJsonOpenOptions.defaults(), CancellationToken.none());
    }

    private static void assertRecords(FeatureSource source, int expected) {
        int count = 0;
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                cursor.current();
                count++;
            }
        }
        if (count != expected) {
            throw new IllegalStateException("geojson-native: query count changed");
        }
    }

    private static void assertMalformedDiagnostic() {
        byte[] malformed =
                "{\"type\":\"Point\",\"type\":\"Point\",\"coordinates\":[0,0]}"
                        .getBytes(StandardCharsets.UTF_8);
        try {
            open(malformed).close();
            throw new IllegalStateException("geojson-native: duplicate member was accepted");
        } catch (SourceException expected) {
            if (!expected.terminal().code().equals("GEOJSON_JSON_INVALID")
                    || !expected.terminal().context().equals(Map.of("reason", "duplicateMember"))) {
                throw new IllegalStateException(
                        "geojson-native: malformed diagnostic changed", expected);
            }
        }
    }

    private static void render(FeatureSource source) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            renderOnEdt(source);
                        } catch (Throwable throwable) {
                            failure.set(throwable);
                        }
                    });
        } catch (Exception exception) {
            source.close();
            throw new IllegalStateException("geojson-native: unable to render on EDT", exception);
        }
        if (failure.get() != null) {
            throw new IllegalStateException("geojson-native: rendering failed", failure.get());
        }
        if (!source.isClosed()) {
            throw new IllegalStateException("geojson-native: owned source was not closed");
        }
    }

    private static void renderOnEdt(FeatureSource source) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        try {
            SolidLineSymbol line =
                    SolidLineSymbol.of(
                            new SymbolStroke(
                                    Rgba.rgb(30, 100, 190),
                                    new SymbolLength(3, SymbolUnit.SCREEN_PIXEL)),
                            1);
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.ownedFeature(
                                    "geojson",
                                    "GeoJSON",
                                    source,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.DIAMOND, Rgba.rgb(30, 100, 190), 14, 1),
                                    line,
                                    SolidFillSymbol.of(
                                            new Rgba(30, 100, 190, 120), Optional.of(line), 1))));
            view.setSize(240, 180);
            view.fitToData(24);
            BufferedImage image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
            int colored = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    Color color = new Color(image.getRGB(x, y), true);
                    if (color.getBlue() > color.getRed() + 25) {
                        colored++;
                    }
                }
            }
            if (colored < 150) {
                throw new IllegalStateException("geojson-native: source did not render");
            }
        } finally {
            view.close();
        }
    }

    private static void cleanup(Path directory) {
        deleteIfPresent(directory.resolve("round-trip-second.geojson"));
        deleteIfPresent(directory.resolve("round-trip.geojson"));
        try {
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // The primary failure remains authoritative.
        }
    }

    private static void deleteIfPresent(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The primary failure remains authoritative.
        }
    }
}
