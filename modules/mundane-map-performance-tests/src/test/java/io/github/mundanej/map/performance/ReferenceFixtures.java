package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
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

/** Independent, formula-based reconstruction of each generated evidence fixture. */
final class ReferenceFixtures {
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

    private ReferenceFixtures() {}

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

    static List<Integer> indexSizes() {
        return List.of(32, 128, 512, 2_048, 8_192, 32_768, 131_072);
    }

    static List<FeatureRecord> indexRecords(int size) {
        int position = indexSizes().indexOf(size);
        if (position < 0) {
            throw new IllegalArgumentException("Unknown comparison size");
        }
        int columns = 8 << position;
        List<FeatureRecord> records = new ArrayList<>(size);
        for (int ordinal = 0; ordinal < size; ordinal++) {
            records.add(
                    new FeatureRecord(
                            String.format(java.util.Locale.ROOT, "index:%06d", ordinal),
                            "",
                            new PointGeometry(
                                    new Coordinate(
                                            Math.floorMod(ordinal, columns) * 1_000.0,
                                            Math.floorDiv(ordinal, columns) * 1_000.0)),
                            Map.of()));
        }
        return List.copyOf(records);
    }

    static List<Envelope> indexViewports(int size, EvidenceConfiguration.Profile profile) {
        int position = indexSizes().indexOf(size);
        int columns = 8 << position;
        int rows = 4 << position;
        int count = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 24;
        List<Envelope> result = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int kind = Math.floorMod(ordinal, 6);
            double maxX = (columns - 1) * 1_000.0;
            double maxY = (rows - 1) * 1_000.0;
            if (kind == 0) {
                result.add(new Envelope(maxX + 500, maxY + 500, maxX + 1_500, maxY + 1_500));
            } else if (kind == 1) {
                int column = Math.floorMod(37 * ordinal, columns);
                int row = Math.floorMod(53 * ordinal, rows);
                double x = column * 1_000.0;
                double y = row * 1_000.0;
                result.add(new Envelope(x - 500, y - 500, x, y));
            } else if (kind == 5) {
                result.add(new Envelope(-500, -500, maxX + 500, maxY + 500));
            } else {
                int selected = indexExpectedRecords(size, ordinal);
                int exponent = Integer.numberOfTrailingZeros(selected);
                int width = 1 << Math.floorDiv(exponent, 2);
                int height = Math.floorDiv(selected, width);
                int originColumn = Math.floorMod(37 * ordinal, columns - width + 1);
                int originRow = Math.floorMod(53 * ordinal, rows - height + 1);
                result.add(
                        new Envelope(
                                originColumn * 1_000.0 - 500,
                                originRow * 1_000.0 - 500,
                                (originColumn + width - 1) * 1_000.0 + 500,
                                (originRow + height - 1) * 1_000.0 + 500));
            }
        }
        return List.copyOf(result);
    }

    static int indexExpectedRecords(int size, int ordinal) {
        return switch (Math.floorMod(ordinal, 6)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> Math.max(1, size / 1_024);
            case 3 -> Math.max(1, size / 128);
            case 4 -> Math.max(1, size / 8);
            case 5 -> size;
            default -> throw new AssertionError("unreachable");
        };
    }

    /** Reviewed exact candidate totals from the independent STR fixture definition. */
    static long indexCandidateTotal(EvidenceConfiguration.Profile profile, int size) {
        int index = indexSizes().indexOf(size);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown comparison size");
        }
        long[] totals =
                profile == EvidenceConfiguration.Profile.BASELINE
                        ? new long[] {4_176, 9_840, 29_648, 105_712, 405_712, 1_590_112, 6_296_784}
                        : new long[] {384, 928, 2_912, 10_064, 39_264, 151_200, 599_456};
        return totals[index];
    }

    @SuppressWarnings("deprecation")
    static List<FeatureRecord> vectorRecords(EvidenceConfiguration.Profile profile) {
        int lineCount = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 16;
        int parts = profile == EvidenceConfiguration.Profile.BASELINE ? 4 : 2;
        int vertices = profile == EvidenceConfiguration.Profile.BASELINE ? 64 : 16;
        List<FeatureRecord> records = new ArrayList<>();
        for (int ordinal = 0; ordinal < lineCount; ordinal++) {
            List<CoordinateSequence> sequences = new ArrayList<>();
            for (int part = 0; part < parts; part++) {
                double[] packed = new double[vertices * 2];
                for (int vertex = 0; vertex < vertices; vertex++) {
                    packed[vertex * 2] = ordinal * 4_096.0 + part * 512.0 + vertex * 8.0;
                    packed[vertex * 2 + 1] =
                            (ordinal % 64) * 2_048.0
                                    + part * 256.0
                                    + (vertex % 2 == 0 ? 96.0 : -96.0)
                                    + vertex * 4.0;
                }
                sequences.add(CoordinateSequence.of(packed));
            }
            records.add(
                    new FeatureRecord(
                            String.format(java.util.Locale.ROOT, "line:%03d", ordinal),
                            "",
                            MultiLineStringGeometry.ofParts(sequences),
                            Map.of()));
        }
        int polygonCount = profile == EvidenceConfiguration.Profile.BASELINE ? 128 : 8;
        for (int ordinal = 0; ordinal < polygonCount; ordinal++) {
            double x = 2_000_000.0 + (ordinal % 16) * 80_000.0;
            double y = Math.floorDiv(ordinal, 16) * 80_000.0;
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
            records.add(
                    new FeatureRecord(
                            String.format(java.util.Locale.ROOT, "polygon:%03d", ordinal),
                            "",
                            MultiPolygonGeometry.ofPolygons(List.of(first, second)),
                            Map.of()));
        }
        return List.copyOf(records);
    }

    static List<InMemoryLayer> symbolLayers(EvidenceConfiguration.Profile profile) {
        int points = profile == EvidenceConfiguration.Profile.BASELINE ? 3_072 : 192;
        int lines = profile == EvidenceConfiguration.Profile.BASELINE ? 512 : 32;
        int polygons = profile == EvidenceConfiguration.Profile.BASELINE ? 512 : 32;
        List<Symbol> symbols = pointSymbols();
        List<Feature> pointFeatures = new ArrayList<>(points);
        for (int ordinal = 0; ordinal < points; ordinal++) {
            pointFeatures.add(
                    new Feature(
                            String.format(java.util.Locale.ROOT, "symbol:point:%04d", ordinal),
                            "",
                            new PointGeometry(
                                    new Coordinate(
                                            ordinal % 64 * 20_000.0,
                                            Math.floorDiv(ordinal, 64) * 20_000.0)),
                            Map.of(),
                            symbols.get(ordinal % symbols.size())));
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
                            : ordinal / 8 * 64 + ordinal % 8;
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
        VectorPath path =
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
                        path,
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
        result.add(
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.SQUARE, Rgba.rgb(30, 90, 190), 18, 1),
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(240, 205, 35), 12, 1)),
                        1.0));
        MarkerPlacement square = placement(18);
        MarkerPlacement diamond = placement(12);
        result.add(
                CompositeSymbol.of(
                        List.of(
                                VectorMarkerSymbol.of(
                                        BuiltInMarkers.path(BuiltInMarker.SQUARE),
                                        BuiltInMarkers.viewBox(),
                                        Rgba.rgb(30, 90, 190),
                                        Optional.empty(),
                                        square,
                                        1),
                                VectorMarkerSymbol.of(
                                        BuiltInMarkers.path(BuiltInMarker.DIAMOND),
                                        BuiltInMarkers.viewBox(),
                                        Rgba.rgb(240, 205, 35),
                                        Optional.empty(),
                                        diamond,
                                        1)),
                        1));
        return List.copyOf(result);
    }

    private static MarkerPlacement placement(double size) {
        return new MarkerPlacement(
                SymbolSize.square(size, SymbolUnit.SCREEN_PIXEL),
                SymbolAnchor.CENTER,
                3,
                -2,
                30,
                SymbolRotationMode.SCREEN_RELATIVE);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
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
