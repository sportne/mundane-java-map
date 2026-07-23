package io.github.mundanej.map.example.raster;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.HorizontalWrapMode;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.RasterRenderOptions;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

/** Runnable bounded PNG/JPEG viewer with explicit normalized demonstration placement. */
public final class RasterViewer {
    static final String PLACEMENT_LABEL = "Normalized demonstration placement — not georeferenced";
    static final String WORLD_FILE_LABEL = "Explicit world-file affine placement";

    private RasterViewer() {}

    /**
     * Validates image arguments, opens off EDT, and launches the Swing viewer.
     *
     * @param arguments image path and optional explicit world-file CRS
     */
    public static void main(String[] arguments) {
        runMain(arguments, System.err::println);
    }

    static boolean runMain(String[] arguments, Consumer<String> failureSink) {
        return runMainWithMode(arguments, failureSink, RasterViewer::launch);
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<RasterSource> launcher) {
        Objects.requireNonNull(launcher, "launcher");
        return runMainWithMode(
                arguments, failureSink, (source, ignored) -> launcher.accept(source));
    }

    private static boolean runMainWithMode(
            String[] arguments,
            Consumer<String> failureSink,
            BiConsumer<RasterSource, Boolean> launcher) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(launcher, "launcher");
        try {
            Arguments parsed = parseArguments(arguments);
            RasterSource source = load(parsed);
            try {
                launcher.accept(source, parsed.repeatGlobal());
            } catch (RuntimeException | Error failure) {
                closeSuppressing(source, failure);
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
        if (arguments.length == 0) {
            throw usage();
        }
        Path path = Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
        Optional<CrsMetadata> worldFileCrs = Optional.empty();
        boolean repeatGlobal = false;
        for (int index = 1; index < arguments.length; index++) {
            String option = Objects.requireNonNull(arguments[index], "arguments[" + index + "]");
            if (option.equals("--repeat-global") && !repeatGlobal) {
                repeatGlobal = true;
                continue;
            }
            if (option.equals("--world-file")
                    && worldFileCrs.isEmpty()
                    && index + 1 < arguments.length) {
                String identifier =
                        Objects.requireNonNull(arguments[++index], "arguments[" + index + "]");
                var definition =
                        switch (identifier) {
                            case "EPSG:4326" -> CrsDefinitions.EPSG_4326;
                            case "EPSG:3857" -> CrsDefinitions.EPSG_3857;
                            default ->
                                    throw new IllegalArgumentException(
                                            "World-file CRS must be EPSG:4326 or EPSG:3857");
                        };
                worldFileCrs =
                        Optional.of(
                                CrsMetadata.recognized(
                                        definition, Optional.of(identifier), Optional.empty()));
                continue;
            }
            throw usage();
        }
        return new Arguments(path, worldFileCrs, repeatGlobal);
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException(
                "Usage: raster-viewer <image.png-or-jpeg> [--world-file EPSG:4326|EPSG:3857] [--repeat-global]");
    }

    static RasterSource load(Path path) {
        return load(new Arguments(path, Optional.empty(), false));
    }

    static RasterSource load(Arguments arguments) {
        Path path = Objects.requireNonNull(arguments, "arguments").path();
        Objects.requireNonNull(path, "path");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Raster loading must run off the event dispatch thread");
        }
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        ImagePlacement placement =
                arguments.worldFileCrs().isPresent()
                        ? ImagePlacement.worldFile(arguments.worldFileCrs())
                        : ImagePlacement.axisAligned(new Envelope(0, 0, 1, 1), crs);
        ImageOpenOptions options = ImageOpenOptions.defaults().withPlacement(placement);
        return RasterImages.open(
                path,
                new SourceIdentity("raster-viewer", "Raster image"),
                options,
                AwtRasterDecoders.level1());
    }

    static MapView createView(RasterSource source) {
        return createView(source, false);
    }

    static MapView createView(RasterSource source, boolean repeatGlobal) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Raster view creation must run on the event dispatch thread");
        }
        var rasterCrs =
                source.metadata()
                        .crs()
                        .flatMap(CrsMetadata::definition)
                        .orElse(CrsDefinitions.EPSG_3857);
        MapView view = new MapView(CrsRegistry.level1(), rasterCrs, rasterCrs);
        MapLayerBinding binding = null;
        try {
            view.setSize(800, 600);
            view.putClientProperty(
                    "raster-placement-label",
                    source.metadata().gridPlacement().orElseThrow().kind()
                                    == io.github.mundanej.map.api.RasterGridPlacement.Kind.AFFINE
                            ? WORLD_FILE_LABEL
                            : PLACEMENT_LABEL);
            view.putClientProperty(
                    "raster-wrap-label", repeatGlobal ? "Global repeat" : "Local extent");
            if (repeatGlobal) {
                view.setHorizontalWrap(horizontalWrap(rasterCrs));
            }
            binding = MapLayerBinding.ownedRaster("image", "Raster image", source);
            if (repeatGlobal) {
                binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
            }
            view.setLayerBindings(List.of(binding));
            binding = null;
            view.fitToData(16);
            return view;
        } catch (RuntimeException | Error failure) {
            if (binding != null) {
                closeSuppressing(binding, failure);
            } else {
                closeSuppressing(view, failure);
                if (!source.isClosed()) {
                    closeSuppressing(source, failure);
                }
            }
            throw failure;
        }
    }

    private static HorizontalWrap horizontalWrap(io.github.mundanej.map.api.CrsDefinition crs) {
        if (crs.equals(CrsDefinitions.EPSG_3857)) {
            return HorizontalWrap.webMercator();
        }
        Envelope domain = crs.coordinateDomain();
        return new HorizontalWrap(
                domain.minX(), domain.maxX(), 8, HorizontalWrap.COPY_INDEX_HARD_MAXIMUM);
    }

    private static void launch(RasterSource source, boolean repeatGlobal) {
        EventQueue.invokeLater(() -> show(source, repeatGlobal));
    }

    private static void show(RasterSource source, boolean repeatGlobal) {
        MapView view = createView(source, repeatGlobal);
        show(view, RasterViewer::showWindow);
    }

    static void show(RasterSource source, Consumer<MapView> presenter) {
        show(createView(source), presenter);
    }

    private static void show(MapView view, Consumer<MapView> presenter) {
        try {
            presenter.accept(view);
        } catch (RuntimeException | Error failure) {
            closeSuppressing(view, failure);
            throw failure;
        }
    }

    private static void showWindow(MapView view) {
        JFrame frame = new JFrame("Mundane raster viewer");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        JLabel status = new JLabel();
        frame.add(createControls(view, status), BorderLayout.NORTH);
        frame.add(status, BorderLayout.SOUTH);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        view.close();
                    }
                });
        frame.setSize(900, 650);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    static JPanel createControls(MapView view, JLabel status) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(status, "status");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Raster controls must be created on the event dispatch thread");
        }
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JComboBox<RasterInterpolation> interpolation =
                new JComboBox<>(RasterInterpolation.values());
        JSlider opacity = new JSlider(0, 100, 100);
        Runnable update =
                () -> {
                    RasterInterpolation selected =
                            Objects.requireNonNull(
                                    (RasterInterpolation) interpolation.getSelectedItem(),
                                    "selected interpolation");
                    RasterRenderOptions options =
                            new RasterRenderOptions(selected, opacity.getValue() / 100.0);
                    view.setRasterRenderOptions("image", options);
                    status.setText(
                            view.getClientProperty("raster-placement-label")
                                    + " — "
                                    + view.getClientProperty("raster-wrap-label")
                                    + " — "
                                    + selected
                                    + " — opacity "
                                    + opacity.getValue()
                                    + "%");
                };
        interpolation.addActionListener(event -> update.run());
        opacity.addChangeListener(event -> update.run());
        controls.add(new JLabel("Interpolation"));
        controls.add(interpolation);
        controls.add(new JLabel("Opacity"));
        controls.add(opacity);
        update.run();
        return controls;
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof SourceException source) {
            return diagnosticSummary(source.terminal());
        }
        if (failure instanceof IllegalArgumentException) {
            return "raster-viewer: IMAGE_VIEWER_ARGUMENT_INVALID";
        }
        return "raster-viewer: IMAGE_VIEWER_STARTUP_FAILED";
    }

    private static String diagnosticSummary(SourceDiagnostic diagnostic) {
        StringBuilder value =
                new StringBuilder("raster-viewer: ")
                        .append(diagnostic.severity())
                        .append(' ')
                        .append(diagnostic.code());
        diagnostic
                .location()
                .ifPresent(
                        location -> {
                            location.component()
                                    .ifPresent(
                                            component ->
                                                    value.append(" component=").append(component));
                            if (location.recordNumber().isPresent()) {
                                value.append(" record=")
                                        .append(location.recordNumber().getAsLong());
                            }
                            location.fieldName()
                                    .ifPresent(field -> value.append(" field=").append(field));
                            if (location.byteOffset().isPresent()) {
                                value.append(" offset=").append(location.byteOffset().getAsLong());
                            }
                        });
        if (!diagnostic.context().isEmpty()) {
            value.append(" context={")
                    .append(
                            diagnostic.context().entrySet().stream()
                                    .map(entry -> entry.getKey() + '=' + bounded(entry.getValue()))
                                    .collect(java.util.stream.Collectors.joining(", ")))
                    .append('}');
        }
        return value.toString();
    }

    private static String bounded(String value) {
        int maximum = 160;
        return value.length() <= maximum ? value : value.substring(0, maximum) + "…";
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Exception failure) {
            primary.addSuppressed(failure);
        }
    }

    record Arguments(Path path, Optional<CrsMetadata> worldFileCrs, boolean repeatGlobal) {
        Arguments {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(worldFileCrs, "worldFileCrs");
        }
    }
}
