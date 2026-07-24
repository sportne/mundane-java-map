package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeaturePortrayal;
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
import io.github.mundanej.map.io.kml.KmlFiles;
import io.github.mundanej.map.io.kml.KmlOpenOptions;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Exact bounded KML parser, query, renderer, warning, and malformed native scenario. */
final class NativeKmlSmokeScenario {
    private static final SourceIdentity ID = new SourceIdentity("native-kml", "Native KML");
    private static final String MALFORMED =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark><Point><coordinates>0,0</coordinates>
            </kml>
            """;

    private NativeKmlSmokeScenario() {}

    static Result run(Path validPath) {
        FeatureSource source =
                KmlFiles.open(validPath, ID, KmlOpenOptions.defaults(), CancellationToken.none());
        return runOpened(source, validPath);
    }

    static Result runOpened(FeatureSource source, Path validPath) {
        try {
            int records = count(source);
            DiagnosticReport warnings = source.openingDiagnostics();
            if (warnings.entries().stream()
                    .noneMatch(diagnostic -> diagnostic.code().equals("KML_ALTITUDE_IGNORED"))) {
                throw new IllegalStateException("kml-native: altitude warning is missing");
            }
            int colored = render(source);
            if (!source.isClosed()) {
                throw new IllegalStateException("kml-native: rendered source was not closed");
            }
            DiagnosticReport malformed =
                    malformed(validPath.resolveSibling("malformed-native.kml"));
            return new Result(records, colored, source.isClosed(), warnings, malformed);
        } catch (RuntimeException | Error failure) {
            try {
                source.close();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static int count(FeatureSource source) {
        int records = 0;
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                cursor.current();
                records++;
            }
        }
        if (records != 3) {
            throw new IllegalStateException("kml-native: query count changed");
        }
        return records;
    }

    private static int render(FeatureSource source) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Integer> colored = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            colored.set(renderOnEdt(source));
                        } catch (Throwable thrown) {
                            failure.set(thrown);
                        }
                    });
        } catch (Exception exception) {
            source.close();
            throw new IllegalStateException("kml-native: unable to render on EDT", exception);
        }
        if (failure.get() != null) {
            source.close();
            throw new IllegalStateException("kml-native: rendering failed", failure.get());
        }
        return colored.get();
    }

    private static int renderOnEdt(FeatureSource source) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        try {
            SolidLineSymbol line =
                    SolidLineSymbol.of(
                            new SymbolStroke(
                                    Rgba.rgb(170, 55, 35),
                                    new SymbolLength(3, SymbolUnit.SCREEN_PIXEL)),
                            1);
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.ownedFeature(
                                    "kml",
                                    "KML",
                                    source,
                                    FeaturePortrayal.fixed(
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.DIAMOND,
                                                    Rgba.rgb(170, 55, 35),
                                                    14,
                                                    1),
                                            line,
                                            SolidFillSymbol.of(
                                                    new Rgba(170, 55, 35, 96),
                                                    Optional.of(line),
                                                    1)))));
            view.setSize(260, 180);
            view.fitToData(20);
            BufferedImage image = new BufferedImage(260, 180, BufferedImage.TYPE_INT_ARGB);
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
                    if (color.getRed() > color.getBlue() + 25) {
                        colored++;
                    }
                }
            }
            if (colored < 80) {
                throw new IllegalStateException("kml-native: source did not render");
            }
            requireDominantRed(image, view, new Coordinate(-77.0365, 38.8977), 3, "point");
            requireDominantRed(image, view, new Coordinate(-77.039, 38.891), 3, "line");
            requireDominantRed(image, view, new Coordinate(-77.045, 38.885), 1, "polygon fill");
            requireNearWhite(image, view, new Coordinate(-77.032, 38.892), 1, "polygon hole");
            return colored;
        } finally {
            view.close();
        }
    }

    private static void requireDominantRed(
            BufferedImage image, MapView view, Coordinate map, int radius, String evidence) {
        Coordinate screen =
                view.mapToScreen(map)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "kml-native: "
                                                        + evidence
                                                        + " is not representable"));
        int matches = 0;
        int centerX = (int) Math.round(screen.x());
        int centerY = (int) Math.round(screen.y());
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() > color.getBlue() + 25) {
                    matches++;
                }
            }
        }
        if (matches == 0) {
            throw new IllegalStateException("kml-native: " + evidence + " probe failed");
        }
    }

    private static void requireNearWhite(
            BufferedImage image, MapView view, Coordinate map, int radius, String evidence) {
        Coordinate screen =
                view.mapToScreen(map)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "kml-native: "
                                                        + evidence
                                                        + " is not representable"));
        int matches = 0;
        int centerX = (int) Math.round(screen.x());
        int centerY = (int) Math.round(screen.y());
        int samples = (radius * 2 + 1) * (radius * 2 + 1);
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() >= 245 && color.getGreen() >= 245 && color.getBlue() >= 245) {
                    matches++;
                }
            }
        }
        if (matches < samples - 1) {
            throw new IllegalStateException("kml-native: " + evidence + " probe failed");
        }
    }

    private static DiagnosticReport malformed(Path path) {
        DiagnosticReport report = null;
        RuntimeException primary = null;
        try {
            Files.writeString(
                    path,
                    MALFORMED,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                KmlFiles.open(path, ID, KmlOpenOptions.defaults(), CancellationToken.none())
                        .close();
                throw new IllegalStateException("kml-native: malformed KML was accepted");
            } catch (SourceException expected) {
                if (!expected.terminal().code().equals("KML_XML_INVALID")
                        || !expected.terminal().context().equals(Map.of("reason", "syntax"))) {
                    throw new IllegalStateException(
                            "kml-native: malformed diagnostic changed", expected);
                }
                report = expected.report();
            }
        } catch (IOException failure) {
            primary =
                    new IllegalStateException(
                            "kml-native: malformed fixture write failed", failure);
        } catch (RuntimeException failure) {
            primary = failure;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanup) {
            if (primary != null) {
                primary.addSuppressed(cleanup);
            } else {
                primary =
                        new IllegalStateException(
                                "kml-native: malformed fixture cleanup failed", cleanup);
            }
        }
        if (primary != null) {
            throw primary;
        }
        return java.util.Objects.requireNonNull(report, "malformed report");
    }

    record Result(
            int records,
            int coloredPixels,
            boolean sourceClosed,
            DiagnosticReport warnings,
            DiagnosticReport malformed) {}
}
