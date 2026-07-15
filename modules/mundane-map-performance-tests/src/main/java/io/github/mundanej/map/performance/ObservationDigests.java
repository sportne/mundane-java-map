package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.MapViewport;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

final class ObservationDigests {
    private ObservationDigests() {}

    static EvidenceObservation observation(
            EvidenceConfiguration.Profile profile,
            String scenarioId,
            Map<String, Long> counters,
            Consumer<FnvOracle> content) {
        FnvOracle digest =
                new FnvOracle(EvidenceConfiguration.SEED).add(profile.name()).add(scenarioId);
        content.accept(digest);
        counters.forEach((key, value) -> digest.add(key).add(value));
        return new EvidenceObservation(digest.value(), counters);
    }

    static void addRecord(FnvOracle digest, FeatureRecord record) {
        digest.add(record.id()).add(record.name());
        addGeometry(digest, record.geometry());
        addAttributes(digest, record.attributes());
    }

    static void addFeature(FnvOracle digest, Feature feature) {
        digest.add(feature.id()).add(feature.name());
        addGeometry(digest, feature.geometry());
        addAttributes(digest, feature.attributes());
        addSymbol(digest, feature.symbol());
    }

    static void addHits(FnvOracle digest, List<MapHit> hits) {
        digest.add(hits.size());
        for (MapHit hit : hits) {
            digest.add(hit.layerId()).add(hit.featureId());
        }
    }

    static void addDiagnostics(FnvOracle digest, DiagnosticReport report) {
        digest.add(report.entries().size()).add(report.omittedWarningCount());
        for (SourceDiagnostic diagnostic : report.entries()) {
            digest.add(diagnostic.code())
                    .add(diagnostic.severity())
                    .add(diagnostic.sourceId())
                    .add(diagnostic.location().map(Object::toString).orElse(""))
                    .add(diagnostic.message());
            diagnostic.context().forEach((key, value) -> digest.add(key).add(value));
        }
    }

    static void addPngPixels(FnvOracle digest, RasterRead read) {
        int width = read.pixels().width();
        int height = read.pixels().height();
        digest.add(width).add(height);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                digest.addPackedRgba(read.pixels().rgbaAt(column, row));
            }
        }
    }

    static void addJpegInteriorProbes(FnvOracle digest, RasterRead read, RasterRequest request) {
        List<JpegProbe> probes = jpegProbes(request);
        digest.add(probes.size());
        for (JpegProbe probe : probes) {
            int actual = read.pixels().rgbaAt(probe.outputColumn(), probe.outputRow());
            if (!withinRgbaTolerance(actual, probe.expectedRgba(), 20)) {
                throw new IllegalStateException("JPEG interior probe exceeded tolerance");
            }
            digest.add(probe.outputColumn())
                    .add(probe.outputRow())
                    .addPackedRgba(probe.expectedRgba())
                    .add("JPEG_INTERIOR_WITHIN_TOLERANCE");
        }
    }

    static List<JpegProbe> jpegProbes(RasterRequest request) {
        List<JpegProbe> result = new java.util.ArrayList<>();
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
            throw new IllegalStateException("JPEG request did not expose eight interior probes");
        }
        return List.copyOf(result);
    }

    static RenderInvariants renderInvariants(
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
                    if (!differentBeyondTolerance(pixel, background)) {
                        backgroundMatches++;
                    }
                }
                if (differentBeyondTolerance(pixel, background)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    painted++;
                    if (differentRgbBeyondTolerance(pixel, background)) {
                        expectedColor++;
                    }
                    if (Math.abs(((pixel >>> 24) & 0xff) - ((background >>> 24) & 0xff)) <= 8) {
                        expectedAlpha++;
                    }
                }
            }
        }
        long pixels = Math.multiplyExact((long) image.getWidth(), image.getHeight());
        requireMajority(backgroundMatches, border, "Background majority changed");
        requireMajority(expectedColor, painted, "Expected color majority changed");
        requireMajority(expectedAlpha, painted, "Expected alpha majority changed");
        if (painted == 0
                || minX < 0
                || minY < 0
                || maxX >= image.getWidth()
                || maxY >= image.getHeight()) {
            throw new IllegalStateException("Paint bounds are not contained");
        }
        if (painted < 16 || painted >= pixels) {
            throw new IllegalStateException("Paint count is outside the portable range");
        }
        if (!viewportMatches(actualViewport, expectedViewport)) {
            throw new IllegalStateException("Viewport changed outside the declared trace");
        }
        return new RenderInvariants(
                List.of(
                        new RenderInvariant("background", RenderClassification.BACKGROUND_MAJORITY),
                        new RenderInvariant(
                                "expectedColor", RenderClassification.EXPECTED_COLOR_MAJORITY),
                        new RenderInvariant(
                                "expectedAlpha", RenderClassification.EXPECTED_ALPHA_MAJORITY),
                        new RenderInvariant(
                                "paintBounds", RenderClassification.PAINT_BOUNDS_CONTAINED),
                        new RenderInvariant(
                                "paintCount", RenderClassification.PAINT_COUNT_IN_RANGE),
                        new RenderInvariant("viewport", RenderClassification.VIEWPORT_MATCH)));
    }

    static void addRenderInvariants(FnvOracle digest, RenderInvariants invariants) {
        digest.add(invariants.values().size());
        invariants.values().forEach(value -> digest.add(value.name()).add(value.classification()));
    }

    static void addGeometry(FnvOracle digest, Geometry geometry) {
        digest.add(geometry.getClass().getSimpleName());
        if (geometry instanceof PointGeometry point) {
            digest.add(point.coordinate().x()).add(point.coordinate().y());
        } else if (geometry instanceof LineStringGeometry line) {
            addCoordinates(digest, line.coordinates());
        } else if (geometry instanceof MultiPointGeometry points) {
            addCoordinates(digest, points.coordinates());
        } else if (geometry instanceof MultiLineStringGeometry lines) {
            addCoordinates(digest, lines.coordinates());
            digest.add(lines.partCount());
            for (int index = 0; index <= lines.partCount(); index++) {
                digest.add(lines.partOffset(index));
            }
        } else if (geometry instanceof PolygonGeometry polygon) {
            addCoordinates(digest, polygon.exterior());
            digest.add(polygon.holes().size());
            polygon.holes().forEach(hole -> addCoordinates(digest, hole));
        } else if (geometry instanceof MultiPolygonGeometry polygons) {
            addCoordinates(digest, polygons.coordinates());
            digest.add(polygons.ringCount()).add(polygons.polygonCount());
            for (int index = 0; index <= polygons.ringCount(); index++) {
                digest.add(polygons.ringOffset(index));
            }
            for (int index = 0; index <= polygons.polygonCount(); index++) {
                digest.add(polygons.polygonRingOffset(index));
            }
        } else {
            throw new IllegalArgumentException("Unsupported evidence geometry: " + geometry);
        }
    }

    @SuppressWarnings("deprecation")
    static void addSymbol(FnvOracle digest, Symbol symbol) {
        digest.add(symbol.getClass().getSimpleName()).add(symbol.opacity());
        if (symbol instanceof FeatureStyle style) {
            digest.add(style.stroke())
                    .add(style.fill())
                    .add(style.strokeWidth())
                    .add(style.pointDiameter());
        } else if (symbol instanceof VectorMarkerSymbol marker) {
            addVectorPath(digest, marker.path());
            digest.add(marker.viewBox().minX())
                    .add(marker.viewBox().minY())
                    .add(marker.viewBox().maxX())
                    .add(marker.viewBox().maxY())
                    .add(marker.fill());
            addOptionalStroke(digest, marker.stroke());
            addPlacement(digest, marker.placement());
        } else if (symbol instanceof RasterIconSymbol icon) {
            digest.add(icon.width()).add(icon.height());
            for (int pixel : icon.toRgbaArray()) {
                digest.addPackedRgba(pixel);
            }
            addPlacement(digest, icon.placement());
            digest.add(icon.interpolation());
        } else if (symbol instanceof CompositeSymbol composite) {
            digest.add(composite.children().size());
            composite.children().forEach(child -> addSymbol(digest, child));
        } else if (symbol instanceof SolidLineSymbol line) {
            addStroke(digest, line.stroke());
            addOptionalSymbol(digest, line.startMarker());
            addOptionalSymbol(digest, line.endMarker());
        } else if (symbol instanceof HatchFillSymbol hatch) {
            digest.add(hatch.pattern());
            addStroke(digest, hatch.stroke());
            digest.add(hatch.spacing().value())
                    .add(hatch.spacing().unit())
                    .add(hatch.rotationMode())
                    .add(hatch.maxSegments());
            addOptionalSymbol(digest, hatch.outline());
        } else if (symbol instanceof SolidFillSymbol fill) {
            digest.add(fill.fill());
            addOptionalSymbol(digest, fill.outline());
        } else {
            throw new IllegalArgumentException("Unsupported evidence symbol: " + symbol);
        }
    }

    private static void addVectorPath(FnvOracle digest, VectorPath path) {
        digest.add(path.commandCount()).add(path.ordinateCount());
        for (int index = 0; index < path.commandCount(); index++) {
            digest.add(path.commandAt(index));
        }
        for (double ordinate : path.toOrdinateArray()) {
            digest.add(ordinate);
        }
    }

    private static void addPlacement(
            FnvOracle digest, io.github.mundanej.map.api.MarkerPlacement placement) {
        digest.add(placement.size().width())
                .add(placement.size().height())
                .add(placement.size().unit())
                .add(placement.anchor())
                .add(placement.offsetX())
                .add(placement.offsetY())
                .add(placement.rotationDegrees())
                .add(placement.rotationMode());
    }

    private static void addOptionalStroke(
            FnvOracle digest, java.util.Optional<SymbolStroke> stroke) {
        digest.add(stroke.isPresent());
        stroke.ifPresent(value -> addStroke(digest, value));
    }

    private static void addStroke(FnvOracle digest, SymbolStroke stroke) {
        digest.add(stroke.color()).add(stroke.width().value()).add(stroke.width().unit());
    }

    private static void addOptionalSymbol(FnvOracle digest, java.util.Optional<Symbol> symbol) {
        digest.add(symbol.isPresent());
        symbol.ifPresent(value -> addSymbol(digest, value));
    }

    private static void addCoordinates(FnvOracle digest, CoordinateSequence coordinates) {
        digest.add(coordinates.size());
        for (int index = 0; index < coordinates.size(); index++) {
            digest.add(coordinates.x(index)).add(coordinates.y(index));
        }
    }

    private static void addAttributes(FnvOracle digest, Map<String, Object> attributes) {
        TreeMap<String, Object> sorted = new TreeMap<>(attributes);
        digest.add(sorted.size());
        sorted.forEach((key, value) -> addAttribute(digest.add(key), value));
    }

    private static void addAttribute(FnvOracle digest, Object value) {
        if (value instanceof String text) {
            digest.add("STRING").add(text);
        } else if (value instanceof Boolean flag) {
            digest.add("BOOLEAN").add(flag);
        } else if (value instanceof Long number) {
            digest.add("LONG").add(number);
        } else if (value instanceof Double number) {
            digest.add("DOUBLE").add(number);
        } else if (value instanceof BigDecimal number) {
            digest.add("DECIMAL").add(number.toPlainString());
        } else if (value instanceof LocalDate date) {
            digest.add("DATE").add(date.toEpochDay());
        } else if (value == AttributeNull.INSTANCE) {
            digest.add("NULL");
        } else if (value instanceof AttributeBytes bytes) {
            digest.add("BYTES").add(bytes.length());
            for (int index = 0; index < bytes.length(); index++) {
                digest.add((int) bytes.byteAt(index));
            }
        } else {
            throw new IllegalArgumentException(
                    "Unsupported evidence attribute: " + value.getClass());
        }
    }

    private static boolean differentBeyondTolerance(int first, int second) {
        for (int shift = 0; shift <= 24; shift += 8) {
            int one = (first >>> shift) & 0xff;
            int two = (second >>> shift) & 0xff;
            if (Math.abs(one - two) > 8) {
                return true;
            }
        }
        return false;
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

    private static boolean differentRgbBeyondTolerance(int first, int second) {
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

    private static boolean viewportMatches(MapViewport first, MapViewport second) {
        return first.width() == second.width()
                && first.height() == second.height()
                && close(first.centerX(), second.centerX())
                && close(first.centerY(), second.centerY())
                && close(first.worldUnitsPerPixel(), second.worldUnitsPerPixel());
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= 4 * Math.ulp(second);
    }

    enum RenderClassification {
        BACKGROUND_MAJORITY,
        EXPECTED_COLOR_MAJORITY,
        EXPECTED_ALPHA_MAJORITY,
        PAINT_BOUNDS_CONTAINED,
        PAINT_COUNT_IN_RANGE,
        VIEWPORT_MATCH
    }

    record RenderInvariant(String name, RenderClassification classification) {}

    record RenderInvariants(List<RenderInvariant> values) {
        RenderInvariants {
            values = List.copyOf(values);
        }
    }

    record JpegProbe(int outputColumn, int outputRow, int expectedRgba) {}
}
