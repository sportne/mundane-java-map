package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Operation-local checked feature-query accounting shared by sources and consumers. */
public final class FeatureQueryAccounting {
    private final String sourceId;
    private final FeatureQueryLimits limits;
    private long examined;
    private long returned;
    private long coordinates;
    private long attributes;
    private long textCharacters;
    private long payloadBytes;

    /**
     * Creates accounting for exact already-resolved limits.
     *
     * @param sourceId stable source identifier used in structured failures
     * @param limits immutable operation ceilings
     */
    public FeatureQueryAccounting(String sourceId, FeatureQueryLimits limits) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Charges one examined physical/logical record. */
    public void recordExamined() {
        examined = accept("recordsExamined", examined, 1, limits.recordsExamined());
    }

    /**
     * Charges one returned record and its deterministic logical payload.
     *
     * @param record immutable record about to be published
     * @param retainedReferenceSlots additional retained reference slots owned by the caller
     * @param cancellation operation cancellation token checked before publication
     * @throws SourceException when cancellation is requested or a query limit would be exceeded
     */
    public void recordReturned(
            FeatureRecord record, int retainedReferenceSlots, CancellationToken cancellation) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(cancellation, "cancellation");
        if (retainedReferenceSlots < 0) {
            throw new IllegalArgumentException("retainedReferenceSlots must be non-negative");
        }
        checkCancellation(cancellation);
        long coordinateCount = coordinateCount(record.geometry(), cancellation);
        long offsetCount = offsetCount(record.geometry());
        long attributeCount = record.attributes().size();
        long text =
                Math.addExact(
                        Math.addExact(record.id().length(), record.name().length()),
                        attributeText(record.attributes(), cancellation));
        long payload =
                Math.addExact(
                        Math.multiplyExact(coordinateCount, 16),
                        Math.multiplyExact(offsetCount, 4));
        payload = Math.addExact(payload, Math.multiplyExact((long) retainedReferenceSlots, 8));
        payload =
                Math.addExact(
                        payload, Math.multiplyExact(geometryReferenceSlots(record.geometry()), 8));
        payload =
                Math.addExact(
                        payload,
                        Math.multiplyExact(
                                (long) record.id().length() + record.name().length(), 2));
        payload = Math.addExact(payload, attributePayload(record.attributes(), cancellation));
        long nextReturned = prospective("recordsReturned", returned, 1, limits.recordsReturned());
        long nextCoordinates =
                prospective(
                        "coordinatesReturned",
                        coordinates,
                        coordinateCount,
                        limits.coordinatesReturned());
        long nextAttributes =
                prospective(
                        "attributeValuesReturned",
                        attributes,
                        attributeCount,
                        limits.attributeValuesReturned());
        long nextText =
                prospective(
                        "decodedTextCharactersReturned",
                        textCharacters,
                        text,
                        limits.decodedTextCharactersReturned());
        long nextPayload =
                prospective("ownedPayloadBytes", payloadBytes, payload, limits.ownedPayloadBytes());
        checkCancellation(cancellation);
        returned = nextReturned;
        coordinates = nextCoordinates;
        attributes = nextAttributes;
        textCharacters = nextText;
        payloadBytes = nextPayload;
    }

    private long accept(String name, long current, long charge, long maximum) {
        return prospective(name, current, charge, maximum);
    }

    private long prospective(String name, long current, long charge, long maximum) {
        long requested;
        boolean overflow = false;
        try {
            requested = Math.addExact(current, charge);
        } catch (ArithmeticException ignored) {
            requested = Long.MAX_VALUE;
            overflow = true;
        }
        if (overflow || requested > maximum) {
            throw failure(
                    "SOURCE_LIMIT_EXCEEDED",
                    "Source query limit exceeded",
                    Map.of(
                            "scope",
                            "feature-query",
                            "limit",
                            name,
                            "requested",
                            Long.toString(requested),
                            "maximum",
                            Long.toString(maximum)));
        }
        return requested;
    }

    private void checkCancellation(CancellationToken token) {
        if (token.isCancellationRequested()) {
            throw failure(
                    "SOURCE_CANCELLED",
                    "Source query was cancelled",
                    Map.of("operation", "feature-query"));
        }
    }

    private SourceException failure(String code, String message, Map<String, String> context) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private long coordinateCount(Geometry geometry, CancellationToken cancellation) {
        long count =
                switch (geometry) {
                    case PointGeometry ignored -> 1;
                    case LineStringGeometry line -> line.coordinates().size();
                    case PolygonGeometry polygon -> polygonCoordinateCount(polygon);
                    case MultiPointGeometry points -> points.coordinates().size();
                    case MultiLineStringGeometry lines -> lines.coordinates().size();
                    case MultiPolygonGeometry polygons -> polygons.coordinates().size();
                };
        for (long checked = 4096; checked <= count; checked += 4096) {
            checkCancellation(cancellation);
            if (checked > Long.MAX_VALUE - 4096) {
                break;
            }
        }
        return count;
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

    private long attributeText(Map<String, Object> values, CancellationToken cancellation) {
        long total = 0;
        int units = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            total = Math.addExact(total, entry.getKey().length());
            if (entry.getValue() instanceof String text) {
                total = Math.addExact(total, text.length());
            }
            if ((++units & 4095) == 0) {
                checkCancellation(cancellation);
            }
        }
        return total;
    }

    private long attributePayload(Map<String, Object> values, CancellationToken cancellation) {
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
            if ((++units & 4095) == 0) {
                checkCancellation(cancellation);
            }
        }
        return total;
    }
}
