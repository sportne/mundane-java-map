package io.github.mundanej.map.example.geopackage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.geopackage.GeoPackageFeatureOptions;
import io.github.mundanej.map.io.geopackage.GeoPackages;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Runnable viewer for one bounded GeoPackage feature table. */
public final class GeoPackageViewer {
    private GeoPackageViewer() {}

    /**
     * Opens one local GeoPackage table and launches a Swing map view.
     *
     * @param arguments {@code <absolute.gpkg> <feature-table>}
     */
    public static void main(String[] arguments) {
        runMain(
                arguments,
                System.err::println,
                source -> EventQueue.invokeLater(() -> launchWindow(source, System.err::println)));
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<FeatureSource> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        try {
            Arguments parsed = parseArguments(arguments);
            FeatureSource source = open(parsed);
            try {
                launcher.accept(source);
            } catch (RuntimeException | Error failure) {
                source.close();
                throw failure;
            }
            return true;
        } catch (RuntimeException failure) {
            failureSink.accept(summary(failure));
            return false;
        }
    }

    static Arguments parseArguments(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: geopackage-viewer <absolute.gpkg> <feature-table>");
        }
        Path path =
                Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"))
                        .toAbsolutePath()
                        .normalize();
        String table = Objects.requireNonNull(arguments[1], "arguments[1]");
        if (table.isBlank() || table.length() > 256 || table.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("feature-table must be a bounded non-blank name");
        }
        return new Arguments(path, table);
    }

    static FeatureSource open(Arguments arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "GeoPackage loading must run off the event dispatch thread");
        }
        return GeoPackages.openFeatures(
                arguments.path(),
                new SourceIdentity("geopackage-viewer", "GeoPackage features"),
                arguments.table(),
                GeoPackageFeatureOptions.defaults(),
                CancellationToken.none());
    }

    static MapView createView(FeatureSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "GeoPackage view creation must run on the event dispatch thread");
        }
        CrsDefinition crs =
                source.metadata()
                        .crs()
                        .flatMap(metadata -> metadata.definition())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "GeoPackage feature table CRS is not recognized"));
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        try {
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.ownedFeature(
                                    "geopackage",
                                    "GeoPackage features",
                                    source,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.CIRCLE, Rgba.rgb(25, 80, 200), 9, 1),
                                    SolidLineSymbol.of(
                                            new SymbolStroke(
                                                    Rgba.rgb(25, 80, 200),
                                                    new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                                            1),
                                    SolidFillSymbol.of(new Rgba(50, 130, 220, 128), 1))));
            view.setSize(900, 640);
            view.fitToData(20);
            return view;
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
    }

    static void launchWindow(FeatureSource source, Consumer<String> failureSink) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(failureSink, "failureSink");
        try {
            installWindow(source);
        } catch (RuntimeException failure) {
            closeAfterLaunchFailure(source, failure);
            failureSink.accept(summary(failure));
        } catch (Error failure) {
            closeAfterLaunchFailure(source, failure);
            throw failure;
        }
    }

    private static void installWindow(FeatureSource source) {
        MapView view = createView(source);
        boolean installed = false;
        try {
            JFrame frame = new JFrame("mundane-java-map — GeoPackage viewer");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.add(view, BorderLayout.CENTER);
            frame.setSize(900, 640);
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent event) {
                            view.close();
                        }
                    });
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            installed = true;
        } finally {
            if (!installed) {
                view.close();
            }
        }
    }

    private static void closeAfterLaunchFailure(FeatureSource source, Throwable failure) {
        if (source.isClosed()) {
            return;
        }
        try {
            source.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof io.github.mundanej.map.api.SourceException sourceFailure) {
            String context =
                    sourceFailure.terminal().context().entrySet().stream()
                            .sorted(java.util.Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + '=' + entry.getValue())
                            .collect(java.util.stream.Collectors.joining(","));
            return sourceFailure.terminal().code()
                    + (context.isEmpty() ? "" : " [" + context + ']')
                    + ": "
                    + sourceFailure.terminal().message();
        }
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    record Arguments(Path path, String table) {}
}
