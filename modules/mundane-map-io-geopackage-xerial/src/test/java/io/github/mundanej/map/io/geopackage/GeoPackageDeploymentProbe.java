package io.github.mundanej.map.io.geopackage;

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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/** Separate-JVM deployment probe used to verify Xerial loader failure translation. */
public final class GeoPackageDeploymentProbe {
    private GeoPackageDeploymentProbe() {}

    /** Opens, queries, and renders one supplied strict fixture, then prints the stable outcome. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one fixture path");
        }
        try {
            Path path = Path.of(arguments[0]);
            GeoPackages.inspect(
                    path,
                    new SourceIdentity("deployment-probe", ""),
                    GeoPackageInspectOptions.defaults(),
                    CancellationToken.none());
            try (FeatureSource source =
                    GeoPackages.openFeatures(
                            path,
                            new SourceIdentity("deployment-probe", ""),
                            "points",
                            GeoPackageFeatureOptions.defaults(),
                            CancellationToken.none())) {
                try (FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                    if (!cursor.advance()) {
                        throw new IllegalStateException("Deployment fixture contained no feature");
                    }
                }
                SwingUtilities.invokeAndWait(() -> render(source));
            }
            System.out.println("SUCCESS");
        } catch (SourceException failure) {
            System.out.println(
                    failure.terminal().code()
                            + "|"
                            + failure.terminal().context().getOrDefault("reason", ""));
        }
    }

    private static void render(FeatureSource source) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);
        try {
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.borrowedFeature(
                                    "deployment",
                                    "Deployment fixture",
                                    source,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.CIRCLE, Rgba.rgb(20, 70, 210), 9, 1),
                                    SolidLineSymbol.of(
                                            new SymbolStroke(
                                                    Rgba.rgb(20, 70, 210),
                                                    new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                                            1),
                                    SolidFillSymbol.of(Rgba.rgb(20, 70, 210), 1))));
            view.setSize(160, 120);
            view.fitToData(8);
            BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            view.close();
        }
    }
}
