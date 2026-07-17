package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.InMemoryLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FixtureCatalog {
    private static final List<BuiltInMarker> MARKERS =
            List.of(
                    BuiltInMarker.CIRCLE,
                    BuiltInMarker.SQUARE,
                    BuiltInMarker.TRIANGLE,
                    BuiltInMarker.DIAMOND,
                    BuiltInMarker.CROSS,
                    BuiltInMarker.X,
                    BuiltInMarker.STAR,
                    BuiltInMarker.ARROW);
    private static final List<HatchPattern> HATCHES =
            List.of(
                    HatchPattern.FORWARD_DIAGONAL,
                    HatchPattern.BACKWARD_DIAGONAL,
                    HatchPattern.CROSS_DIAGONAL);

    private FixtureCatalog() {}

    static List<EvidenceReport.FixtureFact> facts(EvidenceConfiguration.Profile profile) {
        List<FeatureRecord> featureGrid = featureGrid(profile);
        InMemoryLayer vectors = vectorLayer(profile);
        List<InMemoryLayer> symbols = symbolLayers(profile);
        long vectorCoordinates =
                vectors.features().stream()
                        .mapToLong(feature -> coordinateCount(feature.geometry()))
                        .sum();
        long symbolCount = symbols.stream().mapToLong(layer -> layer.features().size()).sum();
        long hitProbes = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32;
        int shapefileColumns = profile == EvidenceConfiguration.Profile.BASELINE ? 500 : 50;
        int shapefileRows = profile == EvidenceConfiguration.Profile.BASELINE ? 100 : 10;
        long shapefileRecords = Math.multiplyExact(shapefileColumns, shapefileRows);
        List<EvidenceReport.FixtureFact> result =
                new ArrayList<>(
                        List.of(
                                factFromRecords("feature-grid-v1", featureGrid),
                                factFromFeatures(
                                        "vector-path-v1", vectorCoordinates, vectors.features()),
                                factFromLayers("symbol-field-v1", symbolCount, symbols),
                                hitFact(profile, hitProbes),
                                shapefileFact(
                                        profile, shapefileColumns, shapefileRows, shapefileRecords),
                                rasterFact(),
                                factFromRecords(
                                        "index-comparison-v1",
                                        IndexComparisonFixture.records(
                                                IndexComparisonFixture.SIZES.getLast()))));
        if (profile == EvidenceConfiguration.Profile.BASELINE) {
            FnvOracle corpus = new FnvOracle(EvidenceConfiguration.SEED).add("dted-corpus-v1");
            Map<String, String> files =
                    Map.of(
                            "level0",
                                    "8762:9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802",
                            "level1",
                                    "488642:ba2b8033ee4942989ec9acb916f95fffc88f054c3e9917145b4e613978db5c4f",
                            "level2",
                                    "4339042:4d0511dd1551b05449ee9a60a4849e3baf132bcff64438f242aebfbaf126e58d");
            files.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> corpus.add(entry.getKey()).add(entry.getValue()));
            result.add(
                    new EvidenceReport.FixtureFact(
                            "dted-corpus-v1", 2_408_143L, corpus.value(), files));
            result.add(dtedGeneratedFact(DtedEvidenceFixture.MAXIMUM));
        } else {
            result.add(dtedGeneratedFact(DtedEvidenceFixture.SMOKE));
        }
        return List.copyOf(result);
    }

    private static EvidenceReport.FixtureFact dtedGeneratedFact(
            DtedEvidenceFixture.Fixture fixture) {
        FnvOracle digest =
                new FnvOracle(EvidenceConfiguration.SEED)
                        .add(fixture.id())
                        .add(fixture.level())
                        .add(fixture.posts())
                        .add(fixture.samples())
                        .add(fixture.bytes())
                        .add(fixture.sha256());
        return new EvidenceReport.FixtureFact(
                fixture.id(),
                fixture.samples(),
                digest.value(),
                Map.of("generated.dt" + fixture.level(), fixture.bytes() + ":" + fixture.sha256()));
    }

    static List<FeatureRecord> featureGrid(EvidenceConfiguration.Profile profile) {
        int side = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32;
        List<FeatureRecord> records = new ArrayList<>(side * side);
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                int ordinal = row * side + column;
                records.add(
                        new FeatureRecord(
                                "grid:" + ordinal,
                                "",
                                new PointGeometry(new Coordinate(column * 1_000.0, row * 1_000.0)),
                                Map.of()));
            }
        }
        return List.copyOf(records);
    }

    @SuppressWarnings("deprecation")
    static InMemoryLayer vectorLayer(EvidenceConfiguration.Profile profile) {
        int records = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 16;
        int parts = profile == EvidenceConfiguration.Profile.BASELINE ? 4 : 2;
        int vertices = profile == EvidenceConfiguration.Profile.BASELINE ? 64 : 16;
        List<Feature> features = new ArrayList<>();
        for (int record = 0; record < records; record++) {
            List<CoordinateSequence> sequences = new ArrayList<>();
            for (int part = 0; part < parts; part++) {
                double[] packed = new double[vertices * 2];
                for (int vertex = 0; vertex < vertices; vertex++) {
                    packed[vertex * 2] = record * 4_096.0 + part * 512.0 + vertex * 8.0;
                    packed[vertex * 2 + 1] =
                            (record % 64) * 2_048.0
                                    + part * 256.0
                                    + (vertex % 2 == 0 ? 96.0 : -96.0)
                                    + vertex * 4.0;
                }
                sequences.add(CoordinateSequence.of(packed));
            }
            features.add(
                    new Feature(
                            String.format(java.util.Locale.ROOT, "line:%03d", record),
                            "",
                            MultiLineStringGeometry.ofParts(sequences),
                            Map.of(),
                            FeatureStyle.line(Rgba.rgb(18, 54, 40), 2.0)));
        }
        int polygons = profile == EvidenceConfiguration.Profile.BASELINE ? 128 : 8;
        for (int record = 0; record < polygons; record++) {
            double x = 2_000_000.0 + (record % 16) * 80_000.0;
            double y = Math.floorDiv(record, 16) * 80_000.0;
            PolygonGeometry first =
                    new PolygonGeometry(
                            steppedRectangle(x, y, x + 30_000.0, y + 60_000.0, 16, true),
                            List.of(
                                    steppedRectangle(
                                            x + 8_000.0,
                                            y + 15_000.0,
                                            x + 22_000.0,
                                            y + 45_000.0,
                                            8,
                                            false)));
            PolygonGeometry second =
                    new PolygonGeometry(
                            steppedRectangle(x + 40_000.0, y, x + 70_000.0, y + 60_000.0, 16, true),
                            List.of(
                                    steppedRectangle(
                                            x + 48_000.0,
                                            y + 15_000.0,
                                            x + 62_000.0,
                                            y + 45_000.0,
                                            8,
                                            false)));
            features.add(
                    new Feature(
                            String.format(java.util.Locale.ROOT, "polygon:%03d", record),
                            "",
                            MultiPolygonGeometry.ofPolygons(List.of(first, second)),
                            Map.of(),
                            FeatureStyle.polygon(
                                    Rgba.rgb(30, 90, 60), Rgba.rgb(150, 210, 170), 2.0)));
        }
        return new InMemoryLayer("vector-path-v1", "vector-path-v1", features);
    }

    static List<FeatureRecord> vectorRecords(EvidenceConfiguration.Profile profile) {
        return vectorLayer(profile).features().stream()
                .map(
                        feature ->
                                new FeatureRecord(
                                        feature.id(),
                                        feature.name(),
                                        feature.geometry(),
                                        feature.attributes()))
                .toList();
    }

    static List<InMemoryLayer> symbolLayers(EvidenceConfiguration.Profile profile) {
        int points = profile == EvidenceConfiguration.Profile.BASELINE ? 3_072 : 192;
        int lines = profile == EvidenceConfiguration.Profile.BASELINE ? 512 : 32;
        int polygons = profile == EvidenceConfiguration.Profile.BASELINE ? 512 : 32;
        List<Symbol> pointSymbols = pointSymbols();
        List<Feature> pointFeatures = new ArrayList<>(points);
        for (int ordinal = 0; ordinal < points; ordinal++) {
            int column = ordinal % 64;
            int row = ordinal / 64;
            pointFeatures.add(
                    new Feature(
                            String.format(java.util.Locale.ROOT, "symbol:point:%04d", ordinal),
                            "",
                            new PointGeometry(new Coordinate(column * 20_000.0, row * 20_000.0)),
                            Map.of(),
                            pointSymbols.get(ordinal % pointSymbols.size())));
        }
        SymbolStroke lineStroke = stroke(Rgba.rgb(18, 54, 40), 2.0);
        Symbol circle =
                BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(18, 54, 40), 8.0, 1.0);
        Symbol arrow =
                BuiltInMarkers.filledScreen(BuiltInMarker.ARROW, Rgba.rgb(18, 54, 40), 8.0, 1.0);
        List<Feature> lineFeatures = new ArrayList<>(lines);
        for (int ordinal = 0; ordinal < lines; ordinal++) {
            double x = 20_000.0 * (ordinal % 32);
            double y = 20_000.0 * Math.floorDiv(ordinal, 32);
            Symbol endpoint = ordinal % 2 == 0 ? circle : arrow;
            lineFeatures.add(
                    new Feature(
                            String.format(java.util.Locale.ROOT, "symbol:line:%04d", ordinal),
                            "",
                            new LineStringGeometry(
                                    CoordinateSequence.of(
                                            x,
                                            y,
                                            x + 6_000.0,
                                            y + 2_000.0,
                                            x + 12_000.0,
                                            y - 2_000.0,
                                            x + 18_000.0,
                                            y)),
                            Map.of(),
                            SolidLineSymbol.of(
                                    lineStroke,
                                    Optional.of(endpoint),
                                    Optional.of(endpoint),
                                    1.0)));
        }
        List<Feature> polygonFeatures = new ArrayList<>(polygons);
        for (int ordinal = 0; ordinal < polygons; ordinal++) {
            double x = 20_000.0 * (ordinal % 32);
            double y = 20_000.0 * Math.floorDiv(ordinal, 32);
            polygonFeatures.add(
                    new Feature(
                            String.format(java.util.Locale.ROOT, "symbol:polygon:%04d", ordinal),
                            "",
                            new PolygonGeometry(rectangle(x, y, x + 12_000.0, y + 10_000.0)),
                            Map.of(),
                            HatchFillSymbol.of(
                                    HATCHES.get(ordinal % HATCHES.size()),
                                    stroke(Rgba.rgb(42, 132, 96), 2.0),
                                    new SymbolLength(8.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolRotationMode.SCREEN_RELATIVE,
                                    1.0)));
        }
        return List.of(
                new InMemoryLayer("symbol-points-v1", "symbol points", pointFeatures),
                new InMemoryLayer("symbol-lines-v1", "symbol lines", lineFeatures),
                new InMemoryLayer("symbol-polygons-v1", "symbol polygons", polygonFeatures));
    }

    static List<FeatureRecord> hitRecords(int binding, EvidenceConfiguration.Profile profile) {
        int count = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 32;
        List<FeatureRecord> records = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int logical =
                    profile == EvidenceConfiguration.Profile.BASELINE
                            ? ordinal
                            : smokeProbe(ordinal);
            double x = 10_000.0 * (logical % 16);
            double y = 10_000.0 * Math.floorDiv(logical, 16);
            Geometry geometry;
            if (logical >= 128 && logical < 192) {
                geometry =
                        new PolygonGeometry(
                                rectangle(x - 3_000, y - 3_000, x + 3_000, y + 3_000),
                                List.of(rectangle(x - 1_000, y - 1_000, x + 1_000, y + 1_000)));
            } else {
                double shift =
                        logical < 64 || (logical < 128 && binding == 0)
                                ? 0.0
                                : logical < 128 ? 6_000.0 : 5_000.0;
                geometry = hitGeometry(logical, x + shift, y + shift);
            }
            records.add(
                    new FeatureRecord("hit:" + binding + ':' + logical, "", geometry, Map.of()));
        }
        return List.copyOf(records);
    }

    private static int smokeProbe(int ordinal) {
        int group = ordinal / 8;
        return group * 64 + ordinal % 8;
    }

    private static Geometry hitGeometry(int ordinal, double x, double y) {
        return switch (ordinal % 3) {
            case 0 -> new PointGeometry(new Coordinate(x, y));
            case 1 ->
                    new LineStringGeometry(
                            CoordinateSequence.of(x - 3_000, y - 3_000, x + 3_000, y + 3_000));
            default -> new PolygonGeometry(rectangle(x - 3_000, y - 3_000, x + 3_000, y + 3_000));
        };
    }

    private static List<Symbol> pointSymbols() {
        List<Symbol> result = new ArrayList<>();
        for (BuiltInMarker marker : MARKERS) {
            result.add(BuiltInMarkers.filledScreen(marker, Rgba.rgb(36, 144, 94), 18.0, 1.0));
        }
        VectorPath nativePath =
                VectorPath.builder()
                        .moveTo(-0.5, 0.5)
                        .lineTo(-0.5, 0.0)
                        .quadraticTo(-0.5, -0.5, 0.0, -0.5)
                        .cubicTo(0.3, -0.5, 0.5, -0.3, 0.5, 0.0)
                        .lineTo(0.5, 0.5)
                        .close()
                        .build();
        result.add(
                VectorMarkerSymbol.filledScreen(
                        nativePath,
                        new Envelope(-0.5, -0.5, 0.5, 0.5),
                        Rgba.rgb(36, 144, 94),
                        18.0,
                        1.0));
        result.add(
                RasterIconSymbol.nativeScreenSize(
                        4,
                        2,
                        new int[] {
                            0x24905eff, 0x24905eff, 0xffff00ff, 0xffff00ff,
                            0xffff00ff, 0xffff00ff, 0x24905eff, 0x24905eff
                        },
                        RasterInterpolation.NEAREST,
                        1.0));
        Symbol composite =
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.SQUARE, Rgba.rgb(30, 90, 190), 18, 1),
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(240, 205, 35), 12, 1)),
                        1.0);
        result.add(composite);
        MarkerPlacement transformedSquare =
                new MarkerPlacement(
                        SymbolSize.square(18, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        3,
                        -2,
                        30,
                        SymbolRotationMode.SCREEN_RELATIVE);
        MarkerPlacement transformedDiamond =
                new MarkerPlacement(
                        SymbolSize.square(12, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        3,
                        -2,
                        30,
                        SymbolRotationMode.SCREEN_RELATIVE);
        result.add(
                CompositeSymbol.of(
                        List.of(
                                VectorMarkerSymbol.of(
                                        BuiltInMarkers.path(BuiltInMarker.SQUARE),
                                        BuiltInMarkers.viewBox(),
                                        Rgba.rgb(30, 90, 190),
                                        Optional.empty(),
                                        transformedSquare,
                                        1),
                                VectorMarkerSymbol.of(
                                        BuiltInMarkers.path(BuiltInMarker.DIAMOND),
                                        BuiltInMarkers.viewBox(),
                                        Rgba.rgb(240, 205, 35),
                                        Optional.empty(),
                                        transformedDiamond,
                                        1)),
                        1));
        return List.copyOf(result);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static EvidenceReport.FixtureFact factFromRecords(
            String id, List<FeatureRecord> records) {
        FnvOracle digest = new FnvOracle(EvidenceConfiguration.SEED).add(id).add(records.size());
        records.forEach(record -> ObservationDigests.addRecord(digest, record));
        return new EvidenceReport.FixtureFact(id, records.size(), digest.value(), Map.of());
    }

    private static EvidenceReport.FixtureFact factFromFeatures(
            String id, long count, List<Feature> features) {
        FnvOracle digest = new FnvOracle(EvidenceConfiguration.SEED).add(id).add(count);
        features.forEach(feature -> ObservationDigests.addFeature(digest, feature));
        return new EvidenceReport.FixtureFact(id, count, digest.value(), Map.of());
    }

    private static EvidenceReport.FixtureFact factFromLayers(
            String id, long count, List<InMemoryLayer> layers) {
        FnvOracle digest = new FnvOracle(EvidenceConfiguration.SEED).add(id).add(count);
        for (InMemoryLayer layer : layers) {
            digest.add(layer.id()).add(layer.name());
            layer.features().forEach(feature -> ObservationDigests.addFeature(digest, feature));
        }
        return new EvidenceReport.FixtureFact(id, count, digest.value(), Map.of());
    }

    private static EvidenceReport.FixtureFact hitFact(
            EvidenceConfiguration.Profile profile, long probes) {
        String id = "hit-stack-v1";
        FnvOracle digest = new FnvOracle(EvidenceConfiguration.SEED).add(id).add(probes);
        for (int binding = 0; binding < 4; binding++) {
            digest.add(binding);
            hitRecords(binding, profile)
                    .forEach(record -> ObservationDigests.addRecord(digest, record));
        }
        return new EvidenceReport.FixtureFact(id, probes, digest.value(), Map.of());
    }

    private static EvidenceReport.FixtureFact shapefileFact(
            EvidenceConfiguration.Profile profile, int columns, int rows, long count) {
        String id = "shapefile-grid-v1";
        FnvOracle digest =
                new FnvOracle(EvidenceConfiguration.SEED)
                        .add(id)
                        .add(columns)
                        .add(rows)
                        .add(count)
                        .add("point-row-major-1000")
                        .add("ID:N(10,0)")
                        .add("GROUP:C(8)")
                        .add("UTF-8")
                        .add("EPSG:3857");
        for (int ordinal = 0; ordinal < count; ordinal++) {
            digest.add(ordinal + 1)
                    .add((ordinal % columns) * 1_000.0)
                    .add(Math.floorDiv(ordinal, columns) * 1_000.0)
                    .add(
                            "group-"
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%02d",
                                            (ordinal / columns) % 20));
        }
        Map<String, String> files =
                profile == EvidenceConfiguration.Profile.BASELINE
                        ? Map.of(
                                "grid.shp",
                                "1400100:4118faa3e09bb3003139195d52c0f50f47235e700e17751b277e5a5ca619c687",
                                "grid.shx",
                                "400100:29e5ddc9ac75a1a588c165ed5b374fd2fc34b2cb0accb5df0ece2c6eb987239a",
                                "grid.dbf",
                                "950097:a745fcd1792fc26f1ba88a0bc4677f881e0e599f98970164122fbe7aec06cdad",
                                "grid.cpg",
                                "6:146d6789ffe033a5297c1ad046e6a62ee35319b86b021444f05b6ea2aa8a1f4a",
                                "grid.prj",
                                "413:4f01f36bf95963fb9174c50d396f1201b266ef7fa43304e718a7cb324ad71394")
                        : Map.of();
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> digest.add(entry.getKey()).add(entry.getValue()));
        return new EvidenceReport.FixtureFact(id, count, digest.value(), files);
    }

    private static EvidenceReport.FixtureFact rasterFact() {
        String id = "raster-1024x768-v1";
        Map<String, String> files =
                Map.of(
                        "PROVENANCE.md",
                        "792:61ba3aa19537c12a8ab36a31bc34ed7d7ac4fb98ec822acecdb4b0159838a133",
                        "evidence.jgw",
                        "24:a82e8504957403224b81cf68a85aec058e0a410c70cabd14a6e9f3158dad8de3",
                        "evidence.jpg",
                        "14106:5afcf4a3fbaf2c4b03f2a12929afc4a3c96b7faee378778e3b4a4dd013d7c731",
                        "evidence.pgw",
                        "24:a82e8504957403224b81cf68a85aec058e0a410c70cabd14a6e9f3158dad8de3",
                        "evidence.png",
                        "1178082:1d7a32d6c8901637d684f6061e1e0563243a118e4b813f94fc7985460a4050a2");
        FnvOracle digest =
                new FnvOracle(EvidenceConfiguration.SEED)
                        .add(id)
                        .add(1_024)
                        .add(768)
                        .add("rgba:x,y,xor,alpha-mod5")
                        .add("jpeg:64x64-tile-formula")
                        .add(2.0)
                        .add(0.25)
                        .add(0.5)
                        .add(-2.0)
                        .add(1_000.0)
                        .add(2_000.0);
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> digest.add(entry.getKey()).add(entry.getValue()));
        return new EvidenceReport.FixtureFact(id, 786_432, digest.value(), files);
    }

    private static long coordinateCount(Geometry geometry) {
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.coordinates().size();
        }
        throw new IllegalArgumentException("Unexpected vector fixture geometry");
    }

    private static CoordinateSequence rectangle(
            double minX, double minY, double maxX, double maxY) {
        return CoordinateSequence.of(minX, minY, maxX, minY, maxX, maxY, minX, maxY, minX, minY);
    }

    private static CoordinateSequence steppedRectangle(
            double minX, double minY, double maxX, double maxY, int steps, boolean clockwise) {
        double[] packed = new double[(4 * steps + 1) * 2];
        double[][] corners =
                clockwise
                        ? new double[][] {
                            {minX, minY}, {minX, maxY}, {maxX, maxY}, {maxX, minY}, {minX, minY}
                        }
                        : new double[][] {
                            {minX, minY}, {maxX, minY}, {maxX, maxY}, {minX, maxY}, {minX, minY}
                        };
        int target = 0;
        for (int edge = 0; edge < 4; edge++) {
            for (int step = 0; step < steps; step++) {
                double fraction = (double) step / steps;
                packed[target++] =
                        corners[edge][0] + fraction * (corners[edge + 1][0] - corners[edge][0]);
                packed[target++] =
                        corners[edge][1] + fraction * (corners[edge + 1][1] - corners[edge][1]);
            }
        }
        packed[target++] = corners[4][0];
        packed[target] = corners[4][1];
        return CoordinateSequence.of(packed);
    }
}
