package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

final class GeoPackageGeometryDecoder {
    private GeoPackageGeometryDecoder() {}

    static DecodedGeometry decode(
            String sourceId,
            byte[] bytes,
            int expectedSrs,
            GeoPackageGeometryType declaredType,
            GeoPackageLimits limits,
            CancellationToken cancellation) {
        if (bytes == null || bytes.length < 13 || bytes.length > limits.maximumBlobBytes()) {
            throw invalid(sourceId, "value");
        }
        if (bytes[0] != 'G' || bytes[1] != 'P' || bytes[2] != 0) {
            throw invalid(sourceId, "value");
        }
        int flags = Byte.toUnsignedInt(bytes[3]);
        if ((flags & 0xC0) != 0 || (flags & 0x20) != 0) {
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
        double[] envelope = null;
        if (envelopeKind == 1) {
            envelope =
                    new double[] {
                        header.getDouble(8),
                        header.getDouble(16),
                        header.getDouble(24),
                        header.getDouble(32)
                    };
            for (double value : envelope) {
                if (!Double.isFinite(value)) {
                    throw invalid(sourceId, "value");
                }
            }
        }
        ByteBuffer wkb = ByteBuffer.wrap(bytes, headerBytes, bytes.length - headerBytes).slice();
        if (empty) {
            return new DecodedGeometry(null, validateEmptyWkb(sourceId, wkb, declaredType));
        }
        Geometry geometry = decodeWkb(sourceId, wkb, declaredType, limits, cancellation);
        if (wkb.hasRemaining()) {
            throw invalid(sourceId, "value");
        }
        if (envelope != null
                && (geometry.envelope().minX() < envelope[0]
                        || geometry.envelope().maxX() > envelope[1]
                        || geometry.envelope().minY() < envelope[2]
                        || geometry.envelope().maxY() > envelope[3])) {
            throw invalid(sourceId, "value");
        }
        return new DecodedGeometry(geometry, null);
    }

    private static String validateEmptyWkb(
            String sourceId, ByteBuffer input, GeoPackageGeometryType declaredType) {
        ByteOrder order = order(sourceId, input);
        input.order(order);
        long type = Integer.toUnsignedLong(readInt(sourceId, input));
        if (type == 1
                && (declaredType == GeoPackageGeometryType.POINT
                        || declaredType == GeoPackageGeometryType.GEOMETRY)) {
            if (input.remaining() != 16
                    || !Double.isNaN(input.getDouble())
                    || !Double.isNaN(input.getDouble())) {
                throw invalid(sourceId, "value");
            }
            return "point";
        }
        if (type == 4
                && (declaredType == GeoPackageGeometryType.MULTI_POINT
                        || declaredType == GeoPackageGeometryType.GEOMETRY)) {
            if (input.remaining() != 4 || input.getInt() != 0) {
                throw invalid(sourceId, "value");
            }
            return "multipoint";
        }
        throw unsupported(sourceId, "geometryType");
    }

    private static Geometry decodeWkb(
            String sourceId,
            ByteBuffer input,
            GeoPackageGeometryType declaredType,
            GeoPackageLimits limits,
            CancellationToken cancellation) {
        ByteOrder order = order(sourceId, input);
        input.order(order);
        long type = Integer.toUnsignedLong(readInt(sourceId, input));
        if (type == 1) {
            if (declaredType != GeoPackageGeometryType.POINT
                    && declaredType != GeoPackageGeometryType.GEOMETRY) {
                throw unsupported(sourceId, "geometryType");
            }
            return new PointGeometry(
                    new Coordinate(readDouble(sourceId, input), readDouble(sourceId, input)));
        }
        if (type == 4) {
            if (declaredType != GeoPackageGeometryType.MULTI_POINT
                    && declaredType != GeoPackageGeometryType.GEOMETRY) {
                throw unsupported(sourceId, "geometryType");
            }
            long count = Integer.toUnsignedLong(readInt(sourceId, input));
            if (count == 0
                    || count > limits.maximumCoordinates()
                    || count > limits.maximumParts()) {
                throw invalid(sourceId, "range");
            }
            long requestedOwnedBytes =
                    Math.addExact(input.capacity(), Math.multiplyExact(count, 32L));
            if (requestedOwnedBytes > limits.maximumOwnedBytes()) {
                throw limit(
                        sourceId, "ownedBytes", requestedOwnedBytes, limits.maximumOwnedBytes());
            }
            double[] coordinates = new double[Math.toIntExact(Math.multiplyExact(count, 2))];
            for (int index = 0; index < count; index++) {
                if ((index & 4_095) == 0) {
                    GeoPackageFailures.checkpoint(
                            sourceId, cancellation::isCancellationRequested, "feature-query");
                }
                input.order(order(sourceId, input));
                if (Integer.toUnsignedLong(readInt(sourceId, input)) != 1) {
                    throw invalid(sourceId, "value");
                }
                coordinates[index * 2] = readDouble(sourceId, input);
                coordinates[index * 2 + 1] = readDouble(sourceId, input);
            }
            return new MultiPointGeometry(CoordinateSequence.of(coordinates));
        }
        throw unsupported(sourceId, "geometryType");
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

    private static int readInt(String sourceId, ByteBuffer input) {
        if (input.remaining() < Integer.BYTES) {
            throw invalid(sourceId, "value");
        }
        return input.getInt();
    }

    private static double readDouble(String sourceId, ByteBuffer input) {
        if (input.remaining() < Double.BYTES) {
            throw invalid(sourceId, "value");
        }
        double value = input.getDouble();
        if (!Double.isFinite(value)) {
            throw invalid(sourceId, "value");
        }
        return value;
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

    record DecodedGeometry(Geometry geometry, String emptyType) {
        boolean isEmpty() {
            return geometry == null;
        }
    }
}
