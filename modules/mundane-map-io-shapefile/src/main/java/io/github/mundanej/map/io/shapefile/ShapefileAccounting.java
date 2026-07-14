package io.github.mundanej.map.io.shapefile;

import java.util.OptionalLong;

final class ShapefileAccounting {
    private final String source, scope;
    private final ShapefileLimits limits;
    private long records, allocation, decodedCharacters;

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

    void parts(long count, long record, long offset) {
        if (count > limits.maximumParts()) {
            throw ShapefileFailures.limit(
                    source,
                    scope,
                    "parts",
                    count,
                    limits.maximumParts(),
                    OptionalLong.of(record),
                    offset);
        }
    }

    void dbfRows(long count, long offset) {
        if (count > limits.maximumPhysicalRecords()) {
            throw ShapefileFailures.limit(
                    source,
                    scope,
                    "physicalRecords",
                    count,
                    limits.maximumPhysicalRecords(),
                    "dbf",
                    OptionalLong.empty(),
                    offset);
        }
    }

    void dbfFields(long count, long offset) {
        if (count > limits.maximumDbfFields()) {
            throw ShapefileFailures.limit(
                    source,
                    scope,
                    "dbfFields",
                    count,
                    limits.maximumDbfFields(),
                    "dbf",
                    OptionalLong.empty(),
                    offset);
        }
    }

    void dbfFieldWidth(long count, int field, long offset) {
        if (count > limits.maximumDbfFieldWidth()) {
            throw ShapefileFailures.limitWithField(
                    source,
                    scope,
                    "dbfFieldWidth",
                    count,
                    limits.maximumDbfFieldWidth(),
                    field,
                    offset);
        }
    }

    void decodedCharacters(long count, long record, long offset) {
        decodedCharacters("shp", count, OptionalLong.of(record), offset);
    }

    void decodedCharacters(long count, long record, int field, String name, long offset) {
        decodedCharacters =
                chargeWithField(
                        "decodedTextCharacters",
                        decodedCharacters,
                        count,
                        limits.maximumDecodedTextCharacters(),
                        OptionalLong.of(record),
                        field,
                        name,
                        offset);
    }

    void decodedCharacters(long count, long offset) {
        decodedCharacters("shp", count, OptionalLong.empty(), offset);
    }

    void decodedCharacters(String component, long count, long offset) {
        decodedCharacters(component, count, OptionalLong.empty(), offset);
    }

    private void decodedCharacters(String component, long count, OptionalLong record, long offset) {
        decodedCharacters =
                charge(
                        "decodedTextCharacters",
                        decodedCharacters,
                        count,
                        limits.maximumDecodedTextCharacters(),
                        component,
                        record,
                        offset);
    }

    void allocate(long bytes, OptionalLong record, long offset) {
        allocate("shp", bytes, record, offset);
    }

    void allocate(String component, long bytes, OptionalLong record, long offset) {
        allocation =
                charge(
                        "parserAllocationBytes",
                        allocation,
                        bytes,
                        limits.maximumParserAllocationBytes(),
                        component,
                        record,
                        offset);
    }

    void allocateDbf(long bytes, long record, int field, String name, long offset) {
        allocation =
                chargeWithField(
                        "parserAllocationBytes",
                        allocation,
                        bytes,
                        limits.maximumParserAllocationBytes(),
                        OptionalLong.of(record),
                        field,
                        name,
                        offset);
    }

    boolean canAllocate(long bytes) {
        if (bytes < 0) {
            return false;
        }
        try {
            return Math.addExact(allocation, bytes) <= limits.maximumParserAllocationBytes();
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private long charge(
            String name,
            long current,
            long amount,
            long maximum,
            OptionalLong record,
            long offset) {
        return charge(name, current, amount, maximum, "shp", record, offset);
    }

    private long charge(
            String name,
            long current,
            long amount,
            long maximum,
            String component,
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
            throw ShapefileFailures.limit(
                    source, scope, name, requested, maximum, component, record, offset);
        }
        return requested;
    }

    private long chargeWithField(
            String name,
            long current,
            long amount,
            long maximum,
            OptionalLong record,
            int field,
            String fieldName,
            long offset) {
        long requested;
        boolean overflow = false;
        try {
            requested = Math.addExact(current, amount);
        } catch (ArithmeticException exception) {
            requested = Long.MAX_VALUE;
            overflow = true;
        }
        if (amount < 0 || overflow || requested > maximum) {
            throw ShapefileFailures.limitWithField(
                    source, scope, name, requested, maximum, record, field, fieldName, offset);
        }
        return requested;
    }
}
