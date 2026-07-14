package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.SourceDiagnostic;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Cursor-local DBF projection, value scratch, warnings, and row alignment state. */
final class DbfCursorState {
    private final String source;
    private final DbfTable table;
    private final ShapefileAccounting accounting;
    private final CancellationToken cancellation;
    private final int warningLimit;
    private final DbfProjection projection;
    private final ByteBuffer marker;
    private final ByteBuffer field;
    private final Object[] values;
    private final DbfValueDecoder decoder;
    private final List<SourceDiagnostic> warnings = new ArrayList<>();
    private long omittedWarnings;

    DbfCursorState(
            String source,
            DbfTable table,
            ShapefileAccounting accounting,
            io.github.mundanej.map.api.AttributeSelection selection,
            CancellationToken cancellation,
            int warningLimit) {
        this.source = source;
        this.table = table;
        this.accounting = accounting;
        this.cancellation = cancellation;
        this.warningLimit = warningLimit;
        checkpoint();
        accounting.allocate(table.cursorReservation(selection), OptionalLong.empty(), 0);
        checkpoint();
        projection = table.projection(selection);
        marker = ByteBuffer.allocate(1);
        if (projection.size() == 0) {
            field = null;
            values = null;
            decoder = null;
        } else {
            field = ByteBuffer.allocate(projection.width());
            byte[] bytes = new byte[projection.width()];
            char[] characters = new char[projection.width()];
            values = new Object[projection.size()];
            decoder =
                    new DbfValueDecoder(
                            source,
                            table,
                            accounting,
                            cancellation,
                            this::warning,
                            bytes,
                            characters);
        }
    }

    boolean deleted(long ordinal) {
        checkpoint();
        return table.deleted(ordinal, marker, cancellation);
    }

    Map<String, Object> attributes(long ordinal) {
        if (projection.size() == 0) {
            return Map.of();
        }
        Arrays.fill(values, null);
        for (int selected = 0; selected < projection.size(); selected++) {
            checkpoint();
            int physical = projection.physicalFields()[selected];
            table.readField(ordinal, physical, field, cancellation);
            values[projection.outputPositions()[selected]] =
                    decoder.decode(ordinal, physical, field);
        }
        checkpoint();
        accounting.allocate(
                Math.multiplyExact((long) projection.size(), 32),
                OptionalLong.of(ordinal),
                table.rowOffset(ordinal));
        checkpoint();
        Map<String, Object> result = new LinkedHashMap<>();
        for (int output = 0; output < projection.outputNames().length; output++) {
            result.put(projection.outputNames()[output], values[output]);
        }
        return result;
    }

    void requireExhausted(long shpRecords) {
        table.requireExhausted(shpRecords);
    }

    void checkSize() {
        table.checkSize(cancellation);
    }

    DiagnosticReport diagnostics() {
        return new DiagnosticReport(warnings, omittedWarnings);
    }

    private void warning(SourceDiagnostic diagnostic) {
        checkpoint();
        if (warnings.size() < warningLimit) {
            warnings.add(diagnostic);
        } else {
            if (omittedWarnings != Long.MAX_VALUE) {
                omittedWarnings++;
            }
        }
    }

    private void checkpoint() {
        Shapefiles.checkpoint(source, cancellation);
    }
}
