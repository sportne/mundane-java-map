package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Bounded common framing reader for SHP PolyLine and Polygon payloads. */
final class ShpMultipartReader {
    private static final int PREFIX_BYTES = 44;

    private final String source;
    private final ShapefileFileAccess.Channel channel;
    private final CancellationToken cancellation;
    private final ShapefileAccounting accounting;
    private final Optional<Envelope> fileBox;
    private final ByteBuffer prefix;
    private final ByteBuffer scalar;

    ShpMultipartReader(
            String source,
            ShapefileFileAccess.Channel channel,
            CancellationToken cancellation,
            ShapefileAccounting accounting,
            Optional<Envelope> fileBox,
            ByteBuffer prefix,
            ByteBuffer scalar) {
        this.source = source;
        this.channel = channel;
        this.cancellation = cancellation;
        this.accounting = accounting;
        this.fileBox = fileBox;
        this.prefix = prefix;
        this.scalar = scalar;
    }

    ShpMultipartPlan preflight(
            long record,
            long recordStart,
            long contentBytes,
            int minimumCoordinatesPerPart,
            String aggregateCode,
            String aggregateReason,
            String spanCode,
            String spanReason) {
        if (contentBytes < PREFIX_BYTES) {
            throw failure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    OptionalInt.empty(),
                    recordStart + 4,
                    Map.of(
                            "reason",
                            "truncatedPrefix",
                            "expectedBytes",
                            Integer.toString(PREFIX_BYTES),
                            "actualBytes",
                            Long.toString(contentBytes)));
        }
        read(prefix, PREFIX_BYTES, recordStart + 8, 0, PREFIX_BYTES, record);
        prefix.order(ByteOrder.LITTLE_ENDIAN);
        int partCount = prefix.getInt(36);
        if (partCount <= 0) {
            throw failure(
                    "SHAPEFILE_PART_TABLE_INVALID",
                    record,
                    OptionalInt.empty(),
                    recordStart + 44,
                    Map.of("reason", "partCount"));
        }
        accounting.parts(partCount, record, recordStart + 44);
        int pointCount = prefix.getInt(40);
        if (pointCount <= 0) {
            throw failure(
                    "SHAPEFILE_PART_TABLE_INVALID",
                    record,
                    OptionalInt.empty(),
                    recordStart + 48,
                    Map.of("reason", "pointCount"));
        }
        accounting.points(pointCount, record, recordStart + 48);

        if ((long) partCount + 1 > Integer.MAX_VALUE) {
            throw invalidCapacity(record, recordStart + 44);
        }
        if ((long) pointCount * 2 > Integer.MAX_VALUE) {
            throw invalidCapacity(record, recordStart + 48);
        }
        long minimumPoints = Math.multiplyExact((long) minimumCoordinatesPerPart, partCount);
        if (pointCount < minimumPoints) {
            throw failure(
                    aggregateCode,
                    record,
                    OptionalInt.empty(),
                    recordStart + 48,
                    Map.of("reason", aggregateReason));
        }
        long partBytes = Math.multiplyExact((long) partCount, Integer.BYTES);
        long coordinateBytes = Math.multiplyExact((long) pointCount, 2L * Double.BYTES);
        long expectedBytes = Math.addExact(PREFIX_BYTES, Math.addExact(partBytes, coordinateBytes));
        if (contentBytes != expectedBytes) {
            throw failure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    OptionalInt.empty(),
                    recordStart + 4,
                    Map.of(
                            "reason",
                            "unexpectedSize",
                            "expectedBytes",
                            Long.toString(expectedBytes),
                            "actualBytes",
                            Long.toString(contentBytes)));
        }

        double minX = finite(prefix.getDouble(4), record, recordStart + 12, "x");
        double minY = finite(prefix.getDouble(12), record, recordStart + 20, "y");
        double maxX = finite(prefix.getDouble(20), record, recordStart + 28, "x");
        double maxY = finite(prefix.getDouble(28), record, recordStart + 36, "y");
        if (minX > maxX) {
            throw bounds(record, recordStart + 12, "record");
        }
        if (minY > maxY) {
            throw bounds(record, recordStart + 20, "record");
        }
        long partTableStart = recordStart + 8 + PREFIX_BYTES;
        return new ShpMultipartPlan(
                record,
                recordStart,
                contentBytes,
                partCount,
                pointCount,
                expectedBytes,
                partTableStart,
                partTableStart + partBytes,
                new Envelope(minX, minY, maxX, maxY),
                minimumCoordinatesPerPart,
                aggregateCode,
                aggregateReason,
                spanCode,
                spanReason);
    }

    ShpMultipartPayload materialize(ShpMultipartPlan plan) {
        checkpoint();
        int[] fenceposts = new int[plan.partCount() + 1];
        checkpoint();
        readPartTable(plan, fenceposts);
        checkpoint();
        double[] packed = new double[plan.pointCount() * 2];
        checkpoint();
        Envelope coordinateEnvelope = readCoordinates(plan, packed);
        checkpoint();
        return new ShpMultipartPayload(packed, fenceposts, plan.recordBox(), coordinateEnvelope);
    }

    private void readPartTable(ShpMultipartPlan plan, int[] fenceposts) {
        int previous = -1;
        for (int part = 0; part < plan.partCount(); part++) {
            checkpoint();
            long fieldOffset = plan.partTableStart() + (long) part * Integer.BYTES;
            read(
                    scalar,
                    Integer.BYTES,
                    fieldOffset,
                    PREFIX_BYTES + (long) part * Integer.BYTES,
                    plan.expectedBytes(),
                    plan.record());
            scalar.order(ByteOrder.LITTLE_ENDIAN);
            int start = scalar.getInt(0);
            if (part == 0 && start != 0) {
                throw structuralTable(plan, part, fieldOffset, "firstNotZero");
            }
            if (part > 0 && start <= previous) {
                throw structuralTable(plan, part, fieldOffset, "notIncreasing");
            }
            if (start >= plan.pointCount()) {
                throw structuralTable(plan, part, fieldOffset, "outOfRange");
            }
            if (part > 0 && start - previous < plan.minimumCoordinatesPerPart()) {
                throw shortSpan(
                        plan,
                        part - 1,
                        plan.partTableStart() + (long) (part - 1) * Integer.BYTES,
                        plan.spanReason());
            }
            fenceposts[part] = start;
            previous = start;
        }
        int lastPart = plan.partCount() - 1;
        if (plan.pointCount() - previous < plan.minimumCoordinatesPerPart()) {
            throw shortSpan(
                    plan,
                    lastPart,
                    plan.partTableStart() + (long) lastPart * Integer.BYTES,
                    plan.spanReason());
        }
        fenceposts[plan.partCount()] = plan.pointCount();
        checkpoint();
    }

    private Envelope readCoordinates(ShpMultipartPlan plan, double[] packed) {
        Envelope recordBounds = plan.recordBox();
        Envelope fileBounds = fileBox.orElseThrow();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int point = 0; point < plan.pointCount(); point++) {
            if ((point & 2047) == 0) {
                checkpoint();
            }
            long coordinateOffset = plan.coordinateStart() + (long) point * 16;
            read(
                    scalar,
                    16,
                    coordinateOffset,
                    coordinateOffset - (plan.recordStart() + 8),
                    plan.expectedBytes(),
                    plan.record());
            scalar.order(ByteOrder.LITTLE_ENDIAN);
            long xOffset = coordinateOffset;
            long yOffset = coordinateOffset + Double.BYTES;
            double x = finite(scalar.getDouble(0), plan.record(), xOffset, "x");
            double y = finite(scalar.getDouble(8), plan.record(), yOffset, "y");
            requireInside(x, y, recordBounds, plan.record(), xOffset, yOffset, "record");
            requireInside(x, y, fileBounds, plan.record(), xOffset, yOffset, "file");
            packed[point * 2] = x;
            packed[point * 2 + 1] = y;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        checkpoint();
        return new Envelope(minX, minY, maxX, maxY);
    }

    private void read(
            ByteBuffer target,
            int length,
            long position,
            long contentOffset,
            long expectedBytes,
            long record) {
        target.clear();
        target.limit(length);
        int total = 0;
        try {
            while (target.hasRemaining()) {
                checkpoint();
                int count = channel.read(target, position + total);
                checkpoint();
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    total += count;
                }
            }
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "shp", "read", position + total, exception);
        }
        if (target.hasRemaining()) {
            throw failure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    OptionalInt.empty(),
                    position + total,
                    Map.of(
                            "reason",
                            "truncatedPayload",
                            "expectedBytes",
                            Long.toString(expectedBytes),
                            "actualBytes",
                            Long.toString(contentOffset + total)));
        }
        target.flip();
    }

    private double finite(double value, long record, long offset, String axis) {
        double canonical = Shapefiles.canonical(value);
        if (!Double.isFinite(canonical)) {
            throw failure(
                    "SHAPEFILE_COORDINATE_NON_FINITE",
                    record,
                    OptionalInt.empty(),
                    offset,
                    Map.of("axis", axis));
        }
        return canonical;
    }

    private void requireInside(
            double x,
            double y,
            Envelope bounds,
            long record,
            long xOffset,
            long yOffset,
            String kind) {
        if (x < bounds.minX() || x > bounds.maxX()) {
            throw bounds(record, xOffset, kind);
        }
        if (y < bounds.minY() || y > bounds.maxY()) {
            throw bounds(record, yOffset, kind);
        }
    }

    private SourceException invalidCapacity(long record, long offset) {
        return failure(
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                record,
                OptionalInt.empty(),
                offset,
                Map.of("reason", "arrayCapacity"));
    }

    private SourceException bounds(long record, long offset, String kind) {
        return failure(
                "SHAPEFILE_BOUNDS_MISMATCH",
                record,
                OptionalInt.empty(),
                offset,
                Map.of("bounds", kind));
    }

    private SourceException structuralTable(
            ShpMultipartPlan plan, int part, long offset, String reason) {
        return failure(
                "SHAPEFILE_PART_TABLE_INVALID",
                plan.record(),
                OptionalInt.of(part),
                offset,
                Map.of("reason", reason));
    }

    private SourceException shortSpan(ShpMultipartPlan plan, int part, long offset, String reason) {
        return failure(
                plan.spanCode(),
                plan.record(),
                OptionalInt.of(part),
                offset,
                Map.of("reason", reason));
    }

    private SourceException failure(
            String code, long record, OptionalInt part, long offset, Map<String, String> context) {
        return ShapefileFailures.failure(
                source,
                code,
                "shp",
                OptionalLong.of(record),
                part,
                offset,
                "Shapefile multipart record is invalid",
                context);
    }

    private void checkpoint() {
        Shapefiles.checkpoint(source, cancellation);
    }
}
