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
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Test-side implementation of the documented typed FNV-1a observation encoding. */
final class ReferenceDigest {
    private static final long OFFSET = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;
    private long value = OFFSET;

    ReferenceDigest(long seed) {
        tag(0x03);
        rawLong(seed);
    }

    ReferenceDigest text(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        tag(0x01);
        rawInt(bytes.length);
        bytes(bytes);
        return this;
    }

    ReferenceDigest integer(int number) {
        tag(0x02);
        rawInt(number);
        return this;
    }

    ReferenceDigest longInteger(long number) {
        tag(0x03);
        rawLong(number);
        return this;
    }

    ReferenceDigest decimal(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Reference digest doubles must be finite");
        }
        tag(0x04);
        rawLong(Double.doubleToLongBits(number == 0.0 ? 0.0 : number));
        return this;
    }

    ReferenceDigest flag(boolean flag) {
        tag(0x05);
        octet(flag ? 1 : 0);
        return this;
    }

    ReferenceDigest enumeration(Enum<?> item) {
        byte[] bytes = item.name().getBytes(StandardCharsets.UTF_8);
        tag(0x06);
        rawInt(bytes.length);
        bytes(bytes);
        return this;
    }

    ReferenceDigest color(Rgba color) {
        return packedRgba(
                (color.red() << 24) | (color.green() << 16) | (color.blue() << 8) | color.alpha());
    }

    ReferenceDigest packedRgba(int rgba) {
        tag(0x07);
        octet(rgba >>> 24);
        octet(rgba >>> 16);
        octet(rgba >>> 8);
        octet(rgba);
        return this;
    }

    long value() {
        return value;
    }

    static void record(ReferenceDigest digest, FeatureRecord record) {
        digest.text(record.id()).text(record.name());
        geometry(digest, record.geometry());
        attributes(digest, record.attributes());
    }

    static void feature(ReferenceDigest digest, Feature feature) {
        digest.text(feature.id()).text(feature.name());
        geometry(digest, feature.geometry());
        attributes(digest, feature.attributes());
        symbol(digest, feature.symbol());
    }

    static void hits(ReferenceDigest digest, List<MapHit> hits) {
        digest.integer(hits.size());
        hits.forEach(hit -> digest.text(hit.layerId()).text(hit.featureId()));
    }

    static void diagnostics(ReferenceDigest digest, DiagnosticReport report) {
        digest.integer(report.entries().size()).longInteger(report.omittedWarningCount());
        for (SourceDiagnostic diagnostic : report.entries()) {
            digest.text(diagnostic.code())
                    .enumeration(diagnostic.severity())
                    .text(diagnostic.sourceId())
                    .text(diagnostic.location().map(Object::toString).orElse(""))
                    .text(diagnostic.message());
            diagnostic.context().forEach((key, value) -> digest.text(key).text(value));
        }
    }

    static void renderInvariants(ReferenceDigest digest, RenderInvariants invariants) {
        digest.integer(invariants.values().size());
        invariants
                .values()
                .forEach(value -> digest.text(value.name()).enumeration(value.classification()));
    }

    private static void geometry(ReferenceDigest digest, Geometry geometry) {
        digest.text(geometry.getClass().getSimpleName());
        if (geometry instanceof PointGeometry point) {
            digest.decimal(point.coordinate().x()).decimal(point.coordinate().y());
        } else if (geometry instanceof LineStringGeometry line) {
            coordinates(digest, line.coordinates());
        } else if (geometry instanceof MultiPointGeometry points) {
            coordinates(digest, points.coordinates());
        } else if (geometry instanceof MultiLineStringGeometry lines) {
            coordinates(digest, lines.coordinates());
            digest.integer(lines.partCount());
            for (int index = 0; index <= lines.partCount(); index++) {
                digest.integer(lines.partOffset(index));
            }
        } else if (geometry instanceof PolygonGeometry polygon) {
            coordinates(digest, polygon.exterior());
            digest.integer(polygon.holes().size());
            polygon.holes().forEach(hole -> coordinates(digest, hole));
        } else if (geometry instanceof MultiPolygonGeometry polygons) {
            coordinates(digest, polygons.coordinates());
            digest.integer(polygons.ringCount()).integer(polygons.polygonCount());
            for (int index = 0; index <= polygons.ringCount(); index++) {
                digest.integer(polygons.ringOffset(index));
            }
            for (int index = 0; index <= polygons.polygonCount(); index++) {
                digest.integer(polygons.polygonRingOffset(index));
            }
        } else {
            throw new IllegalArgumentException("Unsupported reference geometry: " + geometry);
        }
    }

    @SuppressWarnings("deprecation")
    private static void symbol(ReferenceDigest digest, Symbol symbol) {
        digest.text(symbol.getClass().getSimpleName()).decimal(symbol.opacity());
        if (symbol instanceof FeatureStyle style) {
            digest.color(style.stroke())
                    .color(style.fill())
                    .decimal(style.strokeWidth())
                    .decimal(style.pointDiameter());
        } else if (symbol instanceof VectorMarkerSymbol marker) {
            vectorPath(digest, marker.path());
            digest.decimal(marker.viewBox().minX())
                    .decimal(marker.viewBox().minY())
                    .decimal(marker.viewBox().maxX())
                    .decimal(marker.viewBox().maxY())
                    .color(marker.fill());
            optionalStroke(digest, marker.stroke());
            placement(digest, marker.placement());
        } else if (symbol instanceof RasterIconSymbol icon) {
            digest.integer(icon.width()).integer(icon.height());
            for (int pixel : icon.toRgbaArray()) {
                digest.packedRgba(pixel);
            }
            placement(digest, icon.placement());
            digest.enumeration(icon.interpolation());
        } else if (symbol instanceof CompositeSymbol composite) {
            digest.integer(composite.children().size());
            composite.children().forEach(child -> symbol(digest, child));
        } else if (symbol instanceof SolidLineSymbol line) {
            stroke(digest, line.stroke());
            optionalSymbol(digest, line.startMarker());
            optionalSymbol(digest, line.endMarker());
        } else if (symbol instanceof HatchFillSymbol hatch) {
            digest.enumeration(hatch.pattern());
            stroke(digest, hatch.stroke());
            digest.decimal(hatch.spacing().value())
                    .enumeration(hatch.spacing().unit())
                    .enumeration(hatch.rotationMode())
                    .integer(hatch.maxSegments());
            optionalSymbol(digest, hatch.outline());
        } else if (symbol instanceof SolidFillSymbol fill) {
            digest.color(fill.fill());
            optionalSymbol(digest, fill.outline());
        } else {
            throw new IllegalArgumentException("Unsupported reference symbol: " + symbol);
        }
    }

    private static void vectorPath(ReferenceDigest digest, VectorPath path) {
        digest.integer(path.commandCount()).integer(path.ordinateCount());
        for (int index = 0; index < path.commandCount(); index++) {
            digest.enumeration(path.commandAt(index));
        }
        for (double ordinate : path.toOrdinateArray()) {
            digest.decimal(ordinate);
        }
    }

    private static void placement(
            ReferenceDigest digest, io.github.mundanej.map.api.MarkerPlacement placement) {
        digest.decimal(placement.size().width())
                .decimal(placement.size().height())
                .enumeration(placement.size().unit())
                .enumeration(placement.anchor())
                .decimal(placement.offsetX())
                .decimal(placement.offsetY())
                .decimal(placement.rotationDegrees())
                .enumeration(placement.rotationMode());
    }

    private static void optionalStroke(
            ReferenceDigest digest, java.util.Optional<SymbolStroke> stroke) {
        digest.flag(stroke.isPresent());
        stroke.ifPresent(value -> stroke(digest, value));
    }

    private static void stroke(ReferenceDigest digest, SymbolStroke stroke) {
        digest.color(stroke.color())
                .decimal(stroke.width().value())
                .enumeration(stroke.width().unit());
    }

    private static void optionalSymbol(ReferenceDigest digest, java.util.Optional<Symbol> symbol) {
        digest.flag(symbol.isPresent());
        symbol.ifPresent(value -> symbol(digest, value));
    }

    private static void coordinates(ReferenceDigest digest, CoordinateSequence coordinates) {
        digest.integer(coordinates.size());
        for (int index = 0; index < coordinates.size(); index++) {
            digest.decimal(coordinates.x(index)).decimal(coordinates.y(index));
        }
    }

    private static void attributes(ReferenceDigest digest, Map<String, Object> attributes) {
        TreeMap<String, Object> sorted = new TreeMap<>(attributes);
        digest.integer(sorted.size());
        sorted.forEach((key, value) -> attribute(digest.text(key), value));
    }

    private static void attribute(ReferenceDigest digest, Object value) {
        if (value instanceof String text) {
            digest.text("STRING").text(text);
        } else if (value instanceof Boolean flag) {
            digest.text("BOOLEAN").flag(flag);
        } else if (value instanceof Long number) {
            digest.text("LONG").longInteger(number);
        } else if (value instanceof Double number) {
            digest.text("DOUBLE").decimal(number);
        } else if (value instanceof BigDecimal number) {
            digest.text("DECIMAL").text(number.toPlainString());
        } else if (value instanceof LocalDate date) {
            digest.text("DATE").longInteger(date.toEpochDay());
        } else if (value == AttributeNull.INSTANCE) {
            digest.text("NULL");
        } else if (value instanceof AttributeBytes bytes) {
            digest.text("BYTES").integer(bytes.length());
            for (int index = 0; index < bytes.length(); index++) {
                digest.integer(bytes.byteAt(index));
            }
        } else {
            throw new IllegalArgumentException(
                    "Unsupported reference attribute: " + value.getClass());
        }
    }

    private void tag(int tag) {
        octet(tag);
    }

    private void rawInt(int number) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            octet(number >>> shift);
        }
    }

    private void rawLong(long number) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            octet((int) (number >>> shift));
        }
    }

    private void bytes(byte[] bytes) {
        for (byte item : bytes) {
            octet(item);
        }
    }

    private void octet(int item) {
        value ^= item & 0xff;
        value *= PRIME;
    }

    record RenderInvariants(List<RenderInvariant> values) {}

    record RenderInvariant(String name, RenderClassification classification) {}

    enum RenderClassification {
        BACKGROUND_MAJORITY,
        EXPECTED_COLOR_MAJORITY,
        EXPECTED_ALPHA_MAJORITY,
        PAINT_BOUNDS_CONTAINED,
        PAINT_COUNT_IN_RANGE,
        VIEWPORT_MATCH
    }
}
