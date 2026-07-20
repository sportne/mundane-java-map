package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FillSymbol;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import io.github.mundanej.map.io.image.ImageCachePolicy;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class IndependentProfileOracle {
    private IndependentProfileOracle() {}

    static Map<String, String> derive(EvidenceConfiguration.Profile profile, Path workspace)
            throws Exception {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        List<FeatureRecord> grid = ReferenceFixtures.featureGrid(profile);
        result.put("memory-query-full", fullQuery(profile, grid));
        result.put("memory-query-window", windowQuery(profile, grid));
        List<FeatureRecord> vectors = ReferenceFixtures.vectorRecords(profile);
        result.put("dense-vector-render", denseRender(profile, vectors));
        result.put("symbol-heavy-render", symbolRender(profile));
        result.put("hit-test-sweep", hitSweep(profile));

        try (ReferenceWorkspace fixtures = ReferenceWorkspace.create(workspace, profile)) {
            result.put("shapefile-query-window", shapefileQuery(profile, fixtures));
            result.put("shapefile-render-window", shapefileRender(profile, fixtures));
            result.put("png-window-bilinear-disabled", rasterRead(profile, fixtures, true));
            result.put("jpeg-window-bilinear-preseeded", rasterRead(profile, fixtures, false));
            result.put("affine-raster-pan", rasterPan(profile, fixtures));
            result.put("vector-pan-sequence", navigation(profile, vectors, false));
            result.put("vector-zoom-sequence", navigation(profile, vectors, true));
            appendIndexDigests(result, profile);
            result.put("memory-query-window-indexed", result.get("memory-query-window"));
            result.put("hit-test-sweep-indexed", result.get("hit-test-sweep"));
            result.put("dense-vector-render-indexed", result.get("dense-vector-render"));
            result.put("vector-pan-sequence-indexed", result.get("vector-pan-sequence"));
            result.put("vector-zoom-sequence-indexed", result.get("vector-zoom-sequence"));
            List<FeatureRecord> small =
                    vectors.stream()
                            .filter(
                                    record ->
                                            record.id().equals("line:000")
                                                    || record.id().equals("polygon:000"))
                            .toList();
            String smallRender = denseRender(profile, small, "small-vector-render-v1");
            result.put("small-vector-render-unoptimized", smallRender);
            result.put("small-vector-render-optimized", smallRender);
            result.put("dense-vector-render-optimized", result.get("dense-vector-render"));
            result.put("vector-pan-sequence-optimized", result.get("vector-pan-sequence"));
            result.put("vector-zoom-sequence-optimized", result.get("vector-zoom-sequence"));
            result.put(
                    "symbol-heavy-render-template-cache-cold", result.get("symbol-heavy-render"));
            result.put(
                    "symbol-heavy-render-template-cache-warm", result.get("symbol-heavy-render"));
            result.put("portrayed-label-render-sparse", labelRender(profile, false));
            result.put("portrayed-label-render-colliding", labelRender(profile, true));
            result.putAll(IndependentDtedOracle.derive(profile));
            result.putAll(independentGeoTiff(profile, workspace.resolve("geotiff")));
            return Map.copyOf(result);
        }
    }

    private static Map<String, String> independentGeoTiff(
            EvidenceConfiguration.Profile profile, Path workspace) throws Exception {
        GeoTiffEvidenceFixture.Fixture fixture = GeoTiffEvidenceFixture.write(workspace, profile);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("geotiff-raster-window-read", independentGeoTiffRaster(profile, fixture));
        result.put("geotiff-eager-elevation-open", independentGeoTiffElevation(profile, fixture));
        return result;
    }

    private static String independentGeoTiffRaster(
            EvidenceConfiguration.Profile profile, GeoTiffEvidenceFixture.Fixture fixture) {
        int window = profile == EvidenceConfiguration.Profile.BASELINE ? 64 : 32;
        int windows = profile == EvidenceConfiguration.Profile.BASELINE ? 4 : 2;
        long outputPixels = (long) windows * window * window;
        Map<String, Long> counters =
                counters(
                        "windowsRead",
                        windows,
                        "outputPixels",
                        outputPixels,
                        "decodedSegmentBytes",
                        (long) windows * window * fixture.rasterSize(),
                        "encodedBytes",
                        fixture.rasterBytes());
        try (RasterSource source =
                GeoTiffFiles.openRaster(
                        new SourceIdentity("independent-geotiff-raster", ""),
                        fixture.raster(),
                        GeoTiffRasterOptions.defaults())) {
            ReferenceDigest timed =
                    new ReferenceDigest(EvidenceConfiguration.SEED)
                            .text("geotiff-raster-window-read");
            for (int index = 0; index < windows; index++) {
                int column = Math.floorMod(index * 193, fixture.rasterSize() - window + 1);
                int row =
                        16
                                * Math.floorMod(
                                        index * 19,
                                        Math.floorDiv(fixture.rasterSize() - window, 16) + 1);
                RasterRead read =
                        source.read(
                                new RasterRequest(
                                        new RasterWindow(column, row, window, window),
                                        window,
                                        window,
                                        Optional.empty()),
                                CancellationToken.none());
                for (int y = 0; y < window; y++) {
                    for (int x = 0; x < window; x++) {
                        timed.packedRgba(read.pixels().rgbaAt(x, y));
                    }
                }
            }
            return digest(
                    profile,
                    "geotiff-raster-window-read",
                    counters,
                    value -> value.longInteger(timed.value()));
        }
    }

    private static String independentGeoTiffElevation(
            EvidenceConfiguration.Profile profile, GeoTiffEvidenceFixture.Fixture fixture) {
        long samples = (long) fixture.elevationSize() * fixture.elevationSize();
        long publishedMask = 8L * Math.ceilDiv(samples, 64L);
        long published = samples * 8L + publishedMask;
        long temporary = samples * 8L;
        long scratch = 32L * fixture.elevationSize() * 2L;
        long segments = Math.ceilDiv(fixture.elevationSize(), 32L);
        long parserPlan = 13L * 64L + 9L * Double.BYTES + 4L * Long.BYTES * segments;
        long formatWorking = parserPlan + temporary + scratch;
        long snapshotAndFormat = fixture.elevationBytes() + formatWorking;
        long formatAndPublished = formatWorking + published;
        Map<String, Long> counters =
                counters(
                        "samplesPublished", samples,
                        "encodedBytes", fixture.elevationBytes(),
                        "logicalTemporaryBytes", temporary,
                        "logicalPublishedBytes", published,
                        "logicalDecoderScratchBytes", scratch,
                        "logicalParserPlanBytes", parserPlan,
                        "logicalFormatWorkingBytes", formatWorking,
                        "logicalSnapshotAndFormatPeakBytes", snapshotAndFormat,
                        "logicalFormatAndPublishedPeakBytes", formatAndPublished,
                        "logicalOpenPeakBytes", Math.max(snapshotAndFormat, formatAndPublished));
        try (var source =
                GeoTiffFiles.openElevation(
                        new SourceIdentity("independent-geotiff-elevation", ""),
                        fixture.elevation(),
                        GeoTiffElevationOptions.of(ElevationUnit.METRE))) {
            return digest(
                    profile,
                    "geotiff-eager-elevation-open",
                    counters,
                    value -> {
                        value.integer(source.metadata().columnCount())
                                .integer(source.metadata().rowCount())
                                .decimal(source.sample(0, 0).orElseThrow())
                                .decimal(
                                        source.sample(
                                                        source.metadata().columnCount() - 1,
                                                        source.metadata().rowCount() - 1)
                                                .orElseThrow());
                        ReferenceDigest.diagnostics(value, source.openingDiagnostics());
                    });
        }
    }

    private static void appendIndexDigests(
            Map<String, String> result, EvidenceConfiguration.Profile profile) {
        for (int size : List.of(128, 8_192, 131_072)) {
            result.put("index-build-" + size, indexBuild(profile, size));
        }
        for (int size : ReferenceFixtures.indexSizes()) {
            List<FeatureRecord> records = ReferenceFixtures.indexRecords(size);
            result.put("index-query-linear-" + size, indexQuery(profile, size, records, false, 0));
            long candidates = ReferenceFixtures.indexCandidateTotal(profile, size);
            result.put(
                    "index-query-str16-" + size,
                    indexQuery(profile, size, records, true, candidates));
        }
    }

    private static String indexBuild(EvidenceConfiguration.Profile profile, int size) {
        long[] layout = indexLayout(size);
        Map<String, Long> counters =
                counters(
                        "inputRecords",
                        size,
                        "leaves",
                        layout[0],
                        "nodes",
                        layout[1],
                        "height",
                        layout[2],
                        "retainedBytes",
                        layout[3],
                        "buildBytes",
                        Math.addExact(layout[3], Math.multiplyExact(8L, size)));
        return digest(profile, "index-build-" + size, counters, ignored -> {});
    }

    private static String indexQuery(
            EvidenceConfiguration.Profile profile,
            int size,
            List<FeatureRecord> records,
            boolean indexed,
            long candidates) {
        List<FeatureRecord> selected = new ArrayList<>();
        for (Envelope bounds : ReferenceFixtures.indexViewports(size, profile)) {
            for (FeatureRecord record : records) {
                Envelope envelope = record.geometry().envelope();
                if (envelope.maxX() >= bounds.minX()
                        && envelope.minX() <= bounds.maxX()
                        && envelope.maxY() >= bounds.minY()
                        && envelope.minY() <= bounds.maxY()) {
                    selected.add(record);
                }
            }
        }
        LinkedHashMap<String, Long> counters = new LinkedHashMap<>();
        counters.put("implementationIndexed", indexed ? 1L : 0L);
        counters.put("inputRecords", (long) size);
        counters.put("queries", profile == EvidenceConfiguration.Profile.BASELINE ? 256L : 24L);
        counters.put("records", (long) selected.size());
        counters.put("coordinates", (long) selected.size());
        if (indexed) {
            counters.put("indexedCandidates", candidates);
        }
        return digest(
                profile,
                "index-query-" + (indexed ? "str16-" : "linear-") + size,
                counters,
                oracle -> {
                    selected.forEach(record -> ReferenceDigest.record(oracle, record));
                    ReferenceDigest.diagnostics(oracle, DiagnosticReport.empty());
                });
    }

    private static long[] indexLayout(int records) {
        int leaves = Math.floorDiv(records - 1, 16) + 1;
        int nodes = leaves;
        int level = leaves;
        int height = 1;
        while (level > 1) {
            level = Math.floorDiv(level - 1, 16) + 1;
            nodes = Math.addExact(nodes, level);
            height++;
        }
        long retained =
                Math.addExact(Math.multiplyExact(4L, records), Math.multiplyExact(37L, nodes));
        retained = Math.addExact(retained, Math.multiplyExact(4L, nodes - 1L));
        return new long[] {leaves, nodes, height, retained};
    }

    private static String fullQuery(
            EvidenceConfiguration.Profile profile, List<FeatureRecord> records) {
        Map<String, Long> counters =
                counters("records", records.size(), "coordinates", records.size());
        return digest(
                profile,
                "memory-query-full",
                counters,
                oracle -> {
                    records.forEach(record -> ReferenceDigest.record(oracle, record));
                    ReferenceDigest.diagnostics(oracle, DiagnosticReport.empty());
                });
    }

    private static String windowQuery(
            EvidenceConfiguration.Profile profile, List<FeatureRecord> records) {
        int side = profile == EvidenceConfiguration.Profile.BASELINE ? 16 : 4;
        List<FeatureRecord> selected = new ArrayList<>();
        for (int[] origin : memoryOrigins(profile)) {
            for (FeatureRecord record : records) {
                Coordinate coordinate = ((PointGeometry) record.geometry()).coordinate();
                int column = (int) (coordinate.x() / 1_000.0);
                int row = (int) (coordinate.y() / 1_000.0);
                if (column >= origin[0]
                        && column < origin[0] + side
                        && row >= origin[1]
                        && row < origin[1] + side) {
                    selected.add(record);
                }
            }
        }
        Map<String, Long> counters =
                counters("records", selected.size(), "coordinates", selected.size());
        return digest(
                profile,
                "memory-query-window",
                counters,
                oracle -> {
                    selected.forEach(record -> ReferenceDigest.record(oracle, record));
                    ReferenceDigest.diagnostics(oracle, DiagnosticReport.empty());
                });
    }

    private static String denseRender(
            EvidenceConfiguration.Profile profile, List<FeatureRecord> records) throws Exception {
        return denseRender(profile, records, "dense-vector-render");
    }

    private static String denseRender(
            EvidenceConfiguration.Profile profile, List<FeatureRecord> records, String semanticId)
            throws Exception {
        FeatureSource source = source("independent-dense", records);
        MapView view = view();
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        MapViewport[] expected = new MapViewport[1];
        try {
            onEdt(
                    () -> {
                        view.setSize(800, 600);
                        view.setLayerBindings(List.of(binding("dense", source)));
                        view.fitToData(24.0);
                        expected[0] = view.viewport();
                        paint(view, image);
                    });
            MapViewport viewport = view.viewport();
            Map<String, Long> counters =
                    counters("frames", 1, "features", records.size(), "portableInvariants", 6);
            ReferenceDigest.RenderInvariants render =
                    referenceRenderInvariants(image, viewport, expected[0]);
            return digest(
                    profile,
                    semanticId,
                    counters,
                    oracle -> {
                        readAll(source).forEach(record -> ReferenceDigest.record(oracle, record));
                        ReferenceDigest.renderInvariants(oracle, render);
                        ReferenceDigest.diagnostics(oracle, source.openingDiagnostics());
                    });
        } finally {
            onEdt(view::close);
            source.close();
        }
    }

    private static String symbolRender(EvidenceConfiguration.Profile profile) throws Exception {
        List<InMemoryLayer> layers = ReferenceFixtures.symbolLayers(profile);
        MapView view = view();
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        MapViewport[] expected = new MapViewport[1];
        try {
            onEdt(
                    () -> {
                        view.setSize(800, 600);
                        view.setLayers(new ArrayList<>(layers));
                        view.fitToData(24.0);
                        expected[0] = view.viewport();
                        paint(view, image);
                    });
            long features = layers.stream().mapToLong(layer -> layer.features().size()).sum();
            Map<String, Long> counters =
                    counters("frames", 1, "features", features, "portableInvariants", 6);
            MapViewport viewport = view.viewport();
            ReferenceDigest.RenderInvariants render =
                    referenceRenderInvariants(image, viewport, expected[0]);
            return digest(
                    profile,
                    "symbol-heavy-render",
                    counters,
                    oracle -> {
                        for (InMemoryLayer layer : layers) {
                            oracle.text(layer.id()).text(layer.name());
                            layer.features()
                                    .forEach(feature -> ReferenceDigest.feature(oracle, feature));
                        }
                        ReferenceDigest.renderInvariants(oracle, render);
                    });
        } finally {
            onEdt(view::close);
        }
    }

    private static String labelRender(EvidenceConfiguration.Profile profile, boolean colliding)
            throws Exception {
        LabelOracleFixture fixture = labelFixture(profile, colliding);
        String id =
                colliding ? "portrayed-label-render-colliding" : "portrayed-label-render-sparse";
        MapView view = view();
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        MapViewport[] expected = new MapViewport[1];
        try {
            onEdt(
                    () -> {
                        view.setSize(800, 600);
                        view.setLayerBindings(
                                fixture.bindings().stream()
                                        .map(
                                                binding ->
                                                        MapLayerBinding.portrayedSnapshot(
                                                                binding.layer(),
                                                                binding.portrayal()))
                                        .toList());
                        view.fitToData(24);
                        expected[0] = view.viewport();
                        paint(view, image);
                    });
            LabelOracleImageFacts facts = labelImageFacts(image);
            Map<String, Long> counters =
                    counters(
                            "frames",
                            1,
                            "features",
                            fixture.featureCount(),
                            "labelRequests",
                            fixture.featureCount(),
                            "declaredCandidates",
                            fixture.featureCount(),
                            "collisionFixture",
                            colliding ? 1 : 0,
                            "selectedBlueInk",
                            facts.blue() ? 1 : 0,
                            "selectedRedInk",
                            facts.red() ? 1 : 0,
                            "acceptedLabelInk",
                            facts.green() ? 1 : 0,
                            "rejectedLowerPriorityLabelInk",
                            facts.yellow() ? 1 : 0,
                            "portableInvariants",
                            10);
            ReferenceDigest.RenderInvariants render =
                    referenceRenderInvariants(image, view.viewport(), expected[0]);
            return digest(
                    profile,
                    id,
                    counters,
                    oracle -> {
                        for (LabelOracleLayer binding : fixture.bindings()) {
                            oracle.text(binding.layer().id()).text(binding.layer().name());
                            binding.layer()
                                    .features()
                                    .forEach(feature -> ReferenceDigest.feature(oracle, feature));
                        }
                        ReferenceDigest.renderInvariants(oracle, render);
                    });
        } finally {
            onEdt(view::close);
        }
    }

    private static LabelOracleFixture labelFixture(
            EvidenceConfiguration.Profile profile, boolean colliding) {
        int count =
                colliding
                        ? (profile == EvidenceConfiguration.Profile.BASELINE ? 1_024 : 256)
                        : (profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 64);
        MarkerSymbol blue =
                BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(35, 105, 205), 10, 1);
        MarkerSymbol red =
                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(195, 45, 45), 10, 1);
        CategoricalSymbolSelector selector =
                new CategoricalSymbolSelector(
                        "kind",
                        List.of(
                                new CategoricalSymbolRule(ThematicValue.text("blue"), blue),
                                new CategoricalSymbolRule(ThematicValue.text("red"), red)),
                        Optional.empty());
        MarkerSymbol stored =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(115, 115, 115), 8, 1);
        if (!colliding) {
            List<Feature> features = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int column = index % 16;
                int row = index / 16;
                features.add(
                        labelFeature(
                                index,
                                column * 60.0,
                                row * 40.0,
                                index % 2 == 0 ? "blue" : "red",
                                stored));
            }
            return new LabelOracleFixture(
                    List.of(
                            new LabelOracleLayer(
                                    new InMemoryLayer("label-sparse", "Sparse labels", features),
                                    FeaturePortrayal.markers(selector)
                                            .withPointLabel(
                                                    labelProfile(Rgba.rgb(20, 170, 40), 0)))),
                    count);
        }
        List<Feature> lower = new ArrayList<>(count - 1);
        for (int index = 0; index < count - 1; index++) {
            lower.add(labelFeature(index, 0, 0, "red", stored));
        }
        return new LabelOracleFixture(
                List.of(
                        new LabelOracleLayer(
                                new InMemoryLayer(
                                        "label-colliding-lower", "Colliding lower labels", lower),
                                FeaturePortrayal.markers(selector)
                                        .withPointLabel(labelProfile(Rgba.rgb(180, 180, 20), 0))),
                        new LabelOracleLayer(
                                new InMemoryLayer(
                                        "label-colliding-upper",
                                        "Colliding upper label",
                                        List.of(labelFeature(count - 1, 0, 0, "blue", stored))),
                                FeaturePortrayal.markers(selector)
                                        .withPointLabel(labelProfile(Rgba.rgb(20, 170, 40), 10)))),
                count);
    }

    private static Feature labelFeature(
            int index, double x, double y, String kind, MarkerSymbol stored) {
        String id = String.format(java.util.Locale.ROOT, "label-%04d", index);
        return new Feature(
                id, id, new PointGeometry(new Coordinate(x, y)), Map.of("kind", kind), stored);
    }

    private static PointLabelProfile labelProfile(Rgba color, int priority) {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                new LabelTextStyle(color, LabelWeight.NORMAL, 12),
                List.of(PointLabelPosition.NE),
                2,
                0,
                0,
                1,
                priority,
                ResolutionRange.ALL);
    }

    private static LabelOracleImageFacts labelImageFacts(BufferedImage image) {
        boolean blue = false;
        boolean red = false;
        boolean green = false;
        boolean yellow = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int packed = image.getRGB(x, y);
                int r = packed >>> 16 & 0xff;
                int g = packed >>> 8 & 0xff;
                int b = packed & 0xff;
                blue |= b > r + 40 && b > g + 40;
                red |= r > g + 40 && r > b + 40;
                green |= g > r + 50 && g > b + 50;
                yellow |= r > b + 50 && g > b + 50;
            }
        }
        return new LabelOracleImageFacts(blue, red, green, yellow);
    }

    private record LabelOracleLayer(InMemoryLayer layer, FeaturePortrayal portrayal) {}

    private record LabelOracleFixture(List<LabelOracleLayer> bindings, int featureCount) {
        private LabelOracleFixture {
            bindings = List.copyOf(bindings);
        }
    }

    private record LabelOracleImageFacts(
            boolean blue, boolean red, boolean green, boolean yellow) {}

    private static String hitSweep(EvidenceConfiguration.Profile profile) throws Exception {
        List<FeatureSource> sources = new ArrayList<>();
        MapView view = view();
        try {
            for (int binding = 0; binding < 4; binding++) {
                sources.add(
                        source(
                                "independent-hit-" + binding,
                                ReferenceFixtures.hitRecords(binding, profile)));
            }
            onEdt(
                    () -> {
                        List<MapLayerBinding> bindings = new ArrayList<>();
                        for (int index = 0; index < sources.size(); index++) {
                            bindings.add(binding("hit-layer-" + index, sources.get(index)));
                        }
                        view.setSize(800, 600);
                        view.setLayerBindings(bindings);
                        view.setViewport(new MapViewport(800, 600, 75_000, 75_000, 300));
                    });
            int probes = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32;
            List<List<MapHit>> hits = new ArrayList<>(probes);
            onEdt(
                    () -> {
                        for (int ordinal = 0; ordinal < probes; ordinal++) {
                            int logical =
                                    profile == EvidenceConfiguration.Profile.BASELINE
                                            ? ordinal
                                            : ordinal / 8 * 64 + ordinal % 8;
                            Coordinate map =
                                    new Coordinate(
                                            10_000.0 * (logical % 16),
                                            10_000.0 * Math.floorDiv(logical, 16));
                            Coordinate screen = view.mapToScreen(map).orElseThrow();
                            hits.add(view.hitTest(screen.x(), screen.y(), 0.0).hits());
                        }
                    });
            long hitCount = hits.stream().filter(item -> !item.isEmpty()).count();
            Map<String, Long> counters =
                    counters(
                            "probes",
                            probes,
                            "hits",
                            hitCount,
                            "misses",
                            Math.subtractExact(probes, hitCount));
            return digest(
                    profile,
                    "hit-test-sweep",
                    counters,
                    oracle -> {
                        hits.forEach(item -> ReferenceDigest.hits(oracle, item));
                        sources.forEach(
                                item ->
                                        ReferenceDigest.diagnostics(
                                                oracle, item.openingDiagnostics()));
                    });
        } finally {
            onEdt(view::close);
            sources.forEach(FeatureSource::close);
        }
    }

    private static String shapefileQuery(
            EvidenceConfiguration.Profile profile, ReferenceWorkspace fixture) {
        int columns = profile == EvidenceConfiguration.Profile.BASELINE ? 500 : 50;
        int side = profile == EvidenceConfiguration.Profile.BASELINE ? 10 : 5;
        List<FeatureRecord> selected = new ArrayList<>();
        for (int[] origin : shapefileOrigins(profile)) {
            for (int row = origin[1]; row < origin[1] + side; row++) {
                for (int column = origin[0]; column < origin[0] + side; column++) {
                    int ordinal = row * columns + column;
                    selected.add(
                            new FeatureRecord(
                                    "record:" + (ordinal + 1),
                                    "",
                                    new PointGeometry(
                                            new Coordinate(column * 1_000.0, row * 1_000.0)),
                                    Map.of(
                                            "ID",
                                            (long) ordinal + 1,
                                            "GROUP",
                                            String.format(
                                                    java.util.Locale.ROOT,
                                                    "group-%02d",
                                                    row % 20))));
                }
            }
        }
        DiagnosticReport diagnostics;
        try (FeatureSource source = openShapefile(fixture)) {
            diagnostics = source.openingDiagnostics();
        }
        long attributes = selected.stream().mapToLong(record -> record.attributes().size()).sum();
        Map<String, Long> counters =
                counters(
                        "records",
                        selected.size(),
                        "coordinates",
                        selected.size(),
                        "attributes",
                        attributes);
        return digest(
                profile,
                "shapefile-query-window",
                counters,
                oracle -> {
                    selected.forEach(record -> ReferenceDigest.record(oracle, record));
                    ReferenceDigest.diagnostics(oracle, diagnostics);
                });
    }

    private static String shapefileRender(
            EvidenceConfiguration.Profile profile, ReferenceWorkspace fixture) throws Exception {
        FeatureSource source = openShapefile(fixture);
        MapView view = view();
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        MapViewport[] expected = new MapViewport[1];
        try {
            onEdt(
                    () -> {
                        view.setSize(800, 600);
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.borrowedFeature(
                                                "shape", "shape", source, marker(), line(),
                                                fill())));
                        view.fitToData(24.0);
                        expected[0] = view.viewport();
                        paint(view, image);
                    });
            MapViewport viewport = view.viewport();
            ReferenceDigest.RenderInvariants render =
                    referenceRenderInvariants(image, viewport, expected[0]);
            Map<String, Long> counters =
                    counters(
                            "frames",
                            1,
                            "records",
                            fixture.shapefileRecords(),
                            "portableInvariants",
                            6);
            return digest(
                    profile,
                    "shapefile-render-window",
                    counters,
                    oracle -> {
                        ReferenceDigest.renderInvariants(oracle, render);
                        ReferenceDigest.diagnostics(oracle, source.openingDiagnostics());
                    });
        } finally {
            onEdt(view::close);
            source.close();
        }
    }

    private static String rasterRead(
            EvidenceConfiguration.Profile profile, ReferenceWorkspace fixture, boolean png) {
        RasterSource source =
                png
                        ? fixture.openPng(ImageCachePolicy.disabled(), false)
                        : fixture.openJpeg(ImageCachePolicy.defaults(), false);
        try {
            boolean baseline = profile == EvidenceConfiguration.Profile.BASELINE;
            RasterRequest request =
                    new RasterRequest(
                            baseline
                                    ? new RasterWindow(128, 128, 768, 512)
                                    : new RasterWindow(32, 32, 192, 128),
                            baseline ? 480 : 120,
                            baseline ? 320 : 80,
                            RasterInterpolation.BILINEAR,
                            Optional.empty());
            if (!png) {
                source.read(request, CancellationToken.none());
            }
            int count = png ? 1 : baseline ? 8 : 2;
            List<RasterRead> reads = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                reads.add(source.read(request, CancellationToken.none()));
            }
            requireDistinctRasterReads(reads);
            long sourcePixels =
                    Math.multiplyExact(
                            (long) request.sourceWindow().width(), request.sourceWindow().height());
            long outputPixels =
                    Math.multiplyExact(
                            Math.multiplyExact(
                                    (long) request.outputWidth(), request.outputHeight()),
                            reads.size());
            Map<String, Long> counters =
                    counters(
                            "sourcePixels",
                            sourcePixels,
                            "outputPixels",
                            outputPixels,
                            "reads",
                            reads.size());
            String id = png ? "png-window-bilinear-disabled" : "jpeg-window-bilinear-preseeded";
            return digest(
                    profile,
                    id,
                    counters,
                    oracle -> {
                        for (RasterRead read : reads) {
                            if (png) {
                                addPngPixels(oracle, read);
                            } else {
                                addJpegInteriorProbes(oracle, read, request);
                            }
                            ReferenceDigest.diagnostics(oracle, read.diagnostics());
                        }
                        ReferenceDigest.diagnostics(oracle, source.openingDiagnostics());
                    });
        } finally {
            source.close();
        }
    }

    private static String rasterPan(
            EvidenceConfiguration.Profile profile, ReferenceWorkspace fixture) throws Exception {
        RasterSource source = fixture.openPng(ImageCachePolicy.defaults(), true);
        MapView view = view();
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        try {
            onEdt(
                    () -> {
                        view.setSize(800, 600);
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.borrowedRaster(
                                                "raster", "raster", source)));
                        view.fitToData(24.0);
                    });
            MapViewport initial = view.viewport();
            int[][] trace = {
                {-120, -80}, {-80, -40}, {-40, 0}, {0, 0}, {40, 0}, {80, 40},
                {120, 80}, {80, 80}, {40, 40}, {0, 0}, {-40, -40}, {-80, -80}
            };
            int frames = profile == EvidenceConfiguration.Profile.BASELINE ? trace.length : 4;
            onEdt(
                    () -> {
                        for (int index = 0; index < frames; index++) {
                            view.setViewport(initial.panByPixels(trace[index][0], trace[index][1]));
                            paint(view, image);
                        }
                    });
            MapViewport actual = view.viewport();
            int[] finalPosition = trace[frames - 1];
            MapViewport expected = initial.panByPixels(finalPosition[0], finalPosition[1]);
            ReferenceDigest.RenderInvariants render =
                    referenceRenderInvariants(image, actual, expected);
            Map<String, Long> counters = counters("frames", frames, "portableInvariants", 6);
            return digest(
                    profile,
                    "affine-raster-pan",
                    counters,
                    oracle -> {
                        ReferenceDigest.renderInvariants(oracle, render);
                        ReferenceDigest.diagnostics(oracle, source.openingDiagnostics());
                    });
        } finally {
            onEdt(view::close);
            source.close();
        }
    }

    private static String navigation(
            EvidenceConfiguration.Profile profile, List<FeatureRecord> records, boolean zoom)
            throws Exception {
        FeatureSource source = source("independent-navigation", records);
        MapView view = view();
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        try {
            onEdt(
                    () -> {
                        view.setSize(800, 600);
                        view.setLayerBindings(List.of(binding("navigation", source)));
                        view.fitToData(24.0);
                    });
            MapViewport initial = view.viewport();
            int frames = profile == EvidenceConfiguration.Profile.BASELINE ? (zoom ? 12 : 16) : 4;
            onEdt(
                    () -> {
                        for (int index = 0; index < frames; index++) {
                            if (zoom) {
                                view.setViewport(
                                        view.viewport()
                                                .zoomAt(400, 300, index % 2 == 0 ? 1.25 : 0.8));
                            } else {
                                int direction = index / (frames / 4);
                                int[][] deltas = {{12, 0}, {0, 12}, {-12, 0}, {0, -12}};
                                view.setViewport(
                                        view.viewport()
                                                .panByPixels(
                                                        deltas[direction][0],
                                                        deltas[direction][1]));
                            }
                            paint(view, image);
                        }
                    });
            MapViewport actual = view.viewport();
            if (!closeEnough(actual, initial)) {
                throw new IllegalStateException("Independent navigation trace did not close");
            }
            ReferenceDigest.RenderInvariants render =
                    referenceRenderInvariants(image, actual, initial);
            Map<String, Long> counters =
                    counters("frames", frames, "features", records.size(), "portableInvariants", 6);
            String id = zoom ? "vector-zoom-sequence" : "vector-pan-sequence";
            return digest(
                    profile,
                    id,
                    counters,
                    oracle -> {
                        readAll(source).forEach(record -> ReferenceDigest.record(oracle, record));
                        ReferenceDigest.renderInvariants(oracle, render);
                        ReferenceDigest.diagnostics(oracle, source.openingDiagnostics());
                    });
        } finally {
            onEdt(view::close);
            source.close();
        }
    }

    private static String digest(
            EvidenceConfiguration.Profile profile,
            String id,
            Map<String, Long> counters,
            java.util.function.Consumer<ReferenceDigest> content) {
        ReferenceDigest oracle =
                new ReferenceDigest(EvidenceConfiguration.SEED).text(profile.name()).text(id);
        content.accept(oracle);
        counters.forEach((key, value) -> oracle.text(key).longInteger(value));
        return String.format(java.util.Locale.ROOT, "%016x", oracle.value());
    }

    private static void addPngPixels(ReferenceDigest oracle, RasterRead read) {
        int width = read.pixels().width();
        int height = read.pixels().height();
        oracle.integer(width).integer(height);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                oracle.packedRgba(read.pixels().rgbaAt(column, row));
            }
        }
    }

    private static void addJpegInteriorProbes(
            ReferenceDigest oracle, RasterRead read, RasterRequest request) {
        List<JpegProbe> probes = jpegProbes(request);
        oracle.integer(probes.size());
        for (JpegProbe probe : probes) {
            int actual = read.pixels().rgbaAt(probe.outputColumn(), probe.outputRow());
            if (!withinRgbaTolerance(actual, probe.expectedRgba(), 20)) {
                throw new IllegalStateException("Reference JPEG interior probe exceeded tolerance");
            }
            oracle.integer(probe.outputColumn())
                    .integer(probe.outputRow())
                    .packedRgba(probe.expectedRgba())
                    .text("JPEG_INTERIOR_WITHIN_TOLERANCE");
        }
    }

    private static List<JpegProbe> jpegProbes(RasterRequest request) {
        List<JpegProbe> result = new ArrayList<>();
        java.util.Set<Long> sampledTiles = new java.util.LinkedHashSet<>();
        for (int row = 0; row < request.outputHeight() && result.size() < 8; row++) {
            int sourceRow =
                    sourceLower(row, request.sourceWindow().height(), request.outputHeight());
            int absoluteRow = request.sourceWindow().row() + sourceRow;
            if (!tileInterior(absoluteRow)) {
                continue;
            }
            for (int column = 0; column < request.outputWidth() && result.size() < 8; column++) {
                int sourceColumn =
                        sourceLower(column, request.sourceWindow().width(), request.outputWidth());
                int absoluteColumn = request.sourceWindow().column() + sourceColumn;
                if (!tileInterior(absoluteColumn)) {
                    continue;
                }
                int tileX = Math.floorDiv(absoluteColumn, 64);
                int tileY = Math.floorDiv(absoluteRow, 64);
                long tileKey = ((long) tileY << 32) | (tileX & 0xffff_ffffL);
                if (!sampledTiles.add(tileKey)) {
                    continue;
                }
                int expected =
                        (((17 * tileX + 3 * tileY) & 0xff) << 24)
                                | (((5 * tileX + 19 * tileY) & 0xff) << 16)
                                | (((11 * tileX + 7 * tileY) & 0xff) << 8)
                                | 0xff;
                result.add(new JpegProbe(column, row, expected));
            }
        }
        if (result.size() != 8) {
            throw new IllegalStateException("Reference JPEG request lacked eight probes");
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("ReferenceEquality")
    private static void requireDistinctRasterReads(List<RasterRead> reads) {
        for (int first = 0; first < reads.size(); first++) {
            for (int second = first + 1; second < reads.size(); second++) {
                if (reads.get(first) == reads.get(second)
                        || reads.get(first).pixels() == reads.get(second).pixels()) {
                    throw new IllegalStateException("Reference raster read identity was reused");
                }
            }
        }
    }

    private static ReferenceDigest.RenderInvariants referenceRenderInvariants(
            BufferedImage image, MapViewport actualViewport, MapViewport expectedViewport) {
        int background = image.getRGB(0, 0);
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        long painted = 0;
        long expectedColor = 0;
        long expectedAlpha = 0;
        long border = 0;
        long backgroundMatches = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                boolean isBorder =
                        x < 8 || y < 8 || x >= image.getWidth() - 8 || y >= image.getHeight() - 8;
                if (isBorder) {
                    border++;
                    if (!referenceDifferent(pixel, background)) {
                        backgroundMatches++;
                    }
                }
                if (referenceDifferent(pixel, background)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    painted++;
                    if (referenceRgbDifferent(pixel, background)) {
                        expectedColor++;
                    }
                    if (Math.abs(((pixel >>> 24) & 0xff) - ((background >>> 24) & 0xff)) <= 8) {
                        expectedAlpha++;
                    }
                }
            }
        }
        long pixels = Math.multiplyExact((long) image.getWidth(), image.getHeight());
        requireMajority(backgroundMatches, border, "Reference background majority changed");
        requireMajority(expectedColor, painted, "Reference expected color majority changed");
        requireMajority(expectedAlpha, painted, "Reference expected alpha majority changed");
        if (painted == 0
                || minX < 0
                || minY < 0
                || maxX >= image.getWidth()
                || maxY >= image.getHeight()) {
            throw new IllegalStateException("Reference paint bounds are not contained");
        }
        if (painted < 16 || painted >= pixels) {
            throw new IllegalStateException("Reference paint count is outside the portable range");
        }
        if (!closeEnough(actualViewport, expectedViewport)) {
            throw new IllegalStateException("Reference viewport did not match expected trace");
        }
        return new ReferenceDigest.RenderInvariants(
                List.of(
                        new ReferenceDigest.RenderInvariant(
                                "background",
                                ReferenceDigest.RenderClassification.BACKGROUND_MAJORITY),
                        new ReferenceDigest.RenderInvariant(
                                "expectedColor",
                                ReferenceDigest.RenderClassification.EXPECTED_COLOR_MAJORITY),
                        new ReferenceDigest.RenderInvariant(
                                "expectedAlpha",
                                ReferenceDigest.RenderClassification.EXPECTED_ALPHA_MAJORITY),
                        new ReferenceDigest.RenderInvariant(
                                "paintBounds",
                                ReferenceDigest.RenderClassification.PAINT_BOUNDS_CONTAINED),
                        new ReferenceDigest.RenderInvariant(
                                "paintCount",
                                ReferenceDigest.RenderClassification.PAINT_COUNT_IN_RANGE),
                        new ReferenceDigest.RenderInvariant(
                                "viewport", ReferenceDigest.RenderClassification.VIEWPORT_MATCH)));
    }

    private static boolean referenceDifferent(int first, int second) {
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs(((first >>> shift) & 0xff) - ((second >>> shift) & 0xff)) > 8) {
                return true;
            }
        }
        return false;
    }

    private static boolean referenceRgbDifferent(int first, int second) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((first >>> shift) & 0xff) - ((second >>> shift) & 0xff)) > 8) {
                return true;
            }
        }
        return false;
    }

    private static void requireMajority(long matching, long total, String message) {
        if (total <= 0 || matching <= total / 2) {
            throw new IllegalStateException(message);
        }
    }

    private static int sourceLower(int output, int sourceSize, int outputSize) {
        long denominator = 2L * outputSize;
        long numerator =
                Math.subtractExact(
                        Math.multiplyExact(
                                Math.addExact(Math.multiplyExact(2L, output), 1L), sourceSize),
                        outputSize);
        if (sourceSize == 1 || numerator <= 0) {
            return 0;
        }
        if (numerator >= Math.multiplyExact(sourceSize - 1L, denominator)) {
            return sourceSize - 1;
        }
        return Math.toIntExact(Math.floorDiv(numerator, denominator));
    }

    private static boolean tileInterior(int absolute) {
        int within = Math.floorMod(absolute, 64);
        return within >= 8 && within <= 54;
    }

    private static boolean withinRgbaTolerance(int actual, int expected, int tolerance) {
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs(((actual >>> shift) & 0xff) - ((expected >>> shift) & 0xff)) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static List<FeatureRecord> readAll(FeatureSource source) {
        List<FeatureRecord> records = new ArrayList<>();
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                records.add(cursor.current());
            }
        }
        return List.copyOf(records);
    }

    private static FeatureSource source(String id, List<FeatureRecord> records) {
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

    private static FeatureSource openShapefile(ReferenceWorkspace fixture) {
        return Shapefiles.open(
                new SourceIdentity("independent-shape", "independent-shape"),
                fixture.shapefile(),
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

    private static void onEdt(ThrowingRunnable action) throws Exception {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        javax.swing.SwingUtilities.invokeAndWait(
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

    private static boolean closeEnough(MapViewport first, MapViewport second) {
        return Math.abs(first.centerX() - second.centerX()) <= 4 * Math.ulp(second.centerX())
                && Math.abs(first.centerY() - second.centerY()) <= 4 * Math.ulp(second.centerY())
                && Math.abs(first.worldUnitsPerPixel() - second.worldUnitsPerPixel())
                        <= 4 * Math.ulp(second.worldUnitsPerPixel());
    }

    private static int[][] memoryOrigins(EvidenceConfiguration.Profile profile) {
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

    private static Map<String, Long> counters(Object... entries) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], ((Number) entries[index + 1]).longValue());
        }
        return result;
    }

    private record JpegProbe(int outputColumn, int outputRow, int expectedRgba) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
