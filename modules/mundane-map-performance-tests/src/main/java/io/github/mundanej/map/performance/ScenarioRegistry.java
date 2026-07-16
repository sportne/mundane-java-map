package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
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
import io.github.mundanej.map.core.FeatureIndexLimits;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.image.ImageCachePolicy;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;

final class ScenarioRegistry {
    private ScenarioRegistry() {}

    static List<EvidenceScenario> create(EvidenceConfiguration.Profile profile, Path workspace)
            throws Exception {
        return create(profile, workspace, Optional.empty());
    }

    static List<EvidenceScenario> create(
            EvidenceConfiguration.Profile profile, Path workspace, Optional<String> selected)
            throws Exception {
        return create(profile, workspace, selected, ScenarioRegistry::source);
    }

    static List<EvidenceScenario> create(
            EvidenceConfiguration.Profile profile,
            Path workspace,
            FeatureSourceFactory sourceFactory)
            throws Exception {
        return create(profile, workspace, Optional.empty(), sourceFactory);
    }

    static List<EvidenceScenario> create(
            EvidenceConfiguration.Profile profile,
            Path workspace,
            Optional<String> selected,
            FeatureSourceFactory sourceFactory)
            throws Exception {
        return create(profile, workspace, selected, sourceFactory, null);
    }

    static List<EvidenceScenario> createForConstructionTest(
            EvidenceConfiguration.Profile profile, Path workspace, ScenarioAppender appender)
            throws Exception {
        return create(profile, workspace, Optional.empty(), ScenarioRegistry::source, appender);
    }

    private static List<EvidenceScenario> create(
            EvidenceConfiguration.Profile profile,
            Path workspace,
            Optional<String> selected,
            FeatureSourceFactory sourceFactory,
            ScenarioAppender appender)
            throws Exception {
        RasterGridFixture rasters = null;
        ShapefileGridFixture shapefile = null;
        List<EvidenceScenario> result = new ArrayList<>();
        try {
            if (appender == null) {
                if (needsRasterFixture(selected)) {
                    rasters = RasterGridFixture.create(workspace);
                }
                if (needsShapefileFixture(selected)) {
                    shapefile = ShapefileGridFixture.create(workspace, profile);
                }
                appendDefaultScenarios(
                        result, profile, selected, rasters, shapefile, workspace, sourceFactory);
            } else {
                appender.append(result);
            }
            return List.copyOf(result);
        } catch (Throwable failure) {
            closeScenariosAfterFailure(result, failure);
            closeAfterConstructionFailure(rasters, failure);
            closeAfterConstructionFailure(shapefile, failure);
            deleteAfterConstructionFailure(workspace, failure);
            if (failure instanceof Exception exception) {
                throw exception;
            }
            throw (Error) failure;
        }
    }

    @FunctionalInterface
    interface FeatureSourceFactory {
        FeatureSource open(String id, List<FeatureRecord> records);
    }

    @FunctionalInterface
    interface ScenarioAppender {
        void append(List<EvidenceScenario> scenarios) throws Exception;
    }

    private static void appendDefaultScenarios(
            List<EvidenceScenario> result,
            EvidenceConfiguration.Profile profile,
            Optional<String> selected,
            RasterGridFixture rasters,
            ShapefileGridFixture shapefile,
            Path workspace,
            FeatureSourceFactory sourceFactory) {
        if (selected(selected, "memory-query-full")) {
            result.add(
                    new FullQueryScenario(
                            profile,
                            FixtureCatalog.featureGrid(profile),
                            requireFixture(rasters),
                            requireFixture(shapefile),
                            workspace));
        }
        if (selected(selected, "memory-query-window")) {
            result.add(new WindowQueryScenario(profile, FixtureCatalog.featureGrid(profile)));
        }
        if (selected(selected, "dense-vector-render")) {
            result.add(
                    new SourceVectorRenderScenario(
                            profile,
                            "dense-vector-render",
                            FixtureCatalog.vectorRecords(profile),
                            1,
                            "G7-003",
                            sourceFactory));
        }
        if (selected(selected, "symbol-heavy-render")) {
            result.add(
                    new VectorRenderScenario(
                            profile,
                            "symbol-heavy-render",
                            FixtureCatalog.symbolLayers(profile),
                            1,
                            "G7-004"));
        }
        if (selected(selected, "hit-test-sweep")) {
            result.add(new HitScenario(profile));
        }
        if (selected(selected, "shapefile-query-window")) {
            result.add(new ShapefileQueryScenario(profile, requireFixture(shapefile)));
        }
        if (selected(selected, "shapefile-render-window")) {
            result.add(new ShapefileRenderScenario(profile, requireFixture(shapefile)));
        }
        if (selected(selected, "png-window-bilinear-disabled")) {
            result.add(new RasterReadScenario(profile, requireFixture(rasters), true, false));
        }
        if (selected(selected, "jpeg-window-bilinear-preseeded")) {
            result.add(new RasterReadScenario(profile, requireFixture(rasters), false, true));
        }
        if (selected(selected, "affine-raster-pan")) {
            result.add(new RasterPanScenario(profile, requireFixture(rasters)));
        }
        if (selected(selected, "vector-pan-sequence")) {
            result.add(
                    new NavigationScenario(
                            profile, FixtureCatalog.vectorRecords(profile), false, sourceFactory));
        }
        if (selected(selected, "vector-zoom-sequence")) {
            result.add(
                    new NavigationScenario(
                            profile, FixtureCatalog.vectorRecords(profile), true, sourceFactory));
        }
        appendIndexScenarios(result, profile, selected);
    }

    private static void appendIndexScenarios(
            List<EvidenceScenario> result,
            EvidenceConfiguration.Profile profile,
            Optional<String> selected) {
        for (int size : List.of(128, 8_192, 131_072)) {
            if (selected(selected, "index-build-" + size)) {
                result.add(new IndexBuildScenario(profile, size));
            }
        }
        for (int size : IndexComparisonFixture.SIZES) {
            if (selected(selected, "index-query-linear-" + size)) {
                result.add(new IndexQueryScenario(profile, size, false));
            }
            if (selected(selected, "index-query-str16-" + size)) {
                result.add(new IndexQueryScenario(profile, size, true));
            }
        }
        if (selected(selected, "memory-query-window-indexed")) {
            result.add(
                    new WindowQueryScenario(
                            profile,
                            "memory-query-window-indexed",
                            "memory-query-window",
                            "no change",
                            FixtureCatalog.featureGrid(profile),
                            ScenarioRegistry::indexedSource));
        }
        if (selected(selected, "hit-test-sweep-indexed")) {
            result.add(
                    new HitScenario(
                            profile,
                            "hit-test-sweep-indexed",
                            "hit-test-sweep",
                            "G7-004",
                            ScenarioRegistry::indexedSource));
        }
        if (selected(selected, "dense-vector-render-indexed")) {
            result.add(
                    new SourceVectorRenderScenario(
                            profile,
                            "dense-vector-render-indexed",
                            "dense-vector-render",
                            FixtureCatalog.vectorRecords(profile),
                            1,
                            "G7-003",
                            ScenarioRegistry::indexedSource));
        }
        if (selected(selected, "vector-pan-sequence-indexed")) {
            result.add(
                    new NavigationScenario(
                            profile,
                            "vector-pan-sequence-indexed",
                            "vector-pan-sequence",
                            FixtureCatalog.vectorRecords(profile),
                            false,
                            "G7-003/G7-004",
                            ScenarioRegistry::indexedSource));
        }
        if (selected(selected, "vector-zoom-sequence-indexed")) {
            result.add(
                    new NavigationScenario(
                            profile,
                            "vector-zoom-sequence-indexed",
                            "vector-zoom-sequence",
                            FixtureCatalog.vectorRecords(profile),
                            true,
                            "G7-003/G7-004",
                            ScenarioRegistry::indexedSource));
        }
    }

    private static boolean selected(Optional<String> selected, String id) {
        return selected.isEmpty() || selected.orElseThrow().equals(id);
    }

    private static boolean needsRasterFixture(Optional<String> selected) {
        return selected.isEmpty()
                || selected(selected, "memory-query-full")
                || selected(selected, "png-window-bilinear-disabled")
                || selected(selected, "jpeg-window-bilinear-preseeded")
                || selected(selected, "affine-raster-pan");
    }

    private static boolean needsShapefileFixture(Optional<String> selected) {
        return selected.isEmpty()
                || selected(selected, "memory-query-full")
                || selected(selected, "shapefile-query-window")
                || selected(selected, "shapefile-render-window");
    }

    private static <T> T requireFixture(T fixture) {
        return java.util.Objects.requireNonNull(fixture, "selected fixture");
    }

    static List<String> ids() {
        List<String> result =
                new ArrayList<>(
                        List.of(
                                "memory-query-full",
                                "memory-query-window",
                                "dense-vector-render",
                                "symbol-heavy-render",
                                "hit-test-sweep",
                                "shapefile-query-window",
                                "shapefile-render-window",
                                "png-window-bilinear-disabled",
                                "jpeg-window-bilinear-preseeded",
                                "affine-raster-pan",
                                "vector-pan-sequence",
                                "vector-zoom-sequence"));
        result.add("index-build-128");
        result.add("index-build-8192");
        result.add("index-build-131072");
        for (int size : IndexComparisonFixture.SIZES) {
            result.add("index-query-linear-" + size);
            result.add("index-query-str16-" + size);
        }
        result.add("memory-query-window-indexed");
        result.add("hit-test-sweep-indexed");
        result.add("dense-vector-render-indexed");
        result.add("vector-pan-sequence-indexed");
        result.add("vector-zoom-sequence-indexed");
        return List.copyOf(result);
    }

    private abstract static class BaseScenario implements EvidenceScenario {
        final EvidenceConfiguration.Profile profile;
        final String id;
        final String next;
        final long operations;
        final String unit;
        final String sourceCache;
        final EvidenceObservation expected;
        final String semanticId;

        BaseScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                String next,
                long operations,
                String unit,
                String sourceCache,
                Map<String, Long> counters) {
            this(profile, id, id, next, operations, unit, sourceCache, counters);
        }

        BaseScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                String semanticId,
                String next,
                long operations,
                String unit,
                String sourceCache,
                Map<String, Long> counters) {
            this.profile = profile;
            this.id = id;
            this.semanticId = semanticId;
            this.next = next;
            this.operations = operations;
            this.unit = unit;
            this.sourceCache = sourceCache;
            this.expected = ScenarioOracleV1.expected(profile, semanticId, counters);
        }

        @Override
        public final String id() {
            return id;
        }

        @Override
        public final String nextExperiment() {
            return next;
        }

        @Override
        public final long batchOperations() {
            return operations;
        }

        @Override
        public final String workUnit() {
            return unit;
        }

        @Override
        public final String sourceCacheState() {
            return sourceCache;
        }

        @Override
        public final ScenarioOracle oracle() {
            return ScenarioOracleV1.exact(expected);
        }

        final EvidenceObservation observation(
                Map<String, Long> counters, java.util.function.Consumer<FnvOracle> content) {
            return ObservationDigests.observation(profile, semanticId, counters, content);
        }
    }

    private static final class IndexBuildScenario extends BaseScenario {
        private final int size;
        private List<FeatureRecord> records;
        private InMemoryFeatureSource built;

        IndexBuildScenario(EvidenceConfiguration.Profile profile, int size) {
            super(
                    profile,
                    "index-build-" + size,
                    "no change",
                    size,
                    "recordsIndexed",
                    "NOT_APPLICABLE",
                    buildCounters(size));
            this.size = size;
        }

        @Override
        public void setupScenario() {
            records = IndexComparisonFixture.records(size);
        }

        @Override
        public void prepareSample() {
            built = null;
        }

        @Override
        public void runTimedBatch() {
            built = indexedComparisonSource(id, records);
        }

        @Override
        public EvidenceObservation observeSample() {
            built.close();
            built = null;
            return observation(buildCounters(size), digest -> {});
        }

        @Override
        public void finishSample() {
            if (built != null) {
                built.close();
                built = null;
            }
        }
    }

    static final class IndexQueryScenario extends BaseScenario {
        private final int size;
        private final boolean indexed;
        private final Map<String, Long> counters;
        private final FeatureSourceFactory sourceFactory;
        private List<FeatureRecord> records;
        private List<FeatureQuery> queries;
        private int[] expectedOrdinals;
        private FeatureSource source;
        private List<FeatureRecord> actual;

        IndexQueryScenario(EvidenceConfiguration.Profile profile, int size, boolean indexed) {
            this(
                    profile,
                    size,
                    indexed,
                    indexed
                            ? ScenarioRegistry::indexedComparisonSource
                            : ScenarioRegistry::linearComparisonSource);
        }

        IndexQueryScenario(
                EvidenceConfiguration.Profile profile,
                int size,
                boolean indexed,
                FeatureSourceFactory sourceFactory) {
            super(
                    profile,
                    "index-query-" + (indexed ? "str16-" : "linear-") + size,
                    "no change",
                    profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 24,
                    "queries",
                    "NOT_APPLICABLE",
                    queryCounters(
                            size,
                            profile,
                            indexed,
                            indexed
                                    ? IndexComparisonFixture.referenceCandidateTotal(size, profile)
                                    : 0));
            this.size = size;
            this.indexed = indexed;
            this.sourceFactory = sourceFactory;
            counters = expected.counters();
        }

        @Override
        public void setupScenario() {
            records = IndexComparisonFixture.records(size);
            queries =
                    IndexComparisonFixture.viewports(size, profile).stream()
                            .map(
                                    bounds ->
                                            new FeatureQuery(
                                                    Optional.of(bounds),
                                                    AttributeSelection.ALL,
                                                    Optional.empty()))
                            .toList();
            expectedOrdinals = IndexComparisonFixture.selectedOrdinals(size, profile);
            source = sourceFactory.open(id, records);
            if (indexed) {
                long actual = inferProductionCandidateTotal(source, queries, size);
                require(
                        actual == counters.get("indexedCandidates"),
                        "Production candidate total differs from the independent reference");
            }
        }

        @Override
        public void prepareSample() {
            actual = new ArrayList<>(expectedOrdinals.length);
        }

        @Override
        public void runTimedBatch() {
            for (FeatureQuery query : queries) {
                try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
                    while (cursor.advance()) {
                        actual.add(cursor.current());
                    }
                }
            }
        }

        @Override
        public EvidenceObservation observeSample() {
            require(
                    actual.size() == expectedOrdinals.length,
                    "Index comparison query count changed");
            for (int ordinal = 0; ordinal < expectedOrdinals.length; ordinal++) {
                require(
                        actual.get(ordinal).equals(records.get(expectedOrdinals[ordinal])),
                        "Index query record order or value changed");
            }
            return observation(
                    counters,
                    digest -> {
                        for (FeatureRecord record : actual) {
                            ObservationDigests.addRecord(digest, record);
                        }
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void finishSample() {
            actual = null;
        }

        int retainedCaptureCount() {
            return actual == null ? 0 : actual.size();
        }

        @Override
        public void close() {
            if (source != null) {
                source.close();
            }
        }
    }

    private static final class FullQueryScenario extends BaseScenario {
        private final InMemoryFeatureSource source;
        private final RasterGridFixture rasters;
        private final ShapefileGridFixture shapefile;
        private final Path workspace;
        private FeatureRecord[] actual;

        FullQueryScenario(
                EvidenceConfiguration.Profile profile,
                List<FeatureRecord> records,
                RasterGridFixture rasters,
                ShapefileGridFixture shapefile,
                Path workspace) {
            super(
                    profile,
                    "memory-query-full",
                    "no change",
                    records.size(),
                    "records",
                    "NOT_APPLICABLE",
                    counters("records", records.size(), "coordinates", records.size()));
            source = source("feature-grid-full", records);
            this.rasters = rasters;
            this.shapefile = shapefile;
            this.workspace = workspace;
        }

        @Override
        public void prepareSample() {
            actual = new FeatureRecord[Math.toIntExact(operations)];
        }

        @Override
        public void runTimedBatch() {
            int records = 0;
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                while (cursor.advance()) {
                    actual[records++] = cursor.current();
                }
            }
            require(records == operations, "Full query count changed");
        }

        @Override
        public EvidenceObservation observeSample() {
            Map<String, Long> counters =
                    counters("records", actual.length, "coordinates", actual.length);
            return observation(
                    counters,
                    digest -> {
                        for (FeatureRecord record : actual) {
                            ObservationDigests.addRecord(digest, record);
                        }
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void finishSample() {
            actual = null;
        }

        @Override
        public void close() {
            Throwable primary = null;
            try {
                source.close();
            } catch (Throwable failure) {
                primary = failure;
            }
            primary = closeOwned(rasters, primary);
            primary = closeOwned(shapefile, primary);
            try {
                deleteWorkspace(workspace);
            } catch (Throwable failure) {
                primary = suppress(primary, failure);
            }
            if (primary instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (primary instanceof Error error) {
                throw error;
            }
            if (primary != null) {
                throw new java.io.UncheckedIOException((IOException) primary);
            }
        }
    }

    private static final class WindowQueryScenario extends BaseScenario {
        private final FeatureSource source;
        private final int side;
        private FeatureRecord[] actual;

        WindowQueryScenario(EvidenceConfiguration.Profile profile, List<FeatureRecord> records) {
            this(
                    profile,
                    "memory-query-window",
                    "memory-query-window",
                    "G7-002",
                    records,
                    ScenarioRegistry::source);
        }

        WindowQueryScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                String semanticId,
                String next,
                List<FeatureRecord> records,
                FeatureSourceFactory factory) {
            super(
                    profile,
                    id,
                    semanticId,
                    next,
                    profile == EvidenceConfiguration.Profile.BASELINE ? 2_048 : 128,
                    "records",
                    "NOT_APPLICABLE",
                    counters(
                            "records",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 2_048 : 128,
                            "coordinates",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 2_048 : 128));
            source = factory.open(id, records);
            side = profile == EvidenceConfiguration.Profile.BASELINE ? 16 : 4;
        }

        @Override
        public void prepareSample() {
            actual = new FeatureRecord[Math.toIntExact(operations)];
        }

        @Override
        public void runTimedBatch() {
            int count = 0;
            for (int[] origin : windowOrigins(profile)) {
                Envelope bounds =
                        new Envelope(
                                (origin[0] - 0.5) * 1_000.0,
                                (origin[1] - 0.5) * 1_000.0,
                                (origin[0] + side - 0.5) * 1_000.0,
                                (origin[1] + side - 0.5) * 1_000.0);
                FeatureQuery query =
                        new FeatureQuery(
                                Optional.of(bounds), AttributeSelection.ALL, Optional.empty());
                try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
                    while (cursor.advance()) {
                        actual[count++] = cursor.current();
                    }
                }
            }
            require(count == operations, "Window-query count changed");
        }

        @Override
        public EvidenceObservation observeSample() {
            Map<String, Long> counters =
                    counters("records", actual.length, "coordinates", actual.length);
            return observation(
                    counters,
                    digest -> {
                        for (FeatureRecord record : actual) {
                            ObservationDigests.addRecord(digest, record);
                        }
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void finishSample() {
            actual = null;
        }

        @Override
        public void close() {
            source.close();
        }
    }

    private static class VectorRenderScenario extends BaseScenario {
        final List<Layer> layers;
        MapView view;
        MapViewport expectedViewport;
        BufferedImage surface;

        VectorRenderScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                List<InMemoryLayer> layers,
                int frames,
                String next) {
            super(
                    profile,
                    id,
                    next,
                    frames,
                    "frames",
                    "NOT_APPLICABLE",
                    counters(
                            "frames",
                            frames,
                            "features",
                            layers.stream().mapToLong(item -> item.features().size()).sum(),
                            "portableInvariants",
                            6));
            this.layers = List.copyOf(layers);
        }

        @Override
        public boolean runsOnEdt() {
            return true;
        }

        @Override
        public void setupScenario() throws Exception {
            onEdt(
                    () -> {
                        view = view();
                        view.setSize(800, 600);
                        view.setLayers(layers);
                        view.fitToData(24.0);
                        expectedViewport = view.viewport();
                        surface = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
                    });
        }

        @Override
        public void prepareSample() throws Exception {
            onEdt(
                    () -> {
                        clear(surface);
                        view.fitToData(24.0);
                    });
        }

        @Override
        public void runTimedBatch() {
            paint(view, surface);
        }

        @Override
        public EvidenceObservation observeSample() {
            long features = layers.stream().mapToLong(item -> item.features().size()).sum();
            Map<String, Long> counters =
                    counters("frames", operations, "features", features, "portableInvariants", 6);
            ObservationDigests.RenderInvariants render =
                    ObservationDigests.renderInvariants(surface, view.viewport(), expectedViewport);
            return observation(
                    counters,
                    digest -> {
                        for (Layer layer : layers) {
                            digest.add(layer.id()).add(layer.name());
                            for (Feature feature : layer.features()) {
                                ObservationDigests.addFeature(digest, feature);
                            }
                        }
                        ObservationDigests.addRenderInvariants(digest, render);
                    });
        }

        @Override
        public void close() {
            if (view != null) {
                onEdtUnchecked(view::close);
            }
        }
    }

    private static class SourceVectorRenderScenario extends BaseScenario {
        final List<FeatureRecord> declaredRecords;
        final FeatureSourceFactory sourceFactory;
        FeatureSource source;
        MapView view;
        MapViewport expectedViewport;
        BufferedImage surface;

        SourceVectorRenderScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                List<FeatureRecord> records,
                int frames,
                String next,
                FeatureSourceFactory sourceFactory) {
            this(profile, id, id, records, frames, next, sourceFactory);
        }

        SourceVectorRenderScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                String semanticId,
                List<FeatureRecord> records,
                int frames,
                String next,
                FeatureSourceFactory sourceFactory) {
            super(
                    profile,
                    id,
                    semanticId,
                    next,
                    frames,
                    "frames",
                    "NOT_APPLICABLE",
                    counters(
                            "frames", frames, "features", records.size(), "portableInvariants", 6));
            declaredRecords = List.copyOf(records);
            this.sourceFactory = java.util.Objects.requireNonNull(sourceFactory, "sourceFactory");
        }

        @Override
        public boolean runsOnEdt() {
            return true;
        }

        @Override
        public void setupScenario() throws Exception {
            source = sourceFactory.open(id + "-linear", declaredRecords);
            onEdt(
                    () -> {
                        view = view();
                        view.setSize(800, 600);
                        view.setLayerBindings(List.of(binding(id, source)));
                        view.fitToData(24.0);
                        expectedViewport = view.viewport();
                        surface = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
                    });
        }

        @Override
        public void prepareSample() throws Exception {
            onEdt(
                    () -> {
                        clear(surface);
                        view.fitToData(24.0);
                    });
        }

        @Override
        public void runTimedBatch() {
            paint(view, surface);
        }

        @Override
        public EvidenceObservation observeSample() {
            return sourceObservation(view.viewport());
        }

        final EvidenceObservation sourceObservation(MapViewport actualViewport) {
            List<FeatureRecord> actualRecords = readAll(source);
            Map<String, Long> counters =
                    counters(
                            "frames",
                            operations,
                            "features",
                            actualRecords.size(),
                            "portableInvariants",
                            6);
            ObservationDigests.RenderInvariants render =
                    ObservationDigests.renderInvariants(surface, actualViewport, expectedViewport);
            return observation(
                    counters,
                    digest -> {
                        for (FeatureRecord record : actualRecords) {
                            ObservationDigests.addRecord(digest, record);
                        }
                        ObservationDigests.addRenderInvariants(digest, render);
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void close() {
            closeActions(
                    () -> {
                        if (view != null) {
                            onEdtUnchecked(view::close);
                        }
                    },
                    () -> {
                        if (source != null) {
                            source.close();
                        }
                    });
        }
    }

    private static final class HitScenario extends BaseScenario {
        private final List<FeatureSource> sources = new ArrayList<>();
        private final FeatureSourceFactory sourceFactory;
        private MapView view;
        private List<MapHit>[] actualHits;

        HitScenario(EvidenceConfiguration.Profile profile) {
            this(
                    profile,
                    "hit-test-sweep",
                    "hit-test-sweep",
                    "G7-002/G7-004",
                    ScenarioRegistry::source);
        }

        HitScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                String semanticId,
                String next,
                FeatureSourceFactory sourceFactory) {
            super(
                    profile,
                    id,
                    semanticId,
                    next,
                    profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32,
                    "probes",
                    "NOT_APPLICABLE",
                    counters(
                            "probes",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32,
                            "hits",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 128 : 16,
                            "misses",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 128 : 16));
            this.sourceFactory = sourceFactory;
        }

        @Override
        public boolean runsOnEdt() {
            return true;
        }

        @Override
        public void setupScenario() throws Exception {
            for (int binding = 0; binding < 4; binding++) {
                sources.add(
                        sourceFactory.open(
                                id + "-" + binding, FixtureCatalog.hitRecords(binding, profile)));
            }
            onEdt(
                    () -> {
                        List<MapLayerBinding> bindings = new ArrayList<>();
                        for (int index = 0; index < sources.size(); index++) {
                            bindings.add(binding("hit-layer-" + index, sources.get(index)));
                        }
                        view = view();
                        view.setSize(800, 600);
                        view.setLayerBindings(bindings);
                        view.setViewport(new MapViewport(800, 600, 75_000, 75_000, 300));
                    });
        }

        @Override
        public void prepareSample() throws Exception {
            @SuppressWarnings("unchecked")
            List<MapHit>[] prepared = (List<MapHit>[]) new List<?>[Math.toIntExact(operations)];
            actualHits = prepared;
            onEdt(() -> view.setViewport(new MapViewport(800, 600, 75_000, 75_000, 300)));
        }

        @Override
        public void runTimedBatch() {
            int probes = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32;
            for (int ordinal = 0; ordinal < probes; ordinal++) {
                int logical =
                        profile == EvidenceConfiguration.Profile.BASELINE
                                ? ordinal
                                : ordinal / 8 * 64 + ordinal % 8;
                Coordinate map =
                        new Coordinate(
                                10_000.0 * (logical % 16), 10_000.0 * Math.floorDiv(logical, 16));
                Coordinate screen = view.mapToScreen(map).orElseThrow();
                actualHits[ordinal] = view.hitTest(screen.x(), screen.y(), 0.0).hits();
            }
        }

        @Override
        public EvidenceObservation observeSample() {
            long hits = java.util.Arrays.stream(actualHits).filter(item -> !item.isEmpty()).count();
            Map<String, Long> counters =
                    counters(
                            "probes",
                            actualHits.length,
                            "hits",
                            hits,
                            "misses",
                            Math.subtractExact(actualHits.length, hits));
            return observation(
                    counters,
                    digest -> {
                        for (List<MapHit> result : actualHits) {
                            ObservationDigests.addHits(digest, result);
                        }
                        for (FeatureSource source : sources) {
                            ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                        }
                    });
        }

        @Override
        public void finishSample() {
            actualHits = null;
        }

        @Override
        public void close() {
            List<CloseAction> actions = new ArrayList<>();
            actions.add(
                    () -> {
                        if (view != null) {
                            onEdtUnchecked(view::close);
                        }
                    });
            for (FeatureSource source : sources) {
                actions.add(source::close);
            }
            closeActions(actions.toArray(CloseAction[]::new));
        }
    }

    private static final class ShapefileQueryScenario extends BaseScenario {
        private final ShapefileGridFixture fixture;
        private FeatureSource source;
        private FeatureRecord[] actual;

        ShapefileQueryScenario(
                EvidenceConfiguration.Profile profile, ShapefileGridFixture fixture) {
            super(
                    profile,
                    "shapefile-query-window",
                    "no change",
                    profile == EvidenceConfiguration.Profile.BASELINE ? 800 : 200,
                    "records",
                    "NOT_APPLICABLE",
                    counters(
                            "records",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 800 : 200,
                            "coordinates",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 800 : 200,
                            "attributes",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 1_600 : 400));
            this.fixture = fixture;
        }

        @Override
        public void setupScenario() {
            source = openShapefile(fixture);
        }

        @Override
        public void prepareSample() {
            actual = new FeatureRecord[Math.toIntExact(operations)];
        }

        @Override
        public void runTimedBatch() {
            int count = 0;
            int window = profile == EvidenceConfiguration.Profile.BASELINE ? 10 : 5;
            int[][] origins = shapefileOrigins(profile);
            for (int[] origin : origins) {
                Envelope bounds =
                        new Envelope(
                                (origin[0] - 0.5) * 1_000.0,
                                (origin[1] - 0.5) * 1_000.0,
                                (origin[0] + window - 0.5) * 1_000.0,
                                (origin[1] + window - 0.5) * 1_000.0);
                FeatureQuery query =
                        new FeatureQuery(
                                Optional.of(bounds), AttributeSelection.ALL, Optional.empty());
                try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
                    while (cursor.advance()) {
                        actual[count++] = cursor.current();
                    }
                }
            }
            require(count == operations, "Shapefile window count changed");
        }

        @Override
        public EvidenceObservation observeSample() {
            long attributes =
                    java.util.Arrays.stream(actual)
                            .mapToLong(record -> record.attributes().size())
                            .sum();
            Map<String, Long> counters =
                    counters(
                            "records",
                            actual.length,
                            "coordinates",
                            actual.length,
                            "attributes",
                            attributes);
            return observation(
                    counters,
                    digest -> {
                        for (FeatureRecord record : actual) {
                            ObservationDigests.addRecord(digest, record);
                        }
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void finishSample() {
            actual = null;
        }

        @Override
        public void close() {
            if (source != null) {
                source.close();
            }
        }
    }

    private static final class ShapefileRenderScenario extends BaseScenario {
        private final ShapefileGridFixture fixture;
        private FeatureSource source;
        private MapView view;
        private MapViewport expectedViewport;
        private BufferedImage surface;

        ShapefileRenderScenario(
                EvidenceConfiguration.Profile profile, ShapefileGridFixture fixture) {
            super(
                    profile,
                    "shapefile-render-window",
                    "G7-003",
                    1,
                    "frames",
                    "NOT_APPLICABLE",
                    counters("frames", 1, "records", fixture.records(), "portableInvariants", 6));
            this.fixture = fixture;
        }

        @Override
        public boolean runsOnEdt() {
            return true;
        }

        @Override
        public void setupScenario() throws Exception {
            source = openShapefile(fixture);
            onEdt(
                    () -> {
                        view = view();
                        view.setSize(800, 600);
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.ownedFeature(
                                                "shapefile",
                                                "shapefile",
                                                source,
                                                marker(),
                                                line(),
                                                fill())));
                        view.fitToData(24.0);
                        expectedViewport = view.viewport();
                        surface = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
                    });
        }

        @Override
        public void prepareSample() throws Exception {
            onEdt(
                    () -> {
                        clear(surface);
                        view.fitToData(24.0);
                    });
        }

        @Override
        public void runTimedBatch() {
            paint(view, surface);
        }

        @Override
        public EvidenceObservation observeSample() {
            Map<String, Long> counters =
                    counters(
                            "frames",
                            operations,
                            "records",
                            fixture.records(),
                            "portableInvariants",
                            6);
            ObservationDigests.RenderInvariants render =
                    ObservationDigests.renderInvariants(surface, view.viewport(), expectedViewport);
            return observation(
                    counters,
                    digest -> {
                        ObservationDigests.addRenderInvariants(digest, render);
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void close() {
            closeActions(
                    () -> {
                        if (view != null) {
                            onEdtUnchecked(view::close);
                        }
                    },
                    () -> {
                        if (source != null) {
                            source.close();
                        }
                    });
        }
    }

    private static final class RasterReadScenario extends BaseScenario {
        private final RasterGridFixture fixture;
        private final boolean png;
        private final boolean preseeded;
        private RasterSource source;
        private RasterRequest request;
        private RasterRead[] actualReads;

        RasterReadScenario(
                EvidenceConfiguration.Profile profile,
                RasterGridFixture fixture,
                boolean png,
                boolean preseeded) {
            super(
                    profile,
                    png ? "png-window-bilinear-disabled" : "jpeg-window-bilinear-preseeded",
                    png ? "no change" : "G6 cache oracle",
                    profile == EvidenceConfiguration.Profile.BASELINE
                            ? (png ? 153_600 : 1_228_800)
                            : (png ? 9_600 : 19_200),
                    "outputPixels",
                    png ? "DISABLED" : "ENABLED_PRESEEDED",
                    counters(
                            "sourcePixels",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 393_216 : 24_576,
                            "outputPixels",
                            profile == EvidenceConfiguration.Profile.BASELINE
                                    ? (png ? 153_600 : 1_228_800)
                                    : (png ? 9_600 : 19_200),
                            "reads",
                            png ? 1 : profile == EvidenceConfiguration.Profile.BASELINE ? 8 : 2));
            this.fixture = fixture;
            this.png = png;
            this.preseeded = preseeded;
        }

        @Override
        public void setupScenario() {
            source =
                    png
                            ? fixture.openPng(ImageCachePolicy.disabled(), false)
                            : fixture.openJpeg(ImageCachePolicy.defaults(), false);
            boolean baseline = profile == EvidenceConfiguration.Profile.BASELINE;
            RasterWindow window =
                    baseline
                            ? new RasterWindow(128, 128, 768, 512)
                            : new RasterWindow(32, 32, 192, 128);
            request =
                    new RasterRequest(
                            window,
                            baseline ? 480 : 120,
                            baseline ? 320 : 80,
                            RasterInterpolation.BILINEAR,
                            Optional.empty());
            if (preseeded) {
                source.read(request, CancellationToken.none());
            }
        }

        @Override
        public void prepareSample() {
            if (png) {
                source.close();
                source = fixture.openPng(ImageCachePolicy.disabled(), false);
            }
            int reads = png ? 1 : profile == EvidenceConfiguration.Profile.BASELINE ? 8 : 2;
            actualReads = new RasterRead[reads];
        }

        @Override
        public void runTimedBatch() {
            for (int index = 0; index < actualReads.length; index++) {
                actualReads[index] = source.read(request, CancellationToken.none());
            }
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public EvidenceObservation observeSample() {
            for (int first = 0; first < actualReads.length; first++) {
                for (int second = first + 1; second < actualReads.length; second++) {
                    require(
                            actualReads[first] != actualReads[second],
                            "Raster reads reused result identity");
                    require(
                            actualReads[first].pixels() != actualReads[second].pixels(),
                            "Raster reads reused pixel-buffer identity");
                }
            }
            long sourcePixels =
                    Math.multiplyExact(
                            (long) request.sourceWindow().width(), request.sourceWindow().height());
            long outputPixels =
                    Math.multiplyExact(
                            Math.multiplyExact(
                                    (long) request.outputWidth(), request.outputHeight()),
                            actualReads.length);
            Map<String, Long> counters =
                    counters(
                            "sourcePixels",
                            sourcePixels,
                            "outputPixels",
                            outputPixels,
                            "reads",
                            actualReads.length);
            return observation(
                    counters,
                    digest -> {
                        for (RasterRead read : actualReads) {
                            if (png) {
                                ObservationDigests.addPngPixels(digest, read);
                            } else {
                                ObservationDigests.addJpegInteriorProbes(digest, read, request);
                            }
                            ObservationDigests.addDiagnostics(digest, read.diagnostics());
                        }
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void finishSample() {
            actualReads = null;
        }

        @Override
        public void close() {
            if (source != null) {
                source.close();
            }
        }
    }

    private static final class RasterPanScenario extends BaseScenario {
        private final RasterGridFixture fixture;
        private RasterSource source;
        private MapView view;
        private MapViewport initial;
        private BufferedImage surface;

        RasterPanScenario(EvidenceConfiguration.Profile profile, RasterGridFixture fixture) {
            super(
                    profile,
                    "affine-raster-pan",
                    "G7-004",
                    profile == EvidenceConfiguration.Profile.BASELINE ? 12 : 4,
                    "frames",
                    "ENABLED_MIXED_KEYS",
                    counters(
                            "frames",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 12 : 4,
                            "portableInvariants",
                            6));
            this.fixture = fixture;
        }

        @Override
        public boolean runsOnEdt() {
            return true;
        }

        @Override
        public void setupScenario() throws Exception {
            source = fixture.openPng(ImageCachePolicy.defaults(), true);
            onEdt(
                    () -> {
                        view = view();
                        view.setSize(800, 600);
                        view.setLayerBindings(
                                List.of(MapLayerBinding.ownedRaster("raster", "raster", source)));
                        view.fitToData(24.0);
                        initial = view.viewport();
                        surface = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
                    });
        }

        @Override
        public void prepareSample() throws Exception {
            onEdt(
                    () -> {
                        clear(surface);
                        view.setViewport(initial);
                    });
        }

        @Override
        public void runTimedBatch() {
            int[][] trace = rasterTrace();
            int count = profile == EvidenceConfiguration.Profile.BASELINE ? trace.length : 4;
            for (int index = 0; index < count; index++) {
                view.setViewport(initial.panByPixels(trace[index][0], trace[index][1]));
                paint(view, surface);
            }
        }

        @Override
        public EvidenceObservation observeSample() {
            int frames = profile == EvidenceConfiguration.Profile.BASELINE ? 12 : 4;
            Map<String, Long> counters = counters("frames", frames, "portableInvariants", 6);
            MapViewport actualViewport = view.viewport();
            int[] finalPosition = rasterTrace()[frames - 1];
            MapViewport expectedViewport = initial.panByPixels(finalPosition[0], finalPosition[1]);
            ObservationDigests.RenderInvariants render =
                    ObservationDigests.renderInvariants(surface, actualViewport, expectedViewport);
            return observation(
                    counters,
                    digest -> {
                        ObservationDigests.addRenderInvariants(digest, render);
                        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
                    });
        }

        @Override
        public void close() {
            closeActions(
                    () -> {
                        if (view != null) {
                            onEdtUnchecked(view::close);
                        }
                    },
                    () -> {
                        if (source != null) {
                            source.close();
                        }
                    });
        }
    }

    private static final class NavigationScenario extends SourceVectorRenderScenario {
        private final boolean zoom;
        private MapViewport initial;

        NavigationScenario(
                EvidenceConfiguration.Profile profile,
                List<FeatureRecord> records,
                boolean zoom,
                FeatureSourceFactory sourceFactory) {
            this(
                    profile,
                    zoom ? "vector-zoom-sequence" : "vector-pan-sequence",
                    zoom ? "vector-zoom-sequence" : "vector-pan-sequence",
                    records,
                    zoom,
                    zoom ? "G7-003/G7-004" : "G7-002/G7-003/G7-004",
                    sourceFactory);
        }

        NavigationScenario(
                EvidenceConfiguration.Profile profile,
                String id,
                String semanticId,
                List<FeatureRecord> records,
                boolean zoom,
                String next,
                FeatureSourceFactory sourceFactory) {
            super(
                    profile,
                    id,
                    semanticId,
                    records,
                    profile == EvidenceConfiguration.Profile.BASELINE ? (zoom ? 12 : 16) : 4,
                    next,
                    sourceFactory);
            this.zoom = zoom;
        }

        @Override
        public void setupScenario() throws Exception {
            super.setupScenario();
            onEdt(() -> initial = view.viewport());
        }

        @Override
        public void prepareSample() throws Exception {
            onEdt(
                    () -> {
                        clear(surface);
                        view.setViewport(initial);
                    });
        }

        @Override
        public void runTimedBatch() {
            int frames = profile == EvidenceConfiguration.Profile.BASELINE ? (zoom ? 12 : 16) : 4;
            for (int index = 0; index < frames; index++) {
                if (zoom) {
                    double factor = index % 2 == 0 ? 1.25 : 0.8;
                    view.setViewport(view.viewport().zoomAt(400, 300, factor));
                } else {
                    int direction = index / (frames / 4);
                    int[][] deltas = {{12, 0}, {0, 12}, {-12, 0}, {0, -12}};
                    view.setViewport(
                            view.viewport()
                                    .panByPixels(deltas[direction][0], deltas[direction][1]));
                }
                paint(view, surface);
            }
        }

        @Override
        public EvidenceObservation observeSample() {
            require(closeEnough(view.viewport(), initial), "Navigation trace did not close");
            return sourceObservation(view.viewport());
        }
    }

    private static InMemoryFeatureSource source(String id, List<FeatureRecord> records) {
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        return InMemoryFeatureSource.open(
                new SourceIdentity(id, id),
                records,
                Optional.empty(),
                Optional.of(crs),
                FeatureSourceLimits.LEVEL_1);
    }

    private static InMemoryFeatureSource indexedSource(String id, List<FeatureRecord> records) {
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        return InMemoryFeatureSource.openIndexed(
                new SourceIdentity(id, id),
                records,
                Optional.empty(),
                Optional.of(crs),
                FeatureSourceLimits.LEVEL_1,
                FeatureIndexLimits.LEVEL_1);
    }

    private static InMemoryFeatureSource linearComparisonSource(
            String id, List<FeatureRecord> records) {
        return InMemoryFeatureSource.open(
                new SourceIdentity(id, id),
                records,
                Optional.empty(),
                Optional.empty(),
                IndexComparisonFixture.SOURCE_LIMITS);
    }

    private static InMemoryFeatureSource indexedComparisonSource(
            String id, List<FeatureRecord> records) {
        return InMemoryFeatureSource.openIndexed(
                new SourceIdentity(id, id),
                records,
                Optional.empty(),
                Optional.empty(),
                IndexComparisonFixture.SOURCE_LIMITS,
                FeatureIndexLimits.LEVEL_1);
    }

    private static Map<String, Long> buildCounters(int size) {
        IndexComparisonFixture.Layout layout = IndexComparisonFixture.layout(size);
        return counters(
                "inputRecords",
                size,
                "leaves",
                layout.leaves(),
                "nodes",
                layout.nodes(),
                "height",
                layout.height(),
                "retainedBytes",
                layout.retainedBytes(),
                "buildBytes",
                Math.addExact(layout.retainedBytes(), Math.multiplyExact(8L, size)));
    }

    private static Map<String, Long> queryCounters(
            int size, EvidenceConfiguration.Profile profile, boolean indexed, long candidateTotal) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        result.put("implementationIndexed", indexed ? 1L : 0L);
        result.put("inputRecords", (long) size);
        result.put("queries", profile == EvidenceConfiguration.Profile.BASELINE ? 256L : 24L);
        long records = IndexComparisonFixture.expectedRecords(size, profile);
        result.put("records", records);
        result.put("coordinates", records);
        if (indexed) {
            result.put("indexedCandidates", candidateTotal);
        }
        return result;
    }

    private static long inferProductionCandidateTotal(
            FeatureSource source, List<FeatureQuery> queries, int size) {
        long total = 0;
        for (int ordinal = 0; ordinal < queries.size(); ordinal++) {
            FeatureQuery query = queries.get(ordinal);
            if (IndexComparisonFixture.expectedRecords(size, ordinal) == 0
                    && productionQueryAdmits(source, query, 1)) {
                continue;
            }
            int low = 1;
            int high = size;
            while (low < high) {
                int middle = low + Math.floorDiv(high - low, 2);
                if (productionQueryAdmits(source, query, middle)) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            require(
                    productionQueryAdmits(source, query, low),
                    "Production candidate inference did not converge");
            total = Math.addExact(total, low);
        }
        return total;
    }

    private static boolean productionQueryAdmits(
            FeatureSource source, FeatureQuery query, int examinedRecords) {
        FeatureQueryLimits parent = IndexComparisonFixture.SOURCE_LIMITS.queryLimits();
        FeatureQueryLimits tightened =
                new FeatureQueryLimits(
                        examinedRecords,
                        parent.recordsReturned(),
                        parent.coordinatesReturned(),
                        parent.attributeValuesReturned(),
                        parent.decodedTextCharactersReturned(),
                        parent.ownedPayloadBytes(),
                        parent.retainedWarnings());
        FeatureQuery bounded =
                new FeatureQuery(query.sourceBounds(), query.attributes(), Optional.of(tightened));
        try (FeatureCursor cursor = source.openCursor(bounded, CancellationToken.none())) {
            while (cursor.advance()) {
                // Successful exhaustion proves the candidate-work ceiling admits this query.
            }
            return true;
        } catch (SourceException failure) {
            if (failure.terminal().code().equals("SOURCE_LIMIT_EXCEEDED")
                    && failure.terminal().context().get("limit").equals("recordsExamined")) {
                return false;
            }
            throw failure;
        }
    }

    private static List<FeatureRecord> readAll(FeatureSource source) {
        List<FeatureRecord> result = new ArrayList<>();
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                result.add(cursor.current());
            }
        }
        return List.copyOf(result);
    }

    private static FeatureSource openShapefile(ShapefileGridFixture fixture) {
        return Shapefiles.open(
                new SourceIdentity("shapefile-grid-v1", "shapefile-grid-v1"),
                fixture.shp(),
                ShapefileOpenOptions.defaults());
    }

    private static MapView view() {
        return new MapView(
                CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
    }

    private static MapLayerBinding binding(String id, FeatureSource source) {
        return MapLayerBinding.borrowedFeature(id, id, source, marker(), line(), fill());
    }

    private static MarkerSymbol marker() {
        return BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(36, 144, 94), 12, 1);
    }

    private static LineSymbol line() {
        return SolidLineSymbol.of(
                new SymbolStroke(
                        Rgba.rgb(18, 54, 40), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                1);
    }

    private static FillSymbol fill() {
        return SolidFillSymbol.of(Rgba.rgb(42, 132, 96), 1);
    }

    private static void paint(MapView view, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void clear(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
    }

    private static boolean closeEnough(MapViewport first, MapViewport second) {
        return ulpClose(first.centerX(), second.centerX())
                && ulpClose(first.centerY(), second.centerY())
                && ulpClose(first.worldUnitsPerPixel(), second.worldUnitsPerPixel());
    }

    private static boolean ulpClose(double first, double second) {
        return StrictMath.abs(first - second) <= 4 * Math.ulp(second);
    }

    private static void onEdt(ThrowingRunnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
        if (failure.get() instanceof Exception exception) {
            throw exception;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
    }

    private static void onEdtUnchecked(ThrowingRunnable action) {
        try {
            onEdt(action);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("EDT cleanup failed", failure);
        }
    }

    private static int[][] windowOrigins(EvidenceConfiguration.Profile profile) {
        return profile == EvidenceConfiguration.Profile.BASELINE
                ? new int[][] {
                    {0, 0}, {32, 16}, {64, 48}, {96, 80},
                    {128, 112}, {160, 144}, {192, 176}, {240, 240}
                }
                : new int[][] {
                    {0, 0}, {4, 2}, {8, 6}, {12, 10},
                    {16, 14}, {20, 18}, {24, 22}, {28, 28}
                };
    }

    private static int[][] shapefileOrigins(EvidenceConfiguration.Profile profile) {
        return profile == EvidenceConfiguration.Profile.BASELINE
                ? new int[][] {
                    {0, 0}, {60, 10}, {120, 20}, {180, 30},
                    {240, 40}, {300, 50}, {360, 60}, {420, 80}
                }
                : new int[][] {
                    {0, 0}, {6, 0}, {12, 1}, {18, 2},
                    {24, 3}, {30, 4}, {36, 5}, {42, 5}
                };
    }

    private static int[][] rasterTrace() {
        return new int[][] {
            {-120, -80}, {-80, -40}, {-40, 0}, {0, 0}, {40, 0}, {80, 40},
            {120, 80}, {80, 80}, {40, 40}, {0, 0}, {-40, -40}, {-80, -80}
        };
    }

    private static Map<String, Long> counters(Object... entries) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            String key = (String) entries[index];
            Number value = (Number) entries[index + 1];
            result.put(key, value.longValue());
        }
        return result;
    }

    private static void closeAfterConstructionFailure(AutoCloseable owned, Throwable primary) {
        if (owned != null) {
            try {
                owned.close();
            } catch (Throwable failure) {
                primary.addSuppressed(failure);
            }
        }
    }

    static void closeScenariosAfterFailure(List<EvidenceScenario> scenarios, Throwable primary) {
        for (int index = scenarios.size() - 1; index >= 0; index--) {
            closeAfterConstructionFailure(scenarios.get(index), primary);
        }
    }

    private static void deleteAfterConstructionFailure(Path workspace, Throwable primary) {
        try {
            deleteWorkspace(workspace);
        } catch (Throwable failure) {
            primary.addSuppressed(failure);
        }
    }

    private static Throwable closeOwned(AutoCloseable owned, Throwable primary) {
        try {
            owned.close();
            return primary;
        } catch (Throwable failure) {
            return suppress(primary, failure);
        }
    }

    private static Throwable suppress(Throwable primary, Throwable failure) {
        if (primary == null) {
            return failure;
        }
        primary.addSuppressed(failure);
        return primary;
    }

    static void closeActions(CloseAction... actions) {
        Throwable primary = null;
        for (CloseAction action : actions) {
            try {
                action.close();
            } catch (Throwable failure) {
                primary = suppress(primary, failure);
            }
        }
        if (primary instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (primary instanceof Error error) {
            throw error;
        }
        if (primary != null) {
            throw new IllegalStateException("Scenario resource cleanup failed", primary);
        }
    }

    private static void deleteWorkspace(Path workspace) throws IOException {
        if (Files.exists(workspace)) {
            try (var stream = Files.walk(workspace)) {
                for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    interface CloseAction {
        void close() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
