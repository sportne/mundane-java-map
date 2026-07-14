package io.github.mundanej.map.io.shapefile;

import java.util.OptionalLong;

final class ShapefileAccounting {
    private final String source, scope;
    private final ShapefileLimits limits;
    private long records, allocation;

    ShapefileAccounting(String source, String scope, ShapefileLimits limits) {
        this.source = source;
        this.scope = scope;
        this.limits = limits;
    }

    void physicalRecord(long record, long offset) {
        records =
                charge(
                        "physicalRecords",
                        records,
                        1,
                        limits.maximumPhysicalRecords(),
                        OptionalLong.of(record),
                        offset);
    }

    void recordBytes(long bytes, long record, long offset) {
        if (bytes > limits.maximumRecordBytes()) {
            throw ShapefileFailures.limit(
                    source,
                    scope,
                    "recordBytes",
                    bytes,
                    limits.maximumRecordBytes(),
                    OptionalLong.of(record),
                    offset);
        }
    }

    void points(long count, long record, long offset) {
        if (count > limits.maximumPoints()) {
            throw ShapefileFailures.limit(
                    source,
                    scope,
                    "points",
                    count,
                    limits.maximumPoints(),
                    OptionalLong.of(record),
                    offset);
        }
    }

    void allocate(long bytes, OptionalLong record, long offset) {
        allocation =
                charge(
                        "parserAllocationBytes",
                        allocation,
                        bytes,
                        limits.maximumParserAllocationBytes(),
                        record,
                        offset);
    }

    private long charge(
            String name,
            long current,
            long amount,
            long maximum,
            OptionalLong record,
            long offset) {
        long requested;
        boolean overflow = false;
        try {
            requested = Math.addExact(current, amount);
        } catch (ArithmeticException e) {
            requested = Long.MAX_VALUE;
            overflow = true;
        }
        if (overflow || requested > maximum) {
            throw ShapefileFailures.limit(source, scope, name, requested, maximum, record, offset);
        }
        return requested;
    }
}
