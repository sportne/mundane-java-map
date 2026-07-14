package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** PolyLine-specific validation and immutable API geometry construction. */
final class PolylineDecoder {
    private final String source;
    private final CancellationToken cancellation;
    private final ShapefileAccounting accounting;
    private final ShpMultipartReader reader;

    PolylineDecoder(
            String source,
            ShapefileFileAccess.Channel channel,
            CancellationToken cancellation,
            ShapefileAccounting accounting,
            Optional<Envelope> fileBox,
            ByteBuffer prefix,
            ByteBuffer scalar) {
        this.source = source;
        this.cancellation = cancellation;
        this.accounting = accounting;
        reader =
                new ShpMultipartReader(
                        source, channel, cancellation, accounting, fileBox, prefix, scalar);
    }

    Geometry decode(long record, long recordStart, long contentBytes) {
        ShpMultipartPlan plan =
                reader.preflight(
                        record,
                        recordStart,
                        contentBytes,
                        2,
                        "SHAPEFILE_PART_TABLE_INVALID",
                        "insufficientPoints",
                        "SHAPEFILE_PART_TABLE_INVALID",
                        "tooShort");
        long fencepostBytes = Math.multiplyExact((long) plan.partCount() + 1, Integer.BYTES);
        long coordinateBytes = Math.multiplyExact((long) plan.pointCount(), 2L * Double.BYTES);
        long variableBytes = Math.addExact(fencepostBytes, Math.multiplyExact(coordinateBytes, 2));
        if (plan.partCount() > 1) {
            variableBytes = Math.addExact(variableBytes, fencepostBytes);
        }
        checkpoint();
        accounting.allocate(variableBytes, OptionalLong.of(record), recordStart + 44);
        checkpoint();
        ShpMultipartPayload payload = reader.materialize(plan);
        validateDistinctPairs(plan, payload);
        checkpoint();
        CoordinateSequence coordinates = CoordinateSequence.of(payload.packedCoordinates());
        checkpoint();
        return plan.partCount() == 1
                ? new LineStringGeometry(coordinates)
                : MultiLineStringGeometry.of(coordinates, payload.fenceposts());
    }

    private void validateDistinctPairs(ShpMultipartPlan plan, ShpMultipartPayload payload) {
        double[] packed = payload.packedCoordinates();
        int[] fences = payload.fenceposts();
        for (int part = 0; part < plan.partCount(); part++) {
            checkpoint();
            int first = fences[part];
            double firstX = packed[first * 2];
            double firstY = packed[first * 2 + 1];
            boolean distinct = false;
            for (int point = first + 1; point < fences[part + 1]; point++) {
                if (((point - first) & 2047) == 0) {
                    checkpoint();
                }
                if (packed[point * 2] != firstX || packed[point * 2 + 1] != firstY) {
                    distinct = true;
                }
            }
            if (!distinct) {
                long fieldOffset = plan.partTableStart() + (long) part * Integer.BYTES;
                throw ShapefileFailures.failure(
                        source,
                        "SHAPEFILE_PART_TABLE_INVALID",
                        "shp",
                        OptionalLong.of(plan.record()),
                        OptionalInt.of(part),
                        fieldOffset,
                        "Shapefile PolyLine part is degenerate",
                        Map.of("reason", "degenerate"));
            }
        }
        checkpoint();
    }

    private void checkpoint() {
        Shapefiles.checkpoint(source, cancellation);
    }
}
