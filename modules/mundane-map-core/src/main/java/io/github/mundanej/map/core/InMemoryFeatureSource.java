package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable linear-scan feature source for application and integration use. */
public final class InMemoryFeatureSource implements FeatureSource {
    private final List<FeatureRecord> records;
    private final FeatureSourceMetadata metadata;
    private final FeatureSourceLimits limits;
    private Cursor liveCursor;
    private boolean closed;

    private InMemoryFeatureSource(
            SourceIdentity identity,
            List<FeatureRecord> records,
            Optional<AttributeSchema> schema,
            Optional<CrsMetadata> crs,
            FeatureSourceLimits limits) {
        this.records = List.copyOf(records);
        this.limits = Objects.requireNonNull(limits, "limits");
        validateRecords(identity.id(), this.records, schema);
        Envelope extent = null;
        for (FeatureRecord record : this.records) {
            extent =
                    extent == null
                            ? record.geometry().envelope()
                            : extent.union(record.geometry().envelope());
        }
        this.metadata =
                new FeatureSourceMetadata(
                        identity,
                        Optional.ofNullable(extent),
                        OptionalLong.of(this.records.size()),
                        schema,
                        crs);
    }

    /** Opens a source with Level 1 limits and absent schema/CRS. */
    public static InMemoryFeatureSource open(SourceIdentity identity, List<FeatureRecord> records) {
        return open(
                identity, records, Optional.empty(), Optional.empty(), FeatureSourceLimits.LEVEL_1);
    }

    /** Opens a fully described immutable source. */
    public static InMemoryFeatureSource open(
            SourceIdentity identity,
            List<FeatureRecord> records,
            Optional<AttributeSchema> schema,
            Optional<CrsMetadata> crs,
            FeatureSourceLimits limits) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(crs, "crs");
        return new InMemoryFeatureSource(identity, records, schema, crs, limits);
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public FeatureSourceLimits limits() {
        return limits;
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return DiagnosticReport.empty();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        if (closed) {
            throw new IllegalStateException("Source is closed");
        }
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(cancellation, "cancellation");
        if (liveCursor != null) {
            throw new IllegalStateException("A cursor is already open");
        }
        checkCancellation(cancellation);
        FeatureQueryLimits effective = query.tighterLimits().orElse(limits.queryLimits());
        if (!effective.tightens(limits.queryLimits())) {
            throw new IllegalArgumentException("Query limits may only tighten source limits");
        }
        checkSelectedFields(query.attributes(), cancellation);
        Cursor cursor = new Cursor(query, cancellation, effective);
        liveCursor = cursor;
        return cursor;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (liveCursor != null) {
            liveCursor.closeFromSource();
        }
    }

    private void checkSelectedFields(AttributeSelection selection, CancellationToken cancellation) {
        if (!selection.isOnly() || metadata.schema().isEmpty()) {
            return;
        }
        AttributeSchema schema = metadata.schema().orElseThrow();
        int checked = 0;
        for (String field : selection.orderedNames()) {
            if ((checked++ & 4095) == 0) {
                checkCancellation(cancellation);
            }
            if (schema.field(field).isEmpty()) {
                throw failure(
                        "SOURCE_QUERY_ATTRIBUTE_UNKNOWN",
                        "Query requested an unknown attribute",
                        Map.of("field", field));
            }
        }
    }

    private void checkCancellation(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    "SOURCE_CANCELLED",
                    "Source query was cancelled",
                    Map.of("operation", "feature-query"));
        }
    }

    private static void validateRecords(
            String sourceId, List<FeatureRecord> records, Optional<AttributeSchema> schema) {
        Map<String, Integer> firstIndexes = new LinkedHashMap<>();
        for (int index = 0; index < records.size(); index++) {
            FeatureRecord record = Objects.requireNonNull(records.get(index), "record");
            Integer first = firstIndexes.putIfAbsent(record.id(), index);
            if (first != null) {
                throw failure(
                        sourceId,
                        "SOURCE_DUPLICATE_FEATURE_ID",
                        "Source contains a duplicate feature ID",
                        Map.of(
                                "firstIndex",
                                first.toString(),
                                "duplicateIndex",
                                Integer.toString(index)));
            }
            schema.ifPresent(value -> validateRecordSchema(record, value));
        }
    }

    private static void validateRecordSchema(FeatureRecord record, AttributeSchema schema) {
        Map<String, Object> attributes = record.attributes();
        for (String name : attributes.keySet()) {
            if (schema.field(name).isEmpty()) {
                throw new IllegalArgumentException("Record contains undeclared attribute: " + name);
            }
        }
        for (AttributeField field : schema.fields()) {
            Object value = attributes.get(field.name());
            if (value == null || !field.accepts(value)) {
                throw new IllegalArgumentException(
                        "Record attribute does not conform to schema: " + field.name());
            }
        }
    }

    private SourceException failure(String code, String message, Map<String, String> context) {
        return failure(metadata.identity().id(), code, message, context);
    }

    private static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
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

    private static boolean intersects(Envelope first, Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private final class Cursor implements FeatureCursor {
        private final FeatureQuery query;
        private final CancellationToken cancellation;
        private final FeatureQueryAccounting accounting;
        private State state = State.NEW;
        private int nextIndex;
        private FeatureRecord current;

        private Cursor(
                FeatureQuery query, CancellationToken cancellation, FeatureQueryLimits limits) {
            this.query = query;
            this.cancellation = cancellation;
            accounting = new FeatureQueryAccounting(metadata.identity().id(), limits);
        }

        @Override
        public boolean advance() {
            requireUsable();
            if (state == State.EXHAUSTED) {
                return false;
            }
            current = null;
            try {
                while (nextIndex < records.size()) {
                    if (cancellation.isCancellationRequested()) {
                        throw failure(
                                "SOURCE_CANCELLED",
                                "Source query was cancelled",
                                Map.of("operation", "feature-query"));
                    }
                    FeatureRecord candidate = records.get(nextIndex++);
                    accounting.recordExamined();
                    if (query.sourceBounds().isPresent()
                            && !intersects(
                                    candidate.geometry().envelope(),
                                    query.sourceBounds().orElseThrow())) {
                        continue;
                    }
                    FeatureRecord projected = project(candidate, query.attributes(), cancellation);
                    accounting.recordReturned(projected, 0, cancellation);
                    current = projected;
                    state = State.CURRENT;
                    return true;
                }
                state = State.EXHAUSTED;
                release();
                return false;
            } catch (RuntimeException | Error failure) {
                state =
                        failure instanceof SourceException exception
                                        && exception.terminal().code().equals("SOURCE_CANCELLED")
                                ? State.CANCELLED
                                : State.FAILED;
                release();
                throw failure;
            }
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

        private void closeFromSource() {
            if (state != State.CLOSED) {
                state = State.CLOSED;
                current = null;
                release();
            }
        }

        private void requireUsable() {
            if (state == State.CLOSED
                    || state == State.FAILED
                    || state == State.CANCELLED
                    || closed) {
                throw new IllegalStateException("Cursor is not usable");
            }
        }

        private void release() {
            if (liveCursor == this) {
                liveCursor = null;
            }
        }
    }

    private FeatureRecord project(
            FeatureRecord record, AttributeSelection selection, CancellationToken cancellation) {
        if (selection.equals(AttributeSelection.ALL)) {
            return record;
        }
        if (selection.equals(AttributeSelection.NONE)) {
            return new FeatureRecord(record.id(), record.name(), record.geometry(), Map.of());
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> sourceAttributes = record.attributes();
        int checked = 0;
        for (String name : selection.orderedNames()) {
            if ((checked++ & 4095) == 0) {
                checkCancellation(cancellation);
            }
            if (sourceAttributes.containsKey(name)) {
                values.put(name, sourceAttributes.get(name));
            }
        }
        checkCancellation(cancellation);
        return new FeatureRecord(
                record.id(), record.name(), record.geometry(), Collections.unmodifiableMap(values));
    }

    private enum State {
        NEW,
        CURRENT,
        EXHAUSTED,
        FAILED,
        CANCELLED,
        CLOSED
    }
}
