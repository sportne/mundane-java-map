package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.FeatureQueryAccounting;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.OptionalLong;

final class ShapefileCursor implements FeatureCursor {
    private enum State {
        NEW,
        CURRENT,
        EXHAUSTED,
        FAILED,
        CANCELLED,
        CLOSED
    }

    private final ShapefileFeatureSource source;
    private final ShapefileFileAccess.Channel channel;
    private final long size;
    private final ShpHeader header;
    private final FeatureQuery query;
    private final CancellationToken cancellation;
    private final FeatureQueryAccounting queryAccounting;
    private final ShapefileAccounting format;
    private final ShxIndex index;
    private final ByteBuffer recordHeader;
    private final ByteBuffer point;
    private final ByteBuffer prefix;
    private final ByteBuffer coordinate;
    private long nextOffset = 100;
    private long ordinal = 1;
    private int indexEntry;
    private FeatureRecord current;
    private State state = State.NEW;

    ShapefileCursor(
            ShapefileFeatureSource source,
            ShapefileFileAccess.Channel channel,
            long size,
            ShpHeader header,
            FeatureQuery query,
            CancellationToken cancellation,
            FeatureQueryLimits limits,
            ShapefileLimits formatLimits,
            ShxIndex index) {
        this.source = source;
        this.channel = channel;
        this.size = size;
        this.header = header;
        this.query = query;
        this.cancellation = cancellation;
        this.index = index;
        queryAccounting = new FeatureQueryAccounting(source.metadata().identity().id(), limits);
        format =
                new ShapefileAccounting(
                        source.metadata().identity().id(), "shapefileCursor", formatLimits);
        format.allocate(84, OptionalLong.empty(), 100);
        recordHeader = ByteBuffer.allocate(8);
        point = ByteBuffer.allocate(20);
        prefix = ByteBuffer.allocate(40);
        coordinate = ByteBuffer.allocate(16);
    }

    @Override
    public boolean advance() {
        requireUsable();
        if (state == State.EXHAUSTED) {
            return false;
        }
        current = null;
        try {
            while (true) {
                checkpoint();
                if (index != null ? indexEntry == index.size() : nextOffset == size) {
                    checkSize();
                    state = State.EXHAUSTED;
                    release();
                    return false;
                }
                long start = index == null ? nextOffset : index.offsetBytes(indexEntry);
                long remaining = size - start;
                if (remaining < 8) {
                    throw recordFailure(
                            "SHAPEFILE_RECORD_LENGTH_INVALID",
                            ordinal,
                            start + 4,
                            Map.of(
                                    "reason",
                                    "truncatedHeader",
                                    "expectedBytes",
                                    "8",
                                    "actualBytes",
                                    Long.toString(remaining)));
                }
                readRecordHeader(start, ordinal);
                recordHeader.order(ByteOrder.BIG_ENDIAN);
                int actualNumber = recordHeader.getInt(0);
                int words = recordHeader.getInt(4);
                if (actualNumber != ordinal) {
                    throw recordFailure(
                            "SHAPEFILE_RECORD_NUMBER_INVALID",
                            ordinal,
                            start,
                            Map.of(
                                    "expected",
                                    Long.toString(ordinal),
                                    "actual",
                                    Integer.toString(actualNumber)));
                }
                if (words < 2) {
                    throw recordFailure(
                            "SHAPEFILE_RECORD_LENGTH_INVALID",
                            ordinal,
                            start + 4,
                            Map.of(
                                    "reason",
                                    "invalidWords",
                                    "actualWords",
                                    Integer.toString(words)));
                }
                long contentBytes;
                long end;
                try {
                    contentBytes = Math.multiplyExact((long) words, 2);
                    end = Math.addExact(Math.addExact(start, 8), contentBytes);
                } catch (ArithmeticException e) {
                    throw recordFailure(
                            "SHAPEFILE_RECORD_LENGTH_INVALID",
                            ordinal,
                            start + 4,
                            Map.of("reason", "overflow"));
                }
                if (end > size) {
                    throw recordFailure(
                            "SHAPEFILE_RECORD_LENGTH_INVALID",
                            ordinal,
                            start + 4,
                            Map.of(
                                    "reason",
                                    "outOfFile",
                                    "declaredBytes",
                                    Long.toString(contentBytes),
                                    "remainingBytes",
                                    Long.toString(size - start - 8)));
                }
                format.recordBytes(contentBytes, ordinal, start + 4);
                if (index != null) {
                    long indexedBytes = index.contentBytes(indexEntry);
                    if (contentBytes != indexedBytes) {
                        throw recordFailure(
                                "SHAPEFILE_RECORD_LENGTH_INVALID",
                                ordinal,
                                start + 4,
                                Map.of(
                                        "reason",
                                        "indexMismatch",
                                        "actualBytes",
                                        Long.toString(contentBytes),
                                        "expectedBytes",
                                        Long.toString(indexedBytes)));
                    }
                    indexEntry++;
                }
                format.physicalRecord(ordinal, start);
                queryAccounting.recordExamined();
                long recordOrdinal = ordinal++;
                nextOffset = end;
                readPrefix(point, 4, start + 8, recordOrdinal);
                point.order(ByteOrder.LITTLE_ENDIAN);
                int type = point.getInt(0);
                if (type == 0) {
                    if (contentBytes != 4) {
                        throw unexpected(recordOrdinal, start, 4, contentBytes);
                    }
                    continue;
                }
                if (type != header.shapeType()) {
                    throw recordFailure(
                            "SHAPEFILE_RECORD_TYPE_MISMATCH",
                            recordOrdinal,
                            start + 8,
                            Map.of(
                                    "expected",
                                    Integer.toString(header.shapeType()),
                                    "actual",
                                    Integer.toString(type)));
                }
                Geometry geometry =
                        type == 1
                                ? decodePointRecord(recordOrdinal, start, contentBytes)
                                : decodeMultiPoint(recordOrdinal, start, contentBytes);
                if (query.sourceBounds().isPresent()
                        && !intersects(geometry.envelope(), query.sourceBounds().orElseThrow())) {
                    continue;
                }
                long idCharacters = Math.addExact(7, decimalDigits(recordOrdinal));
                checkpoint();
                format.allocate(
                        Math.multiplyExact(idCharacters, 2),
                        OptionalLong.of(recordOrdinal),
                        start + 8);
                checkpoint();
                String id = "record:" + recordOrdinal;
                FeatureRecord result = new FeatureRecord(id, "", geometry, Map.of());
                queryAccounting.recordReturned(result, 0, cancellation);
                checkpoint();
                current = result;
                state = State.CURRENT;
                return true;
            }
        } catch (RuntimeException | Error failure) {
            state =
                    failure instanceof SourceException e
                                    && e.terminal().code().equals("SOURCE_CANCELLED")
                            ? State.CANCELLED
                            : State.FAILED;
            release();
            throw failure;
        }
    }

    private Geometry decodePointRecord(long record, long start, long bytes) {
        if (bytes != 20) {
            throw unexpected(record, start, 20, bytes);
        }
        readPrefix(point, 20, start + 8, record);
        point.order(ByteOrder.LITTLE_ENDIAN);
        double x = finite(point.getDouble(4), record, start + 12, "x"),
                y = finite(point.getDouble(12), record, start + 20, "y");
        requireCoordinateInside(
                x, y, header.extent().orElseThrow(), record, start + 12, start + 20, "file");
        return new PointGeometry(new Coordinate(x, y));
    }

    private Geometry decodeMultiPoint(long record, long start, long bytes) {
        if (bytes < 40) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    start + 4,
                    Map.of(
                            "reason",
                            "truncatedPrefix",
                            "expectedBytes",
                            "40",
                            "actualBytes",
                            Long.toString(bytes)));
        }
        readPrefix(prefix, 40, start + 8, record);
        prefix.order(ByteOrder.LITTLE_ENDIAN);
        int type = prefix.getInt(0);
        if (type != 8) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_TYPE_MISMATCH",
                    record,
                    start + 8,
                    Map.of("expected", "8", "actual", Integer.toString(type)));
        }
        int count = prefix.getInt(36);
        if (count <= 0) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    start + 44,
                    Map.of("reason", "pointCount"));
        }
        format.points(count, record, start + 44);
        long expected;
        try {
            expected = Math.addExact(40, Math.multiplyExact((long) count, 16));
        } catch (ArithmeticException e) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    start + 44,
                    Map.of("reason", "arrayCapacity"));
        }
        if (count > Integer.MAX_VALUE / 2) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    start + 44,
                    Map.of("reason", "arrayCapacity"));
        }
        if (bytes != expected) {
            throw unexpected(record, start, expected, bytes);
        }
        checkpoint();
        format.allocate(Math.multiplyExact((long) count, 32), OptionalLong.of(record), start + 44);
        double minX = finite(prefix.getDouble(4), record, start + 12, "x"),
                minY = finite(prefix.getDouble(12), record, start + 20, "y"),
                maxX = finite(prefix.getDouble(20), record, start + 28, "x"),
                maxY = finite(prefix.getDouble(28), record, start + 36, "y");
        if (minX > maxX) {
            throw bounds(record, start + 12, "record");
        }
        if (minY > maxY) {
            throw bounds(record, start + 20, "record");
        }
        Envelope recordBox = new Envelope(minX, minY, maxX, maxY);
        Envelope fileBox = header.extent().orElseThrow();
        checkpoint();
        double[] packed = new double[count * 2];
        for (int i = 0; i < count; i++) {
            if ((i & 2047) == 0) {
                checkpoint();
            }
            readCoordinate(start + 8, 40 + (long) i * 16, expected, record);
            coordinate.order(ByteOrder.LITTLE_ENDIAN);
            double x = finite(coordinate.getDouble(0), record, start + 48 + (long) i * 16, "x"),
                    y = finite(coordinate.getDouble(8), record, start + 56 + (long) i * 16, "y");
            long xOffset = start + 48 + (long) i * 16;
            long yOffset = start + 56 + (long) i * 16;
            requireCoordinateInside(x, y, recordBox, record, xOffset, yOffset, "record");
            requireCoordinateInside(x, y, fileBox, record, xOffset, yOffset, "file");
            packed[i * 2] = x;
            packed[i * 2 + 1] = y;
        }
        checkpoint();
        CoordinateSequence sequence = CoordinateSequence.of(packed);
        checkpoint();
        return new MultiPointGeometry(sequence);
    }

    private void readCoordinate(
            long contentStart, long coordinateOffset, long expectedBytes, long record) {
        coordinate.clear();
        int total = 0;
        long position = contentStart + coordinateOffset;
        try {
            while (coordinate.hasRemaining()) {
                checkpoint();
                int count = channel.read(coordinate, position + total);
                checkpoint();
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                total += count;
            }
        } catch (IOException exception) {
            throw ShapefileFailures.io(
                    source.metadata().identity().id(), "shp", "read", position + total, exception);
        }
        if (coordinate.hasRemaining()) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    position + total,
                    Map.of(
                            "reason",
                            "truncatedPayload",
                            "expectedBytes",
                            Long.toString(expectedBytes),
                            "actualBytes",
                            Long.toString(coordinateOffset + total)));
        }
        coordinate.flip();
    }

    private void readRecordHeader(long position, long record) {
        recordHeader.clear();
        int total = 0;
        try {
            while (recordHeader.hasRemaining()) {
                checkpoint();
                int n = channel.read(recordHeader, position + total);
                checkpoint();
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                total += n;
            }
        } catch (IOException e) {
            throw ShapefileFailures.io(
                    source.metadata().identity().id(), "shp", "read", position + total, e);
        }
        if (recordHeader.hasRemaining()) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    position + 4,
                    Map.of(
                            "reason",
                            "truncatedHeader",
                            "expectedBytes",
                            "8",
                            "actualBytes",
                            Integer.toString(total)));
        }
        recordHeader.flip();
    }

    private void readPrefix(ByteBuffer buffer, int length, long position, long record) {
        buffer.clear();
        buffer.limit(length);
        int total = 0;
        try {
            while (buffer.hasRemaining()) {
                checkpoint();
                int n = channel.read(buffer, position + total);
                checkpoint();
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                total += n;
            }
        } catch (IOException e) {
            throw ShapefileFailures.io(
                    source.metadata().identity().id(), "shp", "read", position + total, e);
        }
        if (buffer.hasRemaining()) {
            throw recordFailure(
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    record,
                    position + total,
                    Map.of(
                            "reason",
                            "truncatedPayload",
                            "expectedBytes",
                            Integer.toString(length),
                            "actualBytes",
                            Integer.toString(total)));
        }
        buffer.flip();
    }

    private void checkSize() {
        try {
            checkpoint();
            long actual = channel.size();
            checkpoint();
            if (actual != size) {
                throw source.lengthMismatch(actual);
            }
        } catch (IOException e) {
            throw ShapefileFailures.io(source.metadata().identity().id(), "shp", "size", -1, e);
        }
    }

    private double finite(double value, long record, long offset, String axis) {
        value = Shapefiles.canonical(value);
        if (!Double.isFinite(value)) {
            throw recordFailure(
                    "SHAPEFILE_COORDINATE_NON_FINITE", record, offset, Map.of("axis", axis));
        }
        return value;
    }

    private void requireCoordinateInside(
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

    private SourceException bounds(long record, long offset, String kind) {
        return recordFailure("SHAPEFILE_BOUNDS_MISMATCH", record, offset, Map.of("bounds", kind));
    }

    private SourceException unexpected(long record, long start, long expected, long actual) {
        return recordFailure(
                "SHAPEFILE_RECORD_LENGTH_INVALID",
                record,
                start + 4,
                Map.of(
                        "reason",
                        "unexpectedSize",
                        "expectedBytes",
                        Long.toString(expected),
                        "actualBytes",
                        Long.toString(actual)));
    }

    private SourceException recordFailure(
            String code, long record, long offset, Map<String, String> context) {
        return ShapefileFailures.failure(
                source.metadata().identity().id(),
                code,
                "shp",
                OptionalLong.of(record),
                offset,
                "Shapefile record is invalid",
                context);
    }

    private void checkpoint() {
        Shapefiles.checkpoint(source.metadata().identity().id(), cancellation);
    }

    private static boolean intersects(Envelope a, Envelope b) {
        return a.maxX() >= b.minX()
                && a.minX() <= b.maxX()
                && a.maxY() >= b.minY()
                && a.minY() <= b.maxY();
    }

    private static long decimalDigits(long value) {
        long digits = 1;
        while (value >= 10) {
            value /= 10;
            digits++;
        }
        return digits;
    }

    private void requireUsable() {
        if (state == State.CLOSED || state == State.FAILED || state == State.CANCELLED) {
            throw new IllegalStateException("Cursor is not usable");
        }
    }

    private void release() {
        source.release(this);
    }

    @Override
    public FeatureRecord current() {
        if (state != State.CURRENT) {
            throw new IllegalStateException("Cursor has no current record");
        }
        return current;
    }

    @Override
    public DiagnosticReport diagnostics() {
        return DiagnosticReport.empty();
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        current = null;
        release();
    }

    void closeFromSource() {
        close();
    }
}
