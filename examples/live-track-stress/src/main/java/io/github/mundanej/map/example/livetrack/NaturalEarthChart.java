package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.shapefile.ShapefileLimits;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Provenance-verified Natural Earth background for the live-track stress example. */
final class NaturalEarthChart {
    static final String RESOURCE_ROOT = "/io/github/mundanej/map/example/livetrack/naturalearth/";
    static final Color OCEAN = new Color(0x08, 0x15, 0x20);
    static final Rgba LAND = Rgba.rgb(0x43, 0x5e, 0x4b);
    static final Rgba OUTLINE = Rgba.rgb(0x78, 0x90, 0x80);
    private static final String SOURCE_ID = "natural-earth-land";
    private static final String LAYER_ID = "natural-earth-land";
    private static final List<ManifestEntry> MANIFEST =
            List.of(
                    new ManifestEntry(
                            "ne_110m_land.shp",
                            89_504,
                            "8689e6932b8e370e2ca4587cf3ba21e460b1235db37b6ed3c172c35b4a6088de"),
                    new ManifestEntry(
                            "ne_110m_land.shx",
                            1_116,
                            "2719254764a70262a34333581d582d503b8af5d6626e6da4eb2b5f86e7316faa"),
                    new ManifestEntry(
                            "ne_110m_land.dbf",
                            3_431,
                            "db7cf6d2de2811df09bd7fcc6f243ab78a715b83571a0cb7b36b4e2af3297caa"),
                    new ManifestEntry(
                            "ne_110m_land.prj",
                            147,
                            "3259f0e55290a82b1350646f604e8a7ee1e2136c0320a40fad838ab40819fff8"),
                    new ManifestEntry(
                            "ne_110m_land.cpg",
                            5,
                            "3ad3031f5503a4404af825262ee8232cc04d4ea6683d42c5dd0a2f2a27ac9824"));

    private NaturalEarthChart() {}

    static List<ManifestEntry> manifest() {
        return MANIFEST;
    }

    static MaterializedDataset openDataset() {
        return openDataset(NaturalEarthChart::openClasspathResource);
    }

    static MaterializedDataset openDataset(ResourceLoader resources) {
        Objects.requireNonNull(resources, "resources");
        Path directory = createTemporaryDirectory();
        FeatureSource opened = null;
        try {
            for (ManifestEntry entry : MANIFEST) {
                copyVerified(resources, directory, entry);
            }
            Path shp = directory.resolve("ne_110m_land.shp");
            ShapefileOpenOptions options =
                    ShapefileOpenOptions.defaults()
                            .withFeatureSourceLimits(FeatureSourceLimits.LEVEL_1)
                            .withShapefileLimits(ShapefileLimits.defaults())
                            .withCrsOverride(CrsDefinitions.EPSG_4326);
            opened =
                    Shapefiles.open(
                            new SourceIdentity(SOURCE_ID, "Natural Earth 1:110m land"),
                            shp,
                            options);
            requireWgs84(opened.metadata());
            FeatureSource bounded =
                    new MercatorDomainFeatureSource(
                            opened, directory, NaturalEarthChart::deleteTree);
            opened = null;
            return new MaterializedDataset(bounded, directory);
        } catch (RuntimeException | Error failure) {
            closeSuppressing(opened, failure);
            cleanupSuppressing(directory, failure);
            throw failure;
        }
    }

    static ChartSession startHeadless() {
        return startHeadless(ignored -> {});
    }

    static ChartSession startHeadless(Consumer<String> diagnosticSink) {
        Objects.requireNonNull(diagnosticSink, "diagnosticSink");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Natural Earth loading must run off the event thread");
        }
        MaterializedDataset dataset = openDataset();
        try {
            ChartSession[] result = new ChartSession[1];
            EventQueue.invokeAndWait(() -> result[0] = start(dataset, false, diagnosticSink));
            return result[0];
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            closeSuppressing(dataset.source(), failure);
            throw new IllegalStateException("NATURAL_EARTH_START_INTERRUPTED", failure);
        } catch (InvocationTargetException failure) {
            closeSuppressing(dataset.source(), failure);
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("NATURAL_EARTH_START_FAILED", cause);
        }
    }

    static void launch() {
        launch(System.err::println);
    }

    static void launch(Consumer<String> diagnosticSink) {
        Objects.requireNonNull(diagnosticSink, "diagnosticSink");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Natural Earth loading must run off the event thread");
        }
        MaterializedDataset dataset = openDataset();
        try {
            EventQueue.invokeLater(() -> start(dataset, true, diagnosticSink));
        } catch (RuntimeException | Error failure) {
            closeSuppressing(dataset.source(), failure);
            throw failure;
        }
    }

    private static ChartSession start(
            MaterializedDataset dataset, boolean installWindow, Consumer<String> diagnosticSink) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Natural Earth chart must start on the event thread");
        }
        FeatureSource source = dataset.source();
        MapView view = null;
        JFrame frame = null;
        boolean transferred = false;
        try {
            view =
                    new MapView(
                            CrsRegistry.level1(),
                            CrsDefinitions.EPSG_3857,
                            CrsDefinitions.EPSG_3857);
            view.setPreferredSize(new Dimension(1_200, 700));
            view.setBackground(OCEAN);
            view.setOpaque(true);
            view.addMapSourceReportListener(
                    event ->
                            event.current()
                                    .ifPresent(
                                            current ->
                                                    report(
                                                            "layer=" + event.layerId(),
                                                            current,
                                                            diagnosticSink)));
            MapLayerBinding binding =
                    MapLayerBinding.ownedFeature(
                            LAYER_ID, "Natural Earth land", source, marker(), outline(), fill());
            view.setLayerBindings(List.of(binding));
            transferred = true;
            view.fitToData(24.0);
            if (installWindow) {
                frame = installWindow(view);
            }
            return new ChartSession(
                    view,
                    Optional.ofNullable(frame),
                    source,
                    dataset.directory(),
                    source.metadata(),
                    source.openingDiagnostics());
        } catch (RuntimeException | Error failure) {
            if (frame != null) {
                frame.dispose();
            }
            if (transferred && view != null) {
                closeSuppressing(view, failure);
            } else {
                closeSuppressing(source, failure);
                closeSuppressing(view, failure);
            }
            throw failure;
        }
    }

    private static JFrame installWindow(MapView view) {
        JFrame frame = new JFrame("mundane-java-map — live-track stress chart");
        try {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.add(view, BorderLayout.CENTER);
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent event) {
                            try {
                                view.close();
                            } finally {
                                frame.dispose();
                            }
                        }
                    });
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            return frame;
        } catch (RuntimeException | Error failure) {
            frame.dispose();
            throw failure;
        }
    }

    private static VectorMarkerSymbol marker() {
        return BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, LAND, 4.0, 1.0);
    }

    private static SolidLineSymbol outline() {
        return SolidLineSymbol.of(
                new SymbolStroke(OUTLINE, new SymbolLength(0.75, SymbolUnit.SCREEN_PIXEL)), 1.0);
    }

    private static SolidFillSymbol fill() {
        return SolidFillSymbol.of(LAND, Optional.of(outline()), 1.0);
    }

    private static void report(
            String phase, DiagnosticReport report, Consumer<String> diagnosticSink) {
        for (var diagnostic : report.entries()) {
            diagnosticSink.accept(
                    "natural-earth "
                            + phase
                            + ' '
                            + diagnostic.severity()
                            + ' '
                            + diagnostic.code()
                            + ": "
                            + diagnostic.message());
        }
        if (report.omittedWarningCount() > 0) {
            diagnosticSink.accept(
                    "natural-earth " + phase + " WARNING OMITTED: " + report.omittedWarningCount());
        }
    }

    private static InputStream openClasspathResource(String name) throws IOException {
        InputStream stream = NaturalEarthChart.class.getResourceAsStream(RESOURCE_ROOT + name);
        if (stream == null) {
            throw new IOException("resource absent");
        }
        return stream;
    }

    private static Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("mundane-map-natural-earth-");
        } catch (IOException failure) {
            throw resourceFailure(
                    "NATURAL_EARTH_TEMP_CREATE_FAILED", "Unable to stage chart", Map.of(), failure);
        }
    }

    private static void copyVerified(
            ResourceLoader resources, Path directory, ManifestEntry entry) {
        MessageDigest digest = sha256();
        long total = 0;
        Path target = directory.resolve(entry.name());
        try (InputStream input = resources.open(entry.name());
                OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8_192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total = Math.addExact(total, count);
                if (total > entry.size()) {
                    throw resourceFailure(
                            "NATURAL_EARTH_RESOURCE_SIZE_MISMATCH",
                            "Bundled chart resource has an unexpected size",
                            Map.of("resource", entry.name()),
                            null);
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        } catch (NaturalEarthResourceException failure) {
            throw failure;
        } catch (IOException | ArithmeticException failure) {
            throw resourceFailure(
                    "NATURAL_EARTH_RESOURCE_READ_FAILED",
                    "Bundled chart resource could not be read",
                    Map.of("resource", entry.name()),
                    failure);
        }
        if (total != entry.size()) {
            throw resourceFailure(
                    "NATURAL_EARTH_RESOURCE_SIZE_MISMATCH",
                    "Bundled chart resource has an unexpected size",
                    Map.of("resource", entry.name()),
                    null);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(entry.sha256())) {
            throw resourceFailure(
                    "NATURAL_EARTH_RESOURCE_HASH_MISMATCH",
                    "Bundled chart resource failed integrity verification",
                    Map.of("resource", entry.name()),
                    null);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required by Java 21", failure);
        }
    }

    private static void requireWgs84(FeatureSourceMetadata metadata) {
        String identifier = metadata.crs().flatMap(value -> value.canonicalIdentifier()).orElse("");
        if (!identifier.equals(CrsDefinitions.EPSG_4326.canonicalIdentifier())) {
            throw resourceFailure(
                    "NATURAL_EARTH_CRS_UNRECOGNIZED",
                    "Bundled chart CRS is not recognized as EPSG:4326",
                    Map.of(),
                    null);
        }
    }

    private static NaturalEarthResourceException resourceFailure(
            String code, String message, Map<String, String> context, Throwable cause) {
        return new NaturalEarthResourceException(code, message, context, cause);
    }

    private static void deleteTree(Path directory) {
        IOException primary = null;
        for (ManifestEntry entry : MANIFEST.reversed()) {
            try {
                Files.deleteIfExists(directory.resolve(entry.name()));
            } catch (IOException failure) {
                if (primary == null) {
                    primary = failure;
                } else {
                    primary.addSuppressed(failure);
                }
            }
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException failure) {
            if (primary == null) {
                primary = failure;
            } else {
                primary.addSuppressed(failure);
            }
        }
        if (primary != null) {
            throw resourceFailure(
                    "NATURAL_EARTH_CLEANUP_FAILED",
                    "Unable to remove staged chart resources",
                    Map.of(),
                    primary);
        }
    }

    private static void cleanupSuppressing(Path directory, Throwable primary) {
        try {
            deleteTree(directory);
        } catch (RuntimeException cleanup) {
            primary.addSuppressed(cleanup);
        }
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (RuntimeException | Error closeFailure) {
            primary.addSuppressed(closeFailure);
        } catch (Exception closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }

    record ManifestEntry(String name, long size, String sha256) {
        ManifestEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    record MaterializedDataset(FeatureSource source, Path directory) {
        MaterializedDataset {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(directory, "directory");
        }
    }

    @FunctionalInterface
    interface ResourceLoader {
        InputStream open(String name) throws IOException;
    }

    @SuppressWarnings("serial")
    static final class NaturalEarthResourceException extends IllegalStateException {
        private final String code;
        private final Map<String, String> context;

        NaturalEarthResourceException(
                String code, String message, Map<String, String> context, Throwable cause) {
            super(code + ": " + message, cause);
            this.code = Objects.requireNonNull(code, "code");
            this.context = Map.copyOf(Objects.requireNonNull(context, "context"));
        }

        String code() {
            return code;
        }

        Map<String, String> context() {
            return context;
        }
    }

    static final class ChartSession implements AutoCloseable {
        private final MapView view;
        private final Optional<JFrame> frame;
        private final FeatureSource source;
        private final Path materializedDirectory;
        private final FeatureSourceMetadata metadata;
        private final DiagnosticReport openingDiagnostics;

        ChartSession(
                MapView view,
                Optional<JFrame> frame,
                FeatureSource source,
                Path materializedDirectory,
                FeatureSourceMetadata metadata,
                DiagnosticReport openingDiagnostics) {
            this.view = Objects.requireNonNull(view, "view");
            this.frame = Objects.requireNonNull(frame, "frame");
            this.source = Objects.requireNonNull(source, "source");
            this.materializedDirectory =
                    Objects.requireNonNull(materializedDirectory, "materializedDirectory");
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.openingDiagnostics =
                    Objects.requireNonNull(openingDiagnostics, "openingDiagnostics");
        }

        MapView view() {
            return view;
        }

        FeatureSourceMetadata metadata() {
            return metadata;
        }

        DiagnosticReport openingDiagnostics() {
            return openingDiagnostics;
        }

        Path materializedDirectory() {
            return materializedDirectory;
        }

        boolean sourceClosed() {
            return source.isClosed();
        }

        @Override
        public void close() {
            Runnable close =
                    () -> {
                        try {
                            view.close();
                        } finally {
                            frame.ifPresent(JFrame::dispose);
                        }
                    };
            if (EventQueue.isDispatchThread()) {
                close.run();
                return;
            }
            try {
                EventQueue.invokeAndWait(close);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("NATURAL_EARTH_CLOSE_INTERRUPTED", failure);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("NATURAL_EARTH_CLOSE_FAILED", cause);
            }
        }
    }
}
