package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GeoPackageGeometryDecoder {
    private GeoPackageGeometryDecoder() {}

    static DecodedGeometry decode(
            String sourceId,
            byte[] bytes,
            int expectedSrs,
            GeoPackageGeometryType declaredType,
            GeoPackageLimits limits,
            CancellationToken cancellation) {
        return decode(
                sourceId, bytes, expectedSrs, declaredType, limits, cancellation, Optional.empty());
    }

    static DecodedGeometry decode(
            String sourceId,
            byte[] bytes,
            int expectedSrs,
            GeoPackageGeometryType declaredType,
            GeoPackageLimits limits,
            CancellationToken cancellation,
            Optional<Envelope> queryBounds) {
        if (bytes == null || bytes.length < 13 || bytes.length > limits.maximumBlobBytes()) {
            throw invalid(sourceId, "value");
        }
        if (bytes[0] != 'G' || bytes[1] != 'P' || bytes[2] != 0) {
            throw invalid(sourceId, "value");
        }
        int flags = Byte.toUnsignedInt(bytes[3]);
        if ((flags & 0xE0) != 0) {
            throw unsupported(sourceId, "geometryType");
        }
        boolean empty = (flags & 0x10) != 0;
        ByteOrder headerOrder = (flags & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        int envelopeKind = (flags >>> 1) & 7;
        if (envelopeKind != 0 && envelopeKind != 1) {
            throw unsupported(sourceId, "dimension");
        }
        if (empty && envelopeKind != 0) {
            throw invalid(sourceId, "value");
        }
        int headerBytes = envelopeKind == 0 ? 8 : 40;
        if (bytes.length < headerBytes + 5) {
            throw invalid(sourceId, "value");
        }
        ByteBuffer header = ByteBuffer.wrap(bytes).order(headerOrder);
        if (header.getInt(4) != expectedSrs) {
            throw invalid(sourceId, "value");
        }
        Envelope headerEnvelope = null;
        if (envelopeKind == 1) {
            try {
                headerEnvelope =
                        new Envelope(
                                finite(sourceId, header.getDouble(8)),
                                finite(sourceId, header.getDouble(24)),
                                finite(sourceId, header.getDouble(16)),
                                finite(sourceId, header.getDouble(32)));
            } catch (IllegalArgumentException exception) {
                throw invalid(sourceId, "value");
            }
        }
        ByteBuffer wkb = ByteBuffer.wrap(bytes, headerBytes, bytes.length - headerBytes).slice();
        if (empty) {
            return DecodedGeometry.empty(validateEmptyWkb(sourceId, wkb, declaredType));
        }
        DecodeContext context = new DecodeContext(sourceId, limits, cancellation, wkb.remaining());
        Geometry geometry = decodeWkb(wkb, declaredType, context);
        if (wkb.hasRemaining()) {
            throw invalid(sourceId, "value");
        }
        if (headerEnvelope != null && !contains(headerEnvelope, geometry.envelope())) {
            throw invalid(sourceId, "value");
        }
        if (headerEnvelope != null
                && queryBounds.isPresent()
                && !intersects(headerEnvelope, queryBounds.orElseThrow())) {
            return DecodedGeometry.excluded();
        }
        return DecodedGeometry.geometry(geometry);
    }

    private static String validateEmptyWkb(
            String sourceId, ByteBuffer input, GeoPackageGeometryType declaredType) {
        long type = readType(sourceId, input);
        checkDeclared(sourceId, type, declaredType);
        String name =
                switch ((int) type) {
                    case 1 -> {
                        if (input.remaining() != 16
                                || !Double.isNaN(input.getDouble())
                                || !Double.isNaN(input.getDouble())) {
                            throw invalid(sourceId, "value");
                        }
                        yield "point";
                    }
                    case 2 -> emptyCount(sourceId, input, "line");
                    case 3 -> emptyCount(sourceId, input, "polygon");
                    case 4 -> emptyCount(sourceId, input, "multipoint");
                    case 5 -> emptyCount(sourceId, input, "multiline");
                    case 6 -> emptyCount(sourceId, input, "multipolygon");
                    default -> throw unsupported(sourceId, "geometryType");
                };
        if (input.hasRemaining()) {
            throw invalid(sourceId, "value");
        }
        return name;
    }

    private static String emptyCount(String sourceId, ByteBuffer input, String name) {
        if (input.remaining() != Integer.BYTES || readUnsignedInt(sourceId, input) != 0) {
            throw invalid(sourceId, "value");
        }
        return name;
    }

    private static Geometry decodeWkb(
            ByteBuffer input, GeoPackageGeometryType declaredType, DecodeContext context) {
        long type = readType(context.sourceId, input);
        checkDeclared(context.sourceId, type, declaredType);
        return switch ((int) type) {
            case 1 -> readPoint(input, context);
            case 2 -> readLine(input, context);
            case 3 -> readPolygon(input, context);
            case 4 -> readMultiPoint(input, context);
            case 5 -> readMultiLine(input, context);
            case 6 -> readMultiPolygon(input, context);
            default -> throw unsupported(context.sourceId, "geometryType");
        };
    }

    private static PointGeometry readPoint(ByteBuffer input, DecodeContext context) {
        context.reserve(1, 0);
        return new PointGeometry(readCoordinate(input, context));
    }

    private static LineStringGeometry readLine(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count < 2) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(count, 1);
        return new LineStringGeometry(readSequence(input, count, context));
    }

    private static PolygonGeometry readPolygon(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count == 0) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(0, count);
        List<CoordinateSequence> rings = new ArrayList<>(context.checkedArrayLength(count));
        for (long index = 0; index < count; index++) {
            context.checkpoint(index);
            rings.add(readRing(input, context));
        }
        return new PolygonGeometry(rings.getFirst(), rings.subList(1, rings.size()));
    }

    private static MultiPointGeometry readMultiPoint(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count == 0) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(count, count);
        double[] coordinates = new double[context.checkedArrayLength(Math.multiplyExact(count, 2))];
        for (long index = 0; index < count; index++) {
            context.checkpoint(index);
            long type = readType(context.sourceId, input);
            if (type != 1) {
                throw invalid(context.sourceId, "value");
            }
            Coordinate coordinate = readCoordinate(input, context);
            int target = Math.toIntExact(index * 2);
            coordinates[target] = coordinate.x();
            coordinates[target + 1] = coordinate.y();
        }
        return new MultiPointGeometry(CoordinateSequence.of(coordinates));
    }

    private static MultiLineStringGeometry readMultiLine(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count == 0) {
            throw invalid(context.sourceId, "range");
        }
        context.checkParts(count);
        List<CoordinateSequence> lines = new ArrayList<>(context.checkedArrayLength(count));
        for (long index = 0; index < count; index++) {
            context.checkpoint(index);
            long type = readType(context.sourceId, input);
            if (type != 2) {
                throw invalid(context.sourceId, "value");
            }
            lines.add(readLineCoordinates(input, context));
        }
        return MultiLineStringGeometry.ofParts(lines);
    }

    private static MultiPolygonGeometry readMultiPolygon(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count == 0) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(0, count);
        List<PolygonGeometry> polygons = new ArrayList<>(context.checkedArrayLength(count));
        for (long index = 0; index < count; index++) {
            context.checkpoint(index);
            long type = readType(context.sourceId, input);
            if (type != 3) {
                throw invalid(context.sourceId, "value");
            }
            polygons.add(readPolygonBody(input, context));
        }
        return MultiPolygonGeometry.ofPolygons(polygons);
    }

    private static CoordinateSequence readLineCoordinates(ByteBuffer input, DecodeContext context) {
        long coordinates = readUnsignedInt(context.sourceId, input);
        if (coordinates < 2) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(coordinates, 1);
        return readSequence(input, coordinates, context);
    }

    private static PolygonGeometry readPolygonBody(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count == 0) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(0, count);
        List<CoordinateSequence> rings = new ArrayList<>(context.checkedArrayLength(count));
        for (long index = 0; index < count; index++) {
            context.checkpoint(index);
            rings.add(readRing(input, context));
        }
        return new PolygonGeometry(rings.getFirst(), rings.subList(1, rings.size()));
    }

    private static CoordinateSequence readRing(ByteBuffer input, DecodeContext context) {
        long count = readUnsignedInt(context.sourceId, input);
        if (count < 4) {
            throw invalid(context.sourceId, "range");
        }
        context.reserve(count, 0);
        CoordinateSequence ring = readSequence(input, count, context);
        if (!ring.isClosed()) {
            throw invalid(context.sourceId, "value");
        }
        return ring;
    }

    private static CoordinateSequence readSequence(
            ByteBuffer input, long count, DecodeContext context) {
        long required = Math.multiplyExact(count, 2L * Double.BYTES);
        if (required > input.remaining()) {
            throw invalid(context.sourceId, "value");
        }
        double[] values = new double[context.checkedArrayLength(Math.multiplyExact(count, 2))];
        for (long index = 0; index < count; index++) {
            context.checkpoint(index);
            int target = Math.toIntExact(index * 2);
            values[target] = readDouble(context.sourceId, input);
            values[target + 1] = readDouble(context.sourceId, input);
        }
        return CoordinateSequence.of(values);
    }

    private static Coordinate readCoordinate(ByteBuffer input, DecodeContext context) {
        return new Coordinate(
                readDouble(context.sourceId, input), readDouble(context.sourceId, input));
    }

    private static long readType(String sourceId, ByteBuffer input) {
        input.order(order(sourceId, input));
        return readUnsignedInt(sourceId, input);
    }

    private static void checkDeclared(
            String sourceId, long type, GeoPackageGeometryType declaredType) {
        boolean matches =
                declaredType == GeoPackageGeometryType.GEOMETRY
                        ? type >= 1 && type <= 6
                        : switch (declaredType) {
                            case POINT -> type == 1;
                            case LINE_STRING -> type == 2;
                            case POLYGON -> type == 3;
                            case MULTI_POINT -> type == 4;
                            case MULTI_LINE_STRING -> type == 5;
                            case MULTI_POLYGON -> type == 6;
                            case GEOMETRY -> true;
                        };
        if (!matches) {
            throw unsupported(sourceId, "geometryType");
        }
    }

    private static ByteOrder order(String sourceId, ByteBuffer input) {
        if (!input.hasRemaining()) {
            throw invalid(sourceId, "value");
        }
        return switch (Byte.toUnsignedInt(input.get())) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw invalid(sourceId, "value");
        };
    }

    private static long readUnsignedInt(String sourceId, ByteBuffer input) {
        if (input.remaining() < Integer.BYTES) {
            throw invalid(sourceId, "value");
        }
        return Integer.toUnsignedLong(input.getInt());
    }

    private static double readDouble(String sourceId, ByteBuffer input) {
        if (input.remaining() < Double.BYTES) {
            throw invalid(sourceId, "value");
        }
        return finite(sourceId, input.getDouble());
    }

    private static double finite(String sourceId, double value) {
        if (!Double.isFinite(value)) {
            throw invalid(sourceId, "value");
        }
        return value;
    }

    private static boolean contains(Envelope container, Envelope value) {
        return value.minX() >= container.minX()
                && value.maxX() <= container.maxX()
                && value.minY() >= container.minY()
                && value.maxY() <= container.maxY();
    }

    private static boolean intersects(Envelope first, Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private static io.github.mundanej.map.api.SourceException invalid(
            String sourceId, String reason) {
        return GeoPackageFailures.failure(
                sourceId,
                "GEOPACKAGE_RECORD_INVALID",
                "GeoPackage geometry record is invalid",
                Map.of("field", "geometry", "reason", reason));
    }

    private static io.github.mundanej.map.api.SourceException unsupported(
            String sourceId, String construct) {
        return GeoPackageFailures.failure(
                sourceId,
                "GEOPACKAGE_PROFILE_UNSUPPORTED",
                "GeoPackage geometry is outside the supported profile",
                Map.of("construct", construct));
    }

    private static io.github.mundanej.map.api.SourceException limit(
            String sourceId, String name, long requested, long maximum) {
        return GeoPackageFailures.failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "GeoPackage operation limit exceeded",
                Map.of(
                        "scope",
                        "geopackageCursor",
                        "limit",
                        name,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    private static final class DecodeContext {
        private final String sourceId;
        private final GeoPackageLimits limits;
        private final CancellationToken cancellation;
        private final long inputBytes;
        private long coordinates;
        private long parts;

        private DecodeContext(
                String sourceId,
                GeoPackageLimits limits,
                CancellationToken cancellation,
                long inputBytes) {
            this.sourceId = sourceId;
            this.limits = limits;
            this.cancellation = cancellation;
            this.inputBytes = inputBytes;
        }

        private void reserve(long coordinateCount, long partCount) {
            long nextCoordinates = Math.addExact(coordinates, coordinateCount);
            long nextParts = Math.addExact(parts, partCount);
            if (nextCoordinates > limits.maximumCoordinates()) {
                throw limit(sourceId, "coordinates", nextCoordinates, limits.maximumCoordinates());
            }
            if (nextParts > limits.maximumParts()) {
                throw limit(sourceId, "parts", nextParts, limits.maximumParts());
            }
            long owned =
                    Math.addExact(
                            inputBytes,
                            Math.addExact(
                                    Math.multiplyExact(nextCoordinates, 96),
                                    Math.multiplyExact(nextParts, 24)));
            if (owned > limits.maximumOwnedBytes()) {
                throw limit(sourceId, "ownedBytes", owned, limits.maximumOwnedBytes());
            }
            coordinates = nextCoordinates;
            parts = nextParts;
        }

        private void checkParts(long prospectiveParts) {
            if (Math.addExact(parts, prospectiveParts) > limits.maximumParts()) {
                throw limit(
                        sourceId,
                        "parts",
                        Math.addExact(parts, prospectiveParts),
                        limits.maximumParts());
            }
        }

        private int checkedArrayLength(long length) {
            if (length > Integer.MAX_VALUE) {
                throw limit(sourceId, "ownedBytes", length, limits.maximumOwnedBytes());
            }
            return Math.toIntExact(length);
        }

        private void checkpoint(long ordinal) {
            if ((ordinal & 4_095) == 0) {
                GeoPackageFailures.checkpoint(
                        sourceId, cancellation::isCancellationRequested, "feature-query");
            }
        }
    }

    record DecodedGeometry(Geometry geometry, String emptyType, boolean filtered) {
        static DecodedGeometry geometry(Geometry geometry) {
            return new DecodedGeometry(geometry, null, false);
        }

        static DecodedGeometry empty(String type) {
            return new DecodedGeometry(null, type, false);
        }

        static DecodedGeometry excluded() {
            return new DecodedGeometry(null, null, true);
        }

        boolean isEmpty() {
            return geometry == null && !filtered;
        }
    }
}
