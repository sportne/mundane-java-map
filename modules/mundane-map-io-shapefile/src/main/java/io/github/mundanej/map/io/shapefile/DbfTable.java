package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Source-owned validated DBF layout and positional channel. */
final class DbfTable {
    private static final int PLAN_STRIDE = 4;
    private static final int OFFSET = 0;
    private static final int WIDTH = 1;
    private static final int DECIMALS = 2;
    private static final int SCHEMA = 3;

    private final String source;
    private final ShapefileFileAccess.Channel channel;
    private final long capturedSize;
    private final long rowCount;
    private final int headerLength;
    private final int recordLength;
    private final DbfEncoding encoding;
    private final AttributeSchema schema;
    private final String[] names;
    private final byte[] types;
    private final int[] plan;

    DbfTable(
            String source,
            ShapefileFileAccess.Channel channel,
            long capturedSize,
            long rowCount,
            int headerLength,
            int recordLength,
            DbfEncoding encoding,
            AttributeSchema schema,
            String[] names,
            byte[] types,
            int[] plan) {
        this.source = source;
        this.channel = channel;
        this.capturedSize = capturedSize;
        this.rowCount = rowCount;
        this.headerLength = headerLength;
        this.recordLength = recordLength;
        this.encoding = encoding;
        this.schema = schema;
        this.names = names;
        this.types = types;
        this.plan = plan;
    }

    long rowCount() {
        return rowCount;
    }

    AttributeSchema schema() {
        return schema;
    }

    DbfEncoding encoding() {
        return encoding;
    }

    int fieldCount() {
        return names.length;
    }

    String name(int field) {
        return names[field];
    }

    byte type(int field) {
        return types[field];
    }

    int fieldOffset(int field) {
        return plan[field * PLAN_STRIDE + OFFSET];
    }

    int fieldWidth(int field) {
        return plan[field * PLAN_STRIDE + WIDTH];
    }

    int decimalCount(int field) {
        return plan[field * PLAN_STRIDE + DECIMALS];
    }

    int schemaOrdinal(int field) {
        return plan[field * PLAN_STRIDE + SCHEMA];
    }

    long rowOffset(long ordinal) {
        return Math.addExact(headerLength, Math.multiplyExact(ordinal - 1, recordLength));
    }

    DbfProjection projection(AttributeSelection selection) {
        int supported = schema.fields().size();
        if (selection.equals(AttributeSelection.NONE)) {
            return new DbfProjection(new int[0], new int[0], new String[0], 0);
        }
        String[] outputNames;
        int selected;
        if (selection.isOnly()) {
            outputNames = selection.orderedNames().toArray(String[]::new);
            selected = outputNames.length;
        } else {
            outputNames = new String[supported];
            for (int field = 0; field < names.length; field++) {
                int ordinal = schemaOrdinal(field);
                if (ordinal >= 0) {
                    outputNames[ordinal] = names[field];
                }
            }
            selected = supported;
        }
        int[] physical = new int[selected];
        int[] positions = new int[selected];
        int count = 0;
        int maximumWidth = 0;
        for (int field = 0; field < names.length; field++) {
            int schemaIndex = schemaOrdinal(field);
            if (schemaIndex < 0) {
                continue;
            }
            int output = selection.isOnly() ? indexOf(outputNames, names[field]) : schemaIndex;
            if (output >= 0) {
                physical[count] = field;
                positions[count] = output;
                maximumWidth = Math.max(maximumWidth, fieldWidth(field));
                count++;
            }
        }
        return new DbfProjection(
                count == selected ? physical : Arrays.copyOf(physical, count),
                count == selected ? positions : Arrays.copyOf(positions, count),
                outputNames,
                maximumWidth);
    }

    long cursorReservation(AttributeSelection selection) {
        int selected = 0;
        int maximumWidth = 0;
        java.util.List<String> selectedNames = selection.orderedNames();
        for (int field = 0; field < names.length; field++) {
            if (schemaOrdinal(field) < 0) {
                continue;
            }
            boolean chosen =
                    !selection.isOnly()
                            ? !selection.equals(AttributeSelection.NONE)
                            : selectedNames.contains(names[field]);
            if (chosen) {
                selected++;
                maximumWidth = Math.max(maximumWidth, fieldWidth(field));
            }
        }
        return Math.addExact(1L + Math.multiplyExact(maximumWidth, 4L), selected * 24L);
    }

    void validateSelection(AttributeSelection selection) {
        if (!selection.isOnly()) {
            return;
        }
        for (String name : selection.orderedNames()) {
            if (schema.field(name).isEmpty()) {
                throw ShapefileFailures.failure(
                        source,
                        "SOURCE_QUERY_ATTRIBUTE_UNKNOWN",
                        "dbf",
                        OptionalLong.empty(),
                        -1,
                        "Query requested an unknown attribute",
                        Map.of("field", name));
            }
        }
    }

    boolean deleted(long ordinal, ByteBuffer scratch, CancellationToken cancellation) {
        if (ordinal > rowCount) {
            throw countMismatch(
                    OptionalLong.of(ordinal),
                    rowOffset(rowCount + 1),
                    Map.of(
                            "dbfRows",
                            Long.toString(rowCount),
                            "requiredOrdinal",
                            Long.toString(ordinal)));
        }
        long offset = rowOffset(ordinal);
        read(scratch, 1, offset, ordinal, OptionalInt.empty(), cancellation);
        int marker = scratch.get(0) & 0xff;
        if (marker == 0x20) {
            return false;
        }
        if (marker == 0x2a) {
            return true;
        }
        throw DbfDiagnostics.failure(
                source,
                "SHAPEFILE_DBF_RECORD_MARKER_INVALID",
                "dbf",
                OptionalLong.of(ordinal),
                OptionalInt.empty(),
                Optional.empty(),
                offset,
                Map.of());
    }

    void readField(long ordinal, int field, ByteBuffer scratch, CancellationToken cancellation) {
        long offset = Math.addExact(rowOffset(ordinal), fieldOffset(field));
        read(scratch, fieldWidth(field), offset, ordinal, OptionalInt.of(field), cancellation);
    }

    long fieldAbsoluteOffset(long ordinal, int field) {
        return Math.addExact(rowOffset(ordinal), fieldOffset(field));
    }

    void requireExhausted(long shpRecords) {
        if (shpRecords != rowCount) {
            throw countMismatch(
                    OptionalLong.empty(),
                    rowOffset(shpRecords + 1),
                    Map.of(
                            "dbfRows",
                            Long.toString(rowCount),
                            "shpRecords",
                            Long.toString(shpRecords)));
        }
    }

    void checkSize(CancellationToken cancellation) {
        checkpoint(cancellation);
        long actual;
        try {
            actual = channel.size();
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "dbf", "size", -1, exception);
        }
        checkpoint(cancellation);
        if (actual != capturedSize) {
            throw DbfDiagnostics.failure(
                    source,
                    "SHAPEFILE_DBF_HEADER_INVALID",
                    "dbf",
                    OptionalLong.empty(),
                    OptionalInt.empty(),
                    Optional.empty(),
                    0,
                    Map.of("field", "fileLayout", "reason", "mismatch"));
        }
    }

    void close() throws IOException {
        channel.close();
    }

    private void read(
            ByteBuffer scratch,
            int length,
            long offset,
            long ordinal,
            OptionalInt field,
            CancellationToken cancellation) {
        scratch.clear();
        scratch.limit(length);
        int total = 0;
        try {
            while (scratch.hasRemaining()) {
                checkpoint(cancellation);
                int count = channel.read(scratch, offset + total);
                checkpoint(cancellation);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    total += count;
                }
            }
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "dbf", "read", offset + total, exception);
        }
        if (scratch.hasRemaining()) {
            throw DbfDiagnostics.failure(
                    source,
                    "SHAPEFILE_DBF_HEADER_INVALID",
                    "dbf",
                    OptionalLong.of(ordinal),
                    field,
                    field.isPresent() ? Optional.of(names[field.getAsInt()]) : Optional.empty(),
                    offset + total,
                    Map.of("field", "fileLayout", "reason", "truncated"));
        }
        scratch.flip();
    }

    private SourceException countMismatch(
            OptionalLong record, long offset, Map<String, String> context) {
        return DbfDiagnostics.failure(
                source,
                "SHAPEFILE_DBF_RECORD_COUNT_MISMATCH",
                "dbf",
                record,
                OptionalInt.empty(),
                Optional.empty(),
                offset,
                context);
    }

    private void checkpoint(CancellationToken cancellation) {
        Shapefiles.checkpoint(source, cancellation);
    }

    private static int indexOf(String[] values, String target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(target)) {
                return index;
            }
        }
        return -1;
    }
}
