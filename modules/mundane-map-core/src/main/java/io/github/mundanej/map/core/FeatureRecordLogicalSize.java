package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Shared deterministic logical feature-record measurement. */
final class FeatureRecordLogicalSize {
    private FeatureRecordLogicalSize() {}

    static long bytes(FeatureRecord record, int retainedReferenceSlots) {
        return bytes(record, retainedReferenceSlots, null);
    }

    static long bytes(
            FeatureRecord record, int retainedReferenceSlots, Runnable cancellationCheckpoint) {
        long coordinateCount = coordinateCount(record.geometry());
        long payload = Math.multiplyExact(coordinateCount, 16);
        payload = Math.addExact(payload, Math.multiplyExact(offsetCount(record.geometry()), 4));
        payload = Math.addExact(payload, Math.multiplyExact((long) retainedReferenceSlots, 8));
        payload =
                Math.addExact(
                        payload, Math.multiplyExact(geometryReferenceSlots(record.geometry()), 8));
        payload =
                Math.addExact(
                        payload,
                        Math.multiplyExact(
                                (long) record.id().length() + record.name().length(), 2));
        return Math.addExact(
                payload, attributePayload(record.attributes(), cancellationCheckpoint));
    }

    private static long coordinateCount(Geometry geometry) {
        return switch (geometry) {
            case PointGeometry ignored -> 1;
            case io.github.mundanej.map.api.LineStringGeometry line -> line.coordinates().size();
            case PolygonGeometry polygon -> polygonCoordinateCount(polygon);
            case MultiPointGeometry points -> points.coordinates().size();
            case MultiLineStringGeometry lines -> lines.coordinates().size();
            case MultiPolygonGeometry polygons -> polygons.coordinates().size();
        };
    }

    private static long polygonCoordinateCount(PolygonGeometry polygon) {
        long count = polygon.exterior().size();
        for (var hole : polygon.holes()) {
            count = Math.addExact(count, hole.size());
        }
        return count;
    }

    private static long offsetCount(Geometry geometry) {
        return switch (geometry) {
            case MultiLineStringGeometry lines -> lines.partCount() + 1L;
            case MultiPolygonGeometry polygons ->
                    Math.addExact(
                            Math.addExact((long) polygons.ringCount(), polygons.polygonCount()),
                            2L);
            default -> 0L;
        };
    }

    private static long geometryReferenceSlots(Geometry geometry) {
        return geometry instanceof PolygonGeometry polygon ? polygon.holes().size() : 0;
    }

    private static long attributePayload(
            Map<String, Object> values, Runnable cancellationCheckpoint) {
        long total = 0;
        int units = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            total = Math.addExact(total, 16 + Math.multiplyExact(entry.getKey().length(), 2L));
            Object value = entry.getValue();
            long charge =
                    switch (value) {
                        case String text -> Math.multiplyExact(text.length(), 2L);
                        case AttributeBytes bytes -> bytes.length();
                        case AttributeNull ignored -> 1;
                        case Boolean ignored -> 1;
                        case Long ignored -> 8;
                        case Double ignored -> 8;
                        case LocalDate ignored -> 8;
                        case BigDecimal decimal ->
                                4L
                                        + Math.max(
                                                1,
                                                (decimal.unscaledValue().abs().bitLength() + 7L)
                                                        / 8L);
                        default ->
                                throw new IllegalArgumentException("Non-canonical attribute value");
                    };
            total = Math.addExact(total, charge);
            if (cancellationCheckpoint != null && (++units & 4095) == 0) {
                cancellationCheckpoint.run();
            }
        }
        return total;
    }
}
